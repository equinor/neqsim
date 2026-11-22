"""Restore hash-pinned historical qualification outputs outside the repository."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent


def restore(destination, archive=None):
    """Verify every original file, then recreate the complete optional audit tree."""
    manifest = json.loads((ROOT / 'archive-manifest.json').read_text())
    destination = destination.resolve()
    repository = next((path for path in ROOT.parents if (path / '.git').exists()), ROOT)
    if destination == repository or repository in destination.parents:
        raise ValueError('Choose a destination outside the repository')
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Destination must be empty')
    payloads = []
    if archive:
        archive_bytes = archive.read_bytes()
        if hashlib.sha256(archive_bytes).hexdigest() != manifest['archive']['sha256']:
            raise ValueError('Archive SHA256 does not match the pinned original')
        source = zipfile.ZipFile(archive)
    else:
        source = None
    try:
        for row in manifest['files']:
            relative = PurePosixPath(row['path'])
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError('Invalid manifest path')
            if source:
                data = source.read(manifest['zip_prefix'] + row['path'])
            else:
                url = ('https://raw.githubusercontent.com/' + manifest['repository'] + '/'
                       + manifest['commit'] + '/' + manifest['source_directory'] + '/' + row['path'])
                with urllib.request.urlopen(url, timeout=60) as response:
                    data = response.read()
            if len(data) != row['bytes'] or hashlib.sha256(data).hexdigest() != row['sha256']:
                raise ValueError('Evidence bytes do not match: ' + row['path'])
            payloads.append((relative, data))
    finally:
        if source:
            source.close()
    for relative, data in payloads:
        path = destination / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    print('Verified and restored {} original files to {}'.format(len(payloads), destination))


def main():
    """Restore from the downloadable archive or the public pinned GitHub revision."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', required=True, type=Path)
    parser.add_argument('--archive', type=Path, help='Use the complete archive without network access')
    args = parser.parse_args()
    restore(args.destination, args.archive)


if __name__ == '__main__':
    main()
