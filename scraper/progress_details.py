"""Live progress monitor for scrapers 06 (details) and 08 (variants).

Usage: python scraper/progress_details.py
Counts files in data/details and data/variants against data/ratings.
"""

import os
import shutil
import subprocess
import time
from collections import deque
from pathlib import Path

DATA = Path(__file__).parent / "data"
FULL, EMPTY = "█", "░"
GREEN, RED, DIM, BOLD, RESET = "\033[32m", "\033[31m", "\033[2m", "\033[1m", "\033[0m"

JOBS = [
    ("Détails   (06)", DATA / "details", "06_extract_details.py"),
    ("Variantes (08)", DATA / "variants", "08_extract_variants.py"),
]


def count(path: Path) -> int:
    try:
        with os.scandir(path) as it:
            return sum(1 for e in it if e.name.endswith(".json"))
    except FileNotFoundError:
        return 0


def running(script: str) -> bool:
    return subprocess.run(["pgrep", "-f", script], capture_output=True).returncode == 0


def bar(done: int, total: int, width: int) -> str:
    n = int(width * min(done / total, 1.0)) if total else 0
    return FULL * n + EMPTY * (width - n)


def fmt(seconds: float) -> str:
    if seconds < 0:
        return "--:--:--"
    return f"{int(seconds // 3600):02d}:{int(seconds % 3600 // 60):02d}:{int(seconds % 60):02d}"


def main() -> None:
    total = count(DATA / "ratings")
    if not total:
        print("data/ratings vide : rien à suivre.")
        return
    samples = {name: deque(maxlen=24) for name, _, _ in JOBS}  # ~2 min at 5 s
    start = time.time()
    try:
        while True:
            now = time.time()
            width = max(20, min(50, shutil.get_terminal_size().columns - 52))
            out = [f"\n  {BOLD}Coquille dure — scrapers{RESET}   {DIM}{total:,} recettes · écoulé {fmt(now - start)}{RESET}\n"]
            all_done = True
            for name, path, script in JOBS:
                done = count(path)
                alive = running(script)
                samples[name].append((now, done))
                t0, d0 = samples[name][0]
                speed = (done - d0) / (now - t0) if now > t0 else 0
                eta = (total - done) / speed if speed > 0 else -1
                if done >= total:
                    state = f"{GREEN}terminé{RESET}"
                elif alive:
                    state = f"{GREEN}● en cours{RESET}"
                    all_done = False
                else:
                    state = f"{RED}● arrêté{RESET}"
                    all_done = False
                out.append(
                    f"  {name}  {GREEN}{bar(done, total, width)}{RESET}  "
                    f"{done:>6,}/{total:,}  {done / total * 100:5.1f}%"
                )
                out.append(f"  {DIM}{'':14}{RESET}  {state}   {speed:4.1f}/s   ETA {fmt(eta) if alive else '--:--:--'}\n")
            print("\033[2J\033[H" + "\n".join(out))
            if all_done:
                print(f"  {GREEN}{BOLD}Tout est terminé.{RESET}\n")
                break
            print(f"  {DIM}Ctrl+C pour quitter{RESET}")
            time.sleep(5)
    except KeyboardInterrupt:
        print("\n  Arrêt de la surveillance.\n")


if __name__ == "__main__":
    main()
