"""Audit quarantined CO2-in-MEG measurements; never infer an HV fit or feed.

The two retained source representations intentionally disagree on four phase
labels. Exact bytes and Decimal multisets preserve duplicates and uncertainty
semantics. This is a source audit, not a thermodynamic acceptance test.
"""

import argparse
from collections import Counter, defaultdict
import csv
from decimal import Decimal
import hashlib
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / (
    "src/test/resources/neqsim/thermo/mixingrule/hv-co2-meg-receipts")
NS = {"t": "http://www.iupac.org/namespaces/ThermoML"}
DOI = "10.1021/acs.jced.7b00537"
XML_SHA256 = "d2621ba7acadf439e978cf6d13188e6dbc9492b043733df910c0070e965ee182"
XML_MD5 = "332d247b732b334e9db14e090da2dbba"
XML_GIT = "580c8ddf19348fab94de6b7c1879870a14925d4e"
CSV_SHA256 = '54f497616df014fd1fad05edee4bb36fe445f3602904470906feb956ab965b6e'
DUPLICATES = [[29, 33], [30, 34], [31, 35], [32, 36], [50, 51]]


def require(condition, message):
    """Keep all audit gates effective with Python optimization enabled."""
    if not condition:
        raise ValueError(message)


def value(element, path):
    """Read one required ThermoML field, without a missing-value fallback."""
    result = element.findtext(path, namespaces=NS)
    require(result is not None, "Missing ThermoML field: " + path)
    return result


def number(text):
    """Parse a finite decimal, preserving the source's exact numeric value."""
    result = Decimal(text)
    require(result.is_finite(), "Nonfinite measurement")
    return result


def parse_archive(raw):
    """Validate the property direction, phases, units and compiler uncertainty."""
    root = ET.fromstring(raw)
    require(value(root, "t:Citation/t:sDOI") == DOI, "Wrong DOI")
    compounds = {value(c, "t:RegNum/t:nOrgNum"):
                 value(c, "t:sStandardInChIKey") for c in root.findall("t:Compound", NS)}
    require(compounds == {"1": "LYCAIKOWRPUZTN-UHFFFAOYSA-N",
                          "2": "CURLTUGMZLYLDI-UHFFFAOYSA-N"}, "Wrong compound identities")
    groups = root.findall("t:PureOrMixtureData", NS)
    require(len(groups) == 2, "Expected both phase datasets")
    rows = []
    for group, phases, label in zip(groups, (("Liquid", "Gas"),
                                            ("Liquid mixture 1", "Liquid mixture 2")), ("VLE", "LLE")):
        require(tuple(e.text for e in group.findall("t:PhaseID/t:ePhase", NS)) == phases,
                "Changed archive phases")
        prop = group.find("t:Property", NS)
        require(prop is not None and len(group.findall("t:Property", NS)) == 1,
                "Expected one composition property")
        require(value(prop, "t:Property-MethodID/t:RegNum/t:nOrgNum") == "2"
                and value(prop, "t:Property-MethodID/t:PropertyGroup/"
                          "t:CompositionAtPhaseEquilibrium/t:ePropName") == "Mole fraction"
                and value(prop, "t:PropPhaseID/t:ePropPhase") == phases[0],
                "Wrong solubility direction or basis")
        uncertainty = prop.find("t:CombinedUncertainty", NS)
        require(uncertainty is not None
                and value(uncertainty, "t:sCombUncertEvaluator") == "Data file compiler"
                and value(uncertainty, "t:nCombUncertLevOfConfid") == "95"
                and value(uncertainty, "t:eCombUncertEvalMethod")
                == "Propagation of evaluated standard uncertainties", "Changed compiler uncertainty")
        variables = group.findall("t:Variable", NS)
        require(len(variables) == 2, "Expected T and P only")
        require(value(variables[0], "t:VariableID/t:VariableType/t:eTemperature") == "Temperature, K"
                and value(variables[1], "t:VariableID/t:VariableType/t:ePressure") == "Pressure, kPa",
                "Wrong archive units")
        for point in group.findall("t:NumValues", NS):
            variables = point.findall("t:VariableValue", NS)
            vals = {value(v, "t:nVarNumber"): number(value(v, "t:nVarValue")) for v in variables}
            require(len(variables) == 2 and set(vals) == {"1", "2"}, "Missing or duplicate T/P")
            prop_values = point.findall("t:PropertyValue", NS)
            require(len(prop_values) == 1, "Missing or duplicate observation")
            observation = prop_values[0]
            require(value(observation, "t:nPropNumber") == "1", "Wrong property number")
            x = number(value(observation, "t:nPropValue"))
            expanded = number(value(observation, "t:CombinedUncertainty/t:nCombExpandUncertValue"))
            require(vals["1"] > 0 and vals["2"] > 0 and 0 < x < 1 and expanded > 0,
                    "Invalid measurement or expanded uncertainty")
            rows.append({"key": (vals["1"], vals["2"] / 1000, x),
                         "phase": label, "compiler_expanded_95_absolute_x": expanded})
    require(Counter(r["phase"] for r in rows) == {"VLE": 52, "LLE": 28}, "Incomplete archive")
    return rows


