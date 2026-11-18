#!/usr/bin/env python3
"""Génère le logo œuf cassé : aperçu SVG + vector drawables Android (viewport 108)."""
import sys, pathlib

OUT = pathlib.Path(sys.argv[1])  # dossier res/
PREV = pathlib.Path(sys.argv[2])  # dossier aperçu

EGG = "M54,27 C66.5,27 76,45 76,60 C76,73.5 66.5,83 54,83 C41.5,83 32,73.5 32,60 C32,45 41.5,27 54,27 Z"
# zigzag de la fêlure (gauche -> droite)
ZZ = [(28, 56), (36.5, 52.5), (41, 58.5), (46.5, 51.5), (52, 58.5), (57.5, 51.5), (63, 58.5), (68.5, 52), (73, 57), (80, 54)]
zz = " ".join(f"L{x},{y}" for x, y in ZZ)
BELOW = f"M0,{ZZ[0][1]} {zz} L108,{ZZ[-1][1]} L108,108 L0,108 Z"
ABOVE = f"M0,{ZZ[0][1]} {zz} L108,{ZZ[-1][1]} L108,0 L0,0 Z"
CRACK = "M" + " L".join(f"{x},{y}" for x, y in ZZ[1:-1])

SHELL = "#FFF8EC"
SHADE = "#EFD9B4"
INNER = "#C9A46A"
INNER2 = "#E3C690"
YOLK = "#FFB300"
YOLK_HI = "#FFD54F"
GLARE = "#FFFFFF"

# ombre interne côté droit (croissant) : œuf décalé
SHADE_CUT = "M51,25 C63.5,25 72,43 72,58 C72,71.5 63.5,81 51,81 C38.5,81 29,71.5 29,58 C29,43 38.5,25 51,25 Z"
GLARE_BOTTOM = "M38.5,63 C38.5,59.5 40.5,58.5 41.5,60.5 C42.3,64.5 43.5,69 46,72.5 C47,74 45,75 43.5,73.8 C40.5,71.2 38.5,67 38.5,63 Z"
GLARE_LID = "M41.5,47 C42.5,40 46,34 49.5,32 C51,31.3 51.6,32.6 50.6,33.8 C48,37 46.3,41.5 45.2,47.2 C44.8,49.2 41.3,49.2 41.5,47 Z"

OPENING = "M33.4,55 A20.6,5.2 0 1,1 74.6,55 A20.6,5.2 0 1,1 33.4,55 Z"
OPENING_BACK = "M33.4,55 A20.6,5.2 0 0,1 74.6,55 L74.6,56 A20.6,3.6 0 0,0 33.4,56 Z"
YOLK_P = "M44,57 C44,50.5 48.5,47 54,47 C59.5,47 64,50.5 64,57 Z"
YOLK_HI_P = "M48,52 C48.6,50 50.6,48.9 52.6,48.9 C53.6,48.9 53.6,50.1 52.6,50.3 C51,50.6 49.8,51.6 49.4,52.8 C49,53.8 47.7,53.2 48,52 Z"

SHADOW = "M36,85 A18,3.2 0 1,1 72,85 A18,3.2 0 1,1 36,85 Z"
SHARDS = [
    ("M27,44 L31,41.5 L30.2,46 Z", -0),
    ("M78.5,43 L83,45.5 L78.8,47.5 Z", 0),
    ("M33,36 L35.2,33.8 L35.8,37 Z", 0),
]

LID_T = dict(rot=-22, px=36.5, py=52.5, tx=6, ty=-8)


# --------------------------------------------------------------------- SVG
def svg_path(d, fill, op=1.0):
    return f'<path d="{d}" fill="{fill}" fill-opacity="{op}"/>'


def svg_parts(lid_open=True, with_bg=True, with_shards=True, with_shadow=True):
    defs = f"""<defs>
<clipPath id="egg"><path d="{EGG}"/></clipPath>
<clipPath id="below"><path d="{BELOW}"/></clipPath>
<clipPath id="above"><path d="{ABOVE}"/></clipPath>
<clipPath id="open"><path d="{OPENING}"/></clipPath>
</defs>"""
    out = [defs]
    if with_bg:
        out.append('<rect width="108" height="108" fill="#007A33"/>')
    if with_shadow:
        out.append(svg_path(SHADOW, "#000000", 0.22))
    # intérieur
    out.append(f'<g clip-path="url(#egg)">{svg_path(OPENING, INNER)}{svg_path(OPENING_BACK, INNER2)}'
               f'<g clip-path="url(#open)">{svg_path(YOLK_P, YOLK)}{svg_path(YOLK_HI_P, YOLK_HI)}</g></g>')
    # coque basse
    out.append(f'<g clip-path="url(#below)"><g clip-path="url(#egg)">{svg_path(EGG, SHADE)}{svg_path(SHADE_CUT, SHELL)}{svg_path(GLARE_BOTTOM, GLARE, .85)}</g></g>')
    # couvercle
    t = LID_T
    tr = f'transform="translate({t["tx"]},{t["ty"]}) rotate({t["rot"]},{t["px"]},{t["py"]})"' if lid_open else ""
    out.append(f'<g {tr}><g clip-path="url(#above)"><g clip-path="url(#egg)">{svg_path(EGG, SHADE)}{svg_path(SHADE_CUT, SHELL)}{svg_path(GLARE_LID, GLARE, .85)}</g></g></g>')
    if with_shards and lid_open:
        for d, _ in SHARDS:
            out.append(svg_path(d, SHELL))
    return "\n".join(out)


