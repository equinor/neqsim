"""Probe UniSim COM objects for attribute names the reader does not yet use.

Opens a case and dumps, for each operation whose type matches ``--types``, the
readable COM properties and any stream-like collections. Used to discover the
connectivity properties of operations (columns, gasifiers, electrolyzers) that
do not follow the Feeds[]/Products[] pattern.

Usage:
    python devtools/unisim_probe_ops.py --usc <case.usc> --types columnop,absorber
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys
import time
from typing import Any, List

CANDIDATE_ATTRS = [
    'Feeds', 'FeedStream', 'FeedStreams', 'AttachedFeeds', 'InletStreams',
    'Products', 'Product', 'ProductStream', 'ProductStreams',
    'AttachedProducts', 'OutletStreams',
    'VapourProduct', 'LiquidProduct', 'WaterProduct',
    'OverheadProduct', 'BottomsProduct', 'DistillateProduct',
    'ColumnFlowsheet', 'Flowsheet', 'MainFlowsheet',
    'NumberOfStages', 'NumberOfTrays', 'CondenserPressure', 'ReboilerPressure',
    'RefluxRatio', 'Specifications', 'TypeName',
]


def _names(collection) -> List[str]:
    """Return the ``name`` of every item in a COM collection."""
    out: List[str] = []
    try:
        for index in range(collection.Count):
            item = collection.Item(index)
            out.append(str(getattr(item, 'Name', None)
                           or getattr(item, 'name', '')))
    except Exception as exc:  # noqa: BLE001
        out.append(f'<err {exc}>')
    return out


def _describe(obj: Any, attr: str) -> str:
    """Describe one COM attribute without raising."""
    try:
        value = getattr(obj, attr)
    except Exception as exc:  # noqa: BLE001
        return f'<unavailable: {type(exc).__name__}>'
    if value is None:
        return 'None'
    try:
        if hasattr(value, 'Count'):
            return f'collection[{value.Count}] {_names(value)}'
    except Exception:  # noqa: BLE001
        pass
    for name_attr in ('Name', 'name'):
        try:
            got = getattr(value, name_attr, None)
            if isinstance(got, str) and got:
                return f'object name={got!r}'
        except Exception:  # noqa: BLE001
            pass
    try:
        return f'{type(value).__name__} {value}'
    except Exception:  # noqa: BLE001
        return '<unprintable>'


def main() -> int:
    """Entry point for the COM attribute probe."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--usc', required=True)
    parser.add_argument('--types', default='')
    parser.add_argument('--work-dir', default=os.path.join(
        os.environ.get('TEMP', '.'), 'unisim_probe'))
    args = parser.parse_args()

    wanted = {t.strip().lower() for t in args.types.split(',') if t.strip()}
    os.makedirs(args.work_dir, exist_ok=True)
    local = os.path.join(args.work_dir, os.path.basename(args.usc))
    shutil.copy2(args.usc, local)

    import win32com.client
    app = win32com.client.dynamic.Dispatch('UnisimDesign.Application')
    app.Visible = False
    case = app.SimulationCases.Open(os.path.abspath(local))
    time.sleep(3)
    case.Solver.CanSolve = False

    def walk(flowsheet, path: str) -> None:
        """Recursively dump matching operations in a flowsheet."""
        for index in range(flowsheet.Operations.Count):
            op = flowsheet.Operations.Item(index)
            type_name = str(getattr(op, 'TypeName', '')).lower()
            if wanted and type_name not in wanted:
                continue
            name = str(getattr(op, 'Name', None) or getattr(op, 'name', ''))
            print(f'\n=== {path}/{name}  type={type_name} ===')
            for attr in CANDIDATE_ATTRS:
                print(f'  {attr:22s} {_describe(op, attr)}')
        try:
            for index in range(flowsheet.Flowsheets.Count):
                sub = flowsheet.Flowsheets.Item(index)
                walk(sub, f'{path}/{getattr(sub, "name", index)}')
        except Exception:  # noqa: BLE001
            pass

    walk(case.Flowsheet, 'Main')
    case.Close()
    return 0


if __name__ == '__main__':
    sys.exit(main())
