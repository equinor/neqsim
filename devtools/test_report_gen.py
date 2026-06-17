"""Quick integration test for the generate_report.py template in new_task.py.

Creates a temporary task folder with all results.json keys populated,
extracts the report template, and runs it to produce Report.docx + Report.html.
Verifies that all new sections (figure_discussion, benchmark, uncertainty, risk)
are rendered in the HTML output.
"""
import json
import os
import struct
import sys
import tempfile
import zlib


def make_tiny_png():
    """Create a minimal valid 1x1 PNG file."""
    sig = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0)
    ihdr_crc = struct.pack('>I', zlib.crc32(b'IHDR' + ihdr_data) & 0xffffffff)
    ihdr = struct.pack('>I', 13) + b'IHDR' + ihdr_data + ihdr_crc
    raw = b'\x00\x00\x00\x00'
    idat_data = zlib.compress(raw)
    idat_crc = struct.pack('>I', zlib.crc32(b'IDAT' + idat_data) & 0xffffffff)
    idat = struct.pack('>I', len(idat_data)) + b'IDAT' + idat_data + idat_crc
    iend_crc = struct.pack('>I', zlib.crc32(b'IEND') & 0xffffffff)
    iend = struct.pack('>I', 0) + b'IEND' + iend_crc
    return sig + ihdr + idat + iend


def create_test_task(tmpdir):
    """Create a full test task folder with all results.json keys."""
    for subdir in ['figures', 'step3_report', 'step1_scope_and_research']:
        os.makedirs(os.path.join(tmpdir, subdir), exist_ok=True)

    # task_spec.md — richer with Objective section for auto problem description
    with open(os.path.join(tmpdir, 'step1_scope_and_research', 'task_spec.md'), 'w') as f:
        f.write(
            '## Objective\n'
            'Evaluate the thermal performance of a JT cooling system '
            'for rich gas processing at high pressure.\n\n'
            '## Applicable Standards\nASME VIII\n'
            '## Operating Envelope\n'
            'Pressure: 60-120 bara, Temperature: -20 to 40 C\n'
            '## Acceptance Criteria\nT error < 5%\n'
        )

    # Tiny figure
    with open(os.path.join(tmpdir, 'figures', 'plot.png'), 'wb') as f:
        f.write(make_tiny_png())

    # Full results.json
    results = {
        'key_results': {'outlet_T_C': 25.3, 'pressure_drop_bar': 3.2},
        'validation': {'mass_balance_pct': 0.01, 'criteria_met': True},
        'approach': 'Used SRK EOS with classic mixing rule.',
        'conclusions': 'The analysis confirms safe operation.',
        'figure_captions': {'plot.png': 'Temperature profile'},
        'figure_discussion': [
            {
                'figure': 'plot.png',
                'title': 'Temperature Profile Validation',
                'observation': 'Max temperature is 25.3 C at the outlet.',
                'mechanism': 'JT cooling drives temperature drop.',
                'implication': 'Equipment can operate safely.',
                'recommendation': 'Proceed with current design.',
                'linked_results': ['outlet_T_C'],
                'insight_question_ref': 'Q1',
            },
            {
                'figure': 'plot.png',
                'title': 'Pressure Drop Analysis',
                'observation': 'Pressure drop of 3.2 bar exceeds reference by 14%.',
                'mechanism': 'Higher friction factor due to pipe roughness assumption.',
                'implication': 'May require larger pipe diameter for production optimization.',
                'recommendation': 'Conduct sensitivity study on pipe roughness.',
                'linked_results': ['pressure_drop_bar'],
                'insight_question_ref': 'Q2',
            }
        ],
        'benchmark_validation': {
            'source': 'NIST Reference Data',
            'tests': [
                {'parameter': 'Outlet T', 'neqsim': 25.3, 'reference': 25.5,
                 'unit': 'C', 'tolerance_pct': 2.0, 'pass': True},
                {'parameter': 'Pressure Drop', 'neqsim': 3.2, 'reference': 2.8,
                 'unit': 'bar', 'tolerance_pct': 5.0, 'pass': False},
            ]
        },
        'uncertainty': {
            'method': 'Monte Carlo',
            'n_simulations': 500,
            'simulation_engine': 'NeqSim SRK',
            'output_parameter': 'NPV (MNOK)',
            'p10': -10.0, 'p50': 150.0, 'p90': 320.0,
            'mean': 155.0, 'std': 100.0,
            'prob_negative_pct': 8.5,
            'input_parameters': [
                {'name': 'Gas Price', 'low': 0.8, 'base': 1.5, 'high': 2.5,
                 'unit': 'NOK/Sm3', 'distribution': 'triangular'},
            ],
            'tornado': [
                {'parameter': 'Gas Price', 'npv_low': -50, 'npv_high': 400, 'swing': 450},
            ]
        },
        'risk_evaluation': {
            'overall_risk_level': 'Medium',
            'risk_matrix_used': '5x5 (ISO 31000)',
            'risks': [
                {'id': 'R1', 'description': 'Gas price drop', 'category': 'Market',
                 'likelihood': 'Possible', 'consequence': 'Major',
                 'risk_level': 'High', 'mitigation': 'Long-term contracts'},
                {'id': 'R2', 'description': 'Corrosion', 'category': 'Technical',
                 'likelihood': 'Unlikely', 'consequence': 'Moderate',
                 'risk_level': 'Low', 'mitigation': 'Inhibitor injection'},
            ]
        },
        'references': [
            {'id': 'Smith2019', 'text': 'Smith, J. (2019). Test Reference.'},
        ]
    }
    with open(os.path.join(tmpdir, 'results.json'), 'w') as f:
        json.dump(results, f, indent=2)


