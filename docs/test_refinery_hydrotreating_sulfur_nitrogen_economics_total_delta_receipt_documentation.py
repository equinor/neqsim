from pathlib import Path

DOC = Path(
    "docs/thermo/characterization/"
    "refinery_hydrotreating_sulfur_nitrogen_economics_total_delta_receipt.md"
)
INDEX = Path("docs/thermo/characterization/README.md")


def test_total_economics_delta_receipt_contract_is_documented():
    text = DOC.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")

    required = (
        "No default price, market forecast, or economic datum is embedded",
        "does not depend on choosing a physical-first or price-first bridge",
        "Seven contributions",
        "Four explicit residuals",
        "symmetric algebraic attribution, not causal decomposition",
        "shares each bilinear flow-price interaction equally",
        "does not infer export-gas heating value",
    )
    for phrase in required:
        assert phrase in text

    assert (
        "refinery_hydrotreating_sulfur_nitrogen_economics_total_delta_receipt"
        in index
    )
    assert "symmetric total economics-delta attribution receipts" in index
