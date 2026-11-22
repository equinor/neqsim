from pathlib import Path


GUIDE = Path(
    "docs/thermo/characterization/refinery_sarir_atmospheric_main_steam.md"
)


def test_sarir_main_steam_guide_preserves_source_and_engineering_boundaries():
    text = GUIDE.read_text(encoding="utf-8")

    required = (
        "10.66411/jer.v33i.46",
        "CC BY 4.0",
        "Main atmospheric column",
        "Kerosene side stripper",
        "Diesel side stripper",
        "Pressure (kPa, as reported)",
        "does not publish injection trays",
        "absolute-versus-gauge pressure basis",
        "not, by themselves, an executable stream specification",
        "does not infer a tray, pressure basis, quality, enthalpy, or phase state",
        "does not build a side stripper",
        "not tuning targets",
    )
    for phrase in required:
        assert phrase in text


def test_sarir_main_steam_guide_exposes_fail_closed_java_surface():
    text = GUIDE.read_text(encoding="utf-8")

    required = (
        "SarirAtmosphericMainSteamScreen.configure",
        "ReportedPressureBasis.ABSOLUTE",
        "ReportedPressureBasis.GAUGE",
        "setPhaseType(0, PhaseType.GAS)",
        "screen.run(UUID.randomUUID())",
        "getTotalMassClosureRelativeError",
        "exactly one gas phase",
        "crude plus steam",
        "fallback-rejection",
        "Java and JPype-accessible workflow",
    )
    for phrase in required:
        assert phrase in text
