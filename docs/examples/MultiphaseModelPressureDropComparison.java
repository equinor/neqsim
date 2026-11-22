package examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comparison of pressure drop predictions from various multiphase flow models.
 *
 * <p>
 * Models compared:
 * <ul>
 * <li>Beggs and Brill (1973) - Empirical correlation</li>
 * <li>TransientPipe - Drift-flux model (4-equation)</li>
 * <li>TwoFluidPipe - Two-fluid model (7-equation)</li>
 * </ul>
 *
 * <p>
 * Test cases:
 * <ul>
 * <li>Single-phase: Pure Gas</li>
 * <li>Two-phase: Gas + Oil</li>
 * <li>Three-phase: Gas + Oil + Water</li>
 * </ul>
 *
 * <p>
 * Flow Regime Detection Methods:
 * <ul>
 * <li>Beggs and Brill: Uses Froude number and input liquid fraction to identify SEGREGATED,
 * INTERMITTENT, DISTRIBUTED, TRANSITION, or SINGLE_PHASE regimes.</li>
 * <li>Drift-Flux (TransientPipe): Uses Taitel-Dukler (1976) for horizontal and Barnea (1987) for
 * inclined pipes. Regimes: STRATIFIED_SMOOTH, STRATIFIED_WAVY, SLUG, ANNULAR, DISPERSED_BUBBLE,
 * CHURN.</li>
 * <li>Two-Fluid (TwoFluidPipe): Same FlowRegimeDetector with mechanistic criteria based on
 * Kelvin-Helmholtz stability.</li>
 * </ul>
 */
public class MultiphaseModelPressureDropComparison {
  private static final Logger logger =
      LogManager.getLogger(MultiphaseModelPressureDropComparison.class);


  public static void main(String[] args) {
    logger.info("================================================================");
    logger.info("  Multiphase Model Pressure Drop Comparison");
    logger.info("  1 km horizontal pipeline, 300 mm diameter");
    logger.info("================================================================\n");

    // Test Case 0: Pure Gas (single phase)
    logger.info("============================================================");
    logger.info("  TEST CASE 0: Single-Phase Flow (Pure Gas)");
    logger.info("============================================================\n");
    runPureGasComparison();

    // Test Case 1: Two-phase (Gas + Oil)
    logger.info("\n============================================================");
    logger.info("  TEST CASE 1: Two-Phase Flow (Gas + Oil)");
    logger.info("============================================================\n");
    runTwoPhaseComparison();

    // Test Case 2: Three-phase (Gas + Oil + Water)
    logger.info("\n============================================================");
    logger.info("  TEST CASE 2: Three-Phase Flow (Gas + Oil + Water)");
    logger.info("============================================================\n");
    runThreePhaseComparison();

    // Flow regime summary
    logger.info("\n============================================================");
    logger.info("  FLOW REGIME DETECTION METHODS");
    logger.info("============================================================");
    printFlowRegimeSummary();

    // Model summary
    logger.info("\n============================================================");
    logger.info("  MODEL COMPARISON SUMMARY");
    logger.info("============================================================");
    printModelSummary();
  }

