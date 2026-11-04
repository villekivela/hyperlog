// The studio's server address: IPv4 loopback, because `localhost` landed on IPv6 only here.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import config from './vite.config.mjs';

test('the studio binds to 127.0.0.1', () => {
  assert.equal(config.server.host, '127.0.0.1');
});
