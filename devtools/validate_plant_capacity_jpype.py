#!/usr/bin/env python3
"""Validate Java/JPype/JSON parity for the plant capacity configuration contract."""

import argparse
import json
import os
from pathlib import Path

import jpype


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-file", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    dependencies = args.classpath_file.read_text(encoding="utf-8").strip()
    classpath = [str(root / "target/classes")] + dependencies.split(os.pathsep)
    jpype.startJVM(classpath=classpath, convertStrings=True)
    try:
        JClass = jpype.JClass
        Fluid = JClass("neqsim.thermo.system.SystemSrkEos")
        Stream = JClass("neqsim.process.equipment.stream.Stream")
        Compressor = JClass("neqsim.process.equipment.compressor.Compressor")
        ProcessSystem = JClass("neqsim.process.processmodel.ProcessSystem")
        ProcessModel = JClass("neqsim.process.processmodel.ProcessModel")
        HashMap = JClass("java.util.LinkedHashMap")
        Coverage = JClass("neqsim.process.util.optimizer.UtilizationCoverageReport")
        Registry = JClass("neqsim.process.util.optimizer.PlantConstraintRegistry")
        Definition = JClass("neqsim.process.util.optimizer.PlantConstraintDefinition")
        Scope = JClass("neqsim.process.util.optimizer.PlantConstraintScope")
        Sample = JClass("neqsim.process.util.optimizer.PlantConstraintSample")
        Snapshot = JClass("neqsim.process.util.optimizer.PlantUtilizationSnapshot")

        fluid = Fluid(298.15, 50.0)
        fluid.addComponent("methane", 0.9)
        fluid.addComponent("ethane", 0.1)
        fluid.setMixingRule("classic")
        feed = Stream("feed", fluid)
        feed.setFlowRate(5000.0, "kg/hr")
        compressor = Compressor("K-1", feed)
        compressor.setOutletPressure(100.0)
        process = ProcessSystem()
        process.add(feed)
        process.add(compressor)
        model = ProcessModel()
        model.add("Compression", process)
        properties = HashMap()
        properties.put("ratedPower", jpype.JDouble(1000.0))
        properties.put("maxSpeed", jpype.JDouble(12000.0))
        capacities = HashMap()
        capacities.put("Compression::K-1", properties)
        applied = model.applyDesignCapacities(capacities)
        assert applied.containsKey("Compression::K-1")
        assert model.runUntilConverged(50, 5.0e-3)
        assert compressor.getPower("kW") > 0.0

        # Explicitly qualify only installed power; other restrictions remain visible as disabled.
        registry = Registry()
        power_definition = None
        power_constraint = None
        for entry in compressor.getCapacityConstraints().entrySet():
            name = str(entry.getKey())
            constraint = entry.getValue()
            constraint.setEnabled(name == "power")
            constraint.setDataSource("synthetic installed rating")
            definition = (Definition.builder(name, Scope.equipment("Plant", "Compression", "K-1"))
                          .unit(constraint.getUnit()).basis("normalized installed capacity")
                          .provenance("synthetic installed rating").severity(constraint.getSeverity())
                          .limitDirection(Definition.LimitDirection.MINIMUM if constraint.isMinimumConstraint()
                                          else Definition.LimitDirection.MAXIMUM)
                          .enabled(constraint.isEnabled()).build())
            registry.register(definition)
            if name == "power":
                power_definition = definition
                power_constraint = constraint
        coverage = (Coverage.builder("Plant").expectConstraint("Compression", "K-1", "power")
                    .equipment("Compression", compressor).registry(registry).build())
        assert coverage.isComplete(), str(coverage.getDiagnostics())
        document = json.loads(str(coverage.toJson()))
        power_row = next(row for row in document["rows"] if row["constraintName"] == "power")
        assert power_row["unit"] == "%"
        assert power_row["applicableLimit"] == 100.0
        assert abs(power_row["currentValue"] - compressor.getPower("kW") / 1000.0 * 100.0) < 1.0e-9
        calculation_id = "jpype-qualified-calculation"
        value = power_row["currentValue"]
        utilization = power_constraint.getUtilization(value)
        sample = (Sample.builder(power_definition.getQualifiedId(), calculation_id)
                  .values(value, 100.0).normalized(utilization, utilization - 1.0)
                  .physical(100.0 - value, max(0.0, value - 100.0))
                  .unit("%").basis("normalized installed capacity")
                  .provenance("synthetic completed compressor calculation").build())
        snapshot = (Snapshot.builder(registry, calculation_id).expectedCoverage(coverage)
                    .convergenceComplete(True).sample(sample).build())
        assert snapshot.isFeasible(), str(snapshot.getCoverageDiagnostics())
        assert abs(snapshot.getBottleneck().getNormalizedUtilization() - utilization) < 1.0e-12

        missing = (Coverage.builder("Plant").expectConstraint("Compression", "missing", "power")
                   .registry(registry).build())
        assert not missing.isComplete()
        missing_json = json.loads(str(missing.toJson()))
        assert all(row["currentValue"] is None for row in missing_json["rows"])
        assert not (Snapshot.builder(registry, calculation_id).expectedCoverage(missing)
                    .convergenceComplete(True).sample(sample).build()).isFeasible()
        print(json.dumps({"status": "PASS", "jpype": jpype.__version__,
                          "java": str(JClass("java.lang.System").getProperty("java.version")),
                          "shaftPowerKW": compressor.getPower("kW"),
                          "powerUtilization": utilization, "coverageRows": len(document["rows"]),
                          "snapshotSchema": str(snapshot.getSchemaVersion()),
                          "missingEvidenceRejected": True}, indent=2))
    finally:
        jpype.shutdownJVM()


if __name__ == "__main__":
    main()
