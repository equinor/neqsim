"""Batch-convert a corpus of UniSim .usc cases and grade the conversion.

Runs every case in an isolated subprocess so a COM failure on one file cannot
abort the batch, then reports per-case timings, unmapped operation types,
generated-code defects and (optionally) a NeqSim run.

Usage:
    python devtools/unisim_batch_check.py --samples-dir "<dir>" --out report.json
    python devtools/unisim_batch_check.py --single <case.usc> --out one.json
"""

from __future__ import annotations

import argparse
import ast
import builtins
import json
import os
import subprocess
import sys
import time
import traceback
from typing import Any, Dict, List

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)


def _undefined_names(source: str) -> List[str]:
    """Return names loaded in a generated module that are never bound in it.

    A cheap static check that catches broken wiring (a forward reference whose
    placeholder was never emitted) without executing the model. Names bound
    anywhere in the module count as defined, so loop variables and ``except as``
    targets do not produce false positives.

    Args:
        source: Generated Python module source.

    Returns:
        Sorted list of names referenced but never bound.
    """
    try:
        tree = ast.parse(source)
    except SyntaxError as exc:
        return [f'<syntax error: {exc}>']

    bound = set(dir(builtins))
    loaded = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Name):
            (bound if isinstance(node.ctx, (ast.Store, ast.Del))
             else loaded).add(node.id)
        elif isinstance(node, (ast.Import, ast.ImportFrom)):
            for alias in node.names:
                bound.add((alias.asname or alias.name).split('.')[0])
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef,
                               ast.ClassDef)):
            bound.add(node.name)
            args = getattr(node, 'args', None)
            if args is not None:
                for arg in (list(args.args) + list(args.kwonlyargs)
                            + list(args.posonlyargs)):
                    bound.add(arg.arg)
                for extra in (args.vararg, args.kwarg):
                    if extra is not None:
                        bound.add(extra.arg)
        elif isinstance(node, ast.ExceptHandler) and node.name:
            bound.add(node.name)
        elif isinstance(node, ast.Lambda):
            for arg in node.args.args:
                bound.add(arg.arg)
        elif isinstance(node, ast.Global):
            bound.update(node.names)
    return sorted(loaded - bound)


def convert_one(usc_path: str, work_dir: str, run_model: bool = False
                ) -> Dict[str, Any]:
    """Read one UniSim case and grade the NeqSim conversion.

    Args:
        usc_path: Path to the .usc case.
        work_dir: Scratch directory for the working copy and E300 exports.
        run_model: If True, also execute the generated NeqSim model.

    Returns:
        Result record for the case.
    """
    import shutil

    from unisim_reader import UniSimReader, UniSimToNeqSim

    os.makedirs(work_dir, exist_ok=True)
    local_copy = os.path.join(work_dir, os.path.basename(usc_path))
    shutil.copy2(usc_path, local_copy)

    record: Dict[str, Any] = {
        'case': os.path.basename(usc_path),
        'source': usc_path,
        'size_mb': round(os.path.getsize(usc_path) / 1024 / 1024, 2),
        'stage': 'start',
        'ok': False,
    }

    reader = UniSimReader(visible=False)
    try:
        t0 = time.time()
        model = reader.read(local_copy, extract_streams=True,
                            export_e300=True, e300_output_dir=work_dir)
        record['read_s'] = round(time.time() - t0, 1)
        record['stage'] = 'read'

        ops = model.all_operations()
        streams = model.all_streams()
        record['n_operations'] = len(ops)
        record['n_streams'] = len(streams)
        record['n_fluid_packages'] = len(model.fluid_packages)
        record['n_components'] = (len(model.fluid_packages[0].components)
                                  if model.fluid_packages else 0)
        record['property_packages'] = sorted({
            fp.property_package or '' for fp in model.fluid_packages})
        record['e300_written'] = sum(
            1 for fp in model.fluid_packages if fp.e300_file_path)

        type_counts: Dict[str, int] = {}
        for op in ops:
            key = (op.type_name or '').lower()
            type_counts[key] = type_counts.get(key, 0) + 1
        record['op_types'] = dict(sorted(type_counts.items(),
                                         key=lambda kv: -kv[1]))
        record['unmapped_op_types'] = sorted(
            t for t in type_counts
            if UniSimReader.get_operation_handler(t) is None)

        converter = UniSimToNeqSim(model)

        t0 = time.time()
        json_str = converter.to_neqsim_json_str()
        record['to_json_s'] = round(time.time() - t0, 1)
        record['json_bytes'] = len(json_str)
        record['stage'] = 'to_json'

        t0 = time.time()
        code = converter.to_python()
        record['to_python_s'] = round(time.time() - t0, 1)
        record['python_lines'] = code.count('\n') + 1
        record['stage'] = 'to_python'

        record['undefined_names'] = _undefined_names(code)
        try:
            compile(code, '<generated>', 'exec')
            record['compiles'] = True
        except SyntaxError as exc:
            record['compiles'] = False
            record['syntax_error'] = str(exc)

        record['converter_warnings'] = list(converter.warnings)[:40]
        record['n_converter_warnings'] = len(converter.warnings)

        gen_path = os.path.join(
            work_dir,
            os.path.splitext(os.path.basename(usc_path))[0] + '_neqsim.py')
        with open(gen_path, 'w', encoding='utf-8') as handle:
            handle.write(code)
        record['generated_python'] = gen_path

        if run_model:
            t0 = time.time()
            try:
                result = converter.build_and_run(verbose=False)
                record['run_s'] = round(time.time() - t0, 1)
                record['run_ok'] = bool(result)
            except Exception as exc:  # noqa: BLE001 - report, never abort
                record['run_s'] = round(time.time() - t0, 1)
                record['run_ok'] = False
                record['run_error'] = f'{type(exc).__name__}: {exc}'

        record['ok'] = True
    except Exception as exc:  # noqa: BLE001 - report, never abort
        record['error'] = f'{type(exc).__name__}: {exc}'
        record['traceback'] = traceback.format_exc()[-3000:]
    finally:
        try:
            reader.close()
        except Exception:  # noqa: BLE001
            pass
    return record


