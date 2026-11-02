// usage: node eval.mjs <url> <expression>: prints the JSON value of <expression> after the page settles
import { spawn } from 'node:child_process';
const [url, expr] = process.argv.slice(2);
const port = 9334;
const chrome = spawn('chromium', ['--headless=new', '--enable-unsafe-swiftshader', '--use-angle=swiftshader', '--allow-file-access-from-files', `--remote-debugging-port=${port}`, '--user-data-dir=/tmp/claude-1000/-home-villekivela-Work-hyperlog/929cdc28-150a-4b66-a6b7-8fc83e9fb698/scratchpad/machines/.profile', '--window-size=444,700', url], { stdio: 'ignore' });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let targets;
for (let i = 0; i < 50; i++) { try { targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); if (targets.some((t) => t.type === 'page')) break; } catch {} await sleep(200); }
const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r));
let id = 0; const pending = new Map();
ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
const send = (method, params) => new Promise((r) => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
await sleep(12000);
const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
console.log(JSON.stringify(r.result?.result?.value ?? r.result, null, 1));
ws.close(); chrome.kill();
