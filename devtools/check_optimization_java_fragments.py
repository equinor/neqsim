"""Compile the documented optimization fragments and execute their process fixtures.

Run with a JDK11+ and compiled repository classes::

    python devtools/check_optimization_java_fragments.py

Maven must first write dependencies to target/optimization-classpath.txt. Every
run extracts current Markdown: no copied tutorial bodies are committed here.
The complete tutorials additionally run in the Java documentation JUnit tests.
Generated source, manifests, logs and exported tables remain below target.
"""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = Path(__file__).resolve().parent / "optimization_java_fragments"
JAVA_FENCE = re.compile(r"^```java\n(.*?)^```", re.MULTILINE | re.DOTALL)
MIXED_FENCE = re.compile(r"^```(java|python)\n(.*?)^```", re.MULTILINE | re.DOTALL)
IMPORT = re.compile(r"^import .*?;\s*", re.MULTILINE)
EXPECTED_OPTIMIZER_COUNTS = {
    "batch-studies": 9,
    "constraint-framework": 18,
    "flow-rate-optimization": 17,
    "multi-objective-optimization": 12,
    "sqp_optimizer": 5,
    "process-researcher": 7,
    "data-reconciliation": 28,
}
DESIGN_REFERENCES = {
    7: "AutoSizeable API signature reference",
    12: "ProcessTemplate API signature reference",
    21: "SQL schema example outside the optimization workflow",
    22: "Company-standard extension sketch outside the optimization workflow",
    23: "Wall-thickness equation outside the optimization workflow",
}

OPTIMIZER_IMPORTS = """import java.util.*;
import java.time.Duration;
import org.apache.logging.log4j.*;
import neqsim.process.processmodel.*;
import neqsim.thermo.system.*;
import neqsim.process.equipment.stream.*;
import neqsim.process.equipment.compressor.*;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.equipment.separator.*;
import neqsim.process.util.optimizer.*;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.process.util.optimizer.BatchStudy.*;
import neqsim.process.util.reconciliation.*;
import neqsim.process.research.*;
import neqsim.process.equipment.capacity.CapacityConstraint;
"""

OPTIMIZER_COMMON = """static final Logger logger=LogManager.getLogger("documentation");
static ProcessSystem process, baseCase, processSystem;
static Stream feedStream;
static SystemSrkEos fluid, gasFluid;
static Compressor compressor, comp1, comp2;
static Separator separator;
"""

OPTIMIZER_FIELDS = {
    'batch-studies': """static Stream feed;
static BatchStudyResult result;
static BatchStudy study;
""",
    'constraint-framework': """static Stream feed;
static ProcessSimulationEvaluator evaluator;
static ConstraintPenaltyCalculator calc;
static OptimizationConstraint powerLimit, minExport;
static ProcessSimulationEvaluator.ConstraintDefinition external;
""",
    'flow-rate-optimization': """static Stream feed;
static SystemSrkEos gas;
static ProcessModel processModel;
static FlowRateOptimizer optimizer;
static FlowRateOptimizer.ProcessCapacityTable table;
static FlowRateOptimizer.ProcessOperatingPoint point;
static FlowRateOptimizer.LiftCurveResult result;
static double[] inletPressures, outletPressures;
""",
    'multi-objective-optimization': """static Stream feed;
static List<ObjectiveFunction> objectives;
static OptimizationConfig config;
static ParetoFront front;
""",
    'sqp_optimizer': """""",
    'process-researcher': """static ProcessResearchSpec spec;
""",
    'data-reconciliation': """static ReconciliationVariable feed, gas, liq;
static DataReconciliationEngine engine;
static SteadyStateDetector detector, ssd;
static Map<String,Double> snapshot;
""",
}

CAPACITY_IMPORTS = """import java.nio.file.*;
import java.util.*;
import neqsim.process.automation.*;
import neqsim.process.equipment.*;
import neqsim.process.equipment.capacity.*;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.*;
import neqsim.process.equipment.expander.*;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.equipment.manifold.*;
import neqsim.process.equipment.pipeline.*;
import neqsim.process.equipment.pump.*;
import neqsim.process.equipment.separator.*;
import neqsim.process.equipment.stream.*;
import neqsim.process.equipment.valve.*;
import neqsim.process.mechanicaldesign.pipeline.*;
import neqsim.process.mechanicaldesign.separator.*;
import neqsim.process.processmodel.*;
import neqsim.process.util.optimizer.*;
import neqsim.process.util.optimizer.PressureBoundaryOptimizer.LiftCurveTable;
import neqsim.thermo.system.*;
import org.apache.logging.log4j.*;
"""

