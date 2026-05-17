package neqsim.process.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end integration tests for {@link BatchParameterEstimator}.
 *
 * <p>
 * These tests verify that the Levenberg-Marquardt optimizer actually converges when fitting process
 * model parameters to synthetic plant data. Each test generates "truth" data from a known process
 * configuration, then uses the estimator to recover the true parameter values from a deliberately
 * wrong initial guess.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
@Tag("slow")
class BatchParameterEstimatorIntegrationTest extends neqsim.NeqSimTest {

  /**
   * Tests single-parameter estimation: recovers compressor polytropic efficiency from outlet
   * temperature measurements at multiple discharge pressures.
   *
   * <p>
   * The test creates a compressor with a known efficiency (0.75), runs the process at several
   * discharge pressures to generate synthetic outlet temperature measurements, then uses
   * {@code BatchParameterEstimator} to recover the efficiency starting from an initial guess of
   * 0.60. Discharge pressure is used as the varying condition because
   * {@code setOutletPressure(double)} has a single-argument setter compatible with the
   * reflection-based property accessor.
   * </p>
   */
  @Test
  void testSingleParameterCompressorEfficiency() {
    // --- 1. Build process ---
    SystemInterface fluid = new SystemSrkEos(288.15, 30.0); // 15 C, 30 bara
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(15.0, "C");
    feed.setPressure(30.0, "bara");

    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(80.0); // uses single-arg setter (bara)
    comp.setPolytropicEfficiency(0.75);
    comp.setUsePolytropicCalc(true);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);

    // --- 2. Generate synthetic "plant data" at true efficiency = 0.75 ---
    // Vary discharge pressure to create distinguishable data points. The outlet
    // temperature changes with pressure ratio, producing observations at different
    // operating points for the same efficiency.
    double trueEfficiency = 0.75;
    double[] dischargePressures = {50.0, 60.0, 70.0, 80.0, 90.0};
    double[] measuredTemps = new double[dischargePressures.length];

    for (int i = 0; i < dischargePressures.length; i++) {
      comp.setOutletPressure(dischargePressures[i]);
      comp.setPolytropicEfficiency(trueEfficiency);
      process.run();
      measuredTemps[i] = comp.getOutletStream().getTemperature(); // Kelvin
    }

    // Verify truth data is reasonable (outlet temp > inlet temp for compression)
    for (double t : measuredTemps) {
      assertTrue(t > 288.15, "Outlet temp should be above inlet temp, got: " + t);
    }

    // --- 3. Set up estimator with wrong initial guess ---
    BatchParameterEstimator estimator = new BatchParameterEstimator(process);

    estimator.addTunableParameter("comp.polytropicEfficiency", "", 0.50, 0.95, 0.60);
    estimator.addMeasuredVariable("comp.outletStream.temperature", "K", 0.5);

    // Add data points: vary discharge pressure as condition, measure outlet temp
    for (int i = 0; i < dischargePressures.length; i++) {
      Map<String, Double> conditions = new HashMap<>();
      conditions.put("comp.outletPressure", dischargePressures[i]);

      Map<String, Double> measurements = new HashMap<>();
      measurements.put("comp.outletStream.temperature", measuredTemps[i]);

      estimator.addDataPoint(conditions, measurements);
    }

    estimator.setMaxIterations(100);

    // --- 4. Solve ---
    BatchResult result = estimator.solve();

    // --- 5. Verify convergence ---
    assertNotNull(result, "Result should not be null");
    assertTrue(result.isConverged(), "Optimizer should converge");

    double estimatedEfficiency = result.getEstimate(0);

    // Should recover the true efficiency within 5% tolerance
    assertEquals(trueEfficiency, estimatedEfficiency, 0.05,
        "Estimated efficiency should be close to true value. " + "True=" + trueEfficiency
            + " Estimated=" + estimatedEfficiency);

    // R-squared should be high for synthetic noiseless data
    if (!Double.isNaN(result.getRSquared())) {
      assertTrue(result.getRSquared() > 0.90,
          "R-squared should be high for noiseless data, got: " + result.getRSquared());
    }

