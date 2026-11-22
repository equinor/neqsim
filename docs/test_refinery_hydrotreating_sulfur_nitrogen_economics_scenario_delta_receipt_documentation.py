from pathlib import Path

DOC = Path(
    "docs/thermo/characterization/"
    "refinery_hydrotreating_sulfur_nitrogen_economics_scenario_delta_receipt.md"
)
INDEX = Path("docs/thermo/characterization/README.md")


def test_economics_scenario_delta_receipt_contract_is_documented():
    text = DOC.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")

    required = (
        "same qualified physical receipt",
        "No default price, market forecast, or economic datum is embedded",
        "Four explicit residuals",
        "same in-memory qualified net product-intensity receipt",
        "negative contribution",
        "not a multi-variable solver, optimizer, market forecast",
        "does not infer export-gas heating value",
    )
    for phrase in required:
        assert phrase in text

    assert (
        "refinery_hydrotreating_sulfur_nitrogen_economics_scenario_delta_receipt"
        in index
    )
    assert (
        "same-physical-case economics scenario-delta attribution receipts"
        in index
    )
