package neqsim.process.mechanicaldesign.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LineSizingLofCalculator}.
 */
public class LineSizingLofCalculatorTest {
  /**
   * A low-velocity case should give a LOW likelihood band.
   */
  @Test
  void testLowLikelihood() {
    LineSizingLofCalculator calc = new LineSizingLofCalculator();
    calc.setFlowConditions(120.0, 3.0);
    calc.setCriteria(122.0, 20.0, 15000.0);
    calc.calcScreening();

    assertTrue(calc.getErosionalVelocity() > 0.0, "Erosional velocity should be positive");
    assertEquals("LOW", calc.getLikelihoodBand(), "Low velocity should band LOW");
    assertNotNull(calc.toJson());
  }

  /**
   * A velocity above the erosional velocity should give a HIGH likelihood band.
   */
  @Test
  void testHighLikelihoodFromErosion() {
    LineSizingLofCalculator calc = new LineSizingLofCalculator();
    calc.setFlowConditions(120.0, 30.0);
    calc.setCriteria(122.0, 20.0, 1.0e9);
    calc.calcScreening();

    assertTrue(calc.getErosionUtilization() >= 1.0, "Velocity above Ve should give utilization >= 1");
    assertEquals("HIGH", calc.getLikelihoodBand(), "Erosion utilization >= 1 should band HIGH");
    assertTrue(calc.isExceedsRecommendedVelocity(), "30 m/s should exceed a 20 m/s limit");
  }

  /**
   * A high kinetic energy should drive the likelihood band even when erosion is moderate.
   */
  @Test
  void testKineticEnergyDrivesBand() {
    LineSizingLofCalculator calc = new LineSizingLofCalculator();
    calc.setFlowConditions(300.0, 8.0);
    calc.setCriteria(250.0, 20.0, 5000.0);
    calc.calcScreening();

    assertEquals(300.0 * 8.0 * 8.0, calc.getKineticEnergy(), 1e-6, "rho*v^2 should match");
    assertTrue(calc.getLikelihoodOfFailure() >= 1.0, "High normalized KE should drive LOF up");
    assertEquals("HIGH", calc.getLikelihoodBand());
  }

  /**
   * The {@code fromStream} bridge should populate density and velocity from a run NeqSim process stream.
   */
  @Test
  void testFromProcessStream() {
    neqsim.thermo.system.SystemSrkEos fluid = new neqsim.thermo.system.SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    neqsim.process.equipment.stream.Stream feed = new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    neqsim.process.processmodel.ProcessSystem process = new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);
    process.run();
    feed.getFluid().initProperties();

    LineSizingLofCalculator calc = new LineSizingLofCalculator();
    calc.fromStream(feed, 0.2);
    calc.setCriteria(122.0, 20.0, 15000.0);
    calc.calcScreening();

    assertTrue(calc.getErosionalVelocity() > 0.0, "Erosional velocity should be positive");
    assertTrue(calc.getKineticEnergy() > 0.0, "Kinetic energy should be populated from the stream");
    assertTrue(calc.getLikelihoodOfFailure() >= 0.0, "Likelihood of failure should be non-negative");
    assertNotNull(calc.toJson());
  }
}
