// Live test for step 1b camera accounts: Camera (auto), KillerCam, CamJosh, CamMara as spectator
// bots, with Josh, Mara and Hollow as subjects. Spectate targets are read from the server
// (/lo cam status reports getSpectatorTarget). Takes about 3 minutes (one 60 s rotation).
const { harness, sleep, SEED, SPOT } = require('./lib');

const h = harness('livetest-cam');
const { check, outbox, spawnBot } = h;
const cmd = h.cmd;

/** Parses /lo cam status into { Camera: { mode, following, spectating, hold, shot, gamemode } }. */
async function cams() {
  const out = {};
  for (const line of (await cmd('lo cam status')).split('\n').filter(Boolean)) {
    const [name, gamemode] = line.trim().split(' ');
    const kv = Object.fromEntries([...line.matchAll(/(\w+)=(\S+)/g)].map(m => [m[1], m[2]]));
    out[name] = { gamemode, ...kv };
  }
  return out;
}

const camEvents = account => outbox('cam').filter(e => e.account === account);

async function waitFor(what, fn, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    const v = await fn();
    if (v) return v;
    await sleep(250);
  }
  return null;
}

const at = (name, dx, dz = 0) => cmd(`tp ${name} ${SPOT.x + dx} ${SPOT.y} ${SPOT.z + dz}`);

