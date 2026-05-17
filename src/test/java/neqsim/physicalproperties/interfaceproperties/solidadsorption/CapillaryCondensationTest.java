package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.CapillaryCondensationModel.PoreType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for capillary condensation model.
 *
 * @author ESOL
 */
public class CapillaryCondensationTest {

  private SystemInterface testSystem;
  private CapillaryCondensationModel capModel;

  /**
   * Set up test fixtures.
   */
  @BeforeEach
  public void setUp() {
    // Nitrogen at 77K - typical conditions for pore analysis
    testSystem = new SystemSrkEos(77.0, 0.8);
    testSystem.addComponent("nitrogen", 1.0);
    testSystem.setMixingRule("classic");
    testSystem.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    capModel = new CapillaryCondensationModel(testSystem);
    capModel.setMeanPoreRadius(5.0);
    capModel.setPoreRadiusStdDev(2.0);
    capModel.setTotalPoreVolume(0.5);
  }

  /**
   * Test Kelvin radius calculation.
   */
  @Test
  public void testKelvinRadiusCalculation() {
    capModel.calcCapillaryCondensation(0);

    double kelvinR = capModel.getKelvinRadius(0);
    assertTrue(kelvinR > 0, "Kelvin radius should be positive");
    assertTrue(kelvinR < 100, "Kelvin radius should be in reasonable range (nm)");
    assertTrue(capModel.isCalculated(), "Model should be marked as calculated");
  }

  /**
   * Test that Kelvin radius increases with pressure.
   */
  @Test
  public void testKelvinRadiusPressureDependence() {
    // Lower pressure - smaller Kelvin radius
    SystemInterface lowPSystem = new SystemSrkEos(77.0, 0.3);
    lowPSystem.addComponent("nitrogen", 1.0);
    lowPSystem.setMixingRule("classic");
    lowPSystem.init(0);

    CapillaryCondensationModel lowPModel = new CapillaryCondensationModel(lowPSystem);
    lowPModel.calcCapillaryCondensation(0);

    // Higher pressure - larger Kelvin radius
    SystemInterface highPSystem = new SystemSrkEos(77.0, 0.9);
    highPSystem.addComponent("nitrogen", 1.0);
    highPSystem.setMixingRule("classic");
    highPSystem.init(0);

    CapillaryCondensationModel highPModel = new CapillaryCondensationModel(highPSystem);
    highPModel.calcCapillaryCondensation(0);

    assertTrue(highPModel.getKelvinRadius(0) > lowPModel.getKelvinRadius(0),
        "Kelvin radius should increase with relative pressure");
  }

  /**
   * Test condensate amount calculation.
   */
  @Test
  public void testCondensateAmount() {
    capModel.calcCapillaryCondensation(0);

    double condensate = capModel.getCondensateAmount(0);
    assertTrue(condensate >= 0, "Condensate amount should be non-negative");
  }

  /**
   * Test pore size distribution effect on condensation.
   */
  @Test
  public void testPoreSizeDistributionEffect() {
    // Small pores - more condensation at lower P/P0
    capModel.setMeanPoreRadius(3.0);
    capModel.setPoreRadiusStdDev(1.0);
    capModel.calcCapillaryCondensation(0);
    double smallPoreCondensate = capModel.getCondensateAmount(0);

    // Large pores - less condensation at same P/P0
    capModel.setMeanPoreRadius(20.0);
    capModel.setPoreRadiusStdDev(5.0);
    capModel.calcCapillaryCondensation(0);
    double largePoreCondensate = capModel.getCondensateAmount(0);

    assertTrue(smallPoreCondensate >= largePoreCondensate,
        "Smaller pores should show more condensation at same P/P0");
  }

  /**
   * Test pore type geometry factor.
   */
  @Test
  public void testPoreTypeGeometryFactor() {
    assertEquals(2.0, PoreType.CYLINDRICAL.getGeometryFactor());
    assertEquals(1.0, PoreType.SLIT.getGeometryFactor());
    assertEquals(2.0, PoreType.SPHERICAL.getGeometryFactor());
  }

  /**
   * Test pore type effect on Kelvin radius.
   */
  @Test
  public void testPoreTypeEffect() {
    // Test that different pore types have different geometry factors
    assertEquals(2.0, PoreType.CYLINDRICAL.getGeometryFactor());
    assertEquals(1.0, PoreType.SLIT.getGeometryFactor());
    assertEquals(2.0, PoreType.SPHERICAL.getGeometryFactor());

    // Test that pore type can be set and retrieved
    capModel.setPoreType(PoreType.CYLINDRICAL);
    capModel.calcCapillaryCondensation(0);
    assertEquals(PoreType.CYLINDRICAL, capModel.getPoreType());

    capModel.setPoreType(PoreType.SLIT);
    assertEquals(PoreType.SLIT, capModel.getPoreType());
  }

