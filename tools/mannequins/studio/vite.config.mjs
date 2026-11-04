// `npm run studio` (README, The studio): the tool served on the owner's machine, on 127.0.0.1 only.
// Not `localhost`, which landed on IPv6 only here and refused IPv4 browsers.
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
