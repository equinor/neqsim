from pathlib import Path

DOC = Path(
    "docs/thermo/characterization/"
    "refinery_hydrotreating_sulfur_nitrogen_economics_case_delta_receipt.md"
)
INDEX = Path("docs/thermo/characterization/README.md")


def test_economics_case_delta_receipt_contract_is_documented():
    text = DOC.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")

    required = (
        "exactly the same liquid-product price",
        "No default price, market forecast, or economic datum is embedded",
        "Four explicit residuals",
        "not a causal decomposition",
        "aggregate operating-cost contribution",
        "Signed flow and cost contributions are preserved",
        "does not infer export-gas heating value",
    )
    for phrase in required:
        assert phrase in text

    assert (
        "refinery_hydrotreating_sulfur_nitrogen_economics_case_delta_receipt"
        in index
    )
    assert "fixed-screening-price economics case-delta receipts" in index
