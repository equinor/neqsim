import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
GUIDE = DOCS_DIR / "process" / "equipment" / "util" / "calculators.md"
PROCESS_OVERVIEW = DOCS_DIR / "process" / "README.md"
CALCULATOR = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/util/Calculator.java"
)
CALCULATOR_LIBRARY = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/util/CalculatorLibrary.java"
)
SETTER = REPOSITORY_ROOT / "src/main/java/neqsim/process/equipment/util/Setter.java"
SET_POINT = (
    REPOSITORY_ROOT / "src/main/java/neqsim/process/equipment/util/SetPoint.java"
)
MOLE_FRACTION = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/util/MoleFractionControllerUtil.java"
)
GRAPH_BUILDER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/processmodel/graph/ProcessGraphBuilder.java"
)
RECYCLE_REGRESSION = (
    REPOSITORY_ROOT
    / "src/test/java/neqsim/process/processmodel/"
    "CalculatorRecycleHybridExecutionTest.java"
)


def java_fences(content):
    return re.findall(r"\x60\x60\x60java\n(.*?)\x60\x60\x60", content, re.DOTALL)


def heading_slugs(content):
    content_without_fences = re.sub(
        r"\x60\x60\x60.*?\x60\x60\x60", "", content, flags=re.DOTALL
    )
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower())
        .strip()
        .replace(" ", "-")
        for heading in re.findall(
            r"^#{1,6}\s+(.+)$", content_without_fences, flags=re.MULTILINE
        )
    }


class CalculatorDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.overview = PROCESS_OVERVIEW.read_text(encoding="utf-8")
        cls.calculator = CALCULATOR.read_text(encoding="utf-8")
        cls.library = CALCULATOR_LIBRARY.read_text(encoding="utf-8")
        cls.setter = SETTER.read_text(encoding="utf-8")
        cls.set_point = SET_POINT.read_text(encoding="utf-8")
        cls.mole_fraction = MOLE_FRACTION.read_text(encoding="utf-8")
        cls.graph_builder = GRAPH_BUILDER.read_text(encoding="utf-8")
        cls.recycle_regression = RECYCLE_REGRESSION.read_text(encoding="utf-8")

    def test_structure_fences_and_internal_links(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertEqual(self.guide.count("\x60\x60\x60") % 2, 0)
        without_fences = re.sub(
            r"\x60\x60\x60.*?\x60\x60\x60",
            "",
            self.guide,
            flags=re.DOTALL,
        )
        self.assertNotRegex(without_fences, re.compile(r"^# ", re.MULTILINE))

        link_pattern = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
        for destination in link_pattern.findall(self.guide):
            if destination.startswith(("http://", "https://", "mailto:")):
                continue
            target, _, fragment = unquote(destination).partition("#")
            target_path = (GUIDE.parent / target).resolve()
            with self.subTest(destination=destination):
                self.assertTrue(target.endswith((".md", ".java")))
                self.assertTrue(target_path.is_file())
                if fragment:
                    self.assertIn(
                        fragment,
                        heading_slugs(target_path.read_text(encoding="utf-8")),
                    )

    def test_calculator_api_and_error_boundary_match_source(self):
        for contract in (
            "public void addInputVariable(ProcessEquipmentInterface unit)",
            "public void addInputVariable(ProcessEquipmentInterface... units)",
            "public void setOutputVariable(ProcessEquipmentInterface outputVariable)",
            "BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface>",
            "public void setCalculationMethod(Runnable calculationMethod)",
            'logger.error("Error in custom calculation", ex);',
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, self.calculator)

        self.assertNotIn("setExpression(", self.calculator)
        self.assertNotRegex(
            self.calculator,
            re.compile(r"addInputVariable\([^)]*String"),
        )
        self.assertIn("callback exceptions are logged", self.guide)
        self.assertIn("does not provide a fail-fast process contract", self.guide)

    def test_every_documented_preset_exists(self):
        for preset in (
            "ENERGY_BALANCE",
            "DEW_POINT_TARGETING",
            "ANTI_SURGE",
        ):
            with self.subTest(preset=preset):
                self.assertRegex(
                    self.library,
                    re.compile(r"\b" + preset + r"\b"),
                )
                self.assertIn("\x60" + preset + "\x60", self.guide)

        for factory in (
            "energyBalance()",
            "dewPointTargeting()",
            "antiSurge()",
            "byName(String presetName)",
        ):
            with self.subTest(factory=factory):
                self.assertIn(factory, self.library)

    def test_setter_set_point_and_composition_apis_match_source(self):
        for contract in (
            "addTargetEquipment(ProcessEquipmentInterface equipment)",
            "addParameter(String type, String unit, double value)",
        ):
            self.assertIn(contract, self.setter)

        for stale_method in (
            "setEquipment(",
            "setProperty(",
            "setValue(",
            "setUnit(",
        ):
            self.assertNotIn(stale_method, self.setter)

        for contract in (
            "setSourceVariable(ProcessEquipmentInterface adjustedEquipment",
            "setTargetVariable(ProcessEquipmentInterface targetEquipment",
            "setMultiplier(double multiplier)",
            "setOffset(double offset)",
            "setSourceValueCalculator(Function<ProcessEquipmentInterface, Double>",
        ):
            self.assertIn(contract, self.set_point)

        self.assertIn(
            "public MoleFractionControllerUtil(StreamInterface inletStream)",
            self.mole_fraction,
        )
        self.assertIn(
            "setMoleFraction(String compName, double moleFrac)",
            self.mole_fraction,
        )
        self.assertNotIn("setTargetMoleFraction", self.mole_fraction)
        self.assertIn(
            "not algebraically\nguaranteed to equal the requested value",
            self.guide,
        )

    def test_stale_calls_are_absent_from_java_examples(self):
        stale_calls = (
            ".setExpression(",
            ".addInputVariable(stream1, \x22",
            ".setOutputVariable(heater, \x22",
            ".setEquipment(",
            ".setProperty(",
            ".setValue(",
            ".setUnit(",
            ".setTargetMoleFraction(",
        )
        for document in (self.guide, self.overview):
            for block in java_fences(document):
                for stale_call in stale_calls:
                    with self.subTest(stale_call=stale_call):
                        self.assertNotIn(stale_call, block)

        self.assertIn(
            "calculator.setCalculationMethod((inputs, output) -> {",
            self.guide,
        )
        self.assertIn(
            "calc.setCalculationMethod((inputs, output) -> {",
            self.overview,
        )

    def test_registered_calculator_edges_cover_recycle_iteration(self):
        for contract in (
            "if (unit instanceof Calculator)",
            "((Calculator) unit).getOutputVariable()",
            "for (ProcessEquipmentInterface inputEquip : calc.getInputVariable())",
            "addSignalEdgeIfAbsent(graph, signalSource, calc",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, self.graph_builder)

        self.assertIn(
            "calculatorRuns.get() > 1",
            self.recycle_regression,
        )
        self.assertIn(
            "calculator must be re-evaluated as the recycle state changes",
            self.recycle_regression,
        )
        self.assertIn("recycle strongly connected component", self.guide)


if __name__ == "__main__":
    unittest.main()