CAPACITY_FIELDS = """
static Logger logger = LogManager.getLogger("CapacityFragments");
SystemSrkEos fluid;
Stream feed,feedStream,gasStream,inlet1,inlet2,outlet;
ProcessSystem process,processSystem,separationSystem,compressionSystem;
ProcessModule processModule,productionModule;
ProcessModel model,plant;
ProcessEquipmentInterface equipment;
Compressor compressor,lpCompressor,hpCompressor;
Separator separator,hpSeparator,lpSeparator;
Heater heater;
Pump pump;
HeatExchanger exchanger;
PipeBeggsAndBrills pipe,pipeline,exportPipeline,flowline,riser;
Manifold inletManifold,subseaManifold;
CapacityConstraint constraint;
EquipmentCapacityStrategyRegistry registry;
EclipseVFPExporter exporter;
neqsim.process.examples.OilGasProcessSimulationOptimization simulation;
String calculationId;
double installedKw;
"""


def java_tool(name):
    """Use the caller's JDK, including JAVA_HOME launchers in constrained environments."""
    java_home = os.environ.get("JAVA_HOME")
    executable = name + (".exe" if os.name == "nt" else "")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(executable)
    if found:
        return found
    raise RuntimeError(f"{name} is unavailable; select a JDK11+ with JAVA_HOME or PATH")


def source_classpath():
    explicit = os.environ.get("NEQSIM_TEST_CLASSPATH")
    if explicit:
        return explicit.split(os.pathsep)
    dependency_file = ROOT / "target/optimization-classpath.txt"
    if not dependency_file.is_file() or not (ROOT / "target/classes").is_dir():
        raise RuntimeError("Compile the repository and generate target/optimization-classpath.txt first")
    return [str(ROOT / "target/classes")] + dependency_file.read_text().strip().split(os.pathsep)


def read_fences(document, expected, after=None):
    markdown = (ROOT / document).read_text(encoding="utf-8")
    if after:
        if after not in markdown:
            raise AssertionError(f"Missing documented section {after!r} in {document}")
        markdown = markdown[markdown.index(after):]
    blocks = JAVA_FENCE.findall(markdown)
    if len(blocks) != expected:
        raise AssertionError(f"{document}: expected {expected} Java fences, found {len(blocks)}; update coverage")
    return blocks


def imports_and_body(code):
    imports = [match.group().strip() for match in IMPORT.finditer(code)]
    return imports, IMPORT.sub("", code).strip()


def write_source(directory, name, source):
    path = directory / (name + ".java")
    path.write_text(source, encoding="utf-8")
    return path


def optimizer_sources(directory, manifest):
    files = []
    for name, fields in OPTIMIZER_FIELDS.items():
        document = f"docs/process/optimization/{name}.md"
        markdown = (ROOT / document).read_text(encoding="utf-8")
        count = 0
        for ordinal, match in enumerate(MIXED_FENCE.finditer(markdown)):
            if match.group(1) != "java":
                continue
            count += 1
            extra, code = imports_and_body(match.group(2))
            code = re.sub(r"^package .*?;\s*", "", code, flags=re.MULTILINE)
            class_name = "".join(word.title() for word in name.replace("_", "-").split("-"))
            class_name += f"Snippet{ordinal}"
            declarations = fields
            if name == "data-reconciliation":
                result_type = "SteadyStateResult" if ordinal < 10 else "ReconciliationResult"
                declarations += f"static {result_type} result;\n"
            if name == "sqp_optimizer":
                extra.append("import neqsim.process.util.optimizer.SQPoptimizer.OptimizationResult;")
            if code.startswith("public interface "):
                body = code
            elif "public class SeparatorReconciliation" in code:
                body = code.replace("public class SeparatorReconciliation", "public static class SeparatorReconciliation")
            else:
                body = "public static void example() throws Exception {\n" + code + "\n}"
            source = (OPTIMIZER_IMPORTS + "\n".join(extra) + f"\npublic class {class_name} {{\n"
                      + OPTIMIZER_COMMON + declarations + body + "\n}\n")
            files.append(write_source(directory, class_name, source))
            manifest.append({"document": document, "mixed_fence": ordinal, "class": class_name,
                             "coverage": "compiled; complete examples also execute in JUnit"})
        if count != EXPECTED_OPTIMIZER_COUNTS[name]:
            raise AssertionError(f"{document}: expected {EXPECTED_OPTIMIZER_COUNTS[name]} Java fences, found {count}")
    return files


