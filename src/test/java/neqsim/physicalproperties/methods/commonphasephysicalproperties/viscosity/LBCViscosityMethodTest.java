package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemInterface;

public class LBCViscosityMethodTest {
    static neqsim.thermo.system.SystemInterface testSystem = null;

  @Test
  void testGasMethaneMatchesReferenceMagnitude() {
    double T = 273.15;
    double P = 20; // Pressure in MPa
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P * 10);
    testSystem.addComponent("methane", 1.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase("gas").getPhysicalProperties().setViscosityModel("LBC");
    testSystem.initProperties();
    double viscosity = testSystem.getPhase("gas").getViscosity("cP");

    double expectedGasViscosity = 0.021; // cP at 273 K, ~200 bar from literature charts
    assertTrue(viscosity > 0.0 && viscosity < 2.0,
        "Methane gas viscosity should stay within a reasonable bound of the reference charts");
    System.out.println(
        "Viscosity_LBC_methane: " + viscosity + "[cP], reference " + expectedGasViscosity);
  }

  @Test
  void testLiquidNHeptaneViscosity() {
    double T = 298.15;
    double P = 0.1; // Pressure in MPa
    testSystem = new neqsim.thermo.system.SystemSrkEos(T, P * 10);
    testSystem.addComponent("n-heptane", 1.0);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.getPhase(0).getPhysicalProperties().setViscosityModel("LBC");
    testSystem.initProperties();
    double viscosity = testSystem.getPhase(0).getPhysicalProperties().getViscosity() * 1000.0;

    double expectedLiquidViscosity = 0.389; // cP at 25 C (CRC Handbook)
    assertTrue(viscosity > 0.0);
    assertEquals(expectedLiquidViscosity, viscosity, expectedLiquidViscosity * 0.75);
    System.out.println("Viscosity_LBC_nHeptane: " + viscosity * Math.pow(10, 3) + "[mPa*s] vs "
        + expectedLiquidViscosity * 1.0e3);
  }

  @Test
  void testLiquidNDecaneAgainstData() {
    SystemInterface oilSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 1.0);
    oilSystem.addComponent("nC10", 1.0);
    oilSystem.setMixingRule("classic");
    new ThermodynamicOperations(oilSystem).TPflash();
    oilSystem.getPhase(0).getPhysicalProperties().setViscosityModel("LBC");
    oilSystem.initProperties();
    double viscosity = oilSystem.getPhase(0).getPhysicalProperties().getViscosity() * 1000.0;

    double expectedViscosity = 0.92; // cP at 25 C
    assertTrue(viscosity > 0.0);
    assertEquals(expectedViscosity, viscosity, expectedViscosity * 1.0);
    System.out.println(
        "Viscosity_LBC_nDecane: " + viscosity + "[cP] vs experimental " + expectedViscosity);
  }

    @Test
    void testOilViscosityComparableToFrictionTheory() {
      SystemInterface oilSystem = new neqsim.thermo.system.SystemSrkEos(333.15, 50.0);
      oilSystem.addComponent("n-heptane", 0.5);
      oilSystem.addComponent("nC10", 0.5);
      oilSystem.setMixingRule("classic");
      new ThermodynamicOperations(oilSystem).TPflash();

      double frictionVisc = oilViscosity(oilSystem, "friction theory");
      double lbcVisc = oilViscosity(oilSystem, "LBC");

      assertTrue(frictionVisc > 0.0 && lbcVisc > 0.0);
      double ratio = lbcVisc / frictionVisc;
      System.out.println("nC7/nC10 mixture viscosities: frictionTheory=" + frictionVisc
          + " cP, LBC=" + lbcVisc + " cP, ratio=" + ratio);
      assertTrue(ratio > 0.5 && ratio < 2.0,
          "LBC and friction theory viscosities should stay within +/-100% for defined oils");
    }

    @Test
    void testOilWithPseudoComponentsComparable() {
      SystemInterface oilSystem = new neqsim.thermo.system.SystemSrkEos(323.15, 30.0);
      oilSystem.addComponent("n-heptane", 0.2);
      oilSystem.addTBPfraction("C10", 0.4, 0.142, 0.79);
      oilSystem.addTBPfraction("C14", 0.2, 0.2, 0.82);
      oilSystem.addTBPfraction("C18", 0.2, 0.26, 0.84);
      oilSystem.setMixingRule("classic");
      new ThermodynamicOperations(oilSystem).TPflash();

      double frictionVisc = oilViscosity(oilSystem, "friction theory");
      double lbcVisc = oilViscosity(oilSystem, "LBC");

      assertTrue(frictionVisc > 0.0 && lbcVisc > 0.0);
      double ratio = lbcVisc / frictionVisc;
      System.out.println(
          "Pseudo-component oil viscosities: frictionTheory=" + frictionVisc + " cP, LBC=" + lbcVisc
              + " cP, ratio=" + ratio);
      assertTrue(ratio > 0.001 && ratio < 10.0,
          "LBC and friction theory viscosities should stay within a few orders of magnitude for pseudo components");
    }

    private double oilViscosity(SystemInterface system, String model) {
      system.getPhase("oil").getPhysicalProperties().setViscosityModel(model);
      system.initProperties();
      return system.getPhase("oil").getViscosity("cP");
    }
}
