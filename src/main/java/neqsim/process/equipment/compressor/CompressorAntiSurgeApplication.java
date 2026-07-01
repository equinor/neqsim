package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.ControlAlgorithm;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.DualRecycleValveCommand;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.FaultTolerantDecision;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.InstrumentSignal;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.RecycleSizingResult;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.SensorFault;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.VotingMode;

/**
 * Commercial-style compressor anti-surge application design and simulation layer.
 *
 * <p>
 * This class assembles the lower-level anti-surge utilities into a deterministic application model: multi-compressor
 * stage supervision, shared-header diagnostics, hot/cold recycle valve command splitting, startup/shutdown sequence
 * states, commissioning checks, scan-cycle execution, and operator-facing alarms and recommendations.
 * </p>
 *
 * <p>
 * The implementation is for engineering studies, dynamic simulation, operator-training examples, commissioning support,
 * and digital-twin advisory calculations. It is not a certified safety instrumented function, machinery protection
 * system, safety PLC application, or compressor vendor controller.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CompressorAntiSurgeApplication implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default scan period in seconds. */
  private static final double DEFAULT_SCAN_TIME = 0.25;

  /** Application operating mode. */
  public enum OperatingMode {
    /** Application is idle and does not update commands. */
    IDLE,
    /** Startup sequence is active. */
    STARTUP,
    /** Normal anti-surge control is active. */
    RUNNING,
    /** Planned shutdown sequence is active. */
    SHUTDOWN,
    /** Trip/coastdown mode with conservative recycle action. */
    TRIP,
    /** Commissioning and proof-test support mode. */
    COMMISSIONING
  }

  /** Startup/shutdown sequence state. */
  public enum SequenceState {
    /** No sequence is active. */
    IDLE,
    /** Check permissives before moving any equipment. */
    CHECK_PERMISSIVES,
    /** Pre-open recycle valves before speed/load increase. */
    RECYCLE_PREOPEN,
    /** Pressurize compressor train and recycle loop. */
    PRESSURIZE,
    /** Ramp compressor speed or load. */
    SPEED_RAMP,
    /** Accept process load while preserving surge margin. */
    LOAD_ACCEPT,
    /** Normal control is enabled. */
    NORMAL_CONTROL,
    /** Planned shutdown opens recycle and unloads the compressor. */
    SHUTDOWN_RECYCLE_OPEN,
    /** Compressor is coasting down after shutdown/trip. */
    COASTDOWN,
    /** Sequence is complete. */
    COMPLETE,
    /** Sequence failed because permissives or diagnostics were not acceptable. */
    FAILED
  }

  /** Application scan quality. */
  public enum ApplicationStatus {
    /** No issues detected. */
    HEALTHY,
    /** Application is usable but has degraded instrumentation or margin. */
    DEGRADED,
    /** Advisory alarm level. */
    ALARM,
    /** Trip-level condition for simulation/advisory studies. */
    TRIP_DEMAND
  }

  /** Certification status for this open simulation implementation. */
  public enum CertificationStatus {
    /** Simulation and application-design support only. */
    ENGINEERING_APPLICATION_DESIGN,
    /** Not certified as a vendor or SIL machinery-protection package. */
    NOT_CERTIFIED_FOR_PROTECTION
  }

  /** Commissioning check verdict. */
  public enum CheckStatus {
    /** Check passes. */
    PASS,
    /** Check raises a warning but does not block simulation. */
    WARN,
    /** Check fails and should be resolved before relying on the application. */
    FAIL
  }

  /** Application name. */
  private final String name;

  /** Stage configurations in insertion order. */
  private final List<StageApplication> stages = new ArrayList<StageApplication>();

  /** Header configurations keyed by header name. */
  private final Map<String, HeaderApplication> headers = new LinkedHashMap<String, HeaderApplication>();

  /** Shared anti-surge supervisory logic. */
  private final AdvancedAntiSurgeControlSystem supervisor = new AdvancedAntiSurgeControlSystem();

  /** Current operating mode. */
  private OperatingMode operatingMode = OperatingMode.IDLE;

  /** Current sequence state. */
  private SequenceState sequenceState = SequenceState.IDLE;

  /** Time spent in current sequence state. */
  private double stateTime = 0.0;

  /** Time since application start. */
  private double elapsedTime = 0.0;

  /** Last scan result. */
  private ScanResult lastScanResult = null;

  /** Low margin alarm limit. */
  private double alarmMargin = 0.05;

  /** Trip-demand margin limit for advisory/simulation purposes. */
  private double tripMargin = 0.0;

  /** Pre-open demand during startup and shutdown sequences. */
  private double sequencePreopenDemand = 30.0;

  /** Startup permissive timeout in seconds. */
  private double permissiveTimeout = 60.0;

  /**
   * Create an anti-surge application.
   *
   * @param name application name
   */
  public CompressorAntiSurgeApplication(String name) {
    this.name = name == null ? "anti-surge application" : name;
    supervisor.setAlgorithm(ControlAlgorithm.PREDICTIVE_PI);
  }

  /**
   * Add a compressor stage to the anti-surge application.
   *
   * @param stageName stage name
   * @return created stage configuration
   */
  public StageApplication addStage(String stageName) {
    StageApplication stage = new StageApplication(stageName == null ? "stage " + (stages.size() + 1) : stageName);
    stages.add(stage);
    return stage;
  }

  /**
   * Add a compressor stage backed by a NeqSim compressor object.
   *
   * @param stageName stage name
   * @param compressor compressor object; may be null for data-driven studies
   * @return created stage configuration
   */
  public StageApplication addStage(String stageName, Compressor compressor) {
    StageApplication stage = addStage(stageName);
    stage.setCompressor(compressor);
    return stage;
  }

  /**
   * Add a shared process header and connect stages to it by name.
   *
   * @param headerName header name
   * @param stageNames stage names connected to the header
   * @return header configuration
   */
  public HeaderApplication addHeader(String headerName, String... stageNames) {
    HeaderApplication header = new HeaderApplication(
        headerName == null ? "header " + (headers.size() + 1) : headerName);
    if (stageNames != null) {
      for (String stageName : stageNames) {
        StageApplication stage = getStage(stageName);
        if (stage != null) {
          header.addStage(stage);
        }
      }
    }
    headers.put(header.getName(), header);
    return header;
  }

  /**
   * Execute one deterministic anti-surge scan cycle.
   *
   * @param scanInput scan input values keyed by stage name
   * @param dt scan period in seconds
   * @return scan result with stage commands, header diagnostics, alarms, and recommendations
   */
  public ScanResult scan(ScanInput scanInput, double dt) {
    double scanTime = dt > 0.0 && Double.isFinite(dt) ? dt : DEFAULT_SCAN_TIME;
    elapsedTime += scanTime;
    updateSequence(scanInput, scanTime);

    List<StageDecision> stageDecisions = new ArrayList<StageDecision>();
    List<OperatorDiagnostic> diagnostics = new ArrayList<OperatorDiagnostic>();
    ApplicationStatus status = ApplicationStatus.HEALTHY;

    for (StageApplication stage : stages) {
      StageScanInput stageInput = scanInput == null ? null : scanInput.getStageInput(stage.getName());
      StageDecision decision = evaluateStage(stage, stageInput, scanTime);
      stageDecisions.add(decision);
      diagnostics.addAll(decision.getDiagnostics());
      status = worst(status, decision.getStatus());
    }

    List<HeaderDecision> headerDecisions = new ArrayList<HeaderDecision>();
    for (HeaderApplication header : headers.values()) {
      HeaderDecision headerDecision = evaluateHeader(header, stageDecisions);
      headerDecisions.add(headerDecision);
      diagnostics.addAll(headerDecision.getDiagnostics());
      status = worst(status, headerDecision.getStatus());
    }

    if (operatingMode == OperatingMode.TRIP) {
      status = ApplicationStatus.TRIP_DEMAND;
      diagnostics.add(new OperatorDiagnostic("TRIP_MODE", ApplicationStatus.TRIP_DEMAND,
          "Trip mode active; recycle commands forced conservative", "Keep recycle open and verify shutdown path"));
    }

    lastScanResult = new ScanResult(name, operatingMode, sequenceState, status, elapsedTime, stageDecisions,
        headerDecisions, diagnostics, getCertificationStatus());
    return lastScanResult;
  }

  /**
   * Run the commissioning support checks for the configured application.
   *
   * @return commissioning report
   */
  public CommissioningReport runCommissioningChecks() {
    List<CommissioningCheck> checks = new ArrayList<CommissioningCheck>();
    checks.add(new CommissioningCheck("Application has compressor stages",
        stages.isEmpty() ? CheckStatus.FAIL : CheckStatus.PASS, "Configured stages: " + stages.size(),
        "Add at least one compressor stage"));
    for (StageApplication stage : stages) {
      checks.add(checkStage(stage));
      checks.add(checkRecycleSizing(stage));
      checks.add(checkValveStroke(stage));
      checks.add(checkTransmitters(stage));
    }
    for (HeaderApplication header : headers.values()) {
      CheckStatus status = header.getStages().size() > 1 ? CheckStatus.PASS : CheckStatus.WARN;
      checks.add(new CommissioningCheck("Header coordination " + header.getName(), status,
          "Connected stages: " + header.getStages().size(), "Connect all machines sharing a suction/discharge header"));
    }
    return new CommissioningReport(name, checks, getCertificationStatus(), certificationStatement());
  }

  /**
   * Start the startup sequence.
   */
  public void startStartupSequence() {
    operatingMode = OperatingMode.STARTUP;
    sequenceState = SequenceState.CHECK_PERMISSIVES;
    stateTime = 0.0;
  }

  /**
   * Start the planned shutdown sequence.
   */
  public void startShutdownSequence() {
    operatingMode = OperatingMode.SHUTDOWN;
    sequenceState = SequenceState.SHUTDOWN_RECYCLE_OPEN;
    stateTime = 0.0;
  }

  /**
   * Force trip/coastdown mode for simulation and advisory studies.
   */
  public void forceTripMode() {
    operatingMode = OperatingMode.TRIP;
    sequenceState = SequenceState.COASTDOWN;
    stateTime = 0.0;
  }

  /**
   * Set normal running mode.
   */
  public void setRunningMode() {
    operatingMode = OperatingMode.RUNNING;
    sequenceState = SequenceState.NORMAL_CONTROL;
    stateTime = 0.0;
  }

  /**
   * Get the application name.
   *
   * @return application name
   */
  public String getName() {
    return name;
  }

  /**
   * Get stage configurations.
   *
   * @return immutable stage list
   */
  public List<StageApplication> getStages() {
    return Collections.unmodifiableList(stages);
  }

  /**
   * Get header configurations.
   *
   * @return immutable header list
   */
  public List<HeaderApplication> getHeaders() {
    return Collections.unmodifiableList(new ArrayList<HeaderApplication>(headers.values()));
  }

  /**
   * Get the shared advanced supervisor.
   *
   * @return advanced anti-surge supervisor
   */
  public AdvancedAntiSurgeControlSystem getSupervisor() {
    return supervisor;
  }

  /**
   * Get current operating mode.
   *
   * @return operating mode
   */
  public OperatingMode getOperatingMode() {
    return operatingMode;
  }

  /**
   * Get current sequence state.
   *
   * @return sequence state
   */
  public SequenceState getSequenceState() {
    return sequenceState;
  }

  /**
   * Get the latest scan result.
   *
   * @return latest scan result, or null before first scan
   */
  public ScanResult getLastScanResult() {
    return lastScanResult;
  }

  /**
   * Get certification status.
   *
   * @return certification status
   */
  public CertificationStatus getCertificationStatus() {
    return CertificationStatus.NOT_CERTIFIED_FOR_PROTECTION;
  }

  /**
   * Get plain-language certification statement.
   *
   * @return certification statement
   */
  public String certificationStatement() {
    return "NeqSim anti-surge application logic is for simulation, design review, commissioning support, "
        + "and advisory calculations only. It is not a certified vendor controller, SIL safety function, "
        + "or machinery protection system.";
  }

  /**
   * Set alarm and trip margin limits.
   *
   * @param alarmMargin low-margin alarm threshold
   * @param tripMargin trip-demand threshold for simulation/advisory studies
   */
  public void setMarginLimits(double alarmMargin, double tripMargin) {
    this.alarmMargin = alarmMargin;
    this.tripMargin = tripMargin;
  }

  /**
   * Set sequence pre-open recycle demand.
   *
   * @param sequencePreopenDemand pre-open demand in percent
   */
  public void setSequencePreopenDemand(double sequencePreopenDemand) {
    this.sequencePreopenDemand = clamp(sequencePreopenDemand, 0.0, 100.0);
  }

  /**
   * Set permissive timeout.
   *
   * @param permissiveTimeout timeout in seconds
   */
  public void setPermissiveTimeout(double permissiveTimeout) {
    this.permissiveTimeout = Math.max(permissiveTimeout, 0.0);
  }

  /**
   * Get a stage by name.
   *
   * @param stageName stage name
   * @return stage configuration, or null if not found
   */
  private StageApplication getStage(String stageName) {
    if (stageName == null) {
      return null;
    }
    for (StageApplication stage : stages) {
      if (stageName.equals(stage.getName())) {
        return stage;
      }
    }
    return null;
  }

  /**
   * Evaluate one compressor stage.
   *
   * @param stage stage configuration
   * @param input scan input for the stage
   * @param dt scan period in seconds
   * @return stage decision
   */
  private StageDecision evaluateStage(StageApplication stage, StageScanInput input, double dt) {
    List<OperatorDiagnostic> diagnostics = new ArrayList<OperatorDiagnostic>();
    if (input == null) {
      input = StageScanInput.fromStage(stage);
      diagnostics.add(new OperatorDiagnostic("MISSING_STAGE_INPUT", ApplicationStatus.DEGRADED,
          "No scan input supplied for " + stage.getName() + "; using stage defaults",
          "Bind online measurements or provide simulated scan input"));
    }
    stage.updateSignals(input.getMargin(), dt);
    FaultTolerantDecision faultDecision = supervisor.evaluateFaultTolerant(stage.getMarginSignals(),
        stage.getVotingMode(), input.getMarginRate(), dt);
    double totalDemand = faultDecision.getValveOpening();
    if (operatingMode == OperatingMode.STARTUP && sequenceState != SequenceState.NORMAL_CONTROL) {
      totalDemand = Math.max(totalDemand, sequencePreopenDemand);
    } else if (operatingMode == OperatingMode.SHUTDOWN || operatingMode == OperatingMode.TRIP) {
      totalDemand = Math.max(totalDemand, 100.0);
    }
    DualRecycleValveCommand split = supervisor.splitDualRecycleCommand(totalDemand, input.getMargin(),
        input.getMarginRate());
    RecycleSizingResult sizing = AdvancedAntiSurgeControlSystem.sizeRecycleSystem(input.getInletFlow(),
        input.getSurgeFlow(), stage.getControlMargin(), Math.max(input.getSuctionDensity(), 1.0e-6),
        Math.max(stage.getValvePressureDrop(), 1.0e-6), stage.getRecyclePipingVolume(),
        stage.getRequiredResponseTime());

    ApplicationStatus status = ApplicationStatus.HEALTHY;
    if (!faultDecision.isValid() || input.getMargin() <= tripMargin) {
      status = ApplicationStatus.TRIP_DEMAND;
      diagnostics.add(new OperatorDiagnostic("ANTI_SURGE_TRIP_DEMAND", ApplicationStatus.TRIP_DEMAND,
          stage.getName() + " is at or below trip-demand margin",
          "Open recycle, unload compressor, and verify trip path"));
    } else if (input.getMargin() <= alarmMargin) {
      status = ApplicationStatus.ALARM;
      diagnostics.add(new OperatorDiagnostic("LOW_SURGE_MARGIN", ApplicationStatus.ALARM,
          stage.getName() + " surge margin is below alarm limit", "Reduce load or increase recycle flow"));
    } else if (faultDecision.isFallbackActive() || !sizing.isVolumeResponseAcceptable()) {
      status = ApplicationStatus.DEGRADED;
      diagnostics.add(new OperatorDiagnostic("ANTI_SURGE_DEGRADED", ApplicationStatus.DEGRADED,
          stage.getName() + " has degraded instrumentation or recycle response", faultDecision.getMessage()));
    }
    stage.setLastHotValveOpening(split.getHotValveOpening());
    stage.setLastColdValveOpening(split.getColdValveOpening());
    return new StageDecision(stage.getName(), status, input.getMargin(), faultDecision.getVotedMargin(), totalDemand,
        split, sizing, diagnostics);
  }

  /**
   * Evaluate shared header coordination.
   *
   * @param header header configuration
   * @param stageDecisions stage decisions from this scan
   * @return header decision
   */
  private HeaderDecision evaluateHeader(HeaderApplication header, List<StageDecision> stageDecisions) {
    List<OperatorDiagnostic> diagnostics = new ArrayList<OperatorDiagnostic>();
    double minimumMargin = Double.POSITIVE_INFINITY;
    double maximumDemand = 0.0;
    for (StageApplication stage : header.getStages()) {
      StageDecision decision = findDecision(stage.getName(), stageDecisions);
      if (decision != null) {
        minimumMargin = Math.min(minimumMargin, decision.getMeasuredMargin());
        maximumDemand = Math.max(maximumDemand, decision.getTotalRecycleDemand());
      }
    }
    if (!Double.isFinite(minimumMargin)) {
      diagnostics.add(new OperatorDiagnostic("HEADER_NO_STAGE_DATA", ApplicationStatus.DEGRADED,
          "Header " + header.getName() + " has no stage scan data", "Check header-stage configuration"));
      return new HeaderDecision(header.getName(), ApplicationStatus.DEGRADED, Double.NaN, maximumDemand, diagnostics);
    }
    ApplicationStatus status = minimumMargin <= tripMargin ? ApplicationStatus.TRIP_DEMAND
        : minimumMargin <= alarmMargin ? ApplicationStatus.ALARM : ApplicationStatus.HEALTHY;
    if (status != ApplicationStatus.HEALTHY) {
      diagnostics.add(new OperatorDiagnostic("HEADER_LIMITING_STAGE", status,
          "Header " + header.getName() + " is limited by minimum compressor margin",
          "Coordinate load sharing and avoid increasing speed on machines with active recycle"));
    }
    return new HeaderDecision(header.getName(), status, minimumMargin, maximumDemand, diagnostics);
  }

  /**
   * Find a stage decision by stage name.
   *
   * @param stageName stage name
   * @param decisions decisions to search
   * @return matching decision, or null if none exists
   */
  private StageDecision findDecision(String stageName, List<StageDecision> decisions) {
    for (StageDecision decision : decisions) {
      if (decision.getStageName().equals(stageName)) {
        return decision;
      }
    }
    return null;
  }

  /**
   * Update startup/shutdown sequence state.
   *
   * @param scanInput scan input
   * @param dt scan period in seconds
   */
  private void updateSequence(ScanInput scanInput, double dt) {
    stateTime += dt;
    if (operatingMode == OperatingMode.STARTUP) {
      boolean permissivesOk = scanInput == null || scanInput.isPermissivesOk();
      if (sequenceState == SequenceState.CHECK_PERMISSIVES) {
        if (permissivesOk) {
          transition(SequenceState.RECYCLE_PREOPEN);
        } else if (stateTime >= permissiveTimeout) {
          transition(SequenceState.FAILED);
        }
      } else if (sequenceState == SequenceState.RECYCLE_PREOPEN && stateTime >= 2.0) {
        transition(SequenceState.PRESSURIZE);
      } else if (sequenceState == SequenceState.PRESSURIZE && stateTime >= 2.0) {
        transition(SequenceState.SPEED_RAMP);
      } else if (sequenceState == SequenceState.SPEED_RAMP && stateTime >= 2.0) {
        transition(SequenceState.LOAD_ACCEPT);
      } else if (sequenceState == SequenceState.LOAD_ACCEPT && stateTime >= 2.0) {
        transition(SequenceState.NORMAL_CONTROL);
        operatingMode = OperatingMode.RUNNING;
      }
    } else if (operatingMode == OperatingMode.SHUTDOWN) {
      if (sequenceState == SequenceState.SHUTDOWN_RECYCLE_OPEN && stateTime >= 2.0) {
        transition(SequenceState.COASTDOWN);
      } else if (sequenceState == SequenceState.COASTDOWN && stateTime >= 5.0) {
        transition(SequenceState.COMPLETE);
        operatingMode = OperatingMode.IDLE;
      }
    }
  }

  /**
   * Transition to a new sequence state.
   *
   * @param newState new sequence state
   */
  private void transition(SequenceState newState) {
    sequenceState = newState;
    stateTime = 0.0;
  }

  /**
   * Create stage configuration commissioning check.
   *
   * @param stage stage configuration
   * @return commissioning check
   */
  private CommissioningCheck checkStage(StageApplication stage) {
    CheckStatus status = stage.getControlMargin() > 0.0 ? CheckStatus.PASS : CheckStatus.FAIL;
    return new CommissioningCheck("Stage configuration " + stage.getName(), status,
        "Control margin: " + stage.getControlMargin(), "Set a positive surge control margin");
  }

  /**
   * Create recycle sizing commissioning check.
   *
   * @param stage stage configuration
   * @return commissioning check
   */
  private CommissioningCheck checkRecycleSizing(StageApplication stage) {
    StageScanInput input = StageScanInput.fromStage(stage);
    RecycleSizingResult sizing = AdvancedAntiSurgeControlSystem.sizeRecycleSystem(input.getInletFlow(),
        input.getSurgeFlow(), stage.getControlMargin(), Math.max(input.getSuctionDensity(), 1.0e-6),
        Math.max(stage.getValvePressureDrop(), 1.0e-6), stage.getRecyclePipingVolume(),
        stage.getRequiredResponseTime());
    CheckStatus status = sizing.isVolumeResponseAcceptable() ? CheckStatus.PASS : CheckStatus.WARN;
    return new CommissioningCheck("Recycle response " + stage.getName(), status,
        "Response time: " + sizing.getVolumeResponseTime() + " s", "Reduce recycle volume or increase valve capacity");
  }

  /**
   * Create valve stroke commissioning check.
   *
   * @param stage stage configuration
   * @return commissioning check
   */
  private CommissioningCheck checkValveStroke(StageApplication stage) {
    boolean ok = stage.getHotValveStrokeTime() <= stage.getRequiredResponseTime()
        && stage.getColdValveStrokeTime() <= 2.0 * stage.getRequiredResponseTime();
    return new CommissioningCheck("Recycle valve stroke " + stage.getName(), ok ? CheckStatus.PASS : CheckStatus.WARN,
        "Hot/cold stroke time: " + stage.getHotValveStrokeTime() + "/" + stage.getColdValveStrokeTime() + " s",
        "Verify actuator sizing and stroke-test results");
  }

  /**
   * Create transmitter commissioning check.
   *
   * @param stage stage configuration
   * @return commissioning check
   */
  private CommissioningCheck checkTransmitters(StageApplication stage) {
    CheckStatus status = stage.getMarginSignals().size() >= 2 ? CheckStatus.PASS : CheckStatus.WARN;
    return new CommissioningCheck("Transmitter redundancy " + stage.getName(), status,
        "Margin signals: " + stage.getMarginSignals().size(),
        "Use redundant validated transmitters for fault tolerance");
  }

  /**
   * Return the worse of two application statuses.
   *
   * @param left first status
   * @param right second status
   * @return worse status
   */
  private ApplicationStatus worst(ApplicationStatus left, ApplicationStatus right) {
    return rank(right) > rank(left) ? right : left;
  }

  /**
   * Rank status severity.
   *
   * @param status status
   * @return severity rank
   */
  private int rank(ApplicationStatus status) {
    if (status == ApplicationStatus.TRIP_DEMAND) {
      return 3;
    }
    if (status == ApplicationStatus.ALARM) {
      return 2;
    }
    if (status == ApplicationStatus.DEGRADED) {
      return 1;
    }
    return 0;
  }

  /**
   * Clamp value.
   *
   * @param value value
   * @param low lower limit
   * @param high upper limit
   * @return clamped value
   */
  private static double clamp(double value, double low, double high) {
    return Math.max(low, Math.min(high, value));
  }

  /** Stage-level anti-surge application configuration. */
  public static class StageApplication implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Stage name. */
    private final String name;
    /** Optional compressor object. */
    private Compressor compressor;
    /** Redundant margin signals. */
    private final List<InstrumentSignal> marginSignals = new ArrayList<InstrumentSignal>();
    /** Signal voting mode. */
    private VotingMode votingMode = VotingMode.SELECT_LOW;
    /** Design inlet flow in m3/hr. */
    private double designInletFlow = 10000.0;
    /** Design surge flow in m3/hr. */
    private double designSurgeFlow = 9000.0;
    /** Design suction density in kg/m3. */
    private double designSuctionDensity = 35.0;
    /** Control margin. */
    private double controlMargin = 0.10;
    /** Valve pressure drop in bar. */
    private double valvePressureDrop = 4.0;
    /** Recycle piping volume in m3. */
    private double recyclePipingVolume = 3.0;
    /** Required recycle response time in seconds. */
    private double requiredResponseTime = 10.0;
    /** Hot recycle valve stroke time in seconds. */
    private double hotValveStrokeTime = 2.0;
    /** Cold recycle valve stroke time in seconds. */
    private double coldValveStrokeTime = 6.0;
    /** Last hot valve opening. */
    private double lastHotValveOpening = 0.0;
    /** Last cold valve opening. */
    private double lastColdValveOpening = 0.0;

    /**
     * Create stage configuration.
     *
     * @param name stage name
     */
    public StageApplication(String name) {
      this.name = name;
      addMarginSignal(name + " margin A", -1.0, 2.0, 0.2);
      addMarginSignal(name + " margin B", -1.0, 2.0, 0.2);
    }

    /**
     * Add a simulated margin transmitter.
     *
     * @param signalName signal name
     * @param minimum minimum valid value
     * @param maximum maximum valid value
     * @param lagTime lag time in seconds
     * @return created signal
     */
    public InstrumentSignal addMarginSignal(String signalName, double minimum, double maximum, double lagTime) {
      InstrumentSignal signal = new InstrumentSignal(signalName, minimum, maximum, lagTime);
      marginSignals.add(signal);
      return signal;
    }

    /**
     * Update all margin signals.
     *
     * @param margin margin value
     * @param dt timestep in seconds
     */
    private void updateSignals(double margin, double dt) {
      for (InstrumentSignal signal : marginSignals) {
        signal.update(margin, dt);
      }
    }

    /**
     * Inject a fault into a configured margin transmitter.
     *
     * @param index signal index
     * @param fault fault type
     * @param parameter fault parameter
     */
    public void setSignalFault(int index, SensorFault fault, double parameter) {
      if (index >= 0 && index < marginSignals.size()) {
        marginSignals.get(index).setFault(fault, parameter);
      }
    }

    /**
     * Set compressor object.
     *
     * @param compressor compressor object
     */
    public void setCompressor(Compressor compressor) {
      this.compressor = compressor;
    }

    /**
     * Get compressor object.
     *
     * @return compressor object, or null
     */
    public Compressor getCompressor() {
      return compressor;
    }

    /**
     * Set design basis.
     *
     * @param designInletFlow design inlet flow in m3/hr
     * @param designSurgeFlow design surge flow in m3/hr
     * @param designSuctionDensity design suction density in kg/m3
     */
    public void setDesignBasis(double designInletFlow, double designSurgeFlow, double designSuctionDensity) {
      this.designInletFlow = designInletFlow;
      this.designSurgeFlow = Math.max(designSurgeFlow, 1.0e-9);
      this.designSuctionDensity = Math.max(designSuctionDensity, 1.0e-9);
    }

    /**
     * Set recycle design basis.
     *
     * @param controlMargin control margin
     * @param valvePressureDrop valve pressure drop in bar
     * @param recyclePipingVolume recycle piping volume in m3
     * @param requiredResponseTime required response time in seconds
     */
    public void setRecycleDesign(double controlMargin, double valvePressureDrop, double recyclePipingVolume,
        double requiredResponseTime) {
      this.controlMargin = controlMargin;
      this.valvePressureDrop = Math.max(valvePressureDrop, 1.0e-9);
      this.recyclePipingVolume = Math.max(recyclePipingVolume, 0.0);
      this.requiredResponseTime = Math.max(requiredResponseTime, 1.0e-9);
    }

    /**
     * Set valve stroke times.
     *
     * @param hotValveStrokeTime hot valve stroke time in seconds
     * @param coldValveStrokeTime cold valve stroke time in seconds
     */
    public void setValveStrokeTimes(double hotValveStrokeTime, double coldValveStrokeTime) {
      this.hotValveStrokeTime = Math.max(hotValveStrokeTime, 0.0);
      this.coldValveStrokeTime = Math.max(coldValveStrokeTime, 0.0);
    }

    /**
     * Set voting mode.
     *
     * @param votingMode voting mode
     */
    public void setVotingMode(VotingMode votingMode) {
      this.votingMode = votingMode == null ? VotingMode.SELECT_LOW : votingMode;
    }

    /**
     * Get stage name.
     *
     * @return stage name
     */
    public String getName() {
      return name;
    }

    /**
     * Get margin signals.
     *
     * @return immutable signal list
     */
    public List<InstrumentSignal> getMarginSignals() {
      return Collections.unmodifiableList(marginSignals);
    }

    /**
     * Get voting mode.
     *
     * @return voting mode
     */
    public VotingMode getVotingMode() {
      return votingMode;
    }

    /**
     * Get design inlet flow.
     *
     * @return design inlet flow in m3/hr
     */
    public double getDesignInletFlow() {
      return designInletFlow;
    }

    /**
     * Get design surge flow.
     *
     * @return design surge flow in m3/hr
     */
    public double getDesignSurgeFlow() {
      return designSurgeFlow;
    }

    /**
     * Get design suction density.
     *
     * @return design suction density in kg/m3
     */
    public double getDesignSuctionDensity() {
      return designSuctionDensity;
    }

    /**
     * Get control margin.
     *
     * @return control margin
     */
    public double getControlMargin() {
      return controlMargin;
    }

    /**
     * Get valve pressure drop.
     *
     * @return valve pressure drop in bar
     */
    public double getValvePressureDrop() {
      return valvePressureDrop;
    }

    /**
     * Get recycle piping volume.
     *
     * @return recycle piping volume in m3
     */
    public double getRecyclePipingVolume() {
      return recyclePipingVolume;
    }

    /**
     * Get required response time.
     *
     * @return required response time in seconds
     */
    public double getRequiredResponseTime() {
      return requiredResponseTime;
    }

    /**
     * Get hot valve stroke time.
     *
     * @return hot valve stroke time in seconds
     */
    public double getHotValveStrokeTime() {
      return hotValveStrokeTime;
    }

    /**
     * Get cold valve stroke time.
     *
     * @return cold valve stroke time in seconds
     */
    public double getColdValveStrokeTime() {
      return coldValveStrokeTime;
    }

    /**
     * Set last hot valve opening.
     *
     * @param lastHotValveOpening hot valve opening in percent
     */
    private void setLastHotValveOpening(double lastHotValveOpening) {
      this.lastHotValveOpening = lastHotValveOpening;
    }

    /**
     * Set last cold valve opening.
     *
     * @param lastColdValveOpening cold valve opening in percent
     */
    private void setLastColdValveOpening(double lastColdValveOpening) {
      this.lastColdValveOpening = lastColdValveOpening;
    }

    /**
     * Get last hot valve opening.
     *
     * @return hot valve opening in percent
     */
    public double getLastHotValveOpening() {
      return lastHotValveOpening;
    }

    /**
     * Get last cold valve opening.
     *
     * @return cold valve opening in percent
     */
    public double getLastColdValveOpening() {
      return lastColdValveOpening;
    }
  }

  /** Shared header anti-surge coordination configuration. */
  public static class HeaderApplication implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Header name. */
    private final String name;
    /** Stages connected to the header. */
    private final List<StageApplication> stages = new ArrayList<StageApplication>();

    /**
     * Create header configuration.
     *
     * @param name header name
     */
    public HeaderApplication(String name) {
      this.name = name;
    }

    /**
     * Add stage to header.
     *
     * @param stage stage configuration
     */
    public void addStage(StageApplication stage) {
      if (stage != null && !stages.contains(stage)) {
        stages.add(stage);
      }
    }

    /**
     * Get header name.
     *
     * @return header name
     */
    public String getName() {
      return name;
    }

    /**
     * Get connected stages.
     *
     * @return immutable stage list
     */
    public List<StageApplication> getStages() {
      return Collections.unmodifiableList(stages);
    }
  }

  /** Scan input for all stages. */
  public static class ScanInput implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Stage input map. */
    private final Map<String, StageScanInput> stageInputs = new LinkedHashMap<String, StageScanInput>();
    /** Permissive flag. */
    private boolean permissivesOk = true;

    /**
     * Add stage input.
     *
     * @param stageName stage name
     * @param input stage input
     */
    public void putStageInput(String stageName, StageScanInput input) {
      if (stageName != null && input != null) {
        stageInputs.put(stageName, input);
      }
    }

    /**
     * Get stage input.
     *
     * @param stageName stage name
     * @return stage input, or null
     */
    public StageScanInput getStageInput(String stageName) {
      return stageInputs.get(stageName);
    }

    /**
     * Set permissive status.
     *
     * @param permissivesOk true if all permissives are healthy
     */
    public void setPermissivesOk(boolean permissivesOk) {
      this.permissivesOk = permissivesOk;
    }

    /**
     * Get permissive status.
     *
     * @return true if all permissives are healthy
     */
    public boolean isPermissivesOk() {
      return permissivesOk;
    }
  }

  /** Stage scan input. */
  public static class StageScanInput implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Measured surge margin. */
    private final double margin;
    /** Margin rate in margin units per second. */
    private final double marginRate;
    /** Inlet flow in m3/hr. */
    private final double inletFlow;
    /** Surge flow in m3/hr. */
    private final double surgeFlow;
    /** Suction density in kg/m3. */
    private final double suctionDensity;

    /**
     * Create stage scan input.
     *
     * @param margin measured surge margin
     * @param marginRate margin rate in margin units per second
     * @param inletFlow inlet flow in m3/hr
     * @param surgeFlow surge flow in m3/hr
     * @param suctionDensity suction density in kg/m3
     */
    public StageScanInput(double margin, double marginRate, double inletFlow, double surgeFlow, double suctionDensity) {
      this.margin = margin;
      this.marginRate = marginRate;
      this.inletFlow = inletFlow;
      this.surgeFlow = Math.max(surgeFlow, 1.0e-9);
      this.suctionDensity = Math.max(suctionDensity, 1.0e-9);
    }

    /**
     * Create scan input from design defaults.
     *
     * @param stage stage configuration
     * @return stage scan input
     */
    public static StageScanInput fromStage(StageApplication stage) {
      double margin = stage.getDesignInletFlow() / Math.max(stage.getDesignSurgeFlow(), 1.0e-9) - 1.0;
      return new StageScanInput(margin, 0.0, stage.getDesignInletFlow(), stage.getDesignSurgeFlow(),
          stage.getDesignSuctionDensity());
    }

    /**
     * Get margin.
     *
     * @return measured surge margin
     */
    public double getMargin() {
      return margin;
    }

    /**
     * Get margin rate.
     *
     * @return margin rate
     */
    public double getMarginRate() {
      return marginRate;
    }

    /**
     * Get inlet flow.
     *
     * @return inlet flow in m3/hr
     */
    public double getInletFlow() {
      return inletFlow;
    }

    /**
     * Get surge flow.
     *
     * @return surge flow in m3/hr
     */
    public double getSurgeFlow() {
      return surgeFlow;
    }

    /**
     * Get suction density.
     *
     * @return suction density in kg/m3
     */
    public double getSuctionDensity() {
      return suctionDensity;
    }
  }

  /** Stage-level scan decision. */
  public static class StageDecision implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Stage name. */
    private final String stageName;
    /** Stage status. */
    private final ApplicationStatus status;
    /** Measured margin. */
    private final double measuredMargin;
    /** Voted margin. */
    private final double votedMargin;
    /** Total recycle demand. */
    private final double totalRecycleDemand;
    /** Dual valve command. */
    private final DualRecycleValveCommand valveCommand;
    /** Recycle sizing result. */
    private final RecycleSizingResult recycleSizing;
    /** Diagnostics. */
    private final List<OperatorDiagnostic> diagnostics;

    /**
     * Create stage decision.
     *
     * @param stageName stage name
     * @param status stage status
     * @param measuredMargin measured margin
     * @param votedMargin voted margin
     * @param totalRecycleDemand total recycle demand in percent
     * @param valveCommand dual valve command
     * @param recycleSizing recycle sizing result
     * @param diagnostics diagnostics
     */
    public StageDecision(String stageName, ApplicationStatus status, double measuredMargin, double votedMargin,
        double totalRecycleDemand, DualRecycleValveCommand valveCommand, RecycleSizingResult recycleSizing,
        List<OperatorDiagnostic> diagnostics) {
      this.stageName = stageName;
      this.status = status;
      this.measuredMargin = measuredMargin;
      this.votedMargin = votedMargin;
      this.totalRecycleDemand = totalRecycleDemand;
      this.valveCommand = valveCommand;
      this.recycleSizing = recycleSizing;
      this.diagnostics = new ArrayList<OperatorDiagnostic>(diagnostics);
    }

    /**
     * Get stage name.
     *
     * @return stage name
     */
    public String getStageName() {
      return stageName;
    }

    /**
     * Get status.
     *
     * @return stage status
     */
    public ApplicationStatus getStatus() {
      return status;
    }

    /**
     * Get measured margin.
     *
     * @return measured margin
     */
    public double getMeasuredMargin() {
      return measuredMargin;
    }

    /**
     * Get voted margin.
     *
     * @return voted margin
     */
    public double getVotedMargin() {
      return votedMargin;
    }

    /**
     * Get total recycle demand.
     *
     * @return total recycle demand in percent
     */
    public double getTotalRecycleDemand() {
      return totalRecycleDemand;
    }

    /**
     * Get valve command.
     *
     * @return dual valve command
     */
    public DualRecycleValveCommand getValveCommand() {
      return valveCommand;
    }

    /**
     * Get recycle sizing.
     *
     * @return recycle sizing result
     */
    public RecycleSizingResult getRecycleSizing() {
      return recycleSizing;
    }

    /**
     * Get diagnostics.
     *
     * @return immutable diagnostics list
     */
    public List<OperatorDiagnostic> getDiagnostics() {
      return Collections.unmodifiableList(diagnostics);
    }
  }

  /** Header-level scan decision. */
  public static class HeaderDecision implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Header name. */
    private final String headerName;
    /** Header status. */
    private final ApplicationStatus status;
    /** Minimum connected-stage margin. */
    private final double minimumMargin;
    /** Maximum connected-stage recycle demand. */
    private final double maximumRecycleDemand;
    /** Diagnostics. */
    private final List<OperatorDiagnostic> diagnostics;

    /**
     * Create header decision.
     *
     * @param headerName header name
     * @param status header status
     * @param minimumMargin minimum margin
     * @param maximumRecycleDemand maximum recycle demand
     * @param diagnostics diagnostics
     */
    public HeaderDecision(String headerName, ApplicationStatus status, double minimumMargin,
        double maximumRecycleDemand, List<OperatorDiagnostic> diagnostics) {
      this.headerName = headerName;
      this.status = status;
      this.minimumMargin = minimumMargin;
      this.maximumRecycleDemand = maximumRecycleDemand;
      this.diagnostics = new ArrayList<OperatorDiagnostic>(diagnostics);
    }

    /**
     * Get header name.
     *
     * @return header name
     */
    public String getHeaderName() {
      return headerName;
    }

    /**
     * Get status.
     *
     * @return header status
     */
    public ApplicationStatus getStatus() {
      return status;
    }

    /**
     * Get minimum margin.
     *
     * @return minimum margin
     */
    public double getMinimumMargin() {
      return minimumMargin;
    }

    /**
     * Get maximum recycle demand.
     *
     * @return maximum recycle demand in percent
     */
    public double getMaximumRecycleDemand() {
      return maximumRecycleDemand;
    }

    /**
     * Get diagnostics.
     *
     * @return immutable diagnostics list
     */
    public List<OperatorDiagnostic> getDiagnostics() {
      return Collections.unmodifiableList(diagnostics);
    }
  }

  /** Operator-facing diagnostic. */
  public static class OperatorDiagnostic implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Diagnostic code. */
    private final String code;
    /** Diagnostic status. */
    private final ApplicationStatus status;
    /** Message. */
    private final String message;
    /** Recommended action. */
    private final String recommendation;

    /**
     * Create diagnostic.
     *
     * @param code diagnostic code
     * @param status diagnostic status
     * @param message diagnostic message
     * @param recommendation recommended action
     */
    public OperatorDiagnostic(String code, ApplicationStatus status, String message, String recommendation) {
      this.code = code;
      this.status = status;
      this.message = message;
      this.recommendation = recommendation;
    }

    /**
     * Get code.
     *
     * @return diagnostic code
     */
    public String getCode() {
      return code;
    }

    /**
     * Get status.
     *
     * @return diagnostic status
     */
    public ApplicationStatus getStatus() {
      return status;
    }

    /**
     * Get message.
     *
     * @return diagnostic message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Get recommendation.
     *
     * @return recommended action
     */
    public String getRecommendation() {
      return recommendation;
    }
  }

  /** Scan result. */
  public static class ScanResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Application name. */
    private final String applicationName;
    /** Operating mode. */
    private final OperatingMode operatingMode;
    /** Sequence state. */
    private final SequenceState sequenceState;
    /** Application status. */
    private final ApplicationStatus status;
    /** Timestamp in seconds. */
    private final double time;
    /** Stage decisions. */
    private final List<StageDecision> stageDecisions;
    /** Header decisions. */
    private final List<HeaderDecision> headerDecisions;
    /** Diagnostics. */
    private final List<OperatorDiagnostic> diagnostics;
    /** Certification status. */
    private final CertificationStatus certificationStatus;

    /**
     * Create scan result.
     *
     * @param applicationName application name
     * @param operatingMode operating mode
     * @param sequenceState sequence state
     * @param status status
     * @param time scan time in seconds
     * @param stageDecisions stage decisions
     * @param headerDecisions header decisions
     * @param diagnostics diagnostics
     * @param certificationStatus certification status
     */
    public ScanResult(String applicationName, OperatingMode operatingMode, SequenceState sequenceState,
        ApplicationStatus status, double time, List<StageDecision> stageDecisions, List<HeaderDecision> headerDecisions,
        List<OperatorDiagnostic> diagnostics, CertificationStatus certificationStatus) {
      this.applicationName = applicationName;
      this.operatingMode = operatingMode;
      this.sequenceState = sequenceState;
      this.status = status;
      this.time = time;
      this.stageDecisions = new ArrayList<StageDecision>(stageDecisions);
      this.headerDecisions = new ArrayList<HeaderDecision>(headerDecisions);
      this.diagnostics = new ArrayList<OperatorDiagnostic>(diagnostics);
      this.certificationStatus = certificationStatus;
    }

    /**
     * Get application name.
     *
     * @return application name
     */
    public String getApplicationName() {
      return applicationName;
    }

    /**
     * Get operating mode.
     *
     * @return operating mode
     */
    public OperatingMode getOperatingMode() {
      return operatingMode;
    }

    /**
     * Get sequence state.
     *
     * @return sequence state
     */
    public SequenceState getSequenceState() {
      return sequenceState;
    }

    /**
     * Get status.
     *
     * @return application status
     */
    public ApplicationStatus getStatus() {
      return status;
    }

    /**
     * Get time.
     *
     * @return time in seconds
     */
    public double getTime() {
      return time;
    }

    /**
     * Get stage decisions.
     *
     * @return immutable stage decisions
     */
    public List<StageDecision> getStageDecisions() {
      return Collections.unmodifiableList(stageDecisions);
    }

    /**
     * Get header decisions.
     *
     * @return immutable header decisions
     */
    public List<HeaderDecision> getHeaderDecisions() {
      return Collections.unmodifiableList(headerDecisions);
    }

    /**
     * Get diagnostics.
     *
     * @return immutable diagnostics
     */
    public List<OperatorDiagnostic> getDiagnostics() {
      return Collections.unmodifiableList(diagnostics);
    }

    /**
     * Get certification status.
     *
     * @return certification status
     */
    public CertificationStatus getCertificationStatus() {
      return certificationStatus;
    }
  }

  /** Commissioning check. */
  public static class CommissioningCheck implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Check name. */
    private final String name;
    /** Check status. */
    private final CheckStatus status;
    /** Evidence text. */
    private final String evidence;
    /** Recommendation text. */
    private final String recommendation;

    /**
     * Create commissioning check.
     *
     * @param name check name
     * @param status check status
     * @param evidence evidence text
     * @param recommendation recommendation text
     */
    public CommissioningCheck(String name, CheckStatus status, String evidence, String recommendation) {
      this.name = name;
      this.status = status;
      this.evidence = evidence;
      this.recommendation = recommendation;
    }

    /**
     * Get check name.
     *
     * @return check name
     */
    public String getName() {
      return name;
    }

    /**
     * Get status.
     *
     * @return check status
     */
    public CheckStatus getStatus() {
      return status;
    }

    /**
     * Get evidence.
     *
     * @return evidence text
     */
    public String getEvidence() {
      return evidence;
    }

    /**
     * Get recommendation.
     *
     * @return recommendation text
     */
    public String getRecommendation() {
      return recommendation;
    }
  }

  /** Commissioning report. */
  public static class CommissioningReport implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Application name. */
    private final String applicationName;
    /** Checks. */
    private final List<CommissioningCheck> checks;
    /** Certification status. */
    private final CertificationStatus certificationStatus;
    /** Certification statement. */
    private final String certificationStatement;

    /**
     * Create commissioning report.
     *
     * @param applicationName application name
     * @param checks checks
     * @param certificationStatus certification status
     * @param certificationStatement certification statement
     */
    public CommissioningReport(String applicationName, List<CommissioningCheck> checks,
        CertificationStatus certificationStatus, String certificationStatement) {
      this.applicationName = applicationName;
      this.checks = new ArrayList<CommissioningCheck>(checks);
      this.certificationStatus = certificationStatus;
      this.certificationStatement = certificationStatement;
    }

    /**
     * Get application name.
     *
     * @return application name
     */
    public String getApplicationName() {
      return applicationName;
    }

    /**
     * Get checks.
     *
     * @return immutable check list
     */
    public List<CommissioningCheck> getChecks() {
      return Collections.unmodifiableList(checks);
    }

    /**
     * Get certification status.
     *
     * @return certification status
     */
    public CertificationStatus getCertificationStatus() {
      return certificationStatus;
    }

    /**
     * Get certification statement.
     *
     * @return certification statement
     */
    public String getCertificationStatement() {
      return certificationStatement;
    }

    /**
     * Check if all checks pass.
     *
     * @return true if every check is pass
     */
    public boolean allPassed() {
      for (CommissioningCheck check : checks) {
        if (check.getStatus() != CheckStatus.PASS) {
          return false;
        }
      }
      return true;
    }
  }
}
