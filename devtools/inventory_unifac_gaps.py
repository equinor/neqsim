"""Inventory of COMP.csv components that have no UNIFAC group assignment.

Produces, for every component in COMP.csv that is missing from UNIFACcomp.csv or
UNIFACcompUMRPRU.csv:

  * whether DDBST publishes a group assignment for its CAS number,
  * whether every subgroup in that assignment already exists in
    UNIFACGroupParam.csv,
  * whether the main groups involved already have interaction parameters with the
    groups a typical natural-gas fluid uses,
  * a tier saying how much work importing it would be.

Before any of that it runs a gate: the DDBST subgroup numbering is compared
against every component NeqSim already has. Automated import is only defensible
if the two numbering schemes agree on the rows we can already check.

Outputs:
    devtools/output/unifac_gap_inventory.csv
    devtools/output/unifac_gap_proposed_rows.csv

Usage:
    python devtools/inventory_unifac_gaps.py
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data"
OUT = Path(__file__).resolve().parent / "output"

# Subgroups NeqSim uses differently from DDBST. A DDBST assignment touching these
# needs a human decision, not an automatic import.
NEQSIM_CONVENTION_SUBGROUPS = {
    2: "cyclics use cCH2 (136) not CH2 (2) here",
    3: "cyclics use cCH (137) not CH (3) here",
    4: "cyclics use cC (138) not C (4) here",
}

# Component types that are not molecules in the group-contribution sense.
NON_MOLECULAR_TYPES = {"ion", "ice", "seawater", "asphaltene", "salt"}

# ACH, AC, ACCH3, ACCH2, ACCH. Used to work out how much of a molecule's
# unsaturation its aromatic rings already account for.
AROMATIC_SUBGROUPS = {9, 10, 11, 12, 13}

# Main groups a natural-gas / condensate fluid almost always contains. A new
# component is only useful if its groups interact with these.
CORE_MAIN_GROUPS_BY_NAME = ["CH4", "CO2", "N2", "H2S", "H2O", "CH2"]


def read_csv(path: Path) -> list:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def load_group_params() -> dict:
    """Subgroup id -> {main, name, r, q} from UNIFACGroupParam.csv."""
    groups = {}
    for row in read_csv(DATA / "UNIFACGroupParam.csv"):
        groups[int(row["Secondary"])] = {
            "main": int(row["Main"]),
            "name": row["Name"].strip(),
            "r": float(row["VolumeR"]),
            "q": float(row["SurfAreaQ"]),
        }
    return groups


def load_component_groups(filename: str) -> dict:
    """Component name -> {subgroup id: count} from a UNIFACcomp-style table."""
    assignments = {}
    for row in read_csv(DATA / filename):
        counts = {}
        for key, value in row.items():
            if key is None or not key.startswith("sub"):
                continue
            try:
                count = int(float(value))
            except (TypeError, ValueError):
                continue
            if count > 0:
                counts[int(key[3:])] = count
        assignments[row["Name"]] = counts
    return assignments


def load_interaction_main_groups(filename: str) -> set:
    """Main groups that appear as a row in an interaction table."""
    present = set()
    for row in read_csv(DATA / filename):
        try:
            present.add(int(row["MainGroup"]))
        except (TypeError, ValueError):
            continue
    return present


def ddbst_assignments() -> dict:
    """InChI key -> {subgroup: count} for original UNIFAC, or empty when unavailable."""
    try:
        from thermo.unifac import DDBST_UNIFAC_assignments, load_group_assignments_DDBST
    except ImportError:
        print("WARNING: the 'thermo' package is not installed; DDBST lookup disabled.\n")
        return {}
    load_group_assignments_DDBST()
    return DDBST_UNIFAC_assignments


def inchi_key_for(cas: str):
    try:
        from chemicals.identifiers import search_chemical

        return search_chemical(cas).InChI_key
    except Exception:  # noqa: BLE001 - unresolvable CAS is an expected outcome here
        return None


def gate_numbering_agreement(existing: dict, comp_rows: list, ddbst: dict) -> None:
    """Compare NeqSim's stored assignment against DDBST for every component we already have."""
    print("=" * 78)
    print("GATE: does NeqSim's subgroup numbering agree with DDBST on existing rows?")
    print("=" * 78)

    cas_by_name = {row["NAME"]: row["CASnumber"] for row in comp_rows}
    agree, disagree, unchecked = [], [], []

    for name, neqsim_groups in sorted(existing.items()):
        cas = cas_by_name.get(name)
        if not cas or not ddbst:
            unchecked.append(name)
            continue
        key = inchi_key_for(cas)
        reference = ddbst.get(key) if key else None
        if not reference:
            unchecked.append(name)
            continue
        if dict(reference) == neqsim_groups:
            agree.append(name)
        else:
            disagree.append((name, neqsim_groups, dict(reference)))

    print(f"  agree     : {len(agree)}")
    print(f"  disagree  : {len(disagree)}")
    print(f"  unchecked : {len(unchecked)}  (no CAS match in DDBST)")

    if disagree:
        print("\n  Disagreements - these must be understood before trusting an automated import:")
        for name, mine, theirs in disagree[:40]:
            print(f"    {name:<22} NeqSim {mine}   DDBST {theirs}")
        if len(disagree) > 40:
            print(f"    ... and {len(disagree) - 40} more")
    print()


