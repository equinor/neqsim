"""Regression contracts for the process serialization documentation."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "simulation" / "process_serialization.md"
PROCESS_GUIDE = ROOT / "docs" / "process" / "processmodel" / "process_system.md"
PROCESS_SOURCE = (
    ROOT / "src" / "main" / "java" / "neqsim" / "process" / "processmodel" / "ProcessSystem.java"
)
XSTREAM_SOURCE = (
    ROOT / "src" / "main" / "java" / "neqsim" / "util" / "serialization" / "NeqSimXtream.java"
)
PORTABILITY_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "processmodel"
    / "ProcessSystemXStreamPortabilityTest.java"
)


def read(path: Path) -> str:
    """Read one repository text file."""

    return path.read_text(encoding="utf-8")


def test_java_quick_start_checks_archive_failures() -> None:
    """The primary recipe must compile conceptually and check both documented failure signals."""

    guide = read(GUIDE)

    assert "import neqsim.thermo.system.SystemInterface;" in guide
    assert 'if (!process.saveToNeqsim("my_process.neqsim"))' in guide
    assert "if (loaded == null)" in guide
    assert "ProcessSystem.loadFromNeqsim() loads and runs it" in guide


def test_process_system_auto_load_semantics_match_source() -> None:
    """Do not imply that ProcessSystem.loadAuto can consume lifecycle JSON."""

    guide = read(GUIDE)
    source = read(PROCESS_SOURCE)

    assert "ProcessSystem.loadAuto() does **not** load lifecycle JSON" in guide
    assert 'if (lowerName.endsWith(".neqsim"))' in source
    assert "return loadFromNeqsim(filename);" in source
    assert "return open(filename);" in source


def test_python_recipe_checks_wrapper_failure_values() -> None:
    """Python users must not run a missing or partially written process."""

    guide = read(GUIDE)

    assert "if not neqsim.save_neqsim" in guide
    assert "if loaded_process is None:" in guide
    assert "loaded_process.run()" in guide


def test_failed_save_and_trust_boundaries_are_explicit() -> None:
    """Document partial-file behavior without teaching unrestricted XStream setup."""

    guide = read(GUIDE)
    source = read(XSTREAM_SOURCE)

    assert "partial or truncated destination can remain" in guide
    assert "Load `.neqsim` and XML files only from trusted sources" in guide
    assert "AnyTypePermission.ANY" not in guide
    assert "new FileOutputStream(filename)" in source
    assert "return false;" in source


def test_embedded_host_portability_guidance_is_source_anchored() -> None:
    """Keep the recent recycle portability fix discoverable and diagnostically useful."""

    guide = read(GUIDE)
    regression = read(PORTABILITY_TEST)

    assert "embedded hosts such as neqsim-python" in guide
    assert "`No converter available` error" in guide
    assert "recycle-bearing `ProcessSystem`" in guide
    assert "save_neqsim" in regression
    assert "No converter available" in regression
    assert "testProcessWithRecycleRoundTripsThroughXStream" in regression


def test_format_boundaries_and_process_entry_point_remain_discoverable() -> None:
    """Keep full-object archives distinct from selective lifecycle state at both entry points."""

    guide = read(GUIDE)
    process_guide = read(PROCESS_GUIDE)

    assert "selective state model rather than a lossless copy" in guide
    assert "Compression depends on the actual model graph" in guide
    assert "~500 KB" not in guide
    assert "Process Serialization Guide" in process_guide
    assert "does not load lifecycle JSON" in process_guide
    assert "saveToNeqsim" in process_guide