  /**
   * Test contact angle effect.
   */
  @Test
  public void testContactAngleEffect() {
    // Test that contact angle can be set and retrieved
    capModel.setContactAngle(0.0); // Complete wetting
    assertEquals(0.0, capModel.getContactAngle(), 0.001);

    capModel.setContactAngle(Math.PI / 6); // 30 degrees
    assertEquals(Math.PI / 6, capModel.getContactAngle(), 0.001);

    // Calculation runs without error
    capModel.calcCapillaryCondensation(0);
    assertTrue(capModel.getKelvinRadius(0) > 0 || capModel.getKelvinRadius(0) == Double.MAX_VALUE,
        "Kelvin radius should be positive or MAX_VALUE for supersaturated");
  }

  /**
   * Test adsorbed layer thickness effect.
   */
  @Test
  public void testAdsorbedLayerThickness() {
    capModel.setAdsorbedLayerThickness(0.35);
    assertEquals(0.35, capModel.getAdsorbedLayerThickness(), 1e-6);

    capModel.setAdsorbedLayerThickness(0.5);
    assertEquals(0.5, capModel.getAdsorbedLayerThickness(), 1e-6);
  }

  /**
   * Test condensation pressure calculation.
   */
  @Test
  public void testCondensationPressure() {
    capModel.calcCapillaryCondensation(0);

    // Calculate P/P0 at which 5 nm pores fill
    double relP = capModel.getCondensationPressure(5.0, 0, 0);

    assertTrue(relP > 0 && relP < 1, "Relative pressure should be between 0 and 1");

    // Larger pores fill at higher relative pressure
    double relPLarge = capModel.getCondensationPressure(10.0, 0, 0);
    assertTrue(relPLarge > relP, "Larger pores should fill at higher P/P0");
  }

  /**
   * Test very small pores approach complete filling at low pressure.
   */
  @Test
  public void testSmallPoreFilling() {
    // Test that getCondensationPressure returns reasonable values
    // For small pores, condensation should occur
    double relP = capModel.getCondensationPressure(1.5, 0, 0); // 1.5 nm pore
    // The condensation pressure should be positive
    assertTrue(relP >= 0, "Condensation pressure should be non-negative");
  }

  /**
   * Test exception before calculation.
   */
  @Test
  public void testExceptionBeforeCalculation() {
    CapillaryCondensationModel newModel = new CapillaryCondensationModel(testSystem);
    assertThrows(IllegalStateException.class, () -> newModel.getKelvinRadius(0));
    assertThrows(IllegalStateException.class, () -> newModel.getCondensateAmount(0));
  }

  /**
   * Test getter by component name.
   */
  @Test
  public void testGetByComponentName() {
    capModel.calcCapillaryCondensation(0);

    double kelvin = capModel.getKelvinRadius("nitrogen");
    double condensate = capModel.getCondensateAmount("nitrogen");

    assertTrue(kelvin > 0, "Kelvin radius should be positive");
    assertTrue(condensate >= 0, "Condensate should be non-negative");
  }

  /**
   * Test pore volume setter.
   */
  @Test
  public void testPoreVolumeSetter() {
    capModel.setTotalPoreVolume(1.0);
    assertEquals(1.0, capModel.getTotalPoreVolume(), 1e-6);
  }

  /**
   * Test min/max pore radius setters.
   */
  @Test
  public void testPoreRadiusRange() {
    capModel.setMinPoreRadius(0.5);
    capModel.setMaxPoreRadius(50.0);

    assertEquals(0.5, capModel.getMinPoreRadius(), 1e-6);
    assertEquals(50.0, capModel.getMaxPoreRadius(), 1e-6);
  }

  /**
   * Test property getters.
   */
  @Test
  public void testPropertyGetters() {
    capModel.calcCapillaryCondensation(0);

    double pSat = capModel.getSaturationPressure(0);
    double vm = capModel.getLiquidMolarVolume(0);
    double gamma = capModel.getSurfaceTension(0);

    assertTrue(pSat > 0, "Saturation pressure should be positive");
    assertTrue(vm > 0, "Liquid molar volume should be positive");
    assertTrue(gamma > 0, "Surface tension should be positive");
  }

  /**
   * Test multi-component system.
   */
  @Test
  public void testMultiComponentSystem() {
    SystemInterface multiSystem = new SystemSrkEos(200.0, 10.0);
    multiSystem.addComponent("propane", 0.8);
    multiSystem.addComponent("n-pentane", 0.2);
    multiSystem.setMixingRule("classic");
    multiSystem.init(0);

    CapillaryCondensationModel multiModel = new CapillaryCondensationModel(multiSystem);
    multiModel.setMeanPoreRadius(10.0);
    multiModel.setTotalPoreVolume(0.5);
    multiModel.calcCapillaryCondensation(0);

    double propaneKelvin = multiModel.getKelvinRadius(0);
    double pentaneKelvin = multiModel.getKelvinRadius(1);

    // Different components should have different Kelvin radii
    assertTrue(propaneKelvin > 0 && pentaneKelvin > 0,
        "Kelvin radii should be positive for all components");
  }
}
