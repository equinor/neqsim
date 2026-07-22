package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link GasTurbineVendorPerformance}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class GasTurbineVendorPerformanceTest {
  /**
   * Build a simple natural-gas fuel stream.
   *
   * @return a run fuel-gas stream
   */
  private Stream fuelStream() {
    SystemInterface fuel = new SystemSrkEos(288.15, 20.0);
    fuel.addComponent("methane", 0.90);
    fuel.addComponent("ethane", 0.06);
    fuel.addComponent("propane", 0.02);
    fuel.addComponent("nitrogen", 0.02);
    fuel.setMixingRule("classic");
    Stream fs = new Stream("fuel", fuel);
    fs.setFlowRate(1000.0, "kg/hr");
    fs.setTemperature(25.0, "C");
    fs.setPressure(20.0, "bara");
    fs.run();
    return fs;
  }

  /**
   * Base-load and part-load fuel, efficiency and CO2 behaviour.
   */
  @Test
  void testPartLoadFuelAndCo2() {
    GasTurbineVendorPerformance gt = new GasTurbineVendorPerformance("GT", fuelStream());
    gt.setVendorRating(20.0, "MW", 0.35);
    gt.setAmbientDerating(15.0, 0.007);
    gt.setSiteAmbientTemperature(15.0);
    gt.setPartLoadHeatRateCoefficient(0.15);

    // Half load: load fraction 0.5, part-load efficiency below base, positive fuel.
    gt.setLoadDemand(10.0, "MW");
    gt.run(UUID.randomUUID());
    assertEquals(0.5, gt.getLoadFraction(), 1e-6);
    assertFalse(gt.isOverloaded());
    assertTrue(gt.getThermalEfficiency() < 0.35);
    assertTrue(gt.getThermalEfficiency() > 0.25);
    assertTrue(gt.getFuelFlowRate("kg/hr") > 0.0);

    // CO2 per kg of this gas is in the natural-gas range (~2.5-2.9).
    double co2PerKg = gt.getCO2EmissionRate("kg/hr") / gt.getFuelFlowRate("kg/hr");
    assertTrue(co2PerKg > 2.4 && co2PerKg < 3.0, "co2/kg=" + co2PerKg);

    // Fuel heat = load / efficiency.
    assertEquals(gt.getPower("MW") / gt.getThermalEfficiency(), gt.getFuelHeat("MW"), 1e-6);

    // At full load part-load penalty vanishes -> base efficiency.
    gt.setLoadDemand(20.0, "MW");
    gt.run(UUID.randomUUID());
    assertEquals(1.0, gt.getLoadFraction(), 1e-6);
    assertEquals(0.35, gt.getThermalEfficiency(), 1e-9);
  }

  /**
   * Ambient derating reduces the site rated power and can drive overload.
   */
  @Test
  void testAmbientDerating() {
    GasTurbineVendorPerformance gt = new GasTurbineVendorPerformance("GT", fuelStream());
    gt.setVendorRating(20.0, "MW", 0.35);
    gt.setAmbientDerating(15.0, 0.007);

    gt.setSiteAmbientTemperature(15.0);
    gt.setLoadDemand(1.0, "MW");
    gt.run(UUID.randomUUID());
    double ratedAtDesign = gt.getSiteRatedPower("MW");
    assertEquals(20.0, ratedAtDesign, 1e-6);

    gt.setSiteAmbientTemperature(35.0);
    gt.run(UUID.randomUUID());
    double ratedHot = gt.getSiteRatedPower("MW");
    // 20 degC hotter at 0.7 %/degC -> ~14 % power loss.
    assertTrue(ratedHot < ratedAtDesign);
    assertEquals(20.0 * (1.0 - 0.007 * 20.0), ratedHot, 1e-6);

    // Demand above the derated rating flags overload.
    gt.setLoadDemand(ratedHot * 1.1, "MW");
    gt.run(UUID.randomUUID());
    assertTrue(gt.isOverloaded());
  }
}
