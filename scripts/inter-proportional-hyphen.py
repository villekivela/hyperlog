#!/usr/bin/env python3
"""Keep Inter's hyphen proportional under tabular figures.

Inter's `tnum` swaps the hyphen for a digit-wide one, so with tabular figures on every style
"Ramp-in" reads "Ramp - in". This points the hyphen's `tnum` substitution back at the hyphen
itself, in place (the table keeps its size), and leaves digits and every other glyph alone.

Usage: scripts/inter-proportional-hyphen.py app/src/main/res/font/inter_*.ttf
Run it on fonts fresh from the Inter release; it is a no-op on a patched font. Standard library
only: no fontTools.
"""
import struct
import sys

HYPHEN = 0x2D


def patch(path):
    d = bytearray(open(path, 'rb').read())
    u16 = lambda o: struct.unpack_from('>H', d, o)[0]
    u32 = lambda o: struct.unpack_from('>I', d, o)[0]
    tables = {bytes(d[12 + 16 * i:16 + 16 * i]).decode(): (u32(20 + 16 * i), 12 + 16 * i) for i in range(u16(4))}

    def glyph(cp):
        c = tables['cmap'][0]
        for i in range(u16(c + 2)):
            off = c + u32(c + 8 + 8 * i)
            if u16(off) == 12:
                for g in range(u32(off + 12)):
                    start, end, first = struct.unpack_from('>III', d, off + 16 + 12 * g)
                    if start <= cp <= end:
                        return first + cp - start
        raise SystemExit(f'{path}: no format 12 cmap entry for U+{cp:04X}')

    hyphen = glyph(HYPHEN)
    gsub = tables['GSUB'][0]
    features, lookups = gsub + u16(gsub + 6), gsub + u16(gsub + 8)
    patched = 0
    for i in range(u16(features)):
        if bytes(d[features + 2 + 6 * i:features + 6 + 6 * i]) != b'tnum':
            continue
        feature = features + u16(features + 6 + 6 * i)
        for k in range(u16(feature + 2)):
            lookup = lookups + u16(lookups + 2 + 2 * u16(feature + 4 + 2 * k))
            kind = u16(lookup)
            for s in range(u16(lookup + 4)):
                sub = lookup + u16(lookup + 6 + 2 * s)
                if kind == 7:  # extension
                    if u16(sub + 2) != 1:
                        continue
                    sub += u32(sub + 4)
                elif kind != 1:
                    continue
                if u16(sub) != 2:
                    raise SystemExit(f'{path}: tnum single substitution is format {u16(sub)}, expected 2')
                index = coverage_index(d, sub + u16(sub + 2), hyphen, u16)
                if index is not None:
                    struct.pack_into('>H', d, sub + 6 + 2 * index, hyphen)
                    patched += 1
    if not patched:
        raise SystemExit(f'{path}: no tnum substitution for the hyphen')
    fix_checksums(d, tables, u16, u32)
    open(path, 'wb').write(d)
    print(f'{path}: hyphen (glyph {hyphen}) stays proportional under tnum')


def coverage_index(d, cov, g, u16):
    if u16(cov) == 1:
        for i in range(u16(cov + 2)):
            if u16(cov + 4 + 2 * i) == g:
                return i
    else:
        for i in range(u16(cov + 2)):
            start, end, first = struct.unpack_from('>HHH', d, cov + 4 + 6 * i)
            if start <= g <= end:
                return first + g - start
    return None


def checksum(data):
    data = bytes(data) + b'\0' * (-len(data) % 4)
    return sum(struct.unpack(f'>{len(data) // 4}I', data)) & 0xFFFFFFFF


def fix_checksums(d, tables, u16, u32):
    """The GSUB table's checksum, then head's checkSumAdjustment over the whole file."""
    off, record = tables['GSUB']
    struct.pack_into('>I', d, record + 4, checksum(d[off:off + u32(record + 12)]))
    head = tables['head'][0]
    struct.pack_into('>I', d, head + 8, 0)
    struct.pack_into('>I', d, head + 8, (0xB1B0AFBA - checksum(d)) & 0xFFFFFFFF)


if __name__ == '__main__':
    for p in sys.argv[1:]:
        patch(p)
