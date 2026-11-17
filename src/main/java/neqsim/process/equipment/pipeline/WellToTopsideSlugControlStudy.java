package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.OilLevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.WaterLevelTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reproducible wellhead-choke, flowline/riser and inlet-separator slug-control study.
 *
 * <p>
 * The pipe uses the fixed pressure outlet qualified by the {@code #5036} regression. The separator pressure controller
 * is independently centred on that pressure. Updating the pipe pressure boundary from the live separator pressure is
 * deliberately excluded: that API route is not covered by the fixed-boundary phase-ownership qualification and can
 * suppress liquid through-flow after millibar-scale boundary changes.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class WellToTopsideSlugControlStudy {
  /** Qualified riser outlet pressure in bara. */
  public static final double RISER_OUTLET_PRESSURE_BARA = 56.40099362;
  /** Default public replay duration in seconds. */
  public static final double DEFAULT_DURATION_SECONDS = 90.0;
  /** Default reporting interval in seconds. */
  public static final double DEFAULT_TIME_STEP_SECONDS = 0.25;
  private static final Phase[] PHASES = {Phase.GAS, Phase.OIL, Phase.WATER};

  /** Prevents instantiation. */
  private WellToTopsideSlugControlStudy() {
  }

  /** One sampled point in the published replay. */
  public static final class Sample {
    private final double time_s;
    private final double supplyPressure_bara;
    private final double chokeOpening_percent;
    private final double production_kg_s;
    private final double riserLiquid_kg_s;
    private final double separatorPressure_bara;
    private final double totalLiquidLevel_m;
    private final double oilWaterInterface_m;
    private final double pipeResidual_kg;

    private Sample(double time, double supplyPressure, double chokeOpening, double production, double riserLiquid,
        double separatorPressure, double totalLiquidLevel, double oilWaterInterface, double pipeResidual) {
      time_s = time;
      supplyPressure_bara = supplyPressure;
      chokeOpening_percent = chokeOpening;
      production_kg_s = production;
      riserLiquid_kg_s = riserLiquid;
      separatorPressure_bara = separatorPressure;
      totalLiquidLevel_m = totalLiquidLevel;
      oilWaterInterface_m = oilWaterInterface;
      pipeResidual_kg = pipeResidual;
    }
  }

  /** Metrics and time series from one open-loop or regulated replay. */
  public static final class ReplayResult {
    private final String mode;
    private final int cells;
    private final double timeStep_s;
    private final double duration_s;
    private final double analysisStart_s;
    private final double meanProduction_kg_s;
    private final double liquidPeak_kg_s;
    private final double separatorPressureMinimum_bara;
    private final double separatorPressureMaximum_bara;
    private final double totalLiquidLevelMinimum_m;
    private final double totalLiquidLevelMaximum_m;
    private final double oilWaterInterfaceMinimum_m;
    private final double oilWaterInterfaceMaximum_m;
    private final double maximumPipeResidual_kg;
    private final List<Sample> timeSeries;

    private ReplayResult(String replayMode, int numberOfCells, double timeStep, double duration, double analysisStart,
        double meanProduction, double liquidPeak, double minimumPressure, double maximumPressure,
        double minimumLiquidLevel, double maximumLiquidLevel, double minimumInterface, double maximumInterface,
        double maximumPipeResidual, List<Sample> samples) {
      mode = replayMode;
      cells = numberOfCells;
      timeStep_s = timeStep;
      duration_s = duration;
      analysisStart_s = analysisStart;
      meanProduction_kg_s = meanProduction;
      liquidPeak_kg_s = liquidPeak;
      separatorPressureMinimum_bara = minimumPressure;
      separatorPressureMaximum_bara = maximumPressure;
      totalLiquidLevelMinimum_m = minimumLiquidLevel;
      totalLiquidLevelMaximum_m = maximumLiquidLevel;
      oilWaterInterfaceMinimum_m = minimumInterface;
      oilWaterInterfaceMaximum_m = maximumInterface;
      maximumPipeResidual_kg = maximumPipeResidual;
      timeSeries = Collections.unmodifiableList(new ArrayList<Sample>(samples));
    }
  }

  /** Paired replay and matched-production comparison metrics. */
  public static final class ComparisonResult {
    private final String caseId;
    private final String outletBoundaryMode;
    private final ReplayResult openLoop;
    private final ReplayResult regulated;
    private final double meanProductionRelativeDifference;
    private final double liquidPeakRelativeChange;
    private final double separatorPressureRangeRelativeChange;
    private final double totalLiquidLevelRangeRelativeChange;
    private final double oilWaterInterfaceRangeRelativeChange;

    private ComparisonResult(String replayCaseId, String boundaryMode, ReplayResult open, ReplayResult closed) {
      caseId = replayCaseId;
      outletBoundaryMode = boundaryMode;
      openLoop = open;
      regulated = closed;
      meanProductionRelativeDifference = relativeDifference(open.meanProduction_kg_s, closed.meanProduction_kg_s);
      liquidPeakRelativeChange = relativeChange(open.liquidPeak_kg_s, closed.liquidPeak_kg_s);
      separatorPressureRangeRelativeChange = relativeChange(
          range(open.separatorPressureMinimum_bara, open.separatorPressureMaximum_bara),
          range(closed.separatorPressureMinimum_bara, closed.separatorPressureMaximum_bara));
      totalLiquidLevelRangeRelativeChange = relativeChange(
          range(open.totalLiquidLevelMinimum_m, open.totalLiquidLevelMaximum_m),
          range(closed.totalLiquidLevelMinimum_m, closed.totalLiquidLevelMaximum_m));
      oilWaterInterfaceRangeRelativeChange = relativeChange(
          range(open.oilWaterInterfaceMinimum_m, open.oilWaterInterfaceMaximum_m),
          range(closed.oilWaterInterfaceMinimum_m, closed.oilWaterInterfaceMaximum_m));
    }
  }

  /**
   * Run open-loop and regulated cases with identical replay inputs.
   *
   * @param durationSeconds replay duration in seconds
   * @param timeStepSeconds reporting time step in seconds
   * @param meshRefinement one for 60 cells or two for 120 cells
   * @param sampleIntervalSeconds time-series sampling interval in seconds
   * @return paired replay result
   */
  public static ComparisonResult runComparison(double durationSeconds, double timeStepSeconds, int meshRefinement,
      double sampleIntervalSeconds) {
    ReplayResult open = runReplay(false, durationSeconds, timeStepSeconds, meshRefinement, sampleIntervalSeconds);
    ReplayResult regulated = runReplay(true, durationSeconds, timeStepSeconds, meshRefinement, sampleIntervalSeconds);
    return new ComparisonResult("neqsim-5036-well-to-topside", "qualified fixed pressure", open, regulated);
  }

  /**
   * Run one deterministic synthetic replay.
   *
   * @param regulated true to activate the wellhead-choke liquid-level controller
   * @param durationSeconds replay duration in seconds
   * @param timeStepSeconds reporting time step in seconds
   * @param meshRefinement one for 60 cells or two for 120 cells
   * @param sampleIntervalSeconds time-series sampling interval in seconds
   * @return replay metrics and samples
   */
  public static ReplayResult runReplay(boolean regulated, double durationSeconds, double timeStepSeconds,
      int meshRefinement, double sampleIntervalSeconds) {
    validateInputs(durationSeconds, timeStepSeconds, meshRefinement, sampleIntervalSeconds);
    Stream upstream = createInlet();
    upstream.setPressure(80.0, "bara");
    upstream.run();
    ThrottlingValve choke = createChoke(upstream);
    TwoFluidPipe pipe = createPipe((Stream) choke.getOutletStream(), meshRefinement);
    pipe.run();
    if (!pipe.isSteadyStateConverged()) {
      throw new IllegalStateException(pipe.getSteadyStateConvergenceReport().toString());
    }

    ThreePhaseSeparator separator = createSeparator(pipe);
    OilLevelTransmitter liquidLevel = new OilLevelTransmitter("Inlet separator total liquid level", separator);
    WaterLevelTransmitter interfaceLevel = new WaterLevelTransmitter("Inlet separator oil-water interface", separator);
    PressureTransmitter pressure = new PressureTransmitter("Inlet separator pressure", separator.getGasOutStream());
    pressure.setUnit("bara");
    double liquidSetPoint = liquidLevel.getMeasuredValue("m");
    double interfaceSetPoint = interfaceLevel.getMeasuredValue("m");
    double pressureSetPoint = pressure.getMeasuredValue("bara");
    ControllerDeviceBaseClass oilControl = controller("Inlet separator liquid controller", liquidLevel, "m",
        liquidSetPoint, 1000.0, 60.0, false, 5.0, 100.0);
    ControllerDeviceBaseClass waterControl = controller("Inlet separator interface controller", interfaceLevel, "m",
        interfaceSetPoint, 1000.0, 60.0, false, 5.0, 100.0);
    ControllerDeviceBaseClass pressureControl = controller("Inlet separator pressure controller", pressure, "bara",
        pressureSetPoint, 20.0, 20.0, false, 5.0, 100.0);
    ControllerDeviceBaseClass slugControl = controller("Wellhead anti-slug controller", liquidLevel, "m",
        liquidSetPoint - 0.01, 1000.0, 60.0, true, 48.0, 58.0);

    double analysisStart = 0.25 * durationSeconds;
    int steps = (int) Math.round(durationSeconds / timeStepSeconds);
    int sampleStride = Math.max(1, (int) Math.round(sampleIntervalSeconds / timeStepSeconds));
    List<Sample> samples = new ArrayList<Sample>();
    double productionIntegral = 0.0;
    double analysisDuration = 0.0;
    double liquidPeak = 0.0;
    double minimumPressure = Double.POSITIVE_INFINITY;
    double maximumPressure = Double.NEGATIVE_INFINITY;
    double minimumLiquid = Double.POSITIVE_INFINITY;
    double maximumLiquid = Double.NEGATIVE_INFINITY;
    double minimumInterface = Double.POSITIVE_INFINITY;
    double maximumInterface = Double.NEGATIVE_INFINITY;
    double maximumResidual = 0.0;

    for (int step = 0; step < steps; step++) {
      double time = step * timeStepSeconds;
      double supplyPressure = 80.0 + 2.0 * Math.sin(2.0 * Math.PI * time / 45.0);
      upstream.setPressure(supplyPressure, "bara");
      upstream.run();
      UUID id = UUID.nameUUIDFromBytes((regulated + "-" + step).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      if (regulated) {
        slugControl.runTransient(choke.getPercentValveOpening(), timeStepSeconds, id);
        choke.setPercentValveOpening(slugControl.getResponse());
      }
      if (step > 0) {
        choke.getOutletStream().setPressure(pipe.getInletFacePressure("bara"), "bara");
        choke.runTransient(timeStepSeconds, id);
      }
      pipe.setOutletPressure(RISER_OUTLET_PRESSURE_BARA, "bara");
      pipe.runTransient(timeStepSeconds, id);
      runSeparatorControls(separator, oilControl, waterControl, pressureControl, timeStepSeconds, id);
      separator.runTransient(timeStepSeconds, id);

      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      double liquidRate = (report.getOutletMassKg(Phase.OIL) + report.getOutletMassKg(Phase.WATER)) / timeStepSeconds;
      double residual = 0.0;
      for (Phase phase : PHASES) {
        residual = Math.max(residual, Math.abs(report.getResidualKg(phase)));
      }
      maximumResidual = Math.max(maximumResidual, residual);
      if (time >= analysisStart) {
        double production = report.getInletMassKg(Phase.TOTAL) / timeStepSeconds;
        productionIntegral += production * timeStepSeconds;
        analysisDuration += timeStepSeconds;
        liquidPeak = Math.max(liquidPeak, liquidRate);
        minimumPressure = Math.min(minimumPressure, separator.getPressure("bara"));
        maximumPressure = Math.max(maximumPressure, separator.getPressure("bara"));
        minimumLiquid = Math.min(minimumLiquid, separator.getOilLevel());
        maximumLiquid = Math.max(maximumLiquid, separator.getOilLevel());
        minimumInterface = Math.min(minimumInterface, separator.getWaterLevel());
        maximumInterface = Math.max(maximumInterface, separator.getWaterLevel());
      }
      if (step % sampleStride == 0 || step == steps - 1) {
        samples.add(new Sample(time, supplyPressure, choke.getPercentValveOpening(),
            report.getInletMassKg(Phase.TOTAL) / timeStepSeconds, liquidRate, separator.getPressure("bara"),
            separator.getOilLevel(), separator.getWaterLevel(), residual));
      }
    }
    return new ReplayResult(regulated ? "regulated" : "open-loop", 60 * meshRefinement, timeStepSeconds,
        durationSeconds, analysisStart, productionIntegral / analysisDuration, liquidPeak, minimumPressure,
        maximumPressure, minimumLiquid, maximumLiquid, minimumInterface, maximumInterface, maximumResidual, samples);
  }

  private static void runSeparatorControls(ThreePhaseSeparator separator, ControllerDeviceBaseClass oilControl,
      ControllerDeviceBaseClass waterControl, ControllerDeviceBaseClass pressureControl, double timeStep, UUID id) {
    oilControl.runTransient(100.0 * separator.getOilOutletFlowFraction(), timeStep, id);
    waterControl.runTransient(100.0 * separator.getWaterOutletFlowFraction(), timeStep, id);
    pressureControl.runTransient(100.0 * separator.getGasOutletFlowFraction(), timeStep, id);
    separator.setOilOutletFlowFraction(oilControl.getResponse() / 100.0);
    separator.setWaterOutletFlowFraction(waterControl.getResponse() / 100.0);
    separator.setGasOutletFlowFraction(pressureControl.getResponse() / 100.0);
  }

  private static ThreePhaseSeparator createSeparator(TwoFluidPipe pipe) {
    ThreePhaseSeparator separator = new ThreePhaseSeparator("Three-phase inlet separator", pipe.getOutletStream());
    separator.setInternalDiameter(2.2);
    separator.setSeparatorLength(7.0);
    separator.setLiquidLevel(0.5);
    separator.setCalculateSteadyState(false);
    separator.run();
    sizeOutlet(separator.getGasOutStream(), 1.5);
    sizeOutlet(separator.getOilOutStream(), 1.5);
    sizeOutlet(separator.getWaterOutStream(), 1.5);
    separator.setGasOutletFlowFraction(2.0 / 3.0);
    separator.setOilOutletFlowFraction(2.0 / 3.0);
    separator.setWaterOutletFlowFraction(2.0 / 3.0);
    return separator;
  }

  private static void sizeOutlet(neqsim.process.equipment.stream.StreamInterface outlet, double designFactor) {
    outlet.setFlowRate(outlet.getFlowRate("kg/sec") * designFactor, "kg/sec");
  }

  private static ThrottlingValve createChoke(Stream upstream) {
    ThrottlingValve choke = new ThrottlingValve("Wellhead choke", upstream);
    choke.setOutletPressure(70.0, "bara");
    choke.setIsoThermal(true);
    choke.setPercentValveOpening(65.0);
    choke.run();
    double initialCapacity = choke.calculateMolarFlow() * upstream.getFluid().getMolarMass("kg/mol");
    choke.setKv(choke.getKv() * 20.0 / initialCapacity);
    choke.setCalculateSteadyState(false);
    choke.setPercentValveOpening(58.0);
    return choke;
  }

  private static ControllerDeviceBaseClass controller(String name, MeasurementDeviceInterface transmitter, String unit,
      double setPoint, double kp, double ti, boolean reverse, double minimum, double maximum) {
    ControllerDeviceBaseClass result = new ControllerDeviceBaseClass(name);
    result.setTransmitter(transmitter);
    result.setUnit(unit);
    result.setControllerSetPoint(setPoint);
    result.setControllerParameters(kp, ti, 0.0);
    result.setReverseActing(reverse);
    result.setOutputLimits(minimum, maximum);
    return result;
  }

  private static Stream createInlet() {
    SystemInterface fluid = new SystemSrkEos(308.15, 70.0);
    String[] names = {"methane", "ethane", "propane", "n-pentane", "n-heptane", "n-decane", "water"};
    double[] fractions = {0.42, 0.04, 0.03, 0.07, 0.07, 0.05, 0.32};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], fractions[component]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("Synthetic wellhead", fluid);
    inlet.setFlowRate(20.0, "kg/sec");
    inlet.run();
    return inlet;
  }

  private static TwoFluidPipe createPipe(Stream inlet, int refinement) {
    double[] legLengths = {800, 900, 850, 1100, 1350, 400};
    double[] elevations = {-260, -278, -252, -289, -270, -280, 25};
    int[] legCells = {8, 9, 9, 11, 13, 10};
    double[] lengths = new double[60 * refinement];
    double[] faces = new double[lengths.length + 1];
    faces[0] = elevations[0];
    int index = 0;
    for (int leg = 0; leg < legCells.length; leg++) {
      int count = legCells[leg] * refinement;
      for (int cell = 1; cell <= count; cell++) {
        lengths[index] = legLengths[leg] / count;
        faces[++index] = elevations[leg] + (double) cell / count * (elevations[leg + 1] - elevations[leg]);
      }
    }
    TwoFluidPipe pipe = new TwoFluidPipe("Flowline and riser", inlet);
    pipe.setLength(5400.0);
    pipe.setDiameter(0.2032);
    pipe.setRoughness(4.5e-5);
    pipe.setSectionLengths(lengths);
    pipe.setCellFaceElevationProfile(faces);
    pipe.setEnableJouleThomson(false);
    pipe.setEnableWaterOilSlip(true);
    pipe.setOutletPressure(RISER_OUTLET_PRESSURE_BARA, "bara");
    pipe.setAllowOutletPhaseBackflow(false);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(true);
    return pipe;
  }

  private static void validateInputs(double duration, double timeStep, int refinement, double sampleInterval) {
    if (!(duration > 0.0) || !Double.isFinite(duration)) {
      throw new IllegalArgumentException("durationSeconds must be finite and positive");
    }
    if (!(timeStep > 0.0) || !Double.isFinite(timeStep) || duration < 4.0 * timeStep) {
      throw new IllegalArgumentException("timeStepSeconds must be finite, positive and at most one quarter duration");
    }
    if (refinement != 1 && refinement != 2) {
      throw new IllegalArgumentException("meshRefinement must be 1 (60 cells) or 2 (120 cells)");
    }
    if (!(sampleInterval >= timeStep) || !Double.isFinite(sampleInterval)) {
      throw new IllegalArgumentException("sampleIntervalSeconds must be finite and at least timeStepSeconds");
    }
  }

  private static double range(double minimum, double maximum) {
    return maximum - minimum;
  }

  private static double relativeChange(double reference, double candidate) {
    return (candidate - reference) / Math.max(Math.abs(reference), 1.0e-12);
  }

  private static double relativeDifference(double first, double second) {
    return Math.abs(second - first) / Math.max(0.5 * (Math.abs(first) + Math.abs(second)), 1.0e-12);
  }
}
