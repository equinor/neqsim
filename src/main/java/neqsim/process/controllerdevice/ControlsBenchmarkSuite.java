package neqsim.process.controllerdevice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import neqsim.process.controllerdevice.ControllerDeviceInterface.ControllerMode;
import neqsim.process.controllerdevice.structure.CascadeControllerStructure;
import neqsim.process.controllerdevice.structure.MinimumSpeedRecycleControllerStructure;
import neqsim.process.controllerdevice.structure.SplitRangeControllerStructure;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.util.scenario.AntiSurgeDynamicBenchmark;
import neqsim.util.agentic.AgentBenchmarkSuite;
import neqsim.util.agentic.AgentBenchmarkSuite.BenchmarkReport;

/**
 * Deterministic, CI-run qualification suite for canonical NeqSim control structures.
 *
 * <p>
 * The suite exercises six loops requested by the dynamics roadmap: integrating level control, pressure disturbance
 * rejection, cascade temperature control, sequential split-range capacity control, anti-surge recycle, and coordinated
 * compressor minimum-speed/recycle control. Every case records the same time, process-value, set-point and output
 * vectors and evaluates them through {@link ControllerPerformanceMetrics}.
 * </p>
 *
 * <p>
 * The transparent plant models are deliberately small. Self-regulating cases use {@code tau * dy/dt = target - y}; the
 * level case uses an inventory balance {@code capacity * dy/dt = inflow - outflow}. They qualify NeqSim controller
 * execution and metric reporting, not a field tuning, vendor compressor model, safety instrumented function, or
 * commissioning study.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ControlsBenchmarkSuite {
  private static final double TIME_STEP_SECONDS = 1.0;

  private ControlsBenchmarkSuite() {
  }

  /** Type of challenge applied to a benchmark case. */
  public enum ChallengeType {
    /** The controlled set point changes during the run. */
    SET_POINT,
    /** An external process disturbance changes during the run. */
    DISTURBANCE,
    /** Both set point and disturbance changes are applied. */
    SET_POINT_AND_DISTURBANCE,
    /** Machinery protection is compared with a disabled-controller reference. */
    PROTECTION
  }

  /** Immutable result for one canonical control case. */
  public static final class CaseResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String name;
    private final ChallengeType challengeType;
    private final double[] timeSeconds;
    private final double[] processValue;
    private final double[] setPoint;
    private final double[] controllerOutput;
    private final ControllerPerformanceMetrics metrics;
    private final double finalRelativeErrorPercent;
    private final double overshootPercent;
    private final double minimumProcessValue;
    private final double maximumProcessValue;
    private final double agentMetric;
    private final boolean passed;
    private final String acceptanceDetail;

    private CaseResult(String id, String name, ChallengeType challengeType, double[] timeSeconds, double[] processValue,
        double[] setPoint, double[] controllerOutput, double agentMetric, boolean passed, String acceptanceDetail) {
      this.id = id;
      this.name = name;
      this.challengeType = challengeType;
      this.timeSeconds = timeSeconds.clone();
      this.processValue = processValue.clone();
      this.setPoint = setPoint.clone();
      this.controllerOutput = controllerOutput.clone();
      this.metrics = ControllerPerformanceMetrics.fromArrays(timeSeconds, processValue, setPoint, controllerOutput);
      this.finalRelativeErrorPercent = relativeErrorPercent(processValue[processValue.length - 1],
          setPoint[setPoint.length - 1]);
      this.overshootPercent = calculateOvershootPercent(processValue, setPoint);
      this.minimumProcessValue = minimum(processValue);
      this.maximumProcessValue = maximum(processValue);
      this.agentMetric = agentMetric;
      this.passed = passed;
      this.acceptanceDetail = acceptanceDetail;
    }

    /** @return stable benchmark problem identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable loop name */
    public String getName() {
      return name;
    }

    /** @return applied challenge type */
    public ChallengeType getChallengeType() {
      return challengeType;
    }

    /** @return simulation time in seconds */
    public double[] getTimeSeconds() {
      return timeSeconds.clone();
    }

    /** @return process-value trace */
    public double[] getProcessValue() {
      return processValue.clone();
    }

    /** @return set-point trace */
    public double[] getSetPoint() {
      return setPoint.clone();
    }

    /** @return controller or coordinated demand trace in percent */
    public double[] getControllerOutput() {
      return controllerOutput.clone();
    }

    /** @return standard controller performance metrics */
    public ControllerPerformanceMetrics getMetrics() {
      return metrics;
    }

    /** @return final absolute control error divided by the final set point, in percent */
    public double getFinalRelativeErrorPercent() {
      return finalRelativeErrorPercent;
    }

    /** @return largest positive process-value excursion above the instantaneous set point, in percent */
    public double getOvershootPercent() {
      return overshootPercent;
    }

    /** @return minimum process value in the trace */
    public double getMinimumProcessValue() {
      return minimumProcessValue;
    }

    /** @return maximum process value in the trace */
    public double getMaximumProcessValue() {
      return maximumProcessValue;
    }

    /** @return non-negative value submitted to {@link AgentBenchmarkSuite} */
    public double getAgentMetric() {
      return agentMetric;
    }

    /** @return true when all physical and numerical acceptance checks passed */
    public boolean isPassed() {
      return passed;
    }

    /** @return concise acceptance evidence */
    public String getAcceptanceDetail() {
      return acceptanceDetail;
    }
  }

  /** Aggregate immutable report for all canonical cases. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<CaseResult> cases;
    private final BenchmarkReport agentBenchmarkReport;

    private Report(List<CaseResult> cases, BenchmarkReport agentBenchmarkReport) {
      this.cases = new ArrayList<CaseResult>(cases);
      this.agentBenchmarkReport = agentBenchmarkReport;
    }

    /** @return all six case results in stable execution order */
    public List<CaseResult> getCases() {
      return Collections.unmodifiableList(cases);
    }

    /** @return result with the requested identifier, or {@code null} */
    public CaseResult getCase(String id) {
      for (CaseResult result : cases) {
        if (result.getId().equals(id)) {
          return result;
        }
      }
      return null;
    }

    /** @return report produced by the common agent benchmark infrastructure */
    public BenchmarkReport getAgentBenchmarkReport() {
      return agentBenchmarkReport;
    }

    /** @return true when every case and every agent benchmark comparison passed */
    public boolean isPassed() {
      for (CaseResult result : cases) {
        if (!result.isPassed()) {
          return false;
        }
      }
      return agentBenchmarkReport.getFailed() == 0 && agentBenchmarkReport.getNotAttempted() == 0;
    }
  }

  /**
   * Runs all six canonical cases and submits their acceptance metrics to the common agent benchmark suite.
   *
   * @return immutable controls benchmark report
   */
  public static Report runCanonicalSuite() {
    List<CaseResult> results = new ArrayList<CaseResult>();
    results.add(runLevelCase());
    results.add(runPressureCase());
    results.add(runCascadeTemperatureCase());
    results.add(runSplitRangeCase());
    results.add(runAntiSurgeCase());
    results.add(runSpeedRecycleCoordinationCase());

    AgentBenchmarkSuite agentSuite = AgentBenchmarkSuite.createControlsSuite();
    for (CaseResult result : results) {
      agentSuite.addResult(result.getId(), result.getAgentMetric());
      agentSuite.addConvergenceResult(result.getId(), result.isPassed());
    }
    return new Report(results, agentSuite.evaluate());
  }

  private static CaseResult runLevelCase() {
    int steps = 240;
    Trace trace = new Trace(steps);
    MutableMeasurement measurement = new MutableMeasurement("LT-100", 50.0);
    ControllerDeviceBaseClass controller = createController("LIC-100", measurement, 50.0, 50.0, 10.0, 0.0, false);
    double level = 50.0;
    double inflow = 50.0;
    trace.record(0, 0.0, level, 50.0, controller.getResponse());
    for (int step = 1; step <= steps; step++) {
      double time = step * TIME_STEP_SECONDS;
      if (time >= 30.0) {
        controller.setControllerSetPoint(55.0, "percent");
      }
      if (time >= 130.0) {
        inflow = 55.0;
      }
      measurement.setValue(level);
      controller.runTransient(controller.getResponse(), TIME_STEP_SECONDS, UUID.randomUUID());
      double outflow = controller.getResponse();
      level += TIME_STEP_SECONDS * (inflow - outflow) / 25.0;
      trace.record(step, time, level, controller.getControllerSetPoint(), outflow);
    }
    double finalError = relativeErrorPercent(level, controller.getControllerSetPoint());
    boolean passed = finalError <= 1.0 && minimum(trace.processValue) >= 0.0
        && maximum(trace.controllerOutput) <= 100.0;
    return trace.result("control_level_setpoint", "Integrating separator level",
        ChallengeType.SET_POINT_AND_DISTURBANCE, finalError, passed,
        "final relative error <= 1%; non-negative level and bounded valve output");
  }

  private static CaseResult runPressureCase() {
    int steps = 220;
    Trace trace = new Trace(steps);
    MutableMeasurement measurement = new MutableMeasurement("PT-200", 50.0);
    ControllerDeviceBaseClass controller = createController("PIC-200", measurement, 50.0, 50.0, 8.0, 0.0, true);
    double pressure = 50.0;
    double load = 0.0;
    trace.record(0, 0.0, pressure, 50.0, controller.getResponse());
    for (int step = 1; step <= steps; step++) {
      double time = step * TIME_STEP_SECONDS;
      if (time >= 50.0 && time < 140.0) {
        load = 10.0;
      } else if (time >= 140.0) {
        load = -5.0;
      }
      measurement.setValue(pressure);
      controller.runTransient(controller.getResponse(), TIME_STEP_SECONDS, UUID.randomUUID());
      double valve = controller.getResponse();
      pressure += TIME_STEP_SECONDS / 15.0 * (valve + load - pressure);
      trace.record(step, time, pressure, controller.getControllerSetPoint(), valve);
    }
    double finalError = relativeErrorPercent(pressure, controller.getControllerSetPoint());
    boolean passed = finalError <= 1.5 && maximum(trace.controllerOutput) <= 100.0
        && minimum(trace.controllerOutput) >= 0.0;
    return trace.result("control_pressure_disturbance", "Pressure disturbance rejection", ChallengeType.DISTURBANCE,
        finalError, passed,
        "final relative error <= 1.5%; bounded valve output after positive and negative load steps");
  }

  private static CaseResult runCascadeTemperatureCase() {
    int steps = 260;
    Trace trace = new Trace(steps);
    MutableMeasurement temperatureMeasurement = new MutableMeasurement("TT-300", 50.0);
    MutableMeasurement flowMeasurement = new MutableMeasurement("FT-301", 50.0);
    ControllerDeviceBaseClass primary = createController("TIC-300", temperatureMeasurement, 50.0, 50.0, 8.0, 0.0, true);
    ControllerDeviceBaseClass secondary = createController("FIC-301", flowMeasurement, 50.0, 50.0, 2.0, 0.0, true);
    CascadeControllerStructure cascade = new CascadeControllerStructure(primary, secondary);
    double utilityFlow = 50.0;
    double temperature = 50.0;
    double disturbance = 0.0;
    trace.record(0, 0.0, temperature, 50.0, secondary.getResponse());
    for (int step = 1; step <= steps; step++) {
      double time = step * TIME_STEP_SECONDS;
      if (time >= 30.0) {
        primary.setControllerSetPoint(52.0, "percent");
      }
      if (time >= 150.0) {
        disturbance = -2.0;
      }
      temperatureMeasurement.setValue(temperature);
      flowMeasurement.setValue(utilityFlow);
      cascade.runTransient(TIME_STEP_SECONDS);
      double valve = cascade.getOutput();
      utilityFlow += TIME_STEP_SECONDS / 5.0 * (valve - utilityFlow);
      temperature += TIME_STEP_SECONDS / 35.0 * (utilityFlow + disturbance - temperature);
      trace.record(step, time, temperature, primary.getControllerSetPoint(), valve);
    }
    double finalError = relativeErrorPercent(temperature, primary.getControllerSetPoint());
    boolean passed = finalError <= 2.0 && maximum(trace.controllerOutput) <= 100.0
        && minimum(trace.controllerOutput) >= 0.0;
    return trace.result("control_cascade_temperature", "Cascade temperature and utility flow",
        ChallengeType.SET_POINT_AND_DISTURBANCE, finalError, passed,
        "final relative error <= 2%; inner-loop valve remains bounded");
  }

  private static CaseResult runSplitRangeCase() {
    int steps = 240;
    Trace trace = new Trace(steps);
    MutableMeasurement measurement = new MutableMeasurement("PT-400", 50.0);
    ControllerDeviceBaseClass controller = createController("PIC-400", measurement, 50.0, 50.0, 20.0, 0.0, true);
    SplitRangeControllerStructure splitRange = new SplitRangeControllerStructure(controller, 2);
    double processValue = 50.0;
    double disturbance = 0.0;
    boolean secondElementOpened = false;
    trace.record(0, 0.0, processValue, 50.0, controller.getResponse());
    for (int step = 1; step <= steps; step++) {
      double time = step * TIME_STEP_SECONDS;
      if (time >= 40.0 && time < 150.0) {
        disturbance = -20.0;
      }
      if (time >= 150.0) {
        disturbance = -10.0;
      }
      measurement.setValue(processValue);
      splitRange.runTransient(TIME_STEP_SECONDS);
      double firstElement = splitRange.getOutput(0);
      double secondElement = splitRange.getOutput(1);
      secondElementOpened |= secondElement > 1.0;
      double capacity = 0.5 * firstElement + 0.5 * secondElement;
      processValue += TIME_STEP_SECONDS / 20.0 * (capacity + disturbance - processValue);
      trace.record(step, time, processValue, controller.getControllerSetPoint(), splitRange.getOutput());
    }
    double finalError = relativeErrorPercent(processValue, controller.getControllerSetPoint());
    boolean passed = finalError <= 2.0 && secondElementOpened && maximum(trace.controllerOutput) <= 100.0;
    return trace.result("control_split_range", "Sequential split-range capacity", ChallengeType.DISTURBANCE, finalError,
        passed, "final relative error <= 2%; second final element is exercised");
  }

  private static CaseResult runAntiSurgeCase() {
    AntiSurgeDynamicBenchmark closedLoop = new AntiSurgeDynamicBenchmark();
    closedLoop.run(true);
    AntiSurgeDynamicBenchmark openLoop = new AntiSurgeDynamicBenchmark();
    openLoop.run(false);
    double[] margin = closedLoop.getSurgeMarginTrace();
    double[] valve = closedLoop.getValveOpeningTrace();
    double[] time = new double[margin.length];
    double[] setPoint = new double[margin.length];
    for (int i = 0; i < margin.length; i++) {
      time[i] = i * TIME_STEP_SECONDS;
      setPoint[i] = 0.10;
    }
    double marginShortfallPercent = Math.max(0.0, 0.01 - closedLoop.getMinimumSurgeMargin()) * 100.0;
    boolean passed = closedLoop.isSurgeAvoided() && openLoop.getMinimumSurgeMargin() < 0.0
        && closedLoop.getMaximumValveOpening() > 5.0;
    return new CaseResult("control_anti_surge", "Anti-surge recycle protection", ChallengeType.PROTECTION, time, margin,
        setPoint, valve, marginShortfallPercent, passed,
        "closed loop keeps positive margin while the controller-disabled reference crosses surge");
  }

  private static CaseResult runSpeedRecycleCoordinationCase() {
    int steps = 360;
    Trace trace = new Trace(steps);
    double initialPressure = 81.25;
    MutableMeasurement pressureMeasurement = new MutableMeasurement("PT-500", initialPressure);
    ControllerDeviceBaseClass pressureController = createController("PIC-500", pressureMeasurement, initialPressure,
        75.0, 6.0, 0.0, true);
    MutableMeasurement protectionMeasurement = new MutableMeasurement("PROTECTION", 0.0);
    ControllerDeviceBaseClass antiSurge = createManualController("ASC-500", protectionMeasurement, 0.0);
    ControllerDeviceBaseClass suctionPressure = createManualController("PSL-500", protectionMeasurement, 0.0);
    MinimumSpeedRecycleControllerStructure coordination = new MinimumSpeedRecycleControllerStructure(pressureController,
        antiSurge, suctionPressure, 60.0, 100.0, 70.0, 100.0);
    double pressure = initialPressure;
    double disturbance = 0.0;
    boolean recycleRangeEntered = false;
    boolean speedRangeEntered = false;
    boolean protectionSelected = false;
    trace.record(0, 0.0, pressure, initialPressure, pressureController.getResponse());
    for (int step = 1; step <= steps; step++) {
      double time = step * TIME_STEP_SECONDS;
      if (time >= 50.0 && time < 140.0) {
        disturbance = -12.0;
      } else if (time >= 140.0) {
        disturbance = 18.0;
      }
      if (time >= 210.0) {
        antiSurge.setManualOutput(35.0);
      }
      pressureMeasurement.setValue(pressure);
      coordination.runTransient(TIME_STEP_SECONDS);
      recycleRangeEntered |= coordination.isRecycleControlActive();
      speedRangeEntered |= coordination.getSpeedOutput() > 70.5;
      protectionSelected |= time >= 210.0 && coordination.getOutput() >= 35.0;
      double processTarget = coordination.getSpeedOutput() - 0.45 * coordination.getOutput() + disturbance;
      pressure += TIME_STEP_SECONDS / 20.0 * (processTarget - pressure);
      trace.record(step, time, pressure, pressureController.getControllerSetPoint(), pressureController.getResponse());
    }
    double finalError = relativeErrorPercent(pressure, pressureController.getControllerSetPoint());
    boolean passed = finalError <= 2.0 && recycleRangeEntered && speedRangeEntered && protectionSelected;
    return trace.result("control_speed_recycle_coordination", "Compressor minimum-speed and recycle coordination",
        ChallengeType.DISTURBANCE, finalError, passed,
        "final relative error <= 2%; speed, pressure-recycle and independent protection ranges are selected");
  }

  private static ControllerDeviceBaseClass createController(String name, MutableMeasurement measurement,
      double setPoint, double initialOutput, double proportionalGain, double integralTime, boolean reverseActing) {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(name);
    controller.setTransmitter(measurement);
    controller.setUnit(measurement.getUnit());
    controller.setControllerSetPoint(setPoint, measurement.getUnit());
    controller.setControllerParameters(proportionalGain, integralTime, 0.0);
    controller.setReverseActing(reverseActing);
    controller.setOutputLimits(0.0, 100.0);
    controller.setMode(ControllerMode.MANUAL);
    controller.setManualOutput(initialOutput);
    controller.runTransient(initialOutput, TIME_STEP_SECONDS, UUID.randomUUID());
    controller.resetEventLog();
    controller.resetPerformanceMetrics();
    controller.setMode(ControllerMode.AUTO);
    return controller;
  }

  private static ControllerDeviceBaseClass createManualController(String name, MutableMeasurement measurement,
      double output) {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(name);
    controller.setTransmitter(measurement);
    controller.setUnit(measurement.getUnit());
    controller.setMode(ControllerMode.MANUAL);
    controller.setManualOutput(output);
    return controller;
  }

  private static double relativeErrorPercent(double processValue, double setPoint) {
    return Math.abs(processValue - setPoint) / Math.max(Math.abs(setPoint), 1.0) * 100.0;
  }

  private static double calculateOvershootPercent(double[] processValue, double[] setPoint) {
    double maximumOvershoot = 0.0;
    for (int i = 0; i < processValue.length; i++) {
      maximumOvershoot = Math.max(maximumOvershoot,
          (processValue[i] - setPoint[i]) / Math.max(Math.abs(setPoint[i]), 1.0) * 100.0);
    }
    return maximumOvershoot;
  }

  private static double minimum(double[] values) {
    double result = Double.POSITIVE_INFINITY;
    for (double value : values) {
      result = Math.min(result, value);
    }
    return result;
  }

  private static double maximum(double[] values) {
    double result = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      result = Math.max(result, value);
    }
    return result;
  }

  private static final class Trace {
    private final double[] timeSeconds;
    private final double[] processValue;
    private final double[] setPoint;
    private final double[] controllerOutput;

    private Trace(int steps) {
      this.timeSeconds = new double[steps + 1];
      this.processValue = new double[steps + 1];
      this.setPoint = new double[steps + 1];
      this.controllerOutput = new double[steps + 1];
    }

    private void record(int index, double time, double pv, double sp, double output) {
      timeSeconds[index] = time;
      processValue[index] = pv;
      setPoint[index] = sp;
      controllerOutput[index] = output;
    }

    private CaseResult result(String id, String name, ChallengeType challengeType, double agentMetric, boolean passed,
        String acceptanceDetail) {
      return new CaseResult(id, name, challengeType, timeSeconds, processValue, setPoint, controllerOutput, agentMetric,
          passed, acceptanceDetail);
    }
  }

  private static final class MutableMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private double value;

    private MutableMeasurement(String name, double value) {
      super(name, "percent");
      this.value = value;
      setMinimumValue(0.0);
      setMaximumValue(100.0);
    }

    private void setValue(double value) {
      this.value = value;
    }

    @Override
    public double getMeasuredValue(String unit) {
      return value;
    }
  }
}
