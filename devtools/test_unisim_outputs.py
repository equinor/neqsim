"""Test all UniSimToNeqSim output modes with a synthetic model.

No COM / UniSim installation needed — constructs test data in-memory.
"""
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))

from unisim_reader import (
    UniSimModel, UniSimFluidPackage, UniSimComponent,
    UniSimFlowsheet, UniSimStreamData, UniSimOperation,
    UniSimToNeqSim,
)


def _build_test_model():
    """Create a synthetic gas processing plant model."""
    return UniSimModel(
        file_path=r"C:\test\GasPlant.usc",
        file_name="GasPlant.usc",
        fluid_packages=[
            UniSimFluidPackage(
                name="Basis-1",
                property_package="Peng-Robinson",
                components=[
                    UniSimComponent("Methane", 0),
                    UniSimComponent("Ethane", 1),
                    UniSimComponent("Propane", 2),
                    UniSimComponent("n-Butane", 3),
                    UniSimComponent("CO2", 4),
                    UniSimComponent("Nitrogen", 5),
                ],
            )
        ],
        flowsheet=UniSimFlowsheet(
            name="Main",
            material_streams=[
                UniSimStreamData(
                    "Feed Gas", temperature_C=35.0, pressure_bara=85.0,
                    mass_flow_kgh=50000.0,
                    composition={
                        "Methane": 0.80, "Ethane": 0.08, "Propane": 0.05,
                        "n-Butane": 0.02, "CO2": 0.03, "Nitrogen": 0.02,
                    },
                ),
                UniSimStreamData("Cooled Feed", temperature_C=15.0, pressure_bara=84.5),
                UniSimStreamData("Sep Gas", temperature_C=15.0, pressure_bara=84.0),
                UniSimStreamData("Sep Liquid", temperature_C=15.0, pressure_bara=84.0),
                UniSimStreamData("MP Gas", temperature_C=15.0, pressure_bara=30.0),
                UniSimStreamData("MP Oil", temperature_C=15.0, pressure_bara=30.0),
                UniSimStreamData("MP Water", temperature_C=15.0, pressure_bara=30.0),
                UniSimStreamData("Scrubber Gas", temperature_C=14.0, pressure_bara=83.0),
                UniSimStreamData("Scrubber Liq", temperature_C=14.0, pressure_bara=83.0),
                UniSimStreamData("Compressed Gas", temperature_C=95.0, pressure_bara=150.0),
                UniSimStreamData("Export Gas", temperature_C=40.0, pressure_bara=149.0),
                UniSimStreamData("Dry Export Gas", temperature_C=40.0, pressure_bara=148.5),
                UniSimStreamData("Export Condensate", temperature_C=40.0, pressure_bara=148.5),
                UniSimStreamData("Recycled Condensate", temperature_C=40.0, pressure_bara=148.5),
                UniSimStreamData("Mixed Feed", temperature_C=35.0, pressure_bara=85.0),
            ],
            operations=[
                UniSimOperation(
                    "Inlet Cooler", "coolerop",
                    feeds=["Mixed Feed"], products=["Cooled Feed"],
                    properties={"outlet_temperature_C": 15.0},
                ),
                UniSimOperation(
                    "HP Separator", "flashtank",
                    feeds=["Cooled Feed"], products=["Sep Gas", "Sep Liquid"],
                ),
                UniSimOperation(
                    "MP Separator", "sep3op",
                    feeds=["Sep Liquid"],
                    products=["MP Gas", "MP Oil", "MP Water"],
                    properties={
                        "entrainment": [
                            {"value": 0.084, "specType": "volume",
                             "specifiedStream": "product",
                             "phaseFrom": "aqueous", "phaseTo": "oil"},
                            {"value": 0.002, "specType": "volume",
                             "specifiedStream": "product",
                             "phaseFrom": "oil", "phaseTo": "aqueous"},
                        ],
                    },
                ),
                UniSimOperation(
                    "Inlet Scrubber", "flashtank",
                    feeds=["Sep Gas"], products=["Scrubber Gas", "Scrubber Liq"],
                    properties={"detected_vertical": True},
                ),
                UniSimOperation(
                    "Export Compressor", "compressor",
                    feeds=["Scrubber Gas"], products=["Compressed Gas"],
                    properties={"outlet_pressure_bara": 150.0,
                                "adiabatic_efficiency": 0.75},
                ),
                UniSimOperation(
                    "Aftercooler", "coolerop",
                    feeds=["Compressed Gas"], products=["Export Gas"],
                    properties={"outlet_temperature_C": 40.0},
                ),
                # Aftercooler produces condensate that recycles back
                UniSimOperation(
                    "Export Flash", "flashtank",
                    feeds=["Export Gas"],
                    products=["Dry Export Gas", "Export Condensate"],
                ),
                UniSimOperation(
                    "Condensate Recycle", "recycle",
                    feeds=["Export Condensate"],
                    products=["Recycled Condensate"],
                    properties={"tolerance": 1e-2},
                ),
                UniSimOperation(
                    "Feed Mixer", "mixerop",
                    feeds=["Feed Gas", "Recycled Condensate"],
                    products=["Mixed Feed"],
                ),
            ],
        ),
    )


