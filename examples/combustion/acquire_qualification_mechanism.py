"""Acquire a source-locked combustion mechanism without vendoring it.

The downloader accepts only HTTPS sources, verifies the catalogued byte count,
source-reported MD5 and independently calculated SHA-256, and publishes the file
atomically only after all checks pass. Existing destination files are never
overwritten: exact bytes are reused and mismatches fail closed.
"""

import argparse
import hashlib
import os
from pathlib import Path
import tempfile
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from benchmark_qualification import (
    QualificationInputError,
    load_catalog,
    verify_mechanism,
)


DOWNLOAD_BLOCK_BYTES = 1024 * 1024
USER_AGENT = "NeqSim-combustion-qualification/1.0"


def _require(condition, message):
    if not condition:
        raise QualificationInputError(message)


def _verify_source_record(mechanism, path):
    mechanism_id = mechanism["id"]
    candidate = Path(path)
    expected_size = mechanism.get("expectedSizeBytes")
    if expected_size is not None:
        _require(
            candidate.stat().st_size == expected_size,
            f"{mechanism_id} mechanism byte count mismatch",
        )
    expected_md5 = mechanism.get("reportedSourceMd5")
    if expected_md5 is not None:
        digest = hashlib.md5(usedforsecurity=False)
        with candidate.open("rb") as mechanism_file:
            for block in iter(lambda: mechanism_file.read(DOWNLOAD_BLOCK_BYTES), b""):
                digest.update(block)
        _require(
            digest.hexdigest() == expected_md5,
            f"{mechanism_id} source MD5 mismatch",
        )
    return verify_mechanism(mechanism, candidate, for_qualification=True)


def acquire_mechanism(mechanism, destination, opener=urlopen):
    """Download and atomically publish exact qualification-mechanism bytes."""
    mechanism_id = mechanism["id"]
    source_url = mechanism.get("sourceUrl", "")
    parsed = urlparse(source_url)
    _require(
        parsed.scheme == "https" and parsed.netloc,
        f"{mechanism_id} qualification source must use HTTPS",
    )

    destination = Path(destination)
    if destination.exists():
        return _verify_source_record(mechanism, destination)

    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            prefix=f".{destination.name}.",
            suffix=".part",
            dir=str(destination.parent),
            delete=False,
        ) as temporary_file:
            temporary_path = Path(temporary_file.name)
            request = Request(source_url, headers={"User-Agent": USER_AGENT})
            with opener(request, timeout=60) as response:
                while True:
                    block = response.read(DOWNLOAD_BLOCK_BYTES)
                    if not block:
                        break
                    temporary_file.write(block)

        fingerprint = _verify_source_record(mechanism, temporary_path)
        try:
            os.link(str(temporary_path), str(destination))
        except FileExistsError:
            return _verify_source_record(mechanism, destination)
        temporary_path.unlink()
        temporary_path = None
        return fingerprint
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def _mechanism_by_id(catalog, mechanism_id):
    for mechanism in catalog["mechanisms"]:
        if mechanism["id"] == mechanism_id:
            return mechanism
    raise QualificationInputError(f"unknown mechanism id: {mechanism_id}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mechanism_id")
    parser.add_argument("destination", type=Path)
    parser.add_argument(
        "--catalog",
        type=Path,
        default=Path(__file__).with_name("benchmark_catalog.json"),
    )
    arguments = parser.parse_args()
    catalog = load_catalog(arguments.catalog)
    mechanism = _mechanism_by_id(catalog, arguments.mechanism_id)
    print(acquire_mechanism(mechanism, arguments.destination))


if __name__ == "__main__":
    main()
