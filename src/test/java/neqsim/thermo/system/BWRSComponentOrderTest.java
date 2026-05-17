package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test that BWRS gives the same results regardless of the order components are added, and that
 * components without MBWR-32 parameters produce valid (non-NaN) results.
 *
 * @author copilot
 * @version 1.0
 */
public class BWRSComponentOrderTest {

  /**
   * Test that methane-ethane order does not matter.
   */
  @Test
  public void testMethaneEthaneOrderIndependence() {
    SystemInterface bwrs1 = new SystemBWRSEos(298.15, 10.0);
    bwrs1.addComponent("methane", 0.8);
    bwrs1.addComponent("ethane", 0.2);
    bwrs1.createDatabase(true);
    bwrs1.setMixingRule(2);
    new ThermodynamicOperations(bwrs1).TPflash();
    bwrs1.initProperties();

    SystemInterface bwrs2 = new SystemBWRSEos(298.15, 10.0);
    bwrs2.addComponent("ethane", 0.2);
    bwrs2.addComponent("methane", 0.8);
    bwrs2.createDatabase(true);
    bwrs2.setMixingRule(2);
    new ThermodynamicOperations(bwrs2).TPflash();
    bwrs2.initProperties();

    assertEquals(bwrs1.getPhase(0).getDensity(), bwrs2.getPhase(0).getDensity(), 1e-10,
        "Density should not depend on component order");
    assertEquals(bwrs1.getPhase(0).getZ(), bwrs2.getPhase(0).getZ(), 1e-10,
        "Z should not depend on component order");
    assertEquals(bwrs1.getPhase(0).getCp(), bwrs2.getPhase(0).getCp(), 1e-10,
        "Cp should not depend on component order");
    assertEquals(bwrs1.getPhase(0).getEnthalpy(), bwrs2.getPhase(0).getEnthalpy(), 1e-10,
        "H should not depend on component order");

    double fug1CH4 = bwrs1.getPhase(0).getComponent("methane").getFugacityCoefficient();
    double fug2CH4 = bwrs2.getPhase(0).getComponent("methane").getFugacityCoefficient();
    double fug1C2 = bwrs1.getPhase(0).getComponent("ethane").getFugacityCoefficient();
    double fug2C2 = bwrs2.getPhase(0).getComponent("ethane").getFugacityCoefficient();
    assertEquals(fug1CH4, fug2CH4, 1e-10, "CH4 fugacity should not depend on order");
    assertEquals(fug1C2, fug2C2, 1e-10, "C2H6 fugacity should not depend on order");
  }

  /**
   * Test that methane-propane order does not matter, and propane (no MBWR-32 params) gives valid
   * non-NaN fugacities.
   */
  @Test
  public void testMethanePropaneOrderIndependence() {
    SystemInterface bwrs1 = new SystemBWRSEos(298.15, 10.0);
    bwrs1.addComponent("methane", 0.9);
    bwrs1.addComponent("propane", 0.1);
    bwrs1.createDatabase(true);
    bwrs1.setMixingRule(2);
    new ThermodynamicOperations(bwrs1).TPflash();
    bwrs1.initProperties();

    SystemInterface bwrs2 = new SystemBWRSEos(298.15, 10.0);
    bwrs2.addComponent("propane", 0.1);
    bwrs2.addComponent("methane", 0.9);
    bwrs2.createDatabase(true);
    bwrs2.setMixingRule(2);
    new ThermodynamicOperations(bwrs2).TPflash();
    bwrs2.initProperties();

    // Verify no NaN values
    assertFalse(Double.isNaN(bwrs1.getPhase(0).getDensity()), "Density should not be NaN");
    assertFalse(Double.isNaN(bwrs1.getPhase(0).getZ()), "Z should not be NaN");

    double fug1CH4 = bwrs1.getPhase(0).getComponent("methane").getFugacityCoefficient();
    double fug2CH4 = bwrs2.getPhase(0).getComponent("methane").getFugacityCoefficient();
    double fug1C3 = bwrs1.getPhase(0).getComponent("propane").getFugacityCoefficient();
    double fug2C3 = bwrs2.getPhase(0).getComponent("propane").getFugacityCoefficient();

    assertFalse(Double.isNaN(fug1CH4), "CH4 fugacity should not be NaN (order 1)");
    assertFalse(Double.isNaN(fug2CH4), "CH4 fugacity should not be NaN (order 2)");
    assertFalse(Double.isNaN(fug1C3), "C3H8 fugacity should not be NaN (order 1)");
    assertFalse(Double.isNaN(fug2C3), "C3H8 fugacity should not be NaN (order 2)");

    assertEquals(bwrs1.getPhase(0).getDensity(), bwrs2.getPhase(0).getDensity(), 1e-10,
        "Density should not depend on component order");
    assertEquals(bwrs1.getPhase(0).getZ(), bwrs2.getPhase(0).getZ(), 1e-10,
        "Z should not depend on component order");
    assertEquals(fug1CH4, fug2CH4, 1e-10, "CH4 fugacity should not depend on order");
    assertEquals(fug1C3, fug2C3, 1e-10, "C3H8 fugacity should not depend on order");
  }

