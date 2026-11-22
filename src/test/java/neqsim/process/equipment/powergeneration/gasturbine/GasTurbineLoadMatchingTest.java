package neqsim.process.equipment.powergeneration.gasturbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Delivered-load and supplied vendor part-load heat-rate regressions.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class GasTurbineLoadMatchingTest {
  /** Noncombustible fuel cannot silently inherit a methane heating value. */
  @Test
  void rejectsNoncombustibleFuelInsteadOfInventingAHeatingValue() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    Stream fuel = new Stream("inert gas", fluid);
    fuel.setFlowRate(1.0, "kg/sec");
    fuel.run();
    GasTurbineUnit turbine = new GasTurbineUnit("GT", fuel, GasTurbineCatalog.get("LM2500"));
    turbine.setDemandedPower(5.0e6);
    assertThrows(IllegalStateException.class, turbine::run);
  }

  /** An overloaded turbine burns fuel at delivered load, while retaining requested-load diagnostics. */
  @Test
  void saturatedTurbineUsesDeliveredLoadForFuelAndExhaust() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream fuel = new Stream("fuel", fluid);
    fuel.setFlowRate(1.0, "kg/sec");
    fuel.run();
    GasTurbineUnit turbine = new GasTurbineUnit("GT", fuel, GasTurbineCatalog.get("LM2500"));
    double available = turbine.getAvailablePowerAtAmbient(288.15);
    turbine.setDemandedPower(available);
    turbine.run();
    double baseFuel = turbine.getFuelEnergyRateW();
    double baseExhaust = turbine.getExhaustTemperatureK();
    turbine.setDemandedPower(1.5 * available);
    turbine.run();
    assertTrue(turbine.isOverloaded());
    assertEquals(1.5, turbine.getLoadFraction(), 1.0e-12);
    assertEquals(available, turbine.getShaftPowerW(), 1.0e-6);
    assertEquals(baseFuel, turbine.getFuelEnergyRateW(), 1.0e-6);
    assertEquals(baseExhaust, turbine.getExhaustTemperatureK(), 1.0e-9);
  }

  /** Vendor data replaces the polynomial, interpolates linearly and owns copies of its input. */
  @Test
  void vendorTableInterpolatesWithoutGenericEfficiencyClipping() {
    GasTurbinePerformanceMap map = new GasTurbinePerformanceMap();
    double[] loads = {0.30, 0.50, 0.75, 1.0};
    double[] factors = {1.45, 1.18, 1.05, 1.0};
    map.setPartLoadHeatRateTable(loads, factors);
    loads[1] = 0.40;
    factors[1] = 100.0;
    assertEquals(9000.0 * 1.115, map.getHeatRate(9000.0, 0.625, 288.15), 1.0e-6);
    assertEquals(9000.0, map.getHeatRate(9000.0, 1.0, 288.15), 1.0e-6);
    assertEquals(9000.0 * 1.45, map.getHeatRate(9000.0, 0.1, 288.15), 1.0e-6);
    map.setHeatRatePolynomial(2.0, 0.0, 0.0);
    map.clearPartLoadHeatRateTable();
    assertEquals(18000.0, map.getHeatRate(9000.0, 0.5, 288.15), 1.0e-6);
  }

  /** Invalid or incomplete vendor data cannot produce a plausible-looking operating point. */
  @Test
  void rejectsNonFiniteAndUnnormalizedTables() {
    GasTurbinePerformanceMap map = new GasTurbinePerformanceMap();
    assertThrows(IllegalArgumentException.class,
        () -> map.setPartLoadHeatRateTable(new double[] {0.3, 1.0}, new double[] {Double.NaN, 1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> map.setPartLoadHeatRateTable(new double[] {0.3, 1.0}, new double[] {1.2, 0.9}));
    assertThrows(IllegalArgumentException.class,
        () -> map.setPartLoadHeatRateTable(new double[] {0.4, 1.0}, new double[] {1.2, 1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> map.setSiteRatingTable(new double[] {288.15, 303.15}, new double[] {1.0, Double.POSITIVE_INFINITY}));
    assertThrows(IllegalArgumentException.class, () -> map.getHeatRate(9000.0, Double.NaN, 288.15));
    map.setPartLoadHeatRateTable(new double[] {0.3, 1.0}, new double[] {1.3, 1.0});
    assertThrows(IllegalArgumentException.class, () -> map.setMinLoadFraction(0.2));
  }
}
