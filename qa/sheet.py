#!/usr/bin/env python3
"""Planche contact: sheet.py DOSSIER_SHOTS SORTIE.png [largeur_vignette]"""
import sys, glob, os
from PIL import Image
d, out = sys.argv[1], sys.argv[2]; w = int(sys.argv[3]) if len(sys.argv) > 3 else 300
ims = [(os.path.basename(f), Image.open(f).convert("RGB")) for f in sorted(glob.glob(d + "/*.png"))]
h = max(int(im.height * w / im.width) for _, im in ims); cols = 6; rows = -(-len(ims) // cols)
sheet = Image.new("RGB", (w * cols, h * rows), "white")
for i, (n, im) in enumerate(ims): sheet.paste(im.resize((w, int(im.height * w / im.width))), ((i % cols) * w, (i // cols) * h))
sheet.save(out); print(out, sheet.size)