  /**
   * Pure gas (single phase) comparison.
   */
  private static void runPureGasComparison() {
    // Pipeline parameters
    double length = 1000.0; // 1 km
    double diameter = 0.3; // 300 mm
    double roughness = 4.5e-5; // m
    double inletPressure = 50.0; // bara
    double temperature = 40.0; // C

    // Flow rates to test
    double[] flowRates = {5.0, 10.0, 20.0, 40.0, 60.0, 80.0, 100.0}; // kg/s

    logger.info("Configuration:");
    logger.info("{}", String.format("  Length:      %.0f m", length));
    logger.info("{}", String.format("  Diameter:    %.0f mm", diameter * 1000));
    logger.info("{}", String.format("  Roughness:   %.1f um", roughness * 1e6));
    logger.info("{}", String.format("  Inlet P:     %.0f bara", inletPressure));
    logger.info("{}", String.format("  Temperature: %.0f C", temperature));
    logger.info("  Fluid:       Natural gas (C1-C3)");
    logger.info("");

    // Print header
    logger.info("{}", String.format("%-12s %12s %12s %12s %12s", "Flow Rate", "Beggs-Brill", "Drift-Flux",
        "Two-Fluid", "Max Diff"));
    logger.info("{}", String.format("%-12s %12s %12s %12s %12s", "(kg/s)", "(bar)", "(bar)", "(bar)", "(%)"));
    logger.info("------------------------------------------------------------");

    for (double flowRate : flowRates) {
      // Create pure gas fluid
      SystemInterface fluid = createPureGasFluid(temperature, inletPressure);

      double dpBB = runBeggsAndBrill(fluid.clone(), flowRate, length, diameter, roughness,
          temperature, inletPressure);
      double dpDF = runDriftFlux(fluid.clone(), flowRate, length, diameter, roughness, temperature,
          inletPressure);
      double dpTF = runTwoFluid(fluid.clone(), flowRate, length, diameter, roughness, temperature,
          inletPressure);

      // Calculate max difference
      double maxDp = Math.max(Math.max(dpBB, dpDF), dpTF);
      double minDp = Math.min(Math.min(dpBB, dpDF), dpTF);
      double avgDp = (dpBB + dpDF + dpTF) / 3.0;
      double maxDiff = avgDp > 0 ? 100.0 * (maxDp - minDp) / avgDp : 0;

      logger.info("{}", String.format("%-12.1f %12.3f %12.3f %12.3f %12.1f", flowRate, dpBB, dpDF, dpTF,
          maxDiff));
    }
  }

  /**
   * Two-phase flow comparison (gas + oil).
   */
  private static void runTwoPhaseComparison() {
    // Pipeline parameters
    double length = 1000.0; // 1 km
    double diameter = 0.3; // 300 mm
    double roughness = 4.5e-5; // m
    double inletPressure = 50.0; // bara
    double temperature = 40.0; // °C

    // Flow rates to test
    double[] flowRates = {5.0, 10.0, 20.0, 40.0, 60.0, 80.0, 100.0}; // kg/s

    logger.info("Configuration:");
    logger.info("{}", String.format("  Length:      %.0f m", length));
    logger.info("{}", String.format("  Diameter:    %.0f mm", diameter * 1000));
    logger.info("{}", String.format("  Roughness:   %.1f μm", roughness * 1e6));
    logger.info("{}", String.format("  Inlet P:     %.0f bara", inletPressure));
    logger.info("{}", String.format("  Temperature: %.0f °C", temperature));
    logger.info("");

    // Print header
    logger.info("{}", String.format("%-12s %12s %12s %12s %12s", "Flow Rate", "Beggs-Brill", "Drift-Flux",
        "Two-Fluid", "Max Diff"));
    logger.info("{}", String.format("%-12s %12s %12s %12s %12s", "(kg/s)", "(bar)", "(bar)", "(bar)", "(%)"));
    logger.info("------------------------------------------------------------");

    for (double flowRate : flowRates) {
      // Create two-phase fluid (gas + oil)
      SystemInterface fluid = createTwoPhaseFluid(temperature, inletPressure);

      double dpBB = runBeggsAndBrill(fluid.clone(), flowRate, length, diameter, roughness,
          temperature, inletPressure);
      double dpDF = runDriftFlux(fluid.clone(), flowRate, length, diameter, roughness, temperature,
          inletPressure);
      double dpTF = runTwoFluid(fluid.clone(), flowRate, length, diameter, roughness, temperature,
          inletPressure);

      // Calculate max difference
      double maxDp = Math.max(Math.max(dpBB, dpDF), dpTF);
      double minDp = Math.min(Math.min(dpBB, dpDF), dpTF);
      double avgDp = (dpBB + dpDF + dpTF) / 3.0;
      double maxDiff = avgDp > 0 ? 100.0 * (maxDp - minDp) / avgDp : 0;

      logger.info("{}", String.format("%-12.1f %12.3f %12.3f %12.3f %12.1f", flowRate, dpBB, dpDF, dpTF,
          maxDiff));
    }
  }

