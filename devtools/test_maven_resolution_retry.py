"""Execute the CI retry steps against controlled Maven outcomes."""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/core-run-build-test-and-javadoc.yml"
SUREFIRE_RESOLUTION_FAILURE = (
    "[ERROR] Failed to execute goal "
    "org.apache.maven.plugins:maven-surefire-plugin:3.6.1:test (default-test) "
    "on project neqsim: The following artifacts could not be resolved: "
    "org.apache.maven.surefire:surefire-junit-platform:jar:3.6.1 (absent): "
    "Could not find artifact org.apache.maven.surefire:surefire-junit-platform:jar:3.6.1 "
    "in central (https://repo.maven.apache.org/maven2/) -> [Help 1]\n"
    "[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoExecutionException\n"
)
SUREFIRE_DESCRIPTOR_FAILURE = (
    "[ERROR] Failed to execute goal "
    "org.apache.maven.plugins:maven-surefire-plugin:3.6.1:test (default-test) "
    "on project neqsim: Failed to collect dependencies at "
    "org.apache.maven.surefire:surefire-junit-platform:jar:3.6.1: "
    "Failed to read artifact descriptor for "
    "org.apache.maven.surefire:surefire-junit-platform:jar:3.6.1: "
    "The following artifacts could not be resolved: "
    "org.apache.maven.surefire:surefire-junit-platform:pom:3.6.1 (absent): "
    "Could not transfer artifact org.apache.maven.surefire:surefire-junit-platform:pom:3.6.1 "
    "from/to central (https://repo.maven.apache.org/maven2/): "
    "status code: 403, reason phrase: Forbidden (403) -> [Help 1]\n"
    "[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoExecutionException\n"
)


