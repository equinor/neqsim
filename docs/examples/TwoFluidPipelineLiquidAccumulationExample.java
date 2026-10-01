package examples;

import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Demonstrates screened liquid-inventory calculations with {@link TwoFluidPipe}.
 *
 * <p>Inputs use metres, bara, degrees Celsius, kilograms per second, and W/(m2 K). Results report
 * liquid inventory in cubic metres and holdup as volume fraction. The example does not establish
 * accuracy or a universal operating limit; qualify the discretisation, correlations, fluid model,
 * initial state, and transient time step against the intended system.
 */
public final class TwoFluidPipelineLiquidAccumulationExample {
  private static final Logger logger =
      LogManager.getLogger(TwoFluidPipelineLiquidAccumulationExample.class);

  private TwoFluidPipelineLiquidAccumulationExample() {}

  /**
   * Runs the study.
   *
   * @param args pass {@code --smoke} for the bounded documentation-test case
   */
  public static void main(String[] args) {
    boolean smoke = args.length > 0 && "--smoke".equals(args[0]);
    runFlowRateSensitivityStudy(smoke);
  }

  /** Runs the full sensitivity and transient study. */
  public static void runFlowRateSensitivityStudy() {
    runFlowRateSensitivityStudy(false);
  }

  private static void runFlowRateSensitivityStudy(boolean smoke) {
    double pipeLengthMetres = smoke ? 500.0 : 80000.0;
    double pipeDiameterMetres = 0.5;
    int numberOfSections = smoke ? 4 : 80;
    double inletTemperatureC = 60.0;
    double inletPressureBara = 120.0;
    double[] flowRatesKgPerSecond = smoke ? new double[] {50.0} : new double[] {50.0, 100.0, 150.0};
    double[] elevationMetres =
        createSubseaTerrainProfile(numberOfSections);

    logger.info(
        "Sensitivity basis: length={} km, diameter={} m, inlet={} bara/{} C, sections={}",
        pipeLengthMetres / 1000.0, pipeDiameterMetres, inletPressureBara, inletTemperatureC,
        numberOfSections);

    for (double flowRateKgPerSecond : flowRatesKgPerSecond) {
      TwoFluidPipe pipe = createConfiguredPipe(pipeLengthMetres, pipeDiameterMetres,
          numberOfSections, elevationMetres, inletTemperatureC, inletPressureBara,
          flowRateKgPerSecond);
      pipe.run();
      validatePipeState(pipe);
      logState("steady", flowRateKgPerSecond, pipe);
    }

    runDetailedTransientSimulation(pipeLengthMetres, pipeDiameterMetres, numberOfSections,
        elevationMetres, inletTemperatureC, inletPressureBara, smoke);
    logger.info(
        "Qualification boundary: inventory and holdup are screened model outputs, not measurement "
            + "uncertainty or guaranteed pipeline performance.");
  }