def extract_template():
    """Extract the GENERATE_REPORT template from new_task.py.

    We import the variable rather than doing raw text extraction, because
    the template uses \\' and \\\\n escapes inside a triple-quoted string
    that Python resolves during evaluation.
    """
    import importlib.util
    script_dir = os.path.dirname(os.path.abspath(__file__))
    new_task_path = os.path.join(script_dir, 'new_task.py')
    spec = importlib.util.spec_from_file_location("new_task_mod", new_task_path)
    mod = importlib.util.module_from_spec(spec)
    # Prevent argparse / main block from running by patching sys.argv
    saved_argv = sys.argv
    sys.argv = ['new_task.py']
    try:
        spec.loader.exec_module(mod)
    finally:
        sys.argv = saved_argv
    return mod.GENERATE_REPORT


def main():
    tmpdir = tempfile.mkdtemp(prefix='report_test_')
    print("Test task dir:", tmpdir)

    create_test_task(tmpdir)
    template = extract_template()

    # Write report script
    report_script = os.path.join(tmpdir, 'step3_report', 'generate_report.py')
    with open(report_script, 'w', encoding='utf-8') as f:
        f.write(template)

    # Run it
    saved_cwd = os.getcwd()
    os.chdir(os.path.join(tmpdir, 'step3_report'))
    try:
        code = compile(open(report_script, 'r', encoding='utf-8').read(),
                       report_script, 'exec')
        exec_globals = {'__file__': report_script, '__name__': '__main__'}
        exec(code, exec_globals)
    finally:
        os.chdir(saved_cwd)

    # Check outputs exist
    html_path = os.path.join(tmpdir, 'step3_report', 'Report.html')
    docx_path = os.path.join(tmpdir, 'step3_report', 'Report.docx')

    errors = []
    if not os.path.exists(html_path):
        errors.append("Report.html not created")
    if not os.path.exists(docx_path):
        errors.append("Report.docx not created")

    if os.path.exists(html_path):
        with open(html_path, 'r', encoding='utf-8') as f:
            html = f.read()

        # Check new sections are rendered
        checks = {
            # Auto-generated executive summary
            'Auto exec summary - approach': 'SRK EOS',
            'Auto exec summary - key result': 'Outlet T',
            'Auto exec summary - uncertainty': 'P50',
            'Auto exec summary - benchmark count': 'benchmark comparisons',
            'Auto exec summary - risk level': 'Overall project risk',
            # Auto-generated problem description
            'Auto problem desc - objective': 'thermal performance',
            'Auto problem desc - operating envelope': 'Operating envelope',
            # Discussion with numbering
            'Results Discussion section': 'Results Discussion',
            'discussion-block class': 'discussion-block',
            'discussion numbering': 'Discussion 1:',
            'observation text': 'Max temperature is 25.3',
            'mechanism text': 'JT cooling',
            'recommendation text': 'Proceed with current design',
            'second discussion': 'Pressure Drop Analysis',
            'insight question ref': 'Q1',
            'recommendation summary': 'Summary of Recommendations',
            # Benchmark
            'Benchmark section': 'Benchmark Validation',
            'benchmark-table class': 'benchmark-table',
            'NIST source': 'NIST Reference',
            'PASS badge': 'PASS',
            'FAIL badge': 'FAIL',
            # Uncertainty
            'Uncertainty section': 'Uncertainty Analysis',
            'P10 value': 'P10',
            'P50 value': 'P50',
            'tornado table': 'Sensitivity Ranking',
            # Risk
            'Risk section': 'Risk Evaluation',
            'risk-high class': 'risk-high',
            'risk-low class': 'risk-low',
            'ISO 31000': 'ISO 31000',
        }
        for desc, needle in checks.items():
            if needle not in html:
                errors.append("MISSING in HTML: {} ('{}')".format(desc, needle))
            else:
                print("  OK: {}".format(desc))

    # ---- Consistency checker tests ----
    print("\n  ---- Consistency Checker Tests ----")

    # Extract check_report_consistency from the generated report script
    # by executing the template and pulling the function from its namespace
    saved_cwd2 = os.getcwd()
    os.chdir(os.path.join(tmpdir, 'step3_report'))
    try:
        with open(report_script, 'r', encoding='utf-8') as f:
            src = f.read()
        # Compile and exec just the function definitions (stop before __main__)
        # We need to exec in a namespace that has the imports and helpers
        ns = {'__file__': report_script, '__name__': '_test_ns_'}
        exec(compile(src, report_script, 'exec'), ns)
        check_fn = ns['check_report_consistency']
    finally:
        os.chdir(saved_cwd2)

    # Test 1: Our mock data has "safe operation" + benchmark FAIL => should flag ERROR
    with open(os.path.join(tmpdir, 'results.json'), 'r') as f:
        test_results = json.load(f)

    issues = check_fn(test_results)
    severities = [i["severity"] for i in issues]
    if 'ERROR' in severities:
        print("  OK: Consistency checker catches benchmark-vs-conclusions contradiction")
    else:
        errors.append("Consistency checker should flag ERROR for 'safe operation' + benchmark FAIL")

    # Test 2: Fix conclusions to acknowledge the failure, then re-check
    test_results_fixed = dict(test_results)
    test_results_fixed['conclusions'] = (
        'The analysis shows acceptable thermal performance. '
        'However, pressure drop deviation exceeds tolerance and requires '
        'further investigation.')
    issues_fixed = check_fn(test_results_fixed)
    fixed_errors = [i for i in issues_fixed if i["severity"] == 'ERROR']
    # Only text-type errors should be resolved; calculation-type may persist
    fixed_text_errors = [i for i in fixed_errors if i.get("fix_type") == "text"]
    if not fixed_text_errors:
        print("  OK: Fixed conclusions pass text consistency check (no text ERRORs)")
    else:
        errors.append(
            "Fixed conclusions should not have text ERRORs but got: {}".format(
                fixed_text_errors[0]["message"]))

    # Test 3: None results should return INFO, not crash
    issues_none = check_fn(None)
    if issues_none and issues_none[0]["severity"] == 'INFO':
        print("  OK: None results returns INFO gracefully")
    else:
        errors.append("check_report_consistency(None) should return INFO")

    # Test 4: High prob_negative + positive conclusions => WARNING
    test_results_risky = dict(test_results)
    test_results_risky['uncertainty'] = dict(test_results.get('uncertainty', {}))
    test_results_risky['uncertainty']['prob_negative_pct'] = 45.0
    test_results_risky['conclusions'] = 'The analysis confirms safe and favourable conditions.'
    test_results_risky['benchmark_validation'] = {}  # Remove benchmark contradiction
    issues_risky = check_fn(test_results_risky)
    risky_warnings = [i for i in issues_risky if i["severity"] == 'WARNING'
                      and ('unfavourable' in i["message"].lower() or '45' in i["message"])]
    if risky_warnings:
        print("  OK: High prob_negative + positive conclusions flagged")
    else:
        errors.append("Should flag WARNING for 45% negative probability + positive conclusions")

    # Test 5: Clean results should have no errors
    clean_results = {
        'key_results': {'temp_C': 25.0},
        'approach': 'Used SRK EOS.',
        'conclusions': 'Results are within acceptable ranges.',
        'validation': {'mass_balance_pct': 0.01},
    }
    issues_clean = check_fn(clean_results)
    clean_errors = [i for i in issues_clean if i["severity"] == 'ERROR']
    if not clean_errors:
        print("  OK: Clean results produce no ERRORs")
    else:
        errors.append("Clean results should not produce ERRORs: {}".format(
            clean_errors[0]["message"]))

    # Test 6: fix_type classification — benchmark >20% deviation => "calculation" fix
    test_results_calc = dict(test_results)
    test_results_calc['benchmark_validation'] = {
        'tests': [
            {'parameter': 'density', 'pass': False, 'deviation_pct': 25.0},
        ]
    }
    test_results_calc['conclusions'] = 'The model is confirmed safe.'
    issues_calc = check_fn(test_results_calc)
    calc_fixes = [i for i in issues_calc if i.get("fix_type") == "calculation"]
    text_fixes = [i for i in issues_calc if i.get("fix_type") == "text"]
    if calc_fixes:
        print("  OK: Benchmark >20% deviation classified as calculation fix")
    else:
        errors.append("Benchmark >20% deviation should be classified as calculation fix")
    if text_fixes:
        print("  OK: Optimistic conclusions classified as text fix")
    else:
        errors.append("Optimistic conclusions with failed benchmark should be text fix")

    if errors:
        print("\nFAILURES:")
        for e in errors:
            print("  FAIL:", e)
        sys.exit(1)
    else:
        print("\nAll checks passed!")
        print("HTML:", html_path)
        print("DOCX:", docx_path)


if __name__ == '__main__':
    main()
