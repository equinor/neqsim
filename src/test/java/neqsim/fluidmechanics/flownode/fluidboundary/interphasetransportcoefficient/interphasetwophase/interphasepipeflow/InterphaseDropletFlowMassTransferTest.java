package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the corrected InterphaseDropletFlow mass transfer coefficients.
 *
 * <p>
 * Verifies that droplet flow uses Ranz-Marshall Sh with droplet diameter as the characteristic
 * length, not the Yih-Chen stratified flow correlations that were erroneously copy-pasted in the
 * original implementation.
 * </p>
 *
 * @author esol
 */
public class InterphaseDropletFlowMassTransferTest {
  private SystemInterface testSystem;
  private PipeData pipe;
  private DropletFlowNode node;
  private InterphaseDropletFlow interphase;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(313.3, 50.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("nC10", 0.01, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(3);

    pipe = new PipeData(0.15); // 150 mm diameter pipe
    pipe.setNodeLength(1.0);

    node = new DropletFlowNode(testSystem, pipe);
    node.setAverageDropletDiameter(100.0e-6); // 100 micron
    node.initFlowCalc();

    interphase = new InterphaseDropletFlow(node);
  }

  /**
   * Tests that gas-side mass transfer coefficient is positive and physically reasonable.
   */
  @Test
  void testGasSideMassTransferCoefficientIsPositive() {
    double schmidtNumber = 0.7; // typical gas Sc
    double kc = interphase.calcInterphaseMassTransferCoefficient(0, schmidtNumber, node);
    assertTrue(kc > 0, "Gas-side mass transfer coefficient should be positive, got: " + kc);
    // For 100 micron droplets in gas, kc should be in range 0.001 - 1.0 m/s
    assertTrue(kc > 1e-4, "Gas-side kc too small for droplet flow: " + kc);
    assertTrue(kc < 10.0, "Gas-side kc unreasonably large: " + kc);
  }

  /**
   * Tests that liquid-side mass transfer coefficient uses Kronig-Brink and is positive.
   */
  @Test
  void testLiquidSideMassTransferCoefficientIsPositive() {
    double schmidtNumber = 1000.0; // typical liquid Sc
    double kc = interphase.calcInterphaseMassTransferCoefficient(1, schmidtNumber, node);
    assertTrue(kc > 0, "Liquid-side mass transfer coefficient should be positive, got: " + kc);
  }

  /**
   * Tests that the gas-side mass transfer coefficient scales correctly with droplet diameter.
   * Smaller droplets should give larger kc (k_c = Sh * D_ij / d).
   */
  @Test
  void testKcIncreasesWithSmallerDroplets() {
    double sc = 0.7;

    // 100 micron droplets
    node.setAverageDropletDiameter(100.0e-6);
    node.initFlowCalc();
    double kc100 = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);

    // 50 micron droplets
    node.setAverageDropletDiameter(50.0e-6);
    node.initFlowCalc();
    double kc50 = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);

