"""Execute the Python fences in the production and compressor optimization guides.

Run after compiling Java and writing Maven's dependency classpath to
target/optimization-classpath.txt. Uses the selected Python environment's JPype,
neqsim, NumPy and SciPy installations; does not install or change any dependencies.
"""
from pathlib import Path
import re
import traceback

import jpype


ROOT = Path(__file__).resolve().parents[3]
DOCS = [
    ROOT / "docs/examples/PRODUCTION_OPTIMIZATION_GUIDE.md",
    ROOT / "docs/process/optimization/COMPRESSOR_OPTIMIZATION_GUIDE.md",
    ROOT / "docs/process/optimization/OPTIMIZATION_AND_CONSTRAINTS.md",
]


def python_blocks(path):
    text = path.read_text(encoding="utf-8")
    return [match.group(1) for match in re.finditer(
        r"^```python\n(.*?)^```", text, re.MULTILINE | re.DOTALL
    )]


def create_process(feed_name="Well Feed", compressor_name="Gas Compressor"):
    from neqsim import jneqsim
    gas = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.08)
    gas.addComponent("propane", 0.05)
    gas.addComponent("n-butane", 0.02)
    gas.setMixingRule("classic")
    feed = jneqsim.process.equipment.stream.Stream(feed_name, gas)
    feed.setFlowRate(10000.0, "kg/hr")
    process = jneqsim.process.processmodel.ProcessSystem()
    process.add(feed)
    separator = jneqsim.process.equipment.separator.Separator("HP Separator", feed)
    process.add(separator)
    compressor = jneqsim.process.equipment.compressor.Compressor(
        compressor_name, separator.getGasOutStream()
    )
    compressor.setOutletPressure(100.0, "bara")
    compressor.getMechanicalDesign().setMaxDesignPower(500.0)
    process.add(compressor)
    process.run()
    return process, feed, compressor


def production_context():
    from neqsim import jneqsim
    process, feed, comp = create_process()
    optimizer_class = jneqsim.process.util.optimizer.ProductionOptimizer
    config_class = optimizer_class.OptimizationConfig
    return dict(
        jneqsim=jneqsim, jpype=jpype, process=process, feed=feed, comp=comp,
        OptConfig=config_class, ProductionOptimizer=optimizer_class,
        SearchMode=optimizer_class.SearchMode,
        config=config_class(1000.0, 20000.0).tolerance(10.0),
        optimizer=optimizer_class(),
    )


def compressor_context():
    from neqsim import jneqsim
    process, feed, existing = create_process("Inlet Stream", "unused compressor")
    process.removeUnit(existing.getName())
    feed.setFlowRate(100000.0, "kg/hr")
    splitter = jneqsim.process.equipment.splitter.Splitter("Compressor Splitter", feed, 3)
    splitter.setSplitFactors(jpype.JArray(jpype.JDouble)([1 / 3, 1 / 3, 1 / 3]))
    process.add(splitter)
    compressors = []
    for index in range(3):
        compressor = jneqsim.process.equipment.compressor.Compressor(
            f"Train {index + 1}", splitter.getSplitStream(index)
        )
        compressor.setOutletPressure(110.0, "bara")
        compressor.setUsePolytropicCalc(True)
        compressor.setPolytropicEfficiency(0.78)
        compressor.getMechanicalDesign().setMaxDesignPower(2000.0)
        process.add(compressor)
        compressors.append(compressor)
    process.run()
    for compressor in compressors:
        compressor.generateCompressorChart("normal curves", 5)
        compressor.setSolveSpeed(True)
        compressor.reinitializeCapacityConstraints()
    process.run()
    return dict(
        process=process, process_system=process, feed=feed, inlet_stream=feed,
        comp1=compressors[0], comp2=compressors[1], comp3=compressors[2],
        compressor=compressors[0], current_flow=100000.0, low_flow=90000.0,
        high_flow=110000.0,
    )


def main():
    if not jpype.isJVMStarted():
        dependency_file = ROOT / "target/optimization-classpath.txt"
        dependencies = dependency_file.read_text().strip().split(":")
        jpype.startJVM(classpath=[str(ROOT / "target/classes")] + dependencies,
                       convertStrings=True)
    executed = 0
    failures = []
    for page, path in enumerate(DOCS):
        for number, code in enumerate(python_blocks(path)):
            try:
                if page == 0:
                    context = production_context()
                elif page == 1:
                    context = compressor_context()
                else:
                    process, feed, compressor = create_process(
                        "Well Feed", "Export Compressor"
                    )
                    context = dict(process=process)
                exec(compile(code, f"{path.name}:python:{number}", "exec"), context)
                if "result" in context and hasattr(context["result"], "isFeasible"):
                    assert context["result"].isFeasible(), context["result"].getInfeasibilityDiagnosis()
                print(f"PASS {path.name} Python block {number}", flush=True)
                executed += 1
            except Exception:
                detail = traceback.format_exc()
                print(f"FAIL {path.name} Python block {number}\n{detail}", flush=True)
                failures.append((path.name, number, detail))
    assert not failures, f"{len(failures)} Python guide blocks failed"
    print(f"Executed {executed} Python guide blocks", flush=True)


if __name__ == "__main__":
    main()
