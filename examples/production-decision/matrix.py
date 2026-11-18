"""Run the complete comparison and preserve both accepted and rejected trajectories."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

from decision import GRIDS, assess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--duration", type=int, default=40)
    parser.add_argument("--basis", choices=["installed", "matched"], default="installed")
    parser.add_argument("--compressor-chart", type=Path)
    parser.add_argument("--event-id", default="synthetic-two-well-restart")
    args = parser.parse_args()
    chart_args = ["--compressor-chart", str(args.compressor_chart)] if args.compressor_chart else []
    args.directory.mkdir(parents=True, exist_ok=False)
    script = Path(__file__).with_name("tieback.py")
    files = []
    for eos in ("Srk", "Pr"):
        for policy in ("fixed", "ramp"):
            for mesh, dt in GRIDS:
                name = f"{eos}-{policy}-{mesh}-{dt}.json"
                proc = subprocess.run([sys.executable, str(script), "--eos", eos, "--policy", policy, "--mesh", str(mesh),
                                "--dt", str(dt), "--duration", str(args.duration), "--revision", args.revision,
                                "--basis", args.basis, "--event-id", args.event_id, *chart_args, "--output", str(args.directory / name)], check=False)
                if proc.returncode and not (args.directory / name).is_file():
                    proc.check_returncode()
                files.append(name)
    report = assess([json.loads((args.directory / name).read_text()) for name in files])
    (args.directory / "evidence.json").write_text(json.dumps(report, indent=2, allow_nan=False) + "\n")
    (args.directory / "manifest.json").write_text(json.dumps(files, indent=2) + "\n")


if __name__ == "__main__":
    main()
