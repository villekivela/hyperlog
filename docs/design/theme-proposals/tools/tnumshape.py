"""Shapes the digits with and without tnum and reports whether their advances become equal: tnumshape.py <fonts...>."""
import sys
import uharfbuzz as hb

for path in sys.argv[1:]:
    blob = hb.Blob.from_file_path(path)
    font = hb.Font(hb.Face(blob))
    result = []
    for feats in ({}, {"tnum": True}):
        buf = hb.Buffer()
        buf.add_str("0123456789")
        buf.guess_segment_properties()
        hb.shape(font, buf, feats)
        result.append(sorted({p.x_advance for p in buf.glyph_positions}))
    print(f"{path.split('/')[-1]:36} default={result[0]} tnum={result[1]} tabular={len(result[1]) == 1}")
