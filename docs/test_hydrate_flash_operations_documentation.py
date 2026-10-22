"""Source-anchored regression contracts for the hydrate flash operations guide."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "thermodynamicoperations" / "hydrate_flash_operations.md"
OPERATIONS = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "thermodynamicoperations"
    / "ThermodynamicOperations.java"
)
TP_HYDRATE_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "thermodynamicoperations"
    / "flashops"
    / "TPHydrateFlashTest.java"
)
TEMPERATURE_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "thermodynamicoperations"
    / "flashops"
    / "saturationops"
    / "HydrateFormationTemperatureFlashTest.java"
)
PITZER_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "thermodynamicoperations"
    / "flashops"
    / "saturationops"
    / "PitzerHydrateFlashTest.java"
)


def read(path: Path) -> str:
    """Read one repository text file."""

    return path.read_text(encoding="utf-8")


class HydrateFlashOperationsDocumentationContractTest(unittest.TestCase):
    def test_examples_use_repository_logging_contract(self) -> None:
        """Keep every reporting example on parameterized Log4j2 calls."""

        guide = read(GUIDE)
        complete_example = re.search(
            r"```java\n(?P<body>.*?public class HydrateAnalysis.*?)}\n```",
            guide,
            flags=re.DOTALL,
        )

        assert guide.startswith("---\n")
        assert "System.out" not in guide
        assert "System.err" not in guide
        assert guide.count("logger.info(") == 16
        assert "LogManager.getLogger(YourClass.class)" in guide
        assert complete_example is not None
        assert "import org.apache.logging.log4j.LogManager;" in complete_example.group(
            "body"
        )
        assert "import org.apache.logging.log4j.Logger;" in complete_example.group(
            "body"
        )
        assert (
            "private static final Logger logger = "
            "LogManager.getLogger(HydrateAnalysis.class);"
            in complete_example.group("body")
        )

    def test_equilibrium_line_example_uses_supported_api(self) -> None:
        """Reject the removed invented curve wrapper and data getter."""

        guide = read(GUIDE)
        operations = read(OPERATIONS)

        assert "calcHydrateEquilibriumLine" not in guide
        assert "get2DData" not in guide
        assert "ops.hydrateEquilibriumLine(1.0, 200.0);" in guide
        assert "ops.getOperation().getPoints(0)" in guide
        assert (
            "public void hydrateEquilibriumLine("
            "double minimumPressure, double maximumPressure)"
            in operations
        )
        assert "public OperationInterface getOperation()" in operations

    def test_documented_operations_exist_in_current_source(self) -> None:
        """Keep the guide's hydrate entry points aligned with source signatures."""

        guide = read(GUIDE)
        operations = read(OPERATIONS)
        contracts = (
            ("ops.hydrateTPflash();", "public void hydrateTPflash()"),
            ("ops.gasHydrateTPflash();", "public void gasHydrateTPflash()"),
            (
                "ops.hydrateFormationTemperature();",
                "public void hydrateFormationTemperature()",
            ),
            (
                "ops.hydrateFormationPressure();",
                "public void hydrateFormationPressure()",
            ),
            (
                'ops.hydrateInhibitorConcentration("MEG", 273.15 + 5.0);',
                "public void hydrateInhibitorConcentration(",
            ),
            (
                "ops.hydrateEquilibriumLine(1.0, 200.0);",
                "public void hydrateEquilibriumLine(",
            ),
        )
        for documented_call, source_signature in contracts:
            assert documented_call in guide
            assert source_signature in operations

    def test_principal_examples_have_executable_java_anchors(self) -> None:
        """Keep the repaired examples connected to focused Java regressions."""

        tp_hydrate = read(TP_HYDRATE_TEST)
        temperature = read(TEMPERATURE_TEST)
        pitzer = read(PITZER_TEST)

        assert "ops.hydrateTPflash();" in tp_hydrate
        assert "ops.gasHydrateTPflash();" in tp_hydrate
        assert "hydrateFormationTemperature();" in temperature
        assert "ops.hydrateFormationPressure();" in pitzer
        assert "new HydrateEquilibriumLine(fluid, 1.0, 3.0)" in pitzer
        assert "curve.getPoints(0)" in pitzer


if __name__ == "__main__":
    unittest.main()