  /**
   * Three-phase flow comparison (gas + oil + water).
   */
  private static void runThreePhaseComparison() {
    // Pipeline parameters
    double length = 1000.0; // 1 km
    double diameter = 0.3; // 300 mm
    double roughness = 4.5e-5; // m
    double inletPressure = 50.0; // bara
    double temperature = 40.0; // °C

    // Flow rates to test
    double[] flowRates = {5.0, 10.0, 20.0, 40.0, 60.0, 80.0, 100.0}; // kg/s

    logger.info("Configuration:");
    logger.info("{}", String.format("  Length:      %.0f m", length));
    logger.info("{}", String.format("  Diameter:    %.0f mm", diameter * 1000));
    logger.info("{}", String.format("  Roughness:   %.1f μm", roughness * 1e6));
    logger.info("{}", String.format("  Inlet P:     %.0f bara", inletPressure));
    logger.info("{}", String.format("  Temperature: %.0f °C", temperature));
    logger.info("{}", String.format("  Water cut:   ~30%%"));
    logger.info("");

    // Print header
    logger.info("{}", String.format("%-12s %12s %12s %12s %12s", "Flow Rate", "Beggs-Brill", "Drift-Flux",
        "Two-Fluid", "Max Diff"));
    logger.info("{}", String.format("%-12s %12s %12s %12s %12s", "(kg/s)", "(bar)", "(bar)", "(bar)", "(%)"));
    logger.info("------------------------------------------------------------");

    for (double flowRate : flowRates) {
      // Create three-phase fluid (gas + oil + water)
      SystemInterface fluid = createThreePhaseFluid(temperature, inletPressure);

      double dpBB = runBeggsAndBrill(fluid.clone(), flowRate, length, diameter, roughness,
          temperature, inletPressure);
      double dpDF = runDriftFlux(fluid.clone(), flowRate, length, diameter, roughness, temperature,
          inletPressure);
      double dpTF = runTwoFluid(fluid.clone(), flowRate, length, diameter, roughness, temperature,
          inletPressure);

      // Calculate max difference
      double maxDp = Math.max(Math.max(dpBB, dpDF), dpTF);
      double minDp = Math.min(Math.min(dpBB, dpDF), dpTF);
      double avgDp = (dpBB + dpDF + dpTF) / 3.0;
      double maxDiff = avgDp > 0 ? 100.0 * (maxDp - minDp) / avgDp : 0;

      logger.info("{}", String.format("%-12.1f %12.3f %12.3f %12.3f %12.1f", flowRate, dpBB, dpDF, dpTF,
          maxDiff));
    }
  }

