"""Behavioral guards for new-debt checks and incomplete analyzer evidence."""
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import code_health as health


class CodeHealthTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / "src/main/java/neqsim/thermo/Example.java"
        self.path.parent.mkdir(parents=True)
        self.path.write_text("package neqsim.thermo;\nclass Example {}\n", encoding="utf-8")

    def test_new_dependency_fails_without_line_number_noise(self):
        old = health.make_baseline([], {"source"}, "base")
        self.path.write_text("import neqsim.process.Process;\nclass Example {}", encoding="utf-8")
        _, current, _ = health.inventory(self.root)
        introduced, _ = health.check_baseline(current, old, {"source"})
        self.assertEqual("CORE_LAYER_DEPENDENCY", introduced[0]["rule"])
        baseline = health.make_baseline(current, {"source"}, "base")
        self.path.write_text("\n\n" + self.path.read_text(), encoding="utf-8")
        self.assertEqual(([], []), health.check_baseline(health.inventory(self.root)[1], baseline, {"source"}))

    def test_comment_and_literal_do_not_create_dependencies_or_logging(self):
        self.path.write_text('/* import neqsim.process.Process; */\nclass Example {\n'
                             'String value="System.out.println(1);"; // System.err.print(2);\n}\n', encoding="utf-8")
        self.assertEqual([], health.inventory(self.root)[1])

    def test_multiline_logging_and_repeated_findings_are_counted(self):
        self.path.write_text('class Example { void run() { System\n.out.println("x");\n'
                             'System.out.println("y"); }}', encoding="utf-8")
        current = health.inventory(self.root)[1]
        self.assertEqual(2, len(current))
        baseline = health.make_baseline(current[:1], {"source"}, "base")
        self.assertEqual(1, sum(x["count"] for x in health.check_baseline(current, baseline, {"source"})[0]))

    def test_new_jama_import_is_a_finding(self):
        self.path.write_text("import Jama.Matrix;\nclass Example {}", encoding="utf-8")
        self.assertEqual("JAMA_IMPORT", health.inventory(self.root)[1][0]["rule"])

    def test_resolved_debt_is_reported(self):
        old = health.make_baseline([health.finding("source", "RULE", "file.java", "statement")], {"source"}, "base")
        introduced, resolved = health.check_baseline([], old, {"source"})
        self.assertEqual([], introduced)
        self.assertEqual(1, resolved[0]["count"])

    def test_invalid_baseline_and_unknown_tools_fail(self):
        with self.assertRaises(ValueError):
            health.baseline_counts({"schema_version": 2, "findings": []}, {"source"})
        self.assertEqual(1, health.main(["--root", str(self.root), "--tools", "unknown"]))

    def test_missing_selected_report_cannot_pass(self):
        self.assertEqual(1, health.main(["--root", str(self.root), "--tools", "pmd"]))

    def test_empty_checkout_cannot_pass(self):
        with tempfile.TemporaryDirectory() as empty:
            with self.assertRaises(ValueError):
                health.inventory(Path(empty))

    def test_pmd_priority_namespace_and_processing_error(self):
        path = self.root / "pmd.xml"
        path.write_text('<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0">'
                        '<file name="' + str(self.path) + '">'
                        '<violation rule="Bad" priority="1" beginline="2">message</violation>'
                        '<violation rule="Advisory" priority="3">message</violation></file></pmd>')
        findings = health.read_analyzer(path, "pmd", self.root)
        self.assertEqual(1, len(findings))
        self.assertEqual("src/main/java/neqsim/thermo/Example.java", findings[0]["path"])
        old = health.make_baseline(findings, {"pmd"}, "base")
        path.write_text(path.read_text().replace('priority="1"', 'priority="2"'))
        self.assertTrue(health.check_baseline(health.read_analyzer(path, "pmd", self.root), old, {"pmd"})[0])
        path.write_text('<pmd><processingerror msg="failed"/></pmd>')
        with self.assertRaises(ValueError):
            health.read_analyzer(path, "pmd", self.root)

    def test_spotbugs_completion_missing_classes_and_path(self):
        path = self.root / "bugs.xml"
        path.write_text('<BugCollection><FindBugsSummary total_classes="1" total_bugs="1"/><Errors errors="0" missingClasses="0"/>'
                        '<BugInstance type="BUG" priority="2"><Class classname="neqsim.thermo.Example"/>'
                        '<SourceLine sourcepath="neqsim/thermo/Example.java" start="3"/></BugInstance></BugCollection>')
        findings = health.read_analyzer(path, "spotbugs", self.root)
        self.assertEqual("src/main/java/neqsim/thermo/Example.java", findings[0]["path"])
        path.write_text(path.read_text().replace('missingClasses="0"', 'missingClasses="1"'))
        with self.assertRaises(ValueError):
            health.read_analyzer(path, "spotbugs", self.root)
        path.write_text('<BugCollection/>')
        with self.assertRaises(ValueError):
            health.read_analyzer(path, "spotbugs", self.root)
        path.write_text('<BugCollection><FindBugsSummary total_classes="1" total_bugs="1"/></BugCollection>')
        with self.assertRaises(ValueError):
            health.read_analyzer(path, "spotbugs", self.root)

    def test_malformed_and_wrong_report_fail(self):
        path = self.root / "bad.xml"
        path.write_text('<pmd>')
        with self.assertRaises(ET.ParseError):
            health.read_analyzer(path, "pmd", self.root)
        path.write_text('<other/>')
        with self.assertRaises(ValueError):
            health.read_analyzer(path, "pmd", self.root)

    def test_baseline_write_then_gate_and_unmeasured_coverage(self):
        baseline = self.root / "baseline.json"
        output = self.root / "result.json"
        args = ["--root", str(self.root), "--baseline", str(baseline), "--output", str(output)]
        self.assertEqual(0, health.main(args + ["--write-baseline", "--revision", "base"]))
        self.assertEqual(0, health.main(args))
        self.assertEqual("not_measured", json.loads(output.read_text())["coverage"]["status"])
        before = baseline.read_bytes()
        self.path.write_text("import Jama.Matrix; class Example {}")
        self.assertEqual(1, health.main(args))
        self.assertEqual(before, baseline.read_bytes())

    def test_opt_in_analyzer_profile_fails_on_execution_error(self):
        root = ET.parse(health.ROOT / "pom.xml").getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        profiles = root.findall("m:profiles/m:profile", ns)
        profile = next(p for p in profiles if p.findtext("m:id", namespaces=ns) == "code-health")
        self.assertEqual("true", profile.findtext(".//m:failOnError", namespaces=ns))
        self.assertEqual("2048", profile.findtext(".//m:maxHeap", namespaces=ns))
        self.assertEqual("-XX:ActiveProcessorCount=2", profile.findtext(".//m:jvmArgs", namespaces=ns))
        self.assertEqual("false", profile.findtext(".//m:xmlOutput", namespaces=ns))
        self.assertEqual("${project.build.directory}/code-health-input",
                         profile.findtext(".//m:classFilesDirectory", namespaces=ns))
        self.assertEqual("true", profile.findtext(".//m:nested", namespaces=ns))

    def test_required_check_contexts_exist_on_every_pr(self):
        import yaml
        workflow = yaml.load((health.ROOT / ".github/workflows/ai-code-health.yml").read_text(), Loader=yaml.BaseLoader)
        trigger = workflow["on"]["pull_request"]
        self.assertFalse(isinstance(trigger, dict) and ("paths" in trigger or "paths-ignore" in trigger))
        self.assertEqual({"Code health source contracts", "Code health static analysis"},
                         {job["name"] for job in workflow["jobs"].values()})
        self.assertTrue(all("if" not in job for job in workflow["jobs"].values()))

    def test_static_workflow_prepares_archive_after_clean_compile(self):
        import yaml
        workflow = yaml.load((health.ROOT / ".github/workflows/ai-code-health.yml").read_text(), Loader=yaml.BaseLoader)
        steps = workflow["jobs"]["static"]["steps"]
        commands = [step.get("run", "") for step in steps]
        compile_index = next(i for i, command in enumerate(commands) if "clean compile pmd:pmd" in command)
        prepare_index = next(i for i, command in enumerate(commands)
                             if command.startswith("uv run ") and "prepare_code_health_input.py" in command)
        analyze_index = next(i for i, command in enumerate(commands) if "spotbugs:spotbugs" in command)
        self.assertLess(compile_index, prepare_index)
        self.assertLess(prepare_index, analyze_index)
        java = next(step for step in steps if step.get("uses", "").startswith("actions/setup-java@"))
        self.assertEqual("17.0.21+9", java["with"]["java-version"])
        artifact = next(step for step in steps if step.get("uses", "").startswith("actions/upload-artifact@"))
        self.assertIn("target/code-health-input/manifest.json", artifact["with"]["path"])
        impact = next(step for step in steps if step.get("id") == "impact")
        self.assertIn("devtools/prepare_code_health_input.py", impact["run"])


if __name__ == "__main__":
    unittest.main()
