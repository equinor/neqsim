package examples;

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

  public static void main(String[] args) {
    System.out.println("================================================================");
    System.out.println("  Multiphase Model Pressure Drop Comparison");
    System.out.println("  1 km horizontal pipeline, 300 mm diameter");
    System.out.println("================================================================\n");

    // Test Case 0: Pure Gas (single phase)
    System.out.println("============================================================");
    System.out.println("  TEST CASE 0: Single-Phase Flow (Pure Gas)");
    System.out.println("============================================================\n");
    runPureGasComparison();

    // Test Case 1: Two-phase (Gas + Oil)
    System.out.println("\n============================================================");
    System.out.println("  TEST CASE 1: Two-Phase Flow (Gas + Oil)");
    System.out.println("============================================================\n");
    runTwoPhaseComparison();

    // Test Case 2: Three-phase (Gas + Oil + Water)
    System.out.println("\n============================================================");
    System.out.println("  TEST CASE 2: Three-Phase Flow (Gas + Oil + Water)");
    System.out.println("============================================================\n");
    runThreePhaseComparison();

    // Flow regime summary
    System.out.println("\n============================================================");
    System.out.println("  FLOW REGIME DETECTION METHODS");
    System.out.println("============================================================");
    printFlowRegimeSummary();

    // Model summary
    System.out.println("\n============================================================");
    System.out.println("  MODEL COMPARISON SUMMARY");
    System.out.println("============================================================");
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

    System.out.println("Configuration:");
    System.out.printf("  Length:      %.0f m%n", length);
    System.out.printf("  Diameter:    %.0f mm%n", diameter * 1000);
    System.out.printf("  Roughness:   %.1f um%n", roughness * 1e6);
    System.out.printf("  Inlet P:     %.0f bara%n", inletPressure);
    System.out.printf("  Temperature: %.0f C%n", temperature);
    System.out.println("  Fluid:       Natural gas (C1-C3)");
    System.out.println();

    // Print header
    System.out.printf("%-12s %12s %12s %12s %12s%n", "Flow Rate", "Beggs-Brill", "Drift-Flux",
        "Two-Fluid", "Max Diff");
    System.out.printf("%-12s %12s %12s %12s %12s%n", "(kg/s)", "(bar)", "(bar)", "(bar)", "(%)");
    System.out.println("------------------------------------------------------------");

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

      System.out.printf("%-12.1f %12.3f %12.3f %12.3f %12.1f%n", flowRate, dpBB, dpDF, dpTF,
          maxDiff);
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

    System.out.println("Configuration:");
    System.out.printf("  Length:      %.0f m%n", length);
    System.out.printf("  Diameter:    %.0f mm%n", diameter * 1000);
    System.out.printf("  Roughness:   %.1f μm%n", roughness * 1e6);
    System.out.printf("  Inlet P:     %.0f bara%n", inletPressure);
    System.out.printf("  Temperature: %.0f °C%n", temperature);
    System.out.println();

    // Print header
    System.out.printf("%-12s %12s %12s %12s %12s%n", "Flow Rate", "Beggs-Brill", "Drift-Flux",
        "Two-Fluid", "Max Diff");
    System.out.printf("%-12s %12s %12s %12s %12s%n", "(kg/s)", "(bar)", "(bar)", "(bar)", "(%)");
    System.out.println("------------------------------------------------------------");

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

      System.out.printf("%-12.1f %12.3f %12.3f %12.3f %12.1f%n", flowRate, dpBB, dpDF, dpTF,
          maxDiff);
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

    System.out.println("Configuration:");
    System.out.printf("  Length:      %.0f m%n", length);
    System.out.printf("  Diameter:    %.0f mm%n", diameter * 1000);
    System.out.printf("  Roughness:   %.1f μm%n", roughness * 1e6);
    System.out.printf("  Inlet P:     %.0f bara%n", inletPressure);
    System.out.printf("  Temperature: %.0f °C%n", temperature);
    System.out.printf("  Water cut:   ~30%%%n");
    System.out.println();

    // Print header
    System.out.printf("%-12s %12s %12s %12s %12s%n", "Flow Rate", "Beggs-Brill", "Drift-Flux",
        "Two-Fluid", "Max Diff");
    System.out.printf("%-12s %12s %12s %12s %12s%n", "(kg/s)", "(bar)", "(bar)", "(bar)", "(%)");
    System.out.println("------------------------------------------------------------");

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

      System.out.printf("%-12.1f %12.3f %12.3f %12.3f %12.1f%n", flowRate, dpBB, dpDF, dpTF,
          maxDiff);
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
      System.err.println("Beggs-Brill failed: " + e.getMessage());
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
      System.err.println("Drift-Flux failed: " + e.getMessage());
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
      System.err.println("Two-Fluid failed: " + e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Print flow regime detection summary.
   */
  private static void printFlowRegimeSummary() {
    System.out.println();
    System.out.println("Flow Regime Detection by Model:");
    System.out.println();

    System.out.println("BEGGS & BRILL (Empirical):");
    System.out.println("  Method: Froude number (Fr) vs Input liquid volume fraction (lambda)");
    System.out.println("  Transition boundaries: L1, L2, L3, L4 functions of lambda");
    System.out.println("  Regimes:");
    System.out.println("    - SEGREGATED:    lambda<0.01 & Fr<L1, or lambda>=0.01 & Fr<L2");
    System.out.println("    - INTERMITTENT:  Slug/plug flow between segregated and distributed");
    System.out.println("    - DISTRIBUTED:   High Fr - dispersed bubble or mist");
    System.out.println("    - TRANSITION:    L2 < Fr < L3 - blend of segregated/intermittent");
    System.out.println("    - SINGLE_PHASE:  Pure gas or pure liquid");
    System.out.println();

    System.out.println("DRIFT-FLUX / TWO-FLUID (Mechanistic):");
    System.out.println("  Method: Taitel-Dukler (1976) + Barnea (1987) unified model");
    System.out
        .println("  Uses: Kelvin-Helmholtz stability, bubble rise velocity, critical velocities");
    System.out.println("  Regimes (horizontal/near-horizontal):");
    System.out.println("    - STRATIFIED_SMOOTH: Low gas velocity, stable interface");
    System.out.println("    - STRATIFIED_WAVY:   Higher gas velocity, wavy interface");
    System.out.println("    - SLUG:              K-H unstable, liquid bridges pipe");
    System.out.println("    - ANNULAR:           Very high gas velocity, liquid film on wall");
    System.out.println("    - DISPERSED_BUBBLE:  High liquid velocity, bubbles in liquid");
    System.out.println("  Regimes (inclined - Barnea model):");
    System.out.println("    - BUBBLE:            Low gas, upward flow");
    System.out.println("    - SLUG:              Intermittent gas pockets");
    System.out.println("    - CHURN:             Chaotic, high gas upward");
    System.out.println("    - ANNULAR:           Gas core, liquid film");
    System.out.println();

    System.out.println("Key Dimensionless Parameters:");
    System.out.println("  - Martinelli parameter (X): Liquid/gas pressure gradient ratio");
    System.out.println("  - Froude number (Fr): Inertia vs gravity");
    System.out.println("  - Kelvin-Helmholtz number (K): Interface stability criterion");
    System.out.println("  - Weber number (We): Inertia vs surface tension");
    System.out.println();

    System.out.println("Impact on Pressure Drop:");
    System.out.println("  - STRATIFIED: Lower friction, gravity-dominated at inclination");
    System.out.println("  - SLUG/INTERMITTENT: Higher friction, liquid holdup fluctuations");
    System.out.println("  - ANNULAR: High interfacial friction, thin liquid film");
    System.out.println("  - SINGLE_PHASE: Standard Darcy-Weisbach friction");
  }

  /**
   * Print model comparison summary.
   */
  private static void printModelSummary() {
    System.out.println();
    System.out.println("Model Characteristics:");
    System.out.println();
    System.out.printf("%-20s %-15s %-20s %-25s%n", "Model", "Type", "Equations", "Best For");
    System.out.println(
        "--------------------------------------------------------------------------------");
    System.out.printf("%-20s %-15s %-20s %-25s%n", "Beggs & Brill", "Empirical", "Correlation",
        "Quick estimates, validation");
    System.out.printf("%-20s %-15s %-20s %-25s%n", "Drift-Flux", "Mechanistic", "4-equation",
        "Gas-liquid, transients");
    System.out.printf("%-20s %-15s %-20s %-25s%n", "Two-Fluid", "Mechanistic", "7-equation",
        "Three-phase, oil-water slip");
    System.out.println();

    System.out.println("Expected Behavior:");
    System.out.println();
    System.out.println("  - Single-phase gas: All models should converge closely");
    System.out.println("    (friction factor from Colebrook-White or similar)");
    System.out.println();
    System.out.println("  - At low flow rates: Models show larger relative differences");
    System.out.println("    (friction terms smaller relative to numerical/model differences)");
    System.out.println();
    System.out.println("  - At high flow rates: Models tend to converge");
    System.out.println("    (friction-dominated, less sensitive to holdup differences)");
    System.out.println();
    System.out.println("  - Three-phase flow: Two-Fluid may differ more from others");
    System.out.println("    (accounts for oil-water stratification and separate slip)");
    System.out.println();
    System.out.println("  - Beggs & Brill: Empirical, validated for certain conditions");
    System.out.println("    (may over/underpredict outside validation range)");
    System.out.println();

    System.out.println("Recommendations:");
    System.out.println();
    System.out.println("  1. Use Beggs & Brill for quick screening and historical comparison");
    System.out.println("  2. Use Drift-Flux for gas-liquid systems and transient analysis");
    System.out.println("  3. Use Two-Fluid for three-phase systems or detailed phase behavior");
    System.out.println("  4. When models disagree significantly, investigate flow regime");
    System.out.println("  5. For critical applications, validate against field data");
    System.out.println("  6. For single-phase gas, all models are equivalent (use simplest)");
  }
}