def test_to_python():
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    py_code = converter.to_python()
    n = len(py_code.splitlines())
    print(f"  to_python(): {n} lines")
    assert "ProcessSystem" in py_code
    assert "process.run()" in py_code
    assert "addComponent" in py_code
    assert "setMixingRule" in py_code
    # Check equipment appears
    assert "Inlet Cooler" in py_code or "inlet_cooler" in py_code
    assert "HP Separator" in py_code or "hp_separator" in py_code
    assert "MP Separator" in py_code or "mp_separator" in py_code
    # Check three-phase separator type used
    assert "ThreePhaseSeparator" in py_code
    # Check vertical separator maps to GasScrubber
    assert "GasScrubber" in py_code
    assert "Inlet Scrubber" in py_code or "inlet_scrubber" in py_code
    # Check entrainment is set
    assert "setEntrainment" in py_code
    print("  PASS")
    return py_code


def test_to_notebook():
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    nb = converter.to_notebook()
    assert nb["nbformat"] == 4
    code_cells = [c for c in nb["cells"] if c["cell_type"] == "code"]
    md_cells = [c for c in nb["cells"] if c["cell_type"] == "markdown"]
    total = len(nb["cells"])
    print(f"  to_notebook(): {total} cells ({len(code_cells)} code, {len(md_cells)} markdown)")
    assert len(code_cells) >= 4  # imports, fluid, feeds, run
    assert len(md_cells) >= 3  # title, fluid, process
    # Check markdown has equipment descriptions
    all_md = " ".join(
        " ".join(c["source"]) if isinstance(c["source"], list) else c["source"]
        for c in md_cells
    )
    assert "HP Separator" in all_md
    assert "Compressor" in all_md or "Export Compressor" in all_md
    print("  PASS")
    return nb


def test_save_notebook():
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    fd, tmp = tempfile.mkstemp(suffix=".ipynb")
    os.close(fd)
    try:
        converter.save_notebook(tmp)
        with open(tmp) as f:
            loaded = json.load(f)
        assert loaded["nbformat"] == 4
        assert len(loaded["cells"]) > 0
        print(f"  save_notebook(): wrote {os.path.getsize(tmp)} bytes")
        print("  PASS")
    finally:
        os.unlink(tmp)


def test_to_eot_simulator():
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    eot_code = converter.to_eot_simulator(class_name="GasPlantSim")
    n = len(eot_code.splitlines())
    print(f"  to_eot_simulator(): {n} lines")
    assert "class GasPlantSim(BaseSimulator)" in eot_code
    assert "def build_process(self)" in eot_code
    assert "get_stream" in eot_code
    # Should use eot factories for known types
    assert "get_cooler" in eot_code
    assert "get_compressor" in eot_code
    assert "get_separator" in eot_code
    print("  PASS")
    return eot_code


def test_save_eot_simulator():
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    fd, tmp = tempfile.mkstemp(suffix=".py")
    os.close(fd)
    try:
        converter.save_eot_simulator(tmp, class_name="GasPlantSim")
        with open(tmp) as f:
            content = f.read()
        assert "class GasPlantSim" in content
        print(f"  save_eot_simulator(): wrote {os.path.getsize(tmp)} bytes")
        print("  PASS")
    finally:
        os.unlink(tmp)


def test_to_eot_notebook():
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    nb = converter.to_eot_notebook(class_name="GasPlantSim")
    assert nb["nbformat"] == 4
    total = len(nb["cells"])
    code_cells = [c for c in nb["cells"] if c["cell_type"] == "code"]
    md_cells = [c for c in nb["cells"] if c["cell_type"] == "markdown"]
    print(f"  to_eot_notebook(): {total} cells ({len(code_cells)} code, {len(md_cells)} markdown)")
    assert total > 3
    # Should contain the simulator class definition
    all_code = " ".join(
        " ".join(c["source"]) if isinstance(c["source"], list) else c["source"]
        for c in code_cells
    )
    assert "GasPlantSim" in all_code
    print("  PASS")