def classify(row, key, ddbst, group_params, umr_main_groups, unifac_main_groups, cas_index, covered):
    """Return (tier, reason, proposed assignment)."""
    name = row["NAME"]
    cas = row.get("CASnumber", "").strip()
    comptype = row.get("COMPTYPE", "").strip()

    if comptype in NON_MOLECULAR_TYPES:
        return "D-not-a-molecule", "component type '{}' has no group decomposition".format(comptype), None
    if not cas or cas in ("", "0", "0-0-0-0"):
        return "D-not-a-molecule", "placeholder CAS", None

    # Ions and PVTsim duplicates borrow their neutral parent's CAS, so a CAS-driven
    # lookup would hand them the parent molecule's groups. Only the borrower is
    # diverted here; the canonical molecule is classified normally below.
    siblings = [other for other in cas_index.get(cas, []) if other != name]
    if "PVTsim" in name:
        return (
            "E-alias",
            "PVTsim duplicate of CAS {}; copy the canonical row rather than re-deriving".format(cas),
            None,
        )
    covered_siblings = [other for other in siblings if other in covered]
    if covered_siblings:
        return (
            "E-alias",
            "shares CAS {} with {}, which already has a row; copy it rather than re-deriving".format(
                cas, ", ".join(sorted(covered_siblings))
            ),
            None,
        )

    reference = ddbst.get(key) if key else None
    if not reference:
        return "C-no-ddbst", "no DDBST assignment for CAS {}".format(cas), None

    proposed = dict(reference)

    unsaturation = rings_plus_double_bonds(row.get("FORMULA", ""))
    conflicts = sorted(set(proposed) & set(NEQSIM_CONVENTION_SUBGROUPS))
    if unsaturation is not None and conflicts:
        # The cCH2 convention applies to saturated rings only. An aromatic ring is
        # already carried by the AC* groups, and a CH2 on its side chain is acyclic,
        # so subtract the unsaturation the aromatic rings account for before deciding.
        aromatic_carbons = sum(count for sub, count in proposed.items() if sub in AROMATIC_SUBGROUPS)
        saturated_unsaturation = unsaturation - 4.0 * (aromatic_carbons / 6.0)
        if saturated_unsaturation >= 1.0:
            return (
                "B-convention",
                "saturated ring present (unsaturation {:.0f}, of which {:.0f} aromatic) and {}".format(
                    unsaturation, 4.0 * (aromatic_carbons / 6.0),
                    "; ".join(NEQSIM_CONVENTION_SUBGROUPS[c] for c in conflicts)
                ),
                proposed,
            )

    unknown = [sub for sub in proposed if sub not in group_params]
    if unknown:
        return "B-new-subgroup", "subgroups absent from UNIFACGroupParam.csv: " + str(unknown), proposed

    mains = sorted({group_params[sub]["main"] for sub in proposed})
    missing_umr = [m for m in mains if m not in umr_main_groups]
    missing_unifac = [m for m in mains if m not in unifac_main_groups]
    if missing_umr or missing_unifac:
        return (
            "B-no-interaction",
            "main groups without interaction rows - UMR: {} classic: {}".format(missing_umr, missing_unifac),
            proposed,
        )
    return "A-ready", "subgroups and interaction rows present, no convention conflict", proposed


def rings_plus_double_bonds(formula: str):
    """Degree of unsaturation from a CxHy formula, or None when it is not a plain hydrocarbon.

    A saturated hydrocarbon scoring 1 has exactly one ring, which detects cycloalkanes
    without needing a structure toolkit.
    """
    match = re.fullmatch(r"C(\d*)H(\d*)", formula.strip())
    if not match:
        return None
    carbons = int(match.group(1) or 1)
    hydrogens = int(match.group(2) or 1)
    return (2 * carbons + 2 - hydrogens) / 2.0


