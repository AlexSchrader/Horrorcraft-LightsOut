// Live test for the LightsOut plugin: bots Josh, Hollow (killer) and Mara against the running
// server at C:\dev\lightsout-server. Drives the plugin over RCON and checks what the bots see,
// what the server reports and what lands in the outbox. Needs Node >= 22.
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Rcon } = require('./rcon');

const SERVER = 'C:/dev/lightsout-server';
const HOST = '127.0.0.1';
const SEED = '-1541124385142397106';
const RUN = 'livetest-' + new Date().toISOString().replace(/[:.]/g, '-');
const OUTBOX = path.join(SERVER, 'plugins/LightsOut/outbox', RUN + '.jsonl');
const STATE = path.join(SERVER, 'plugins/LightsOut/state.json');
const SPOT = { x: 83.5, y: 89, z: 283.5 }; // trailhead, standing height (88 is the grass block)

const props = Object.fromEntries(fs.readFileSync(path.join(SERVER, 'server.properties'), 'utf8')
  .split(/\r?\n/).filter(l => l.includes('=') && !l.startsWith('#'))
  .map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));

const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
}

function outbox(kind) {
  try {
    return fs.readFileSync(OUTBOX, 'utf8').split('\n').filter(Boolean).map(l => JSON.parse(l))
      .filter(e => !kind || e.kind === kind);
  } catch { return []; }
}
const last = kind => outbox(kind).at(-1);

let rcon;
const cmd = c => rcon.cmd(c);

async function lives(id) {
  const s = await cmd('lo status');
  const m = s.match(new RegExp(`\\b${id}=(\\d)`));
  return m ? +m[1] : null;
}

async function pos(name) {
  const s = await cmd(`data get entity ${name} Pos`);
  const n = [...s.matchAll(/(-?\d+(?:\.\d+)?)d/g)].map(m => +m[1]);
  return n.length === 3 ? { x: n[0], y: n[1], z: n[2] } : null;
}
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);

async function mounted(rider, carrier) {
  const s = await cmd(`execute as ${rider} on vehicle if entity @s[name=${carrier}]`);
  return /passed/i.test(s);
}

function spawnBot(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: +props['server-port'], username, auth: 'offline', version: '1.21.6' });
    bot.on('resourcePack', () => bot.acceptResourcePack());
    bot.heard = [];
    // What a vanilla client displays: the server-edited (unsigned) text when there is one.
    bot.on('message', (msg, pos) => { if (pos === 'chat') bot.heard.push((msg.unsigned ?? msg).toString()); });
    bot.dust = 0;
    bot.on('particle', p => { if (p.id === bot.registry.particlesByName.dust?.id) bot.dust++; });
    bot.once('spawn', () => resolve(bot));
    bot.once('kicked', r => reject(new Error(`${username} kicked: ${JSON.stringify(r)}`)));
    bot.once('error', reject);
  });
}

function slowness(bot) {
  const id = bot.registry.effectsByName.Slowness?.id;
  const e = bot.entity.effects?.[id];
  return e ? e.amplifier : -1;
}

function attack(attacker, targetName) {
  const e = attacker.players[targetName]?.entity;
  if (!e) return false;
  attacker.attack(e);
  return true;
}

