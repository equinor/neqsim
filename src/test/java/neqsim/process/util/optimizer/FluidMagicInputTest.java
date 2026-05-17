package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for FluidMagicInput class.
 *
 * @author ESOL
 * @version 1.0
 */
public class FluidMagicInputTest {

  private SystemInterface referenceFluid;

  /**
   * Set up test fixtures.
   */
  @BeforeEach
  void setUp() {
    // Create a typical oil/gas fluid
    referenceFluid = new SystemSrkEos(288.15, 1.01325); // 15°C, 1 atm (std conditions)
    referenceFluid.addComponent("nitrogen", 0.01);
    referenceFluid.addComponent("CO2", 0.02);
    referenceFluid.addComponent("methane", 0.70);
    referenceFluid.addComponent("ethane", 0.08);
    referenceFluid.addComponent("propane", 0.05);
    referenceFluid.addComponent("n-butane", 0.03);
    referenceFluid.addComponent("n-pentane", 0.02);
    referenceFluid.addComponent("n-hexane", 0.02);
    referenceFluid.addComponent("n-heptane", 0.02);
    referenceFluid.addComponent("n-octane", 0.02);
    referenceFluid.addComponent("n-nonane", 0.015);
    referenceFluid.addComponent("nC10", 0.015);
    referenceFluid.setMixingRule("classic");
  }

