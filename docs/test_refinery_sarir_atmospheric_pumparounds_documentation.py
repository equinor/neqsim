from pathlib import Path


GUIDE = Path("docs/thermo/characterization/refinery_sarir_atmospheric_pumparounds.md")


def test_sarir_pumparound_guide_preserves_source_and_model_boundaries():
    text = GUIDE.read_text(encoding="utf-8")

    required = (
        "10.66411/jer.v33i.46",
        "CC BY 4.0",
        "Top pump around (TPA)",
        "Bottom pump around (BPA)",
        "does not say whether",
        "explicit caller input",
        "comparison evidence",
        "not values reported by the source",
        "does not publish a qualified source-to-NeqSim tray mapping",
        "no positive liquid traffic",
        "not plant-calibration",
        "does not infer tray numbering",
    )
    for phrase in required:
        assert phrase in text


def test_sarir_pumparound_guide_exposes_fail_closed_java_surface():
    text = GUIDE.read_text(encoding="utf-8")

    required = (
        "SarirAtmosphericPumparoundScreen.Mapping",
        "SarirAtmosphericPumparoundScreen.configure",
        "screen.run(UUID.randomUUID())",
        "getModeledReturnMassFlowKgPerHour",
        "getSourceMassFlowKgPerHour",
        "getDutyW",
        "outer tear converged",
        "neither failed nor fallback",
    )
    for phrase in required:
        assert phrase in text