async function main() {
  rcon = await new Rcon(HOST, +props['rcon.port'], props['rcon.password']).connect();
  console.log('run', RUN);
  console.log(await cmd('list'));
  const r = await cmd(`lo run ${RUN} ${SEED}`);
  check('lo run starts a run', r.startsWith('ok run'), r.trim());

  const hollow = await spawnBot('Hollow');
  const josh = await spawnBot('Josh');
  const mara = await spawnBot('Mara');
  const bots = [hollow, josh, mara];
  for (const b of bots) {
    await cmd(`gamemode survival ${b.username}`);
    await cmd(`effect clear ${b.username}`);
  }
  await cmd(`tp Josh ${SPOT.x} ${SPOT.y} ${SPOT.z}`);
  await cmd(`tp Hollow ${SPOT.x + 2} ${SPOT.y} ${SPOT.z}`);
  await cmd(`tp Mara ${SPOT.x + 7} ${SPOT.y} ${SPOT.z}`);
  await sleep(2500);

  // ---- killer visibility ----
  const hollowEntityIn = b => Object.values(b.entities).some(e => e.username === 'Hollow');
  check('killer hidden from Josh: not in tab list', !josh.players.Hollow);
  check('killer hidden from Josh: no entity', !hollowEntityIn(josh));
  check('killer hidden from Mara', !mara.players.Hollow && !hollowEntityIn(mara));
  check('killer still sees Josh', !!hollow.players.Josh?.entity);
  await cmd('lo killer show');
  await sleep(1000);
  check('/lo killer show: Josh sees Hollow', !!josh.players.Hollow && hollowEntityIn(josh));
  await cmd('lo killer hide');
  await sleep(1000);
  check('/lo killer hide: gone again', !josh.players.Hollow && !hollowEntityIn(josh));
  check('killer_visibility logged', outbox('killer_visibility').length === 2);

  // ---- three-hit rule ----
  check('Josh starts at 20 hp', josh.health === 20, `hp ${josh.health}`);
  attack(hollow, 'Josh');
  await sleep(800);
  check('unarmed swing lands nothing', josh.health === 20 && await lives('josh') === 3, `hp ${josh.health}`);
  check('unarmed swing logged as not_armed', last('hit_blocked')?.reason === 'not_armed');

  await cmd('lo arm josh 2');
  attack(hollow, 'Josh');
  await sleep(500);
  check('armed hit 1: lives 2, 13 hp', await lives('josh') === 2 && josh.health === 13, `hp ${josh.health}`);
  check('2/3: Slowness I', slowness(josh) === 0, `amp ${slowness(josh)}`);
  attack(hollow, 'Josh');
  await sleep(300);
  check('second swing inside 1.5 s blocked (too_soon)', last('hit_blocked')?.reason === 'too_soon' && await lives('josh') === 2);
  await sleep(1500);
  attack(hollow, 'Josh');
  await sleep(600);
  check('armed hit 2: lives 1, 6 hp', await lives('josh') === 1 && josh.health === 6, `hp ${josh.health}`);
  check('1/3: Slowness II', slowness(josh) === 1, `amp ${slowness(josh)}`);
  check('1/3: food capped at 6 (no sprint)', josh.food <= 6, `food ${josh.food}`);
  await sleep(1600);
  attack(hollow, 'Josh');
  await sleep(500);
  check('third swing past cap blocked (cap_reached)', last('hit_blocked')?.reason === 'cap_reached' && await lives('josh') === 1);
  const hits = outbox('hit');
  check('hits logged with lives_after 2 then 1', hits.length === 2 && hits[0].lives_after === 2 && hits[1].lives_after === 1,
    JSON.stringify(hits.map(h => h.lives_after)));
  check('hit events carry timestamp and position', hits.every(h => h.at && h.run_id === RUN && typeof h.x === 'number'));

  // ---- no healing, no non-killer damage ----
  await cmd('effect give Josh minecraft:instant_health 1 3');
  await cmd('effect give Josh minecraft:regeneration 5 3');
  await sleep(2500);
  check('no healing from instant_health / regeneration', josh.health === 6, `hp ${josh.health}`);
  await cmd('effect clear Josh minecraft:regeneration');
  await cmd('damage Josh 4 minecraft:generic');
  await cmd('damage Josh 4 minecraft:fall');
  await cmd('damage Josh 4 minecraft:starve');
  await sleep(500);
  check('generic / fall / starve damage cancelled', josh.health === 6 && await lives('josh') === 1, `hp ${josh.health}`);
  check('milk-proof: tier re-applied after effect clear', await (async () => {
    await cmd('effect clear Josh');
    await sleep(1500);
    return slowness(josh) === 1;
  })(), `amp ${slowness(josh)}`);

  // ---- blood ----
  hollow.dust = 0;
  await sleep(3000);
  check('blood particles around bleeding Josh', hollow.dust > 0, `${hollow.dust} dust particles in 3 s`);

  // ---- stun ----
  await cmd('lo killer show');
  await sleep(1000);
  const hpKiller = hollow.health;
  let stunned = false;
  for (let i = 0; i < 12 && !stunned; i++) {
    attack(josh, 'Hollow');
    await sleep(600);
    stunned = outbox('stun').length > 0;
  }
  const rolls = outbox('stun_roll');
  check('camper hit on killer rolls stun', rolls.length > 0, `${rolls.length} rolls`);
  check('stun rolls carry seed and sequential index',
    rolls.every((x, i) => x.index === i && x.seed === SEED && typeof x.roll === 'number'),
    JSON.stringify(rolls.map(x => [x.index, +x.roll.toFixed(3), x.stunned])));
  check('a stun happened', stunned);
  check('killer takes no damage', hollow.health === hpKiller && hollow.health === 20, `hp ${hollow.health}`);

  if (stunned) {
    await cmd('lo arm josh 1');
    attack(hollow, 'Josh');
    await sleep(300);
    check('stunned killer cannot hit', last('hit_blocked')?.reason === 'stunned' && await lives('josh') === 1);
    const awayFromJosh = () => hollow.lookAt(hollow.entity.position.offset(10, hollow.entity.height, 0), true);
    await awayFromJosh();
    const before = await pos('Hollow');
    hollow.setControlState('forward', true);
    await sleep(1200);
    const during = await pos('Hollow');
    hollow.setControlState('forward', false);
    check('stunned killer cannot move', before && during && dist(before, during) < 0.3,
      before && during ? `moved ${dist(before, during).toFixed(2)}` : 'no pos');
    attack(josh, 'Hollow');
    await sleep(300);
    check('no re-stun while stunned', last('killer_struck')?.result === 'already_stunned');
    await sleep(2500);
    check('stun slowness gone after 3 s', slowness(hollow) === -1, `amp ${slowness(hollow)}`);
    await awayFromJosh();
    const after1 = await pos('Hollow');
    hollow.setControlState('forward', true);
    await sleep(1000);
    hollow.setControlState('forward', false);
    const after2 = await pos('Hollow');
    check('killer moves again after 3 s', after1 && after2 && dist(after1, after2) > 0.5,
      after1 && after2 ? `moved ${dist(after1, after2).toFixed(2)}` : 'no pos');
  }
  await cmd('lo killer hide');

  // ---- proximity chat ----
  await cmd(`spreadplayers ${SPOT.x + 45} ${SPOT.z} 0 1 false Hollow`);
  await cmd(`tp Mara ${SPOT.x + 7} ${SPOT.y} ${SPOT.z}`);
  await sleep(1500);
  const hp = await pos('Hollow');
  const jp = await pos('Josh');
  mara.heard = []; hollow.heard = [];
  josh.chat('can anyone hear me');
  await sleep(400);
  josh.chat('*quiet line');
  await sleep(1500);
  const maraHeard = mara.heard.join(' | ');
  check('camper 7 blocks away hears', /can anyone hear me/.test(maraHeard), maraHeard);
  check(`listener ${hp && jp ? dist(hp, jp).toFixed(0) : '?'} blocks away hears nothing`,
    !hollow.heard.some(m => /can anyone hear me|quiet line/.test(m)), hollow.heard.join(' | '));
  check("'*' line shown without the star", /quiet line/.test(maraHeard) && !/\*quiet line/.test(maraHeard));

  // ---- carry ----
  await cmd(`tp Mara ${SPOT.x + 1} ${SPOT.y} ${SPOT.z}`);
  await sleep(800);
  const c = await cmd('lo carry mara josh');
  await sleep(800);
  check('/lo carry mounts Josh on Mara', c.startsWith('ok') && await mounted('Josh', 'Mara'), c.trim());
  josh.setControlState('sneak', true);
  await sleep(300);
  josh.setControlState('sneak', false);
  await sleep(1000);
  check('rider re-mounted after sneaking off', await mounted('Josh', 'Mara'));
  await cmd('execute as Mara at @s run tp @s ~5 ~ ~');
  await sleep(1000);
  check('rider stays on through carrier teleport', await mounted('Josh', 'Mara'));
  await cmd('lo drop josh');
  await sleep(800);
  check('/lo drop dismounts', !await mounted('Josh', 'Mara'));
  check('carry and drop logged', outbox('carry').length >= 1 && last('drop')?.reason === 'director');

  // ---- death by killer at 0 lives ----
  await cmd('tp Hollow Josh');
  const mark = 'LIVETEST-DEATH-' + Date.now();
  await cmd('say ' + mark);
  await sleep(2000);
  const died = new Promise(res => josh.once('death', () => res(true)));
  attack(hollow, 'Josh');
  const didDie = await Promise.race([died, sleep(3000).then(() => false)]);
  check('last hit kills Josh', didDie && await lives('josh') === 0);
  await sleep(500);
  const joshDeaths = outbox('death').filter(d => d.target === 'josh');
  check('death logged exactly once, cause killer', joshDeaths.length === 1 && joshDeaths[0].cause === 'killer'
    && last('hit')?.lives_after === 0, JSON.stringify(joshDeaths.map(d => d.cause)));
  const log = fs.readFileSync(path.join(SERVER, 'logs/latest.log'), 'utf8');
  const deathMsgs = (log.slice(log.indexOf(mark)).match(/Josh died/g) || []).length;
  check('server processed the death once (one death message)', deathMsgs === 1, `${deathMsgs} "Josh died" lines`);

  // ---- void stays lethal ----
  const maraDied = new Promise(res => mara.once('death', () => res(true)));
  await cmd(`tp Mara ${SPOT.x} -140 ${SPOT.z}`);
  const vd = await Promise.race([maraDied, sleep(8000).then(() => false)]);
  const vdeath = outbox('death').find(d => d.target === 'mara');
  check('void still kills (lives -> 0)', vd && vdeath?.cause === 'void' && await lives('mara') === 0, vdeath?.cause);

  // ---- state.json ----
  await sleep(500);
  const st = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  check('state.json saved: run, lives, stun index', st.run_id === RUN && st.lives.josh === 0 && st.lives.mara === 0
    && st.lives.dane === 3 && st.stun_index === rolls.length, JSON.stringify({ run: st.run_id, lives: st.lives, idx: st.stun_index }));
  check('no state.json.tmp left behind', !fs.existsSync(STATE + '.tmp'));

  console.log('\n' + (await cmd('lo status')).trim());
  for (const b of bots) b.quit();
  await sleep(500);
  rcon.close();

  const failed = results.filter(x => !x.ok);
  console.log(`\n${results.length - failed.length}/${results.length} passed`);
  console.log('outbox: ' + OUTBOX);
  process.exit(failed.length ? 1 : 0);
}

main().catch(e => { console.error('ABORTED:', e); process.exit(2); });
