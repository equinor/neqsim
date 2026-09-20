"""Validate emitted source frames and deliberate corruptions against Draft 2020-12.

Requires jsonschema only in the validation environment, never in the NeqSim runtime.
Run SourceTermFrameTest first to create the actual serialized fixtures.
"""

import copy
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


def main():
    """Check schema, emitted fixtures and invalid unit/status/version mutations."""
    root = Path(__file__).resolve().parents[1]
    schema_path = root / (
        "src/main/resources/neqsim/process/safety/release/schema/"
        "safety-source-term.schema.json"
    )
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    fixtures = sorted((root / "target/source-term-contract-fixtures").glob("*.json"))
    if len(fixtures) < 6:
        raise RuntimeError("Run SourceTermFrameTest to generate the six required fixtures")
    for path in fixtures:
        frame = json.loads(path.read_text(encoding="utf-8"))
        validator.validate(frame)
        for station in frame.get("source", {}).get("stations", {}).values():
            for field in ("componentMoleFractions", "componentMassFractions", "phaseMassFractions"):
                if abs(sum(station[field].values()) - 1.0) > 1e-8:
                    raise AssertionError(f"Unclosed {field} in {path.name}")
    valid = json.loads((root / "target/source-term-contract-fixtures/valid.json").read_text())
    mutations = []
    changed = copy.deepcopy(valid)
    changed["schemaVersion"] = "neqsim_safety_source_term.v2"
    mutations.append(changed)
    changed = copy.deepcopy(valid)
    changed["source"]["massFlowRate"]["unit"] = "kg/hr"
    mutations.append(changed)
    changed = copy.deepcopy(valid)
    changed["source"]["massFlowRate"]["value"] = -1.0
    mutations.append(changed)
    changed = copy.deepcopy(valid)
    changed["status"] = "STALE"
    mutations.append(changed)
    changed = copy.deepcopy(valid)
    del changed["source"]
    mutations.append(changed)
    changed = copy.deepcopy(valid)
    changed["calculationId"] = "not-a-uuid"
    mutations.append(changed)
    for field, value in (("diameter", -1), ("dischargeCoefficient", 1.1),
                         ("physicalArea", 0), ("backPressure", 0)):
        changed = copy.deepcopy(valid)
        changed["source"][field]["value"] = value
        mutations.append(changed)
    for index, changed in enumerate(mutations):
        if validator.is_valid(changed):
            raise AssertionError(f"Invalid mutation {index} was accepted")
    print(f"Validated {len(fixtures)} emitted frames and rejected {len(mutations)} invalid mutations")


if __name__ == "__main__":
    main()