  /**
   * Test creating FluidMagicInput from SystemInterface.
   */
  @Test
  void testFromFluid() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);

    assertNotNull(input);
    assertNotNull(input.getReferenceFluid());
    assertEquals(referenceFluid.getNumberOfComponents(),
        input.getReferenceFluid().getNumberOfComponents());
  }

  /**
   * Test phase separation at standard conditions.
   */
  @Test
  void testSeparateToStandardConditions() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);
    input.separateToStandardConditions();

    assertNotNull(input.getGasPhase());
    assertNotNull(input.getOilPhase());

    // Check that separation was performed
    assertTrue(input.isReady());

    // Gas phase should have lower molar mass (light components)
    // Oil phase should have higher molar mass (heavy components)
    assertTrue(input.getGasPhase().getMolarMass() < input.getOilPhase().getMolarMass(),
        "Gas phase should be lighter than oil phase");
  }

  /**
   * Test GOR range configuration.
   */
  @Test
  void testGORRangeConfiguration() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);
    input.setGORRange(100, 5000);
    input.setNumberOfGORPoints(7);

    double[] gorValues = input.generateGORValues();

    assertNotNull(gorValues);
    assertEquals(7, gorValues.length);
    assertEquals(100, gorValues[0], 0.1);
    assertEquals(5000, gorValues[gorValues.length - 1], 0.1);

    // Check monotonically increasing
    for (int i = 1; i < gorValues.length; i++) {
      assertTrue(gorValues[i] > gorValues[i - 1], "GOR values should be increasing");
    }
  }

  /**
   * Test logarithmic spacing for GOR.
   */
  @Test
  void testLogarithmicGORSpacing() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);
    input.setGORRange(100, 10000);
    input.setNumberOfGORPoints(5);
    input.setGorSpacing(FluidMagicInput.GORSpacing.LOGARITHMIC);

    double[] gorValues = input.generateGORValues();

    assertEquals(5, gorValues.length);
    assertEquals(100, gorValues[0], 0.1);
    assertEquals(10000, gorValues[4], 0.1);

    // For logarithmic spacing, middle value should be geometric mean
    double expectedMiddle = Math.sqrt(100.0 * 10000.0); // ~1000
    assertEquals(expectedMiddle, gorValues[2], 50.0);
  }

  /**
   * Test water cut range configuration.
   */
  @Test
  void testWaterCutRangeConfiguration() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);
    input.setWaterCutRange(0.0, 0.90);
    input.setNumberOfWaterCutPoints(10);

    double[] wcValues = input.generateWaterCutValues();

    assertNotNull(wcValues);
    assertEquals(10, wcValues.length);
    assertEquals(0.0, wcValues[0], 0.001);
    assertEquals(0.90, wcValues[wcValues.length - 1], 0.001);
  }

  /**
   * Test invalid GOR range.
   */
  @Test
  void testInvalidGORRange() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);

    assertThrows(IllegalArgumentException.class, () -> input.setGORRange(-100, 5000));
    assertThrows(IllegalArgumentException.class, () -> input.setGORRange(5000, 100));
  }

  /**
   * Test invalid water cut range.
   */
  @Test
  void testInvalidWaterCutRange() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);

    assertThrows(IllegalArgumentException.class, () -> input.setWaterCutRange(-0.1, 0.5));
    assertThrows(IllegalArgumentException.class, () -> input.setWaterCutRange(0.0, 1.5));
    assertThrows(IllegalArgumentException.class, () -> input.setWaterCutRange(0.6, 0.3));
  }

  /**
   * Test builder pattern.
   */
  @Test
  void testBuilderPattern() {
    FluidMagicInput input =
        FluidMagicInput.builder().referenceFluid(referenceFluid).gorRange(200, 8000)
            .waterCutRange(0.05, 0.70).numberOfGORPoints(6).numberOfWaterCutPoints(8).build();

    assertNotNull(input);

    double[] gorValues = input.generateGORValues();
    double[] wcValues = input.generateWaterCutValues();

    assertEquals(6, gorValues.length);
    assertEquals(8, wcValues.length);
    assertEquals(200, gorValues[0], 0.1);
    assertEquals(8000, gorValues[5], 0.1);
    assertEquals(0.05, wcValues[0], 0.001);
    assertEquals(0.70, wcValues[7], 0.001);
  }

  /**
   * Test reference GOR calculation.
   */
  @Test
  void testReferenceGORCalculation() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);
    input.separateToStandardConditions();

    double refGOR = input.getBaseCaseGOR();
    assertTrue(refGOR > 0, "Reference GOR should be positive");

    // For this gas-rich fluid, GOR should be high
    assertTrue(refGOR > 100, "Reference GOR should be > 100 for gas-rich fluid");
  }

  /**
   * Test validation before phase separation.
   */
  @Test
  void testValidateBeforeSeparation() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);

    // Should not be ready before separation
    assertFalse(input.isReady());
  }

  /**
   * Test total scenarios calculation.
   */
  @Test
  void testTotalScenarios() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);
    input.setNumberOfGORPoints(5);
    input.setNumberOfWaterCutPoints(4);

    assertEquals(20, input.getTotalScenarios());
  }

  /**
   * Test minimum number of points validation.
   */
  @Test
  void testMinimumPointsValidation() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);

    assertThrows(IllegalArgumentException.class, () -> input.setNumberOfGORPoints(1));
    assertThrows(IllegalArgumentException.class, () -> input.setNumberOfWaterCutPoints(1));
  }

  /**
   * Test getters for min/max values.
   */
  @Test
  void testGettersForRanges() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);
    input.setGORRange(200, 8000);
    input.setWaterCutRange(0.05, 0.70);

    assertEquals(200, input.getMinGOR(), 0.1);
    assertEquals(8000, input.getMaxGOR(), 0.1);
    assertEquals(0.05, input.getMinWaterCut(), 0.001);
    assertEquals(0.70, input.getMaxWaterCut(), 0.001);
  }

  /**
   * Test temperature and pressure setters.
   */
  @Test
  void testTemperatureAndPressureSetters() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);

    input.setTemperature(293.15);
    input.setPressure(1.0);

    assertEquals(293.15, input.getTemperature(), 0.01);
    assertEquals(1.0, input.getPressure(), 0.01);
  }

  /**
   * Test water salinity configuration.
   */
  @Test
  void testWaterSalinityConfiguration() {
    FluidMagicInput input = FluidMagicInput.fromFluid(referenceFluid);

    input.setWaterSalinityPPM(35000.0);

    assertEquals(35000.0, input.getWaterSalinityPPM(), 0.1);
  }
}
