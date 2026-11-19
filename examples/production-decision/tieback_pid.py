"""Export the process, instrument index and cause/effect connections of the tie-back fixture."""

import argparse
import json
import os
from pathlib import Path

import jpype

from tieback_host import FLARE_BARA, INLET_SEAT_TIME_S, PSV_SET_BARA, SEPARATOR_PSHH_BARA
from tieback_protection import HIPPS_TAGS, PSD_TAG, ProtectionBasis, make_plant


def export(n, output, basis=ProtectionBasis()):
    if output.exists():
        raise FileExistsError(output)
    _, well, dhsv, _, _, hipvs, _, sep = make_plant(n, 1)
    # DEXPI Plant describes physical piping. Project the operating branch of each
    # composite item into its constituent valves and named pipe connections.
    eq = n.process.equipment
    plant = n.process.processmodel.ProcessSystem()
    inlet = well.getOutletStream().clone("W-101 well inflow")
    plant.add(inlet)

    def valve(kind, tag, feed):
        item = kind(tag, feed)
        item.setOutletPressure(feed.getPressure("bara"), "bara")
        item.run()
        plant.add(item)
        return item.getOutletStream()

    inlet = valve(eq.valve.ESDValve, str(dhsv.getName()), inlet)
    for tag in ("PMV-101", "PWV-101"):
        inlet = valve(eq.valve.ESDValve, tag, inlet)
    for tag in ("CHV-101", "MAN-101-PROD"):
        inlet = valve(eq.valve.ThrottlingValve, tag, inlet)
    for hipv in hipvs:
        inlet = valve(eq.valve.HIPPSValve, str(hipv.getName()), inlet)
    inlet.setName("FL-201 production flowline and riser")
    tee = eq.splitter.Splitter("TEE-301", inlet, 2)
    tee.setSplitFactors([1., 0.])
    tee.run()
    plant.add(tee)
    inlet = valve(eq.valve.ESDValve, "ESV-301", tee.getSplitStream(0))
    inlet = valve(eq.valve.ESDValve, "XSV-302", inlet)
    inlet = valve(eq.valve.ThrottlingValve, "CHV-301", inlet)
    vessel = eq.separator.ThreePhaseSeparator(str(sep.getName()), inlet)
    vessel.run()
    plant.add(vessel)
    # Relief branches represent physical connections; this drawing gives no relief credit.
    gas_tee = eq.splitter.Splitter("TEE-302", vessel.getGasOutStream(), 2)
    gas_tee.setSplitFactors([1., 0.])
    gas_tee.run()
    plant.add(gas_tee)
    for tag, supply in (("PSV-301", tee.getSplitStream(1)), ("PSV-302", gas_tee.getSplitStream(1))):
        rv = eq.valve.SafetyValve(tag, supply)
        plant.add(rv)
        rv.getOutletStream().setName(tag + " discharge to host flare")
    md = n.process.measurementdevice
    for tag in (PSD_TAG, *HIPPS_TAGS):
        plant.add(md.PressureTransmitter(tag, plant.getUnit("HIPV-201B").getOutletStream()))
    plant.add(md.PressureTransmitter("PST-301", tee.getSplitStream(0)))
    plant.add(md.PressureTransmitter("PT-302", vessel.getGasOutStream()))
    plant.add(md.OilLevelTransmitter("LT-301", vessel))
    plant.add(md.WaterLevelTransmitter("LIT-302", vessel))
    eng = n.process.engineering
    pid = eng.pid
    project = getattr(eng.NorsokOffshoreEngineeringBuilder, "from_")(
        "Synthetic subsea tie-back", plant, "2.0.1").projectId("TIEBACK").build().setRevision("A")
    model = pid.PidDesignModel("TIEBACK", "SUBSEA-PRESSURE-PROTECTION")
    route = "HIPV-201B"

    def element(tag, kind, owner, description, *connections):
        entry = pid.PidElement(tag, tag, getattr(pid.PidElementType, kind)).equipment(owner)
        entry.description(description).provenance("SYNTHETIC-TIEBACK", "Illustrative design basis")
        for connection in connections:
            entry.connect(connection)
        model.add(entry)
        return entry

    for tag, owner, desc in [("DHSV-101", "DHSV-101", f"Downhole safety valve, {basis.dhsv_stroke_s:g} s stroke"),
                             ("PMV-101", "PMV-101", "Production master valve, tree shutdown"),
                             ("PWV-101", "PWV-101", "Production wing valve, tree shutdown"),
                             ("HIPV-201A", "HIPV-201A", f"First series HIPPS valve, {basis.hipv_a_stroke_s:g} s stroke"),
                             ("HIPV-201B", "HIPV-201B", f"Second series HIPPS valve, {basis.hipv_b_stroke_s:g} s stroke"),
                             ("ESV-301", "ESV-301", "Riser emergency isolation"),
                             ("XSV-302", "XSV-302", "Platform inlet isolation")]:
        element(tag, "SHUTDOWN_VALVE", owner, desc)
    element("PSD-101", "SAFETY_FUNCTION", "PMV-101", "Subsea production shutdown",
            "DHSV-101", "PMV-101", "PWV-101")
    element("HIPPS-201", "SAFETY_FUNCTION", "HIPV-201A", "2oo3 live pressure vote",
            "HIPV-201A", "HIPV-201B")
    element("PSD-301", "SAFETY_FUNCTION", "ESV-301", "Host shutdown and remote PSD request",
            "ESV-301", "XSV-302", "PSD-101")
    for tag, limit, owner, function in [(PSD_TAG, basis.psd_bara, route, "PSD-101"),
                                        *((t, basis.hipps_bara, route, "HIPPS-201") for t in HIPPS_TAGS),
                                        ("PST-301", basis.host_riser_bara, "ESV-301", "PSD-301")]:
        element(tag, "MEASUREMENT", owner, "High pressure trip", function).attribute(
            "tripPressureBara", limit)
    element("PT-302", "MEASUREMENT", "V-301", "Separator gas pressure", "PSHH-302")
    element("PSHH-302", "SAFETY_FUNCTION", "XSV-302", "Separator pressure initiates inlet isolation",
            "XSV-302").attribute("tripPressureBara", SEPARATOR_PSHH_BARA).attribute(
                "seatDelaySeconds", INLET_SEAT_TIME_S)
    element("LT-301", "MEASUREMENT", "V-301", "Separator oil level")
    element("LIT-302", "MEASUREMENT", "V-301", "Separator water interface")
    element("PSV-301", "SAFETY_RELIEF_VALVE", "ESV-301",
            "Riser relief connection upstream of isolation to host flare; sizing open")
    element("PSV-302", "SAFETY_RELIEF_VALVE", "V-301", "Separator gas relief to host flare").attribute(
        "setPressureBara", PSV_SET_BARA).attribute("flarePressureBara", FLARE_BARA)
    bindings = getattr(pid.PidSimulationBindings, "from_")(project, model)
    for item in model.getElements():
        kind = str(item.getType().name())
        if kind in ("SHUTDOWN_VALVE", "SAFETY_RELIEF_VALVE"):
            bindings.bindEquipment(item.getTag(), item.getTag())
        elif kind == "MEASUREMENT":
            bindings.bindMeasurement(item.getTag(), item.getTag())
    meta = n.process.processmodel.dexpi.Dexpi20PlantExportMetadata
    metadata = meta.builder("2026-11-18T00:00:00Z", "Tie-back study", "NeqSim", "3.29.0")
    metadata.plantProperty(meta.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, "TIEBACK")
    path = jpype.JClass("java.io.File")(str(output.resolve())).toPath()
    pid.ProfessionalPidPackageExporter.export(project, model, bindings, metadata.build(), path)
    reread = pid.ProfessionalPidPackageReader.read(project, path)
    check = n.process.processmodel.dexpi.Dexpi201XmlWriter.assess(path.resolve("plant.dexpi.xml"))
    if not check.isSchemaAndProfileConformant() or reread.hasImportLosses():
        raise ValueError("DEXPI package failed its round-trip check: " + str(reread.getDiagnostics()))
    readiness = json.loads((output / "professional-pid-readiness.json").read_text())
    content = json.loads((output / "pid-drawing-content-report.json").read_text())
    material_status = "COMPLETE" if readiness["drawingHasNoErrors"] else "HOLD"
    unresolved = [h["subject"] for h in content["holds"] if h["code"] == "PID_ATTACHMENT_HOLD"]
    incomplete_signals = [h["subject"] for h in content["holds"] if h["code"] == "SIGNAL_CONTINUATION_INCOMPLETE"]
    return {"drawing_coverage": "HOLD" if unresolved or incomplete_signals else material_status,
            "material_drawing_coverage": material_status, "unresolved_drawing_attachments": unresolved,
            "incomplete_signal_continuations": incomplete_signals,
            "schema_and_profile": True, "fingerprint": str(reread.getSemanticFingerprint()),
            "functional_elements": model.getElements().size()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--basis", type=Path)
    args = parser.parse_args()
    jpype.startJVM("--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED",
                   "-Xmx768m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    print(json.dumps(export(jpype.JPackage("neqsim"), args.directory, ProtectionBasis.read(args.basis)), indent=2))
