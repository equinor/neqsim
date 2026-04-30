"""Test all UniSimToNeqSim output modes with a synthetic model.

No COM / UniSim installation needed — constructs test data in-memory.
"""
import json
import math
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))

from unisim_reader import (
    UniSimModel, UniSimFluidPackage, UniSimComponent,
    UniSimFlowsheet, UniSimStreamData, UniSimOperation,
    UniSimReader, UniSimToNeqSim,
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
                # Adjuster: adjust compressor outlet pressure to hit
                # a target temperature on the export gas stream
                UniSimOperation(
                    "Temp Adjuster", "adjust",
                    feeds=[], products=[],
                    properties={
                        "adjusted_object_name": "Export Compressor",
                        "adjusted_variable": "pressure",
                        "target_object_name": "Aftercooler",
                        "target_variable": "temperature",
                        "target_value": 313.15,
                        "tolerance": 0.5,
                    },
                ),
            ],
        ),
    )


def _component(name, index, tc, pc, omega, mw, tboil=None, vcrit=None,
               volume_shift=None, parachor=None):
    """Create a synthetic UniSim component with E300 properties."""
    return UniSimComponent(
        name=name,
        index=index,
        is_hypothetical=name.endswith('*'),
        tc_K=tc,
        pc_bara=pc,
        acentric_factor=omega,
        mw=mw,
        tboil_K=tboil,
        vcrit_m3_kgmol=vcrit,
        volume_shift=volume_shift,
        parachor=parachor,
    )


class _FakeQuantity:
    """Small stand-in for a UniSim COM quantity object."""

    def __init__(self, values_by_unit):
        self.values_by_unit = values_by_unit

    def GetValue(self, unit=None):
        """Return the configured value for the requested unit."""
        key = unit if unit in self.values_by_unit else None
        if key in self.values_by_unit:
            return self.values_by_unit[key]
        raise AttributeError(unit)


class _FakeComponentWithoutAcentricFactor:
    """Component whose UniSim COM surface lacks AcentricFactor."""

    name = 'Methane'
    Name = 'Methane'
    CriticalTemperature = _FakeQuantity({'K': 190.56})
    CriticalPressure = _FakeQuantity({'bar': 45.99})
    MolecularWeight = _FakeQuantity({None: 16.043})
    NormalBoilingPt = _FakeQuantity({'K': 111.66})
    CriticalVolume = _FakeQuantity({'m3/kgmole': 0.099})

    def __getattr__(self, attribute_name):
        """Raise the same style of missing-property error seen in COM."""
        if attribute_name == 'AcentricFactor':
            raise AttributeError('Item.AcentricFactor')
        raise AttributeError(attribute_name)


def _build_e300_test_model(tmpdir):
    """Create a model with an exported PR-LK E300 fluid package."""
    components = [
        _component('Methane', 0, 190.56, 45.99, 0.011, 16.043, 111.66, 0.099),
        _component('Ethane', 1, 305.32, 48.72, 0.099, 30.070, 184.55, 0.148),
        _component('n-Butane', 2, 425.12, 37.96, 0.200, 58.124, 272.65, 0.255),
        _component('CO2', 3, 304.13, 73.77, 0.225, 44.010, 194.67, 0.094),
        _component('Nitrogen', 4, 126.20, 33.95, 0.037, 28.014, 77.36, 0.090),
        _component('OilPseudo*', 5, 640.0, 20.0, 0.720, 180.0, 520.0, 0.700,
                   volume_shift=0.045, parachor=240.0),
        _component('C7+', 6, 700.0, 18.0, 0.850, 230.0, 570.0, 0.850,
               volume_shift=0.055, parachor=290.0),
    ]
    bips = [[0.0 for _ in components] for _ in components]
    bips[1][0] = bips[0][1] = 0.011
    bips[3][0] = bips[0][3] = 0.095
    bips[5][0] = bips[0][5] = 0.020
    bips[6][0] = bips[0][6] = 0.025

    fluid_package = UniSimFluidPackage(
        name='PR-LK Basis',
        property_package='Peng-Robinson - LK',
        components=components,
        bips=bips,
        reference_composition=[0.70, 0.08, 0.04, 0.05, 0.03, 0.06, 0.04],
    )
    fluid_package.write_e300(os.path.join(tmpdir, 'pr_lk_basis.e300'))

    return UniSimModel(
        file_path=r'C:\test\E300GasPlant.usc',
        file_name='E300GasPlant.usc',
        fluid_packages=[fluid_package],
        flowsheet=UniSimFlowsheet(
            name='Main',
            material_streams=[
                UniSimStreamData(
                    'Feed Gas', temperature_C=42.0, pressure_bara=92.0,
                    mass_flow_kgh=25000.0,
                    composition={
                        'Methane': 0.74, 'Ethane': 0.08, 'n-Butane': 0.04,
                        'CO2': 0.05, 'Nitrogen': 0.03, 'OilPseudo*': 0.06,
                        'C7+': 0.04,
                    },
                ),
            ],
            operations=[
                UniSimOperation('Spreadsheet 1', 'spreadsheetop', feeds=['Feed Gas']),
                UniSimOperation(
                    'Balance 1', 'balanceop',
                    properties={
                        'adjusted_object_name': 'Feed Gas',
                        'adjusted_variable': 'flowRate',
                        'target_object_name': 'Feed Gas',
                        'target_variable': 'pressure',
                        'target_value': 92.0,
                    },
                ),
                UniSimOperation('Logic 1', 'logicalop'),
            ],
        ),
    )


