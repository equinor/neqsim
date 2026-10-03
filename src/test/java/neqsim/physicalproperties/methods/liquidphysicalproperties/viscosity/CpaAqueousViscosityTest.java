package neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the viscosity of CPA aqueous phases against independent reference data.
 *
 * <p>
 * Water: IAPWS-95 via CoolProp. Water/MEG and water/methanol: Melinder (2010) secondary-fluid correlations (CoolProp
 * INCOMP::MEG and INCOMP::MMA, mass-fraction basis).
 * </p>
 *
 * @author esol
 * @version 1.0
 */
class CpaAqueousViscosityTest {
  /**
   * Builds a CPA system with water and an optional inhibitor, flashes it and returns the aqueous viscosity.
   *
   * @param temperatureC temperature in C
   * @param pressureBara pressure in bara
   * @param inhibitor inhibitor component name, or null for pure water
   * @param inhibitorMassFraction inhibitor mass fraction in the liquid
   * @param inhibitorMolarMass inhibitor molar mass in g/mol
   * @return aqueous-phase viscosity in cP
   */
  private static double aqueousViscosity(double temperatureC, double pressureBara, String inhibitor,
      double inhibitorMassFraction, double inhibitorMolarMass) {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + temperatureC, pressureBara);
    if (inhibitorMassFraction < 1.0) {
      fluid.addComponent("water", (1.0 - inhibitorMassFraction) / 18.015);
    }
    if (inhibitor != null) {
      fluid.addComponent(inhibitor, inhibitorMassFraction / inhibitorMolarMass);
    }
    fluid.setMixingRule(10);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid.getPhase("aqueous").getViscosity("cP");
  }

  /** Pure water against IAPWS-95 from ambient to reservoir conditions. */
  @Test
  void pureWaterMatchesIapws() {
    assertEquals(1.0013, aqueousViscosity(20.0, 10.0, null, 0.0, 0.0), 1.0013 * 0.015);
    assertEquals(0.4684, aqueousViscosity(60.0, 100.0, null, 0.0, 0.0), 0.4684 * 0.015);
    assertEquals(0.2896, aqueousViscosity(100.0, 300.0, null, 0.0, 0.0), 0.2896 * 0.015);
    assertEquals(0.2100, aqueousViscosity(140.0, 542.0, null, 0.0, 0.0), 0.2100 * 0.015);
    assertEquals(0.1574, aqueousViscosity(180.0, 300.0, null, 0.0, 0.0), 0.1574 * 0.015);
  }

  /** The Lucas pressure term used to raise 20 C water viscosity with pressure; IAPWS-95 gives a slight decrease. */
  @Test
  void coldWaterViscosityDoesNotRiseWithPressure() {
    double low = Viscosity.calcWaterViscosity(293.15, 1.0);
    double high = Viscosity.calcWaterViscosity(293.15, 542.0);
    assertEquals(0.99, high / low, 0.01);
  }

  /** Water and MEG against Melinder at 50 wt% MEG. */
  @Test
  void waterMegMixtureMatchesMelinder() {
    assertEquals(3.156, aqueousViscosity(25.0, 10.0, "MEG", 0.5, 62.068), 3.156 * 0.06);
    assertEquals(1.375, aqueousViscosity(60.0, 10.0, "MEG", 0.5, 62.068), 1.375 * 0.06);
  }

  /** Water and methanol against Melinder; the viscosity maximum near 40 wt% must be reproduced. */
  @Test
  void waterMethanolMixtureMatchesMelinder() {
    assertEquals(3.017, aqueousViscosity(5.0, 10.0, "methanol", 0.4, 32.042), 3.017 * 0.08);
    assertEquals(1.589, aqueousViscosity(25.0, 10.0, "methanol", 0.4, 32.042), 1.589 * 0.08);
  }

  /** Pure methanol against CoolProp (Methanol equation of state). */
  @Test
  void pureMethanolMatchesReference() {
    assertEquals(0.5867, aqueousViscosity(20.0, 5.0, "methanol", 1.0, 32.042), 0.5867 * 0.03);
  }
}
