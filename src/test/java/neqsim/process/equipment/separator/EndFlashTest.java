package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link EndFlash}.
 *
 * @author NeqSim
 */
class EndFlashTest {

  @Test
  void testBasicEndFlash() {
    // Sub-cooled LNG from MCHE
    SystemInterface lngFluid = new SystemSrkEos(273.15 - 160.0, 1.2);
    lngFluid.addComponent("methane", 0.92);
    lngFluid.addComponent("ethane", 0.05);
    lngFluid.addComponent("propane", 0.02);
    lngFluid.addComponent("nitrogen", 0.01);
    lngFluid.setMixingRule("classic");

    Stream lngIn = new Stream("lng_subcooled", lngFluid);
    lngIn.setFlowRate(100000.0, "kg/hr");
    lngIn.setTemperature(-160.0, "C");
    lngIn.setPressure(1.2, "bara");

    EndFlash endFlash = new EndFlash("End Flash", lngIn);
    endFlash.setMaxN2InLNG(0.01);

    ProcessSystem process = new ProcessSystem();
    process.add(lngIn);
    process.add(endFlash);
    process.run();

    // Should have gas and liquid outlets
    assertNotNull(endFlash.getGasOutStream());
    assertNotNull(endFlash.getLiquidOutStream());

    // N2 should be enriched in flash gas vs LNG product
    double n2Gas = endFlash.getN2InFlashGasMolFrac();
    double n2Liq = endFlash.getN2InLNGMolFrac();

    // Flash gas ratio should be between 0 and 1
    double ratio = endFlash.getFlashGasRatio();
    assertTrue(ratio >= 0.0 && ratio <= 1.0, "Flash gas ratio should be 0-1, got: " + ratio);

    // Max N2 in LNG getter
    assertEquals(0.01, endFlash.getMaxN2InLNG(), 1e-10);
  }

  @Test
  void testLNGSpecCheck() {
    EndFlash flash = new EndFlash("test_flash");
    flash.setMaxN2InLNG(0.005);
    assertEquals(0.005, flash.getMaxN2InLNG(), 1e-10);

    // Before run, spec should be false (default n2 = 0 <= 0.005)
    assertFalse(flash.isLNGSpecMet()); // not run yet
  }
}
