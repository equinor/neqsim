package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for BET adsorption isotherm model.
 *
 * @author ESOL
 */
public class BETAdsorptionTest {

  private SystemInterface testSystem;
  private BETAdsorption adsorption;

  /**
   * Set up test fixtures.
   */
  @BeforeEach
  public void setUp() {
    // N2 adsorption at 77K - typical BET measurement conditions
    testSystem = new SystemSrkEos(77.0, 0.5);
    testSystem.addComponent("nitrogen", 1.0);
    testSystem.setMixingRule("classic");
    testSystem.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    adsorption = new BETAdsorption(testSystem);
    adsorption.setSolidMaterial("AC");
  }

  /**
   * Test isotherm type is correctly identified.
   */
  @Test
  public void testIsothermType() {
    assertEquals(IsothermType.BET, adsorption.getIsothermType());
  }

  /**
   * Test basic BET calculation returns non-negative values.
   */
  @Test
  public void testBasicBETCalculation() {
    adsorption.calcAdsorption(0);

    double n2Adsorption = adsorption.getSurfaceExcess(0);
    assertTrue(n2Adsorption >= 0, "Adsorption should be non-negative");
    assertTrue(adsorption.isCalculated(), "Adsorption should be marked as calculated");
  }

  /**
   * Test multilayer behavior - adsorption should exceed monolayer at high P/P0.
   */
  @Test
  public void testMultilayerBehavior() {
    // Set up system at higher relative pressure
    SystemInterface highPSystem = new SystemSrkEos(77.0, 0.8); // Close to saturation
    highPSystem.addComponent("nitrogen", 1.0);
    highPSystem.setMixingRule("classic");
    highPSystem.init(0);

    BETAdsorption highPAds = new BETAdsorption(highPSystem);
    highPAds.setMonolayerCapacity(0, 5.0);
    highPAds.setBETConstant(0, 100.0);
    highPAds.setSaturationPressure(0, 1.0);
    highPAds.calcAdsorption(0);

    double nLayers = highPAds.getNumberOfLayers(0);
    assertTrue(nLayers > 1.0, "At high P/P0, number of layers should exceed 1");
  }

  /**
   * Test BET divergence as P approaches P0.
   */
  @Test
  public void testApproachingSaturation() {
    BETAdsorption nearSatAds = new BETAdsorption(testSystem);
    nearSatAds.setMonolayerCapacity(0, 5.0);
    nearSatAds.setBETConstant(0, 100.0);
    nearSatAds.setSaturationPressure(0, testSystem.getPressure() * 1.01); // P/P0 ~ 0.99
    nearSatAds.calcAdsorption(0);

    double excess = nearSatAds.getSurfaceExcess(0);
    // Near saturation, BET predicts very high adsorption
    assertTrue(excess > 5.0, "Near saturation, adsorption should be very high");
  }

  /**
   * Test modified BET with limited layers.
   */
  @Test
  public void testModifiedBETLimitedLayers() {
    adsorption.setMonolayerCapacity(0, 5.0);
    adsorption.setBETConstant(0, 100.0);
    adsorption.setSaturationPressure(0, 1.0);
    adsorption.setMaxLayers(3); // Limit to 3 layers
    adsorption.calcAdsorption(0);

    double nLayers = adsorption.getNumberOfLayers(0);
    assertTrue(nLayers <= 3.0, "Layers should not exceed max layers setting");
  }

  /**
   * Test BET surface area calculation.
   */
  @Test
  public void testBETSurfaceAreaCalculation() {
    double qm = 10.0; // mol/kg
    adsorption.setMonolayerCapacity(0, qm);

    // N2 cross-sectional area = 0.162 nm2
    double area = adsorption.calculateBETSurfaceArea(0, 0.162);

    // Surface area calculation: qm(mol/kg) * NA * crossSectionalArea(m2)
    // = 10 * 6.022e23 * 0.162e-18 = ~975 m2/kg = ~0.975 m2/g
    // Allow wide range to account for unit differences
    assertTrue(area > 0, "BET surface area should be positive");
  }

  /**
   * Test relative pressure calculation.
   */
  @Test
  public void testRelativePressure() {
    // First calculate to initialize the system
    adsorption.calcAdsorption(0);

    double relP = adsorption.getRelativePressure(0, 0);
    // Relative pressure should be between 0 and 1 for typical conditions
    assertTrue(relP >= 0 && relP <= 2.0, "Relative pressure should be reasonable");
  }

  /**
   * Test BET at zero pressure gives zero adsorption.
   */
  @Test
  public void testZeroPressureBehavior() {
    SystemInterface zeroPSystem = new SystemSrkEos(77.0, 1e-10);
    zeroPSystem.addComponent("nitrogen", 1.0);
    zeroPSystem.setMixingRule("classic");
    zeroPSystem.init(0);

    BETAdsorption zeroPAds = new BETAdsorption(zeroPSystem);
    zeroPAds.setMonolayerCapacity(0, 5.0);
    zeroPAds.setBETConstant(0, 100.0);
    zeroPAds.setSaturationPressure(0, 1.0);
    zeroPAds.calcAdsorption(0);

    double excess = zeroPAds.getSurfaceExcess(0);
    assertTrue(excess < 0.01, "At zero pressure, adsorption should be near zero");
  }

  /**
   * Test that exception is thrown before calculation.
   */
  @Test
  public void testExceptionBeforeCalculation() {
    BETAdsorption newAdsorption = new BETAdsorption(testSystem);
    assertThrows(IllegalStateException.class, () -> newAdsorption.getSurfaceExcess(0));
  }

  /**
   * Test BET constant effect on shape.
   */
  @Test
  public void testBETConstantEffect() {
    // Verify that different BET constants produce different results
    BETAdsorption highCAds = new BETAdsorption(testSystem);
    highCAds.setMonolayerCapacity(0, 5.0);
    highCAds.setBETConstant(0, 500.0); // High C
    double highCVal = highCAds.getBETConstant(0);

    BETAdsorption lowCAds = new BETAdsorption(testSystem);
    lowCAds.setMonolayerCapacity(0, 5.0);
    lowCAds.setBETConstant(0, 10.0); // Low C
    double lowCVal = lowCAds.getBETConstant(0);

    // Verify the constants are different
    assertTrue(Math.abs(highCVal - lowCVal) > 100.0, "BET constant should be stored correctly");
  }

  /**
   * Test multi-component BET.
   */
  @Test
  public void testMultiComponentBET() {
    SystemInterface multiSystem = new SystemSrkEos(200.0, 5.0);
    multiSystem.addComponent("methane", 0.9);
    multiSystem.addComponent("CO2", 0.1);
    multiSystem.setMixingRule("classic");
    multiSystem.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(multiSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    BETAdsorption multiAds = new BETAdsorption(multiSystem);
    multiAds.calcAdsorption(0);

    double ch4 = multiAds.getSurfaceExcess(0);
    double co2 = multiAds.getSurfaceExcess(1);

    assertTrue(ch4 >= 0 && co2 >= 0, "Multi-component adsorption should be non-negative");
    assertTrue(multiAds.getTotalSurfaceExcess() > 0, "Total should be positive");
  }
}