  private static SystemInterface createGasCondensateWithWater(double temperatureC,
      double pressureBara) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperatureC + 273.15, pressureBara);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("CO2", 2.5);
    fluid.addComponent("methane", 65.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 6.0);
    fluid.addComponent("i-butane", 2.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("i-pentane", 2.5);
    fluid.addComponent("n-pentane", 3.0);
    fluid.addComponent("n-hexane", 2.5);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("n-octane", 1.0);
    fluid.addComponent("water", 1.5);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static double[] createSubseaTerrainProfile(int numberOfSections) {
    double[] elevationMetres = new double[numberOfSections];
    for (int section = 0; section < numberOfSections; section++) {
      double fraction = (double) section / Math.max(1, numberOfSections - 1);
      elevationMetres[section] = -30.0 * Math.sin(2.0 * Math.PI * fraction)
          - 15.0 * Math.sin(6.0 * Math.PI * fraction);
    }
    return elevationMetres;
  }

  private static TwoFluidPipe createConfiguredPipe(double pipeLengthMetres,
      double pipeDiameterMetres, int numberOfSections, double[] elevationMetres,
      double inletTemperatureC, double inletPressureBara, double flowRateKgPerSecond) {
    Stream inlet = new Stream("gas-condensate feed",
        createGasCondensateWithWater(inletTemperatureC, inletPressureBara));
    inlet.setFlowRate(flowRateKgPerSecond, "kg/sec");
    inlet.setTemperature(inletTemperatureC, "C");
    inlet.setPressure(inletPressureBara, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("subsea pipeline", inlet);
    pipe.setLength(pipeLengthMetres);
    pipe.setDiameter(pipeDiameterMetres);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevationMetres);
    pipe.setThermodynamicUpdateInterval(50);
    pipe.setHeatTransferCoefficient(25.0);
    pipe.setSurfaceTemperature(4.0, "C");
    return pipe;
  }

  private static void runDetailedTransientSimulation(double pipeLengthMetres,
      double pipeDiameterMetres, int numberOfSections, double[] elevationMetres,
      double inletTemperatureC, double inletPressureBara, boolean smoke) {
    double startFlowRateKgPerSecond = 50.0;
    double endFlowRateKgPerSecond = smoke ? 55.0 : 150.0;
    double timeStepSeconds = smoke ? 1.0 : 120.0;
    double rampDurationSeconds = smoke ? 1.0 : 7200.0;
    double totalTimeSeconds = smoke ? 2.0 : 14400.0;

    TwoFluidPipe pipe = createConfiguredPipe(pipeLengthMetres, pipeDiameterMetres,
        numberOfSections, elevationMetres, inletTemperatureC, inletPressureBara,
        startFlowRateKgPerSecond);
    pipe.run();
    validatePipeState(pipe);
    double initialInventoryM3 = pipe.getLiquidInventory("m3");
    Stream inlet = (Stream) pipe.getInletStream();
    UUID runId = UUID.randomUUID();

    for (double timeSeconds = timeStepSeconds; timeSeconds <= totalTimeSeconds;
        timeSeconds += timeStepSeconds) {
      double rampFraction = Math.min(1.0, timeSeconds / rampDurationSeconds);
      double flowRateKgPerSecond = startFlowRateKgPerSecond
          + rampFraction * (endFlowRateKgPerSecond - startFlowRateKgPerSecond);
      inlet.setFlowRate(flowRateKgPerSecond, "kg/sec");
      inlet.run();
      pipe.runTransient(timeStepSeconds, runId);
      validatePipeState(pipe);
      if (smoke || timeSeconds == totalTimeSeconds || timeSeconds % 1200.0 == 0.0) {
        logState("transient t=" + format(timeSeconds) + " s", flowRateKgPerSecond, pipe);
      }
    }

    double finalInventoryM3 = pipe.getLiquidInventory("m3");
    requireFinite("initial liquid inventory", initialInventoryM3);
    requireFinite("final liquid inventory", finalInventoryM3);
    assert initialInventoryM3 >= 0.0;
    assert finalInventoryM3 >= 0.0;
    logger.info("Inventory change={} m3", format(finalInventoryM3 - initialInventoryM3));
    logProfile(pipe, elevationMetres);
  }

  private static void validatePipeState(TwoFluidPipe pipe) {
    requireProfile("pressure", pipe.getPressureProfile(), false);
    requireProfile("temperature", pipe.getTemperatureProfile(), false);
    requireProfile("liquid holdup", pipe.getLiquidHoldupProfile(), true);
    requireProfile("oil holdup", pipe.getOilHoldupProfile(), true);
    requireProfile("water holdup", pipe.getWaterHoldupProfile(), true);
    double inventoryM3 = pipe.getLiquidInventory("m3");
    requireFinite("liquid inventory", inventoryM3);
    if (inventoryM3 < 0.0) {
      throw new IllegalStateException("Liquid inventory must be non-negative");
    }
    assert inventoryM3 >= 0.0;
  }

  private static void logState(String state, double flowRateKgPerSecond, TwoFluidPipe pipe) {
    double[] pressurePa = pipe.getPressureProfile();
    double pressureDropBar = (pressurePa[0] - pressurePa[pressurePa.length - 1]) / 1.0e5;
    logger.info(
        "{}: flow={} kg/s, liquid={} m3, water holdup={}, oil holdup={}, pressure drop={} bar",
        state, format(flowRateKgPerSecond), format(pipe.getLiquidInventory("m3")),
        format(average(pipe.getWaterHoldupProfile())), format(average(pipe.getOilHoldupProfile())),
        format(pressureDropBar));
  }

  private static void logProfile(TwoFluidPipe pipe, double[] elevationMetres) {
    double[] positionsMetres = pipe.getPositionProfile();
    double[] pressurePa = pipe.getPressureProfile();
    double[] liquidHoldup = pipe.getLiquidHoldupProfile();
    int step = Math.max(1, positionsMetres.length / 8);
    for (int section = 0; section < positionsMetres.length; section += step) {
      int elevationIndex = Math.min(section, elevationMetres.length - 1);
      logger.info("profile: x={} km, elevation={} m, pressure={} bara, liquid holdup={}",
          format(positionsMetres[section] / 1000.0), format(elevationMetres[elevationIndex]),
          format(pressurePa[section] / 1.0e5), format(liquidHoldup[section]));
    }
  }

  private static void requireProfile(String label, double[] values, boolean boundedFraction) {
    if (values == null || values.length == 0) {
      throw new IllegalStateException(label + " profile is missing");
    }
    for (double value : values) {
      requireFinite(label, value);
      if (boundedFraction && (value < 0.0 || value > 1.0)) {
        throw new IllegalStateException(label + " must remain in [0, 1]");
      }
    }
  }

  private static void requireFinite(String label, double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(label + " must be finite");
    }
  }

  private static double average(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total / values.length;
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }
}