def build_cas_index(comp_rows: list) -> dict:
    index = {}
    for row in comp_rows:
        cas = row.get("CASnumber", "").strip()
        if cas and cas not in ("", "0", "0-0-0-0"):
            index.setdefault(cas, []).append(row["NAME"])
    return index


def main() -> None:
    OUT.mkdir(exist_ok=True)

    comp_rows = read_csv(DATA / "COMP.csv")
    group_params = load_group_params()
    classic = load_component_groups("UNIFACcomp.csv")
    umrpru = load_component_groups("UNIFACcompUMRPRU.csv")
    umr_main_groups = load_interaction_main_groups("UNIFACInterParamA_UMRMC.csv")
    unifac_main_groups = load_interaction_main_groups("UNIFACInterParam.csv")
    ddbst = ddbst_assignments()
    cas_index = build_cas_index(comp_rows)
    covered = set(classic) | set(umrpru)

    print(f"COMP.csv                 : {len(comp_rows)} components")
    print(f"UNIFACcomp.csv           : {len(classic)} rows")
    print(f"UNIFACcompUMRPRU.csv     : {len(umrpru)} rows")
    print(f"UNIFACGroupParam.csv     : {len(group_params)} subgroups")
    print(f"UMR-MC interaction rows  : {len(umr_main_groups)} main groups")
    print(f"classic interaction rows : {len(unifac_main_groups)} main groups")
    print()

    gate_numbering_agreement(umrpru, comp_rows, ddbst)

    inventory = []
    for row in comp_rows:
        name = row["NAME"]
        in_classic = name in classic
        in_umrpru = name in umrpru
        if in_classic and in_umrpru:
            continue

        cas = row.get("CASnumber", "").strip()
        key = inchi_key_for(cas) if cas and cas not in ("", "0", "0-0-0-0") else None
        tier, reason, proposed = classify(
            row, key, ddbst, group_params, umr_main_groups, unifac_main_groups, cas_index, covered
        )

        inventory.append(
            {
                "name": name,
                "cas": cas,
                "comptype": row.get("COMPTYPE", ""),
                "formula": row.get("FORMULA", ""),
                "molar_mass_gmol": row.get("MOLARMASS", ""),
                "in_unifac_classic": "yes" if in_classic else "no",
                "in_unifac_umrpru": "yes" if in_umrpru else "no",
                "tier": tier,
                "reason": reason,
                "proposed_groups": ""
                if not proposed
                else " ".join(
                    "{}x{}({})".format(count, sub, group_params.get(sub, {}).get("name", "?"))
                    for sub, count in sorted(proposed.items())
                ),
            }
        )

    inventory.sort(key=lambda r: (r["tier"], r["comptype"], r["name"]))

    inventory_path = OUT / "unifac_gap_inventory.csv"
    with inventory_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(inventory[0].keys()))
        writer.writeheader()
        writer.writerows(inventory)

    print("=" * 78)
    print("MISSING COMPONENTS BY TIER")
    print("=" * 78)
    tiers = {}
    for entry in inventory:
        tiers.setdefault(entry["tier"], []).append(entry)
    for tier in sorted(tiers):
        entries = tiers[tier]
        print(f"\n{tier}  ({len(entries)} components)")
        by_type = {}
        for entry in entries:
            by_type.setdefault(entry["comptype"], []).append(entry["name"])
        for comptype, names in sorted(by_type.items()):
            shown = ", ".join(sorted(names)[:14])
            more = "" if len(names) <= 14 else f" ... (+{len(names) - 14})"
            print(f"    {comptype or '(blank)':<8} {len(names):>4}  {shown}{more}")

    ready = [entry for entry in inventory if entry["tier"] == "A-ready"]
    if ready:
        rows_path = OUT / "unifac_gap_proposed_rows.csv"
        with rows_path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(["Name", "CAS", "subgroup", "count", "subgroup_name"])
            for entry in ready:
                for token in entry["proposed_groups"].split():
                    count, rest = token.split("x", 1)
                    sub, sub_name = rest.split("(", 1)
                    writer.writerow([entry["name"], entry["cas"], sub, count, sub_name.rstrip(")")])
        print(f"\nProposed rows written to {rows_path}")

    print(f"Full inventory written to {inventory_path}")
    print(f"\nTotal components needing attention: {len(inventory)}")


if __name__ == "__main__":
    main()