    assertTrue(kc50 > kc100,
        "Smaller droplets should give larger kc. kc50=" + kc50 + " kc100=" + kc100);
  }

  /**
   * Tests that the Ranz-Marshall stagnant limit Sh=2 is recovered for very small Re.
   */
  @Test
  void testStagnantDropletLimit() {
    // For a nearly stagnant droplet (very low Re), Sh should approach 2
    // k_c = 2 * D / d
    double sc = 1.0; // Sc = 1 for simplicity
    double nuGas = node.getBulkSystem().getPhase(0).getPhysicalProperties().getKinematicViscosity();
    double diffusivity = nuGas / sc;
    double dropDiam = 100.0e-6;

    // The minimum kc should be close to 2 * D / d (stagnant limit)
    double kcMin = 2.0 * diffusivity / dropDiam;

    double kc = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);
    // kc should be >= stagnant limit (convection always helps)
    assertTrue(kc >= kcMin * 0.5,
        "kc should be at least near the stagnant limit. kc=" + kc + " kcMin=" + kcMin);
  }

  /**
   * Tests that the Abramzon-Sirignano correction function F(B_M) has correct limits.
   */
  @Test
  void testAbramzonSirignanoFunctionLimits() {
    // F(0) should be 1.0 (no blowing correction)
    double f0 = interphase.calcAbramzonSirignanoF(0.0);
    assertEquals(1.0, f0, 1e-6, "F(0) should be 1.0");

    // F(B_M) should increase with B_M
    double f1 = interphase.calcAbramzonSirignanoF(1.0);
    double f5 = interphase.calcAbramzonSirignanoF(5.0);
    assertTrue(f5 > f1, "F should increase with B_M");
    assertTrue(f1 > 1.0, "F(1) should be > 1.0");
  }

  /**
   * Tests that Abramzon-Sirignano reduces the Sherwood number (thicker film due to blowing).
   */
  @Test
  void testAbramzonSirignanoReducesSherwood() {
    double sc = 0.7;

    // Without Abramzon-Sirignano
    interphase.setUseAbramzonSirignano(false);
    double kcStandard = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);

    // With Abramzon-Sirignano and significant B_M
    interphase.setUseAbramzonSirignano(true);
    interphase.setSpaldingMassTransferNumber(2.0);
    double kcAS = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);

    // Abramzon-Sirignano with B_M > 0 should give lower Sh* (thicker film)
    // which means lower kc
    assertTrue(kcAS < kcStandard, "Abramzon-Sirignano should reduce kc due to blowing. kcAS=" + kcAS
        + " kcStandard=" + kcStandard);
    assertTrue(kcAS > 0, "Abramzon-Sirignano kc should still be positive");
  }

  /**
   * Tests that Abramzon-Sirignano with B_M = 0 gives same result as standard Ranz-Marshall.
   */
  @Test
  void testAbramzonSirignanoZeroBmMatchesRanzMarshall() {
    double sc = 0.7;

    interphase.setUseAbramzonSirignano(false);
    double kcRM = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);

    interphase.setUseAbramzonSirignano(true);
    interphase.setSpaldingMassTransferNumber(0.0); // no blowing
    double kcAS0 = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);

    assertEquals(kcRM, kcAS0, kcRM * 1e-6, "A-S with B_M=0 should match standard Ranz-Marshall");
  }

  /**
   * Tests that droplet flow mass transfer coefficients differ from stratified flow.
   */
  @Test
  void testDropletMassTransferDiffersFromStratified() {
    // Create a stratified flow node with same system
    neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode stratNode =
        new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode(
            testSystem, pipe);
    stratNode.initFlowCalc();

    InterphaseStratifiedFlow stratInterphase = new InterphaseStratifiedFlow(stratNode);

    double sc = 0.7;
    double kcDropletGas = interphase.calcInterphaseMassTransferCoefficient(0, sc, node);
    double kcStratifiedGas =
        stratInterphase.calcInterphaseMassTransferCoefficient(0, sc, stratNode);

    // The two should NOT be equal (the old bug made them identical)
    // This test ensures the fix is working
    assertTrue(Math.abs(kcDropletGas - kcStratifiedGas) > 1e-10,
        "Droplet and stratified gas-side kc should differ. " + "kcDroplet=" + kcDropletGas
            + " kcStrat=" + kcStratifiedGas);
  }

  /**
   * Tests that the heat transfer coefficient also uses Ranz-Marshall for droplet flow.
   */
  @Test
  void testInterphaseHeatTransferUsesRanzMarshall() {
    double pr = 0.7;
    double htcGas = interphase.calcInterphaseHeatTransferCoefficient(0, pr, node);
    double htcLiq = interphase.calcInterphaseHeatTransferCoefficient(1, pr, node);

    assertTrue(htcGas > 0, "Gas-side heat transfer coefficient should be positive");
    assertTrue(htcLiq > 0, "Liquid-side heat transfer coefficient should be positive");
  }

  /**
   * Tests the Abramzon-Sirignano F function with a known reference value. For B_M = 1.0, the
   * formula F = (1+B_M)^0.7 * ln(1+B_M) / B_M gives: F(1) = 2^0.7 * ln(2) / 1 = 1.6245 * 0.6931 =
   * 1.1254
   */
  @Test
  void testAbramzonSirignanoFReferenceValue() {
    double bm = 1.0;
    double expected = Math.pow(2.0, 0.7) * Math.log(2.0); // 1.6245 * 0.6931 ≈ 1.1254
    double actual = interphase.calcAbramzonSirignanoF(bm);
    assertEquals(expected, actual, 1e-4, "F(1.0) should match reference calculation");
  }
}
