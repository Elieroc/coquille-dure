"""Live progress monitor for scraper 09 (descriptions de collections).

Usage: python scraper/progress_collections.py
Compte data/collections/*.json contre les ids de collections trouvés dans data/details.
"""

import json
import os
import shutil
import subprocess
import time
from collections import deque
from pathlib import Path

DATA = Path(__file__).parent / "data"
OUT = DATA / "collections"
SCRIPT = "09_extract_collections.py"
FULL, EMPTY = "█", "░"
GREEN, RED, DIM, BOLD, RESET = "\033[32m", "\033[31m", "\033[2m", "\033[1m", "\033[0m"


def total_ids() -> int:
    ids = set()
    for p in (DATA / "details").glob("*.json"):
        try:
            ids.update(c["id"] for c in json.loads(p.read_text()).get("collections", []))
        except (OSError, ValueError, KeyError):
            pass
    return len(ids)


def count() -> int:
    try:
        with os.scandir(OUT) as it:
            return sum(1 for e in it if e.name.endswith(".json"))
    except FileNotFoundError:
        return 0


def running() -> bool:
    return subprocess.run(["pgrep", "-f", f"python.*{SCRIPT}"], capture_output=True).returncode == 0


def errors() -> int:
    try:
        return (DATA / "collections.log").read_text().count("ERROR")
    except OSError:
        return 0


def fmt(seconds: float) -> str:
    if seconds < 0:
        return "--:--:--"
    return f"{int(seconds // 3600):02d}:{int(seconds % 3600 // 60):02d}:{int(seconds % 60):02d}"


def main() -> None:
    total = total_ids()
    if not total:
        print("Aucune collection trouvée dans data/details.")
        return
    samples: deque = deque(maxlen=24)
    start = time.time()
    try:
        while True:
            now = time.time()
            width = max(20, min(50, shutil.get_terminal_size().columns - 40))
            done, alive = count(), running()
            samples.append((now, done))
            t0, d0 = samples[0]
            speed = (done - d0) / (now - t0) if now > t0 else 0
            eta = (total - done) / speed if speed > 0 else -1
            n = int(width * min(done / total, 1.0))
            if done >= total:
                state = f"{GREEN}terminé{RESET}"
            elif alive:
                state = f"{GREEN}● en cours{RESET}"
            else:
                state = f"{RED}● arrêté{RESET}"
            err = errors()
            print(
                "\033[2J\033[H"
                f"\n  {BOLD}Coquille dure — descriptions de collections{RESET}   {DIM}écoulé {fmt(now - start)}{RESET}\n\n"
                f"  Collections  {GREEN}{FULL * n}{EMPTY * (width - n)}{RESET}  {done:>5,}/{total:,}  {done / total * 100:5.1f}%\n"
                f"  {DIM}{'':11}{RESET}  {state}   {speed:4.1f}/s   ETA {fmt(eta) if alive else '--:--:--'}"
                f"   {RED if err else DIM}erreurs {err}{RESET}\n"
            )
            if done >= total:
                print(f"  {GREEN}{BOLD}Terminé.{RESET}\n")
                break
            if not alive:
                print(f"  {RED}Le scraper ne tourne plus.{RESET}\n")
                break
            print(f"  {DIM}Ctrl+C pour quitter{RESET}")
            time.sleep(5)
    except KeyboardInterrupt:
        print("\n  Arrêt de la surveillance.\n")


if __name__ == "__main__":
    main()
