package neqsim.thermo.util.hydrogen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Physical and input-validation checks for the solid hydride screening bridge.
 *
 * @author ESOL
 * @version 1.0
 */
public class HydrideStorageScreeningTest extends neqsim.NeqSimTest {

  /** Checks stoichiometric and crystallographic capacities against reported LiBH3 data. */
  @Test
  public void testPublishedLithiumBorohydrideCapacities() {
    // Geldasa and Dejene (2026), doi:10.1007/s00894-026-06861-x, tables 1 and 5.
    double gravimetric = HydrideStorageScreening.gravimetricCapacityPercent(6.94 + 10.81, 3);
    double volumetric = HydrideStorageScreening.volumetricHydrogenDensityGPerL(3, 1, 29.45);
    assertEquals(14.56, gravimetric, 0.05);
    assertEquals(170.6, volumetric, 0.5);
    assertEquals(2.0 * volumetric, HydrideStorageScreening.volumetricHydrogenDensityGPerL(3, 2, 29.45), 1.0e-10);
  }

  /** Checks Gibbs balance, inverse calculation, and suppression of desorption at higher pressure. */
  @Test
  public void testPressureDependentEquilibrium() {
    double entropy = 130.7;
    double enthalpy = 457.7 * entropy;
    assertEquals(457.7, HydrideStorageScreening.equilibriumTemperatureK(1.0, enthalpy, entropy), 1.0e-10);
    double temperatureAtTenBar = HydrideStorageScreening.equilibriumTemperatureK(10.0, enthalpy, entropy);
    assertTrue(temperatureAtTenBar > 457.7);
    assertEquals(10.0, HydrideStorageScreening.equilibriumFugacityBar(temperatureAtTenBar, enthalpy, entropy), 1.0e-10);
    assertEquals(0.0,
        HydrideStorageScreening.desorptionGibbsEnergyJPerMolH2(temperatureAtTenBar, 10.0, enthalpy, entropy), 1.0e-8);
    assertTrue(HydrideStorageScreening.desorptionGibbsEnergyJPerMolH2(457.7, 10.0, enthalpy, entropy) > 0.0);
  }

  /** Rejects invalid masses, cell volumes, enthalpies, and hydrogen fugacities. */
  @Test
  public void testInvalidPhysicalInputs() {
    assertThrows(IllegalArgumentException.class, () -> HydrideStorageScreening.gravimetricCapacityPercent(0.0, 3));
    assertThrows(IllegalArgumentException.class,
        () -> HydrideStorageScreening.volumetricHydrogenDensityGPerL(3, 1, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> HydrideStorageScreening.equilibriumTemperatureK(0.0, 60000.0, 130.7));
    assertThrows(IllegalArgumentException.class,
        () -> HydrideStorageScreening.equilibriumTemperatureK(1.0e10, 60000.0, 130.7));
    assertThrows(IllegalArgumentException.class,
        () -> HydrideStorageScreening.desorptionGibbsEnergyJPerMolH2(300.0, Double.POSITIVE_INFINITY, 60000.0, 130.7));
  }
}