def reconcile(archive, manuscript):
    """Keep multiplicity, ordinal locators and both contradictory phase labels."""
    rows = list(csv.DictReader(io.StringIO(manuscript.decode("utf-8"))))
    require(len(rows) == 80 and [int(r["row"]) for r in rows] == list(range(1, 81)),
            "Missing or reordered manuscript rows")
    keys = [(number(r["T_K"]), number(r["P_MPa"]), number(r["x_CO2"])) for r in rows]
    require(Counter(keys) == Counter(r["key"] for r in archive), "Changed values or multiplicity")
    require(all(r["phase_as_extracted"] in ("VLE", "LLE")
                and 10 <= int(r["pdf_page_zero_based"]) <= 13
                and int(r["line"]) == int(r["row"]) + 269 for r in rows), "Changed source locators")
    occurrences = defaultdict(list)
    labels = defaultdict(set)
    for key, row in zip(keys, rows):
        occurrences[key].append(int(row["row"]))
    for row in archive:
        labels[row["key"]].add(row["phase"])
    duplicates = [indices for indices in occurrences.values() if len(indices) > 1]
    require(duplicates == DUPLICATES and len(occurrences) == 75, "Changed duplicate observations")
    differences = [int(r["row"]) for k, r in zip(keys, rows)
                   if r["phase_as_extracted"] not in labels[k]]
    require(differences == [53, 54, 55, 56], "Changed phase reconciliation")
    require(all(rows[n - 1]["phase_as_extracted"] == "VLE"
                and labels[keys[n - 1]] == {"LLE"} for n in differences), "Reversed phase discrepancy")
    return {"rows": len(rows), "distinct_numeric_rows": len(occurrences),
            "duplicate_row_groups": duplicates, "phase_disagreement_rows": differences,
            "archive_phase_counts": dict(Counter(r["phase"] for r in archive)),
            "temperature_count": len({k[0] for k in keys}),
            "qualification": "Quarantined source receipts; no HV fit, feed or physical acceptance"}


def audit(directory=ROOT):
    """Audit immutable raw bytes and metadata, then reconcile source semantics."""
    directory = Path(directory)
    raw = (directory / "source.xml").read_bytes()
    manuscript = (directory / "manuscript.csv").read_bytes()
    metadata = json.loads((directory / "provenance.json").read_text(encoding="utf-8"))
    require(len(raw) == 62341 and hashlib.sha256(raw).hexdigest() == XML_SHA256
            and hashlib.md5(raw).hexdigest() == XML_MD5
            and hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest() == XML_GIT,
            "Changed original XML bytes")
    require(hashlib.sha256(manuscript).hexdigest() == metadata["manuscript_csv_sha256"] == CSV_SHA256,
            "Changed manuscript receipt bytes")
    provenance = metadata["archive"]
    require(provenance["official_xml_md5"] == XML_MD5 and provenance["git_blob"] == XML_GIT
            and provenance["sha256"] == XML_SHA256
            and provenance["commit"] == "b3f595195a61c0b9d6794c971f487f3c8180ca86"
            and provenance["github_repository"] == "Syn-Sat/Surrogate_Modelling"
            and provenance["path"] == "ThermoML.v2020-09-30/" + DOI + ".xml",
            "Changed archive provenance")
    require(metadata["doi"] == DOI and metadata["status"] == "quarantined"
            and metadata["parameter_activation"] is False
            and metadata["author_standard_uncertainties"] == {
                "relative_x": "0.026", "T_K": "0.05", "P_MPa": "0.04"}
            and metadata["archive_uncertainty"] == "compiler expanded 95% absolute mole fraction"
            and metadata["missing"] == ["final-publication reconciliation", "reverse MEG partition",
                                        "complete source-compatible HV and pure-component inputs"],
            "Changed qualification or uncertainty semantics")
    return reconcile(parse_archive(raw), manuscript)


def main():
    """Print a portable audit receipt without changing any retained observation."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=ROOT)
    args = parser.parse_args()
    print(json.dumps(audit(args.directory), indent=2))


if __name__ == "__main__":
    main()