    result.printSummary();
  }

  /**
   * Tests two-parameter estimation: recovers both heater outlet temperatures from mixed stream
   * temperature measurements at multiple operating conditions.
   *
   * <p>
   * Uses two heaters feeding into a mixer. The measured variable is the mixer outlet temperature.
   * Instead of varying flow rates (which requires two-argument setters), the outlet pressure of
   * each heater is varied. Both the heater set-point temperatures and the mixer outlet temperature
   * are used. The estimator must recover both heater set-points simultaneously.
   * </p>
   */
  @Test
  void testTwoParameterHeaterEstimation() {
    // --- 1. Build process ---
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed1 = new Stream("feed1", fluid);
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.setTemperature(20.0, "C");
    feed1.setPressure(50.0, "bara");

    neqsim.process.equipment.heatexchanger.Heater heater1 =
        new neqsim.process.equipment.heatexchanger.Heater("heater1", feed1);
    heater1.setOutletTemperature(273.15 + 50.0); // 50 C in K

    Stream feed2 = new Stream("feed2", fluid.clone());
    feed2.setFlowRate(100.0, "kg/hr");
    feed2.setTemperature(20.0, "C");
    feed2.setPressure(50.0, "bara");

    neqsim.process.equipment.heatexchanger.Heater heater2 =
        new neqsim.process.equipment.heatexchanger.Heater("heater2", feed2);
    heater2.setOutletTemperature(273.15 + 70.0); // 70 C in K

    neqsim.process.equipment.mixer.Mixer mixer = new neqsim.process.equipment.mixer.Mixer("mixer");
    mixer.addStream(heater1.getOutletStream());
    mixer.addStream(heater2.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed1);
    process.add(heater1);
    process.add(feed2);
    process.add(heater2);
    process.add(mixer);

    // --- 2. Generate truth data ---
    // Vary outlet pressures on heaters to create different operating conditions.
    // While pressure doesn't affect temperature much for ideal heaters, it
    // produces distinguishable model evaluations.
    double trueT1 = 273.15 + 50.0;
    double trueT2 = 273.15 + 70.0;

    // Use different heater2 outlet pressures to create data diversity
    double[] heater2Pressures = {40.0, 45.0, 50.0, 55.0};
    double[] mixerTemps = new double[heater2Pressures.length];

    for (int i = 0; i < heater2Pressures.length; i++) {
      heater1.setOutletTemperature(trueT1);
      heater2.setOutletTemperature(trueT2);
      heater2.setOutletPressure(heater2Pressures[i]);
      process.run();
      mixerTemps[i] = mixer.getOutletStream().getTemperature();
    }

    // --- 3. Set up estimator ---
    BatchParameterEstimator estimator = new BatchParameterEstimator(process);

    // Start with wrong guesses (40 C and 60 C instead of 50 and 70)
    estimator.addTunableParameter("heater1.outletTemperature", "K", 273.15 + 30.0, 273.15 + 80.0,
        273.15 + 40.0);
    estimator.addTunableParameter("heater2.outletTemperature", "K", 273.15 + 40.0, 273.15 + 90.0,
        273.15 + 60.0);
    estimator.addMeasuredVariable("mixer.outletStream.temperature", "K", 0.5);

    for (int i = 0; i < heater2Pressures.length; i++) {
      Map<String, Double> conditions = new HashMap<>();
      conditions.put("heater2.outletPressure", heater2Pressures[i]);

      Map<String, Double> measurements = new HashMap<>();
      measurements.put("mixer.outletStream.temperature", mixerTemps[i]);

      estimator.addDataPoint(conditions, measurements);
    }

    estimator.setMaxIterations(200);

    // --- 4. Solve ---
    BatchResult result = estimator.solve();

    // --- 5. Verify ---
    assertNotNull(result);
    assertTrue(result.isConverged(), "Optimizer should converge");

    double estT1 = result.getEstimate(0);
    double estT2 = result.getEstimate(1);

    // Should recover within ~3 K
    assertEquals(trueT1, estT1, 3.0,
        "Heater1 temp: true=" + (trueT1 - 273.15) + "C est=" + (estT1 - 273.15) + "C");
    assertEquals(trueT2, estT2, 3.0,
        "Heater2 temp: true=" + (trueT2 - 273.15) + "C est=" + (estT2 - 273.15) + "C");

    result.printSummary();
  }
}
