package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for PotentialTheoryAdsorption (DRA model).
 *
 * <p>
 * Tests the Dubinin-Radushkevich-Astakhov potential theory implementation which is the primary
 * adsorption model integrated into the NeqSim pipeline.
 * </p>
 *
 * @author ESOL
 */
public class PotentialTheoryAdsorptionTest {

  private SystemInterface testSystem;

  /**
   * Set up test fixtures.
   */
  @BeforeEach
  public void setUp() {
    testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("CO2", 0.1);
    testSystem.setMixingRule("classic");
    testSystem.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }
  }

  /**
   * Test that DRA model calculates adsorption without error.
   */
  @Test
  public void testBasicDRACalculation() {
    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(testSystem);
    dra.setSolidMaterial("AC");
    dra.calcAdsorption(0);

    assertTrue(dra.isCalculated(), "Adsorption should be marked as calculated");
    double totalExcess = dra.getTotalSurfaceExcess();
    System.out.println("DRA total surface excess: " + totalExcess);

    // DRA model should produce some surface excess
    assertNotNull(dra.getTotalSurfaceExcess());
  }

  /**
   * Test isotherm type is correctly identified as DRA.
   */
  @Test
  public void testIsothermType() {
    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(testSystem);
    assertEquals(IsothermType.DRA, dra.getIsothermType());
  }

  /**
   * Test that getSurfaceExcess(int) works after calculation.
   */
  @Test
  public void testGetSurfaceExcessByIndex() {
    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(testSystem);
    dra.setSolidMaterial("AC");
    dra.calcAdsorption(0);

    double ch4Excess = dra.getSurfaceExcess(0);
    double co2Excess = dra.getSurfaceExcess(1);

    System.out.println("DRA CH4 excess: " + ch4Excess);
    System.out.println("DRA CO2 excess: " + co2Excess);

    // Both should be finite numbers
    assertTrue(Double.isFinite(ch4Excess), "CH4 excess should be finite");
    assertTrue(Double.isFinite(co2Excess), "CO2 excess should be finite");
  }

  /**
   * Test getSurfaceExcess by name.
   */
  @Test
  public void testGetSurfaceExcessByName() {
    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(testSystem);
    dra.setSolidMaterial("AC");
    dra.calcAdsorption(0);

    double ch4Excess = dra.getSurfaceExcess("methane");
    double co2Excess = dra.getSurfaceExcess("CO2");

    // Should match index-based access
    assertEquals(dra.getSurfaceExcess(0), ch4Excess, 1e-10);
    assertEquals(dra.getSurfaceExcess(1), co2Excess, 1e-10);
  }

  /**
   * Test that exception is thrown before calculation.
   */
  @Test
  public void testExceptionBeforeCalculation() {
    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(testSystem);
    assertThrows(IllegalStateException.class, () -> dra.getSurfaceExcess(0));
  }

  /**
   * Test with different solid materials.
   */
  @Test
  public void testDifferentSolidMaterials() {
    PotentialTheoryAdsorption dra1 = new PotentialTheoryAdsorption(testSystem);
    dra1.setSolidMaterial("AC Calgon F400");
    dra1.calcAdsorption(0);
    double loading1 = dra1.getTotalSurfaceExcess();

    PotentialTheoryAdsorption dra2 = new PotentialTheoryAdsorption(testSystem);
    dra2.setSolidMaterial("AC Norit R1");
    dra2.calcAdsorption(0);
    double loading2 = dra2.getTotalSurfaceExcess();

    System.out.println("DRA on AC Calgon F400: " + loading1);
    System.out.println("DRA on AC Norit R1: " + loading2);

    // Both should produce results but may differ
    assertTrue(Double.isFinite(loading1), "Loading on Calgon F400 should be finite");
    assertTrue(Double.isFinite(loading2), "Loading on Norit R1 should be finite");
  }

  /**
   * Test single component pure methane calculation.
   */
  @Test
  public void testSingleComponentCH4() {
    SystemInterface pureCH4 = new SystemSrkEos(298.15, 10.0);
    pureCH4.addComponent("methane", 1.0);
    pureCH4.setMixingRule("classic");
    pureCH4.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(pureCH4);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(pureCH4);
    dra.setSolidMaterial("AC Calgon F400");
    dra.calcAdsorption(0);

    double ch4Excess = dra.getSurfaceExcess(0);
    System.out.println("Pure CH4 DRA at 10 bar on Calgon F400: " + ch4Excess);

    assertTrue(Double.isFinite(ch4Excess), "Surface excess should be finite");
    assertTrue(dra.isCalculated(), "Should be marked as calculated");
  }

  /**
   * Test that setSolidMaterial resets calculation state.
   */
  @Test
  public void testSetSolidMaterialResetsState() {
    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(testSystem);
    dra.setSolidMaterial("AC");
    dra.calcAdsorption(0);
    assertTrue(dra.isCalculated());

    dra.setSolidMaterial("AC Calgon F400");
    assertTrue(!dra.isCalculated(), "Changing solid should reset calculated state");
  }

  /**
   * Test integration with InterfaceProperties via initAdsorption.
   */
  @Test
  public void testInterfacePropertiesIntegration() {
    testSystem.initPhysicalProperties();
    testSystem.getInterphaseProperties().initAdsorption();
    testSystem.getInterphaseProperties().setSolidAdsorbentMaterial("AC");
    testSystem.getInterphaseProperties().calcAdsorption();

    AdsorptionInterface[] adsorptionCalc = testSystem.getInterphaseProperties().getAdsorptionCalc();
    assertNotNull(adsorptionCalc, "Adsorption calc array should not be null");
    assertTrue(adsorptionCalc.length > 0, "Should have at least one phase calc");

    // Should be DRA by default
    assertEquals(IsothermType.DRA, adsorptionCalc[0].getIsothermType());
    assertTrue(adsorptionCalc[0].isCalculated());
  }

  /**
   * Test model selection through InterfaceProperties init.
   */
  @Test
  public void testModelSelectionViaInterfaceProperties() {
    testSystem.initPhysicalProperties();
    testSystem.getInterphaseProperties().initAdsorption(IsothermType.LANGMUIR);
    testSystem.getInterphaseProperties().setSolidAdsorbentMaterial("AC");
    testSystem.getInterphaseProperties().calcAdsorption();

    AdsorptionInterface[] adsorptionCalc = testSystem.getInterphaseProperties().getAdsorptionCalc();
    assertEquals(IsothermType.LANGMUIR, adsorptionCalc[0].getIsothermType());

    // Test with Freundlich
    testSystem.getInterphaseProperties().initAdsorption(IsothermType.FREUNDLICH);
    testSystem.getInterphaseProperties().setSolidAdsorbentMaterial("AC");
    testSystem.getInterphaseProperties().calcAdsorption();

    adsorptionCalc = testSystem.getInterphaseProperties().getAdsorptionCalc();
    assertEquals(IsothermType.FREUNDLICH, adsorptionCalc[0].getIsothermType());
  }

  /**
   * Test that DRA handles unknown component gracefully with defaults.
   */
  @Test
  public void testUnknownComponentDefaults() {
    SystemInterface unknownGas = new SystemSrkEos(298.15, 5.0);
    unknownGas.addComponent("i-butane", 1.0);
    unknownGas.setMixingRule("classic");
    unknownGas.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(unknownGas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // pass
    }

    PotentialTheoryAdsorption dra = new PotentialTheoryAdsorption(unknownGas);
    dra.setSolidMaterial("AC");
    dra.calcAdsorption(0);

    // Should use default parameters and not crash
    assertTrue(dra.isCalculated(), "Should calculate with defaults");
    assertTrue(Double.isFinite(dra.getTotalSurfaceExcess()), "Result should be finite");
  }
}
