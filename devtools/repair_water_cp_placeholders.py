"""Reproduce issue #4049 hydrocarbon ideal-gas Cp database replacements.

Joback and Reid, Chem. Eng. Commun. 57, 233-243 (1987), DOI:
10.1080/00986448708960487. Group coefficients are the published Table III.
The NIST WebBook data for CAS 95-63-6 are fitted directly where indicated.
"""

import argparse
import csv
import re
from collections import Counter
from pathlib import Path


# J/(mol K), J/(mol K^2), J/(mol K^3), J/(mol K^4).
GROUP = {
    "CH3": (19.5, -0.00808, 0.000153, -9.67e-08),
    "CH2": (-0.909, 0.095, -5.44e-05, 1.19e-08),
    "CH": (-23.0, 0.204, -0.000265, 1.20e-07),
    "C": (-66.2, 0.427, -0.000641, 3.01e-07),
    "=CH2": (23.6, -0.0381, 0.000172, -1.03e-07),
    "=CH": (-8.0, 0.105, -9.63e-05, 3.56e-08),
    "=C": (-28.1, 0.208, -0.000306, 1.46e-07),
    "#CH": (24.5, -0.0271, 0.000111, -6.78e-08),
    "#C": (7.87, 0.0201, -8.33e-06, 1.39e-09),
    "ringCH2": (-6.03, 0.0854, -8e-06, -1.8e-08),
    "ringCH": (-20.5, 0.162, -0.00016, 6.24e-08),
    "ringC": (-90.9, 0.557, -0.0009, 4.69e-07),
    "ring=CH": (-2.14, 0.0574, -1.64e-06, -1.59e-08),
    "ring=C": (-8.25, 0.101, -0.000142, 6.78e-08),
}

# Least-squares quartic of the NIST SRD 69 gas Cp values at 273.15-1000 K
# for CAS 95-63-6 (https://webbook.nist.gov/cgi/cbook.cgi?ID=C95636&Mask=1).
# Maximum residual of the ten tabulated points is 0.365 J/(mol K).
NIST_124_TRIMETHYLBENZENE = (
    14.183877917345455, 0.45192428510456795, 0.00020059136895914955,
    -5.376099424346501e-07, 2.3329918322489563e-10,
)
DATA = Path(__file__).resolve().parents[1] / "src/main/resources/data"
MANIFEST = Path(__file__).resolve().parents[1] / "docs/thermo/data/water_cp_replacements.csv"

ROOT = {"but": 4, "pent": 5, "hex": 6, "hept": 7, "oct": 8, "non": 9,
        "dec": 10, "undec": 11, "dodec": 12}
MULT = {"": 1, "di": 2, "tri": 3, "tetra": 4, "penta": 5}


class Molecule:
    def __init__(self):
        self.nodes = []
        self.edges = []

    def atom(self, ring=False, aromatic=False):
        self.nodes.append((ring, aromatic))
        return len(self.nodes) - 1

    def bond(self, a, b, order=1):
        self.edges.append((a, b, order))

    def chain(self, length, parent=None, at=None, branch=None):
        atoms = [self.atom() for _ in range(length)]
        for a, b in zip(atoms, atoms[1:]):
            self.bond(a, b)
        if parent is not None:
            self.bond(at, atoms[0])
        if branch == "iso":
            # isopropyl: attachment at CH; isobutyl: attachment at CH2.
            pivot = atoms[0] if length == 2 else atoms[1]
            self.bond(pivot, self.atom())
            # iso propyl C3 is a CH attached to two CH3; iso butyl C4
            # is CH2-CH(CH3)2. The initial linear chain has one terminal CH3.
        return atoms

    def ring(self, length, aromatic=False):
        atoms = [self.atom(ring=True, aromatic=aromatic) for _ in range(length)]
        for a, b in zip(atoms, atoms[1:] + atoms[:1]):
            self.bond(a, b)
        return atoms

    def formula_and_groups(self):
        counts = Counter()
        h_total = 0
        for i, (ring, aromatic) in enumerate(self.nodes):
            adjacent = [(a, b, o) for a, b, o in self.edges if a == i or b == i]
            if aromatic:
                # A benzene/fused aromatic carbon has one H if it has two
                # aromatic neighbours and no substituent, otherwise zero.
                h = 3 - len(adjacent)
                group = "ring=CH" if h == 1 else "ring=C"
            else:
                valence = sum(o for _, _, o in adjacent)
                h = 4 - valence
                max_order = max((o for _, _, o in adjacent), default=0)
                if max_order == 3:
                    group = "#CH" if h == 1 else "#C"
                elif max_order == 2:
                    group = ("ring=" if ring else "=") + ("CH" + ("2" if h == 2 else "") if h else "C")
                elif ring:
                    group = {2: "ringCH2", 1: "ringCH", 0: "ringC"}[h]
                else:
                    group = {3: "CH3", 2: "CH2", 1: "CH", 0: "C"}[h]
            assert h >= 0 and group in GROUP, (i, h, group)
            counts[group] += 1
            h_total += h
        return f"C{len(self.nodes)}H{h_total}", counts


