package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemPrEos;

/**
 * Reproducible AgentRCA-style benchmark driven by a dynamic NeqSim process.
 *
 * <p>
 * The benchmark trains {@link RcaNormalOperationModel} only on normal operation and evaluates controlled
 * pressure-sensor bias, an export-gas leak, inlet-valve blockage and imposed multiphase slugging excitation. The
 * process is a synthetic gas/liquid feed, inlet valve, dynamic separator and pressure-controlled outlet valves. All
 * values are public, deterministic and intended for testing and teaching rather than equipment design.
 * </p>
 *
 * <p>
 * The slugging case imposes periodic, out-of-phase gas and liquid feed-rate bursts. It exercises a real dynamic
 * separator inventory and its outlet response, but it is not a mechanistic slug-capturing pipe calculation. This
 * distinction avoids claiming validation of hydrodynamic slug initiation, frequency or length.
 * </p>
 *
 * <p>
 * This class reproduces the normal-only baseline, condition-specific evidence and ranked hypothesis-table parts of
 * AgentRCA. It deliberately omits the paper's convolutional autoencoder and language-model inference because the
 * authors' implementation is not public at the time of this benchmark. {@link RcaDiagnosis#toJson()} provides a stable
 * boundary for an optional external reasoning agent.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AgentRcaDynamicProcessBenchmark {
  /** Total upstream feed flow signal in kg/hr. */
  public static final String FEED_TOTAL_FLOW = "feed_total_flow_kg_hr";
  /** Flow downstream of the inlet restriction in kg/hr. */
  public static final String SEPARATOR_INLET_FLOW = "separator_inlet_flow_kg_hr";
  /** Separator pressure-transmitter signal in bara. */
  public static final String SEPARATOR_PRESSURE = "separator_pressure_bara";
  /** Separator liquid level as a fraction of diameter. */
  public static final String SEPARATOR_LEVEL = "separator_liquid_level_fraction";
  /** Export gas flow after the leak junction in kg/hr. */
  public static final String GAS_EXPORT_FLOW = "gas_export_flow_kg_hr";
  /** Liquid export flow in kg/hr. */
  public static final String LIQUID_EXPORT_FLOW = "liquid_export_flow_kg_hr";
  /** Upstream liquid feed flow in kg/hr. */
  public static final String LIQUID_FEED_FLOW = "liquid_feed_flow_kg_hr";

  /** Default window length, matching the 60-s PRONTO window used by AgentRCA. */
  public static final int DEFAULT_WINDOW_SAMPLES = 60;
  /** Default sample interval in seconds. */
  public static final double DEFAULT_TIME_STEP_SECONDS = 1.0;
  private static final int WARMUP_STEPS = 30;
  private static final double BASE_GAS_FLOW_KG_HR = 1000.0;
  private static final double BASE_LIQUID_FLOW_KG_HR = 100.0;

  /**
   * Controlled benchmark states.
   */
  public enum Scenario {
    /** Normal operation. */
    NORMAL,
    /** Constant positive pressure-transmitter bias; the physical process is unchanged. */
    PRESSURE_SENSOR_BIAS,
    /** Progressive diversion of export gas into an unmeasured leak branch. */
    EXPORT_GAS_LEAK,
    /** Progressive loss of effective inlet-valve flow coefficient. */
    INLET_BLOCKAGE,
    /** Periodic out-of-phase gas/liquid feed bursts applied to the dynamic separator. */
    MULTIPHASE_SLUGGING
  }

  /**
   * Runs one default 60-s scenario.
   *
   * @param scenario controlled process state
   * @return scenario result
   */
  public ScenarioRun runScenario(Scenario scenario) {
    return runScenario(scenario, DEFAULT_TIME_STEP_SECONDS, DEFAULT_WINDOW_SAMPLES, 0L);
  }

  /**
   * Runs all controlled scenarios, fitting the normal model only to an independent normal window.
   *
   * @return benchmark result with windows and diagnoses
   */
  public BenchmarkResult runBenchmark() {
    ScenarioRun trainingRun = runScenario(Scenario.NORMAL, DEFAULT_TIME_STEP_SECONDS, DEFAULT_WINDOW_SAMPLES, 0L);
    RcaNormalOperationModel normalModel = RcaNormalOperationModel
        .fit(Collections.singletonList(trainingRun.getWindow()));
    List<RcaFaultHypothesis> hypotheses = createDefaultHypotheses();
    RcaDiagnosisEngine engine = new RcaDiagnosisEngine();
    Map<Scenario, ScenarioRun> runs = new EnumMap<Scenario, ScenarioRun>(Scenario.class);
    Map<Scenario, RcaDiagnosis> diagnoses = new EnumMap<Scenario, RcaDiagnosis>(Scenario.class);

    for (Scenario scenario : Scenario.values()) {
      ScenarioRun run = runScenario(scenario, DEFAULT_TIME_STEP_SECONDS, DEFAULT_WINDOW_SAMPLES,
          100L + scenario.ordinal());
      runs.put(scenario, run);
      diagnoses.put(scenario, engine.diagnose(normalModel, run.getWindow(), hypotheses));
    }
    return new BenchmarkResult(trainingRun, runs, diagnoses);
  }

  /**
   * Creates the physical, zero-shot fault taxonomy used by the benchmark.
   *
   * <p>
   * Rules describe expected directions and dynamics, not fitted faulty examples.
   * </p>
   *
   * @return unmodifiable hypothesis list
   */
  public List<RcaFaultHypothesis> createDefaultHypotheses() {
    List<RcaFaultHypothesis> hypotheses = new ArrayList<RcaFaultHypothesis>();

    hypotheses.add(RcaFaultHypothesis
        .builder(Scenario.NORMAL.name(), "All measured variables remain close to the matched normal regime.")
        .overallRule(RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 4.0,
            "Normal operation should not contain a large multivariate anomaly.")
        .signalRule(SEPARATOR_PRESSURE, RcaFaultHypothesis.Metric.MEAN_Z_SCORE,
            RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 1.0,
            "Separator pressure should remain near its condition-specific baseline.")
        .signalRule(GAS_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.NEAR_ZERO,
            2.0, 1.0, "Gas export should remain near its condition-specific baseline.")
        .signalRule(LIQUID_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE,
            RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 1.0,
            "Liquid export should remain near its condition-specific baseline.")
        .build());

    hypotheses.add(RcaFaultHypothesis
        .builder(Scenario.PRESSURE_SENSOR_BIAS.name(),
            "The pressure transmitter is biased high while related physical flows and level remain normal.")
        .signalRule(SEPARATOR_PRESSURE, RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.POSITIVE,
            3.0, 5.0, "A positive transmitter bias raises only the reported pressure.")
        .signalRule(SEPARATOR_PRESSURE, RcaFaultHypothesis.Metric.LOG_VARIANCE_RATIO,
            RcaFaultHypothesis.Expectation.NEAR_ZERO, Math.log(2.0), 1.0,
            "A constant bias changes the reported mean but should not materially change pressure variance.")
        .signalRule(SEPARATOR_INLET_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE,
            RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 1.0,
            "A sensor-only fault must not alter process inlet flow.")
        .signalRule(GAS_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.NEAR_ZERO,
            2.0, 1.0, "A sensor-only fault must not alter gas export.")
        .signalRule(LIQUID_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE,
            RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 1.0, "A sensor-only fault must not alter liquid export.")
        .build());

    hypotheses.add(RcaFaultHypothesis.builder(Scenario.EXPORT_GAS_LEAK.name(),
        "Export gas is diverted after the gas outlet valve while upstream separator behavior remains close to normal.")
        .signalRule(GAS_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.NEGATIVE,
            3.0, 5.0, "A downstream gas leak reduces delivered export gas.")
        .signalRule(GAS_EXPORT_FLOW, RcaFaultHypothesis.Metric.NORMALIZED_SLOPE,
            RcaFaultHypothesis.Expectation.NEGATIVE, 2.0, 2.0,
            "The controlled leak grows progressively through the evaluation window.")
        .signalRule(SEPARATOR_INLET_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE,
            RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 1.0, "The downstream leak does not restrict separator feed.")
        .signalRule(LIQUID_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE,
            RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 1.0,
            "A gas-only export leak should not directly remove liquid product.")
        .build());

    hypotheses.add(RcaFaultHypothesis
        .builder(Scenario.INLET_BLOCKAGE.name(),
            "Loss of effective inlet-valve flow coefficient restricts both gas and liquid entering the separator.")
        .signalRule(SEPARATOR_INLET_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE,
            RcaFaultHypothesis.Expectation.NEGATIVE, 3.0, 5.0, "An inlet restriction reduces total separator feed.")
        .signalRule(GAS_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.NEGATIVE,
            3.0, 2.0, "Reduced separator feed propagates to gas export.")
        .signalRule(LIQUID_EXPORT_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.NEGATIVE,
            3.0, 2.0, "Reduced separator feed propagates to liquid export.")
        .signalRule(FEED_TOTAL_FLOW, RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.NEAR_ZERO,
            2.0, 1.0, "Upstream feed setpoints remain unchanged while the restriction grows.")
        .build());

    hypotheses.add(RcaFaultHypothesis
        .builder(Scenario.MULTIPHASE_SLUGGING.name(),
            "Periodic gas/liquid feed bursts create a coherent high-variance response across feed, level and exports.")
        .signalRule(LIQUID_FEED_FLOW, RcaFaultHypothesis.Metric.LOG_VARIANCE_RATIO,
            RcaFaultHypothesis.Expectation.POSITIVE, Math.log(4.0), 4.0,
            "Imposed liquid slugs strongly increase inlet liquid-flow variance.")
        .signalRule(SEPARATOR_LEVEL, RcaFaultHypothesis.Metric.LOG_VARIANCE_RATIO,
            RcaFaultHypothesis.Expectation.POSITIVE, Math.log(2.0), 2.0,
            "Dynamic separator inventory responds to liquid feed bursts.")
        .signalRule(LIQUID_EXPORT_FLOW, RcaFaultHypothesis.Metric.LOG_VARIANCE_RATIO,
            RcaFaultHypothesis.Expectation.POSITIVE, Math.log(2.0), 2.0,
            "The liquid outlet carries a delayed oscillatory response.")
        .correlationRule(LIQUID_FEED_FLOW, SEPARATOR_LEVEL, RcaFaultHypothesis.Expectation.LARGE_ABSOLUTE, 0.3, 1.0,
            "Slugging changes the normal coupling between liquid feed and vessel inventory.")
        .overallRule(RcaFaultHypothesis.Expectation.POSITIVE, 1.0, 1.0,
            "Slugging produces a distributed multivariate anomaly, not an isolated noisy tag.")
        .build());
    return Collections.unmodifiableList(hypotheses);
  }

  ScenarioRun runScenario(Scenario scenario, double timeStepSeconds, int sampleCount, long seedOffset) {
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("timeStepSeconds must be finite and > 0");
    }
    if (sampleCount < 3) {
      throw new IllegalArgumentException("sampleCount must be at least three");
    }

    ProcessFixture fixture = createProcess(seedOffset);
    for (int i = 0; i < WARMUP_STEPS; i++) {
      setNormalFeeds(fixture);
      fixture.process.runTransient(timeStepSeconds, UUID.randomUUID());
    }
    if (scenario == Scenario.PRESSURE_SENSOR_BIAS) {
      fixture.pressureTransmitter.setFault(SensorFaultType.BIAS, 2.0);
    }

    double[] feedTotalFlow = new double[sampleCount];
    double[] separatorInletFlow = new double[sampleCount];
    double[] separatorPressure = new double[sampleCount];
    double[] separatorLevel = new double[sampleCount];
    double[] gasExportFlow = new double[sampleCount];
    double[] liquidExportFlow = new double[sampleCount];
    double[] liquidFeedFlow = new double[sampleCount];
    double leakedMassKg = 0.0;

    for (int i = 0; i < sampleCount; i++) {
      double severity = (i + 1.0) / sampleCount;
      applyScenario(fixture, scenario, severity, i);
      fixture.process.runTransient(timeStepSeconds, UUID.randomUUID());

      feedTotalFlow[i] = fixture.gasFeed.getFlowRate("kg/hr") + fixture.liquidFeed.getFlowRate("kg/hr");
      separatorInletFlow[i] = fixture.inletValve.getOutletStream().getFlowRate("kg/hr");
      separatorPressure[i] = fixture.pressureTransmitter.getMeasuredValue("bara");
      separatorLevel[i] = fixture.levelTransmitter.getMeasuredValue("");
      gasExportFlow[i] = fixture.exportSplitter.getSplitStream(0).getFlowRate("kg/hr");
      liquidExportFlow[i] = fixture.liquidOutletValve.getOutletStream().getFlowRate("kg/hr");
      liquidFeedFlow[i] = fixture.liquidFeed.getFlowRate("kg/hr");
      double leakFlow = fixture.exportSplitter.getSplitStream(1).getFlowRate("kg/hr");
      leakedMassKg += leakFlow * timeStepSeconds / 3600.0;
      validatePhysicalState(fixture, separatorPressure[i], separatorLevel[i], separatorInletFlow[i], gasExportFlow[i],
          liquidExportFlow[i], leakFlow);
    }

    RcaProcessWindow window = RcaProcessWindow.builder(scenario.name(), timeStepSeconds)
        .operatingCondition("gas_feed_setpoint_kg_hr", BASE_GAS_FLOW_KG_HR)
        .operatingCondition("liquid_feed_setpoint_kg_hr", BASE_LIQUID_FLOW_KG_HR).signal(FEED_TOTAL_FLOW, feedTotalFlow)
        .signal(SEPARATOR_INLET_FLOW, separatorInletFlow).signal(SEPARATOR_PRESSURE, separatorPressure)
        .signal(SEPARATOR_LEVEL, separatorLevel).signal(GAS_EXPORT_FLOW, gasExportFlow)
        .signal(LIQUID_EXPORT_FLOW, liquidExportFlow).signal(LIQUID_FEED_FLOW, liquidFeedFlow).build();
    return new ScenarioRun(scenario, window, leakedMassKg, fixture.inletValve.getFoulingFraction(),
        fixture.separator.getGasOutStream().getPressure("bara"), fixture.separator.getLiquidLevel());
  }

  private static void setNormalFeeds(ProcessFixture fixture) {
    fixture.gasFeed.setFlowRate(BASE_GAS_FLOW_KG_HR, "kg/hr");
    fixture.liquidFeed.setFlowRate(BASE_LIQUID_FLOW_KG_HR, "kg/hr");
    fixture.exportSplitter.setSplitFactors(new double[] { 1.0, 0.0 });
    fixture.inletValve.setFoulingFraction(0.0);
  }

  private static void applyScenario(ProcessFixture fixture, Scenario scenario, double severity, int sampleIndex) {
    setNormalFeeds(fixture);
    switch (scenario) {
    case EXPORT_GAS_LEAK:
      double leakFraction = 0.25 * severity;
      fixture.exportSplitter.setSplitFactors(new double[] { 1.0 - leakFraction, leakFraction });
      break;
    case INLET_BLOCKAGE:
      fixture.inletValve.setFoulingFraction(0.75 * severity);
      break;
    case MULTIPHASE_SLUGGING:
      double wave = Math.sin(2.0 * Math.PI * sampleIndex / 12.0);
      double amplitude = 0.15 + 0.70 * severity;
      fixture.liquidFeed.setFlowRate(Math.max(10.0, BASE_LIQUID_FLOW_KG_HR * (1.0 + amplitude * wave)), "kg/hr");
      fixture.gasFeed.setFlowRate(BASE_GAS_FLOW_KG_HR * (1.0 - 0.20 * amplitude * wave), "kg/hr");
      break;
    case NORMAL:
    case PRESSURE_SENSOR_BIAS:
    default:
      break;
    }
  }

  private static ProcessFixture createProcess(long seedOffset) {
    SystemPrEos gasFluid = new SystemPrEos(298.15, 35.0);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.10);
    gasFluid.setMixingRule("classic");
    gasFluid.setMultiPhaseCheck(true);

    SystemPrEos liquidFluid = new SystemPrEos(298.15, 35.0);
    liquidFluid.addComponent("nC10", 1.0);
    liquidFluid.setMixingRule("classic");
    liquidFluid.setMultiPhaseCheck(true);

    Stream gasFeed = new Stream("gas feed", gasFluid);
    gasFeed.setFlowRate(BASE_GAS_FLOW_KG_HR, "kg/hr");
    gasFeed.setTemperature(25.0, "C");
    gasFeed.setPressure(35.0, "bara");
    gasFeed.run();

    Stream liquidFeed = new Stream("liquid feed", liquidFluid);
    liquidFeed.setFlowRate(BASE_LIQUID_FLOW_KG_HR, "kg/hr");
    liquidFeed.setTemperature(25.0, "C");
    liquidFeed.setPressure(35.0, "bara");
    liquidFeed.run();

    Mixer inletMixer = new Mixer("multiphase inlet mixer");
    inletMixer.addStream(gasFeed);
    inletMixer.addStream(liquidFeed);
    inletMixer.run();

    ThrottlingValve inletValve = new ThrottlingValve("inlet restriction", inletMixer.getOutletStream());
    inletValve.setOutletPressure(20.0, "bara");
    inletValve.setCalculateSteadyState(false);

    Separator separator = new Separator("dynamic inlet separator", inletValve.getOutletStream());
    separator.setOrientation("horizontal");
    separator.setSeparatorLength(4.0);
    separator.setInternalDiameter(1.0);
    separator.setLiquidLevel(0.35);
    separator.setCalculateSteadyState(false);

    ThrottlingValve gasOutletValve = new ThrottlingValve("gas outlet valve", separator.getGasOutStream());
    gasOutletValve.setOutletPressure(5.0, "bara");
    gasOutletValve.setCalculateSteadyState(false);

    Splitter exportSplitter = new Splitter("export gas leak junction", gasOutletValve.getOutletStream(), 2);
    exportSplitter.setSplitFactors(new double[] { 1.0, 0.0 });

    ThrottlingValve liquidOutletValve = new ThrottlingValve("liquid outlet valve", separator.getLiquidOutStream());
    liquidOutletValve.setOutletPressure(5.0, "bara");
    liquidOutletValve.setCalculateSteadyState(false);

    PressureTransmitter pressureTransmitter = new PressureTransmitter("separator pressure transmitter",
        separator.getGasOutStream());
    pressureTransmitter.setUnit("bara");
    pressureTransmitter.setNoiseStdDev(0.02);
    pressureTransmitter.setRandomSeed(87123L + seedOffset);
    LevelTransmitter levelTransmitter = new LevelTransmitter("separator level transmitter", separator);

    ProcessSystem process = new ProcessSystem("AgentRCA dynamic fault benchmark");
    process.add(gasFeed);
    process.add(liquidFeed);
    process.add(inletMixer);
    process.add(inletValve);
    process.add(separator);
    process.add(gasOutletValve);
    process.add(exportSplitter);
    process.add(liquidOutletValve);
    process.add(pressureTransmitter);
    process.add(levelTransmitter);
    process.run();
    process.storeInitialState();
    process.setTimeStep(DEFAULT_TIME_STEP_SECONDS);
    return new ProcessFixture(process, gasFeed, liquidFeed, inletValve, separator, gasOutletValve, liquidOutletValve,
        exportSplitter, pressureTransmitter, levelTransmitter);
  }

  private static void validatePhysicalState(ProcessFixture fixture, double pressureBara, double level,
      double inletFlowKgHr, double gasExportFlowKgHr, double liquidExportFlowKgHr, double leakFlowKgHr) {
    if (!Double.isFinite(pressureBara) || pressureBara <= 0.0) {
      throw new IllegalStateException("separator pressure must remain finite and positive");
    }
    if (!Double.isFinite(level) || level < 0.0 || level > 1.0) {
      throw new IllegalStateException("separator liquid level must remain within [0, 1]");
    }
    double[] flows = { inletFlowKgHr, gasExportFlowKgHr, liquidExportFlowKgHr, leakFlowKgHr };
    for (double flow : flows) {
      if (!Double.isFinite(flow) || flow < -1.0e-9) {
        throw new IllegalStateException("all process flows must remain finite and non-negative");
      }
    }
    if (!Double.isFinite(fixture.inletValve.getEffectiveKv()) || fixture.inletValve.getEffectiveKv() <= 0.0) {
      throw new IllegalStateException("inlet valve effective Kv must remain finite and positive");
    }
  }

  private static final class ProcessFixture {
    private final ProcessSystem process;
    private final Stream gasFeed;
    private final Stream liquidFeed;
    private final ThrottlingValve inletValve;
    private final Separator separator;
    private final ThrottlingValve gasOutletValve;
    private final ThrottlingValve liquidOutletValve;
    private final Splitter exportSplitter;
    private final PressureTransmitter pressureTransmitter;
    private final LevelTransmitter levelTransmitter;

    private ProcessFixture(ProcessSystem process, Stream gasFeed, Stream liquidFeed, ThrottlingValve inletValve,
        Separator separator, ThrottlingValve gasOutletValve, ThrottlingValve liquidOutletValve, Splitter exportSplitter,
        PressureTransmitter pressureTransmitter, LevelTransmitter levelTransmitter) {
      this.process = process;
      this.gasFeed = gasFeed;
      this.liquidFeed = liquidFeed;
      this.inletValve = inletValve;
      this.separator = separator;
      this.gasOutletValve = gasOutletValve;
      this.liquidOutletValve = liquidOutletValve;
      this.exportSplitter = exportSplitter;
      this.pressureTransmitter = pressureTransmitter;
      this.levelTransmitter = levelTransmitter;
    }
  }

  /**
   * One controlled dynamic process run.
   */
  public static final class ScenarioRun implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final Scenario scenario;
    private final RcaProcessWindow window;
    private final double leakedMassKg;
    private final double finalInletValveFoulingFraction;
    private final double finalSeparatorPressureBara;
    private final double finalSeparatorLevel;

    private ScenarioRun(Scenario scenario, RcaProcessWindow window, double leakedMassKg,
        double finalInletValveFoulingFraction, double finalSeparatorPressureBara, double finalSeparatorLevel) {
      this.scenario = scenario;
      this.window = window;
      this.leakedMassKg = leakedMassKg;
      this.finalInletValveFoulingFraction = finalInletValveFoulingFraction;
      this.finalSeparatorPressureBara = finalSeparatorPressureBara;
      this.finalSeparatorLevel = finalSeparatorLevel;
    }

    /**
     * Returns the controlled state.
     *
     * @return scenario
     */
    public Scenario getScenario() {
      return scenario;
    }

    /**
     * Returns the measured process window.
     *
     * @return process window
     */
    public RcaProcessWindow getWindow() {
      return window;
    }

    /**
     * Returns integrated mass diverted through the leak branch.
     *
     * @return leaked mass in kg
     */
    public double getLeakedMassKg() {
      return leakedMassKg;
    }

    /**
     * Returns the final inlet-valve fouling fraction.
     *
     * @return fouling fraction
     */
    public double getFinalInletValveFoulingFraction() {
      return finalInletValveFoulingFraction;
    }

    /**
     * Returns the final separator pressure.
     *
     * @return pressure in bara
     */
    public double getFinalSeparatorPressureBara() {
      return finalSeparatorPressureBara;
    }

    /**
     * Returns the final separator liquid level.
     *
     * @return level fraction
     */
    public double getFinalSeparatorLevel() {
      return finalSeparatorLevel;
    }
  }

  /**
   * Complete normal-only benchmark result.
   */
  public static final class BenchmarkResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final ScenarioRun normalTrainingRun;
    private final Map<Scenario, ScenarioRun> scenarioRuns;
    private final Map<Scenario, RcaDiagnosis> diagnoses;

    private BenchmarkResult(ScenarioRun normalTrainingRun, Map<Scenario, ScenarioRun> scenarioRuns,
        Map<Scenario, RcaDiagnosis> diagnoses) {
      this.normalTrainingRun = normalTrainingRun;
      this.scenarioRuns = Collections.unmodifiableMap(new EnumMap<Scenario, ScenarioRun>(scenarioRuns));
      this.diagnoses = Collections.unmodifiableMap(new EnumMap<Scenario, RcaDiagnosis>(diagnoses));
    }

    /**
     * Returns the independent normal-only training window.
     *
     * @return normal training run
     */
    public ScenarioRun getNormalTrainingRun() {
      return normalTrainingRun;
    }

    /**
     * Returns all evaluated scenario runs.
     *
     * @return unmodifiable scenario map
     */
    public Map<Scenario, ScenarioRun> getScenarioRuns() {
      return scenarioRuns;
    }

    /**
     * Returns all ranked diagnoses.
     *
     * @return unmodifiable diagnosis map
     */
    public Map<Scenario, RcaDiagnosis> getDiagnoses() {
      return diagnoses;
    }

    /**
     * Returns one diagnosis.
     *
     * @param scenario scenario
     * @return diagnosis
     */
    public RcaDiagnosis getDiagnosis(Scenario scenario) {
      return diagnoses.get(scenario);
    }
  }
}