def _build_multi_e300_test_model(tmpdir):
    """Create a model with two exported E300 fluid packages."""
    model = _build_e300_test_model(tmpdir)

    water_components = [
        _component('Water', 0, 647.10, 220.64, 0.344, 18.015, 373.15, 0.056),
        _component('Methane', 1, 190.56, 45.99, 0.011, 16.043, 111.66, 0.099),
    ]
    water_bips = [[0.0 for _ in water_components] for _ in water_components]
    water_bips[0][1] = water_bips[1][0] = 0.120

    water_package = UniSimFluidPackage(
        name='Water Service Basis',
        property_package='SRK',
        components=water_components,
        bips=water_bips,
        reference_composition=[0.95, 0.05],
    )
    water_package.write_e300(os.path.join(tmpdir, 'water_service_basis.e300'))
    model.fluid_packages.append(water_package)
    return model


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

    # --- Adjuster assertions ---

    # Adjuster entry should exist with correct type
    assert types_by_name.get('Temp Adjuster') == 'Adjuster', \
        f"Expected Adjuster, got {types_by_name.get('Temp Adjuster')}"
    adj_entry = next(e for e in process if e.get('name') == 'Temp Adjuster')
    assert 'properties' in adj_entry
    adj_props = adj_entry['properties']
    # Check adjusted variable references
    assert adj_props.get('adjustedEquipment') == 'Export Compressor'
    assert adj_props.get('adjustedVariable') == 'pressure'
    # Check target variable references
    assert adj_props.get('targetEquipment') == 'Aftercooler'
    assert adj_props.get('targetVariable') == 'temperature'
    assert adj_props.get('targetValue') == 313.15
    # Check tolerance
    assert adj_props.get('tolerance') == 0.5

    print("  PASS")
    return result


def test_missing_acentric_factor_uses_edmister_fallback():
    """Verify missing UniSim AcentricFactor does not block E300 export."""
    reader = UniSimReader()
    extracted_component = UniSimComponent('Methane', 0)
    reader._populate_component_properties(
        _FakeComponentWithoutAcentricFactor(), extracted_component)

    expected_omega = ((3.0 / 7.0)
                      * math.log10(extracted_component.pc_bara / 1.01325)
                      / (extracted_component.tc_K / extracted_component.tboil_K - 1.0)
                      - 1.0)
    assert extracted_component.tc_K == 190.56
    assert extracted_component.pc_bara == 45.99
    assert extracted_component.mw == 16.043
    assert extracted_component.acentric_factor is not None
    assert abs(extracted_component.acentric_factor - expected_omega) < 1e-12

    with tempfile.TemporaryDirectory() as tmpdir:
        fluid_package = UniSimFluidPackage(
            name='COM Missing Acentric',
            property_package='Peng-Robinson',
            components=[extracted_component],
        )
        e300_path = fluid_package.write_e300(
            os.path.join(tmpdir, 'missing_acentric.e300'))
        with open(e300_path, encoding='utf-8') as e300_file:
            e300_text = e300_file.read()

    assert 'ACF' in e300_text
    assert f'{expected_omega:.6f}' in e300_text
    print("  PASS")


