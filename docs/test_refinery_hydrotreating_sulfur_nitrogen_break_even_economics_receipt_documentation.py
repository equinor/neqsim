from pathlib import Path


DOC = Path(
    "docs/thermo/characterization/"
    "refinery_hydrotreating_sulfur_nitrogen_break_even_economics_receipt.md"
)
INDEX = Path("docs/thermo/characterization/README.md")


def test_break_even_economics_receipt_contract_is_documented():
    text = DOC.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")

    required = (
        "single-variable break-even arithmetic",
        "No default price, market forecast, or economic datum is embedded",
        "Three explicit residuals",
        "Break-even values and deltas are signed",
        "negative export-gas break-even price",
        "not a multi-variable solver, optimizer, market forecast",
        "does not infer export-gas heating value",
    )
    for phrase in required:
        assert phrase in text

    assert "refinery_hydrotreating_sulfur_nitrogen_break_even_economics_receipt" in index
    assert "single-variable break-even economics and price-sensitivity receipts" in index
