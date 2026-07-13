import os
import sys
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def main():
    print("Helmet Detection Demo")
    print("1. Run webcam")
    print("2. Run on image")
    print("3. Exit")
    choice = input("Choose an option (1/2/3): ").strip()

    if choice == "1":
        cmd = [sys.executable, str(ROOT / "main.py"), "--source", "0", "--show"]
    elif choice == "2":
        image_path = input("Enter image path (or press Enter for sample.jpg): ").strip() or "sample.jpg"
        cmd = [sys.executable, str(ROOT / "main.py"), "--source", image_path, "--show"]
    else:
        print("Goodbye")
        return

    subprocess.run(cmd, cwd=str(ROOT))


if __name__ == "__main__":
    main()