  /**
   * Creates a pure gas fluid (single phase).
   */
  private static SystemInterface createPureGasFluid(double tempC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(tempC + 273.15, pressure);
    // Light natural gas - stays in gas phase
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("nitrogen", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates a two-phase gas-oil fluid.
   */
  private static SystemInterface createTwoPhaseFluid(double tempC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(tempC + 273.15, pressure);
    // Gas-dominant with some condensate
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.addComponent("n-octane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates a three-phase gas-oil-water fluid.
   */
  private static SystemInterface createThreePhaseFluid(double tempC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(tempC + 273.15, pressure);
    // Gas-dominant with condensate and water
    fluid.addComponent("methane", 0.55);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent("n-octane", 0.03);
    fluid.addComponent("water", 0.22); // ~30% water cut in liquid
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Run Beggs and Brill model.
   */
  private static double runBeggsAndBrill(SystemInterface fluid, double flowRate, double length,
      double diameter, double roughness, double tempC, double pressure) {
    try {
      Stream inlet = new Stream("BB_inlet", fluid);
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(tempC, "C");
      inlet.setPressure(pressure, "bara");
      inlet.run();

      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("BeggsAndBrill", inlet);
      pipe.setDiameter(diameter);
      pipe.setLength(length);
      pipe.setPipeWallRoughness(roughness);
      pipe.setAngle(0); // Horizontal
      pipe.setNumberOfIncrements(20);
      pipe.run();

      return pipe.getPressureDrop(); // Returns bar
    } catch (Exception e) {
      logger.error("Beggs-Brill failed: {}", e.getMessage(), e);
      return Double.NaN;
    }
  }

  /**
   * Run Drift-Flux model (TransientPipe).
   */
  private static double runDriftFlux(SystemInterface fluid, double flowRate, double length,
      double diameter, double roughness, double tempC, double pressure) {
    try {
      Stream inlet = new Stream("DF_inlet", fluid);
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(tempC, "C");
      inlet.setPressure(pressure, "bara");
      inlet.run();

      TransientPipe pipe = new TransientPipe("DriftFlux", inlet);
      pipe.setLength(length);
      pipe.setDiameter(diameter);
      pipe.setRoughness(roughness);
      pipe.setNumberOfSections(25);
      pipe.setMaxSimulationTime(60); // Short sim for steady-state
      pipe.run();

      double[] P = pipe.getPressureProfile();
      if (P != null && P.length > 1) {
        return (P[0] - P[P.length - 1]) / 1e5; // Pa to bar
      }
      return Double.NaN;
    } catch (Exception e) {
      logger.error("Drift-Flux failed: {}", e.getMessage(), e);
      return Double.NaN;
    }
  }

  /**
   * Run Two-Fluid model (TwoFluidPipe).
   */
  private static double runTwoFluid(SystemInterface fluid, double flowRate, double length,
      double diameter, double roughness, double tempC, double pressure) {
    try {
      Stream inlet = new Stream("TF_inlet", fluid);
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(tempC, "C");
      inlet.setPressure(pressure, "bara");
      inlet.run();

      TwoFluidPipe pipe = new TwoFluidPipe("TwoFluid", inlet);
      pipe.setLength(length);
      pipe.setDiameter(diameter);
      pipe.setRoughness(roughness);
      pipe.setNumberOfSections(25);
      pipe.run();

      double[] P = pipe.getPressureProfile();
      if (P != null && P.length > 1) {
        return (P[0] - P[P.length - 1]) / 1e5; // Pa to bar
      }
      return Double.NaN;
    } catch (Exception e) {
      logger.error("Two-Fluid failed: {}", e.getMessage(), e);
      return Double.NaN;
    }
  }

  /**
   * Print flow regime detection summary.
   */
  private static void printFlowRegimeSummary() {
    logger.info("");
    logger.info("Flow Regime Detection by Model:");
    logger.info("");

    logger.info("BEGGS & BRILL (Empirical):");
    logger.info("  Method: Froude number (Fr) vs Input liquid volume fraction (lambda)");
    logger.info("  Transition boundaries: L1, L2, L3, L4 functions of lambda");
    logger.info("  Regimes:");
    logger.info("    - SEGREGATED:    lambda<0.01 & Fr<L1, or lambda>=0.01 & Fr<L2");
    logger.info("    - INTERMITTENT:  Slug/plug flow between segregated and distributed");
    logger.info("    - DISTRIBUTED:   High Fr - dispersed bubble or mist");
    logger.info("    - TRANSITION:    L2 < Fr < L3 - blend of segregated/intermittent");
    logger.info("    - SINGLE_PHASE:  Pure gas or pure liquid");
    logger.info("");

    logger.info("DRIFT-FLUX / TWO-FLUID (Mechanistic):");
    logger.info("  Method: Taitel-Dukler (1976) + Barnea (1987) unified model");
    logger.info("  Uses: Kelvin-Helmholtz stability, bubble rise velocity, critical velocities");
    logger.info("  Regimes (horizontal/near-horizontal):");
    logger.info("    - STRATIFIED_SMOOTH: Low gas velocity, stable interface");
    logger.info("    - STRATIFIED_WAVY:   Higher gas velocity, wavy interface");
    logger.info("    - SLUG:              K-H unstable, liquid bridges pipe");
    logger.info("    - ANNULAR:           Very high gas velocity, liquid film on wall");
    logger.info("    - DISPERSED_BUBBLE:  High liquid velocity, bubbles in liquid");
    logger.info("  Regimes (inclined - Barnea model):");
    logger.info("    - BUBBLE:            Low gas, upward flow");
    logger.info("    - SLUG:              Intermittent gas pockets");
    logger.info("    - CHURN:             Chaotic, high gas upward");
    logger.info("    - ANNULAR:           Gas core, liquid film");
    logger.info("");

    logger.info("Key Dimensionless Parameters:");
    logger.info("  - Martinelli parameter (X): Liquid/gas pressure gradient ratio");
    logger.info("  - Froude number (Fr): Inertia vs gravity");
    logger.info("  - Kelvin-Helmholtz number (K): Interface stability criterion");
    logger.info("  - Weber number (We): Inertia vs surface tension");
    logger.info("");

    logger.info("Impact on Pressure Drop:");
    logger.info("  - STRATIFIED: Lower friction, gravity-dominated at inclination");
    logger.info("  - SLUG/INTERMITTENT: Higher friction, liquid holdup fluctuations");
    logger.info("  - ANNULAR: High interfacial friction, thin liquid film");
    logger.info("  - SINGLE_PHASE: Standard Darcy-Weisbach friction");
  }

  /**
   * Print model comparison summary.
   */
  private static void printModelSummary() {
    logger.info("");
    logger.info("Model Characteristics:");
    logger.info("");
    logger.info("{}", String.format("%-20s %-15s %-20s %-25s", "Model", "Type", "Equations", "Best For"));
    logger.info(
        "--------------------------------------------------------------------------------");
    logger.info("{}", String.format("%-20s %-15s %-20s %-25s", "Beggs & Brill", "Empirical", "Correlation",
        "Quick estimates, validation"));
    logger.info("{}", String.format("%-20s %-15s %-20s %-25s", "Drift-Flux", "Mechanistic", "4-equation",
        "Gas-liquid, transients"));
    logger.info("{}", String.format("%-20s %-15s %-20s %-25s", "Two-Fluid", "Mechanistic", "7-equation",
        "Three-phase, oil-water slip"));
    logger.info("");

    logger.info("Expected Behavior:");
    logger.info("");
    logger.info("  - Single-phase gas: All models should converge closely");
    logger.info("    (friction factor from Colebrook-White or similar)");
    logger.info("");
    logger.info("  - At low flow rates: Models show larger relative differences");
    logger.info("    (friction terms smaller relative to numerical/model differences)");
    logger.info("");
    logger.info("  - At high flow rates: Models tend to converge");
    logger.info("    (friction-dominated, less sensitive to holdup differences)");
    logger.info("");
    logger.info("  - Three-phase flow: Two-Fluid may differ more from others");
    logger.info("    (accounts for oil-water stratification and separate slip)");
    logger.info("");
    logger.info("  - Beggs & Brill: Empirical, validated for certain conditions");
    logger.info("    (may over/underpredict outside validation range)");
    logger.info("");

    logger.info("Recommendations:");
    logger.info("");
    logger.info("  1. Use Beggs & Brill for quick screening and historical comparison");
    logger.info("  2. Use Drift-Flux for gas-liquid systems and transient analysis");
    logger.info("  3. Use Two-Fluid for three-phase systems or detailed phase behavior");
    logger.info("  4. When models disagree significantly, investigate flow regime");
    logger.info("  5. For critical applications, validate against field data");
    logger.info("  6. For single-phase gas, all models are equivalent (use simplest)");
  }
}