@unittest.skipUnless(shutil.which("bash"), "CI retry steps require Bash")
class MavenResolutionRetryTest(unittest.TestCase):
    """Keep retries bounded and preserve substantive build failures."""

    def run_step(self, job, pom, failure_log, succeed_on):
        """Run the actual workflow step with a fake Maven executable."""
        workflow = yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))
        steps = workflow["jobs"][job]["steps"]
        step = next(step for step in steps if "for attempt in 1 2" in step.get("run", ""))
        script = step["run"].replace("${{ matrix.pom }}", pom)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            binaries = root / "bin"
            binaries.mkdir()
            test_file = root / "src/test/java/example/ExampleTest.java"
            test_file.parent.mkdir(parents=True)
            test_file.write_text('@Tag("slow")\nclass ExampleTest {}\n', encoding="utf-8")
            (root / "failure.log").write_text(failure_log, encoding="utf-8")
            (root / "calls").write_text("0", encoding="utf-8")
            (binaries / "mvn").write_text(
                '#!/usr/bin/env bash\n'
                'count=$(cat "$RUNNER_TEMP/calls")\n'
                'count=$((count + 1))\n'
                'printf "%s" "$count" > "$RUNNER_TEMP/calls"\n'
                'printf "%s\\n" "$*" >> "$RUNNER_TEMP/arguments"\n'
                'if ((count < MVN_SUCCEED_ON)); then\n'
                '  cat "$RUNNER_TEMP/failure.log"\n'
                '  exit 17\n'
                'fi\n'
                'echo "[INFO] BUILD SUCCESS"\n',
                encoding="utf-8",
            )
            (binaries / "sleep").write_text(
                '#!/usr/bin/env bash\n'
                'printf "%s\\n" "$*" >> "$RUNNER_TEMP/sleeps"\n',
                encoding="utf-8",
            )
            for executable in binaries.iterdir():
                executable.chmod(0o755)
            environment = dict(
                os.environ,
                PATH=str(binaries) + os.pathsep + os.environ["PATH"],
                RUNNER_TEMP=str(root),
                SHARD_INDEX="0",
                SHARD_COUNT="1",
                MVN_SUCCEED_ON=str(succeed_on),
            )
            result = subprocess.run(
                ["bash", "-e", "-c", script],
                cwd=root,
                env=environment,
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            calls = int((root / "calls").read_text(encoding="utf-8"))
            arguments = (root / "arguments").read_text(encoding="utf-8").splitlines()
            sleeps_path = root / "sleeps"
            sleeps = (
                sleeps_path.read_text(encoding="utf-8").splitlines() if sleeps_path.exists() else []
            )
            self.assertEqual(len(arguments), calls)
            self.assertTrue(all(arguments[0] == args for args in arguments))
            self.assertIn("-U", arguments[0].split())
            if job == "model_spec":
                self.assertIn("-f " + pom, arguments[0])
                self.assertIn("-Dtest=ModelSpec*Test,ComponentCorrelationSpecTest", arguments[0])
            else:
                self.assertIn("-Dtest=example.ExampleTest", arguments[0])
                self.assertIn("-Dgroups=slow", arguments[0])
                self.assertIn("-DexcludedTestGroups=", arguments[0])
            return result, calls, sleeps

    def contexts(self):
        """Cover the slow shard and both Java model-specification POMs."""
        return (
            ("test_java_slow", "pom.xml"),
            ("model_spec", "pom.xml"),
            ("model_spec", "pomJava8.xml"),
        )

    def test_provider_resolution_retries_and_recovers(self):
        for job, pom in self.contexts():
            for failure in (SUREFIRE_RESOLUTION_FAILURE, SUREFIRE_DESCRIPTOR_FAILURE):
                with self.subTest(job=job, pom=pom, failure=failure):
                    result, calls, sleeps = self.run_step(job, pom, failure, 2)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertEqual(calls, 2)
                    self.assertEqual(sleeps, ["10"])

    def test_resolution_retries_are_bounded_and_preserve_exit_status(self):
        failures = (
            SUREFIRE_RESOLUTION_FAILURE,
            SUREFIRE_DESCRIPTOR_FAILURE,
            "[ERROR] [Help 1] https://cwiki.apache.org/confluence/display/MAVEN/PluginResolutionException\n",
            "[ERROR] [Help 2] http://cwiki.apache.org/confluence/display/MAVEN/DependencyResolutionException\n",
        )
        for job, pom in self.contexts():
            for failure in failures:
                with self.subTest(job=job, pom=pom, failure=failure):
                    result, calls, sleeps = self.run_step(job, pom, failure, 3)
                    self.assertEqual(result.returncode, 17, result.stdout + result.stderr)
                    self.assertEqual(calls, 2)
                    self.assertEqual(sleeps, ["10"])

    def test_compilation_test_and_other_mojo_failures_do_not_retry(self):
        failures = (
            "[ERROR] COMPILATION ERROR :\n[ERROR] cannot find symbol\n"
            "[ERROR] [Help 1] https://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException\n",
            "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.6.1:test "
            "(default-test) on project neqsim: There are test failures.\n"
            "[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException\n",
            "[ERROR] The forked VM terminated without properly saying goodbye.\n"
            "[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoExecutionException\n",
            SUREFIRE_DESCRIPTOR_FAILURE.replace(
                "org.apache.maven.surefire:surefire-junit-platform", "example:unrelated-library"
            ),
            SUREFIRE_DESCRIPTOR_FAILURE.replace(
                "maven-surefire-plugin:3.6.1:test", "maven-compiler-plugin:3.16.0:compile"
            ),
        )
        for job, pom in self.contexts():
            for failure in failures:
                with self.subTest(job=job, pom=pom, failure=failure):
                    result, calls, sleeps = self.run_step(job, pom, failure, 2)
                    self.assertEqual(result.returncode, 17, result.stdout + result.stderr)
                    self.assertEqual(calls, 1)
                    self.assertEqual(sleeps, [])

    def test_success_does_not_retry(self):
        for job, pom in self.contexts():
            with self.subTest(job=job, pom=pom):
                result, calls, sleeps = self.run_step(job, pom, "", 1)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(calls, 1)
                self.assertEqual(sleeps, [])


if __name__ == "__main__":
    unittest.main()