async function main() {
  await h.connect();
  console.log('run', h.RUN);
  console.log(await cmd('list'));
  await cmd(`lo run ${h.RUN} ${SEED}`);
  await cmd('lo killer hide');
  await cmd('lo disarm');

  const hollow = await spawnBot('Hollow');
  const josh = await spawnBot('Josh');
  const mara = await spawnBot('Mara');
  for (const b of ['Hollow', 'Josh', 'Mara']) await cmd(`gamemode survival ${b}`);
  await at('Hollow', 0);
  await at('Josh', 5);          // closest to the killer
  await at('Mara', 15);
  await sleep(500);

  const camera = await spawnBot('Camera');
  const killerCam = await spawnBot('KillerCam');
  const camJosh = await spawnBot('CamJosh');
  const camMara = await spawnBot('CamMara');
  const cameraBots = [camera, killerCam, camJosh, camMara];
  const camNames = cameraBots.map(b => b.username);
  await sleep(3500); // join lock runs 1 s after join; director tick every 2 s

  // ---- forced spectator, hidden, see the killer ----
  let c = await cams();
  check('camera accounts forced into spectator', camNames.every(n => c[n]?.gamemode === 'spectator'),
    camNames.map(n => `${n}:${c[n]?.gamemode}`).join(' '));
  check('cameras hidden from campers (tab list)', camNames.every(n => !josh.players[n] && !mara.players[n]));
  check('cameras hidden from the killer bot', camNames.every(n => !hollow.players[n]));
  check('killer hidden from campers', !josh.players.Hollow && !mara.players.Hollow);
  check('cameras still see the hidden killer', cameraBots.every(b => !!b.players.Hollow));

  // ---- fixed cameras lock on ----
  check('KillerCam spectates Hollow', c.KillerCam?.spectating === 'Hollow', c.KillerCam?.spectating);
  check('CamJosh spectates Josh', c.CamJosh?.spectating === 'Josh', c.CamJosh?.spectating);
  check('CamMara spectates Mara', c.CamMara?.spectating === 'Mara', c.CamMara?.spectating);

  // ---- no lives, no damage, no chat in or out ----
  const st = await cmd('lo status');
  check('cameras have no lives', !/cam/.test(st.split('\n').find(l => l.startsWith('lives')) || ''));
  await cmd('damage CamJosh 10 minecraft:generic');
  await sleep(300);
  check('cameras take no damage', camJosh.health === 20, `hp ${camJosh.health}`);
  josh.heard = []; mara.heard = []; camJosh.heard = []; camera.heard = [];
  camJosh.chat('camera talking');
  await sleep(300);
  josh.chat('josh talking');
  await sleep(1200);
  check('camera chat reaches nobody', !josh.heard.concat(mara.heard).some(m => /camera talking/.test(m)),
    josh.heard.concat(mara.heard).join(' | '));
  check('cameras hear no proximity chat', !camJosh.heard.concat(camera.heard).some(m => /josh talking/.test(m)),
    camJosh.heard.concat(camera.heard).join(' | '));
  check('camper chat still works', mara.heard.some(m => /josh talking/.test(m)), mara.heard.join(' | '));

  // ---- re-lock after teleport ----
  await cmd(`tp Josh ${SPOT.x + 200} ${SPOT.y + 40} ${SPOT.z}`);
  await sleep(300);
  await cmd('tp CamJosh 0 120 0'); // shove the camera off its target
  const relocked = await waitFor('relock', async () => (await cams()).CamJosh?.spectating === 'Josh', 7000);
  check('CamJosh re-locks within 5 s after being knocked off', !!relocked);
  await at('Josh', 5);
  await sleep(1000);

  // ---- auto: closest ----
  c = await cams();
  const first = camEvents('camera')[0];
  check('auto camera starts on the camper closest to the killer', c.Camera?.spectating === 'Josh' && first?.reason === 'closest',
    `spectating ${c.Camera?.spectating}, first cut ${JSON.stringify(first && [first.target, first.reason])}`);

  // ---- auto: chase detected (armed + within 20) beats closest, after the 8 s minimum ----
  const lastCut = () => camEvents('camera').at(-1);
  const t0 = Date.parse(lastCut().at);
  await cmd('lo arm mara 1');           // Mara is 15 blocks from Hollow
  const chaseCut = await waitFor('chase', async () => lastCut()?.reason === 'chase' && lastCut(), 14000);
  const gap = chaseCut ? (Date.parse(chaseCut.at) - t0) / 1000 : null;
  check('chase detected: armed killer within 20 blocks cuts to Mara', chaseCut?.target === 'mara', JSON.stringify(chaseCut && [chaseCut.target, chaseCut.reason]));
  check('previous shot held at least 8 s', gap !== null && gap >= 8, `gap ${gap?.toFixed(1)} s`);
  c = await cams();
  check('auto camera actually spectates Mara', c.Camera?.spectating === 'Mara', c.Camera?.spectating);
  await cmd('lo disarm');

  // ---- auto: reveal detected from /lo killer show ----
  await at('Mara', 40);                  // Josh closest again
  await cmd('lo killer show');
  const t1 = Date.parse(lastCut().at);
  const revealCut = await waitFor('reveal', async () => lastCut()?.reason === 'reveal' && lastCut(), 14000);
  check('reveal detected: /lo killer show cuts to the closest camper', revealCut?.target === 'josh',
    JSON.stringify(revealCut && [revealCut.target, revealCut.reason]));
  check('reveal cut waited for the 8 s minimum', revealCut && (Date.parse(revealCut.at) - t1) / 1000 >= 8 - 0.1,
    revealCut ? `${((Date.parse(revealCut.at) - t1) / 1000).toFixed(1)} s` : '');
  check('cameras still see the killer when shown', cameraBots.every(b => !!b.players.Hollow));
  await cmd('lo killer hide');
  check('cameras keep seeing the killer after /lo killer hide', (await sleep(800), cameraBots.every(b => !!b.players.Hollow)));

  // ---- auto: discovery cue ----
  const cue = await cmd('lo cue discovery mara');
  check('/lo cue discovery accepted', cue.startsWith('ok'), cue.trim());
  const discCut = await waitFor('discovery', async () => lastCut()?.reason === 'discovery' && lastCut(), 14000);
  check('discovery cue cuts to the finder', discCut?.target === 'mara', JSON.stringify(discCut && [discCut.target, discCut.reason]));
  check('director cue logged', outbox('cue').some(e => e.cue === 'discovery' && e.target === 'mara'));

  // ---- auto: rotation when nothing is within 60 blocks of the killer ----
  await cmd('tp Hollow 300 120 500');   // far away (hovering is fine for a bot)
  await sleep(21000);                   // let the 20 s discovery cue expire
  const rotStart = lastCut();
  const rot = await waitFor('rotate', async () => {
    const e = camEvents('camera').filter(x => x.reason === 'rotate');
    return e.length > 0 && e.at(-1);
  }, 75000);
  check('rotation cuts to the next living camper', !!rot && rot.target !== rotStart.target,
    rot ? `${rotStart.target} -> ${rot.target} after ${((Date.parse(rot.at) - Date.parse(rotStart.at)) / 1000).toFixed(0)} s` : 'no rotation');
  check('rotation waited ~60 s', rot && (Date.parse(rot.at) - Date.parse(rotStart.at)) / 1000 >= 58,
    rot ? `${((Date.parse(rot.at) - Date.parse(rotStart.at)) / 1000).toFixed(1)} s` : '');
  await at('Hollow', 0);

  // ---- target dies: 10 s body hold, then the killer ----
  await at('Josh', 5);
  const refused = await cmd('lo cam Camera CamJosh');
  check('/lo cam refuses a camera as target', refused.startsWith('error'), refused.trim());
  await sleep(1500);
  for (let i = 0; i < 3; i++) { await cmd('lo hit josh'); await sleep(200); }
  await sleep(1500);
  c = await cams();
  const body = camEvents('camjosh').at(-1);
  check('CamJosh holds on the body when Josh dies', body?.reason === 'body' && c.CamJosh?.spectating === 'none' && c.CamJosh?.hold,
    `${JSON.stringify(body && [body.target, body.reason])} spectating=${c.CamJosh?.spectating} hold=${c.CamJosh?.hold}`);
  const fb = await waitFor('fallback', async () => {
    const e = camEvents('camjosh').at(-1);
    return e?.reason === 'killer_fallback' && e;
  }, 15000);
  const hold = fb && body ? (Date.parse(fb.at) - Date.parse(body.at)) / 1000 : null;
  check('then follows the killer', !!fb && (await cams()).CamJosh?.spectating === 'Hollow');
  check('body hold lasted ~10 s', hold !== null && hold >= 9.5 && hold <= 13, `${hold?.toFixed(1)} s`);

  // ---- target offline: hold, killer, then back when it returns ----
  mara.quit();
  await sleep(1500);
  const off = camEvents('cammara').at(-1);
  check('CamMara holds when Mara logs off', off?.reason === 'body', JSON.stringify(off && [off.target, off.reason]));
  const mfb = await waitFor('mara fallback', async () => camEvents('cammara').at(-1)?.reason === 'killer_fallback', 15000);
  check('CamMara falls back to the killer', !!mfb);
  const mara2 = await spawnBot('Mara');
  await cmd('gamemode survival Mara');
  const back = await waitFor('back', async () => (await cams()).CamMara?.spectating === 'Mara', 8000);
  check('CamMara returns to Mara when she reconnects', !!back);

  // ---- director overrides ----
  let r = await cmd('lo cam CamMara free');
  await sleep(500);
  c = await cams();
  check('/lo cam free releases the camera', r.startsWith('ok') && c.CamMara?.spectating === 'none' && c.CamMara?.mode === 'free', r.trim());
  await sleep(6000);
  check('free camera is not re-locked', (await cams()).CamMara?.spectating === 'none');
  r = await cmd('lo cam CamMara Hollow');
  await sleep(800);
  check('/lo cam <account> <target> retargets', r.startsWith('ok') && (await cams()).CamMara?.spectating === 'Hollow', r.trim());
  r = await cmd('lo cam KillerCam auto');
  await sleep(800);
  c = await cams();
  check('/lo cam <account> auto switches a fixed camera to auto', r.startsWith('ok') && c.KillerCam?.mode === 'auto', r.trim());

  // ---- every cut logged ----
  const all = outbox('cam');
  check('every cut logged as a cam event with account, target, reason',
    all.length > 0 && all.every(e => e.account && e.reason && e.at && e.run_id === h.RUN), `${all.length} cam events`);
  void mara2;
  await h.finish();
}

main().catch(e => { console.error('ABORTED:', e); process.exit(2); });
