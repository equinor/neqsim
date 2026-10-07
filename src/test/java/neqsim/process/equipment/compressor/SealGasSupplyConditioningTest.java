package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link SealGasSupplyConditioning}.
 *
 * @author neqsim
 * @version 1.0
 */
public class SealGasSupplyConditioningTest {

  private SystemInterface gas;

  /** Lean, water-saturated compressor gas (about 700 ppm mol water). */
  @BeforeEach
  public void setUp() {
    gas = new SystemSrkEos(273.15 + 40.0, 50.0);
    gas.addComponent("nitrogen", 0.011);
    gas.addComponent("CO2", 0.011);
    gas.addComponent("methane", 0.880);
    gas.addComponent("ethane", 0.068);
    gas.addComponent("propane", 0.017);
    gas.addComponent("i-butane", 0.005);
    gas.addComponent("n-butane", 0.002);
    gas.addComponent("n-pentane", 0.001);
    gas.addComponent("n-hexane", 0.0012);
    gas.addComponent("water", 0.0007);
    gas.setMixingRule("classic");
  }

  private SealGasSupplyConditioning build(double sourceBarg, double sourceC) {
    SealGasSupplyConditioning c = new SealGasSupplyConditioning("KA-4/5");
    c.setSealGas(gas);
    c.setSource(sourceBarg, "barg", sourceC, "C");
    c.setSupplyPressure(49.0, "barg");
    return c;
  }

  /** Expansion cools a dense gas and the expanded gas is colder than the source. */
  @Test
  public void expansionCoolsTheGas() {
    SealGasSupplyConditioning c = build(92.0, 45.0);
    assertTrue(c.getSupplyTemperatureC() < 45.0);
    assertTrue(c.getSupplyTemperatureC() > 15.0);
  }

  /** A hot discharge take-off keeps far more margin than a cool impeller take-off at the same pressure. */
  @Test
  public void hotSourceHasMoreMarginThanCoolSource() {
    double cool = build(92.0, 45.0).getMinimumMarginK();
    double hot = build(92.0, 80.0).getMinimumMarginK();
    assertTrue(hot > cool + 25.0);
  }

  /** The required inlet temperature restores the superheat and lies above the source temperature. */
  @Test
  public void requiredInletTemperatureRestoresMargin() {
    SealGasSupplyConditioning c = build(92.0, 45.0);
    assertFalse(c.isMarginMet());
    double required = c.calculateRequiredInletTemperatureC(120.0);
    assertTrue(required > 45.0);
    c.setHeaterOutletTemperature(required + 0.5, "C");
    assertTrue(c.isMarginMet());
  }

  /** The water dew point is reported for a gas that holds water and lies below the supply temperature when hot. */
  @Test
  public void waterDewPointIsReported() {
    SealGasSupplyConditioning c = build(92.0, 80.0);
    assertFalse(Double.isNaN(c.getWaterDewPointC()));
    assertTrue(c.getWaterDewPointC() < c.getSupplyTemperatureC());
  }

  /** Implied contaminant load and sump fill time. */
  @Test
  public void impliedLoadAndFillTime() {
    // 10 L/day of 940 kg/m3 liquid in 8000 kg/h of 18.4 g/mol gas = 9.4 kg/day in 0.245 MSm3/day
    double load = SealGasSupplyConditioning.impliedLiquidLoadKgPerMSm3(10.0, 940.0, 8000.0, 0.0184);
    assertEquals(38.4, load, 1.0);
    assertEquals(4.3, SealGasSupplyConditioning.sumpFillTimeDays(43.0, 10.0), 1.0e-9);
    assertTrue(Double.isInfinite(SealGasSupplyConditioning.sumpFillTimeDays(43.0, 0.0)));
  }
}
