package neqsim.process.measurementdevice;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Performance test for WaterDewPointAnalyser to identify bottlenecks.
 * 
 * @author ESOL
 * @version 1.0
 */
public class WaterDewPointPerformanceTest {

  /**
   * Test performance of water dew point calculation - matches the Python code exactly.
   */
  @Test
  public void testWaterDewPointPerformance() {
    double referencePressure = 60.0; // bara

    System.out.println(StringUtils.repeat("=", 60));
    System.out.println("WATER DEW POINT PERFORMANCE TEST");
    System.out.println(StringUtils.repeat("=", 60));

    // ==========================================
    // STEP 1: System creation and component addition
    // ==========================================
    long startTotal = System.nanoTime();
    long start = System.nanoTime();

    SystemInterface feedGas = new SystemSrkCPAstatoil(298.15, 1.01325);

    long afterSystemCreate = System.nanoTime();
    System.out.printf("1. System creation:                    %8.2f ms%n",
        (afterSystemCreate - start) / 1e6);

    // Add Kristin gas composition
    start = System.nanoTime();
    feedGas.addComponent("nitrogen", 0.675);
    feedGas.addComponent("CO2", 2.773);
    feedGas.addComponent("methane", 83.55);
    feedGas.addComponent("ethane", 6.967);
    feedGas.addComponent("propane", 3.709);
    feedGas.addComponent("i-butane", 0.524);
    feedGas.addComponent("n-butane", 1.02);
    feedGas.addComponent("i-pentane", 0.216);
    feedGas.addComponent("n-pentane", 0.235);
    feedGas.addComponent("water", 0.05);

    long afterComponents = System.nanoTime();
    System.out.printf("2. Adding components:                  %8.2f ms%n",
        (afterComponents - start) / 1e6);

    // ==========================================
    // STEP 2: Mixing rule setup (CPA rule = 10)
    // ==========================================
    start = System.nanoTime();
    feedGas.setMixingRule(10); // CPA mixing rule - THIS IS EXPENSIVE!

    long afterMixingRule = System.nanoTime();
    System.out.printf("3. Setting CPA mixing rule (10):       %8.2f ms%n",
        (afterMixingRule - start) / 1e6);

    feedGas.setMultiPhaseCheck(false);

    // ==========================================
    // STEP 3: Create and run stream
    // ==========================================
    start = System.nanoTime();
    Stream feedGasStream = new Stream("feed gas", feedGas);
    feedGasStream.setFlowRate(1.0, "MSm3/day");
    feedGasStream.setTemperature(7.0, "C");
    feedGasStream.setPressure(referencePressure, "bara");

    long afterStreamCreate = System.nanoTime();
    System.out.printf("4. Stream creation:                    %8.2f ms%n",
        (afterStreamCreate - start) / 1e6);

    start = System.nanoTime();
    feedGasStream.run();

    long afterStreamRun = System.nanoTime();
    System.out.printf("5. Stream.run() [TP flash]:            %8.2f ms%n",
        (afterStreamRun - start) / 1e6);

    // ==========================================
    // STEP 4: Water dew point analyzer
    // ==========================================
    start = System.nanoTime();
    WaterDewPointAnalyser analyzer =
        new WaterDewPointAnalyser("water dew point analyser", feedGasStream);
    analyzer.setMethod("multiphase");
    analyzer.setReferencePressure(referencePressure);

    long afterAnalyzerCreate = System.nanoTime();
    System.out.printf("6. Analyzer creation:                  %8.2f ms%n",
        (afterAnalyzerCreate - start) / 1e6);

    // ==========================================
    // STEP 5: Get measured value - THIS IS THE SLOW PART
    // ==========================================
    start = System.nanoTime();
    double waterDewPointC = analyzer.getMeasuredValue("C");

    long afterGetValue = System.nanoTime();
    System.out.printf("7. getMeasuredValue() [SLOW]:          %8.2f ms%n",
        (afterGetValue - start) / 1e6);

    long totalTime = System.nanoTime() - startTotal;

    System.out.println(StringUtils.repeat("-", 60));
    System.out.printf("TOTAL TIME:                            %8.2f ms%n", totalTime / 1e6);
    System.out.println(StringUtils.repeat("=", 60));

    // Results
    double waterContentPpm =
        1e6 * feedGasStream.getFluid().getPhase("gas").getComponent("water").getz();
    System.out.printf("Water Dew Point:      %.2f °C%n", waterDewPointC);
    System.out.printf("Water Content:        %.2f ppm%n", waterContentPpm);
    System.out.println(StringUtils.repeat("=", 60));
  }

