#!/usr/bin/env python3
"""Parcours Coquille dure + captures. Usage: shoot.py SERIAL TAG [--font F] [--night] [--dens D] [--ime] [--land]"""
import subprocess, sys, re, time, os, xml.etree.ElementTree as ET
ADB = os.path.expanduser("~/Android/sdk/platform-tools/adb")
args = sys.argv[1:]
ser, tag = args[0], args[1]
opt = lambda n, d=None: args[args.index(n)+1] if n in args else d
font, dens, night = opt("--font", "1.0"), opt("--dens"), "--night" in args
land = "--land" in args
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shots", tag)
os.makedirs(out, exist_ok=True)

def adb(*a, raw=False):
    r = subprocess.run([ADB, "-s", ser, *a], capture_output=True)
    return r.stdout if raw else r.stdout.decode(errors="replace")
sh = lambda c: adb("shell", c)

def nodes():
    sh("uiautomator dump /sdcard/u.xml")
    x = adb("exec-out", "cat", "/sdcard/u.xml")
    try: root = ET.fromstring(x)
    except Exception: return []
    res = []
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if m:
            l, t, r, b = map(int, m.groups())
            res.append(dict(text=n.get("text"), desc=n.get("content-desc"), click=n.get("clickable") == "true", l=l, t=t, r=r, b=b))
    return res

def tap(x, y): sh(f"input tap {x} {y}")
def tap_label(lbl):
    for n in nodes():
        if lbl in (n["text"], n["desc"]):
            tap((n["l"]+n["r"])//2, (n["t"]+n["b"])//2); return True
    print(f"  [!] label introuvable: {lbl}"); return False
def shot(name):
    open(f"{out}/{name}.png", "wb").write(adb("exec-out", "screencap", "-p", raw=True)); print("  shot", name)
def swipe_up():
    w, h = [int(v) for v in re.findall(r"(\d+)x(\d+)", sh("wm size"))[-1]]
    if land: w, h = max(w, h), min(w, h)
    sh(f"input swipe {w//2} {int(h*.75)} {w//2} {int(h*.3)} 300")
def back(): sh("input keyevent 4")

# réglages
sh("svc power stayon true"); sh("input keyevent 224"); sh("input keyevent 82")
sh(f"settings put system font_scale {font}")
sh(f"cmd uimode night {'yes' if night else 'no'}")
if dens: sh(f"wm density {dens}")
sh("settings put system accelerometer_rotation 0"); sh(f"settings put system user_rotation {1 if land else 0}")
sh("am force-stop fr.coquilledure"); sh("logcat -c")
sh("am start -n fr.coquilledure/.MainActivity"); time.sleep(7)

shot("01_home_top"); swipe_up(); time.sleep(1); shot("02_home_scroll"); swipe_up(); time.sleep(1); shot("03_home_scroll2")
for i, lbl in enumerate(["Découvrir", "Mes recettes", "Ma semaine", "Courses"], 4):
    tap_label(lbl); time.sleep(2.5); shot(f"{i:02d}_{lbl.replace(' ','_')}")
    if lbl == "Découvrir": swipe_up(); time.sleep(1); shot("04b_Decouvrir_scroll")
# fiche recette : première grosse carte cliquable depuis l'accueil
tap_label("Pour Vous"); time.sleep(1)
for _ in range(4): sh("input swipe 540 600 540 1500 200" if not land else "input swipe 1200 250 1200 800 200")
time.sleep(1)
cands = [n for n in nodes() if n["click"] and (n["b"]-n["t"]) > 250 and (n["r"]-n["l"]) > 250]
if cands:
    n = cands[0]; tap((n["l"]+n["r"])//2, (n["t"]+n["b"])//2); time.sleep(4)
    shot("10_recette_top"); swipe_up(); time.sleep(1); shot("11_recette_scroll"); swipe_up(); time.sleep(1); shot("12_recette_scroll2")
    back(); time.sleep(1)
# recherche + clavier
tap_label("Découvrir"); time.sleep(2)
cands = [n for n in nodes() if n["click"] and n["t"] < 700 and (n["r"]-n["l"]) > 400]
if cands:
    n = cands[0]; tap((n["l"]+n["r"])//2, (n["t"]+n["b"])//2); time.sleep(2); sh("input text poulet"); time.sleep(2); shot("20_recherche_clavier"); back(); back()

# restauration
sh("settings put system user_rotation 0"); sh("settings put system accelerometer_rotation 1"); sh("svc power stayon false"); sh("settings put system font_scale 1.0"); sh("cmd uimode night auto"); sh("wm density reset")
crash = sh("logcat -d -b crash")
open(f"{out}/crash.log", "w").write(crash)
print("crash:", "OUI" if "FATAL" in crash else "non")
