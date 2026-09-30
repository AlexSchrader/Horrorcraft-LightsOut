// Minimal Source RCON client (no dependency). One request at a time.
const net = require('net');

class Rcon {
  constructor(host, port, password) {
    Object.assign(this, { host, port, password, nextId: 1, buf: Buffer.alloc(0), waiting: new Map() });
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.sock = net.connect(this.port, this.host, async () => {
        try {
          await this.send(this.password, 3);
          resolve(this);
        } catch (e) { reject(e); }
      });
      this.sock.on('error', reject);
      this.sock.on('data', d => this.onData(d));
    });
  }

  onData(d) {
    this.buf = Buffer.concat([this.buf, d]);
    while (this.buf.length >= 4) {
      const len = this.buf.readInt32LE(0);
      if (this.buf.length < 4 + len) return;
      const id = this.buf.readInt32LE(4);
      const body = this.buf.toString('utf8', 12, 4 + len - 2);
      this.buf = this.buf.subarray(4 + len);
      if (id === -1) { for (const w of this.waiting.values()) w.reject(new Error('RCON auth failed')); this.waiting.clear(); continue; }
      const w = this.waiting.get(id);
      if (w) { this.waiting.delete(id); w.resolve(body); }
    }
  }

  send(body, type = 2) {
    const id = this.nextId++;
    const payload = Buffer.from(body, 'utf8');
    const pkt = Buffer.alloc(14 + payload.length);
    pkt.writeInt32LE(10 + payload.length, 0);
    pkt.writeInt32LE(id, 4);
    pkt.writeInt32LE(type, 8);
    payload.copy(pkt, 12);
    return new Promise((resolve, reject) => {
      this.waiting.set(id, { resolve, reject });
      this.sock.write(pkt);
      setTimeout(() => { if (this.waiting.delete(id)) reject(new Error('RCON timeout: ' + body)); }, 5000);
    });
  }

  cmd(c) { return this.send(c, 2); }
  close() { this.sock.end(); }
}

module.exports = { Rcon };
