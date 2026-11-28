"""Assess unfitted methane hydrate dissociation against Semenov et al. (2023), Table 1."""

import argparse
import csv
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from statistics import mean

import jpype

FIXTURE = Path(__file__).resolve().parents[1] / "src/test/resources/data/hydrate/semenov_2023_charges.csv"
MOLAR_MASS = {"water": 18.01528, "methanol": 32.04186, "MEG": 62.06784}  # g/mol
CELL_M3 = 0.000600


def fluid_at_measurement(row, methane_mol):
    """Build a fresh fluid from the weighed solution and total methane inventory."""
    fluid = jpype.JClass("neqsim.thermo.system.SystemSrkCPAstatoil")(
        float(row["observed_K"]), 10 * float(row["pressure_MPa_abs"])
    )
    mass = float(row["inhibitor_g"])
    fluid.addComponent("methane", methane_mol)
    fluid.addComponent("water", float(row["water_g"]) / MOLAR_MASS["water"])
    if mass:
        fluid.addComponent(row["component"], mass / MOLAR_MASS[row["component"]])
    fluid.setMixingRule(10)
    fluid.setMultiPhaseCheck(True)
    return fluid


def reconstruct_charge(row, operations):
    """Recover methane moles from the cell volume at the measured dissociation endpoint."""
    methane = 0.6
    for _ in range(12):
        fluid = fluid_at_measurement(row, methane)
        operations(fluid).TPflash()
        volume_error = CELL_M3 - fluid.getVolume("m3")
        if abs(volume_error) < 1e-10:
            return methane
        gas = fluid.getPhase("gas")
        methane += volume_error * gas.getNumberOfMolesInPhase() / gas.getVolume("m3")
    raise ValueError("cell volume did not close")


def assess(row, methane, operations, structure=None):
    """Return calculated temperature, phase identities, partitioning and equilibrium diagnostics."""
    fluid = fluid_at_measurement(row, methane)
    fluid.setHydrateCheck(True)
    ops = operations(fluid)
    if structure is None:
        ops.hydrateFormationTemperature()
    else:
        ops.hydrateFormationTemperature(jpype.JInt(structure))
    diag = ops.getOperation().getDiagnostics()
    if not diag.isConverged():
        raise ValueError("hydrate flash did not converge")
    phases = "|".join(str(value) for value in diag.getPhaseTypes())
    aqueous = fluid.getPhase("aqueous")
    gas = fluid.getPhase("gas")
    species = row["component"]
    vapor_fraction = gas.getComponent(species).getNumberOfMolesInPhase() / gas.getComponent(species).getNumberOfmoles()
    return dict(
        calculated_K=fluid.getTemperature(),
        residual_K=fluid.getTemperature() - row["observed_K"],
        structure=fluid.getPhases()[4].getStableHydrateStructure(),
        fluid_phases=phases,
        aqueous_x=aqueous.getComponent(species).getx(),
        fraction_of_species_in_vapor=vapor_fraction,
        hydrate_residual=diag.getHydrateResidual(),
        balance_residual=diag.getComponentBalanceResidual(),
    )


def compare_lattices(row, methane, operations, result):
    """Solve independent fixed-lattice roots at the same pressure and charge."""
    for structure, column in ((1, "fixed_sI_K"), (2, "fixed_sII_K")):
        trial = assess(row, methane, operations, structure)
        if trial["structure"] != structure:
            raise ValueError(f"Requested lattice {structure} was not retained")
        result[column] = trial["calculated_K"]
    roots = (result["fixed_sI_K"], result["fixed_sII_K"])
    if abs(result["calculated_K"] - max(roots)) > 2e-4:
        raise ValueError("Automatic root differs from the warmer fixed-lattice root")
    if roots[result["structure"] - 1] < max(roots) - 2e-4:
        raise ValueError("Reported lattice does not have the warmer root")


def measurements(article):
    """Read the first two column groups of Table 1 in the publisher's JATS XML."""
    with FIXTURE.open() as stream:
        charges = {row["sample"]: row for row in csv.DictReader(stream)}
    table = ET.parse(article).find('.//table-wrap[@id="tbl0001"]')
    groups = [None, None]
    point = {}
    for tr in table.findall(".//tbody/tr"):
        cells = iter(tr.findall("td"))
        if len(tr) == 1:  # horizontal separator
            continue
        for column in range(2):
            cell = next(cells)
            label = "".join(cell.itertext()).strip()
            if label in charges or not label:
                groups[column] = label
                cell = next(cells)
            temp = "".join(cell.itertext()).strip()
            pressure = "".join(next(cells).itertext()).strip()
            next(cells)  # published suppression relative to distilled water
            sample = groups[column]
            if sample:
                # The letter c (Cyrillic in the XML) marks the 0.5 K/h heating run.
                temp_value, flag = re.fullmatch(r"([0-9.]+)([cс]?)", temp).groups()
                point[sample] = point.get(sample, 0) + 1
                yield dict(charges[sample], point=point[sample], observed_K=float(temp_value),
                           pressure_MPa_abs=float(pressure.rstrip("cс")), heating_K_h=0.5 if flag else 0.1)
    if set(point) != set(charges) or sum(point.values()) != 59:
        raise ValueError("Unexpected Semenov Table 1 layout or sample count")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True, help="NeqSim jar with dependencies or compiled classpath")
    parser.add_argument("--article", required=True, type=Path, help="PMC9876827.1.xml (JATS)")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    jpype.startJVM("-Xmx1g", classpath=args.classpath.split(os.pathsep))
    operations = jpype.JClass("neqsim.thermodynamicoperations.ThermodynamicOperations")
    fields = ("methane_factor methane_mol status calculated_K residual_K structure fluid_phases aqueous_x "
              "fraction_of_species_in_vapor hydrate_residual balance_residual fixed_sI_K fixed_sII_K lattice_check").split()
    baseline = {}
    failures = 0
    with args.output.open("w", newline="") as dest:
        rows = measurements(args.article)
        columns = "sample component point pressure_MPa_abs heating_K_h".split()
        writer = csv.DictWriter(dest, fieldnames=columns + fields, extrasaction="ignore", lineterminator="\n")
        writer.writeheader()
        for row in rows:
            try:
                methane = reconstruct_charge(row, operations)
            except (jpype.JException, ValueError) as exc:
                writer.writerow(dict(row, status=str(exc)))
                failures += 1
                continue
            for factor in (1.0, 0.8, 1.2):
                result = dict(row, methane_factor=factor, methane_mol=methane * factor)
                try:
                    result.update(assess(row, methane * factor, operations), status="converged")
                    if factor == 1.0:
                        baseline.setdefault(row["component"], []).append(result["residual_K"])
                        try:
                            compare_lattices(row, methane, operations, result)
                            result["lattice_check"] = "passed"
                        except (jpype.JException, ValueError) as exc:
                            result["lattice_check"] = str(exc)
                            failures += 1
                except (jpype.JException, ValueError) as exc:
                    result["status"] = str(exc)
                    failures += 1
                writer.writerow(result)
                dest.flush()
    for name, errors in baseline.items():
        print(f"{name}: n={len(errors)} bias={mean(errors):.4f} MAE={mean(map(abs, errors)):.4f} "
              f"max_abs={max(map(abs, errors)):.4f} K")
    if failures:
        raise SystemExit(f"{failures} unsuccessful runs; see pointwise output")


if __name__ == "__main__":
    main()
