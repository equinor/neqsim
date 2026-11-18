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
    bindings = getattr(J(pid + "PidSimulationBindings"), "from_")(project, model)
    metadata_type = J("neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata")
    metadata = metadata_type.builder("2026-11-18T00:00:00Z", "OSR study", "NeqSim", "3.29.0")
    metadata.plantProperty(metadata_type.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "OSR-001")
    java_path = J("java.io.File")(str(target)).toPath()
    J(pid + "ProfessionalPidPackageExporter").export(project, model, bindings, metadata.build(), java_path)
    result = J(pid + "ProfessionalPidPackageReader").read(project, java_path)
    assessment = J("neqsim.process.processmodel.dexpi.Dexpi201XmlWriter").assess(java_path.resolve("plant.dexpi.xml"))
    evidence = {
        "schema_and_profile_conformant": bool(assessment.isSchemaAndProfileConformant()),
        "import_losses": bool(result.hasImportLosses()),
        "diagnostics": [str(item) for item in result.getDiagnostics()],
        "semantic_fingerprint": str(result.getSemanticFingerprint()),
    }
    if not evidence["schema_and_profile_conformant"]:
        raise ValueError("Generated DEXPI failed its schema/profile check")
    return evidence


if __name__ == "__main__":
    # Draw the evaluated turndown lineup with all three open anti-surge recycle paths.
    low = json.loads((ROOT / "steady_turndown.json").read_text())
    plant, *_ = build(rate=55_000, gas_splits=low["gas_split_fractions"])
    target = Path(mkdtemp(prefix="drawings-", dir=ROOT)) / "pid"
    evidence = export_package(plant, target)
    evidence["package_directory"] = str(target.relative_to(ROOT))
    (ROOT / "drawing-intake.json").write_text(json.dumps(evidence, indent=2))
