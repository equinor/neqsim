"""Export the actual flowsheet and re-read its DEXPI 2.0.1 engineering package."""
import json
from pathlib import Path
from tempfile import mkdtemp

from steady import J, ROOT, build


def export_package(process, target):
    engineering = "neqsim.process.engineering."
    pid = engineering + "pid."
    builder = J(engineering + "NorsokOffshoreEngineeringBuilder")
    project = getattr(builder, "from_")("Oil stabilization and recompression", process, "2.0.1")
    project = project.projectId("OSR-001").registerProposedInstruments(False).build().setRevision("A")
    model = J(pid + "PidDesignSynthesizer").synthesize(
        project, J(pid + "PidDesignBasis")("OSR-CONCEPT", "20"),
        J(pid + "NorsokPidRuleCatalog").completeProposals(),
    )
    for element in model.getElements():
        if str(element.getTag()) == "20-PT-002":
            element.attribute("sensingLocation", "VESSEL")
    bindings = getattr(J(pid + "PidSimulationBindings"), "from_")(project, model)
    bindings.bindMeasurement("20-PT-002", "20-PT-002")
    bindings.bindEquipment("20-BDV-001", "20-BDV-001")
    metadata_type = J("neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata")
    metadata = metadata_type.builder("2026-11-18T00:00:00Z", "OSR study", "NeqSim", "3.29.0")
    metadata.plantProperty(metadata_type.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "OSR-001")
    java_path = J("java.io.File")(str(target)).toPath()
    J(pid + "ProfessionalPidPackageExporter").export(project, model, bindings, metadata.build(), java_path)
    result = J(pid + "ProfessionalPidPackageReader").read(project, java_path)
    assessment = J("neqsim.process.processmodel.dexpi.Dexpi201XmlWriter").assess(java_path.resolve("plant.dexpi.xml"))
    projection = json.loads((target / "plant.dexpi.xml.pid-projection.json").read_text())
    signals = [
        {
            "source_tag": row["sourceTag"],
            "target_tag": row["targetTag"],
            "native_id": row["nativeId"],
        }
        for row in projection["signalLinks"]
        if row.get("status") == "PROJECTED"
    ]
    actuations = [
        {
            "function_tag": "20-BDV-001",
            "simulation_final_element": str(
                result.getBindings().getSimulationName("20-BDV-001")
            ),
            "native_id": projection["nativeIdsByPidTag"].get("20-BDV-001"),
        }
    ]
    evidence = {
        "schema_and_profile_conformant": bool(assessment.isSchemaAndProfileConformant()),
        "import_losses": bool(result.hasImportLosses()),
        "diagnostics": [str(item) for item in result.getDiagnostics()],
        "semantic_fingerprint": str(result.getSemanticFingerprint()),
        "verified_signals": signals,
        "verified_actuations": actuations,
    }
    if not evidence["schema_and_profile_conformant"]:
        raise ValueError("Generated DEXPI failed its schema/profile check")
    if not any(
        row["source_tag"] == "20-PSHH-001"
        and row["target_tag"] == "20-BDV-001"
        for row in signals
    ):
        raise ValueError("Missing physical 20-PSHH-001 to 20-BDV-001 DEXPI signal")
    if actuations[0]["simulation_final_element"] != "20-BDV-001":
        raise ValueError("Missing physical 20-BDV-001 final-element actuation")
    return evidence


if __name__ == "__main__":
    # Draw the evaluated turndown lineup with all three open anti-surge recycle paths.
    low = json.loads((ROOT / "steady_turndown.json").read_text())
    plant, units, *_ = build(rate=55_000, gas_splits=low["gas_split_fractions"])
    gas_branch = J("neqsim.process.equipment.splitter.Splitter")(
        "20-SPL-101", units["V-101"].getGasOutStream(), 2
    )
    gas_branch.setSplitFactors([1.0, 0.0])
    gas_branch.run()
    units["PV-103"].setInletStream(gas_branch.getSplitStream(0))
    pressure = J("neqsim.process.measurementdevice.PressureTransmitter")(
        "20-PT-002", gas_branch.getSplitStream(0)
    )
    pressure.setUnit("bara")
    blowdown = J("neqsim.process.equipment.valve.BlowdownValve")(
        "20-BDV-001", gas_branch.getSplitStream(1)
    )
    blowdown.setCv(2.0)
    blowdown.setOutletPressure(1.5, "bara")
    plant.add(gas_branch)
    plant.add(pressure)
    plant.add(blowdown)
    plant.run()
    target = Path(mkdtemp(prefix="drawings-", dir=ROOT)) / "pid"
    evidence = export_package(plant, target)
    evidence["package_directory"] = str(target.relative_to(ROOT))
    (ROOT / "drawing-intake.json").write_text(json.dumps(evidence, indent=2))
