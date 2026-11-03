"""Reproduce the dry/wet pressure impulse and reject an unsafe volume-coordinate candidate.

The production source is never edited. The one-line experimental domain change is compiled
into an isolated precedence classpath, with all EOS/energy/admissibility checks unchanged.
Exit 1 means the candidate did not complete both requested 120-second ramps.
"""
import difflib
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
REPO = next(path for path in ROOT.parents if (path / "pom.xml").is_file())
SOURCE = REPO / "src/main/java/neqsim/process/equipment/pipeline/twophasepipe/TwoFluidConservationEquations.java"


def main():
    """Save the exact experiment, complete accepted intervals, failure and primary rollback checks."""
    raw = SOURCE.read_bytes()
    blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    if blob != "704966f147522007f13c535ae1d65a8492e0c759":
        raise ValueError("Reassess the experiment against the changed production source")
    before = "if (!Double.isFinite(closure) || Math.abs(closure - 1.0) > 1.0e-10) {"
    after = "if (!Double.isFinite(closure) || !(closure > 0.0)) {"
    original = raw.decode("utf-8")
    if original.count(before) != 1:
        raise ValueError("Experimental source substitution is not unique")
    candidate = original.replace(before, after)
    (ROOT / "stage-coordinate-change.patch").write_text("".join(difflib.unified_diff(
        original.splitlines(True), candidate.splitlines(True), fromfile="production", tofile="experiment")))
    report = REPO / "target/surefire-reports/TEST-neqsim.process.equipment.pipeline.twophasepipe.StratifiedPressureMomentCouplingTest.xml"
    properties = {node.get("name"): node.get("value") for node in ET.parse(report).getroot().find("properties")}
    classpath = properties["java.class.path"]
    with tempfile.TemporaryDirectory() as temporary:
        temporary = Path(temporary)
        probes = temporary / "probes"
        classes = temporary / "candidate"
        source = temporary / "TwoFluidConservationEquations.java"
        source.write_text(candidate)
        compile_command = ["java", "--module", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8", "-cp", classpath]
        subprocess.run(compile_command + ["-d", str(probes), str(ROOT / "DryWetPressureProbe.java"),
                                         str(ROOT / "CoupledRichGasRampProbe.java")], check=True)
        subprocess.run(["java", "-cp", str(probes) + os.pathsep + classpath,
                        "DryWetPressureProbe", str(ROOT / "dry-wet-pressure.csv")], check=True)
        subprocess.run(compile_command + ["-d", str(classes), str(source)], check=True)
        result = subprocess.run(["java", "-XX:-UsePerfData", "-cp",
                                 os.pathsep.join([str(classes), str(probes), classpath]),
                                 "CoupledRichGasRampProbe", str(ROOT / "stage-coordinate-ramp.csv"),
                                 str(ROOT / "stage-coordinate-ramp-status.txt")])
    receipt = {"source_head": "8e88e8755a3422474ca1a50402bff5935858887d", "source_blob": blob,
               "candidate_sha256": hashlib.sha256(candidate.encode()).hexdigest(),
               "candidate_exit_code": result.returncode, "production_changed": False,
               "eos_energy_endpoint_tolerances_changed": False,
               "interpretation": "Diagnostic candidate only; no pooling or physical-rate qualification"}
    (ROOT / "stage-coordinate-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
