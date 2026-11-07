#!/usr/bin/env python3
"""Prepare a canonical class archive for order-sensitive static analyzers.

SpotBugs enumerates directories in filesystem order. A single archive with sorted
entries supplies the same complete class order on local and hosted filesystems.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
ARCHIVE = "neqsim-classes.jar"


def prepare(classes: Path, output: Path) -> dict:
    """Archive every compiled class, rejecting empty or ambiguous input."""
    if classes.is_symlink() or any(p.is_symlink() for p in classes.rglob("*")):
        raise ValueError("Compiled-class file and directory symlinks are not supported")
    classes, output = classes.resolve(), output.resolve()
    if output == classes or output.is_relative_to(classes):
        raise ValueError("Analysis output must be outside the compiled-class directory")
    sources = sorted(classes.rglob("*.class"), key=lambda p: p.relative_to(classes).as_posix())
    if not sources:
        raise ValueError("No compiled classes found; compile before preparing analysis")
    output.mkdir(parents=True, exist_ok=True)
    unexpected = [p.name for p in output.iterdir() if p.name not in {ARCHIVE, "manifest.json"}]
    if unexpected:
        raise ValueError("Unexpected files in analysis-only directory: " + ", ".join(sorted(unexpected)))
    descriptor, temporary = tempfile.mkstemp(prefix=".classes-", suffix=".tmp", dir=output)
    os.close(descriptor)
    digest = hashlib.sha256()
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED) as archive:
            for source in sources:
                name = source.relative_to(classes).as_posix()
                content = source.read_bytes()
                # Fixed metadata plus stored bytes avoids clock, permission and zlib drift.
                entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                entry.create_system = 3
                entry.external_attr = 0o100644 << 16
                archive.writestr(entry, content)
                digest.update(name.encode("utf-8") + b"\0" + hashlib.sha256(content).digest())
        destination = output / ARCHIVE
        os.replace(temporary, destination)
    finally:
        Path(temporary).unlink(missing_ok=True)
    manifest = {"schema_version": 1, "format": "sorted-class-archive-v1", "archive": ARCHIVE,
                "classes": len(sources), "class_content_sha256": digest.hexdigest(),
                "archive_sha256": hashlib.sha256(destination.read_bytes()).hexdigest()}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def main(argv=None) -> int:
    """Prepare the exact input used by the opt-in Maven code-health profile."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes", type=Path, default=ROOT / "target/classes")
    parser.add_argument("--output", type=Path, default=ROOT / "target/code-health-input")
    args = parser.parse_args(argv)
    try:
        manifest = prepare(args.classes, args.output)
    except (OSError, ValueError) as error:
        parser.exit(1, str(error) + "\n")
    print(f"Prepared {manifest['classes']} classes in canonical archive order")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
