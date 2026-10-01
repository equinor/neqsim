package examples;

import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Compares NeqSim's two-fluid and drift-flux pipeline implementations on one screened case.
 *
 * <p>The example reports pressure drop in bar, temperature in degrees Celsius, liquid inventory in
 * cubic metres, and holdup as a volume fraction. It is a numerical screening example, not a
 * validation data set or a model-selection rule. Qualify both models against representative
 * measurements, convergence studies, and the intended operating envelope before engineering use.
 */
public final class TwoFluidVsDriftFluxComparisonExample {
  private static final Logger logger =
      LogManager.getLogger(TwoFluidVsDriftFluxComparisonExample.class);

  private TwoFluidVsDriftFluxComparisonExample() {}

  /**
   * Runs the comparison.
   *
   * @param args pass {@code --smoke} for the bounded documentation-test case
   */
  public static void main(String[] args) {
    boolean smoke = args.length > 0 && "--smoke".equals(args[0]);
    runModelComparison(smoke);
  }

  /** Runs the full example study. */
  public static void runModelComparison() {
    runModelComparison(false);
  }

  private static void runModelComparison(boolean smoke) {
    double pipeLengthMetres = smoke ? 500.0 : 80000.0;
    double pipeDiameterMetres = 0.5;
    int numberOfSections = smoke ? 4 : 80;
    double inletTemperatureC = 60.0;
    double inletPressureBara = 120.0;
    double roughnessMetres = 4.5e-5;
    double heatTransferCoefficientWPerM2K = 25.0;
    double ambientTemperatureC = 4.0;
    double[] flowRatesKgPerSecond = smoke ? new double[] {50.0} : new double[] {50.0, 100.0, 150.0};
    double[] elevationMetres =
        createSubseaTerrainProfile(numberOfSections, pipeLengthMetres);

    logger.info(
        "Comparison basis: length={} km, diameter={} m, inlet={} bara/{} C, sections={}",
        pipeLengthMetres / 1000.0, pipeDiameterMetres, inletPressureBara, inletTemperatureC,
        numberOfSections);

    for (double flowRateKgPerSecond : flowRatesKgPerSecond) {
      ModelResults driftFlux = runDriftFluxModel(pipeLengthMetres, pipeDiameterMetres,
          numberOfSections, elevationMetres, inletTemperatureC, inletPressureBara,
          flowRateKgPerSecond, roughnessMetres, heatTransferCoefficientWPerM2K,
          ambientTemperatureC, smoke);
      ModelResults twoFluid = runTwoFluidModel(pipeLengthMetres, pipeDiameterMetres,
          numberOfSections, elevationMetres, inletTemperatureC, inletPressureBara,
          flowRateKgPerSecond, roughnessMetres, heatTransferCoefficientWPerM2K,
          ambientTemperatureC);

      validateResults(twoFluid);
      validateResults(driftFlux);
      logComparison(flowRateKgPerSecond, twoFluid, driftFlux);
    }

    logger.info(
        "Qualification boundary: these values compare two implementations for one composition and "
            + "discretisation; they do not establish accuracy, stability, or universal model choice.");
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

  private static double[] createSubseaTerrainProfile(int numberOfSections,
      double totalLengthMetres) {
    double[] elevationMetres = new double[numberOfSections];
    for (int section = 0; section < numberOfSections; section++) {
      double fraction = (double) section / Math.max(1, numberOfSections - 1);
      elevationMetres[section] = -30.0 * Math.sin(2.0 * Math.PI * fraction)
          - 15.0 * Math.sin(6.0 * Math.PI * fraction);
    }
    return elevationMetres;
  }

  private static ModelResults runTwoFluidModel(double pipeLengthMetres,
      double pipeDiameterMetres, int numberOfSections, double[] elevationMetres,
      double inletTemperatureC, double inletPressureBara, double flowRateKgPerSecond,
      double roughnessMetres, double heatTransferCoefficientWPerM2K,
      double ambientTemperatureC) {
    Stream inlet = createInlet("two-fluid inlet", inletTemperatureC, inletPressureBara,
        flowRateKgPerSecond);
    TwoFluidPipe pipe = new TwoFluidPipe("two-fluid pipe", inlet);
    pipe.setLength(pipeLengthMetres);
    pipe.setDiameter(pipeDiameterMetres);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setRoughness(roughnessMetres);
    pipe.setElevationProfile(elevationMetres);
    pipe.setHeatTransferCoefficient(heatTransferCoefficientWPerM2K);
    pipe.setSurfaceTemperature(ambientTemperatureC, "C");
    pipe.setThermodynamicUpdateInterval(50);
    pipe.run();

    ModelResults results = new ModelResults("two-fluid");
    results.pressurePa = pipe.getPressureProfile();
    results.temperatureK = pipe.getTemperatureProfile();
    results.liquidHoldup = pipe.getLiquidHoldupProfile();
    results.liquidInventoryM3 = pipe.getLiquidInventory("m3");
    completeDerivedResults(results);
    return results;
  }

  private static ModelResults runDriftFluxModel(double pipeLengthMetres,
      double pipeDiameterMetres, int numberOfSections, double[] elevationMetres,
      double inletTemperatureC, double inletPressureBara, double flowRateKgPerSecond,
      double roughnessMetres, double heatTransferCoefficientWPerM2K,
      double ambientTemperatureC, boolean smoke) {
    Stream inlet = createInlet("drift-flux inlet", inletTemperatureC, inletPressureBara,
        flowRateKgPerSecond);
    TransientPipe pipe = new TransientPipe("drift-flux pipe", inlet);
    pipe.setLength(pipeLengthMetres);
    pipe.setDiameter(pipeDiameterMetres);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setRoughness(roughnessMetres);
    pipe.setElevationProfile(elevationMetres);
    pipe.setIncludeHeatTransfer(true);
    pipe.setOverallHeatTransferCoeff(heatTransferCoefficientWPerM2K);
    pipe.setAmbientTemperature(ambientTemperatureC + 273.15);
    pipe.setThermodynamicUpdateInterval(50);
    pipe.setMaxSimulationTime(smoke ? 1.0 : 60.0);

    try {
      pipe.run();
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Drift-flux comparison failed; no partial result is valid",
          exception);
    }

    ModelResults results = new ModelResults("drift-flux");
    results.pressurePa = pipe.getPressureProfile();
    results.temperatureK = pipe.getTemperatureProfile();
    results.liquidHoldup = pipe.getLiquidHoldupProfile();
    requireProfile("drift-flux liquid holdup", results.liquidHoldup);
    double sectionLengthMetres = pipeLengthMetres / numberOfSections;
    double areaM2 = Math.PI * pipeDiameterMetres * pipeDiameterMetres / 4.0;
    for (double holdup : results.liquidHoldup) {
      results.liquidInventoryM3 += holdup * areaM2 * sectionLengthMetres;
    }
    completeDerivedResults(results);
    return results;
  }

