"""Live progress monitor for the extraction pipeline."""

import json
import time
import shutil
from pathlib import Path

DATA_DIR = Path(__file__).parent / "data"
INDEX_FILE = DATA_DIR / "index.json"
EXTRACT_PROGRESS = DATA_DIR / "extract_progress.json"
IMAGES_PROGRESS = DATA_DIR / "images_progress.json"

BLOCK_FULL = "█"
BLOCK_EMPTY = "░"


def bar(done: int, total: int, width: int = 40) -> str:
    if total == 0:
        return BLOCK_EMPTY * width
    ratio = min(done / total, 1.0)
    filled = int(width * ratio)
    return BLOCK_FULL * filled + BLOCK_EMPTY * (width - filled)


def fmt_time(seconds: float) -> str:
    if seconds < 0:
        return "--:--:--"
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


def count_json(path: Path) -> int:
    if not path.exists():
        return 0
    with open(path) as f:
        data = json.load(f)
    return len(data.get("completed", []))


def main():
    cols = shutil.get_terminal_size().columns

    if not INDEX_FILE.exists():
        print("No index.json found. Run 01_inventory.py first.")
        return

    with open(INDEX_FILE) as f:
        total = len(json.load(f)["recipes"])

    print(f"\n  Coquille dure Extraction Monitor")
    print(f"  Total recipes in inventory: {total:,}\n")
    print("  Press Ctrl+C to stop watching.\n")

    prev_extract = 0
    prev_time = time.time()
    speed_samples: list[float] = []

    try:
        while True:
            now = time.time()
            extract_done = count_json(EXTRACT_PROGRESS)
            images_done = count_json(IMAGES_PROGRESS)

            dt = now - prev_time
            if dt > 0 and prev_extract > 0:
                speed = (extract_done - prev_extract) / dt
                speed_samples.append(speed)
                if len(speed_samples) > 10:
                    speed_samples.pop(0)
            prev_extract = extract_done
            prev_time = now

            avg_speed = sum(speed_samples) / len(speed_samples) if speed_samples else 0
            remaining = (total - extract_done) / avg_speed if avg_speed > 0 else -1

            extract_pct = (extract_done / total * 100) if total else 0
            images_pct = (images_done / total * 100) if total else 0

            lines = [
                f"  Extraction  {bar(extract_done, total)}  {extract_done:>6,}/{total:,}  {extract_pct:5.1f}%",
                f"  Images      {bar(images_done, total)}  {images_done:>6,}/{total:,}  {images_pct:5.1f}%",
                f"",
                f"  Speed: {avg_speed:.1f} req/s    ETA: {fmt_time(remaining)}",
            ]

            print(f"\033[2J\033[H")  # clear screen
            print(f"\n  Coquille dure Extraction Monitor")
            print(f"  Total: {total:,} recipes\n")
            for line in lines:
                print(line)

            if extract_done >= total and images_done >= total:
                print("\n  Done!")
                break

            time.sleep(5)

    except KeyboardInterrupt:
        print("\n\n  Stopped.")


if __name__ == "__main__":
    main()
