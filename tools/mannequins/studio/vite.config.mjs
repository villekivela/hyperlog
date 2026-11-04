// `npm run studio` (README, The studio): the tool served on the owner's machine, at 127.0.0.1 only (IPv4 loopback; `localhost` landed on IPv6 only).
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { defineConfig } from 'vite';
import { studio } from './plugin.mjs';

export default defineConfig({
  root: path.dirname(path.dirname(fileURLToPath(import.meta.url))),
  plugins: [studio()],
  clearScreen: false,
  server: { host: '127.0.0.1', port: 5190, open: false },
});
