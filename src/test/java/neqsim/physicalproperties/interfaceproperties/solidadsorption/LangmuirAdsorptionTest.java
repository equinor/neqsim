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
 * Unit tests for Langmuir adsorption isotherm model.
 *
 * @author ESOL
 */
public class LangmuirAdsorptionTest {

  private SystemInterface testSystem;
  private LangmuirAdsorption adsorption;

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
      // Handle exception
    }

    adsorption = new LangmuirAdsorption(testSystem);
    adsorption.setSolidMaterial("AC");
  }

  /**
   * Test isotherm type is correctly identified.
   */
  @Test
  public void testIsothermType() {
    assertEquals(IsothermType.LANGMUIR, adsorption.getIsothermType());
  }

  /**
   * Test basic adsorption calculation returns non-negative values.
   */
  @Test
  public void testBasicAdsorptionCalculation() {
    adsorption.calcAdsorption(0);

    double methaneAdsorption = adsorption.getSurfaceExcess("methane");
    double co2Adsorption = adsorption.getSurfaceExcess("CO2");

    assertTrue(methaneAdsorption >= 0, "Methane adsorption should be non-negative");
    assertTrue(co2Adsorption >= 0, "CO2 adsorption should be non-negative");
    assertTrue(adsorption.isCalculated(), "Adsorption should be marked as calculated");
  }

  /**
   * Test that CO2 has higher adsorption than methane (expected behavior).
   */
  @Test
  public void testCO2VsMethaneSeparation() {
    // Set typical Langmuir parameters for AC
    adsorption.setQmax(0, 5.0); // methane
    adsorption.setKLangmuir(0, 0.5);
    adsorption.setQmax(1, 8.0); // CO2
    adsorption.setKLangmuir(1, 2.0);

    adsorption.calcAdsorption(0);

    double methaneAdsorption = adsorption.getSurfaceExcess("methane");
    double co2Adsorption = adsorption.getSurfaceExcess("CO2");

    // CO2 typically adsorbs more strongly on activated carbon
    double selectivity = adsorption.getSelectivity(1, 0, 0);
    assertTrue(selectivity > 1.0, "CO2/CH4 selectivity should be > 1 for AC");
  }

  /**
   * Test Langmuir saturation behavior.
   */
  @Test
  public void testSaturationBehavior() {
    double qmax = 10.0;
    adsorption.setQmax(0, qmax);
    adsorption.setKLangmuir(0, 1000.0); // Very high K for saturation

    adsorption.calcAdsorption(0);
    double coverage = adsorption.getCoverage(0);

    // At high K*P, coverage should approach 1
    assertTrue(coverage > 0.9, "Coverage should approach 1 at high K*P");
    assertTrue(coverage <= 1.0, "Coverage cannot exceed 1");
  }

  /**
   * Test extended Langmuir for multi-component systems.
   */
  @Test
  public void testExtendedLangmuir() {
    adsorption.setQmax(0, 5.0);
    adsorption.setKLangmuir(0, 1.0);
    adsorption.setQmax(1, 8.0);
    adsorption.setKLangmuir(1, 2.0);

    adsorption.calcExtendedLangmuir(0);

    double total = adsorption.getTotalSurfaceExcess();
    assertTrue(total > 0, "Total surface excess should be positive");

    // Check mole fractions sum to 1
    double sumMolFrac =
        adsorption.getAdsorbedPhaseMoleFraction(0) + adsorption.getAdsorbedPhaseMoleFraction(1);
    assertEquals(1.0, sumMolFrac, 1e-6, "Adsorbed phase mole fractions should sum to 1");
  }

  /**
   * Test that calling getSurfaceExcess before calculation throws exception.
   */
  @Test
  public void testExceptionBeforeCalculation() {
    LangmuirAdsorption newAdsorption = new LangmuirAdsorption(testSystem);
    assertThrows(IllegalStateException.class, () -> newAdsorption.getSurfaceExcess(0));
  }

  /**
   * Test parameter setters invalidate calculated state.
   */
  @Test
  public void testParameterChangeInvalidatesCalculation() {
    adsorption.calcAdsorption(0);
    assertTrue(adsorption.isCalculated());

    adsorption.setQmax(0, 10.0);
    assertTrue(!adsorption.isCalculated(), "Changing parameters should invalidate calculation");
  }

  /**
   * Test temperature effect on adsorption.
   */
  @Test
  public void testTemperatureEffect() {
    // Set up low temperature system
    SystemInterface lowTSystem = new SystemSrkEos(273.15, 10.0);
    lowTSystem.addComponent("CO2", 1.0);
    lowTSystem.setMixingRule("classic");
    lowTSystem.init(0);
    ThermodynamicOperations ops1 = new ThermodynamicOperations(lowTSystem);
    try {
      ops1.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    // Set up high temperature system
    SystemInterface highTSystem = new SystemSrkEos(373.15, 10.0);
    highTSystem.addComponent("CO2", 1.0);
    highTSystem.setMixingRule("classic");
    highTSystem.init(0);
    ThermodynamicOperations ops2 = new ThermodynamicOperations(highTSystem);
    try {
      ops2.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    LangmuirAdsorption lowTAds = new LangmuirAdsorption(lowTSystem);
    lowTAds.setQmax(0, 10.0);
    lowTAds.setKLangmuir(0, 1.0);
    lowTAds.setHeatOfAdsorption(0, -25000.0); // Exothermic
    lowTAds.calcAdsorption(0);

    LangmuirAdsorption highTAds = new LangmuirAdsorption(highTSystem);
    highTAds.setQmax(0, 10.0);
    highTAds.setKLangmuir(0, 1.0);
    highTAds.setHeatOfAdsorption(0, -25000.0);
    highTAds.calcAdsorption(0);

    // Adsorption should decrease with temperature (exothermic process)
    assertTrue(lowTAds.getSurfaceExcess(0) > highTAds.getSurfaceExcess(0),
        "Adsorption should decrease with increasing temperature");
  }

  /**
   * Test solid material setter.
   */
  @Test
  public void testSolidMaterialSetter() {
    adsorption.setSolidMaterial("Zeolite 13X");
    assertEquals("Zeolite 13X", adsorption.getSolidMaterial());
  }

  /**
   * Test linear range behavior at low pressure.
   */
  @Test
  public void testLinearRangeLowPressure() {
    // At low pressure (K*P << 1), Langmuir approaches linear (Henry's law)
    SystemInterface lowPSystem = new SystemSrkEos(298.15, 0.01); // Very low pressure
    lowPSystem.addComponent("methane", 1.0);
    lowPSystem.setMixingRule("classic");
    lowPSystem.init(0);

    LangmuirAdsorption lowPAds = new LangmuirAdsorption(lowPSystem);
    lowPAds.setQmax(0, 10.0);
    lowPAds.setKLangmuir(0, 1.0);
    lowPAds.calcAdsorption(0);

    double coverage = lowPAds.getCoverage(0);
    // At K*P = 0.01, coverage should be approximately K*P = 0.01
    assertTrue(coverage < 0.1, "At low pressure, coverage should be low and linear");
  }
}
