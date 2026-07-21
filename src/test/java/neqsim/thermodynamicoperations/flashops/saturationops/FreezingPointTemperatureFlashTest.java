package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class FreezingPointTemperatureFlashTest {
  @Test
  void testLNGFreezingPointFlashAfterFluidOnlyTPFlash() {
    SystemInterface system = new SystemSrkEos(120.35, 5.0);
    system.addComponent("CO2", 0.17);
    system.addComponent("nitrogen", 1.1011731548);
    system.addComponent("methane", 0.324);
    system.addComponent("ethane", 0.274);
    system.addComponent("propane", 0.0306);
    system.setMixingRule(2);
    system.setSolidPhaseCheck("CO2");

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> {
      operations.freezingPointTemperatureFlash();
    });

    double freezingTemperature = system.getTemperature();
    assertTrue(Double.isFinite(freezingTemperature));
    assertTrue(freezingTemperature > 90.0 && freezingTemperature < 220.0);

    FreezingPointTemperatureFlash flash = new FreezingPointTemperatureFlash(system);
    double residual = assertDoesNotThrow(flash::calcFunc);
    assertTrue(Math.abs(residual) < 1.0e-8);
  }
}
