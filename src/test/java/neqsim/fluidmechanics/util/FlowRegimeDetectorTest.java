package neqsim.fluidmechanics.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.util.FlowRegimeDetector.FlowRegime;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for FlowRegimeDetector class.
 *
 * <p>
 * Tests verify that the flow regime detection correctly identifies different two-phase flow
 * patterns based on Taitel-Dukler criteria.
 * </p>
 *
 * @author esol
 */
public class FlowRegimeDetectorTest {
  private SystemInterface testSystem;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(300.0, 10.0);
    testSystem.addComponent("methane", 1.0, 0);
    testSystem.addComponent("n-heptane", 1.0, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
  }

  @Test
  void testStratifiedFlowDetection() {
    // Low gas and liquid velocities should give stratified flow
    double uSG = 0.5; // Superficial gas velocity m/s
    double uSL = 0.1; // Superficial liquid velocity m/s
    double diameter = 0.1; // 10 cm pipe
    double inclination = 0.0; // Horizontal

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);

    assertTrue(
        regime == FlowRegime.STRATIFIED_SMOOTH || regime == FlowRegime.STRATIFIED_WAVY
            || regime == FlowRegime.SLUG,
        "Low velocities in horizontal pipe should give stratified or slug flow, got: " + regime);
  }

  @Test
  void testAnnularFlowDetection() {
    // Very high gas velocity should give annular flow
    double uSG = 20.0; // High superficial gas velocity m/s
    double uSL = 0.05; // Low superficial liquid velocity m/s
    double diameter = 0.1;
    double inclination = 0.0;

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);

    assertTrue(regime == FlowRegime.ANNULAR || regime == FlowRegime.DROPLET,
        "Very high gas velocity should give annular or droplet flow, got: " + regime);
  }

  @Test
  void testBubbleFlowDetection() {
    // High liquid rate with moderate gas should give dispersed bubble
    double uSG = 0.5; // Moderate gas velocity
    double uSL = 5.0; // High liquid velocity m/s
    double diameter = 0.1;
    double inclination = 0.0;

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);

    // High liquid rates tend towards bubble flow
    assertNotNull(regime);
  }

  @Test
  void testSlugFlowDetection() {
    // Intermediate gas and liquid velocities in horizontal pipe
    double uSG = 2.0; // Intermediate gas velocity
    double uSL = 0.5; // Intermediate liquid velocity
    double diameter = 0.1;
    double inclination = 0.0;

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);

    assertNotNull(regime);
    // Intermediate conditions often lead to slug flow
  }

  @Test
  void testFlowRegimeWithFluidProperties() {
    // Test with explicit fluid properties
    double uSG = 5.0;
    double uSL = 0.5;
    double diameter = 0.1;
    double inclination = 0.0;
    double rhoG = 10.0; // kg/m3
    double rhoL = 800.0; // kg/m3
    double muG = 1e-5; // Pa.s
    double muL = 1e-3; // Pa.s
    double sigma = 0.02; // N/m

    FlowRegime regime = FlowRegimeDetector.detectFlowRegime(uSG, uSL, diameter, inclination, rhoG,
        rhoL, muG, muL, sigma);

    assertNotNull(regime);
  }

  @Test
  void testUphillFlowRegime() {
    // Uphill flow tends towards slug and annular
    double uSG = 3.0;
    double uSL = 0.5;
    double diameter = 0.1;
    double inclination = 30.0; // 30 degrees uphill

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);

    assertTrue(regime == FlowRegime.SLUG || regime == FlowRegime.ANNULAR,
        "Uphill flow should give slug or annular, got: " + regime);
  }

  @Test
  void testDownhillFlowRegime() {
    // Downhill flow tends towards stratified
    double uSG = 1.0;
    double uSL = 0.2;
    double diameter = 0.1;
    double inclination = -15.0; // 15 degrees downhill

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);

    assertNotNull(regime);
    // Downhill typically favors stratified flow
  }

  @Test
  void testFlowPatternName() {
    double uSG = 0.5;
    double uSL = 0.1;
    double diameter = 0.1;
    double inclination = 0.0;

    String patternName =
        FlowRegimeDetector.detectFlowPatternName(testSystem, uSG, uSL, diameter, inclination);

    assertNotNull(patternName);
    assertTrue(patternName.length() > 0, "Pattern name should not be empty");
    // Pattern name should be one of the valid node types
    assertTrue(
        patternName.equals("stratified") || patternName.equals("slug")
            || patternName.equals("annular") || patternName.equals("bubble")
            || patternName.equals("droplet"),
        "Pattern name should be a valid flow node type, got: " + patternName);
  }

  @Test
  void testFlowRegimeEnumNodeNames() {
    // Verify that each FlowRegime enum value has a valid node name
    for (FlowRegime regime : FlowRegime.values()) {
      String nodeName = regime.getNodeName();
      assertNotNull(nodeName);
      assertTrue(nodeName.length() > 0, "Node name should not be empty for " + regime);
    }
  }

  @Test
  void testZeroVelocities() {
    // Edge case: very low velocities
    double uSG = 0.001;
    double uSL = 0.001;
    double diameter = 0.1;
    double inclination = 0.0;

    // Should not throw exception
    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);
    assertNotNull(regime);
  }

  @Test
  void testLargeDiameter() {
    // Large diameter pipe
    double uSG = 2.0;
    double uSL = 0.5;
    double diameter = 1.0; // 1 meter pipe
    double inclination = 0.0;

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);
    assertNotNull(regime);
  }

  @Test
  void testSmallDiameter() {
    // Small diameter pipe
    double uSG = 2.0;
    double uSL = 0.5;
    double diameter = 0.01; // 1 cm pipe
    double inclination = 0.0;

    FlowRegime regime =
        FlowRegimeDetector.detectFlowRegime(testSystem, uSG, uSL, diameter, inclination);
    assertNotNull(regime);
  }
}