  /**
   * Compare different water dew point methods.
   */
  @Test
  public void testCompareWaterDewPointMethods() {
    double referencePressure = 40.0;

    System.out.println("\n" + StringUtils.repeat("=", 60));
    System.out.println("COMPARING WATER DEW POINT METHODS");
    System.out.println(StringUtils.repeat("=", 60));

    // Create the fluid once
    SystemInterface feedGas = new SystemSrkCPAstatoil(298.15, 1.01325);
    feedGas.addComponent("nitrogen", 0.675);
    feedGas.addComponent("CO2", 2.773);
    feedGas.addComponent("methane", 83.55);
    feedGas.addComponent("ethane", 6.967);
    feedGas.addComponent("propane", 3.709);
    feedGas.addComponent("i-butane", 0.524);
    feedGas.addComponent("n-butane", 1.02);
    feedGas.addComponent("i-pentane", 0.216);
    feedGas.addComponent("n-pentane", 0.235);
    feedGas.addComponent("water", 0.1);
    feedGas.setMixingRule(10);
    feedGas.setMultiPhaseCheck(false);

    Stream feedGasStream = new Stream("feed gas", feedGas);
    feedGasStream.setFlowRate(1.0, "MSm3/day");
    feedGasStream.setTemperature(7.0, "C");
    feedGasStream.setPressure(referencePressure, "bara");
    feedGasStream.run();

    // Test Bukacek method (fast empirical correlation)
    WaterDewPointAnalyser analyzerBukacek =
        new WaterDewPointAnalyser("bukacek analyser", feedGasStream);
    analyzerBukacek.setMethod("Bukacek");
    analyzerBukacek.setReferencePressure(referencePressure);

    long start = System.nanoTime();
    double bukacekResult = analyzerBukacek.getMeasuredValue("C");
    long bukacekTime = System.nanoTime() - start;

    // Test multiphase method (slow iterative)
    WaterDewPointAnalyser analyzerMultiphase =
        new WaterDewPointAnalyser("multiphase analyser", feedGasStream);
    analyzerMultiphase.setMethod("multiphase");
    analyzerMultiphase.setReferencePressure(referencePressure);

    start = System.nanoTime();
    double multiphaseResult = analyzerMultiphase.getMeasuredValue("C");
    long multiphaseTime = System.nanoTime() - start;

    System.out.printf("Bukacek method:     %.2f °C in %8.2f ms%n", bukacekResult,
        bukacekTime / 1e6);
    System.out.printf("Multiphase method:  %.2f °C in %8.2f ms%n", multiphaseResult,
        multiphaseTime / 1e6);
    System.out.printf("Speedup factor:     %.1fx faster with Bukacek%n",
        (double) multiphaseTime / bukacekTime);
    System.out.println(StringUtils.repeat("=", 60));
  }

  /**
   * Test showing the problem: iterative TP flash loop in WaterDewPointTemperatureMultiphaseFlash.
   */
  @Test
  public void testIterativeFlashBottleneck() {
    System.out.println("\n" + StringUtils.repeat("=", 60));
    System.out.println("ANALYZING ITERATIVE FLASH BOTTLENECK");
    System.out.println(StringUtils.repeat("=", 60));

    // Create system for dew point calculation
    SystemInterface tempFluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    tempFluid.addComponent("nitrogen", 0.675);
    tempFluid.addComponent("CO2", 2.773);
    tempFluid.addComponent("methane", 83.55);
    tempFluid.addComponent("ethane", 6.967);
    tempFluid.addComponent("propane", 3.709);
    tempFluid.addComponent("i-butane", 0.524);
    tempFluid.addComponent("n-butane", 1.02);
    tempFluid.addComponent("i-pentane", 0.216);
    tempFluid.addComponent("n-pentane", 0.235);
    tempFluid.addComponent("water", 0.1);
    tempFluid.setMixingRule(10);

    // Set up like the WaterDewPointTemperatureMultiphaseFlash does
    tempFluid.setPressure(40.0);
    tempFluid.setTemperature(0.1, "C");
    tempFluid.setMultiPhaseCheck(true);

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);

    // Single TP flash timing
    long start = System.nanoTime();
    thermoOps.TPflash();
    long singleFlashTime = System.nanoTime() - start;
    System.out.printf("Single TP flash time: %.2f ms%n", singleFlashTime / 1e6);

