package neqsim.process.equipment.powergeneration.gasturbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the ambient site rating, exhaust-temperature limit and waste-heat duty of {@link GasTurbineUnit}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class GasTurbineSiteRatingTest {

  /**
   * Build a run fuel-gas stream.
   *
   * @return fuel gas stream
   */
  private static Stream fuel() {
    SystemInterface sys = new SystemSrkEos(288.15, 30.0);
    sys.addComponent("methane", 0.90);
    sys.addComponent("ethane", 0.08);
    sys.addComponent("propane", 0.02);
    sys.setMixingRule("classic");
    sys.init(0);
    Stream s = new Stream("fuel", sys);
    s.setFlowRate(10.0, "kg/hr");
    s.run();
    return s;
  }

  /**
   * Power falls with ambient temperature and equals the ISO rating at 15 C; the default stays unchanged.
   */
  @Test
  void siteRatingFallsWithAmbientTemperature() {
    GasTurbineSpec spec = GasTurbineCatalog.get("LM2500");
    GasTurbineUnit gt = new GasTurbineUnit("GT", fuel(), spec);
    assertEquals(spec.getRatedPowerW(), gt.getAvailablePowerAtAmbientC(15.0), 1.0);
    double p5 = gt.getAvailablePowerAtAmbientC(5.0);
    double p30 = gt.getAvailablePowerAtAmbientC(30.0);
    assertTrue(p5 > spec.getRatedPowerW() && p30 < spec.getRatedPowerW());
    double[] curve = gt.getSiteRatingCurveW(new double[] {-5.0, 10.0, 25.0});
    assertTrue(curve[0] > curve[1] && curve[1] > curve[2]);
    gt.setAmbientTemperatureC(30.0);
    gt.setDemandedPower(10.0e6);
    gt.run();
    assertEquals(p30, gt.getAvailablePowerW(), 1.0);
  }

  /**
   * A vendor site-rating table replaces the linear lapse.
   */
  @Test
  void vendorTableDefinesSiteRating() {
    GasTurbineSpec spec = new GasTurbineSpec("FRAME40", GasTurbineSpec.TurbineType.INDUSTRIAL, 40.0e6, 9500.0, 100.0,
        800.0, 25.0, 10.0, "test frame");
    GasTurbineUnit gt = new GasTurbineUnit("GT", fuel(), spec);
    GasTurbinePerformanceMap map = GasTurbinePerformanceMap.fromSpec(spec);
    map.setSiteRatingFromPowerPoints(new double[] {5.0, 16.0}, new double[] {41.4e6, 39.3e6}, 40.0e6);
    gt.setPerformanceMap(map);
    assertEquals(41.4e6, gt.getAvailablePowerAtAmbientC(5.0), 1.0e3);
    assertEquals(39.3e6, gt.getAvailablePowerAtAmbientC(16.0), 1.0e3);
    double slope = (41.4e6 - 39.3e6) / 11.0;
    assertEquals(39.3e6 - slope * 14.0, gt.getAvailablePowerAtAmbientC(30.0), 1.0e3);
    assertThrows(IllegalArgumentException.class,
        () -> map.setSiteRatingTable(new double[] {20.0, 10.0}, new double[] {1.0, 0.9}));
  }

  /**
   * Exhaust temperature rises with ambient temperature and an exhaust limit cuts the power on a hot day.
   */
  @Test
  void exhaustTemperatureLimitDerates() {
    GasTurbineSpec spec = GasTurbineCatalog.get("LM2500");
    GasTurbineUnit gt = new GasTurbineUnit("GT", fuel(), spec);
    double base30 = gt.getAvailablePowerAtAmbientC(30.0);
    gt.setMaxExhaustTemperatureK(spec.getExhaustTemperatureK() + 2.0);
    assertEquals(gt.getAvailablePowerAtAmbientC(15.0), spec.getRatedPowerW(), 1.0);
    double limited30 = gt.getAvailablePowerAtAmbientC(30.0);
    assertTrue(limited30 < base30);
    gt.setAmbientTemperatureC(30.0);
    gt.setDemandedPower(0.5 * limited30);
    gt.run();
    assertTrue(gt.isExhaustTemperatureLimited());
    gt.setAmbientTemperatureC(15.0);
    gt.run();
    assertFalse(gt.isExhaustTemperatureLimited());
  }

  /**
   * Waste-heat duty scales with exhaust flow, temperature above the stack and effectiveness.
   */
  @Test
  void wasteHeatDutyFromExhaust() {
    GasTurbineSpec spec = GasTurbineCatalog.get("LM2500");
    GasTurbineUnit gt = new GasTurbineUnit("GT", fuel(), spec);
    gt.setDemandedPower(0.9 * spec.getRatedPowerW());
    gt.run();
    double hot = gt.getWasteHeatRecoveryDutyW(423.15, 1.0);
    double cold = gt.getWasteHeatRecoveryDutyW(523.15, 1.0);
    assertTrue(hot > cold && cold > 0.0);
    assertEquals(0.5 * hot, gt.getWasteHeatRecoveryDutyW(423.15, 0.5), 1.0e-6 * hot);
    assertTrue(gt.getFuelEnergyRateW() > gt.getShaftPowerW());
    assertEquals(0.0, gt.getWasteHeatRecoveryDutyW(2000.0, 1.0), 0.0);
    double exhaustAt15 = gt.getExhaustTemperatureK();
    gt.setAmbientTemperatureC(35.0);
    gt.run();
    assertTrue(gt.getExhaustTemperatureK() > exhaustAt15 - 5.0);
  }
}