def capacity_sources(directory, manifest):
    document = "docs/process/CAPACITY_CONSTRAINT_FRAMEWORK.md"
    markdown = (ROOT / document).read_text(encoding="utf-8")
    matches = list(JAVA_FENCE.finditer(markdown))
    strict_sections = list(re.finditer(r"^### Strict piping evidence\s*$", markdown, re.MULTILINE))
    if len(strict_sections) > 1:
        raise AssertionError(f"{document}: duplicate strict piping evidence sections")
    strict_match = None
    if strict_sections:
        section_start = strict_sections[0].end()
        next_section = re.search(r"^#{1,3} ", markdown[section_start:], re.MULTILINE)
        section_end = section_start + next_section.start() if next_section else len(markdown)
        candidates = [match for match in matches if section_start <= match.start() < section_end]
        if len(candidates) != 1 or not re.match(
                r"\s*PlantPipelineEvidence\s+pipelineEvidence\s*=\s*PlantPipelineEvidence\s*\.builder\(",
                candidates[0].group(1)):
            raise AssertionError(f"{document}: unknown strict piping example; update its dedicated fixture")
        strict_match = candidates[0]
    regular = [match for match in matches if match is not strict_match]
    if len(regular) != 60:
        raise AssertionError(f"{document}: expected 60 base Java fences and the optional strict piping example; "
                             f"found {len(regular)} base fences")
    if any("PlantPipelineEvidence" in match.group(1) for match in regular):
        raise AssertionError(f"{document}: unexpected PlantPipelineEvidence block outside the recognized section")
    blocks = [match.group(1) for match in regular]
    imports = set(CAPACITY_IMPORTS.splitlines())
    members = []
    files = []
    for ordinal, code in enumerate(blocks):
        extra, code = imports_and_body(code)
        imports.update(extra)
        coverage = "compiled member or executable method"
        if not code:
            coverage = "imports compiled in CapacityFragments"
        elif code.startswith("public interface "):
            # Compile the public API sketch separately so its simple name cannot
            # shadow the actual imported capacity interface in executable examples.
            name = f"CapacityApiReference{ordinal}"
            source = CAPACITY_IMPORTS + f"\npublic class {name} {{\n" + code + "\n}\n"
            files.append(write_source(directory, name, source))
            coverage = "compiled API reference"
        elif code.startswith("public class "):
            members.append(code.replace("public class ", "public static class ", 1))
        elif code.startswith("/**") and "public double findMaxThroughput" in code:
            members.append(code)
        else:
            members.append(f"void example{ordinal}() throws Exception {{\n" + code + "\n}")
        manifest.append({"document": document, "java_fence": matches.index(regular[ordinal]),
                         "fixture_ordinal": ordinal, "coverage": coverage})
    if strict_match is not None:
        extra, code = imports_and_body(strict_match.group(1))
        imports.update(extra)
        checks = """
if (pipelineEvidence.getSamples().size() != 6 || pipelineEvidence.getDefinitions().size() != 6
    || pipelineEvidence.getLocations().size() != 6 || pipelineSnapshot.getEvidence().size() != 6) {
  throw new AssertionError("Strict piping example must retain all six installed limits");
}
for (PlantConstraintSample sample : pipelineEvidence.getSamples()) {
  if (sample.getStatus() != PlantConstraintSample.SampleStatus.AVAILABLE
      || !calculationId.equals(sample.getCalculationId())
      || !Double.isFinite(sample.getSampledValue()) || !Double.isFinite(sample.getApplicableLimit())
      || !Double.isFinite(sample.getPhysicalMargin()) || sample.getPhysicalMargin() < 0.0) {
    throw new AssertionError("Incomplete, stale, or infeasible strict piping evidence");
  }
}
"""
        members.append("void strictPipingEvidence() throws Exception {\n" + code + "\n" + checks + "\n}")
        manifest.append({"document": document, "java_fence": matches.index(strict_match),
                         "section": "Strict piping evidence",
                         "coverage": "compiled and executed; six physical limits and completed calculation"})
    header = "\n".join(sorted(imports)) + "\n"
    source = header + "public class CapacityFragments {\n" + CAPACITY_FIELDS + "\n".join(members) + "\n}\n"
    files.append(write_source(directory, "CapacityFragments", source))
    return files, header, strict_match is not None


