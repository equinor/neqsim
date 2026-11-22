package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.wellplanning.WellboreThermalModel.FlowDirection;

/**
 * Analytic-limit tests of {@link WellboreThermalModel}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class WellboreThermalModelTest {

  /**
   * Zero flow rate gives the undisturbed geothermal profile.
   */
  @Test
  public void zeroFlowGivesFormationTemperature() {
    WellboreThermalModel m = new WellboreThermalModel().setSurfaceTemperatureC(10.0).setGeothermalGradient(0.03)
        .setTotalDepth(3000.0).setMassFlowRate(0.0).setInletTemperatureC(150.0);
    assertEquals(10.0 + 273.15 + 0.03 * 1500.0, m.temperatureAt(1500.0), 1.0e-9);
    assertEquals(0.0, m.getRelaxationDistance(), 1.0e-12);
  }

  /**
   * Very high rate keeps the producing fluid at the bottom-hole temperature (infinite relaxation distance).
   */
  @Test
  public void veryHighRateProducerKeepsInletTemperature() {
    WellboreThermalModel m = new WellboreThermalModel().setSurfaceTemperatureC(10.0).setGeothermalGradient(0.03)
        .setTotalDepth(3000.0).setMassFlowRate(1.0e7).setInletTemperatureC(100.0);
    assertEquals(100.0 + 273.15, m.temperatureAt(0.0), 0.05);
    assertEquals(100.0 + 273.15, m.temperatureAt(1500.0), 0.05);
  }

  /**
   * Producer is warmer than the formation along the well; injector is colder than the formation.
   */
  @Test
  public void producerLagsAboveAndInjectorBelowFormation() {
    WellboreThermalModel p = new WellboreThermalModel().setSurfaceTemperatureC(5.0).setGeothermalGradient(0.03)
        .setTotalDepth(3000.0).setMassFlowRate(15.0).setInletTemperatureC(5.0 + 0.03 * 3000.0);
    double formation = p.getFormationTemperatureK(1500.0);
    assertTrue(p.temperatureAt(1500.0) > formation);
    assertTrue(p.temperatureAt(0.0) > p.getFormationTemperatureK(0.0));
    assertEquals(p.getFormationTemperatureK(3000.0), p.temperatureAt(3000.0), 1.0e-9);
    WellboreThermalModel inj = new WellboreThermalModel().setFlowDirection(FlowDirection.INJECTION)
        .setSurfaceTemperatureC(5.0).setGeothermalGradient(0.03).setTotalDepth(3000.0).setMassFlowRate(30.0)
        .setInletTemperatureC(20.0);
    assertEquals(20.0 + 273.15, inj.temperatureAt(0.0), 1.0e-9);
    assertTrue(inj.temperatureAt(3000.0) < inj.getFormationTemperatureK(3000.0));
    assertTrue(inj.temperatureAt(3000.0) > 20.0 + 273.15);
  }

  /**
   * Closed-form producer solution agrees with an independent numerical integration of the energy balance dT/ds = (T_f -
   * T)/A.
   */
  @Test
  public void producerClosedFormMatchesNumericalIntegration() {
    WellboreThermalModel m = new WellboreThermalModel().setSurfaceTemperatureC(8.0).setGeothermalGradient(0.031)
        .setTotalDepth(2800.0).setMassFlowRate(12.0).setInletTemperatureC(8.0 + 0.031 * 2800.0 + 3.0);
    double a = m.getRelaxationDistance();
    int n = 200000;
    double ds = 2800.0 / n;
    double t = 8.0 + 273.15 + 0.031 * 2800.0 + 3.0;
    for (int i = 0; i < n; i++) {
      double depth = 2800.0 - (i + 0.5) * ds;
      double tf = m.getFormationTemperatureK(depth);
      t += (tf - t) / a * ds;
    }
    assertEquals(t, m.temperatureAt(0.0), 0.02);
  }

  /**
   * Hasan-Kabir time function against hand-evaluated values.
   */
  @Test
  public void timeFunctionMatchesHandValues() {
    WellboreThermalModel m = new WellboreThermalModel().setWellboreRadius(0.1).setFormationProperties(2.0, 1.0e-6)
        .setFlowingTimeDays(1.0e4 / 86400.0);
    assertEquals(1.1281 * 0.7, m.getTimeFunction(), 1.0e-6);
    m.setFlowingTimeDays(1.0e5 / 86400.0);
    assertEquals((0.4063 + 0.5 * Math.log(10.0)) * 1.06, m.getTimeFunction(), 1.0e-6);
  }

  /**
   * Relaxation distance equals the closed-form A = w cp (ke + rU f) / (2 pi rU ke).
   */
  @Test
  public void relaxationDistanceMatchesFormula() {
    WellboreThermalModel m = new WellboreThermalModel().setMassFlowRate(10.0).setFluidHeatCapacity(3000.0)
        .setOverallHeatTransferCoefficient(20.0).setTubingRadii(0.05, 0.06).setFormationProperties(2.0, 1.0e-6)
        .setWellboreRadius(0.1).setFlowingTimeDays(1.0e5 / 86400.0);
    double f = m.getTimeFunction();
    double expected = 10.0 * 3000.0 * (2.0 + 0.06 * 20.0 * f) / (2.0 * Math.PI * 0.06 * 20.0 * 2.0);
    assertEquals(expected, m.getRelaxationDistance(), 1.0e-6 * expected);
  }

  /**
   * Overall coefficient for one conduction layer equals k / (r_o ln(r_o / r_i)) when the film resistance vanishes.
   */
  @Test
  public void overallCoefficientSingleLayerMatchesCylinderConduction() {
    double u = WellboreThermalModel.overallHeatTransferCoefficient(1.0e12, new double[] {0.05, 0.06},
        new double[] {40.0}, 1);
    assertEquals(40.0 / (0.06 * Math.log(0.06 / 0.05)), u, 1.0e-6 * u);
    double uIns = WellboreThermalModel.overallHeatTransferCoefficient(1.0e12, new double[] {0.05, 0.06, 0.09},
        new double[] {40.0, 0.05}, 1);
    double rPerLength = Math.log(0.06 / 0.05) / (2.0 * Math.PI * 40.0) + Math.log(0.09 / 0.06) / (2.0 * Math.PI * 0.05);
    assertEquals(1.0 / (2.0 * Math.PI * 0.06 * rPerLength), uIns, 1.0e-6 * uIns);
    assertThrows(IllegalArgumentException.class, () -> WellboreThermalModel.overallHeatTransferCoefficient(100.0,
        new double[] {0.05, 0.04}, new double[] {40.0}, 1));
  }

  /**
   * Shut-in cooldown is exponential, consistent between temperature and time-to-threshold.
   */
  @Test
  public void shutInCooldownIsConsistent() {
    WellboreThermalModel m = new WellboreThermalModel().setSurfaceTemperatureC(4.0).setGeothermalGradient(0.0)
        .setTotalDepth(1000.0).setOverallHeatTransferCoefficient(10.0).setFlowingTimeDays(30.0);
    double tau = m.getShutInTimeConstant();
    assertTrue(tau > 3600.0);
    double t0 = 70.0 + 273.15;
    double thr = 20.0 + 273.15;
    double hours = m.timeToReachTemperatureHours(500.0, t0, thr);
    assertEquals(thr, m.shutInTemperatureK(500.0, t0, hours), 1.0e-6);
    double tf = m.getFormationTemperatureK(500.0);
    assertEquals(tf + (t0 - tf) * Math.exp(-1.0), m.shutInTemperatureK(500.0, t0, tau / 3600.0), 1.0e-9);
    assertEquals(0.0, m.timeToReachTemperatureHours(500.0, 15.0 + 273.15, thr), 0.0);
    assertTrue(Double.isInfinite(m.timeToReachTemperatureHours(500.0, t0, 2.0 + 273.15)));
    assertFalse(Double.isNaN(hours));
  }
}