def build(name):
    n = re.sub(r"^\(e\)-|^(?:cis|trans)-", "", name.lower())
    m = Molecule()
    if n in ("biphenyl", "1-methylnaphthalene", "2-methylnaphthalene", "5-ethyltetralin"):
        if n == "biphenyl":
            a, b = m.ring(6, True), m.ring(6, True)
            m.bond(a[0], b[0])
        elif "naphthalene" in n:
            # Two benzene rings share the bond between the first two atoms.
            a = m.ring(6, True)
            b = [a[0], a[1]] + [m.atom(ring=True, aromatic=True) for _ in range(4)]
            for x, y in zip(b[1:], b[2:] + b[:1]):
                m.bond(x, y)
            m.chain(1, parent=True, at=b[3])
        else:
            a = m.ring(6, True)
            b = [a[0], a[1]] + [m.atom(ring=True) for _ in range(4)]
            for x, y in zip(b[1:], b[2:] + b[:1]):
                m.bond(x, y)
            m.chain(2, parent=True, at=a[3])
        return m

    aliases = {
        "cumene": "isopropylbenzene",
        "mesitylene": "1,3,5-trimethylbenzene",
        "m-cymene": "1-isopropyl-3-methylbenzene",
        "m-diethylbenzene": "1,3-diethylbenzene",
        "isoprene": "2-methyl-1,3-butadiene",
        "butylbenzene": "1-butylbenzene",
        "isobutylbenzene": "1-isobutylbenzene",
        "4-ethyl-o-xylene": "1,2-dimethyl-4-ethylbenzene",
        "5-ethyl-m-xylene": "1,3-dimethyl-5-ethylbenzene",
    }
    n = aliases.get(n, n)
    n = re.sub(r"(\d+)-ethyltoluene", r"1-methyl-\1-ethylbenzene", n)
    n = re.sub(r"(\d+)-propyltoluene", r"1-methyl-\1-propylbenzene", n)
    n = re.sub(r"pentamethylbenzene", "1,2,3,4,5-pentamethylbenzene", n)

    if n.endswith("benzene"):
        root, prefix = "benzene", n[:-7]
        atoms = m.ring(6, True)
    elif n.endswith("toluene"):
        root, prefix = "benzene", n[:-7]
        atoms = m.ring(6, True)
        m.chain(1, parent=True, at=atoms[0])
    else:
        root_match = re.search(r"(?P<ring>cyclo)?(?P<root>undec|dodec|hept|pent|hex|oct|non|dec|but)"
                               r"(?P<suffix>ane|ene|adiene|yne)$", n)
        assert root_match, n
        root = root_match.group("root")
        prefix = n[:root_match.start()]
        is_ring = bool(root_match.group("ring"))
        atoms = m.ring(ROOT[root]) if is_ring else m.chain(ROOT[root])
        suffix = root_match.group("suffix")
        if suffix != "ane":
            # Locants immediately before the parent root (or default 1).
            double_locants = re.search(r"(\d+(?:,\d+)*)-$", prefix)
            if double_locants:
                locs = [int(x) for x in double_locants.group(1).split(",")]
                prefix = prefix[:double_locants.start()]
            else:
                locs = [1]
            assert len(locs) == (2 if suffix == "adiene" else 1), (name, locs)
            for loc in locs:
                a = atoms[loc - 1]
                b = atoms[loc % len(atoms)]
                idx = next(i for i, (x, y, _) in enumerate(m.edges)
                           if {x, y} == {a, b})
                m.edges[idx] = (a, b, 3 if suffix == "yne" else 2)

    # A side-chain descriptor consists of locants, multiplicity and alkyl name.
    # Any remaining text is rejected to avoid silently guessing connectivity.
    pattern = re.compile(r"(?:(\d+(?:,\d+)*)-)?(penta|tetra|tri|di)?"
                         r"(isobutyl|isopropyl|butyl|propyl|ethyl|methyl)(?:-|$)")
    while prefix:
        match = pattern.match(prefix)
        assert match, (name, prefix)
        numbers, mult, side = match.groups()
        positions = [int(x) for x in numbers.split(",")] if numbers else [1]
        assert len(positions) == MULT[mult or ""], (name, positions, mult)
        for pos in positions:
            assert 1 <= pos <= len(atoms), (name, pos)
            size = {"methyl": 1, "ethyl": 2, "propyl": 3,
                    "butyl": 4, "isopropyl": 2, "isobutyl": 3}[side]
            # iso adds one branched carbon to the linear path.
            m.chain(size, parent=True, at=atoms[pos - 1],
                    branch="iso" if side.startswith("iso") else None)
        prefix = prefix[match.end():]
    return m


