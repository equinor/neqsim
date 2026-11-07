package neqsim.process.engineering.pid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.measurementdevice.CompressorSurgeMarginMeasurement;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.process.measurementdevice.TransientMeasurementDevice;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Imports, configures and verifies actual native control-system instrumentation in a canonical P&amp;ID.
 *
 * <p>
 * The source process is authoritative for hardware and causal object identity. The canonical proposal governs complete
 * supported PID/anti-surge policy, instrument signal settings and native valve hydraulics/travel. Generic safeguarding
 * proposals are not promoted to physical objects. This is an automation-engineering simulation handoff, not a safety
 * PLC, vendor machinery-protection package, installed independence assessment or construction approval.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidNativeControlSystem {
  private static final Gson GSON = new Gson();
  private static final String CONFIGURATION = "nativeControlConfiguration";

  /** Prevents utility construction. */
  private PidNativeControlSystem() {
  }

  /**
   * Creates proposals solely for actual registered instruments, native controllers and valve equipment.
   *
   * @param project governed project with its actual source process
   * @return source-backed review-required canonical control graph
   * @throws IllegalArgumentException if sources, targets or supported native wiring are ambiguous
   */
  public static PidDesignModel fromProcess(EngineeringProject project) {
    if (project == null) {
      throw new IllegalArgumentException("A governed project is required");
    }
    ProcessSystem process = project.getEngineeringProcessSystem();
    PidDesignModel model = new PidDesignModel(project.getProjectId(), "ACTUAL-NATIVE-CONTROL-SYSTEM-v1");
    for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
      PidElement element = proposal(measurement.getName(), PidElementType.MEASUREMENT);
      if (measurement instanceof CompressorSurgeMarginMeasurement) {
        CompressorSurgeMarginMeasurement margin = (CompressorSurgeMarginMeasurement) measurement;
        requireRegistered(process, margin.getCompressor());
        requireMeasurement(process, margin.getFlowTransmitter());
        element.equipment(margin.getCompressor().getName()).attribute("inputKind", "CALCULATED_COMPRESSOR_MAP_MARGIN")
            .attribute("calculation", "HELD_INLET_ACTUAL_VOLUME_FLOW / NATIVE_MODEL_SURGE_REFERENCE_FLOW - 1")
            .attribute("modelReferences",
                "Native active surge curve, head, speed, IGV and composition; not independently measured")
            .attribute("physicalTransmitter", Boolean.FALSE);
      } else if (measurement instanceof LevelTransmitter) {
        ProcessEquipmentInterface vessel = ((LevelTransmitter) measurement).getVessel();
        requireRegistered(process, vessel);
        element.equipment(vessel.getName()).attribute("sensingLocation", "VESSEL");
      } else if (measurement instanceof StreamMeasurementDeviceBaseClass) {
        StreamInterface stream = ((StreamMeasurementDeviceBaseClass) measurement).getStream();
        element.equipment(owner(process, stream).getName()).line(stream.getName());
      } else if (measurement.getClass() == PushButton.class) {
        element.equipment(firstOwner(process).getName()).attribute("inputKind", "MANUAL_PUSH_BUTTON");
      } else {
        throw new IllegalArgumentException(
            "Actual instrument has no supported source location: " + measurement.getName());
      }
      element.attribute(CONFIGURATION, sensorConfiguration(measurement));
      model.add(element);
    }
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment instanceof ThrottlingValve
          && (equipment.getClass() == ThrottlingValve.class || equipment.getClass() == ESDValve.class
              || equipment.getClass() == neqsim.process.equipment.valve.LevelControlValve.class
              || equipment.getClass() == neqsim.process.equipment.valve.PressureControlValve.class)) {
        ThrottlingValve valve = (ThrottlingValve) equipment;
        PidElementType type = valve instanceof ESDValve ? PidElementType.SHUTDOWN_VALVE : PidElementType.CONTROL_VALVE;
        model.add(proposal(valve.getName(), type).equipment(valveDrawingOwner(process, valve).getName())
            .line(valve.getInletStream().getName()).attribute(CONFIGURATION, valveConfiguration(valve)));
      }
    }
    Set<ControllerDeviceInterface> seen = Collections
        .newSetFromMap(new IdentityHashMap<ControllerDeviceInterface, Boolean>());
    for (ControllerDeviceInterface controller : controllers(process)) {
      if (!seen.add(controller)) {
        continue;
      }
      ControllerDeviceBaseClass nativeController = supportedController(controller);
      ProcessEquipmentInterface actuator = target(process, controller);
      PidElement finalElement;
      if (actuator instanceof Compressor) {
        String tag = actuator.getName() + "-SPEED-DEMAND";
        finalElement = null;
        for (PidElement candidate : model.getElements()) {
          if (tag.equals(candidate.getTag())) {
            finalElement = candidate;
          }
        }
        if (finalElement == null) {
          finalElement = proposal(tag, PidElementType.SIGNAL).equipment(actuator.getName())
              .attribute("nativeActuatorRole", "COMPRESSOR_SPEED")
              .attribute("simulationTargetEquipmentTag", actuator.getName())
              .attribute("physicalDriverHardwareAdded", Boolean.FALSE)
              .attribute(CONFIGURATION, compressorSpeedConfiguration((Compressor) actuator));
          model.add(finalElement);
        }
      } else if (actuator.getClass() == Cooler.class && ((Cooler) actuator).isDynamicTemperatureControlEnabled()) {
        String tag = actuator.getName() + "-UTILITY-DEMAND";
        finalElement = proposal(tag, PidElementType.SIGNAL).equipment(actuator.getName())
            .attribute("nativeActuatorRole", "COOLER_UTILITY_OPENING")
            .attribute("simulationTargetEquipmentTag", actuator.getName())
            .attribute("physicalUtilityValveInstalled", Boolean.FALSE)
            .attribute(CONFIGURATION, coolerUtilityConfiguration((Cooler) actuator));
        model.add(finalElement);
      } else {
        finalElement = byTag(model, actuator.getName());
      }
      MeasurementDeviceInterface source = input(controller);
      if (source != null) {
        requireMeasurement(process, source);
      }
      PidElement element = proposal(controller.getName(), PidElementType.CONTROLLER)
          .equipment(finalElement.getEquipmentTag())
          .attribute(CONFIGURATION, nativeController.getControlConfiguration()).connect(finalElement.getId());
      if (source == null) {
        if (!(controller instanceof AntiSurgeController)) {
          throw new IllegalArgumentException("Native PID controller has no actual measurement input");
        }
        element.attribute("inputKind", "DIRECT_NATIVE_COMPRESSOR_MAP").attribute("physicalInstrumentFaultCoverage",
            "NOT_ESTABLISHED");
      } else {
        byTag(model, source.getName()).connect(element.getId());
      }
      model.add(element);
    }
    for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
      if (measurement instanceof CompressorSurgeMarginMeasurement) {
        CompressorSurgeMarginMeasurement margin = (CompressorSurgeMarginMeasurement) measurement;
        byTag(model, margin.getFlowTransmitter().getName()).connect(byTag(model, margin.getName()).getId());
      }
    }
    return model;
  }

  /**
   * Rebuilds explicit bindings from actual source names after every canonical revision.
   *
   * @param project governed source project
   * @param model current canonical graph
   * @return complete actual-object register; function-only elements remain semantic functions
   */
  public static PidSimulationBindings bind(EngineeringProject project, PidDesignModel model) {
    PidSimulationBindings bindings = PidSimulationBindings.from(project, model);
    ProcessSystem process = project.getEngineeringProcessSystem();
    for (PidElement element : model.getElements()) {
      if (element.getType() == PidElementType.MEASUREMENT || element.getType() == PidElementType.INDICATOR) {
        for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
          if (element.getTag().equals(measurement.getName())) {
            bindings.bindMeasurement(element.getTag(), measurement.getName());
          }
        }
      } else if (element.getType() == PidElementType.CONTROLLER) {
        for (ControllerDeviceInterface controller : controllers(process)) {
          if (element.getTag().equals(controller.getName())) {
            bindings.bindController(element.getTag(), controller.getName());
          }
        }
      } else if (process.getUnit(element.getTag()) instanceof ThrottlingValve) {
        bindings.bindEquipment(element.getTag(), element.getTag());
      }
    }
    return bindings;
  }

  /**
   * Applies complete supported canonical settings to the caller's isolated copy, never the source project.
   *
   * @param project governed source project
   * @param model revised canonical control graph
   * @param bindings freshly rebound current identities
   * @param configured isolated process copy
   * @return readback-verified configuration receipt
   * @throws IllegalArgumentException if the caller passes source objects or unsupported settings
   */
  public static ConfiguredControlSystem applyCanonicalConfiguration(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem configured) {
    requireIsolated(project, configured);
    bindings.assertCurrent(project, model);
    for (PidElement element : model.getElements()) {
      if (!element.getAttributes().containsKey(CONFIGURATION)) {
        continue;
      }
      Map<String, Object> values = configuration(element);
      if (element.getType() == PidElementType.CONTROLLER) {
        supportedController(bindings.resolveController(element.getTag(), configured)).applyControlConfiguration(values);
      } else if (element.getType() == PidElementType.MEASUREMENT || element.getType() == PidElementType.INDICATOR) {
        applySensor(bindings.resolveMeasurement(element.getTag(), configured), values);
      } else if (isCompressorSpeed(element)) {
        applyCompressorSpeed(speedTarget(configured, element), values);
      } else if (isCoolerUtility(element)) {
        applyCoolerUtility(coolerTarget(configured, element), values);
      } else {
        applyValve((ThrottlingValve) bindings.resolveEquipment(element.getTag(), configured), values);
      }
    }
    return validateConfigured(project, model, bindings, configured);
  }

  /**
   * Verifies complete actual policy and causal wiring before native scenario execution.
   *
   * @param project governed source
   * @param model canonical control graph
   * @param bindings current explicit identities
   * @param configured isolated configured process
   * @return detached immutable configuration receipt
   */
  public static ConfiguredControlSystem validateConfigured(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem configured) {
    return validateConfigured(project, model, bindings, configured,
        Collections.<String, Map<String, Object>>emptyMap());
  }

  /**
   * Verifies declared instrument, control-availability and actuator fault overrides against the healthy canonical
   * design.
   *
   * @param project governed source
   * @param model canonical graph
   * @param bindings current explicit identities
   * @param configured isolated configured process
   * @param sensorFaultOverrides explicit per-tag instrument/controller/actuator fault fields; never unreported geometry
   * or tuning
   * @return configuration receipt containing declared overrides and actual native readback
   * @throws IllegalArgumentException if settings, aliases, endpoints or causal graph differ
   */
  public static ConfiguredControlSystem validateConfigured(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem configured, Map<String, Map<String, Object>> sensorFaultOverrides) {
    requireIsolated(project, configured);
    if (bindings == null || sensorFaultOverrides == null) {
      throw new IllegalArgumentException("Bindings and explicit fault overrides are required");
    }
    bindings.assertCurrent(project, model);
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    Set<String> seenOverrides = new java.util.LinkedHashSet<String>();
    for (PidElement element : model.getElements()) {
      if (!element.getAttributes().containsKey(CONFIGURATION)) {
        continue;
      }
      Map<String, Object> expected = configuration(element);
      Map<String, Object> actual;
      if (element.getType() == PidElementType.CONTROLLER) {
        ControllerDeviceBaseClass controller = supportedController(
            bindings.resolveController(element.getTag(), configured));
        ProcessEquipmentInterface actuator = target(configured, controller);
        PidElement canonicalActuator = byId(model, onlyConnection(element));
        ProcessEquipmentInterface canonicalTarget = isCompressorSpeed(canonicalActuator)
            ? speedTarget(configured, canonicalActuator)
            : isCoolerUtility(canonicalActuator) ? coolerTarget(configured, canonicalActuator)
                : bindings.resolveEquipment(canonicalActuator.getTag(), configured);
        if (canonicalTarget != actuator) {
          throw new IllegalArgumentException("Canonical controller target is not its actual native actuator");
        }
        MeasurementDeviceInterface source = input(controller);
        List<PidElement> incoming = incoming(model, element);
        if (source == null ? !incoming.isEmpty()
            : incoming.size() != 1 || bindings.resolveMeasurement(incoming.get(0).getTag(), configured) != source) {
          throw new IllegalArgumentException("Canonical controller input is not its actual native measurement");
        }
        if (controller instanceof AntiSurgeController) {
          requireRegistered(configured, ((AntiSurgeController) controller).getCompressor());
        }
        actual = controller.getControlConfiguration();
      } else if (element.getType() == PidElementType.MEASUREMENT || element.getType() == PidElementType.INDICATOR) {
        MeasurementDeviceInterface measurement = bindings.resolveMeasurement(element.getTag(), configured);
        actual = sensorConfiguration(measurement);
        if (measurement instanceof CompressorSurgeMarginMeasurement) {
          CompressorSurgeMarginMeasurement margin = (CompressorSurgeMarginMeasurement) measurement;
          List<PidElement> incoming = incoming(model, element);
          if (incoming.size() != 1
              || bindings.resolveMeasurement(incoming.get(0).getTag(), configured) != margin.getFlowTransmitter()
              || margin.getFlowTransmitter().getStream() != margin.getCompressor().getInletStream()) {
            throw new IllegalArgumentException("Calculated margin must consume its actual bound compressor-inlet FT");
          }
        }
      } else if (isCompressorSpeed(element)) {
        actual = compressorSpeedConfiguration(speedTarget(configured, element));
      } else if (isCoolerUtility(element)) {
        Cooler cooler = coolerTarget(configured, element);
        if (cooler.getCalculateSteadyState()) {
          throw new IllegalArgumentException("Native cooler utility control requires actual transient dispatch mode");
        }
        actual = coolerUtilityConfiguration(cooler);
      } else {
        actual = valveConfiguration((ThrottlingValve) bindings.resolveEquipment(element.getTag(), configured));
      }
      if (sensorFaultOverrides.containsKey(element.getTag())) {
        Map<String, Object> fault = sensorFaultOverrides.get(element.getTag());
        java.util.Set<String> allowed = new java.util.LinkedHashSet<String>();
        if (element.getType() == PidElementType.MEASUREMENT || element.getType() == PidElementType.INDICATOR) {
          allowed.addAll(java.util.Arrays.asList("faultType", "faultParameter"));
          if (fault == null || fault.size() != 2 || !fault.keySet().equals(allowed)) {
            throw new IllegalArgumentException("Declared instrument faults require faultType and faultParameter");
          }
        } else if (element.getType() == PidElementType.CONTROLLER) {
          allowed.add("active");
        } else if (isCoolerUtility(element)) {
          allowed.add("utilityAvailabilityFraction");
        } else {
          allowed.addAll(java.util.Arrays.asList("actuatorAvailable", "strokeSeconds", "openingTravelSeconds",
              "closingTravelSeconds", "travelSeconds", "timeConstantSeconds", "foulingFraction"));
        }
        if (fault == null || fault.isEmpty() || !allowed.containsAll(fault.keySet())
            || !expected.keySet().containsAll(fault.keySet())) {
          throw new IllegalArgumentException(
              "Only explicit supported instrument/controller/actuator fault fields are allowed");
        }
        expected.putAll(fault);
        seenOverrides.add(element.getTag());
      }
      if (!GSON.toJsonTree(expected).equals(GSON.toJsonTree(actual))) {
        throw new IllegalArgumentException("Canonical/native control configuration differs for " + element.getTag());
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("pidElementId", element.getId());
      row.put("pidTag", element.getTag());
      row.put("type", element.getType().name());
      row.put("equipmentTag", element.getEquipmentTag());
      row.put("lineTag", element.getLineTag());
      row.put("nativeConfiguration", actual);
      row.put("connectedElementIds", new ArrayList<String>(element.getConnectedElementIds()));
      rows.add(row);
    }
    if (!seenOverrides.equals(sensorFaultOverrides.keySet())) {
      throw new IllegalArgumentException("Fault override does not identify a governed actual control object");
    }
    Map<String, Object> receipt = new LinkedHashMap<String, Object>();
    receipt.put("schemaVersion", "neqsim.native_control_configuration_receipt.v1");
    receipt.put("projectId", project.getProjectId());
    receipt.put("revision", project.getRevision());
    receipt.put("bindingConfiguration", bindings.toMap());
    receipt.put("nativeControls", rows);
    receipt.put("declaredFaultOverrides", sensorFaultOverrides);
    receipt.put("executionEvidence", "CONFIGURATION_ONLY_NO_RESPONSE_CREDIT");
    receipt.put("qualification", "CONTROLLED_ENGINEERING_STANDARDS_VENDOR_AND_SAFETY_REVIEW_HOLD");
    return new ConfiguredControlSystem(receipt);
  }

  /**
   * Creates source-backed I/O, loop, cause/effect and unresolved-requirement registers for automation engineering.
   *
   * @param project governed source
   * @param model canonical graph including imported actual safety functions
   * @param bindings current identities
   * @return detached registers; rows never infer tested protection or SIL from a diagram
   */
  public static Map<String, Object> automationRegisters(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings) {
    bindings.assertCurrent(project, model);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> io = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> loops = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> effects = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> requirements = new ArrayList<Map<String, Object>>();
    for (PidElement element : model.getElements()) {
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("tag", element.getTag());
      row.put("equipmentTag", element.getEquipmentTag());
      row.put("lineTag", element.getLineTag());
      row.put("type", element.getType().name());
      row.put("canonicalElementId", element.getId());
      row.put("qualification", "REVIEW_REQUIRED");
      if (element.getType() == PidElementType.MEASUREMENT && bindings.getBoundPidTags().contains(element.getTag())) {
        MeasurementDeviceInterface actual = bindings.resolveMeasurement(element.getTag(),
            project.getEngineeringProcessSystem());
        row.put("actualClass", actual.getClass().getName());
        row.put("ioDirection", "INPUT");
        row.put("unit", actual.getUnit());
        double value = actual.getMeasuredValue(actual.getUnit());
        row.put("sourceValue", Double.isFinite(value) ? Double.valueOf(value) : "UNAVAILABLE");
        row.put("valueBasis", "CURRENT_SOURCE_OBJECT_READBACK_NOT_A_DYNAMIC_RESPONSE_VERDICT");
        row.put("causalOutputTags", connectedTags(model, element));
        row.put("fieldAddress", "UNASSIGNED");
        row.put("minimum", Double.valueOf(actual.getMinimumValue()));
        row.put("maximum", Double.valueOf(actual.getMaximumValue()));
        row.put("signalKind",
            actual instanceof PushButton ? "NATIVE_DISCRETE_INPUT"
                : actual instanceof CompressorSurgeMarginMeasurement
                    ? "CALCULATED_FROM_HELD_FT_AND_NATIVE_MAP_REFERENCE"
                    : "NATIVE_PROCESS_MEASUREMENT");
        row.put("physicalInstrument", Boolean.valueOf(!(actual instanceof CompressorSurgeMarginMeasurement)));
        io.add(row);
      } else if ((element.getType() == PidElementType.SHUTDOWN_VALVE
          || element.getType() == PidElementType.CONTROL_VALVE)
          && bindings.getBoundPidTags().contains(element.getTag())) {
        ThrottlingValve valve = (ThrottlingValve) project.getEngineeringProcessSystem()
            .getUnit(bindings.getSimulationName(element.getTag()));
        row.put("ioDirection", "OUTPUT_WITH_SIMULATED_POSITION_FEEDBACK");
        row.put("signalKind", "NATIVE_VALVE_ACTUATOR_COMMAND");
        row.put("unit", "%");
        row.put("commandPercent", Double.valueOf(valve.getTargetPercentValveOpening()));
        row.put("actualPositionPercent", Double.valueOf(valve.getPercentValveOpening()));
        row.put("physicalPositionTransmitterInstalled", Boolean.FALSE);
        row.put("fieldAddress", "UNASSIGNED");
        row.put("actualClass", valve.getClass().getName());
        row.put("configuration", configuration(element));
        io.add(row);
      } else if (isCompressorSpeed(element)) {
        Compressor compressor = speedTarget(project.getEngineeringProcessSystem(), element);
        row.put("ioDirection", "OUTPUT");
        row.put("signalKind", "NATIVE_COMPRESSOR_SPEED_COMMAND");
        row.put("unit", "RPM");
        row.put("sourceValue", Double.valueOf(compressor.getSpeed()));
        row.put("physicalDriverHardwareAdded", Boolean.FALSE);
        row.put("fieldAddress", "UNASSIGNED");
        row.put("configuration", configuration(element));
        io.add(row);
      } else if (isCoolerUtility(element)) {
        Cooler cooler = coolerTarget(project.getEngineeringProcessSystem(), element);
        row.put("ioDirection", "OUTPUT_WITH_EMBEDDED_ACTUATOR_MODEL");
        row.put("signalKind", "NATIVE_COOLER_UTILITY_OPENING_COMMAND");
        row.put("unit", "%");
        row.put("actualPositionPercent", Double.valueOf(cooler.getCoolingValveOpening()));
        row.put("physicalUtilityValveInstalled", Boolean.FALSE);
        row.put("fieldAddress", "UNASSIGNED");
        row.put("configuration", configuration(element));
        io.add(row);
      } else if (element.getType() == PidElementType.CONTROLLER) {
        row.put("inputTags", tags(incoming(model, element)));
        row.put("outputTags", connectedTags(model, element));
        row.put("configuration", configuration(element));
        loops.add(row);
      } else if (element.getType() == PidElementType.SAFETY_FUNCTION || element.getType() == PidElementType.TRIP) {
        row.put("causeTags", tags(incoming(model, element)));
        row.put("effectTags", connectedTags(model, element));
        row.put("nativeActionPolicy",
            element.getAttributes().containsKey("simulationDriverTripConfiguration")
                ? element.getAttributes().get("simulationDriverTripConfiguration")
                : element.getAttributes().get("simulationFinalElementActionConfiguration"));
        row.put("canonicalFunctionPolicy", element.getAttributes());
        if ("NATIVE_COMPRESSOR_STOP_COMMAND".equals(element.getAttributes().get("simulationFinalElementRole"))) {
          Map<String, Object> output = new LinkedHashMap<String, Object>(row);
          output.put("ioDirection", "OUTPUT");
          output.put("signalKind", "NATIVE_COMPRESSOR_STOP_COMMAND");
          output.put("physicalDriverHardwareAdded", Boolean.FALSE);
          output.put("fieldAddress", "UNASSIGNED");
          io.add(output);
        }
        row.put("resetAndRestart", "Native reset/rearm is not a restart permissive; controlled narrative required");
        row.put("safetyCredit", "NO_SIL_OR_INSTALLED_INDEPENDENCE_INFERRED");
        effects.add(row);
      } else if (!bindings.getBoundPidTags().contains(element.getTag())) {
        row.put("reason", "UNBOUND_PROPOSAL_NOT_IMPLEMENTED_HARDWARE_OR_TESTED_PROTECTION");
        requirements.add(row);
      }
    }
    result.put("schemaVersion", "neqsim.automation_engineering_registers.v1");
    result.put("ioList", io);
    result.put("controlLoops", loops);
    result.put("causeAndEffect", effects);
    result.put("unresolvedRequirements", requirements);
    result.put("approval", "NOT_A_PLC_APPLICATION_OR_APPROVED_SRS");
    return result;
  }

  /** Immutable configuration evidence without live source-object references. */
  public static final class ConfiguredControlSystem {
    private final String json;
    private final String fingerprint;

    /**
     * Freezes one actual configuration.
     *
     * @param values validated policy and identities
     */
    private ConfiguredControlSystem(Map<String, Object> values) {
      json = GSON.toJson(values);
      fingerprint = sha256(json);
    }

    /** @return exact configuration SHA-256 */
    public String getFingerprint() {
      return fingerprint;
    }

    /** @return native scenario evidence reference */
    public String getEvidenceReference() {
      return "pid-native-control-sha256:" + fingerprint;
    }

    /** @return detached configuration evidence */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
      Map<String, Object> result = GSON.fromJson(json, Map.class);
      result.put("configurationFingerprint", fingerprint);
      return result;
    }

    /**
     * Rejects execution against a different configured native copy.
     *
     * @param actual fresh actual readback
     * @throws IllegalStateException if the actual configuration changed
     */
    public void assertSameConfiguration(ConfiguredControlSystem actual) {
      if (actual == null || !fingerprint.equals(actual.fingerprint)) {
        throw new IllegalStateException("Actual native control system differs from the frozen configuration");
      }
    }
  }

  /**
   * Creates an explicitly unapproved actual-object proposal.
   *
   * @param tag actual object tag
   * @param type supported category
   * @return review-required proposal
   */
  private static PidElement proposal(String tag, PidElementType type) {
    return new PidElement("native-control:" + tag, tag, type).status(PidProposalStatus.REVIEW_REQUIRED)
        .provenance("ACTUAL-NATIVE-CONTROL-IMPORT-v1",
            "Actual source-backed control objects; engineering and safety review required")
        .attribute("proposalOrigin", "ACTUAL_NATIVE_CONTROL_OBJECT").attribute("fitnessForConstruction", Boolean.FALSE);
  }

  /**
   * Enumerates actual attached and registered controllers once by identity.
   *
   * @param process actual source
   * @return ordered distinct controller objects
   */
  private static List<ControllerDeviceInterface> controllers(ProcessSystem process) {
    List<ControllerDeviceInterface> result = new ArrayList<ControllerDeviceInterface>();
    Set<ControllerDeviceInterface> seen = Collections
        .newSetFromMap(new IdentityHashMap<ControllerDeviceInterface, Boolean>());
    for (ControllerDeviceInterface controller : process.getControllerDevices()) {
      if (seen.add(controller)) {
        result.add(controller);
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      for (ControllerDeviceInterface controller : unit.getControllers()) {
        if (seen.add(controller)) {
          result.add(controller);
        }
      }
    }
    return result;
  }

  /**
   * Resolves one actual native target, including the explicit anti-surge recycle reference.
   *
   * @param process actual configured process
   * @param controller actual controller
   * @return actual registered actuator
   */
  private static ProcessEquipmentInterface target(ProcessSystem process, ControllerDeviceInterface controller) {
    ProcessEquipmentInterface found = null;
    if (controller instanceof AntiSurgeController) {
      found = ((AntiSurgeController) controller).getRecycleValve();
      requireRegistered(process, found);
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit.getControllers().contains(controller)
          && (unit instanceof ThrottlingValve || unit.getClass() == Compressor.class
              || unit.getClass() == Cooler.class && ((Cooler) unit).isDynamicTemperatureControlEnabled())) {
        if (found != null && found != unit) {
          throw new IllegalArgumentException("Controller has conflicting actual actuator targets");
        }
        found = unit;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException(
          "Supported native control requires an actual registered valve or compressor target");
    }
    return found;
  }

  /**
   * Tests the bounded actual native compressor-speed command function.
   *
   * @param element canonical function
   * @return whether this function governs a native compressor speed actuator
   */
  private static boolean isCompressorSpeed(PidElement element) {
    return element.getType() == PidElementType.SIGNAL
        && "COMPRESSOR_SPEED".equals(element.getAttributes().get("nativeActuatorRole"));
  }

  /**
   * Resolves a speed command to its exact registered compressor without creating driver hardware.
   *
   * @param process actual isolated process
   * @param element canonical command function
   * @return actual compressor
   */
  private static Compressor speedTarget(ProcessSystem process, PidElement element) {
    String tag = String.valueOf(element.getAttributes().get("simulationTargetEquipmentTag"));
    ProcessEquipmentInterface target = process.getUnit(tag);
    if (!isCompressorSpeed(element) || target == null || target.getClass() != Compressor.class
        || !tag.equals(element.getEquipmentTag())) {
      throw new IllegalArgumentException("Speed command requires its exact actual native compressor target");
    }
    return (Compressor) target;
  }

  /**
   * Captures static native RPM bounds and speed-actuator policy, separate from valve percent opening.
   *
   * @param compressor actual target
   * @return native speed policy
   */
  private static Map<String, Object> compressorSpeedConfiguration(Compressor compressor) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("schemaVersion", "neqsim.compressor_speed_configuration.v1");
    values.put("nativeClass", compressor.getClass().getName());
    values.put("targetTag", compressor.getName());
    values.put("commandUnit", "RPM");
    values.put("minimumSpeedRpm", Double.valueOf(compressor.getMinimumSpeed()));
    values.put("maximumSpeedRpm", Double.valueOf(compressor.getMaximumSpeed()));
    values.put("speedRateLimitEnabled", Boolean.valueOf(compressor.isControllerSpeedRateLimitEnabled()));
    values.put("maximumAccelerationRpmPerSecond", Double.valueOf(compressor.getMaxAccelerationRate()));
    values.put("maximumDecelerationRpmPerSecond", Double.valueOf(compressor.getMaxDecelerationRate()));
    values.put("controllerTag", compressor.getController() == null ? "" : compressor.getController().getName());
    values.put("transientCalculationMode", compressor.getTransientCalculationMode().name());
    return values;
  }

  /**
   * Applies complete native speed policy while retaining the actual controller, compressor and gas path.
   *
   * @param compressor actual isolated target
   * @param values governed complete policy
   */
  private static void applyCompressorSpeed(Compressor compressor, Map<String, Object> values) {
    Map<String, Object> current = compressorSpeedConfiguration(compressor);
    if (!current.keySet().equals(values.keySet())) {
      throw new IllegalArgumentException("Complete native compressor speed configuration is required");
    }
    for (String key : new String[] {"schemaVersion", "nativeClass", "targetTag", "commandUnit", "controllerTag",
        "transientCalculationMode"}) {
      if (!current.get(key).equals(values.get(key))) {
        throw new IllegalArgumentException("Canonical speed policy cannot rewire its native target: " + key);
      }
    }
    double minimum = number(values, "minimumSpeedRpm");
    double maximum = number(values, "maximumSpeedRpm");
    double acceleration = number(values, "maximumAccelerationRpmPerSecond");
    double deceleration = number(values, "maximumDecelerationRpmPerSecond");
    if (minimum < 0.0 || maximum <= minimum || acceleration < 0.0 || deceleration < 0.0
        || !(values.get("speedRateLimitEnabled") instanceof Boolean)) {
      throw new IllegalArgumentException("Native speed limits/rates require a valid finite RPM envelope");
    }
    compressor.setMinimumSpeed(minimum);
    compressor.setMaximumSpeed(maximum);
    compressor.setMaxAccelerationRate(acceleration);
    compressor.setMaxDecelerationRate(deceleration);
    compressor.setControllerSpeedRateLimitEnabled(Boolean.TRUE.equals(values.get("speedRateLimitEnabled")));
  }

  /**
   * Tests a source-backed embedded cooler utility command without adding a material utility circuit.
   *
   * @param element canonical function
   * @return true for the bounded native utility actuator role
   */
  private static boolean isCoolerUtility(PidElement element) {
    return element.getType() == PidElementType.SIGNAL
        && "COOLER_UTILITY_OPENING".equals(element.getAttributes().get("nativeActuatorRole"));
  }

  /**
   * Resolves the exact actual cooler owning the embedded utility model.
   *
   * @param process actual source or isolated process
   * @param element canonical utility function
   * @return native cooler
   */
  private static Cooler coolerTarget(ProcessSystem process, PidElement element) {
    String tag = String.valueOf(element.getAttributes().get("simulationTargetEquipmentTag"));
    ProcessEquipmentInterface target = process.getUnit(tag);
    if (!isCoolerUtility(element) || target == null || target.getClass() != Cooler.class
        || !tag.equals(element.getEquipmentTag()) || !((Cooler) target).isDynamicTemperatureControlEnabled()) {
      throw new IllegalArgumentException("Utility command requires its exact configured native cooler");
    }
    return (Cooler) target;
  }

  /**
   * Reads complete native thermal/utility policy and the actual controller identity.
   *
   * @param cooler exact native cooler
   * @return static source policy, excluding dynamic position and temperature state
   */
  private static Map<String, Object> coolerUtilityConfiguration(Cooler cooler) {
    Map<String, Object> values = new LinkedHashMap<String, Object>(cooler.getDynamicTemperatureControlConfiguration());
    values.put("nativeClass", cooler.getClass().getName());
    values.put("calculateSteadyState", Boolean.valueOf(cooler.getCalculateSteadyState()));
    values.put("targetTag", cooler.getName());
    values.put("controllerTag", cooler.getController() == null ? "" : cooler.getController().getName());
    values.put("commandUnit", "%");
    return values;
  }

  /**
   * Applies governed embedded utility policy through the cooler's validated native API.
   *
   * @param cooler actual isolated cooler
   * @param values complete canonical policy
   */
  private static void applyCoolerUtility(Cooler cooler, Map<String, Object> values) {
    Map<String, Object> current = coolerUtilityConfiguration(cooler);
    if (!current.keySet().equals(values.keySet())) {
      throw new IllegalArgumentException("Complete native cooler utility configuration is required");
    }
    Map<String, Object> thermal = new LinkedHashMap<String, Object>(values);
    if (!Boolean.FALSE.equals(thermal.remove("calculateSteadyState"))) {
      throw new IllegalArgumentException("Canonical cooler utility control requires native transient dispatch");
    }
    for (String key : new String[] {"nativeClass", "targetTag", "controllerTag", "commandUnit"}) {
      if (!current.get(key).equals(thermal.remove(key))) {
        throw new IllegalArgumentException("Canonical cooler policy cannot rewire its source: " + key);
      }
    }
    cooler.applyDynamicTemperatureControlConfiguration(thermal);
    cooler.setCalculateSteadyState(false);
  }

  /**
   * Resolves the actual causal measurement, never a similarly named proposed input.
   *
   * @param controller actual controller
   * @return actual input or null for the disclosed legacy direct-map policy
   */
  private static MeasurementDeviceInterface input(ControllerDeviceInterface controller) {
    return controller instanceof AntiSurgeController ? ((AntiSurgeController) controller).getSurgeMarginMeasurement()
        : controller.getTransmitter();
  }

  /**
   * Restricts readback/application to concrete native policies whose complete settings are exposed.
   *
   * @param controller actual controller
   * @return supported native object
   */
  private static ControllerDeviceBaseClass supportedController(ControllerDeviceInterface controller) {
    if (controller == null || controller.getClass() != ControllerDeviceBaseClass.class
        && controller.getClass() != AntiSurgeController.class) {
      throw new IllegalArgumentException("A supported concrete native PID or anti-surge controller is required");
    }
    return (ControllerDeviceBaseClass) controller;
  }

  /**
   * Captures actual instrument policy without reading or advancing its signal.
   *
   * @param measurement actual device
   * @return detached supported configuration
   */
  private static Map<String, Object> sensorConfiguration(MeasurementDeviceInterface measurement) {
    if (!(measurement instanceof MeasurementDeviceBaseClass)) {
      throw new IllegalArgumentException("Native signal-policy readback is required");
    }
    MeasurementDeviceBaseClass sensor = (MeasurementDeviceBaseClass) measurement;
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("nativeClass", measurement.getClass().getName());
    if (measurement instanceof CompressorSurgeMarginMeasurement) {
      CompressorSurgeMarginMeasurement margin = (CompressorSurgeMarginMeasurement) measurement;
      values.put("calculatedCompressorTag", margin.getCompressor().getName());
      values.put("causalFlowInputTag", margin.getFlowTransmitter().getName());
    }
    values.put("unit", measurement.getUnit());
    values.put("minimum", Double.valueOf(measurement.getMinimumValue()));
    values.put("maximum", Double.valueOf(measurement.getMaximumValue()));
    values.put("noiseStdDev", Double.valueOf(sensor.getNoiseStdDev()));
    values.put("filterSeconds", Double.valueOf(sensor.getFirstOrderTimeConstant()));
    values.put("delaySteps", Integer.valueOf(sensor.getDelaySteps()));
    values.put("faultType", sensor.getFaultType().name());
    values.put("faultParameter", Double.valueOf(sensor.getFaultParameter()));
    values.put("onlineSignal", Boolean.valueOf(sensor.isOnlineSignal()));
    values.put("sampleAndHold", Boolean.valueOf(measurement instanceof TransientMeasurementDevice
        && ((TransientMeasurementDevice) measurement).isTransientSamplingEnabled()));
    return values;
  }

  /**
   * Applies complete declared supported instrument settings before execution.
   *
   * @param measurement actual target
   * @param values complete canonical signal policy
   */
  private static void applySensor(MeasurementDeviceInterface measurement, Map<String, Object> values) {
    sameKeys(sensorConfiguration(measurement), values);
    if (!measurement.getClass().getName().equals(values.get("nativeClass"))
        || Boolean.TRUE.equals(values.get("onlineSignal"))) {
      throw new IllegalArgumentException("Instrument class changed or external online state is unsupported");
    }
    MeasurementDeviceBaseClass sensor = (MeasurementDeviceBaseClass) measurement;
    measurement.setUnit(String.valueOf(values.get("unit")));
    measurement.setMinimumValue(number(values, "minimum"));
    measurement.setMaximumValue(number(values, "maximum"));
    sensor.setNoiseStdDev(number(values, "noiseStdDev"));
    sensor.setFirstOrderTimeConstant(number(values, "filterSeconds"));
    sensor.setDelaySteps((int) number(values, "delaySteps"));
    sensor.setFault(SensorFaultType.valueOf(String.valueOf(values.get("faultType"))), number(values, "faultParameter"));
    if (measurement instanceof TransientMeasurementDevice) {
      ((TransientMeasurementDevice) measurement)
          .setTransientSamplingEnabled(Boolean.TRUE.equals(values.get("sampleAndHold")));
    } else if (Boolean.TRUE.equals(values.get("sampleAndHold"))) {
      throw new IllegalArgumentException("Instrument cannot implement held physical-step sampling");
    }
  }

  /**
   * Captures valve hydraulics, actuator policy and actual controller identities.
   *
   * @param valve actual native valve
   * @return detached configuration, excluding current position and travel progress
   */
  private static Map<String, Object> valveConfiguration(ThrottlingValve valve) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("nativeClass", valve.getClass().getName());
    values.put("cvUs", Double.valueOf(valve.getCv()));
    values.put("gasValve", Boolean.valueOf(valve.isGasValve()));
    values.put("allowChoked", Boolean.valueOf(valve.isAllowChoked()));
    values.put("allowLaminar", Boolean.valueOf(valve.isAllowLaminar()));
    values.put("isoThermal", Boolean.valueOf(valve.isIsoThermal()));
    values.put("acceptNegativeDp", Boolean.valueOf(valve.isAcceptNegativeDP()));
    values.put("pipingGeometryFactor", Double.valueOf(valve.getFp()));
    values.put("foulingFraction", Double.valueOf(valve.getFoulingFraction()));
    values.put("sizingClass", valve.getMechanicalDesign().getValveSizingMethod().getClass().getName());
    values.put("characterizationClass",
        valve.getMechanicalDesign().getValveCharacterizationMethod().getClass().getName());
    values.put("xT", Double.valueOf(valve.getMechanicalDesign().getValveSizingMethod().getxT()));
    values.put("minimumOpeningPercent", Double.valueOf(valve.getMinimumValveOpening()));
    values.put("maximumOpeningPercent", Double.valueOf(valve.getMaximumValveOpening()));
    values.put("deadbandPercent", Double.valueOf(valve.getValveDeadband()));
    values.put("stictionPercent", Double.valueOf(valve.getValveStiction()));
    values.put("hysteresisPercent", Double.valueOf(valve.getValveHysteresis()));
    values.put("travelModel", valve.getTravelModel().name());
    values.put("travelSeconds", Double.valueOf(valve.getTravelTime()));
    values.put("openingTravelSeconds", Double.valueOf(valve.getOpeningTravelTime()));
    values.put("closingTravelSeconds", Double.valueOf(valve.getClosingTravelTime()));
    values.put("timeConstantSeconds", Double.valueOf(valve.getTravelTimeConstant()));
    values.put("physicalStepIdempotency", Boolean.valueOf(valve.isPhysicalStepIdempotencyEnabled()));
    values.put("calculateSteadyState", Boolean.valueOf(valve.getCalculateSteadyState()));
    values.put("controller", valve.getController() == null ? "" : valve.getController().getName());
    if (valve instanceof ESDValve) {
      values.put("failSafePositionPercent", Double.valueOf(((ESDValve) valve).getFailSafePosition()));
      values.put("strokeSeconds", Double.valueOf(((ESDValve) valve).getStrokeTime()));
      values.put("actuatorAvailable", Boolean.valueOf(((ESDValve) valve).isActuatorAvailable()));
    }
    return values;
  }

  /**
   * Applies declared native valve hydraulics and motion policy without teleporting position.
   *
   * @param valve actual configured-copy valve
   * @param values complete canonical valve policy
   */
  private static void applyValve(ThrottlingValve valve, Map<String, Object> values) {
    sameKeys(valveConfiguration(valve), values);
    if (!valve.getClass().getName().equals(values.get("nativeClass"))
        || !(valve.getController() == null ? "" : valve.getController().getName()).equals(values.get("controller"))) {
      throw new IllegalArgumentException("Valve class or actual controller identity differs");
    }
    valve.setCv(number(values, "cvUs"));
    valve.setGasValve(Boolean.TRUE.equals(values.get("gasValve")));
    if (!valve.getMechanicalDesign().getValveSizingMethod().getClass().getName().equals(values.get("sizingClass"))
        || !valve.getMechanicalDesign().getValveCharacterizationMethod().getClass().getName()
            .equals(values.get("characterizationClass"))) {
      throw new IllegalArgumentException("Valve sizing/trim method changes require a supported model revision");
    }
    valve.setAllowChoked(Boolean.TRUE.equals(values.get("allowChoked")));
    valve.setAllowLaminar(Boolean.TRUE.equals(values.get("allowLaminar")));
    valve.setIsoThermal(Boolean.TRUE.equals(values.get("isoThermal")));
    valve.setAcceptNegativeDP(Boolean.TRUE.equals(values.get("acceptNegativeDp")));
    valve.setFp(number(values, "pipingGeometryFactor"));
    valve.setFoulingFraction(number(values, "foulingFraction"));
    valve.getMechanicalDesign().getValveSizingMethod().setxT(number(values, "xT"));
    valve.setMinimumValveOpening(number(values, "minimumOpeningPercent"));
    valve.setMaximumValveOpening(number(values, "maximumOpeningPercent"));
    valve.setValveDeadband(number(values, "deadbandPercent"));
    valve.setValveStiction(number(values, "stictionPercent"));
    valve.setValveHysteresis(number(values, "hysteresisPercent"));
    valve.setTravelModel(ValveTravelModel.valueOf(String.valueOf(values.get("travelModel"))));
    valve.setTravelTime(number(values, "travelSeconds"));
    valve.setOpeningTravelTime(number(values, "openingTravelSeconds"));
    valve.setClosingTravelTime(number(values, "closingTravelSeconds"));
    valve.setTravelTimeConstant(number(values, "timeConstantSeconds"));
    valve.setPhysicalStepIdempotencyEnabled(Boolean.TRUE.equals(values.get("physicalStepIdempotency")));
    valve.setCalculateSteadyState(Boolean.TRUE.equals(values.get("calculateSteadyState")));
    if (valve instanceof ESDValve) {
      ((ESDValve) valve).setFailSafePosition(number(values, "failSafePositionPercent"));
      ((ESDValve) valve).setStrokeTime(number(values, "strokeSeconds"));
      ((ESDValve) valve).setActuatorAvailable(Boolean.TRUE.equals(values.get("actuatorAvailable")));
    }
  }

  /**
   * Resolves one stream's unique source owner, falling back to its sole actual consumer at a battery limit.
   *
   * @param process source process
   * @param stream actual measured stream
   * @return actual equipment owner
   */
  private static ProcessEquipmentInterface owner(ProcessSystem process, StreamInterface stream) {
    List<ProcessEquipmentInterface> producers = new ArrayList<ProcessEquipmentInterface>();
    List<ProcessEquipmentInterface> consumers = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface)) {
        if (unit.getOutletStreams().contains(stream)) {
          producers.add(unit);
        }
        if (unit.getInletStreams().contains(stream)) {
          consumers.add(unit);
        }
      }
    }
    if (producers.size() == 1) {
      return producers.get(0);
    }
    if (producers.isEmpty() && consumers.size() == 1) {
      return consumers.get(0);
    }
    throw new IllegalArgumentException("Measurement requires a unique actual source port");
  }

  /**
   * Selects the nearest actual process unit for an inline valve, traversing only native piping junctions.
   *
   * @param process actual source process
   * @param valve actual inline device
   * @return upstream primary unit, or the receiving primary at a source battery limit
   */
  private static ProcessEquipmentInterface valveDrawingOwner(ProcessSystem process, ThrottlingValve valve) {
    Set<ProcessEquipmentInterface> visited = Collections
        .newSetFromMap(new IdentityHashMap<ProcessEquipmentInterface, Boolean>());
    ProcessEquipmentInterface found = drawingOwner(process, valve, true, visited);
    if (found == null) {
      visited.clear();
      found = drawingOwner(process, valve, false, visited);
    }
    return found == null ? valve : found;
  }

  /**
   * Traverses exact stream-object connections without inferring ownership from tag spelling.
   *
   * @param process source process
   * @param unit current actual device
   * @param upstream traversal direction
   * @param visited cycle guard
   * @return nearest supported process owner or null
   */
  private static ProcessEquipmentInterface drawingOwner(ProcessSystem process, ProcessEquipmentInterface unit,
      boolean upstream, Set<ProcessEquipmentInterface> visited) {
    if (!visited.add(unit)) {
      return null;
    }
    for (StreamInterface port : upstream ? unit.getInletStreams() : unit.getOutletStreams()) {
      for (ProcessEquipmentInterface candidate : process.getUnitOperations()) {
        if (candidate == unit || candidate instanceof StreamInterface
            || !(upstream ? candidate.getOutletStreams() : candidate.getInletStreams()).contains(port)) {
          continue;
        }
        boolean junction = candidate.getClass() == neqsim.process.equipment.mixer.Mixer.class
            || candidate.getClass() == neqsim.process.equipment.splitter.Splitter.class;
        if (!(candidate instanceof ThrottlingValve) && !junction) {
          return candidate;
        }
        ProcessEquipmentInterface found = drawingOwner(process, candidate, upstream, visited);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Chooses a registered owner for a source-backed local manual input without asserting process sensing.
   *
   * @param process source
   * @return first actual process unit
   */
  private static ProcessEquipmentInterface firstOwner(ProcessSystem process) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof neqsim.process.equipment.compressor.Compressor) {
        return unit;
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && !(unit instanceof ThrottlingValve)) {
        return unit;
      }
    }
    throw new IllegalArgumentException("Manual input requires actual process equipment");
  }

  /**
   * Requires exact actual source membership.
   *
   * @param process source
   * @param unit expected native object
   */
  private static void requireRegistered(ProcessSystem process, ProcessEquipmentInterface unit) {
    if (unit == null || process.getUnit(unit.getName()) != unit) {
      throw new IllegalArgumentException("Actual target is not registered in this process copy");
    }
  }

  /**
   * Requires a registered measurement by object identity.
   *
   * @param process source
   * @param measurement actual expected input
   */
  private static void requireMeasurement(ProcessSystem process, MeasurementDeviceInterface measurement) {
    for (MeasurementDeviceInterface candidate : process.getMeasurementDevices()) {
      if (candidate == measurement) {
        return;
      }
    }
    throw new IllegalArgumentException("Actual causal measurement is not registered in this process copy");
  }

  /**
   * Rejects any caller that tries to configure source equipment or shallow aliases.
   *
   * @param project governed source
   * @param process caller's isolated copy
   */
  private static void requireIsolated(EngineeringProject project, ProcessSystem process) {
    if (project == null || process == null || process == project.getEngineeringProcessSystem()) {
      throw new IllegalArgumentException("An isolated process copy is required");
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (project.getEngineeringProcessSystem().getUnit(unit.getName()) == unit) {
        throw new IllegalArgumentException("Configured process aliases source equipment");
      }
    }
  }

  /**
   * Returns one declared canonical configuration as detached JSON values.
   *
   * @param element governed element
   * @return static configuration
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> configuration(PidElement element) {
    Object value = element.getAttributes().get(CONFIGURATION);
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Canonical element lacks native configuration: " + element.getTag());
    }
    return GSON.fromJson(GSON.toJson(value), Map.class);
  }

  /**
   * Requires the exact supported key set.
   *
   * @param actual actual native configuration
   * @param expected declared canonical configuration
   */
  private static void sameKeys(Map<String, Object> actual, Map<String, Object> expected) {
    if (expected == null || !actual.keySet().equals(expected.keySet())) {
      throw new IllegalArgumentException("Complete supported native configuration keys are required");
    }
  }

  /**
   * Reads one required finite scalar.
   *
   * @param values canonical configuration
   * @param key scalar name
   * @return finite value
   */
  private static double number(Map<String, Object> values, String key) {
    Object value = values.get(key);
    if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
      throw new IllegalArgumentException("Finite declared setting required: " + key);
    }
    return ((Number) value).doubleValue();
  }

  /**
   * Requires one actual canonical actuator link.
   *
   * @param element controller function
   * @return target element ID
   */
  private static String onlyConnection(PidElement element) {
    if (element.getConnectedElementIds().size() != 1) {
      throw new IllegalArgumentException("Native controller requires exactly one actual actuator link");
    }
    return element.getConnectedElementIds().get(0);
  }

  /**
   * Finds direct canonical sources.
   *
   * @param model graph
   * @param element target
   * @return ordered incoming elements
   */
  private static List<PidElement> incoming(PidDesignModel model, PidElement element) {
    List<PidElement> result = new ArrayList<PidElement>();
    for (PidElement candidate : model.getElements()) {
      if (candidate.getConnectedElementIds().contains(element.getId())) {
        result.add(candidate);
      }
    }
    return result;
  }

  /**
   * Returns tags for structured registers.
   *
   * @param elements source elements
   * @return ordered tags
   */
  private static List<String> tags(List<PidElement> elements) {
    List<String> result = new ArrayList<String>();
    for (PidElement element : elements) {
      result.add(element.getTag());
    }
    return result;
  }

  /**
   * Returns declared output tags.
   *
   * @param model graph
   * @param element source
   * @return ordered tags
   */
  private static List<String> connectedTags(PidDesignModel model, PidElement element) {
    List<String> result = new ArrayList<String>();
    for (String id : element.getConnectedElementIds()) {
      result.add(byId(model, id).getTag());
    }
    return result;
  }

  /**
   * Finds a unique canonical tag.
   *
   * @param model graph
   * @param tag exact tag
   * @return matching proposal
   */
  private static PidElement byTag(PidDesignModel model, String tag) {
    for (PidElement element : model.getElements()) {
      if (tag.equals(element.getTag())) {
        return element;
      }
    }
    throw new IllegalArgumentException("Missing canonical tag: " + tag);
  }

  /**
   * Finds a canonical identity.
   *
   * @param model graph
   * @param id exact identity
   * @return matching proposal
   */
  private static PidElement byId(PidDesignModel model, String id) {
    for (PidElement element : model.getElements()) {
      if (id.equals(element.getId())) {
        return element;
      }
    }
    throw new IllegalArgumentException("Missing canonical identity: " + id);
  }

  /**
   * Computes stable SHA-256 evidence identity.
   *
   * @param value exact JSON
   * @return lowercase hexadecimal digest
   */
  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : digest) {
        result.append(String.format(java.util.Locale.ROOT, "%02x", Integer.valueOf(item & 255)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Required SHA-256 algorithm is unavailable", ex);
    }
  }
}
