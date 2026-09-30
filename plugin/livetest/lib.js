// Shared helpers for the live tests: server config, RCON, bots, outbox, checks.
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Rcon } = require('./rcon');

const SERVER = 'C:/dev/lightsout-server';
const HOST = '127.0.0.1';
const SEED = '-1541124385142397106';
const STATE = path.join(SERVER, 'plugins/LightsOut/state.json');
const SPOT = { x: 83.5, y: 89, z: 283.5 }; // trailhead, standing height (88 is the grass block)

const props = Object.fromEntries(fs.readFileSync(path.join(SERVER, 'server.properties'), 'utf8')
  .split(/\r?\n/).filter(l => l.includes('=') && !l.startsWith('#'))
  .map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));

const sleep = ms => new Promise(r => setTimeout(r, ms));
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);

function harness(prefix) {
  const RUN = prefix + '-' + new Date().toISOString().replace(/[:.]/g, '-');
  const OUTBOX = path.join(SERVER, 'plugins/LightsOut/outbox', RUN + '.jsonl');
  const results = [];
  let rcon;
  const bots = [];

  const h = {
    RUN, OUTBOX, results, bots,

    async connect() {
      rcon = await new Rcon(HOST, +props['rcon.port'], props['rcon.password']).connect();
    },

    cmd: c => rcon.cmd(c),

    check(name, ok, detail = '') {
      results.push({ name, ok: !!ok, detail });
      console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`);
    },

    outbox(kind) {
      try {
        return fs.readFileSync(OUTBOX, 'utf8').split('\n').filter(Boolean).map(l => JSON.parse(l))
          .filter(e => !kind || e.kind === kind);
      } catch { return []; }
    },

    last: kind => h.outbox(kind).at(-1),

    async lives(id) {
      const s = await h.cmd('lo status');
      const m = s.match(new RegExp(`\\b${id}=(\\d)`));
      return m ? +m[1] : null;
    },

    async pos(name) {
      const s = await h.cmd(`data get entity ${name} Pos`);
      const n = [...s.matchAll(/(-?\d+(?:\.\d+)?)d/g)].map(m => +m[1]);
      return n.length === 3 ? { x: n[0], y: n[1], z: n[2] } : null;
    },

    spawnBot(username) {
      return new Promise((resolve, reject) => {
        const bot = mineflayer.createBot({ host: HOST, port: +props['server-port'], username, auth: 'offline', version: '1.21.6' });
        bot.on('resourcePack', () => bot.acceptResourcePack());
        bot.heard = [];
        // What a vanilla client displays: the server-edited (unsigned) text when there is one.
        bot.on('message', (msg, pos) => { if (pos === 'chat') bot.heard.push((msg.unsigned ?? msg).toString()); });
        bot.dust = 0;
        bot.on('particle', p => { if (p.id === bot.registry.particlesByName.dust?.id) bot.dust++; });
        bot.once('spawn', () => { bots.push(bot); resolve(bot); });
        bot.once('kicked', r => reject(new Error(`${username} kicked: ${JSON.stringify(r)}`)));
        bot.once('error', reject);
      });
    },

    async finish() {
      console.log('\n' + (await h.cmd('lo status')).trim());
      for (const b of bots) b.quit();
      await sleep(500);
      rcon.close();
      const failed = results.filter(x => !x.ok);
      console.log(`\n${results.length - failed.length}/${results.length} passed`);
      console.log('outbox: ' + OUTBOX);
      process.exit(failed.length ? 1 : 0);
    },
  };
  return h;
}

module.exports = { harness, sleep, dist, SERVER, SEED, STATE, SPOT };
