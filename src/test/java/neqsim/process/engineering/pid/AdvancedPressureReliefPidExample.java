package neqsim.process.engineering.pid;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.GsonBuilder;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringSimulationResult;
import neqsim.process.engineering.EngineeringSimulationRunner;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata;
import neqsim.process.safety.scenario.AdvancedV101SafetyStudy;
import neqsim.process.safety.scenario.AdvancedV101PsvStudy;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;

/**
 * Recomputable actual passive-PSV proposal, native response evidence and DEXPI/P&amp;ID example.
 *
 * <p>
 * Policy thresholds are explicit synthetic study inputs. Automatic import proposes actual native instruments, functions
 * and hardware; canonical values then configure new native scenarios. These tests do not determine SIL, installed
 * independence, continuous-time safety, certified relief adequacy or construction/standards approval.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AdvancedPressureReliefPidExample {
  /** Prevents utility construction. */
  private AdvancedPressureReliefPidExample() {
  }

  /**
   * Executes actual passive nominal, relief, negative-control and cycle cases before native export.
   * 
   * @param args new output directory and optional timestep, seconds
   * @throws Exception if exact binding, strict execution or native export fails
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: AdvancedPressureReliefPidExample <new-output> [dt]");
    }
    Path output = Paths.get(args[0]).toAbsolutePath();
    if (Files.exists(output)) {
      throw new IllegalArgumentException("Output must be a new directory");
    }
    Files.createDirectories(output);
    double dt = args.length > 1 ? Double.parseDouble(args[1]) : 0.1;
    Design design = buildDesign();
    EngineeringProject project = design.project;
    final PidDesignModel canonical = design.pid;
    final PidSimulationBindings bindings = design.bindings;
    Map<String, Double> governed = governedSettings(canonical, design.policy);
    List<AdvancedV101PsvStudy.CaseBundle> cases = new ArrayList<AdvancedV101PsvStudy.CaseBundle>();
    Map<String, PidPressureReliefValidator.ConfiguredRelief> receipts = new LinkedHashMap<String, PidPressureReliefValidator.ConfiguredRelief>();
    Set<String> registeredReferences = new LinkedHashSet<String>();
    for (String mode : Arrays.asList("PSV_NORMAL", "PSV_RELIEF", "PSV_FAILED_CLOSED", "PSV_CYCLE")) {
      PidPressureReliefValidator.ConfiguredRelief receipt = configuredReceipt(design, mode);
      receipts.put(mode, receipt);
      if (registeredReferences.add(receipt.getEvidenceReference())) {
        project.addEvidenceRecord(
            new EngineeringEvidenceRecord(receipt.getEvidenceReference(), "ACTUAL_NATIVE_PASSIVE_RELIEF_CONFIGURATION",
                project.getRevision()).setTitle("Exact canonical/native PSV mechanical configuration")
                .setSourceOrganization("NeqSim public synthetic example").setChecksum(receipt.getFingerprint())
                .linkEquipment("20-PSV-001").linkEquipment("20-V-101"));
      }
      AdvancedV101PsvStudy.CaseBundle bundle = configuredCase(design, mode, dt, receipt);
      project.addDynamicSafetyScenario(bundle.getScenario());
      cases.add(bundle);
      write(output.resolve(mode + "-configuration.json"), receipt.toMap());
    }
    EngineeringSimulationResult executed = EngineeringSimulationRunner.runStrict(project);
    write(output.resolve("engineering-simulation-results.json"), executed.toMap());
    List<Map<String, Object>> feedback = new ArrayList<Map<String, Object>>();
    Set<String> envelope = new LinkedHashSet<String>(Arrays.asList("V101-PRESSURE-ENVELOPE", "V100-PRESSURE-ENVELOPE"));
    for (int index = 0; index < cases.size(); index++) {
      AdvancedV101PsvStudy.CaseBundle bundle = cases.get(index);
      DynamicSafetyScenarioResult result = executed.getDynamicScenarioResults().get(index);
      bundle.export(output.resolve("case-" + bundle.getMode()));
      write(output.resolve(bundle.getMode() + "-native-result.json"), result.toMap());
      feedback.add(PidPressureReliefValidator.attachResult(receipts.get(bundle.getMode()), bundle.getScenario(), result,
          envelope));
    }
    write(output.resolve("canonical-settings-used.json"), governed);
    write(output.resolve("canonical-pid.json"), canonical.toMap());
    write(output.resolve("dynamic-pid-feedback.json"), feedback);
    String checksum = sha256(Files.readAllBytes(output.resolve("dynamic-pid-feedback.json")));
    project.addEvidenceRecord(new EngineeringEvidenceRecord("ADVANCED-PSV-EXECUTED-FEEDBACK",
        "DYNAMIC_NATIVE_PASSIVE_RELIEF_BINDING_EVIDENCE", project.getRevision())
        .setTitle("Executed passive PSV pressure/lift/flow/reseat evidence; failures preserved")
        .setSourceOrganization("NeqSim public synthetic example").setChecksum(checksum).linkEquipment("20-PSV-001")
        .linkEquipment("20-V-101").linkEquipment("10-V-100"));
    write(output.resolve("project-validation.json"), project.validate());
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-05T00:00:00Z", "NeqSim native passive PSV example", "NeqSim", "development").plantProperty(
            Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE, project.getProjectId())
        .build();
    Path nativePackage = ProfessionalPidPackageExporter.export(project, canonical, bindings, metadata,
        output.resolve("native-pid-package"));
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("nativePackage", nativePackage.getFileName().toString());
    summary.put("actualMeasurementCount", design.source.getMeasurementDevices().size());
    summary.put("actualControllerCount", design.source.getControllerDevices().size());
    summary.put("canonicalElementCount", canonical.getElements().size());
    summary.put("feedbackChecksum", checksum);
    summary.put("nativeResponseFailed", executed.hasFailedDynamicScenario());
    summary.put("passiveReliefRole", "ACTUAL_MECHANICAL_RESPONSE_NO_PLC_SIGNAL_OR_FAKE_SIF");
    summary.put("capacityBasis", "SYNTHETIC_US_CV_NOT_CERTIFIED_RELIEVING_CAPACITY");
    summary.put("atmosphericReferenceBara", 1.01325);
    summary.put("atmosphericReferenceBasis", "EXPLICIT_SYNTHETIC_EXPORT_REFERENCE_NOT_A_MEASUREMENT");
    summary.put("energyAndComponentVerdicts",
        "Separate actual case metrics; response PASS does not clear numerical FAIL");
    summary.put("standardsCompliance", "CONTROLLED_REQUIREMENTS_AND_DISCIPLINE_REVIEW_HOLD");
    summary.put("safetyApproval", "NOT_GRANTED");
    write(output.resolve("example-summary.json"), summary);
    org.apache.logging.log4j.LogManager.getLogger(AdvancedPressureReliefPidExample.class).info("Exported {}",
        nativePackage);
  }

  /** Detached public-example engineering objects reused by focused executable regressions. */
  static final class Design {
    final ProcessSystem source;
    final EngineeringProject project;
    final PidDesignModel pid;
    final PidSimulationBindings bindings;
    final Map<String, Double> policy;

    /**
     * @param s physical source
     * @param p governed project
     * @param m canonical graph
     * @param b bindings
     * @param inputs settings
     */
    Design(ProcessSystem s, EngineeringProject p, PidDesignModel m, PidSimulationBindings b,
        Map<String, Double> inputs) {
      source = s;
      project = p;
      pid = m;
      bindings = b;
      policy = inputs;
    }
  }

  /** @return source-backed design retaining the original instruments/functions and one actual passive PSV */
  static Design buildDesign() {
    Map<String, Double> policy = AdvancedV101PsvStudy.defaultSettings();
    ProcessSystem source = AdvancedV101PsvStudy.buildSource(policy);
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Synthetic advanced PSV control/safety design", source, "2.0.1").projectId("ADVANCED-PSV-PID")
        .registerProposedInstruments(false).build().setRevision("A");
    project.getDesignBasis().setProjectPhase("Synthetic passive relief response; controlled review HOLD");
    for (String[] standard : new String[][] {{"ISO 15519-1", "2010"}, {"ISO 15519-2", "2015"}, {"ISO 10628-1", "2014"},
        {"ISO 10628-2", "2012"}, {"ISO 14617-1", "2025"}, {"ISO 14617-2", "2025"}, {"ANSI/ISA-5.1", "2024"},
        {"IEC 62424", "2016"}, {"ISO 10418", "2019"}, {"IEC 61511-1", "2016+A1:2017"}}) {
      project.getDesignBasis().addStandard(new EngineeringStandard(standard[0], standard[1], standard[0],
          "Public-scope reference only; controlled requirements/engineering review HOLD"));
    }
    project.addEvidenceRecord(new EngineeringEvidenceRecord("SYNTHETIC-PSV-CV-BASIS", "SYNTHETIC_CV_ASSUMPTION", "A")
        .setTitle("Synthetic rated US Cv " + policy.get("psvRatedCv")
            + " with enabled native gas choking; no certified mass-flow capacity")
        .setSourceOrganization("NeqSim public synthetic example").linkEquipment("20-PSV-001"));
    Set<String> lines = new LinkedHashSet<String>();
    for (ProcessEquipmentInterface unit : source.getUnitOperations()) {
      for (StreamInterface stream : unit.getInletStreams()) {
        lines.add(stream.getName());
      }
      for (StreamInterface stream : unit.getOutletStreams()) {
        lines.add(stream.getName());
      }
    }
    for (String line : lines) {
      project.addLineDesignInput(
          new LineDesignInput(line, "UNRESOLVED-PIPELINE-ASSOCIATION").setInstallationTemperatureC(Double.NaN));
    }
    PidDesignModel pid = basicControlProposal(project, source);
    ProcessSystem configured = source.copy();
    configured.run();
    AdvancedV101PsvStudy.configureAfterSteady(configured, policy);
    for (ClosedLoopSafetyFunction function : AdvancedV101SafetyStudy.createFunctions(configured, policy, "NORMAL")) {
      String role = function.getName().contains("HIPPS") ? "HIPPS"
          : function.getName().contains("MANUAL") ? "MANUAL_ESD" : "ESD";
      pid = PidSafetyFunctionImporter.fromNativeFunction(project, pid, bindings(project, pid), configured, function,
          role);
    }
    pid = PidPressureReliefImporter.fromNativeValve(project, pid, bindings(project, pid), configured, "20-PSV-001",
        "20-V-101", "20-TEE-PSV-001", "SYNTHETIC-PSV-CV-BASIS");
    tag(pid, "20-PSV-001").attribute("simulationAtmosphericReferenceBara", Double.valueOf(1.01325));
    return new Design(source, project, pid, bindings(project, pid), policy);
  }

  /**
   * Freezes actual canonical values on the same configured topology used by each strict factory.
   * 
   * @param design governed model
   * @param mode declared scenario profile
   * @return exact healthy/fault/boundary configured-copy receipt
   */
  static PidPressureReliefValidator.ConfiguredRelief configuredReceipt(Design design, String mode) {
    ProcessSystem copy = design.source.copy();
    copy.run();
    AdvancedV101PsvStudy.configureAfterSteady(copy, governedSettings(design.pid, design.policy));
    return PidPressureReliefValidator.applyCanonicalConfiguration(design.project, design.pid, design.bindings, copy,
        "20-PSV-001", AdvancedV101PsvStudy.configurationOverrides(mode),
        AdvancedV101PsvStudy.backpressureOverride(mode));
  }

  /**
   * Creates a strict case that consumes the canonical settings before its first physical step.
   * 
   * @param design governed model
   * @param mode declared scenario mode
   * @param dt physical scan timestep, seconds
   * @param receipt frozen configured-copy receipt
   * @return case bound to exact native readback and declared scenario evidence
   */
  static AdvancedV101PsvStudy.CaseBundle configuredCase(Design design, String mode, double dt,
      PidPressureReliefValidator.ConfiguredRelief receipt) {
    Map<String, Double> governed = governedSettings(design.pid, design.policy);
    AdvancedV101PsvStudy.CaseBundle bundle = AdvancedV101PsvStudy.createCase(design.source, governed, mode, dt,
        Collections.singletonList(receipt.getEvidenceReference()));
    bundle.setConfigurationObserver((process, psv, settings, profile) -> {
      if (!settings.equals(governed)) {
        throw new IllegalStateException("Factory did not consume governed BPCS settings");
      }
      AdvancedSafetyPidExample.validateBpcsReadback(design.pid, design.bindings, process);
      receipt.assertSameConfiguration(PidPressureReliefValidator.applyCanonicalConfiguration(design.project, design.pid,
          design.bindings, process, "20-PSV-001", AdvancedV101PsvStudy.configurationOverrides(profile),
          AdvancedV101PsvStudy.backpressureOverride(profile)));
    });
    return bundle;
  }

  /**
   * Proposes the actual existing monitoring and BPCS objects; native SIF import adds safety-function policy.
   *
   * @param project governed source
   * @param process actual source
   * @return initial canonical actual-object proposal
   */
  private static PidDesignModel basicControlProposal(EngineeringProject project, ProcessSystem process) {
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "ACTUAL-NATIVE-ADVANCED-SAFETY-v1");
    for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
      if (measurement instanceof PushButton) {
        continue;
      }
      PidElement element = proposal(measurement.getName(), PidElementType.MEASUREMENT);
      if (measurement instanceof LevelTransmitter) {
        element.equipment(((LevelTransmitter) measurement).getVessel().getName()).attribute("sensingLocation",
            "VESSEL");
      } else {
        StreamInterface stream = ((StreamMeasurementDeviceBaseClass) measurement).getStream();
        element.equipment(owner(process, stream).getName()).line(stream.getName());
      }
      element.attribute("nativeMeasurementUnit", measurement.getUnit());
      pid.add(element);
    }
    for (ControllerDeviceInterface controller : process.getControllerDevices()) {
      ProcessEquipmentInterface actuator = null;
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (unit.getControllers().contains(controller)) {
          actuator = unit;
        }
      }
      if (!(actuator instanceof ThrottlingValve)) {
        throw new IllegalArgumentException("Actual BPCS controller lacks its native valve target");
      }
      StreamInterface stream = actuator.getInletStreams().get(0);
      PidElement valve = proposal(actuator.getName(), PidElementType.CONTROL_VALVE)
          .equipment(owner(process, stream).getName()).line(stream.getName())
          .attribute("simulationValveTravelSeconds", Double.valueOf(3.0));
      ControllerDeviceBaseClass nativeController = (ControllerDeviceBaseClass) controller;
      PidElement control = proposal(controller.getName(), PidElementType.CONTROLLER).equipment(valve.getEquipmentTag())
          .connect(valve.getId()).attribute("simulationKp", Double.valueOf(nativeController.getKp()))
          .attribute("simulationTiSeconds", Double.valueOf(nativeController.getTi()))
          .attribute("simulationSetpoint", Double.valueOf(nativeController.getControllerSetPoint()))
          .attribute("simulationUnit", nativeController.getUnit()).attribute("controlRole", "BPCS");
      tag(pid, controller.getTransmitter().getName()).connect(control.getId());
      pid.add(valve).add(control);
    }
    return pid;
  }

  /**
   * Extracts executable candidate values from the imported canonical proposal, retaining independent study limits.
   *
   * @param pid canonical imported graph
   * @param policy independent frozen study limits
   * @return native fixture settings actually consumed by the scenario factories
   */
  private static Map<String, Double> governedSettings(PidDesignModel pid, Map<String, Double> policy) {
    Map<String, Double> result = new LinkedHashMap<String, Double>(policy);
    result.put("hippsTripBara", number(pid, "20-PT-101A", "simulationTripSetpoint"));
    for (String name : Arrays.asList("20-PT-101B", "20-PT-101C")) {
      if (!number(pid, name, "simulationTripSetpoint").equals(result.get("hippsTripBara"))) {
        throw new IllegalArgumentException("This fixture requires a common governed HIPPS threshold");
      }
    }
    result.put("esdV100TripBara", number(pid, "10-PT-002", "simulationTripSetpoint"));
    result.put("esdV101TripBara", number(pid, "20-PT-002", "simulationTripSetpoint"));
    result.put("sensorQualificationSeconds", number(pid, "20-PT-101A", "simulationResponseDelaySeconds"));
    result.put("logicDelaySeconds", number(pid, "SIF-HIPPS-001", "simulationLogicDelaySeconds"));
    result.put("hippsStrokeSeconds", number(pid, "20-HIPPS-XV-001", "simulationStrokeSeconds"));
    result.put("esdStrokeSeconds", number(pid, "20-ESDV-001", "simulationStrokeSeconds"));
    result.put("pressureKpPercentPerBar", number(pid, "20-PIC-001", "simulationKp"));
    result.put("pressureTiSeconds", number(pid, "20-PIC-001", "simulationTiSeconds"));
    result.put("levelKpPercentPerPercent", number(pid, "20-LIC-001", "simulationKp"));
    result.put("levelTiSeconds", number(pid, "20-LIC-001", "simulationTiSeconds"));
    Map<?, ?> relief = (Map<?, ?>) tag(pid, "20-PSV-001").getAttributes().get("simulationPressureReliefConfiguration");
    for (String[] entry : new String[][] {{"psvSetPressureBara", "setPressureBar"},
        {"psvOverpressureFraction", "overpressureFraction"}, {"psvBlowdownFraction", "blowdownFraction"},
        {"psvRatedCv", "ratedCv"}, {"psvKd", "kd"}, {"psvKbMax", "kbMax"},
        {"psvBackpressureSensitivity", "backpressureSensitivity"}, {"psvTauOpenSeconds", "tauOpenSeconds"},
        {"psvTauCloseSeconds", "tauCloseSeconds"}, {"psvMinimumOpenDwellSeconds", "minimumOpenDwellSeconds"},
        {"psvMinimumClosedDwellSeconds", "minimumClosedDwellSeconds"},
        {"psvMaximumLiftRatePerSecond", "maximumLiftRatePerSecond"},
        {"psvMinimumStableCommandFraction", "minimumStableCommandFraction"},
        {"psvSeatToleranceFraction", "seatToleranceFraction"}}) {
      Object value = relief.get(entry[1]);
      if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
        throw new IllegalArgumentException("Missing canonical finite PSV setting " + entry[1]);
      }
      result.put(entry[0], Double.valueOf(((Number) value).doubleValue()));
    }
    Map<?, ?> route = (Map<?, ?>) tag(pid, "20-PSV-001").getAttributes().get("simulationPressureReliefRoute");
    result.put("psvBackpressureBara", Double.valueOf(((Number) route.get("backpressureBara")).doubleValue()));
    return result;
  }

  /**
   * Rejects controller annotations that do not describe the actual native tuning used by the scenario copy.
   *
   * @param pid canonical graph
   * @param bindings current exact bindings
   * @param process actual configured process
   */
  static void validateBpcsReadback(PidDesignModel pid, PidSimulationBindings bindings, ProcessSystem process) {
    for (PidElement element : pid.getElementsByType(PidElementType.CONTROLLER)) {
      ControllerDeviceBaseClass actual = (ControllerDeviceBaseClass) bindings.resolveController(element.getTag(),
          process);
      if (Math.abs(actual.getKp() - number(pid, element.getTag(), "simulationKp")) > 1.0e-12
          || Math.abs(actual.getTi() - number(pid, element.getTag(), "simulationTiSeconds")) > 1.0e-12
          || Math.abs(actual.getControllerSetPoint() - number(pid, element.getTag(), "simulationSetpoint")) > 1.0e-12
          || !actual.getUnit().equals(element.getAttributes().get("simulationUnit"))) {
        throw new IllegalArgumentException("Canonical BPCS settings differ from the actual native controller");
      }
    }
    for (PidElement element : pid.getElementsByType(PidElementType.CONTROL_VALVE)) {
      ThrottlingValve actual = (ThrottlingValve) bindings.resolveEquipment(element.getTag(), process);
      if (actual.getTravelModel() != ValveTravelModel.LINEAR_RATE_LIMIT || Math
          .abs(actual.getTravelTime() - number(pid, element.getTag(), "simulationValveTravelSeconds")) > 1.0e-12) {
        throw new IllegalArgumentException(
            "Canonical BPCS valve timing differs from the configured native travel model");
      }
    }
  }

  /**
   * Binds actual instruments, BPCS controllers and valves while leaving safety functions governed by native receipts.
   *
   * @param project actual source project
   * @param pid canonical model
   * @return complete actual hardware/BPCS bindings
   */
  private static PidSimulationBindings bindings(EngineeringProject project, PidDesignModel pid) {
    PidSimulationBindings result = PidSimulationBindings.from(project, pid);
    for (PidElement element : pid.getElements()) {
      if (element.getType() == PidElementType.MEASUREMENT) {
        result.bindMeasurement(element.getTag(), element.getTag());
      } else if (element.getType() == PidElementType.CONTROLLER) {
        result.bindController(element.getTag(), element.getTag());
      } else if (element.getType() == PidElementType.SHUTDOWN_VALVE || element.getType() == PidElementType.CONTROL_VALVE
          || element.getType() == PidElementType.SAFETY_RELIEF_VALVE) {
        result.bindEquipment(element.getTag(), element.getTag());
      }
    }
    return result;
  }

  /**
   * Finds the actual producer of one actual process stream.
   *
   * @param process native source
   * @param stream actual stream
   * @return actual producing equipment
   */
  private static ProcessEquipmentInterface owner(ProcessSystem process, StreamInterface stream) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && unit.getOutletStreams().contains(stream)) {
        if (unit instanceof neqsim.process.equipment.splitter.Splitter) {
          return owner(process, unit.getInletStreams().get(0));
        }
        return unit;
      }
    }
    throw new IllegalArgumentException("No actual equipment owner for measured stream " + stream.getName());
  }

  /**
   * Creates a stable actual-object proposal.
   *
   * @param name actual native name
   * @param type canonical category
   * @return unapproved proposal
   */
  private static PidElement proposal(String name, PidElementType type) {
    return new PidElement("native:" + name, name, type).provenance("ACTUAL-NATIVE-BPCS-AND-MONITORING-v1",
        "Actual executable source object proposed for discipline review; no installed hardware or standards approval")
        .attribute("approvalStatus", "REVIEW_REQUIRED");
  }

  /**
   * Finds a canonical proposal tag.
   *
   * @param pid canonical graph
   * @param name exact tag
   * @return proposal
   */
  private static PidElement tag(PidDesignModel pid, String name) {
    for (PidElement element : pid.getElements()) {
      if (element.getTag().equals(name)) {
        return element;
      }
    }
    throw new IllegalArgumentException("Missing canonical tag " + name);
  }

  /**
   * Reads a finite governed numeric setting.
   *
   * @param pid canonical graph
   * @param name exact element tag
   * @param key governed key
   * @return finite scalar
   */
  private static Double number(PidDesignModel pid, String name, String key) {
    Object value = tag(pid, name).getAttributes().get(key);
    if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
      throw new IllegalArgumentException("Missing finite canonical setting " + name + "/" + key);
    }
    return Double.valueOf(((Number) value).doubleValue());
  }

  /**
   * Writes exact structured evidence as UTF-8 JSON.
   *
   * @param file new evidence file
   * @param value actual structured result
   * @throws Exception if writing fails
   */
  private static void write(Path file, Object value) throws Exception {
    Files.write(file, new GsonBuilder().setPrettyPrinting().create().toJson(value).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Hashes an exact artifact.
   *
   * @param bytes exact artifact bytes
   * @return lowercase SHA-256
   * @throws Exception if SHA-256 is unavailable
   */
  private static String sha256(byte[] bytes) throws Exception {
    StringBuilder result = new StringBuilder();
    for (byte item : MessageDigest.getInstance("SHA-256").digest(bytes)) {
      result.append(String.format(java.util.Locale.ROOT, "%02x", Integer.valueOf(item & 0xff)));
    }
    return result.toString();
  }
}
