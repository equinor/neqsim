"""Probe a UniSim column operation: attached streams and column internals."""

from __future__ import annotations

import os
import shutil
import sys
import time


def describe(obj, attrs):
    """Print the readable subset of ``attrs`` on a COM object."""
    for attr in attrs:
        try:
            value = getattr(obj, attr)
        except Exception as exc:  # noqa: BLE001
            continue
        if value is None:
            print(f'    {attr:26s} None')
            continue
        try:
            if hasattr(value, 'Count'):
                items = []
                for i in range(value.Count):
                    it = value.Item(i)
                    items.append(str(getattr(it, 'Name', None)
                                     or getattr(it, 'name', '')))
                print(f'    {attr:26s} [{value.Count}] {items}')
                continue
        except Exception:  # noqa: BLE001
            pass
        try:
            print(f'    {attr:26s} {value}')
        except Exception:  # noqa: BLE001
            print(f'    {attr:26s} <unprintable>')


COL_ATTRS = ['NumberOfStages', 'NumberOfTrays', 'StageCount', 'TrayCount',
             'RefluxRatio', 'CondenserPressure', 'ReboilerPressure',
             'CondenserType', 'ColumnType', 'Specifications',
             'MaterialStreams', 'EnergyStreams', 'Operations', 'Flowsheets']

TRAY_ATTRS = ['NumberOfTrays', 'NumberOfStages', 'TrayCount', 'StageCount',
              'PressureValues', 'TemperatureValues', 'TypeName',
              'FeedStages', 'Feeds', 'AttachedFeeds', 'AttachedProducts']

STREAM_PROBE = ['MassFlow', 'HeatFlow', 'ComponentMolarFraction', 'TypeName']


def main() -> int:
    """Dump column connectivity plus its column-flowsheet internals."""
    usc = sys.argv[1]
    work = os.path.join(os.environ.get('TEMP', '.'), 'unisim_probe')
    os.makedirs(work, exist_ok=True)
    local = os.path.join(work, os.path.basename(usc))
    shutil.copy2(usc, local)

    import win32com.client
    app = win32com.client.dynamic.Dispatch('UnisimDesign.Application')
    app.Visible = False
    case = app.SimulationCases.Open(os.path.abspath(local))
    time.sleep(3)
    case.Solver.CanSolve = False

    column_types = {'distillation', 'columnop', 'absorber',
                    'reboiledabsorber', 'refluxedabsorber', 'ratedistillation'}

    def walk(flowsheet, path):
        """Recursively dump column operations."""
        for i in range(flowsheet.Operations.Count):
            op = flowsheet.Operations.Item(i)
            tname = str(getattr(op, 'TypeName', '')).lower()
            if tname not in column_types:
                continue
            name = str(getattr(op, 'Name', None) or getattr(op, 'name', ''))
            print(f'\n=== {path}/{name} type={tname} ===')
            for coll_name in ('AttachedFeeds', 'AttachedProducts'):
                try:
                    coll = getattr(op, coll_name)
                except Exception:  # noqa: BLE001
                    continue
                print(f'  {coll_name}:')
                for k in range(coll.Count):
                    item = coll.Item(k)
                    nm = str(getattr(item, 'Name', None)
                             or getattr(item, 'name', ''))
                    kinds = []
                    for probe in STREAM_PROBE:
                        try:
                            getattr(item, probe)
                            kinds.append(probe)
                        except Exception:  # noqa: BLE001
                            pass
                    print(f'     {nm:22s} has={kinds}')
            try:
                cfs = op.ColumnFlowsheet
            except Exception:  # noqa: BLE001
                cfs = None
            if cfs is not None:
                print('  ColumnFlowsheet attrs:')
                describe(cfs, COL_ATTRS)
                try:
                    for j in range(cfs.Operations.Count):
                        sub = cfs.Operations.Item(j)
                        st = str(getattr(sub, 'TypeName', '')).lower()
                        sn = str(getattr(sub, 'Name', None)
                                 or getattr(sub, 'name', ''))
                        print(f'  -- internal {sn} type={st}')
                        describe(sub, TRAY_ATTRS)
                except Exception as exc:  # noqa: BLE001
                    print('   internals err', exc)
        try:
            for i in range(flowsheet.Flowsheets.Count):
                sub = flowsheet.Flowsheets.Item(i)
                walk(sub, f'{path}/{getattr(sub, "name", i)}')
        except Exception:  # noqa: BLE001
            pass

    walk(case.Flowsheet, 'Main')
    case.Close()
    return 0


if __name__ == '__main__':
    sys.exit(main())
