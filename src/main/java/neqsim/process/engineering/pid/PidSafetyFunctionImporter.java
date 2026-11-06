package neqsim.process.engineering.pid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;

/**
 * Proposes canonical P&amp;ID instruments, voting functions and actual final elements from a configured native SIF.
 *
 * <p>
 * The native model supplies executable settings; the caller supplies the engineering role and study policy. Existing
 * exact bindings are reused. Unsupported locations or conflicting governed settings fail instead of creating phantom
 * hardware. Rebuild simulation bindings after importing, then validate and test the proposal before retaining results.
 * No hazard analysis, SIL assignment, independence or engineering approval is inferred.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidSafetyFunctionImporter {
  /** Prevents utility construction. */
  private PidSafetyFunctionImporter() {
  }

  /**
   * Imports a healthy native function into the existing canonical proposal graph.
   *
   * @param project governed source project
   * @param sourceModel canonical graph to extend without mutating the input
   * @param bindings current bindings before this import
   * @param process configured isolated copy containing all actual source and target objects
   * @param function native function whose actual settings are proposed
   * @param role explicit ESD, HIPPS or MANUAL_ESD engineering role
   * @return copied canonical model containing the proposed native function and connections
   */
  public static PidDesignModel fromNativeFunction(EngineeringProject project, PidDesignModel sourceModel,
      PidSimulationBindings bindings, ProcessSystem process, ClosedLoopSafetyFunction function, String role) {
    if (function == null || process == null || bindings == null
        || !("ESD".equals(role) || "HIPPS".equals(role) || "MANUAL_ESD".equals(role))) {
      throw new IllegalArgumentException(
          "Actual configured function/process/bindings and an explicit role are required");
    }
    bindings.assertCurrent(project, sourceModel);
    if (!(function.getFinalElementLogic() instanceof ESDLogic)) {
      throw new IllegalArgumentException("Native final elements require a supported ESD trip policy");
    }
    Map<String, Object> actionPolicy = ((ESDLogic) function.getFinalElementLogic()).getTripActionConfiguration();
    if (!Boolean.TRUE.equals(actionPolicy.get("supportedTripPolicy"))) {
      throw new IllegalArgumentException("Native final-element action graph is not a supported trip policy");
    }
    if (((ESDLogic) function.getFinalElementLogic()).getTargetEquipmentCoverageIssue() != null) {
      throw new IllegalArgumentException("Native final-element action identities are incomplete or opaque");
    }
    PidDesignModel model = PidDesignModel.fromJson(new Gson().toJson(sourceModel.toMap()));
    if (findTag(model, function.getName()) != null) {
      throw new IllegalArgumentException("Canonical safety function already exists: " + function.getName());
    }
    Map<String, Object> evidence = function.getDynamicSafetyScenarioEvidence();
    List<?> channelValues = (List<?>) evidence.get("channelConfiguration");
    List<PidElement> targets = new ArrayList<PidElement>();
    List<PidElement> additions = new ArrayList<PidElement>();
    String defaultOwner = "";
    for (ProcessEquipmentInterface target : function.getTargetEquipment()) {
      if (!(target instanceof ESDValve)) {
        throw new IllegalArgumentException("Only actual native ESD-valve actuator targets are supported");
      }
      if (Math.abs(((ESDValve) target).getFailSafePosition()) > 1.0e-12) {
        throw new IllegalArgumentException(
            "The supported isolation proposal requires an actual fail-closed native valve");
      }
      PidElement element = boundElement(model, bindings, process, target.getName(), "EQUIPMENT", target);
      ProcessEquipmentInterface owner = downstreamOwner(process, target);
      if (defaultOwner.isEmpty()) {
        defaultOwner = owner.getName();
      }
      if (element == null) {
        element = proposed(target.getName(), PidElementType.SHUTDOWN_VALVE).equipment(owner.getName())
            .line(target.getOutletStreams().get(0).getName());
        additions.add(element);
      }
      governed(element, "simulationStrokeSeconds", Double.valueOf(((ESDValve) target).getStrokeTime()));
      governed(element, "simulationFailSafePosition", Double.valueOf(((ESDValve) target).getFailSafePosition()));
      targets.add(element);
    }
    if (targets.isEmpty()) {
      throw new IllegalArgumentException("Native function has no actual final element");
    }
    List<PidElement> inputs = new ArrayList<PidElement>();
    for (int index = 0; index < function.getChannels().size(); index++) {
      SafetyFunctionChannel channel = function.getChannels().get(index);
      MeasurementDeviceInterface source = channel.getMeasurementSource();
      Map<?, ?> values = (Map<?, ?>) channelValues.get(index);
      if (source == null || !"HEALTHY".equals(values.get("faultMode"))) {
        throw new IllegalArgumentException("Import requires actual healthy native measurement-backed channels");
      }
      PidElement element = boundElement(model, bindings, process, source.getName(), "MEASUREMENT", source);
      if (element == null) {
        element = proposed(source.getName(), PidElementType.MEASUREMENT);
        if (source.getClass() == PushButton.class && "MANUAL_ESD".equals(role)) {
          element.equipment(defaultOwner).attribute("inputKind", "MANUAL_PUSH_BUTTON");
        } else if (source instanceof StreamMeasurementDeviceBaseClass) {
          StreamInterface stream = ((StreamMeasurementDeviceBaseClass) source).getStream();
          element.equipment(streamOwner(process, stream).getName()).line(stream.getName());
        } else if (source instanceof LevelTransmitter) {
          element.equipment(((LevelTransmitter) source).getVessel().getName()).attribute("sensingLocation", "VESSEL");
        } else {
          throw new IllegalArgumentException("Native safety input has no supported actual drawing location");
        }
        additions.add(element);
      }
      governed(element, "simulationTripUnit", values.get("unit"));
      governed(element, "simulationTripSetpoint", values.get("setpoint"));
      governed(element, "simulationTripDirection", values.get("tripDirection"));
      governed(element, "simulationResponseDelaySeconds", values.get("responseDelaySeconds"));
      inputs.add(element);
    }
    if (inputs.isEmpty()) {
      throw new IllegalArgumentException("Native function has no actual measurement input");
    }
    PidElement imported = proposed(function.getName(), PidElementType.SAFETY_FUNCTION)
        .equipment(inputs.get(0).getEquipmentTag()).attribute("simulationSifId", function.getSifId())
        .attribute("safetyFunctionRole", role).attribute("simulationVoting", evidence.get("votingPattern"))
        .attribute("simulationLogicDelaySeconds", evidence.get("logicSolverDelaySeconds"))
        .attribute("simulationFinalElementActionConfiguration", actionPolicy)
        .attribute("nativeSourceFingerprint", bindings.toMap().get("sourceFingerprint"))
        .attribute("hardwareIndependence", "NOT_ESTABLISHED").attribute("silTargetInferred", Boolean.FALSE);
    for (PidElement target : targets) {
      imported.connect(target.getId());
    }
    for (PidElement element : additions) {
      model.add(element);
    }
    model.add(imported);
    for (PidElement input : inputs) {
      input.connect(imported.getId());
    }
    return model;
  }

  /**
   * Creates an explicitly unapproved proposal with stable native identity and bounded provenance.
   *
   * @param tag actual native object name
   * @param type canonical category
   * @return review-required proposal
   */
  private static PidElement proposed(String tag, PidElementType type) {
    return new PidElement("native-sif:" + tag, tag, type).status(PidProposalStatus.REVIEW_REQUIRED).provenance(
        "NATIVE-CLOSED-LOOP-SIF-IMPORT-v1",
        "Actual configured native objects and policy settings imported as an engineering proposal; no SIL or approval")
        .attribute("proposalOrigin", "ACTUAL_NATIVE_CLOSED_LOOP_FUNCTION")
        .attribute("qualificationStatus", "CONTROLLED_SAFETY_AND_STANDARDS_REVIEW_REQUIRED");
  }

  /**
   * Reuses one exact binding and rejects aliases or objects outside the configured copy.
   *
   * @param model canonical graph
   * @param bindings current bindings
   * @param process configured process
   * @param name actual native name
   * @param role native binding role
   * @param actual expected actual object
   * @return unique existing proposal or null
   */
  private static PidElement boundElement(PidDesignModel model, PidSimulationBindings bindings, ProcessSystem process,
      String name, String role, Object actual) {
    PidElement match = null;
    for (Object item : (List<?>) bindings.toMap().get("bindings")) {
      Map<?, ?> row = (Map<?, ?>) item;
      if (role.equals(row.get("role")) && name.equals(row.get("simulationName"))) {
        String tag = String.valueOf(row.get("pidTag"));
        Object resolved = "MEASUREMENT".equals(role) ? bindings.resolveMeasurement(tag, process)
            : bindings.resolveEquipment(tag, process);
        if (resolved != actual || match != null) {
          throw new IllegalArgumentException("Native import requires one exact canonical binding per actual object");
        }
        match = findTag(model, tag);
      }
    }
    if (match == null && findTag(model, name) != null) {
      throw new IllegalArgumentException("Existing native-name proposal lacks an exact reusable binding: " + name);
    }
    return match;
  }

  /**
   * Finds the actual receiving owner after a native inlet isolation valve.
   *
   * @param process configured process
   * @param valve actual target
   * @return unique actual downstream owner
   */
  private static ProcessEquipmentInterface downstreamOwner(ProcessSystem process, ProcessEquipmentInterface valve) {
    if (valve.getOutletStreams().size() != 1) {
      throw new IllegalArgumentException("Native target requires one actual outlet stream");
    }
    StreamInterface stream = valve.getOutletStreams().get(0);
    ProcessEquipmentInterface found = null;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit != valve && !(unit instanceof StreamInterface) && unit.getInletStreams().contains(stream)) {
        if (found != null) {
          throw new IllegalArgumentException("Native target has ambiguous downstream drawing ownership");
        }
        found = unit;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("Native target has no actual downstream drawing owner");
    }
    return found;
  }

  /**
   * Finds actual equipment exposing a measured process stream.
   *
   * @param process configured process
   * @param stream actual measured stream
   * @return unique source owner
   */
  private static ProcessEquipmentInterface streamOwner(ProcessSystem process, StreamInterface stream) {
    ProcessEquipmentInterface found = null;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && unit.getOutletStreams().contains(stream)) {
        if (found != null) {
          throw new IllegalArgumentException("Actual measurement stream has ambiguous equipment ownership");
        }
        found = unit;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("Actual measurement stream has no supported equipment source");
    }
    return found;
  }

  /**
   * Adds a native setting without overwriting a conflicting governed proposal.
   *
   * @param element existing or new proposal
   * @param key canonical setting
   * @param value actual native readback
   */
  private static void governed(PidElement element, String key, Object value) {
    Object previous = element.getAttributes().get(key);
    if (previous != null && !previous.equals(value)) {
      if (!(previous instanceof Number && value instanceof Number
          && Math.abs(((Number) previous).doubleValue() - ((Number) value).doubleValue()) <= 1.0e-12)) {
        throw new IllegalArgumentException("Conflicting governed safety setting " + key + " on " + element.getTag());
      }
    }
    element.attribute(key, value);
  }

  /**
   * Looks up an exact canonical tag.
   *
   * @param model canonical graph
   * @param tag exact tag
   * @return matching proposal or null
   */
  private static PidElement findTag(PidDesignModel model, String tag) {
    for (PidElement element : model.getElements()) {
      if (element.getTag().equals(tag)) {
        return element;
      }
    }
    return null;
  }
}
