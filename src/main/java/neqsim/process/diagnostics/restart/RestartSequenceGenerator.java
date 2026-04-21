package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.Objects;
import neqsim.process.diagnostics.RootCauseReport;
import neqsim.process.diagnostics.TripEvent;
import neqsim.process.diagnostics.TripType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates a restart sequence based on the trip type, process state, and root cause analysis.
 *
 * <p>
 * Creates an ordered sequence of {@link RestartStep} actions tailored to the specific trip type.
 * For example, a compressor surge trip generates a different sequence than a high-level separator
 * trip.
 * </p>
 *
 * <p>
 * The generated sequence can be optimised by the {@link RestartOptimiser} to minimise MTTR.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RestartSequenceGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;

  /** Default compressor speed ramp rate in rpm per second. */
  private double compressorRampRate = 50.0;

  /** Default valve opening ramp rate in percent per second. */
  private double valveRampRate = 2.0;

  /** Default settling time after major changes (seconds). */
  private double settlingTime = 60.0;

  /**
   * Constructs a sequence generator for a process system.
   *
   * @param processSystem the process system to restart
   */
  public RestartSequenceGenerator(ProcessSystem processSystem) {
    this.processSystem = Objects.requireNonNull(processSystem, "processSystem must not be null");
  }

  /**
   * Sets the default compressor ramp rate.
   *
   * @param rampRate ramp rate in rpm/s
   */
  public void setCompressorRampRate(double rampRate) {
    this.compressorRampRate = rampRate;
  }

  /**
   * Sets the default valve ramp rate.
   *
   * @param rampRate ramp rate in %/s
   */
  public void setValveRampRate(double rampRate) {
    this.valveRampRate = rampRate;
  }

  /**
   * Sets the default settling time between major steps.
   *
   * @param seconds settling time in seconds
   */
  public void setSettlingTime(double seconds) {
    this.settlingTime = seconds;
  }

  /**
   * Generates a restart sequence based on the trip event and optional root cause report.
   *
   * @param tripEvent the trip event
   * @param rootCauseReport the root cause report (may be null)
   * @return a restart sequence
   */
  public RestartSequence generate(TripEvent tripEvent, RootCauseReport rootCauseReport) {
    Objects.requireNonNull(tripEvent, "tripEvent must not be null");

    TripType tripType = tripEvent.getTripType();
    RestartSequence sequence = new RestartSequence("Restart after " + tripType.getDisplayName());

    int stepNum = 1;

    // Step 1: Always start with verification
    sequence.addStep(new RestartStep(stepNum++, "Verify root cause has been addressed",
        RestartStep.ActionType.VERIFICATION, tripEvent.getInitiatingEquipment(),
        "rootCauseResolved", 1.0, "boolean"));

    // Trip-type-specific steps
    switch (tripType) {
      case COMPRESSOR_SURGE:
        stepNum = addCompressorSurgeRestartSteps(sequence, stepNum, tripEvent);
        break;
      case HIGH_PRESSURE:
        stepNum = addHighPressureRestartSteps(sequence, stepNum, tripEvent);
        break;
      case HIGH_LEVEL:
        stepNum = addHighLevelRestartSteps(sequence, stepNum, tripEvent);
        break;
      case HIGH_TEMPERATURE:
        stepNum = addHighTemperatureRestartSteps(sequence, stepNum, tripEvent);
        break;
      case LOW_FLOW:
        stepNum = addLowFlowRestartSteps(sequence, stepNum, tripEvent);
        break;
      case ESD_ACTIVATED:
        stepNum = addEsdRestartSteps(sequence, stepNum, tripEvent);
        break;
      default:
        stepNum = addGenericRestartSteps(sequence, stepNum, tripEvent);
        break;
    }

    // Final step: Confirm stable operation
    RestartStep confirm =
        new RestartStep(stepNum, "Confirm process is stable at normal operating conditions",
            RestartStep.ActionType.VERIFICATION, "", "stableOperation", 1.0, "boolean");
    confirm.setDurationSeconds(300.0); // 5 min monitoring
    sequence.addStep(confirm);

    return sequence;
  }

  /**
   * Adds compressor surge restart steps.
   *
   * @param seq the sequence to add steps to
   * @param stepNum current step number
   * @param tripEvent the trip event
   * @return the next step number
   */
  private int addCompressorSurgeRestartSteps(RestartSequence seq, int stepNum,
      TripEvent tripEvent) {
    String compName = tripEvent.getInitiatingEquipment();

    // Open anti-surge valve fully
    RestartStep openAsv = new RestartStep(stepNum++, "Open anti-surge valve fully",
        RestartStep.ActionType.VALVE_ACTION, compName + " ASV", "valveOpening", 100.0, "%");
    openAsv.setRampRate(valveRampRate * 5); // Open fast
    seq.addStep(openAsv);

    // Reset compressor trip
    seq.addStep(new RestartStep(stepNum++, "Reset compressor trip alarm and acknowledge",
        RestartStep.ActionType.OPERATOR_ACTION, compName, "tripReset", 1.0, "boolean"));

    // Wait for settle
    RestartStep settle = new RestartStep(stepNum++, "Wait for process to settle after trip",
        RestartStep.ActionType.WAIT_DURATION, "", "", 0.0, "s");
    settle.setDurationSeconds(settlingTime);
    seq.addStep(settle);

    // Start compressor
    seq.addStep(new RestartStep(stepNum++, "Start compressor at minimum speed",
        RestartStep.ActionType.COMPRESSOR_START, compName, "speed", 0.0, "rpm"));

    // Ramp to normal speed
    RestartStep ramp = new RestartStep(stepNum++, "Ramp compressor to normal operating speed",
        RestartStep.ActionType.COMPRESSOR_RAMP, compName, "speed", 100.0, "%");
    ramp.setRampRate(compressorRampRate);
    seq.addStep(ramp);

    // Close anti-surge valve gradually
    RestartStep closeAsv =
        new RestartStep(stepNum++, "Gradually close anti-surge valve as flow stabilises",
            RestartStep.ActionType.VALVE_ACTION, compName + " ASV", "valveOpening", 0.0, "%");
    closeAsv.setRampRate(valveRampRate);
    seq.addStep(closeAsv);

    return stepNum;
  }

  /**
   * Adds high pressure restart steps.
   *
   * @param seq the sequence
   * @param stepNum current step number
   * @param tripEvent the trip event
   * @return next step number
   */
  private int addHighPressureRestartSteps(RestartSequence seq, int stepNum, TripEvent tripEvent) {
    // Depressurise to safe level
    seq.addStep(new RestartStep(stepNum++, "Verify pressure has decreased to safe operating level",
        RestartStep.ActionType.VERIFICATION, tripEvent.getInitiatingEquipment(), "pressure", 0.0,
        "bara"));

    // Open inlet valve gradually
    RestartStep openInlet = new RestartStep(stepNum++, "Gradually open inlet valve",
        RestartStep.ActionType.VALVE_ACTION, tripEvent.getInitiatingEquipment() + " inlet",
        "valveOpening", 100.0, "%");
    openInlet.setRampRate(valveRampRate);
    seq.addStep(openInlet);

    // Wait for stabilisation
    RestartStep settle = new RestartStep(stepNum++, "Wait for pressure to stabilise",
        RestartStep.ActionType.WAIT_DURATION, "", "", 0.0, "s");
    settle.setDurationSeconds(settlingTime);
    seq.addStep(settle);

    return stepNum;
  }

  /**
   * Adds high level restart steps.
   *
   * @param seq the sequence
   * @param stepNum current step number
   * @param tripEvent the trip event
   * @return next step number
   */
  private int addHighLevelRestartSteps(RestartSequence seq, int stepNum, TripEvent tripEvent) {
    // Drain excess liquid
    seq.addStep(new RestartStep(stepNum++, "Open liquid outlet valve to drain excess liquid",
        RestartStep.ActionType.VALVE_ACTION, tripEvent.getInitiatingEquipment() + " LCV",
        "valveOpening", 100.0, "%"));

    // Wait for level to drop
    RestartStep waitLevel =
        new RestartStep(stepNum++, "Wait for separator level to reach normal operating range",
            RestartStep.ActionType.WAIT_CONDITION, tripEvent.getInitiatingEquipment(), "level",
            50.0, "%");
    waitLevel.setConditionExpression("level between 30% and 60%");
    seq.addStep(waitLevel);

    // Resume normal control
    seq.addStep(new RestartStep(stepNum++, "Resume automatic level control",
        RestartStep.ActionType.SETPOINT_CHANGE, tripEvent.getInitiatingEquipment() + " LC",
        "setpoint", 50.0, "%"));

    return stepNum;
  }

  /**
   * Adds high temperature restart steps.
   *
   * @param seq the sequence
   * @param stepNum current step number
   * @param tripEvent the trip event
   * @return next step number
   */
  private int addHighTemperatureRestartSteps(RestartSequence seq, int stepNum,
      TripEvent tripEvent) {
    // Wait for cooldown
    RestartStep cooldown =
        new RestartStep(stepNum++, "Wait for equipment temperature to decrease to safe level",
            RestartStep.ActionType.WAIT_CONDITION, tripEvent.getInitiatingEquipment(),
            "temperature", 0.0, "C");
    cooldown.setConditionExpression("temperature below high alarm setpoint");
    seq.addStep(cooldown);

    // Gradually increase flow
    RestartStep rampFlow = new RestartStep(stepNum++, "Gradually increase feed flow",
        RestartStep.ActionType.VALVE_ACTION, tripEvent.getInitiatingEquipment() + " inlet",
        "valveOpening", 100.0, "%");
    rampFlow.setRampRate(valveRampRate);
    seq.addStep(rampFlow);

    return stepNum;
  }

  /**
   * Adds low flow restart steps.
   *
   * @param seq the sequence
   * @param stepNum current step number
   * @param tripEvent the trip event
   * @return next step number
   */
  private int addLowFlowRestartSteps(RestartSequence seq, int stepNum, TripEvent tripEvent) {
    // Verify feed availability
    seq.addStep(new RestartStep(stepNum++, "Verify upstream feed is available and stable",
        RestartStep.ActionType.VERIFICATION, tripEvent.getInitiatingEquipment(), "feedAvailable",
        1.0, "boolean"));

    // Open inlet gradually
    RestartStep openInlet = new RestartStep(stepNum++,
        "Open inlet valve gradually to establish flow", RestartStep.ActionType.VALVE_ACTION,
        tripEvent.getInitiatingEquipment() + " inlet", "valveOpening", 50.0, "%");
    openInlet.setRampRate(valveRampRate);
    seq.addStep(openInlet);

    // Increase to normal
    RestartStep rampUp = new RestartStep(stepNum++, "Ramp inlet to normal operating flow",
        RestartStep.ActionType.VALVE_ACTION, tripEvent.getInitiatingEquipment() + " inlet",
        "valveOpening", 100.0, "%");
    rampUp.setRampRate(valveRampRate);
    rampUp.setDurationSeconds(settlingTime);
    seq.addStep(rampUp);

    return stepNum;
  }

  /**
   * Adds ESD restart steps (most conservative).
   *
   * @param seq the sequence
   * @param stepNum current step number
   * @param tripEvent the trip event
   * @return next step number
   */
  private int addEsdRestartSteps(RestartSequence seq, int stepNum, TripEvent tripEvent) {
    // ESD reset
    seq.addStep(
        new RestartStep(stepNum++, "Perform ESD cause investigation and get clearance to reset",
            RestartStep.ActionType.OPERATOR_ACTION, "ESD System", "investigation", 1.0, "boolean"));

    seq.addStep(new RestartStep(stepNum++, "Reset ESD system and verify all ESD valves",
        RestartStep.ActionType.OPERATOR_ACTION, "ESD System", "reset", 1.0, "boolean"));

    // Pressure test
    RestartStep pressTest =
        new RestartStep(stepNum++, "Perform leak test / pressure test where required",
            RestartStep.ActionType.OPERATOR_ACTION, "", "pressureTest", 1.0, "boolean");
    pressTest.setDurationSeconds(600.0); // 10 minutes
    seq.addStep(pressTest);

    // Restart separators
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Separator) {
        RestartStep fillSep =
            new RestartStep(stepNum++, "Fill separator " + eq.getName() + " to normal level",
                RestartStep.ActionType.WAIT_CONDITION, eq.getName(), "level", 50.0, "%");
        fillSep.setConditionExpression("level reaches 50%");
        seq.addStep(fillSep);
      }
    }

    // Restart compressors (last, after separation is stable)
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Compressor) {
        RestartStep startComp = new RestartStep(stepNum++, "Start compressor " + eq.getName(),
            RestartStep.ActionType.COMPRESSOR_START, eq.getName(), "speed", 0.0, "rpm");
        seq.addStep(startComp);

        RestartStep rampComp =
            new RestartStep(stepNum++, "Ramp compressor " + eq.getName() + " to normal speed",
                RestartStep.ActionType.COMPRESSOR_RAMP, eq.getName(), "speed", 100.0, "%");
        rampComp.setRampRate(compressorRampRate);
        seq.addStep(rampComp);
      }
    }

    return stepNum;
  }

  /**
   * Adds generic restart steps for unrecognised trip types.
   *
   * @param seq the sequence
   * @param stepNum current step number
   * @param tripEvent the trip event
   * @return next step number
   */
  private int addGenericRestartSteps(RestartSequence seq, int stepNum, TripEvent tripEvent) {
    // Generic: check, wait, restart gradually
    seq.addStep(new RestartStep(stepNum++, "Verify all safety interlocks are clear",
        RestartStep.ActionType.VERIFICATION, "", "safetyInterlocksClear", 1.0, "boolean"));

    RestartStep settle = new RestartStep(stepNum++, "Allow process to settle before restart",
        RestartStep.ActionType.WAIT_DURATION, "", "", 0.0, "s");
    settle.setDurationSeconds(settlingTime * 2);
    seq.addStep(settle);

    RestartStep gradualStart =
        new RestartStep(stepNum++, "Gradually bring process back to normal operating conditions",
            RestartStep.ActionType.OPERATOR_ACTION, tripEvent.getInitiatingEquipment(),
            "manualRestart", 1.0, "boolean");
    seq.addStep(gradualStart);

    return stepNum;
  }
}
