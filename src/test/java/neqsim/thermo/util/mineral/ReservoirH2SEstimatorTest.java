package neqsim.thermo.util.mineral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ReservoirH2SEstimator}.
 *
 * @author esol
 * @version 1.0
 */
public class ReservoirH2SEstimatorTest {

  /**
   * Creates a North Sea type gas condensate without H2S.
   *
   * @param temperatureC temperature [C]
   * @param pressure pressure [bara]
   * @param co2 CO2 mole fraction
   * @return fluid
   */
  private static SystemInterface fluid(double temperatureC, double pressure, double co2) {
    SystemInterface f = new SystemSrkEos(273.15 + temperatureC, pressure);
    f.addComponent("nitrogen", 0.005);
    f.addComponent("CO2", co2);
    f.addComponent("methane", 0.80 - co2);
    f.addComponent("ethane", 0.08);
    f.addComponent("propane", 0.04);
    f.addComponent("n-butane", 0.025);
    f.addComponent("n-hexane", 0.05);
    f.setMixingRule("classic");
    return f;
  }

  /** Estimates match the NCS (Haaland 1999) magnitude: ~1-3 ppm at 100 C, 5-40 ppm at 160 C. */
  @Test
  public void testNcsMagnitudeAndTrend() {
    ReservoirH2SEstimator est = new ReservoirH2SEstimator();
    ReservoirH2SEstimator.Estimate low = est.estimate(fluid(100.0, 320.0, 0.01));
    ReservoirH2SEstimator.Estimate high = est.estimate(fluid(160.0, 800.0, 0.04));
    assertTrue(low.isConverged() && high.isConverged());
    assertTrue(low.getH2SPpm() > 0.5 && low.getH2SPpm() < 6.0, "100 C: " + low.getH2SPpm());
    assertTrue(high.getH2SPpm() > 5.0 && high.getH2SPpm() < 60.0, "160 C: " + high.getH2SPpm());
    assertTrue(high.isBufferConsistent());
    assertTrue(high.getSideriteStabilityMargin() > 0.5);
  }

  /** Lower Fe-carbonate activity raises H2S as a^(-1/2); lower water activity lowers it as a_w^(3/4). */
  @Test
  public void testActivityScaling() {
    SystemInterface f = fluid(130.0, 450.0, 0.03);
    ReservoirH2SEstimator est = new ReservoirH2SEstimator();
    est.setFeCarbonateActivity(1.0);
    double pure = est.estimate(f).getH2SPpm();
    est.setFeCarbonateActivity(0.25);
    assertEquals(2.0, est.estimate(f).getH2SPpm() / pure, 0.01);
    est.setFeCarbonateActivity(1.0);
    est.setWaterActivity(0.9);
    assertEquals(Math.pow(0.9, 0.75), est.estimate(f).getH2SPpm() / pure, 0.01);
    assertThrows(IllegalArgumentException.class, () -> est.setFeCarbonateActivity(0.0));
  }

  /** Very low CO2 makes magnetite stable instead of siderite; the estimate flags the inconsistency. */
  @Test
  public void testLowCo2FlagsMagnetiteField() {
    ReservoirH2SEstimator est = new ReservoirH2SEstimator();
    ReservoirH2SEstimator.Estimate e = est.estimate(fluid(150.0, 600.0, 1.0e-6));
    assertTrue(e.getSideriteStabilityMargin() < 0.0);
    assertFalse(e.isBufferConsistent());
    assertTrue(e.getWarnings().toString().contains("siderite-magnetite"));
  }

  /** Mirrors the reservoir H2S example in docs/thermo/solid_gas_reaction_equilibrium.md. */
  @Test
  public void testDocumentationExample() {
    SystemInterface reservoirFluid = new SystemSrkEos(273.15 + 140.0, 550.0);
    reservoirFluid.addComponent("CO2", 0.03);
    reservoirFluid.addComponent("methane", 0.80);
    reservoirFluid.addComponent("ethane", 0.08);
    reservoirFluid.addComponent("propane", 0.04);
    reservoirFluid.addComponent("n-hexane", 0.05);
    reservoirFluid.setMixingRule("classic");

    ReservoirH2SEstimator estimator = new ReservoirH2SEstimator();
    estimator.setWaterActivity(0.97);
    ReservoirH2SEstimator.Estimate estimate = estimator.estimate(reservoirFluid);
    double originalH2sPpm = estimate.getH2SPpm();
    boolean consistent = estimate.isBufferConsistent();

    assertTrue(originalH2sPpm > 2.0 && originalH2sPpm < 40.0, "ppm " + originalH2sPpm);
    assertTrue(consistent);
  }

  /** Low temperature and sulfate minerals produce warnings; input fluid is not modified. */
  @Test
  public void testWarningsAndInputUnchanged() {
    SystemInterface f = fluid(50.0, 150.0, 0.005);
    ReservoirH2SEstimator est = new ReservoirH2SEstimator();
    est.setSulfateMineralsPresent(true);
    ReservoirH2SEstimator.Estimate e = est.estimate(f);
    assertTrue(e.getWarnings().toString().contains("70 C"));
    assertFalse(f.hasComponent("H2S"));
    ReservoirH2SEstimator.Estimate hot = est.estimate(fluid(150.0, 600.0, 0.03));
    assertTrue(hot.getWarnings().toString().contains("thermochemical sulfate reduction"));
  }
}
