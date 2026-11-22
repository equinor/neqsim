from pathlib import Path


DOC = Path("docs/thermo/characterization/refinery_hydrotreating_sulfur_nitrogen_screening_economics_receipt.md")
INDEX = Path("docs/thermo/characterization/README.md")


def test_screening_economics_receipt_contract_is_documented():
    text = DOC.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")

    required = (
        "caller-owned scenario inputs",
        "No default price, market forecast, or economic datum is embedded",
        "signed screening margin",
        "Three explicit residuals",
        "A negative screening margin is valid",
        "not a product-quality or specification model",
        "does not infer export-gas energy content or commercial acceptability",
    )
    for phrase in required:
        assert phrase in text

    assert "refinery_hydrotreating_sulfur_nitrogen_screening_economics_receipt" in index
    assert "caller-priced coupled sulfur/nitrogen screening-economics receipts" in index
