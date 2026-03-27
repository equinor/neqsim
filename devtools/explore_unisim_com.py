r"""
Explore UniSim Design COM API to map out what data can be extracted.
Opens a .usc file and dumps the full object model to understand:
  - BasisManager / FluidPackages / Components
  - Flowsheet / Operations (unit operations)
  - MaterialStreams / EnergyStreams
  - SubFlowsheets
  - Stream properties (T, P, flow, composition, density, etc.)
  - Operation properties (types, connections, specs)

Usage:
    python devtools/explore_unisim_com.py <path_to_usc_file>

If no path given, uses a default Grane file.
"""
import sys
import os
import json
import time
import traceback

try:
    import win32com.client
except ImportError:
    print("ERROR: pywin32 not installed. Run: pip install pywin32")
    sys.exit(1)


def safe_get(obj, attr, default="<unavailable>"):
    """Safely get an attribute from a COM object."""
    try:
        val = getattr(obj, attr)
        if callable(val):
            val = val()
        return val
    except Exception:
        return default


def safe_getvalue(prop, unit=None, default=None):
    """Safely get value from a UniSim property object."""
    try:
        if unit:
            return prop.GetValue(unit)
        else:
            return prop.GetValue()
    except Exception:
        return default


def explore_components(basis):
    """Extract component list from BasisManager."""
    result = []
    try:
        n_fp = basis.FluidPackages.Count
        print(f"\n=== FLUID PACKAGES ({n_fp}) ===")
        for i in range(n_fp):
            fp = basis.FluidPackages.Item(i)
            fp_name = safe_get(fp, 'name', f'FP_{i}')
            pp_name = safe_get(fp, 'PropertyPackageName', '<unknown>')
            n_comp = fp.Components.Count
            print(f"\n  Fluid Package: '{fp_name}'")
            print(f"  Property Package: {pp_name}")
            print(f"  Components ({n_comp}):")

            components = []
            for j in range(n_comp):
                comp = fp.Components.Item(j)
                comp_name = safe_get(comp, 'name', f'Comp_{j}')
                components.append(comp_name)
                print(f"    [{j}] {comp_name}")

            result.append({
                'name': fp_name,
                'property_package': pp_name,
                'components': components
            })
    except Exception as e:
        print(f"  ERROR exploring components: {e}")
    return result


def explore_stream(stream, comp_names):
    """Extract all properties from a material stream."""
    info = {
        'name': safe_get(stream, 'name', '<unnamed>'),
        'type': 'MaterialStream',
    }

    # Basic properties
    info['temperature_C'] = safe_getvalue(stream.Temperature, 'C')
    info['temperature_K'] = safe_getvalue(stream.Temperature, 'K')
    info['pressure_kPa'] = safe_getvalue(stream.Pressure, 'kPa')
    info['pressure_bara'] = safe_getvalue(stream.Pressure, 'bar')
    info['molar_flow_kgmolh'] = safe_getvalue(stream.MolarFlow, 'kgmole/h')
    info['mass_flow_kgh'] = safe_getvalue(stream.MassFlow, 'kg/h')
    info['std_gas_flow'] = safe_getvalue(stream.StdGasFlow, 'STD_m3/h') if hasattr(stream, 'StdGasFlow') else None
    info['mass_density_kgm3'] = safe_getvalue(stream.MassDensity, 'kg/m3')
    info['molecular_weight'] = safe_getvalue(stream.MolecularWeight, None)
    info['vapour_fraction'] = safe_getvalue(stream.VapourFraction, None)
    info['specific_heat_kJkgC'] = safe_getvalue(stream.SpecificHeat, 'kJ/kg-C') if hasattr(stream, 'SpecificHeat') else None
    info['viscosity_cP'] = safe_getvalue(stream.Viscosity, 'cP') if hasattr(stream, 'Viscosity') else None
    info['enthalpy_kJkg'] = safe_getvalue(stream.MassEnthalpy, 'kJ/kg') if hasattr(stream, 'MassEnthalpy') else None
    info['entropy'] = safe_getvalue(stream.MassEntropy, 'kJ/kg-C') if hasattr(stream, 'MassEntropy') else None

    # Composition
    try:
        comp_fracs = stream.ComponentMolarFraction.GetValues()
        if comp_fracs and comp_names:
            info['composition'] = {}
            for k, name in enumerate(comp_names):
                if k < len(comp_fracs) and comp_fracs[k] is not None:
                    info['composition'][name] = float(comp_fracs[k])
    except Exception:
        info['composition'] = None

    # Phase info
    try:
        n_phases = stream.NumberOfPhases
        info['n_phases'] = n_phases
    except Exception:
        info['n_phases'] = None

    return info


