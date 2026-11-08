"""Independent infinite radial diffusion inverse-Laplace pressure and derivative."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import time


def main():
    """Generate complete25/35digit mathematical references without importing NeqSim."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dependency-dir', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.dependency_dir:
        sys.path.insert(0, str(args.dependency_dir.resolve()))
    import mpmath as mp

    started = time.perf_counter()
    times = ['1e-12', '1e-8', '1e-6', '3e-5', '1e-4', '.003', '.009999', '.01',
             '.010001', '.3', '1', '7.5', '100', '199.999', '200', '200.001',
             '8000', '10000', '1e8', '1e12']
    rows = []
    failures = []
    for time_text in times:
        row = {'time': time_text}
        for quantity in ['pressure', 'derivative']:
            values = []
            for precision in [25, 35]:
                with mp.workdps(precision):
                    def transfer(s):
                        z = mp.sqrt(s)
                        base = mp.besselk(0, z) / (z * mp.besselk(1, z))
                        return base / s if quantity == 'pressure' else base
                    value = mp.invertlaplace(transfer, mp.mpf(time_text), method='dehoog')
                    values.append(mp.nstr(value, precision))
            with mp.workdps(50):
                a, b = map(mp.mpf, values)
                error = abs(a - b)
                if not mp.isfinite(a) or not mp.isfinite(b) or min(a, b) <= 0 or \
                        error > mp.mpf('1e-9') * (1 + abs(b)):
                    failures.append([time_text, quantity, values])
                row[quantity] = {'digits25': values[0], 'digits35': values[1],
                                 'difference': mp.nstr(error, 30)}
        rows.append(row)
    result = {'claim': 'independent mathematical inverse-Laplace solution; no measured-data or EOS calibration',
              'equations': 'Pbar=K0(sqrt(s))/(s*sqrt(s)*K1(sqrt(s))); derivative transform=s*Pbar',
              'mpmath': mp.__version__, 'rows': rows, 'failures': failures,
              'seconds': time.perf_counter() - started,
              'sourceSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')
    print(json.dumps({'rows': len(rows), 'failures': failures, 'seconds': result['seconds']}))
    raise SystemExit(bool(failures))


if __name__ == '__main__':
    main()