def pressure_and_design_sources(directory, manifest, capacity_imports):
    imports = capacity_imports + """
import neqsim.process.design.*;
import neqsim.process.design.template.*;
import neqsim.process.design.DesignOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
"""
    documents = [
        ("PressureFragments", "docs/process/pressure_boundary_optimization.md", 16,
         "PressureBoundaryOptimizer optimizer; double inletPressure,outletPressure,targetPressure; "
         "PressureBoundaryOptimizer.LiftCurveTable table;"),
        ("DesignFragments", "docs/process/DESIGN_FRAMEWORK.md", 33,
         "DesignOptimizer optimizer; ProcessTemplate template; ProcessBasis basis; ProcessModule myModule; "
         "ProcessSystem myProcess; Separator sep; ThrottlingValve valve; Stream compOutput; "
         "double gasFlowRate,liquidFlowRate; DesignSpecification spec; EquipmentConstraintRegistry registry; "
         "SystemInterface myOilGasFluid,myFluid; double rate,pressure,temp;"),
    ]
    files = []
    for name, document, expected, fields in documents:
        methods, extra_imports = [], []
        for ordinal, code in enumerate(read_fences(document, expected)):
            if name == "DesignFragments" and ordinal in DESIGN_REFERENCES:
                manifest.append({"document": document, "java_fence": ordinal,
                                 "coverage": "reference, not executed", "reason": DESIGN_REFERENCES[ordinal]})
                continue
            extra, code = imports_and_body(code)
            extra_imports.extend(extra)
            methods.append(f"void example{ordinal}() throws Exception {{\n" + code + "\n}")
            manifest.append({"document": document, "java_fence": ordinal, "coverage": "compiled and executed"})
        source = (imports + "\n".join(extra_imports) + f"\npublic class {name} extends CapacityFragments {{\n"
                  + fields + "\n" + "\n".join(methods) + "\n}\n")
        files.append(write_source(directory, name, source))
    return files


def module_source(directory, manifest):
    document = "docs/process/processmodel/process_module.md"
    blocks = read_fences(document, 4, after="## Capacity Constraints in ProcessModule")
    methods = []
    extra_imports = []
    for ordinal, code in enumerate(blocks):
        extra, code = imports_and_body(code)
        extra_imports.extend(extra)
        methods.append(f"void example{ordinal}() throws Exception {{\n" + code + "\n}")
        manifest.append({"document": document, "section_java_fence": ordinal, "coverage": "compiled and executed"})
    template = (FIXTURES / "ModuleFragments.java.template").read_text(encoding="utf-8")
    source = "\n".join(extra_imports) + "\n" + template.replace("@@DOCUMENTED_METHODS@@", "\n".join(methods))
    return write_source(directory, "ModuleFragments", source)


def run_command(command, directory, log, timeout):
    result = subprocess.run(command, cwd=directory, capture_output=True, text=True, timeout=timeout, check=False)
    output = result.stdout + result.stderr
    log.write_text(output, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Command failed with exit {result.returncode}; see {log}\n{output[-12000:]}")
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "target/optimization-java-fragments")
    args = parser.parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    run_directory = Path(tempfile.mkdtemp(prefix="run-", dir=output))
    sources = run_directory / "generated-sources"
    classes = run_directory / "generated-classes"
    working = run_directory / "exports"
    for directory in [sources, classes, working]:
        directory.mkdir()
    manifest = []
    files = optimizer_sources(sources, manifest)
    capacity_files, capacity_imports, strict_piping = capacity_sources(sources, manifest)
    files.extend(capacity_files)
    files.extend(pressure_and_design_sources(sources, manifest, capacity_imports))
    files.append(module_source(sources, manifest))
    for name in ["FragmentRunner.java", "DesignFragmentRunner.java"]:
        destination = sources / name
        shutil.copyfile(FIXTURES / name, destination)
        files.append(destination)
    (run_directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    classpath = os.pathsep.join([str(classes)] + source_classpath())
    run_command([java_tool("javac"), "--release", "8", "-Xmaxerrs", "200", "-cp", classpath,
                 "-d", str(classes)] + [str(path) for path in files], ROOT, run_directory / "compile.log", 180)
    compiled_count = 204 + int(strict_piping)
    executed_count = 98 + int(strict_piping)
    print(f"PASS: {compiled_count} Java fences compiled (96 optimizer, {60 + int(strict_piping)} capacity, "
          "16 pressure, 28 design, 4 module)", flush=True)
    for runner, count in [("FragmentRunner", 66 + int(strict_piping)),
                          ("DesignFragmentRunner", 28), ("ModuleFragments", 4)]:
        run_command([java_tool("java"), "-Xmx1g", "-Djava.awt.headless=true", "-cp", classpath, runner],
                    working, run_directory / (runner + ".log"), 300)
        print(f"PASS: {runner} executed {count} documented fragments", flush=True)
    summary = {"compiled_java_fences": compiled_count, "executed_java_fragments": executed_count,
               "strict_piping_evidence_included": strict_piping,
               "reference_design_fences_excluded": DESIGN_REFERENCES, "python_executable": sys.executable,
               "artifacts": str(run_directory.relative_to(output))}
    (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"Validation artifacts: {run_directory}", flush=True)


if __name__ == "__main__":
    main()