def explore_operation(op):
    """Extract type and key properties from a unit operation."""
    info = {
        'name': safe_get(op, 'name', '<unnamed>'),
    }

    # Get the TypeName which tells us what kind of operation
    info['type_name'] = safe_get(op, 'TypeName', '<unknown>')

    # Try to get attached streams (feeds and products)
    try:
        feeds = []
        if hasattr(op, 'Feeds'):
            for i in range(op.Feeds.Count):
                feeds.append(safe_get(op.Feeds.Item(i), 'name', f'feed_{i}'))
        info['feeds'] = feeds
    except Exception:
        info['feeds'] = []

    try:
        products = []
        if hasattr(op, 'Products'):
            for i in range(op.Products.Count):
                products.append(safe_get(op.Products.Item(i), 'name', f'prod_{i}'))
        info['products'] = products
    except Exception:
        info['products'] = []

    # Try to get energy streams
    try:
        if hasattr(op, 'EnergyFeeds'):
            energy_feeds = []
            for i in range(op.EnergyFeeds.Count):
                energy_feeds.append(safe_get(op.EnergyFeeds.Item(i), 'name', f'efeed_{i}'))
            info['energy_feeds'] = energy_feeds
    except Exception:
        pass

    try:
        if hasattr(op, 'EnergyProducts'):
            energy_products = []
            for i in range(op.EnergyProducts.Count):
                energy_products.append(safe_get(op.EnergyProducts.Item(i), 'name', f'eprod_{i}'))
            info['energy_products'] = energy_products
    except Exception:
        pass

    # Type-specific properties
    type_name = info['type_name']

    if 'compressor' in type_name.lower():
        info['duty_kW'] = safe_getvalue(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None
        info['efficiency'] = safe_getvalue(op.AdiabaticEfficiency, None) if hasattr(op, 'AdiabaticEfficiency') else None
        info['polytropic_efficiency'] = safe_getvalue(op.PolytropicEfficiency, None) if hasattr(op, 'PolytropicEfficiency') else None

    elif 'separator' in type_name.lower() or 'flash' in type_name.lower():
        pass  # Separator mainly has feed/product connections

    elif 'cooler' in type_name.lower() or 'heater' in type_name.lower():
        info['duty_kW'] = safe_getvalue(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None

    elif 'valve' in type_name.lower():
        info['pressure_drop_kPa'] = safe_getvalue(op.PressureDrop, 'kPa') if hasattr(op, 'PressureDrop') else None

    elif 'mixer' in type_name.lower():
        pass

    elif 'tee' in type_name.lower() or 'splitter' in type_name.lower():
        pass

    elif 'pump' in type_name.lower():
        info['duty_kW'] = safe_getvalue(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None
        info['efficiency'] = safe_getvalue(op.AdiabaticEfficiency, None) if hasattr(op, 'AdiabaticEfficiency') else None

    elif 'heat exchanger' in type_name.lower() or 'lNG' in type_name.lower():
        info['duty_kW'] = safe_getvalue(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None

    return info


def explore_flowsheet(fs, comp_names, indent=""):
    """Recursively explore a flowsheet and all sub-flowsheets."""
    result = {
        'name': safe_get(fs, 'name', '<main>'),
        'operations': [],
        'material_streams': [],
        'energy_streams': [],
        'sub_flowsheets': [],
    }

    # Material Streams
    try:
        n_ms = fs.MaterialStreams.Count
        print(f"{indent}  Material Streams: {n_ms}")
        for i in range(n_ms):
            ms = fs.MaterialStreams.Item(i)
            ms_info = explore_stream(ms, comp_names)
            result['material_streams'].append(ms_info)
            print(f"{indent}    [{i}] {ms_info['name']:40s} T={ms_info.get('temperature_C','?'):>8s} C  "
                  f"P={ms_info.get('pressure_bara','?'):>8s} bara  "
                  f"F={ms_info.get('mass_flow_kgh','?'):>10s} kg/h"
                  if isinstance(ms_info.get('temperature_C'), (int, float))
                  else f"{indent}    [{i}] {ms_info['name']}")
    except Exception as e:
        print(f"{indent}  ERROR reading material streams: {e}")

    # Energy Streams
    try:
        n_es = fs.EnergyStreams.Count
        print(f"{indent}  Energy Streams: {n_es}")
        for i in range(n_es):
            es = fs.EnergyStreams.Item(i)
            es_info = {
                'name': safe_get(es, 'name', f'E_{i}'),
                'type': 'EnergyStream',
            }
            es_info['heat_flow_kW'] = safe_getvalue(es.HeatFlow, 'kW') if hasattr(es, 'HeatFlow') else None
            result['energy_streams'].append(es_info)
    except Exception as e:
        print(f"{indent}  ERROR reading energy streams: {e}")

    # Operations (Unit Operations)
    try:
        n_ops = fs.Operations.Count
        print(f"{indent}  Operations: {n_ops}")
        for i in range(n_ops):
            op = fs.Operations.Item(i)
            op_info = explore_operation(op)
            result['operations'].append(op_info)
            feeds_str = ', '.join(op_info.get('feeds', []))
            prods_str = ', '.join(op_info.get('products', []))
            print(f"{indent}    [{i}] {op_info['type_name']:20s} '{op_info['name']}'"
                  f"  feeds=[{feeds_str}]  products=[{prods_str}]")
    except Exception as e:
        print(f"{indent}  ERROR reading operations: {e}")

    # Sub-Flowsheets
    try:
        if hasattr(fs, 'Flowsheets') and fs.Flowsheets.Count > 0:
            n_sub = fs.Flowsheets.Count
            print(f"{indent}  Sub-Flowsheets: {n_sub}")
            for i in range(n_sub):
                sub = fs.Flowsheets.Item(i)
                sub_name = safe_get(sub, 'name', f'Sub_{i}')
                print(f"{indent}    === Sub-Flowsheet: '{sub_name}' ===")
                sub_info = explore_flowsheet(sub, comp_names, indent + "    ")
                result['sub_flowsheets'].append(sub_info)
    except Exception as e:
        print(f"{indent}  (No sub-flowsheets or error: {e})")

    return result


def main():
    if len(sys.argv) > 1:
        usc_path = sys.argv[1]
    else:
        usc_path = (r"C:\Users\ESOL\OneDrive - Equinor\Hunting production optimisation"
                     r" - Case 1 - Oseberg-Grane-Sture\Grane\Unisim files\2025-09-12"
                     r"\Case02_2031_NorOP_GraneGBE_PF_WithoutCurve.usc")

    if not os.path.exists(usc_path):
        print(f"ERROR: File not found: {usc_path}")
        sys.exit(1)

    print(f"=== UniSim COM API Explorer ===")
    print(f"File: {usc_path}\n")

    # Start UniSim
    print("Starting UniSim Design...")
    app = win32com.client.dynamic.Dispatch('UnisimDesign.Application')
    app.Visible = True
    time.sleep(2)

    print("Opening case...")
    case = app.SimulationCases.Open(usc_path)
    time.sleep(3)

    # Pause solver so we can read without it recalculating
    solver = case.Solver
    solver.CanSolve = False

    # Explore BasisManager
    basis = case.BasisManager
    fluid_packages = explore_components(basis)

    # Get component names from first fluid package
    comp_names = fluid_packages[0]['components'] if fluid_packages else []

    # Explore Main Flowsheet
    fs = case.Flowsheet
    print(f"\n=== MAIN FLOWSHEET ===")
    flowsheet_data = explore_flowsheet(fs, comp_names)

    # Assemble full report
    report = {
        'file': os.path.basename(usc_path),
        'fluid_packages': fluid_packages,
        'flowsheet': flowsheet_data,
    }

    # Save JSON report
    out_path = os.path.splitext(usc_path)[0] + '_extracted.json'
    # Save next to the script if the original path is not writable
    try:
        with open(out_path, 'w') as f:
            json.dump(report, f, indent=2, default=str)
        print(f"\nSaved extraction report to: {out_path}")
    except Exception:
        out_path = os.path.join(os.path.dirname(__file__), 'unisim_extracted.json')
        with open(out_path, 'w') as f:
            json.dump(report, f, indent=2, default=str)
        print(f"\nSaved extraction report to: {out_path}")

    # Print summary
    n_streams = len(flowsheet_data['material_streams'])
    n_ops = len(flowsheet_data['operations'])
    n_subs = len(flowsheet_data['sub_flowsheets'])
    print(f"\n=== SUMMARY ===")
    print(f"  Fluid Packages: {len(fluid_packages)}")
    print(f"  Components: {len(comp_names)}")
    print(f"  Material Streams: {n_streams}")
    print(f"  Operations: {n_ops}")
    print(f"  Sub-Flowsheets: {n_subs}")

    # Close
    print("\nClosing UniSim...")
    case.Close()
    app.Quit()
    print("Done!")


if __name__ == "__main__":
    main()
