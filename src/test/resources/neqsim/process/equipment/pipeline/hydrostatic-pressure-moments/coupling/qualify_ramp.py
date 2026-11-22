"""Replay the existing rich-gas fixture with pressure moments explicitly enabled.

Requires completed Maven test compilation. Keeps every accepted interval and rejection status.
Nonzero exit means the requested 120 s interval did not complete; no truncated pass is produced.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
REPO = next(path for path in ROOT.parents if (path / "pom.xml").is_file())


def main():
    """Compile and execute the opt-in replay on the focused-test classpath."""
    report = REPO / "target/surefire-reports/TEST-neqsim.process.equipment.pipeline.twophasepipe.StratifiedPressureMomentCouplingTest.xml"
    properties = {node.get("name"): node.get("value") for node in ET.parse(report).getroot().find("properties")}
    classpath = properties["java.class.path"]
    with tempfile.TemporaryDirectory() as temporary:
        subprocess.run(["java", "--module", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8",
                        "-cp", classpath, "-d", temporary, str(ROOT / "CoupledRichGasRampProbe.java")], check=True)
        subprocess.run(["java", "-XX:-UsePerfData", "-cp", temporary + os.pathsep + classpath,
                        "CoupledRichGasRampProbe", str(ROOT / "optin-ramp.csv"), str(ROOT / "optin-ramp-status.txt")],
                       check=True)


if __name__ == "__main__":
    main()
