package neqsim.standards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.NeqSimTest;
import neqsim.standards.gasquality.Standard_ISO6578;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.oilquality.Standard_ASTM_D6377;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/** Executes the complete quick starts in the standards-package landing guide. */
class StandardsPackageDocumentationTest extends NeqSimTest {
  @Test
  void testIso6976GasQualityQuickStart() {
    SystemInterface gas = new SystemSrkEos(293.15, 1.0);
    gas.addComponent("methane", 0.931819);
    gas.addComponent("ethane", 0.025618);
    gas.addComponent("nitrogen", 0.010335);
    gas.addComponent("CO2", 0.015391);
    gas.setMixingRule("classic");
    new ThermodynamicOperations(gas).TPflash();

    Standard_ISO6976 iso6976 = new Standard_ISO6976(gas, 0.0, 15.55, "volume");
    iso6976.setReferenceState("real");
    iso6976.calculate();

    double gcvMJPerNm3 = iso6976.getValue("GCV") / 1000.0;
    double wobbeMJPerNm3 = iso6976.getValue("SuperiorWobbeIndex") / 1000.0;
    double relativeDensity = iso6976.getValue("RelativeDensity");

    assertEquals(39.6145678335, gcvMJPerNm3, 1.0e-5);
    assertEquals(51.7010127582, wobbeMJPerNm3, 1.0e-5);
    assertEquals(0.5870995452, relativeDensity, 1.0e-8);
    assertEquals(iso6976.getValue("WI"), iso6976.getValue("WobbeIndex"), 1.0e-9);
  }

  @Test
  void testIso6578LngDensityQuickStart() {
    SystemInterface lng = new SystemSrkEos(113.15, 1.0);
    lng.addComponent("nitrogen", 0.006538);
    lng.addComponent("methane", 0.918630);
    lng.addComponent("ethane", 0.058382);
    lng.addComponent("propane", 0.011993);
    lng.addComponent("n-butane", 0.003255);
    lng.addComponent("i-pentane", 0.000657);
    lng.addComponent("n-pentane", 0.000545);
    lng.setMixingRule("classic");
    lng.init(0);

    Standard_ISO6578 iso6578 = new Standard_ISO6578(lng);
    iso6578.calculate();
    double densityKgPerM3 = iso6578.getValue("density");

    assertTrue(Double.isFinite(densityKgPerM3));
    assertTrue(densityKgPerM3 > 400.0);
    assertTrue(densityKgPerM3 < 500.0);
    assertEquals("kg/m^3", iso6578.getUnit("density"));
  }

  @Test
  void testAstmD6377VapourPressureQuickStart() {
    SystemInterface oil = new SystemSrkEos(275.15, 1.0);
    oil.addComponent("methane", 0.0006538);
    oil.addComponent("ethane", 0.006538);
    oil.addComponent("propane", 0.065380);
    oil.addComponent("n-pentane", 0.154500);
    oil.addComponent("nC10", 0.545000);
    oil.setMixingRule(2);
    oil.init(0);

    Standard_ASTM_D6377 vapourPressure = new Standard_ASTM_D6377(oil);
    vapourPressure.setReferenceTemperature(37.8, "C");
    vapourPressure.setMethodRVP(Standard_ASTM_D6377.RvpMethod.RVP_ASTM_D6377);
    vapourPressure.calculate();

    double rvpBara = vapourPressure.getValue("RVP", "bara");
    double tvpBara = vapourPressure.getValue("TVP", "bara");

    assertEquals(0.9653068384, rvpBara, 1.0e-3);
    assertEquals(1.6662983670, tvpBara, 1.0e-3);
  }
}