    // The waterDewPointTemperatureMultiphaseFlash runs up to 350 iterations
    // Each iteration does a TPflash
    System.out.printf("Estimated time for 100 iterations: %.2f ms%n", 100 * singleFlashTime / 1e6);
    System.out.printf("Estimated time for 350 iterations: %.2f ms%n", 350 * singleFlashTime / 1e6);
    System.out.println(StringUtils.repeat("=", 60));
  }

  /**
   * Test optimized approach using better initial guess.
   */
  @Test
  public void testOptimizedWaterDewPoint() {
    double referencePressure = 40.0;

    System.out.println("\n" + StringUtils.repeat("=", 60));
    System.out.println("OPTIMIZED WATER DEW POINT CALCULATION");
    System.out.println(StringUtils.repeat("=", 60));

    // Create the fluid
    SystemInterface feedGas = new SystemSrkCPAstatoil(298.15, 1.01325);
    feedGas.addComponent("nitrogen", 0.675);
    feedGas.addComponent("CO2", 2.773);
    feedGas.addComponent("methane", 83.55);
    feedGas.addComponent("ethane", 6.967);
    feedGas.addComponent("propane", 3.709);
    feedGas.addComponent("i-butane", 0.524);
    feedGas.addComponent("n-butane", 1.02);
    feedGas.addComponent("i-pentane", 0.216);
    feedGas.addComponent("n-pentane", 0.235);
    feedGas.addComponent("water", 0.1);
    feedGas.setMixingRule(10);
    feedGas.setMultiPhaseCheck(false);

    Stream feedGasStream = new Stream("feed gas", feedGas);
    feedGasStream.setFlowRate(1.0, "MSm3/day");
    feedGasStream.setTemperature(7.0, "C");
    feedGasStream.setPressure(referencePressure, "bara");
    feedGasStream.run();

    // OPTIMIZATION 1: Use Bukacek as initial guess for multiphase
    WaterDewPointAnalyser analyzerBukacek = new WaterDewPointAnalyser("bukacek", feedGasStream);
    analyzerBukacek.setMethod("Bukacek");
    analyzerBukacek.setReferencePressure(referencePressure);

    long start = System.nanoTime();
    double bukacekEstimate = analyzerBukacek.getMeasuredValue("C");
    long bukacekTime = System.nanoTime() - start;

    System.out.printf("Bukacek estimate: %.2f °C (%.2f ms)%n", bukacekEstimate, bukacekTime / 1e6);

    // Use Bukacek estimate as starting point for rigorous calculation
    start = System.nanoTime();
    SystemInterface tempFluid = feedGasStream.getThermoSystem().clone();
    tempFluid.setPressure(referencePressure);
    // Start closer to the expected dew point instead of 600K!
    tempFluid.setTemperature(bukacekEstimate + 273.15 + 20); // Start 20°C above estimate
    tempFluid.setMultiPhaseCheck(true);

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);

    double dT = 1.0;
    int i = 0;
    while (i < 100 && Math.abs(dT) > 1e-5) {
      i++;
      thermoOps.TPflash();
      if (tempFluid.hasPhaseType("aqueous")) {
        dT = tempFluid.getPhaseOfType("aqueous").getComponent("water").getNumberOfMolesInPhase()
            / tempFluid.getPhase(0).getComponent("water").getNumberOfmoles();
        if (dT > 1.0) {
          dT = 1.0;
        }
        tempFluid.setTemperature(tempFluid.getTemperature() + dT);
      } else {
        dT = -5.0; // Smaller step when no aqueous phase
        tempFluid.setTemperature(tempFluid.getTemperature() + dT);
      }
    }
    long optimizedTime = System.nanoTime() - start;

    double optimizedResult = tempFluid.getTemperature("C");
    System.out.printf("Optimized result: %.2f °C (%.2f ms, %d iterations)%n", optimizedResult,
        optimizedTime / 1e6, i);

    // Compare with standard multiphase
    WaterDewPointAnalyser analyzerStandard = new WaterDewPointAnalyser("standard", feedGasStream);
    analyzerStandard.setMethod("multiphase");
    analyzerStandard.setReferencePressure(referencePressure);

    start = System.nanoTime();
    double standardResult = analyzerStandard.getMeasuredValue("C");
    long standardTime = System.nanoTime() - start;

    System.out.printf("Standard result:  %.2f °C (%.2f ms)%n", standardResult, standardTime / 1e6);
    System.out.printf("Speedup: %.1fx faster with optimized approach%n",
        (double) standardTime / (bukacekTime + optimizedTime));
    System.out.println(StringUtils.repeat("=", 60));
  }

  /**
   * Test showing mixing rule setup is expensive.
   */
  @Test
  public void testMixingRulePerformance() {
    System.out.println("\n" + StringUtils.repeat("=", 60));
    System.out.println("MIXING RULE PERFORMANCE COMPARISON");
    System.out.println(StringUtils.repeat("=", 60));

    // Test with classic mixing rule (faster)
    long start = System.nanoTime();
    SystemInterface fluid1 = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid1.addComponent("methane", 83.55);
    fluid1.addComponent("ethane", 6.967);
    fluid1.addComponent("propane", 3.709);
    fluid1.addComponent("water", 0.1);
    fluid1.setMixingRule("classic"); // String version
    long classicTime = System.nanoTime() - start;
    System.out.printf("Classic mixing rule: %.2f ms%n", classicTime / 1e6);

    // Test with CPA mixing rule (slower)
    start = System.nanoTime();
    SystemInterface fluid2 = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid2.addComponent("methane", 83.55);
    fluid2.addComponent("ethane", 6.967);
    fluid2.addComponent("propane", 3.709);
    fluid2.addComponent("water", 0.1);
    fluid2.setMixingRule(10); // CPA mixing rule
    long cpaTime = System.nanoTime() - start;
    System.out.printf("CPA mixing rule (10): %.2f ms%n", cpaTime / 1e6);
    System.out.printf("CPA is %.1fx slower than classic%n", (double) cpaTime / classicTime);
    System.out.println(StringUtils.repeat("=", 60));
  }
}
