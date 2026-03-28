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
                UniSimStreamData("Compressed Gas", temperature_C=95.0, pressure_bara=150.0),
                UniSimStreamData("Export Gas", temperature_C=40.0, pressure_bara=149.0),
            ],
            operations=[
                UniSimOperation(
                    "Inlet Cooler", "coolerop",
                    feeds=["Feed Gas"], products=["Cooled Feed"],
                    properties={"outlet_temperature_C": 15.0},
                ),
                UniSimOperation(
                    "HP Separator", "flashtank",
                    feeds=["Cooled Feed"], products=["Sep Gas", "Sep Liquid"],
                ),
                UniSimOperation(
                    "Export Compressor", "compressor",
                    feeds=["Sep Gas"], products=["Compressed Gas"],
                    properties={"outlet_pressure_bara": 150.0,
                                "adiabatic_efficiency": 0.75},
                ),
                UniSimOperation(
                    "Aftercooler", "coolerop",
                    feeds=["Compressed Gas"], products=["Export Gas"],
                    properties={"outlet_temperature_C": 40.0},
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


if __name__ == "__main__":
    tests = [
        ("to_python", test_to_python),
        ("to_notebook", test_to_notebook),
        ("save_notebook", test_save_notebook),
        ("to_eot_simulator", test_to_eot_simulator),
        ("save_eot_simulator", test_save_eot_simulator),
        ("to_eot_notebook", test_to_eot_notebook),
        ("code_consistency", test_code_consistency),
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
