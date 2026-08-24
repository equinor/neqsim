import csv
import re
import unittest
from pathlib import Path


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
GUIDE = DOCS_DIR / "standards" / "sales_contracts.md"
STANDARDS_INDEX = DOCS_DIR / "standards" / "README.md"
REFERENCE_INDEX = DOCS_DIR / "REFERENCE_MANUAL_INDEX.md"
BASE_CONTRACT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/standards/salescontract/BaseContract.java"
)
CONTRACT_INTERFACE = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/standards/salescontract/ContractInterface.java"
)
CONTRACT_SPECIFICATION = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/standards/salescontract/ContractSpecification.java"
)
CONTRACT_DATA = (
    REPOSITORY_ROOT
    / "src/main/resources/commercial/GASCONTRACTSPECIFICATIONS.csv"
)


class SalesContractsDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.base_contract = BASE_CONTRACT.read_text(encoding="utf-8")
        cls.contract_interface = CONTRACT_INTERFACE.read_text(encoding="utf-8")
        cls.contract_specification = CONTRACT_SPECIFICATION.read_text(
            encoding="utf-8"
        )
        with CONTRACT_DATA.open(encoding="utf-8", newline="") as data_file:
            cls.contract_rows = list(csv.DictReader(data_file))

    def test_structure_fences_and_internal_links_are_valid(self):
        self.assertTrue(self.guide.startswith("---\n"))
        without_fences = re.sub(
            r"```.*?```",
            "",
            self.guide,
            flags=re.DOTALL,
        )
        self.assertNotRegex(without_fences, re.compile(r"^# ", re.MULTILINE))
        self.assertEqual(self.guide.count("```"), 2)
        self.assertEqual(self.guide.count("```java"), 1)
        self.assertNotIn(r"\[", without_fences)
        self.assertNotIn(r"\(", without_fences)

        destinations = re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", self.guide)
        for destination in destinations:
            if destination.startswith(("http://", "https://", "mailto:")):
                continue
            target, _, _fragment = destination.partition("#")
            with self.subTest(destination=destination):
                self.assertTrue(target.endswith(".md"))
                self.assertTrue((GUIDE.parent / target).resolve().is_file())

    def test_complete_example_uses_the_current_database_backed_api(self):
        required_fragments = (
            "new SystemGERGwaterEos(268.15, 20.0)",
            "gas.setMixingRule(8)",
            'new BaseContract(gas, "central", "Brazil")',
            "contract.runCheck()",
            "contract.getResultTable()",
            "contract.getSpecificationsNumber()",
            "Double.parseDouble(rows[rowIndex][1])",
            "value >= minimum && value <= maximum",
        )
        for fragment in required_fragments:
            with self.subTest(fragment=fragment):
                self.assertIn(fragment, self.guide)
        self.assertNotIn("System.out.", self.guide)
        self.assertNotIn("contract.display()", self.guide)
        self.assertNotIn("contract.addSpecification", self.guide)
        self.assertNotIn("new ContractSpecification(", self.guide)

    def test_documented_public_api_and_result_columns_match_source(self):
        flattened = self.base_contract.replace("\n", " ")
        for signature in (
            "public BaseContract()",
            "public BaseContract(SystemInterface system)",
            "public BaseContract(SystemInterface system, String terminal, String country)",
            "public void runCheck()",
            "public String[][] getResultTable()",
            "public int getSpecificationsNumber()",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, flattened)

        combined_api = self.base_contract + self.contract_interface
        self.assertNotIn("addSpecification", combined_api)
        self.assertIn("extends NamedBaseClass", self.contract_specification)
        self.assertIn("new String[specificationsNumber][12]", self.base_contract)
        self.assertIn("There is no pass/fail column", self.guide)
        for column in range(12):
            self.assertIn("| {} |".format(column), self.guide)

    def test_method_keys_and_bundled_dataset_limitations_are_explicit(self):
        supported_keys = (
            "ISO18453",
            "ISO6974",
            "Total sulphur",
            "oxygen",
            "ISO6976",
            "SulfurSpecificationMethod",
            "BestPracticeHydrocarbonDewPoint",
            "UKspecifications",
        )
        for key in supported_keys:
            with self.subTest(key=key):
                self.assertIn('methodName.equals("{}")'.format(key), self.base_contract)
                self.assertIn("`{}`".format(key), self.guide)

        brazil_rows = [
            row
            for row in self.contract_rows
            if row["TERMINAL"] == "central" and row["COUNTRY"] == "Brazil"
        ]
        self.assertEqual(8, len(brazil_rows))
        self.assertEqual("CO2", brazil_rows[1]["SPECIFICATION"])
        self.assertEqual("0.0", brazil_rows[1]["MINVALUE"])
        self.assertEqual("3.0", brazil_rows[1]["MAXVALUE"])
        self.assertEqual("mol%", brazil_rows[1]["UNIT"])
        self.assertFalse(
            any(row["TERMINAL"] == "Kaarstoe" for row in self.contract_rows)
        )
        self.assertTrue(
            any(
                row["METHOD"] == "StatoilBestPracticeHydrocarbonDewPoint"
                for row in self.contract_rows
            )
        )
        self.assertIn("`StatoilBestPracticeHydrocarbonDewPoint`", self.guide)

    def test_loader_and_compliance_boundaries_are_not_overstated(self):
        normalized_guide = " ".join(self.guide.split())
        required_boundaries = (
            "not a general contract authoring API",
            "There is no pass/fail column",
            "return `true` unconditionally",
            "builds its database query by string concatenation",
            "supplies an empty string to `ContractSpecification`",
            "does not assign terminal or country to `contractName`",
            "write to standard output",
            "not a maintained register of current pipeline",
        )
        for boundary in required_boundaries:
            with self.subTest(boundary=boundary):
                self.assertIn(boundary, normalized_guide)

        self.assertIn("setSalesContract(this)", self.base_contract)
        self.assertIn("isOnSpec()", self.base_contract)
        self.assertIn('referencePressure, ""', self.base_contract)
        self.assertIn("this.setContractName(contractName)", self.base_contract)
        self.assertIn("System.out.println", self.base_contract)

    def test_existing_indexes_discover_the_guide(self):
        standards_index = STANDARDS_INDEX.read_text(encoding="utf-8")
        reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        self.assertIn("[Sales contracts](sales_contracts.md)", standards_index)
        self.assertIn("standards/sales_contracts.md", reference_index)


if __name__ == "__main__":
    unittest.main()
