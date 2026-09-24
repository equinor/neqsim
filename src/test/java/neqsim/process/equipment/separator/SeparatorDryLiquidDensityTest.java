package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the dry-vessel liquid density used by the Souders-Brown gas-load factor.
 */
public class SeparatorDryLiquidDensityTest {
  private Separator dryScrubber() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 50.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");
    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();
    Separator scrubber = new Separator("scrubber", feed);
    scrubber.setOrientation("vertical");
    scrubber.setInternalDiameter(1.5);
    scrubber.run();
    return scrubber;
  }

  @Test
  void defaultDryLiquidDensityIsWater() {
    assertEquals(1000.0, dryScrubber().getDryLiquidDensity(), 1e-12);
  }

  @Test
  void dryLiquidDensityRaisesGasLoadAsSoudersBrownPredicts() {
    Separator scrubber = dryScrubber();
    double kDefault = scrubber.getGasLoadFactor();
    double rhoGas = scrubber.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    scrubber.setDryLiquidDensity(600.0);
    double kCondensate = scrubber.getGasLoadFactor();
    double expectedRatio = Math.sqrt((1000.0 - rhoGas) / (600.0 - rhoGas));
    assertTrue(kCondensate > kDefault);
    assertEquals(expectedRatio, kCondensate / kDefault, 1e-9);

    scrubber.setDryLiquidDensity(0.0);
    assertEquals(kDefault, scrubber.getGasLoadFactor(), 1e-12);
  }
}
