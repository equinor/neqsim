package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemLeachmanEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class FreezingPointTemperatureFlashTest {
  /** Verifies explicit para-hydrogen freezing at the calibrated triple point. */
  @Test
  void testParaHydrogenFreezingPoint() {
    SystemInterface system = new SystemLeachmanEos(13.6, 0.07042, "para-hydrogen", true);
    system.setSolidPhaseCheck("para-hydrogen");
    ThermodynamicOperations operations = new ThermodynamicOperations(system);

    FreezingPointResult result = assertDoesNotThrow(operations::freezingPointTemperatureFlashResult);

    assertTrue(result.isConverged());
    assertEquals(13.8033, result.getTemperature("K"), 1.0e-4);
    assertEquals(result.getTemperature("K"), system.getTemperature(), 0.0);
    assertEquals("para-hydrogen", result.getComponentName());
    assertTrue(Math.abs(result.getResidual()) < 1.0e-10);
  }

  /** Verifies that invalid bracket trials do not abort valid para-hydrogen melting states. */
  @Test
  void testParaHydrogenFreezingPointSkipsInvalidBracketTrials() {
    double[] pressuresBara = {3.512706909625152, 18.76432785899884};
    for (double pressureBara : pressuresBara) {
      SystemInterface system = new SystemLeachmanEos(13.8, pressureBara, "para-hydrogen", true);
      system.setSolidPhaseCheck("para-hydrogen");
      ThermodynamicOperations operations = new ThermodynamicOperations(system);

      FreezingPointResult result = assertDoesNotThrow(operations::freezingPointTemperatureFlashResult);

      assertTrue(result.isConverged(), result.getFailureReason());
      assertTrue(Double.isFinite(result.getTemperature("K")));
      assertTrue(Math.abs(result.getResidual()) < 1.0e-8);
    }
  }

  /** Verifies that an unsupported solid request fails without changing the system temperature. */
  @Test
  void testUnsupportedSolidRequestRestoresTemperature() {
    SystemInterface system = new SystemSrkEos(140.0, 5.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    system.setSolidPhaseCheck("CO2");
    FreezingPointTemperatureFlash flash = new FreezingPointTemperatureFlash(system);

    IllegalStateException exception = assertThrows(IllegalStateException.class, flash::run);

    assertTrue(exception.getMessage().contains("No component was enabled for solid checking"));
    assertEquals(140.0, system.getTemperature(), 0.0);
    assertTrue(!flash.getResult().isConverged());
  }

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

  /** Rejects invalid pressure without changing the supplied state or reusing an earlier result. */
  @Test
  void invalidPressureResetsResultBeforeStateMutation() {
    SystemInterface system = wetCpaGas();
    FreezingPointTemperatureFlash flash = new FreezingPointTemperatureFlash(system);
    assertDoesNotThrow(flash::run);
    assertTrue(flash.getResult().isConverged());
    for (double invalid : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      system.setPressure(invalid);
      double temperature = system.getTemperature();
      int phases = system.getNumberOfPhases();
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, flash::run);
      assertTrue(exception.getMessage().contains("positive finite absolute pressure"));
      assertTrue(!flash.getResult().isConverged());
      assertEquals(0, flash.getResult().getIterations());
      assertEquals(0, flash.Niterations);
      assertEquals(temperature, system.getTemperature(), 0.0);
      assertEquals(phases, system.getNumberOfPhases());
      assertThrows(IllegalStateException.class, () -> flash.getResult().getTemperature("K"));
    }
    system.setPressure(50.0);
    assertDoesNotThrow(flash::run);
    assertTrue(flash.getResult().isConverged());
    double committedResidual = assertDoesNotThrow(flash::calcFunc);
    assertTrue(Math.abs(committedResidual) < 1.0e-10);
    assertEquals(committedResidual, flash.getResult().getResidual(), 1.0e-12);
  }

  /** An absent solid configuration must leave an explicit failed diagnostic. */
  @Test
  void missingSolidConfigurationHasFailedResult() {
    SystemInterface system = new SystemSrkCPAstatoil(300.0, 50.0);
    system.addComponent("methane", 0.9999);
    system.addComponent("water", 0.0001);
    system.setMixingRule(10);
    FreezingPointTemperatureFlash flash = new FreezingPointTemperatureFlash(system);
    assertThrows(IllegalStateException.class, flash::run);
    assertTrue(!flash.getResult().isConverged());
    assertTrue(flash.getResult().getFailureReason().contains("Solid phase is not configured"));
    assertEquals(300.0, system.getTemperature(), 0.0);
  }

  /** A failed enabled solid cannot be hidden by another component's successful freezing point. */
  @Test
  void everyEnabledSolidMustBeQualified() {
    SystemInterface system = new SystemSrkCPAstatoil(273.15, 20.0);
    system.addComponent("methane", 0.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(10);
    system.setSolidPhaseCheck("water");
    system.getPhase(0).getComponent("methane").setSolidCheck(true);
    FreezingPointTemperatureFlash flash = new FreezingPointTemperatureFlash(system);
    assertThrows(IllegalStateException.class, flash::run);
    assertTrue(!flash.getResult().isConverged());
    assertEquals("methane", flash.getResult().getComponentName());
    assertEquals(273.15, system.getTemperature(), 0.0);
  }

  /**
   * Creates a gas with trace water and an explicitly configured ice reference.
   *
   * @return configured Statoil-CPA gas
   */
  private SystemInterface wetCpaGas() {
    SystemInterface system = new SystemSrkCPAstatoil(300.0, 50.0);
    system.addComponent("methane", 0.9999);
    system.addComponent("water", 0.0001);
    system.setMixingRule(10);
    system.setSolidPhaseCheck("water");
    return system;
  }
}
