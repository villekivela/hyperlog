// Generates Material 3 colour schemes for the theme proposals as Kotlin, from tonal palettes
// (material-color-utilities HCT), with the standard M3 role tones and per-proposal overrides.
import { argbFromHex, hexFromArgb, Hct, TonalPalette } from '@material/material-color-utilities';
import { writeFileSync, readFileSync } from 'fs';

const proposals = JSON.parse(readFileSync('proposals.json', 'utf8'));

// role: [palette, light tone, dark tone]
const std = {
  primary: ['p', 40, 80], onPrimary: ['p', 100, 20], primaryContainer: ['p', 90, 30], onPrimaryContainer: ['p', 10, 90],
  inversePrimary: ['p', 80, 40], surfaceTint: ['p', 40, 80],
  secondary: ['s', 40, 80], onSecondary: ['s', 100, 20], secondaryContainer: ['s', 90, 30], onSecondaryContainer: ['s', 10, 90],
  tertiary: ['t', 40, 80], onTertiary: ['t', 100, 20], tertiaryContainer: ['t', 90, 30], onTertiaryContainer: ['t', 10, 90],
  error: ['e', 40, 80], onError: ['e', 100, 20], errorContainer: ['e', 90, 30], onErrorContainer: ['e', 10, 90],
  background: ['n', 98, 6], onBackground: ['n', 10, 90], surface: ['n', 98, 6], onSurface: ['n', 10, 90],
  surfaceVariant: ['nv', 90, 30], onSurfaceVariant: ['nv', 30, 80], inverseSurface: ['n', 20, 90], inverseOnSurface: ['n', 95, 20],
  outline: ['nv', 50, 60], outlineVariant: ['nv', 80, 30], scrim: ['n', 0, 0],
  surfaceDim: ['n', 87, 6], surfaceBright: ['n', 98, 24], surfaceContainerLowest: ['n', 100, 4], surfaceContainerLow: ['n', 96, 10],
  surfaceContainer: ['n', 94, 12], surfaceContainerHigh: ['n', 92, 17], surfaceContainerHighest: ['n', 90, 22],
};

let kt = 'package com.hyperlog.app.ui.theme\n\nimport androidx.compose.material3.darkColorScheme\nimport androidx.compose.material3.lightColorScheme\nimport androidx.compose.ui.graphics.Color\n\n// Generated from tonal palettes (material-color-utilities HCT) for the theme proposals.\n\n';
const summary = {};
for (const p of proposals) {
  const seed = Hct.fromInt(argbFromHex(p.seed));
  const pal = {
    p: TonalPalette.fromHueAndChroma(seed.hue, p.primaryChroma ?? Math.max(seed.chroma, 48)),
    s: TonalPalette.fromHueAndChroma(p.secondaryHue ?? seed.hue, p.sec),
    t: TonalPalette.fromHueAndChroma(p.ter[0], p.ter[1]),
    e: TonalPalette.fromHueAndChroma(25, 84),
    n: TonalPalette.fromHueAndChroma(p.neu[0] ?? seed.hue, p.neu[1]),
    nv: TonalPalette.fromHueAndChroma(p.neu[0] ?? seed.hue, p.nv),
    // Dark surfaces may carry a different amount of colour (neuDark, nvDark).
    nDark: TonalPalette.fromHueAndChroma(p.neu[0] ?? seed.hue, p.neuDark ?? p.neu[1]),
    nvDark: TonalPalette.fromHueAndChroma(p.neu[0] ?? seed.hue, p.nvDark ?? p.nv),
  };
  summary[p.name] = { seedHue: seed.hue, seedChroma: seed.chroma, seedTone: seed.tone };
  for (const dark of [false, true]) {
    const mode = dark ? 'dark' : 'light';
    const over = (p.override ?? {})[mode] ?? {};
    const lines = Object.entries(std).map(([r, [k, lt, dt]]) => {
      const o = over[r];
      let argb;
      if (typeof o === 'string' && o.startsWith('#')) argb = argbFromHex(o);
      else if (Array.isArray(o)) argb = pal[o[0]].tone(o[1]);
      else argb = pal[dark && (k === 'n' || k === 'nv') ? k + 'Dark' : k].tone(o ?? (dark ? dt : lt));
      summary[p.name][mode + '.' + r] = hexFromArgb(argb);
      return `    ${r} = Color(0xFF${hexFromArgb(argb).slice(1).toUpperCase()}),`;
    });
    kt += `internal val ${p.name}${dark ? 'Dark' : 'Light'} = ${mode}ColorScheme(\n${lines.join('\n')}\n)\n\n`;
  }
}
writeFileSync(process.argv[2] ?? 'Proposals.kt', kt);
writeFileSync('summary.json', JSON.stringify(summary, null, 1));
for (const [n, m] of Object.entries(summary)) {
  console.log(n, m.seedHue.toFixed(0), m.seedChroma.toFixed(0), m.seedTone.toFixed(0));
  for (const mode of ['light', 'dark'])
    console.log('  ', mode, ['primary', 'primaryContainer', 'onPrimaryContainer', 'secondaryContainer', 'tertiaryContainer', 'surfaceContainer', 'surfaceContainerLowest', 'surfaceBright'].map(r => `${r}=${m[mode + '.' + r]}`).join(' '));
}