  /**
   * Test a three-component mixture (methane + ethane + nitrogen). Nitrogen has no MBWR-32 params.
   * Verify results are order-independent and valid.
   */
  @Test
  public void testThreeComponentOrderIndependence() {
    SystemInterface bwrs1 = new SystemBWRSEos(298.15, 10.0);
    bwrs1.addComponent("methane", 0.7);
    bwrs1.addComponent("ethane", 0.2);
    bwrs1.addComponent("nitrogen", 0.1);
    bwrs1.createDatabase(true);
    bwrs1.setMixingRule(2);
    new ThermodynamicOperations(bwrs1).TPflash();
    bwrs1.initProperties();

    SystemInterface bwrs2 = new SystemBWRSEos(298.15, 10.0);
    bwrs2.addComponent("nitrogen", 0.1);
    bwrs2.addComponent("methane", 0.7);
    bwrs2.addComponent("ethane", 0.2);
    bwrs2.createDatabase(true);
    bwrs2.setMixingRule(2);
    new ThermodynamicOperations(bwrs2).TPflash();
    bwrs2.initProperties();

    assertEquals(bwrs1.getPhase(0).getDensity(), bwrs2.getPhase(0).getDensity(), 1e-10,
        "Density should be order-independent for 3-component mixture");
    assertEquals(bwrs1.getPhase(0).getZ(), bwrs2.getPhase(0).getZ(), 1e-10,
        "Z should be order-independent for 3-component mixture");

    for (String comp : new String[] {"methane", "ethane", "nitrogen"}) {
      double fug1 = bwrs1.getPhase(0).getComponent(comp).getFugacityCoefficient();
      double fug2 = bwrs2.getPhase(0).getComponent(comp).getFugacityCoefficient();
      assertFalse(Double.isNaN(fug1), comp + " fugacity should not be NaN (order 1)");
      assertFalse(Double.isNaN(fug2), comp + " fugacity should not be NaN (order 2)");
      assertEquals(fug1, fug2, 1e-10, comp + " fugacity should be order-independent");
    }
  }

  /**
   * Test with a realistic natural gas composition (7 components, only 2 have MBWR params).
   */
  @Test
  public void testNaturalGasMixture() {
    SystemInterface bwrs = new SystemBWRSEos(298.15, 50.0);
    bwrs.addComponent("nitrogen", 0.02);
    bwrs.addComponent("CO2", 0.03);
    bwrs.addComponent("methane", 0.80);
    bwrs.addComponent("ethane", 0.08);
    bwrs.addComponent("propane", 0.04);
    bwrs.addComponent("i-butane", 0.015);
    bwrs.addComponent("n-butane", 0.015);
    bwrs.createDatabase(true);
    bwrs.setMixingRule(2);
    new ThermodynamicOperations(bwrs).TPflash();
    bwrs.initProperties();

    double density = bwrs.getPhase(0).getDensity();
    double z = bwrs.getPhase(0).getZ();

    assertFalse(Double.isNaN(density), "Density should not be NaN for natural gas");
    assertFalse(Double.isNaN(z), "Z should not be NaN for natural gas");
    assertFalse(Double.isInfinite(density), "Density should not be Infinite");
    assertFalse(Double.isInfinite(z), "Z should not be Infinite");

    // All fugacity coefficients should be valid
    for (int i = 0; i < bwrs.getPhase(0).getNumberOfComponents(); i++) {
      double fug = bwrs.getPhase(0).getComponent(i).getFugacityCoefficient();
      assertFalse(Double.isNaN(fug),
          "Fugacity of " + bwrs.getPhase(0).getComponent(i).getName() + " should not be NaN");
      assertFalse(Double.isInfinite(fug),
          "Fugacity of " + bwrs.getPhase(0).getComponent(i).getName() + " should not be Infinite");
    }
  }
}
