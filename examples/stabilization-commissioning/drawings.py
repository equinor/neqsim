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
    element_type = J(pid + "PidElementType")
    element_class = J(pid + "PidElement")
    by_tag = {str(element.getTag()): element for element in model.getElements()}
    trip = by_tag["20-PSHH-001"]
    trip_id = str(trip.getId())

    def element(tag, kind, owner, description):
        item = element_class("native-sas:" + tag, tag, kind)
        item.equipment(owner).description(description)
        item.provenance(
            "DYN-C049-SAS-IO",
            "Actual physical or logical I/O identity used by the executable virtual-FAT case",
        )
        item.standard("ANSI/ISA-5.1:2024").attribute(
            "qualificationStatus", "SYNTHETIC_VIRTUAL_FAT_EVIDENCE"
        ).attribute("fitnessForConstruction", False)
        return item

    for suffix in "ABC":
        tag = "20-PT-001" + suffix
        item = element(tag, element_type.MEASUREMENT, "V-101", "HIPPS pressure channel " + suffix)
        item.line("gasOutStream").connect(trip_id).attribute("sensingLocation", "VESSEL")
        item.attribute("responseDelaySeconds", 0.5).attribute("filterTimeConstantSeconds", 0.25)
        model.add(item)

    for suffix in "AB":
        valve_tag = "20-XV-101" + suffix
        valve = element(
            valve_tag, element_type.SHUTDOWN_VALVE, "V-101", "Independent HIPPS isolation valve " + suffix
        )
        valve.line(str(process.getUnit(valve_tag).getInletStream().getName()))
        valve.attribute("failurePosition", "FAIL_CLOSED").attribute(
            "strokeSeconds", 2.0 if suffix == "A" else 3.0
        )
        model.add(valve)
        solenoid = element(
            valve_tag + "-SOL", element_type.SIGNAL, "V-101", "HIPPS solenoid command " + suffix
        ).connect(str(valve.getId()))
        model.add(solenoid)
        trip.connect(str(solenoid.getId()))
        feedback = element(
            valve_tag + "-ZSC", element_type.SIGNAL, "V-101", "HIPPS closed-position feedback " + suffix
        )
        model.add(feedback)
        valve.connect(str(feedback.getId()))

    esd_valve = element(
        "10-ESDV-100", element_type.SHUTDOWN_VALVE, "V-101", "Inlet ESD isolation valve"
    )
    esd_valve.line(str(process.getUnit("10-ESDV-100").getInletStream().getName()))
    esd_valve.attribute("failurePosition", "FAIL_CLOSED").attribute("strokeSeconds", 4.0)
    model.add(esd_valve)
    esd_feedback = element(
        "10-ESDV-100-ZSC", element_type.SIGNAL, "V-101", "Inlet ESD closed-position feedback"
    )
    model.add(esd_feedback)
    esd_valve.connect(str(esd_feedback.getId()))
    esd_function = element(
        "ESD-101", element_type.SAFETY_FUNCTION, "V-101", "Independent inlet-isolation function"
    ).connect(str(esd_valve.getId()))
    model.add(esd_function)
    fg_demand = element(
        "00-FG-001", element_type.SIGNAL, "V-101", "Fire-and-gas demand to inlet ESD"
    ).connect(str(esd_function.getId()))
    model.add(fg_demand)
    for element in model.getElements():
        if str(element.getTag()) == "20-PT-002":
            element.attribute("sensingLocation", "VESSEL")
    bindings = getattr(J(pid + "PidSimulationBindings"), "from_")(project, model)
    bindings.bindMeasurement("20-PT-002", "20-PT-002")
    bindings.bindEquipment("20-BDV-001", "20-BDV-001")
    for suffix in "ABC":
        bindings.bindMeasurement("20-PT-001" + suffix, "20-PT-001" + suffix)
    for tag in ("20-XV-101A", "20-XV-101B", "10-ESDV-100"):
        bindings.bindEquipment(tag, tag)
    metadata_type = J("neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata")
    metadata = metadata_type.builder("2026-11-18T00:00:00Z", "OSR study", "NeqSim", "3.29.0")
    metadata.plantProperty(metadata_type.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "OSR-001")
    java_path = J("java.io.File")(str(target)).toPath()
    J(pid + "ProfessionalPidPackageExporter").export(project, model, bindings, metadata.build(), java_path)
    result = J(pid + "ProfessionalPidPackageReader").read(project, java_path)
    assessment = J("neqsim.process.processmodel.dexpi.Dexpi201XmlWriter").assess(java_path.resolve("plant.dexpi.xml"))
    projection = json.loads((target / "plant.dexpi.xml.pid-projection.json").read_text())
    sas_io_tags = [
        "20-PT-001A", "20-PT-001B", "20-PT-001C", "20-PSHH-001",
        "20-XV-101A-ZSC", "20-XV-101B-ZSC", "20-XV-101A-SOL",
        "20-XV-101B-SOL", "00-FG-001", "10-ESDV-100-ZSC",
    ]
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
        "sas_io_native_ids": {
            tag: projection["nativeIdsByPidTag"].get(tag) for tag in sas_io_tags
        },
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
    missing = [tag for tag, native_id in evidence["sas_io_native_ids"].items() if not native_id]
    if missing:
        raise ValueError("Missing DEXPI native identities for SAS I/O tags: " + ", ".join(missing))
    for tag in ("20-PT-001A", "20-PT-001B", "20-PT-001C",
                "20-XV-101A", "20-XV-101B", "10-ESDV-100"):
        if str(result.getBindings().getSimulationName(tag)) != tag:
            raise ValueError("Missing physical simulation binding for " + tag)
    return evidence


