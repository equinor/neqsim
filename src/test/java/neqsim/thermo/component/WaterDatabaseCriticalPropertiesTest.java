package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.WaterReferenceConstants;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.humidair.HumidAir;

/**
 * Distinguishes physical water reference data from established simulation parameters.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class WaterDatabaseCriticalPropertiesTest {
  /** Verifies the IAPWS reference and the saturation-correlation critical endpoint. */
  @Test
  void waterReferenceCriticalPointMatchesIapws() {
    assertEquals(647.096, WaterReferenceConstants.CRITICAL_TEMPERATURE_K, 1.0e-9);
    assertEquals(220.64, WaterReferenceConstants.CRITICAL_PRESSURE_BARA, 1.0e-9);
    assertEquals(22064000.0, WaterReferenceConstants.CRITICAL_PRESSURE_PA, 1.0e-6);
    assertEquals(WaterReferenceConstants.CRITICAL_PRESSURE_PA,
        HumidAir.saturationPressureWater(WaterReferenceConstants.CRITICAL_TEMPERATURE_K), 1.0e-6);
  }

  /** Guards the explicit compatibility boundary until model migration is qualified. */
  @Test
  void databaseModelParametersRetainEstablishedValues() {
    SystemSrkEos system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("water", 1.0);

    ComponentInterface water = system.getPhase(0).getComponent("water");
    assertEquals(647.30, water.getTC(), 1.0e-9);
    assertEquals(220.89, water.getPC(), 1.0e-9);
  }

  /** User model overrides remain effective without changing the physical reference constants. */
  @Test
  void modelOverridesAndCloningRemainIndependentOfReference() {
    SystemSrkEos system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("water", 1.0);
    ComponentInterface water = system.getPhase(0).getComponent("water");
    water.setTC(650.0);
    water.setPC(225.0);
    ComponentInterface copied = system.clone().getPhase(0).getComponent("water");
    assertEquals(650.0, copied.getTC(), 1.0e-9);
    assertEquals(225.0, copied.getPC(), 1.0e-9);
    assertEquals(647.096, WaterReferenceConstants.CRITICAL_TEMPERATURE_K, 1.0e-9);
    assertEquals(220.64, WaterReferenceConstants.CRITICAL_PRESSURE_BARA, 1.0e-9);
  }
}