def test_e300_fluid_package_export_and_usage():
    """Verify UniSim fluid packages export to E300 and are consumed as E300."""
    with tempfile.TemporaryDirectory() as tmpdir:
        model = _build_e300_test_model(tmpdir)
        fluid_package = model.fluid_packages[0]
        with open(fluid_package.e300_file_path, encoding='utf-8') as e300_file:
            e300_text = e300_file.read()

        assert 'PRLKCORR' in e300_text
        assert 'PRCORR' not in e300_text
        assert 'C1' in e300_text
        assert 'C2' in e300_text
        assert 'C4' in e300_text
        assert 'N2' in e300_text
        assert 'OilPseudo*' in e300_text
        assert 'C7plus' in e300_text
        assert 'BIC' in e300_text
        assert '0.0950' in e300_text
        assert 'PARACHOR' in e300_text
        assert 'SSHIFT' in e300_text

        converter = UniSimToNeqSim(model)
        result = converter.to_json()
        fluid = result['fluid']
        assert fluid['model'] == 'PR_LK'
        assert fluid['e300FilePath'] == fluid_package.e300_file_path
        assert fluid['componentCount'] == len(fluid_package.components)
        assert fluid['componentNames'][:5] == ['C1', 'C2', 'C4', 'CO2', 'N2']
        assert fluid['componentNames'][-1] == 'C7plus'
        assert len(result['fluidPackages']) == 1
        assert result['fluidPackages'][0]['e300FilePath'] == fluid_package.e300_file_path
        assert result['fluids'][result['fluidPackages'][0]['fluidRef']]['e300FilePath'] == \
            fluid_package.e300_file_path

        py_code = converter.to_python()
        assert 'EclipseFluidReadWrite.read' in py_code
        assert 'addComponent' not in py_code.split('# Process definition')[0]

        process = result['process']
        types_by_name = {entry['name']: entry['type'] for entry in process}
        assert types_by_name.get('Spreadsheet 1') == 'SpreadsheetBlock'
        assert types_by_name.get('Balance 1') == 'Adjuster'
        assert 'Logic 1' not in types_by_name
        spreadsheet_entry = next(e for e in process if e['name'] == 'Spreadsheet 1')
        assert 'inlet' not in spreadsheet_entry

    print("  PASS")


def test_all_fluid_packages_are_exposed_as_e300():
    """Verify every UniSim fluid package becomes a named E300 fluid."""
    with tempfile.TemporaryDirectory() as tmpdir:
        model = _build_multi_e300_test_model(tmpdir)
        converter = UniSimToNeqSim(model)
        result = converter.to_json()

        assert len(model.fluid_packages) == 2
        assert len(result['fluidPackages']) == 2
        assert len(result['fluids']) == 2
        assert result['fluid']['e300FilePath'] == model.fluid_packages[0].e300_file_path

        e300_paths = [fp.e300_file_path for fp in model.fluid_packages]
        assert len(set(e300_paths)) == len(model.fluid_packages)
        for e300_path in e300_paths:
            assert e300_path is not None
            assert os.path.exists(e300_path)

        for package_entry in result['fluidPackages']:
            fluid_ref = package_entry['fluidRef']
            assert fluid_ref in result['fluids']
            assert package_entry['e300FilePath'] == result['fluids'][fluid_ref]['e300FilePath']
            assert os.path.exists(package_entry['e300FilePath'])

        py_code = converter.to_python()
        assert py_code.count('EclipseFluidReadWrite.read') >= 2
        for e300_path in e300_paths:
            assert e300_path.replace('\\', '/') in py_code

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
        ("to_json", test_to_json),
        ("missing_acentric_factor_uses_edmister_fallback",
         test_missing_acentric_factor_uses_edmister_fallback),
        ("e300_fluid_package_export_and_usage", test_e300_fluid_package_export_and_usage),
        ("all_fluid_packages_are_exposed_as_e300", test_all_fluid_packages_are_exposed_as_e300),
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
