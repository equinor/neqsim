#!/usr/bin/env python3
"""Verify the issue #4258 Java/JPype/JSON convergence contract against workspace classes."""

import argparse
import json
import os
from pathlib import Path

import jpype


def main():
    """Run the public reproducer, recovery and forward-mode compatibility checks."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-file", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    dependencies = args.classpath_file.read_text(encoding="utf-8").strip()
    jpype.startJVM(
        classpath=[str(root / "target/classes")] + dependencies.split(os.pathsep),
        convertStrings=True,
    )
    try:
        fluid_class = jpype.JClass("neqsim.thermo.system.SystemSrkEos")
        stream_class = jpype.JClass("neqsim.process.equipment.stream.Stream")
        pipe_class = jpype.JClass("neqsim.process.equipment.pipeline.PipeBeggsAndBrills")
        state_exception = jpype.JClass("java.lang.IllegalStateException")
        location = str(pipe_class.class_.getProtectionDomain().getCodeSource().getLocation())
        assert str(root / "target/classes") in location, location
        fluid = fluid_class(313.15, 100.0)
        fluid.addComponent("methane", 0.94)
        fluid.addComponent("nC10", 0.06)
        fluid.setMixingRule("classic")
        fluid.setMultiPhaseCheck(True)
        inlet = stream_class("inlet", fluid)
        inlet.setFlowRate(10.0, "kg/sec")
        inlet.run()
        pipe = pipe_class("pipe", inlet)
        pipe.setLength(40000.0)
        pipe.setDiameter(0.35)
        pipe.setPipeWallRoughness(4.5e-5)
        pipe.setNumberOfIncrements(30)
        pipe.setHeatTransferMode(pipe_class.HeatTransferMode.ISOTHERMAL)
        pipe.setOutletPressure(80.0, "bara")
        pipe.setMaxFlowIterations(1)
        try:
            pipe.run()
        except state_exception:
            pass
        else:
            raise AssertionError("Iteration-limited candidate was accepted")
        failed = json.loads(pipe.getFlowSolveReport().toJson())
        assert failed["terminationReason"] == "ITERATION_LIMIT", failed
        assert not pipe.getFlowSolveReport().isConverged()
        assert abs(inlet.getFlowRate("kg/sec") - 10.0) < 1e-10
        pipe.setMaxFlowIterations(50)
        pipe.run()
        report = pipe.getFlowSolveReport()
        assert report.isConverged()
        assert abs(pipe.getOutletStream().getPressure("bara") - 80.0) <= 0.008
        output = json.loads(pipe.toJson())
        assert output["flowSolveReport"] == json.loads(report.toJson())
        print(json.dumps({"failed": failed, "accepted": output["flowSolveReport"]}, indent=2))
        pipe.setCalculationMode(pipe_class.CalculationMode.CALCULATE_OUTLET_PRESSURE)
        pipe.run()
        assert pipe.getFlowSolveReport() is None
        assert abs(pipe.getOutletStream().getPressure("bara") - 80.0) <= 0.008
    finally:
        jpype.shutdownJVM()


if __name__ == "__main__":
    main()