def main() -> int:
    """Entry point for batch or single-case conversion checks."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--samples-dir')
    parser.add_argument('--single')
    parser.add_argument('--out', required=True)
    parser.add_argument('--work-dir', default=os.path.join(
        os.environ.get('TEMP', '.'), 'unisim_batch'))
    parser.add_argument('--max-mb', type=float, default=100.0)
    parser.add_argument('--limit', type=int, default=0)
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--run-model', action='store_true')
    args = parser.parse_args()

    if args.single:
        record = convert_one(args.single, args.work_dir, args.run_model)
        with open(args.out, 'w', encoding='utf-8') as handle:
            json.dump(record, handle, indent=2)
        return 0 if record.get('ok') else 1

    if not args.samples_dir:
        parser.error('either --single or --samples-dir is required')

    cases: List[str] = []
    for root, _dirs, files in os.walk(args.samples_dir):
        for name in files:
            if name.lower().endswith('.usc'):
                path = os.path.join(root, name)
                if os.path.getsize(path) / 1024 / 1024 <= args.max_mb:
                    cases.append(path)
    cases.sort(key=os.path.getsize)
    if args.limit:
        cases = cases[:args.limit]

    print(f'Batch converting {len(cases)} UniSim cases')
    results: List[Dict[str, Any]] = []
    for index, case in enumerate(cases, start=1):
        out_path = os.path.join(args.work_dir, f'_case_{index}.json')
        os.makedirs(args.work_dir, exist_ok=True)
        cmd = [sys.executable, os.path.abspath(__file__),
               '--single', case, '--out', out_path,
               '--work-dir', os.path.join(args.work_dir, f'case_{index}')]
        if args.run_model:
            cmd.append('--run-model')
        started = time.time()
        try:
            subprocess.run(cmd, timeout=args.timeout, capture_output=True)
        except subprocess.TimeoutExpired:
            results.append({'case': os.path.basename(case), 'source': case,
                            'ok': False, 'error': 'TIMEOUT',
                            'elapsed_s': round(time.time() - started, 1)})
            print(f'[{index}/{len(cases)}] TIMEOUT  {os.path.basename(case)}')
            continue
        if os.path.exists(out_path):
            with open(out_path, encoding='utf-8') as handle:
                record = json.load(handle)
        else:
            record = {'case': os.path.basename(case), 'source': case,
                      'ok': False, 'error': 'no result file'}
        record['elapsed_s'] = round(time.time() - started, 1)
        results.append(record)
        status = 'OK  ' if record.get('ok') else 'FAIL'
        detail = record.get('error', '')[:70]
        print(f'[{index}/{len(cases)}] {status} {record["case"][:52]:52s} '
              f'{record.get("elapsed_s", 0):6.1f}s {detail}')

    summary = {
        'n_cases': len(results),
        'n_ok': sum(1 for r in results if r.get('ok')),
        'n_fail': sum(1 for r in results if not r.get('ok')),
        'unmapped_op_types': sorted({
            t for r in results for t in r.get('unmapped_op_types', [])}),
        'cases_with_undefined_names': [
            r['case'] for r in results if r.get('undefined_names')],
        'cases_not_compiling': [
            r['case'] for r in results if r.get('compiles') is False],
    }
    with open(args.out, 'w', encoding='utf-8') as handle:
        json.dump({'summary': summary, 'results': results}, handle, indent=2)
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
