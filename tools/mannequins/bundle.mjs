// Bundles the tool's own modules for the review page's Tune panel (README, Reviewing a batch), so
// the page poses and checks a body with the code that bakes it. The page can't fetch modules, so
// each is wrapped in a function of its own and its imports are rewritten to look the others up;
// what is node's (reading files) is stubbed, and never reached when the page hands it the mannequin.
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const HERE = path.dirname(fileURLToPath(import.meta.url));

/** The modules, in no particular order: each is run when something first asks for it. */
const MODULES = ['angles', 'rig', 'kit', 'motions', 'poses', 'entries', 'stage', 'checks', 'tune'];

/** What the modules ask for that isn't one of them. */
const EXTERNALS = `{
  'three': THREE,
  'three/examples/jsm/loaders/GLTFLoader.js': { GLTFLoader },
  'node:fs': { readFileSync() { throw new Error('no files in the page'); } },
  'node:fs/promises': { readFile() { throw new Error('no files in the page'); } },
  'node:url': { fileURLToPath: () => '' },
  'node:path': { default: { join: (...parts) => parts.join('/'), dirname: () => '' } },
}`;

/** The `const …` that stands for one import clause. */
function bindings(clause, spec) {
  const m = clause.match(/^(?:(\w+)\s*,?\s*)?(?:\*\s+as\s+(\w+)|\{([^}]*)\})?$/);
  if (!m) throw new Error(`Can't bundle the import ${clause} from ${spec}`);
  const [, name, all, named] = m;
  const out = [];
  if (name) out.push(`const ${name} = __require(${JSON.stringify(spec)}).default;`);
  if (all) out.push(`const ${all} = __require(${JSON.stringify(spec)});`);
  if (named) out.push(`const { ${named.split(',').map((n) => n.trim().replace(/\s+as\s+/, ': ')).filter(Boolean).join(', ')} } = __require(${JSON.stringify(spec)});`);
  return out.join(' ');
}

function wrap(name, source) {
  const exported = [];
  const body = source
    .replace(/^import\s+(.+?)\s+from\s+'([^']+)';?[ \t]*$/gm, (_, clause, spec) => bindings(clause, spec))
    .replace(/^export\s+((?:async\s+)?(?:const|let|function|class))\s+(\w+)/gm, (_, kind, id) => { exported.push(id); return `${kind} ${id}`; })
    .replace(/import\.meta\.url/g, "''");
  if (/^(import|export)\s/m.test(body)) throw new Error(`${name}.mjs has an import or export the bundle can't rewrite`);
  return `__defs[${JSON.stringify(`./${name}.mjs`)}] = (__exports) => {\n${body}\nObject.assign(__exports, { ${exported.join(', ')} });\n};`;
}

/** The bundle's source: a function body taking `THREE` and `GLTFLoader` and returning every module's exports, merged. */
export async function bundleTune() {
  const parts = [];
  for (const name of MODULES) {
    parts.push(wrap(name, await readFile(path.join(HERE, `${name}.mjs`), 'utf8')));
  }
  return `const __defs = {};
const __done = {};
const __externals = ${EXTERNALS};
function __require(spec) {
  if (!spec.startsWith('./')) {
    if (!(spec in __externals)) throw new Error('The page has no ' + spec);
    return __externals[spec];
  }
  if (!__done[spec]) {
    __done[spec] = {};
    __defs[spec](__done[spec]);
  }
  return __done[spec];
}
${parts.join('\n')}
return Object.assign({}, ...Object.keys(__defs).map(__require));
`;
}
