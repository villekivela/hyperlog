// Every kit's recipe, by the name a pose entry's `kit` gives it.
import barbell from './barbell.mjs';
import dumbbells from './dumbbells.mjs';
import bench from './bench.mjs';
import platform from './platform.mjs';
import cableTower from './cable-tower.mjs';
import cableColumn from './cable-column.mjs';
import legPress from './leg-press.mjs';
import chestPress from './chest-press.mjs';
import legExtension from './leg-extension.mjs';
import legCurl from './leg-curl.mjs';
import calfRaise from './calf-raise.mjs';
import squatMachine from './squat-machine.mjs';
import rowMachine from './row-machine.mjs';
import pulldownMachine from './pulldown-machine.mjs';
import preacherCurl from './preacher-curl.mjs';
import tBarRowMachine from './t-bar-row-machine.mjs';

export const RECIPES = Object.fromEntries([
  barbell, dumbbells, bench, platform, cableTower, cableColumn, legPress, chestPress, legExtension, legCurl, calfRaise, squatMachine, rowMachine,
  pulldownMachine, preacherCurl, tBarRowMachine,
].map((r) => [r.name, r]));
