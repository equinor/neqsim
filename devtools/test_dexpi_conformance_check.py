import importlib.util
from pathlib import Path
import tempfile
import unittest
import unittest.mock

SCRIPT = Path(__file__).with_name("dexpi_conformance_check.py")
SPEC = importlib.util.spec_from_file_location("dexpi_conformance_check", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

MODEL = (
    '<Model name="x"><Import prefix="Core" source="https://data.dexpi.org/models/{v}/Core.xml"/>'
    '<Import prefix="{p}" source="https://data.dexpi.org/models/{v}/{p}.xml"/></Model>'
)


def write(text):
    handle = tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False, encoding="utf-8")
    handle.write(text)
    handle.close()
    return Path(handle.name)


class DexpiConformanceCheckTest(unittest.TestCase):
    def test_version_and_profile_come_from_imports_not_the_file_name(self):
        path = write(MODEL.format(v="2.0.1", p="Plant"))
        identity = MODULE.identify(path)
        self.assertEqual(("DEXPI_2X", "2.0.1", "Plant"),
                         (identity["kind"], identity["specificationVersion"], identity["profile"]))
        process = MODULE.identify(write(MODEL.format(v="2.0.0", p="Process")))
        self.assertEqual(("2.0.0", "Process"), (process["specificationVersion"], process["profile"]))

    def test_proteus_is_not_a_dexpi_2_document(self):
        identity = MODULE.identify(write('<PlantModel SchemaVersion="4.2.0"/>'))
        self.assertEqual("PROTEUS_1X", identity["kind"])
        report = {"identity": identity, "neqsimAssessment": {"status": "NOT_APPLICABLE"},
                  "pyDexpi": {"status": "NOT_RUN"}}
        self.assertEqual("FAIL", MODULE.verdict(report, None)[0])

    def test_201_expectation_rejects_a_200_file(self):
        identity = MODULE.identify(write(MODEL.format(v="2.0.0", p="Plant")))
        report = {"identity": identity, "neqsimAssessment": {"status": "CONFORMANT"},
                  "pyDexpi": {"status": "PASSED"}}
        self.assertEqual("FAIL", MODULE.verdict(report, "2.0.1")[0])
        self.assertEqual("PASS", MODULE.verdict(report, "2.0.0")[0])

    def test_missing_layers_never_give_a_full_pass(self):
        identity = MODULE.identify(write(MODEL.format(v="2.0.1", p="Plant")))
        conformant = {"identity": identity, "neqsimAssessment": {"status": "CONFORMANT"},
                      "pyDexpi": {"status": "NOT_RUN"}}
        self.assertEqual("PASS_NEQSIM_ONLY", MODULE.verdict(conformant, "2.0.1")[0])
        skipped = {"identity": identity, "neqsimAssessment": {"status": "NOT_RUN"},
                   "pyDexpi": {"status": "PASSED"}}
        self.assertEqual("UNVERIFIED", MODULE.verdict(skipped, "2.0.1")[0])
        failing = {"identity": identity, "neqsimAssessment": {"status": "NONCONFORMANT"},
                   "pyDexpi": {"status": "PASSED"}}
        self.assertEqual("FAIL", MODULE.verdict(failing, "2.0.1")[0])


    def test_pydexpi_report_is_summarised_and_missing_tool_is_not_a_pass(self):
        self.assertEqual("NOT_RUN", MODULE.run_pydexpi(Path("x.xml"), None)["status"])
        payload = ('{"loaded": true, "loader": [{"level": "warning"}], "issues": [{"level": "Mandatory",'
                   ' "rule": "pydexpi_segment_connectivity"}]}')
        done = unittest.mock.Mock(returncode=1, stdout=payload)
        with unittest.mock.patch.object(MODULE.subprocess, "run", return_value=done), \
                unittest.mock.patch.object(MODULE, "_pydexpi_version", return_value="2.1.0"):
            result = MODULE.run_pydexpi(Path("x.xml"), "pydexpi")
        self.assertEqual("ISSUES_FOUND", result["status"])
        self.assertEqual(1, result["loaderWarningCount"])
        self.assertEqual("pydexpi_segment_connectivity", result["issuesByRule"][0]["rule"])


if __name__ == "__main__":
    unittest.main()