def coeffs(groups):
    a = [-37.93, 0.210, -3.91e-4, 2.06e-7]
    for key, count in groups.items():
        a = [x + count * y for x, y in zip(a, GROUP[key])]
    return a + [0.0]


def cp(a, t):
    return sum(x * t**i for i, x in enumerate(a))


def expected(name):
    formula, groups = build(name).formula_and_groups()
    a = (NIST_124_TRIMETHYLBENZENE if name == "1,2,4-trimethylbenzene"
         else coeffs(groups))
    for t in (250.0, 298.15, 500.0, 800.0, 1000.0):
        assert 35 < cp(a, t) < 600, (name, t, cp(a, t))
    return formula, groups, a


def read_rows(path):
    with path.open(newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def cp_fields(row):
    return tuple(row[x] for x in ("CPA", "CPB", "CPC", "CPD", "CPE"))


def replace_fields(path, replacements):
    # Preserve unrelated columns, their quoting, and line endings. In
    # particular COMP_EXT.csv is huge; csv.writer would rewrite every record.
    with path.open("r", encoding="utf-8", newline="") as f:
        lines = f.readlines()
    output = []
    modified = 0
    for line in lines:
        fields = next(csv.reader([line]))
        if len(fields) < 21 or fields[1] not in replacements:
            output.append(line)
            continue
        values = replacements[fields[1]]
        if all(fields[index] == value for index, value in values.items()):
            output.append(line)
            continue
        delimiters = []
        quoted = False
        for pos, char in enumerate(line):
            if char == '"':
                quoted = not quoted
            elif char == "," and not quoted:
                delimiters.append(pos)
        assert len(delimiters) >= len(fields) - 1
        parts = []
        cursor = 0
        for index, value in values.items():
            if fields[index] == value:
                continue
            start = delimiters[index - 1] + 1 if index else 0
            end = delimiters[index] if index < len(delimiters) else len(line.rstrip("\r\n"))
            assert not any(mark in value for mark in ',"\r\n'), (fields[1], index)
            parts.append(line[cursor:start])
            parts.append(value)
            cursor = end
        parts.append(line[cursor:])
        output.append("".join(parts))
        modified += 1
    if modified:
        with path.open("w", encoding="utf-8", newline="") as f:
            f.writelines(output)
    return modified


WATER_CP = ("36.54003", "-0.034802404", "0.000116811", "-1.3e-07", "5.254448e-11")
SYNC_EXCLUDED = {"ID", "COMPINDEX", "NAME"}


def run(write):
    standard = read_rows(DATA / "COMP.csv")
    extended = read_rows(DATA / "COMP_EXT.csv")
    by_name = {row["NAME"]: row for row in standard}
    ext_by_name = {row["NAME"]: row for row in extended}
    if MANIFEST.exists():
        names = [row["NAME"] for row in read_rows(MANIFEST)]
    else:
        assert write, "Missing manifest; run --write on the original database"
        names = [row["NAME"] for row in standard
                 if row["NAME"] != "water" and cp_fields(row) == WATER_CP]
    assert len(names) == len(set(names)) == 130, len(names)
    assert sum(name in ext_by_name for name in names) == 121
    assert cp_fields(by_name["water"]) == cp_fields(ext_by_name["water"]) == WATER_CP
    replacements = {}
    manifest = []
    for name in names:
        row = by_name[name]
        formula, groups, a = expected(name)
        assert formula == row["FORMULA"], (name, formula, row["FORMULA"])
        values = tuple(format(x, ".12g") for x in a)
        replacements[name] = values
        method = "NIST SRD 69 quartic fit" if name == "1,2,4-trimethylbenzene" else "Joback-Reid estimate"
        manifest.append({"NAME": name, "CASnumber": row["CASnumber"], "FORMULA": formula,
                         "method": method, "group_counts": ";".join(
                             f"{key}:{groups[key]}" for key in sorted(groups)),
                         "Cp298_J_mol_K": format(cp(a, 298.15), ".5f"),
                         "Cp500_J_mol_K": format(cp(a, 500.0), ".5f")})
        assert cp_fields(row) == values or (write and cp_fields(row) == WATER_CP), name
        if name in ext_by_name:
            assert cp_fields(ext_by_name[name]) == values or (write and cp_fields(ext_by_name[name]) == WATER_CP), name
    if write:
        changed_standard = replace_fields(DATA / "COMP.csv", {
            name: {index + 16: value for index, value in enumerate(values)}
            for name, values in replacements.items()})
        # Keep shared neutral pure-component parameters in the extended table
        # aligned with COMP; row IDs remain unique to the extended catalog.
        standard = read_rows(DATA / "COMP.csv")
        by_name = {row["NAME"]: row for row in standard}
        shared = [column for column in extended[0] if column in standard[0]
                  and column not in SYNC_EXCLUDED]
        columns = list(extended[0])
        sync = {row["NAME"]: {columns.index(column): row[column] for column in shared}
                for row in standard if row["NAME"] in ext_by_name
                and row["COMPTYPE"].lower() != "ion"}
        changed_ext = replace_fields(DATA / "COMP_EXT.csv", sync)
        MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        with MANIFEST.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=list(manifest[0]), lineterminator="\n")
            writer.writeheader()
            writer.writerows(manifest)
        print(f"Updated {changed_standard} COMP and {changed_ext} COMP_EXT rows")
    else:
        assert read_rows(MANIFEST) == manifest, "Provenance manifest differs from the expected values"
        shared = [column for column in ext_by_name["water"] if column in by_name["water"]
                  and column not in SYNC_EXCLUDED]
        for row in standard:
            if row["NAME"] not in ext_by_name or row["COMPTYPE"].lower() == "ion":
                continue
            for column in shared:
                assert row[column] == ext_by_name[row["NAME"]][column], (row["NAME"], column)
        print(f"Verified {len(names)} Cp replacements and shared neutral component parameter sync")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument("--write", action="store_true")
    choice.add_argument("--check", action="store_true")
    run(parser.parse_args().write)
