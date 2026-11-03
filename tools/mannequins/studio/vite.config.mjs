// `npm run studio` (README, The studio): the tool served on the owner's machine, at localhost only.
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { defineConfig } from 'vite';
import { studio } from './plugin.mjs';

export default defineConfig({
  root: path.dirname(path.dirname(fileURLToPath(import.meta.url))),
  plugins: [studio()],
  clearScreen: false,
  server: { host: 'localhost', port: 5190, open: false },
});