def test_code_consistency():
    """Verify Python and Notebook produce identical process logic."""
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    py_code = converter.to_python()
    nb = converter.to_notebook()

    # Extract all code from notebook
    nb_code_parts = []
    for c in nb["cells"]:
        if c["cell_type"] != "code":
            continue
        src = c["source"]
        if isinstance(src, list):
            nb_code_parts.extend(src)
        else:
            nb_code_parts.append(src)
    nb_code = "\n".join(nb_code_parts)

    # Key functional fragments must appear in both
    fragments = [
        'addComponent("methane"',
        "setMixingRule",
        "process.run()",
        "process.add(",
        "setFlowRate",
        "setTemperature",
    ]
    all_ok = True
    for frag in fragments:
        py_has = frag in py_code
        nb_has = frag in nb_code
        status = "OK" if py_has == nb_has else "MISMATCH"
        print(f"  {frag:45s}  py={py_has!s:5s}  nb={nb_has!s:5s}  [{status}]")
        if py_has != nb_has:
            all_ok = False
    assert all_ok, "Python/Notebook code mismatch"
    print("  PASS")


def test_to_json():
    """Verify JSON output has correct types, ports, and entrainment."""
    model = _build_test_model()
    converter = UniSimToNeqSim(model)
    result = converter.to_json()
    process = result['process']
    print(f"  to_json(): {len(process)} process entries")

    # Check fluid section
    assert 'fluid' in result
    assert result['fluid']['model'] in ('SRK', 'PR')
    assert 'methane' in result['fluid']['components']

    # Collect types from process array
    types_by_name = {e['name']: e['type'] for e in process if 'name' in e}

    # Vertical separator should be GasScrubber
    assert types_by_name.get('Inlet Scrubber') == 'GasScrubber', \
        f"Expected GasScrubber, got {types_by_name.get('Inlet Scrubber')}"

    # 3-phase separator should be ThreePhaseSeparator
    assert types_by_name.get('MP Separator') == 'ThreePhaseSeparator'

    # HP Separator should be plain Separator
    assert types_by_name.get('HP Separator') == 'Separator'

    # Check entrainment property on MP Separator
    mp_entry = next(e for e in process if e.get('name') == 'MP Separator')
    assert 'properties' in mp_entry
    assert 'entrainment' in mp_entry['properties']
    ent = mp_entry['properties']['entrainment']
    assert len(ent) == 2
    assert ent[0]['phaseFrom'] == 'aqueous'
    assert ent[0]['phaseTo'] == 'oil'

    # Check inlet references use dot-notation for separator ports
    comp_entry = next(e for e in process if e.get('name') == 'Export Compressor')
    assert 'inlet' in comp_entry
    # Should reference Inlet Scrubber's gas port
    assert 'Inlet Scrubber.gasOut' in comp_entry['inlet']

    # --- Recycle loop assertions ---

    # Recycle entry should exist with correct type and tolerance
    assert types_by_name.get('Condensate Recycle') == 'Recycle', \
        f"Expected Recycle, got {types_by_name.get('Condensate Recycle')}"
    rcy_entry = next(e for e in process if e.get('name') == 'Condensate Recycle')
    assert 'inlet' in rcy_entry
    # Recycle inlet should reference Export Flash liquid port
    assert 'Export Flash.liquidOut' in rcy_entry['inlet'], \
        f"Expected Export Flash.liquidOut, got {rcy_entry['inlet']}"
    # Recycle should have tolerance property
    assert 'properties' in rcy_entry
    assert rcy_entry['properties'].get('tolerance') == 1e-2

    # Feed Mixer should exist and reference both Feed Gas and Recycle outlet
    mixer_entry = next(e for e in process if e.get('name') == 'Feed Mixer')
    assert mixer_entry['type'] == 'Mixer'
    assert 'inlets' in mixer_entry
    # One inlet should be the external feed, other should be the recycle outlet
    inlet_refs = mixer_entry['inlets']
    assert len(inlet_refs) == 2
    has_recycle_ref = any('Condensate Recycle' in ref for ref in inlet_refs)
    assert has_recycle_ref, \
        f"Mixer should reference Condensate Recycle outlet, got: {inlet_refs}"

    # Export Flash should be a Separator (2-phase flashtank)
    assert types_by_name.get('Export Flash') == 'Separator'

    print("  PASS")
    return result


if __name__ == "__main__":
    tests = [
        ("to_python", test_to_python),
        ("to_notebook", test_to_notebook),
        ("save_notebook", test_save_notebook),
        ("to_eot_simulator", test_to_eot_simulator),
        ("save_eot_simulator", test_save_eot_simulator),
        ("to_eot_notebook", test_to_eot_notebook),
        ("code_consistency", test_code_consistency),
        ("to_json", test_to_json),
    ]
    passed = 0
    failed = 0
    for name, fn in tests:
        print(f"\n--- {name} ---")
        try:
            fn()
            passed += 1
        except Exception as e:
            print(f"  FAIL: {e}")
            import traceback
            traceback.print_exc()
            failed += 1
    print(f"\n{'='*50}")
    print(f"Results: {passed} passed, {failed} failed out of {len(tests)}")
    if failed:
        sys.exit(1)
    print("ALL TESTS PASSED")