def write_svg(name, **kw):
    s = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">{svg_parts(**kw)}</svg>'
    (PREV / name).write_text(s)


write_svg("icon_open.svg")
(PREV / "icon_masked.svg").write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs><clipPath id="m"><circle cx="54" cy="54" r="33"/></clipPath></defs><g clip-path="url(#m)"><rect width="108" height="108" fill="#007A33"/><g transform="translate(-1,3.5) translate(55,50.5) scale(0.78) translate(-55,-50.5)">{svg_parts(with_bg=False)}</g></g></svg>')
write_svg("icon_closed.svg", lid_open=False)


# ------------------------------------------------------------ VectorDrawable
def vp(d, fill, alpha=None, name=None):
    a = f' android:fillAlpha="{alpha}"' if alpha is not None else ""
    n = f' android:name="{name}"' if name else ""
    return f'<path{n} android:fillColor="{fill}"{a} android:pathData="{d}"/>'


def clip(d):
    return f'<clip-path android:pathData="{d}"/>'


def interior():
    return (f'<group>{clip(EGG)}{vp(OPENING, INNER)}{vp(OPENING_BACK, INNER2)}'
            f'<group>{clip(OPENING)}{vp(YOLK_P, YOLK)}{vp(YOLK_HI_P, YOLK_HI)}</group></group>')


def bottom():
    return f'<group>{clip(BELOW)}<group>{clip(EGG)}{vp(EGG, SHADE)}{vp(SHADE_CUT, SHELL)}{vp(GLARE_BOTTOM, GLARE, .85)}</group></group>'


def lid(open_):
    t = LID_T
    attrs = (f' android:rotation="{t["rot"]}" android:pivotX="{t["px"]}" android:pivotY="{t["py"]}"'
             f' android:translateX="{t["tx"]}" android:translateY="{t["ty"]}"') if open_ else ""
    return f'<group android:name="lid"{attrs}><group>{clip(ABOVE)}<group>{clip(EGG)}{vp(EGG, SHADE)}{vp(SHADE_CUT, SHELL)}{vp(GLARE_LID, GLARE, .85)}</group></group></group>'


def whole():
    return f'{vp(EGG, SHADE)}<group>{clip(EGG)}{vp(SHADE_CUT, SHELL)}</group>{vp(GLARE_BOTTOM, GLARE, .85)}{vp(GLARE_LID, GLARE, .85)}'


def shards():
    return "".join(vp(d, SHELL) for d, _ in SHARDS)


def vector(body, size=108, vw=108, vh=108, comment=""):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Généré par gen_egg.py -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp" android:height="{size}dp"\n'
            f'    android:viewportWidth="{vw}" android:viewportHeight="{vh}">\n{body}\n</vector>\n')


d = OUT / "drawable"
# icône lanceur (couche avant de l'adaptive icon)
FIT = '<group android:pivotX="55" android:pivotY="50.5" android:scaleX="0.78" android:scaleY="0.78" android:translateX="-1" android:translateY="3.5">'
(d / "ic_launcher_foreground.xml").write_text(vector(
    FIT + vp(SHADOW, "#000000", .22) + interior() + bottom() + lid(True) + shards() + "</group>", comment="Logo Coquille dure : œuf cassé."))
# monochrome (Android 13 icônes thématiques)
mono = FIT + (f'<group>{clip(BELOW)}{vp(EGG, "#FFFFFF")}</group>'
        f'<group android:rotation="{LID_T["rot"]}" android:pivotX="{LID_T["px"]}" android:pivotY="{LID_T["py"]}" android:translateX="{LID_T["tx"]}" android:translateY="{LID_T["ty"]}">'
        f'<group>{clip(ABOVE)}{vp(EGG, "#FFFFFF")}</group></group>' + shards().replace(SHELL, "#FFFFFF")) + "</group>"
(d / "ic_launcher_monochrome.xml").write_text(vector(mono, comment="Logo monochrome."))

# Splash : viewport 108 étendu sur 288dp de large -> l'œuf tient dans le cercle de 192dp.
# Œuf entier (état initial de l'animation).
(d / "splash_egg_whole.xml").write_text(vector(
    vp(SHADOW, "#000000", .22) + whole(), size=288, comment="Œuf intact du splash."))
# Pièces séparées pour l'animation Compose (même cadre 108).
(d / "egg_shadow.xml").write_text(vector(vp(SHADOW, "#000000", .22), comment="Ombre."))
(d / "egg_inside.xml").write_text(vector(interior(), comment="Intérieur + jaune."))
(d / "egg_bottom.xml").write_text(vector(bottom(), comment="Coque basse."))
(d / "egg_whole.xml").write_text(vector(whole(), comment="Œuf intact sans ombre."))
(d / "egg_lid.xml").write_text(vector(lid(False), comment="Calotte (position fermée)."))
print("ok")