  private static Stream createInlet(String name, double temperatureC, double pressureBara,
      double flowRateKgPerSecond) {
    Stream inlet = new Stream(name, createGasCondensateWithWater(temperatureC, pressureBara));
    inlet.setFlowRate(flowRateKgPerSecond, "kg/sec");
    inlet.setTemperature(temperatureC, "C");
    inlet.setPressure(pressureBara, "bara");
    inlet.run();
    return inlet;
  }

  private static void completeDerivedResults(ModelResults results) {
    requireProfile(results.model + " pressure", results.pressurePa);
    requireProfile(results.model + " temperature", results.temperatureK);
    requireHoldup(results.model + " liquid holdup", results.liquidHoldup);
    int last = results.pressurePa.length - 1;
    results.pressureDropBar = (results.pressurePa[0] - results.pressurePa[last]) / 1.0e5;
    results.outletTemperatureC = results.temperatureK[results.temperatureK.length - 1] - 273.15;
  }

  private static void validateResults(ModelResults results) {
    requireFinite(results.model + " pressure drop", results.pressureDropBar);
    requireFinite(results.model + " outlet temperature", results.outletTemperatureC);
    requireFinite(results.model + " liquid inventory", results.liquidInventoryM3);
    if (results.liquidInventoryM3 < 0.0) {
      throw new IllegalStateException(results.model + " returned negative liquid inventory");
    }
    assert Double.isFinite(results.pressureDropBar);
    assert Double.isFinite(results.outletTemperatureC);
    assert results.liquidInventoryM3 >= 0.0;
  }

  private static void logComparison(double flowRateKgPerSecond, ModelResults twoFluid,
      ModelResults driftFlux) {
    logger.info("Flow={} kg/s", flowRateKgPerSecond);
    logger.info("Two-fluid: pressure drop={} bar, outlet={} C, liquid={} m3",
        format(twoFluid.pressureDropBar), format(twoFluid.outletTemperatureC),
        format(twoFluid.liquidInventoryM3));
    logger.info("Drift-flux: pressure drop={} bar, outlet={} C, liquid={} m3",
        format(driftFlux.pressureDropBar), format(driftFlux.outletTemperatureC),
        format(driftFlux.liquidInventoryM3));
  }

  private static void requireProfile(String label, double[] values) {
    if (values == null || values.length == 0) {
      throw new IllegalStateException(label + " profile is missing");
    }
    for (double value : values) {
      requireFinite(label, value);
    }
  }

  private static void requireHoldup(String label, double[] values) {
    requireProfile(label, values);
    for (double value : values) {
      if (value < 0.0 || value > 1.0) {
        throw new IllegalStateException(label + " must remain in [0, 1]");
      }
    }
  }

  private static void requireFinite(String label, double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(label + " must be finite");
    }
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }

  private static final class ModelResults {
    private final String model;
    private double[] pressurePa;
    private double[] temperatureK;
    private double[] liquidHoldup;
    private double liquidInventoryM3;
    private double pressureDropBar;
    private double outletTemperatureC;

    private ModelResults(String model) {
      this.model = model;
    }
  }
}
