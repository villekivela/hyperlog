// usage: node cdp.mjs <url> <download-dir> [selector-to-click] [screenshot.png]
// Opens a page in headless Chromium over the DevTools protocol, waits, optionally clicks a
// selector with downloads allowed into <download-dir>, and optionally takes a screenshot.
import { spawn } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';

const [url, dir, selector, shot] = process.argv.slice(2);
mkdirSync(dir, { recursive: true });
const port = 9333;
const chrome = spawn('chromium', [
  '--headless=new', '--enable-unsafe-swiftshader', '--use-angle=swiftshader', '--no-first-run',
  `--remote-debugging-port=${port}`, '--window-size=1400,1000', `--user-data-dir=${dir}/.profile`, 'about:blank',
], { stdio: 'ignore' });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let targets;
for (let i = 0; i < 50; i++) {
  try { targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); break; } catch { await sleep(200); }
}
const page = targets.find((t) => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r));
let id = 0;
const pending = new Map();
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
});
const send = (method, params = {}) => new Promise((r) => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });

await send('Page.enable');
await send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: dir, eventsEnabled: true });
await send('Page.navigate', { url });
await sleep(15000);
if (shot) {
  const r = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(shot, Buffer.from(r.result.data, 'base64'));
}
if (selector) {
  const r = await send('Runtime.evaluate', {
    expression: `(() => { const b = document.querySelector(${JSON.stringify(selector)}); if (!b) return 'missing'; b.click(); return 'clicked'; })()`,
    returnByValue: true,
  });
  console.log(r.result?.result?.value);
  await sleep(15000);
}
ws.close();
chrome.kill();