if __name__ == "__main__":
    # Draw the evaluated turndown lineup with all three open anti-surge recycle paths.
    low = json.loads((ROOT / "steady_turndown.json").read_text())
    plant, units, streams, sinks, _ = build(
        rate=55_000, gas_splits=low["gas_split_fractions"], integrated_sas=True
    )
    gas_branch = J("neqsim.process.equipment.splitter.Splitter")(
        "20-SPL-101", units["V-101"].getGasOutStream(), 2
    )
    gas_branch.setSplitFactors([1.0, 0.0])
    gas_branch.run()
    units["PV-101"].setInletStream(gas_branch.getSplitStream(0))
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
    previous_sinks = None
    for sweep in range(180):
        for tag, unit in units.items():
            unit.run()
            if tag == "V-101":
                gas_branch.run()
                blowdown.run()
        current_sinks = [stream.getFlowRate("kg/hr") for stream in sinks.values()]
        external_mass_error = streams["feed"].getFlowRate("kg/hr") - sum(current_sinks)
        sink_change = (
            float("inf") if previous_sinks is None
            else max(abs(actual - previous) for actual, previous in zip(current_sinks, previous_sinks))
        )
        recycles_solved = all(
            unit.solved() for tag, unit in units.items()
            if tag.startswith(("RS-", "RD-", "RG-"))
        )
        if (sweep >= 3 and recycles_solved
                and abs(external_mass_error / streams["feed"].getFlowRate("kg/hr")) < 4.0e-6
                and sink_change < 1.0e-3):
            break
        previous_sinks = current_sinks
    else:
        raise RuntimeError("Instrumented drawing model did not reconverge after branch insertion")
    integrated_sas_sweeps = sweep + 1
    max_sink_difference = max(
        abs(
            stream.getFlowRate("kg/hr")
            - low["streams"]["net203" if name == "gas_export" else name]["kg_h"]
        )
        for name, stream in sinks.items()
    )
    target = Path(mkdtemp(prefix="drawings-", dir=ROOT)) / "pid"
    evidence = export_package(plant, target)
    evidence["integrated_sas_reconvergence_sweeps"] = integrated_sas_sweeps
    evidence["integrated_sas_steady_mass_error_kg_h"] = external_mass_error
    evidence["integrated_sas_steady_relative_mass_error"] = (
        external_mass_error / streams["feed"].getFlowRate("kg/hr")
    )
    evidence["max_sink_flow_difference_from_uninstrumented_kg_h"] = max_sink_difference
    evidence["max_sink_flow_difference_fraction_of_feed"] = (
        max_sink_difference / streams["feed"].getFlowRate("kg/hr")
    )
    evidence["integrated_sas_physical_valve_openings_pct"] = {
        tag: float(units[tag].getPercentValveOpening())
        for tag in ("20-XV-101A", "20-XV-101B", "10-ESDV-100")
    }
    evidence["all_nine_recycles_solved"] = all(
        unit.solved() for tag, unit in units.items() if tag.startswith(("RS-", "RD-", "RG-"))
    )
    if abs(evidence["integrated_sas_steady_relative_mass_error"]) >= 4.0e-6:
        raise ValueError("Integrated SAS drawing model failed the declared external mass balance")
    if evidence["max_sink_flow_difference_fraction_of_feed"] >= 1.0e-6:
        raise ValueError("Fully open SAS hardware changed the qualified turndown boundary")
    if not evidence["all_nine_recycles_solved"]:
        raise ValueError("Integrated SAS drawing model has an unsolved recycle")
    evidence["package_directory"] = str(target.relative_to(ROOT))
    (ROOT / "drawing-intake.json").write_text(json.dumps(evidence, indent=2))
