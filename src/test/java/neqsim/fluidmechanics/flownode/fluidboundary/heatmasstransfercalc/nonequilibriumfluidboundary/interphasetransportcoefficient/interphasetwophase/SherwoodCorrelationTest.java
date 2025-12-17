package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.interphasetransportcoefficient.interphasetwophase;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseTwoPhasePipeFlow;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseAnnularFlow;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseDropletFlow;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseStratifiedFlow;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for Sherwood number correlations in interphase transport coefficients.
 */
public class SherwoodCorrelationTest {
  private SystemInterface testSystem;
  private PipeData pipe;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(295.0, 5.0);
    testSystem.addComponent("methane", 0.1, 0);
    testSystem.addComponent("water", 0.05, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(3);

    pipe = new PipeData(0.1); // 10 cm diameter pipe
    pipe.setNodeLength(0.01);
  }

  @Test
  void testStratifiedFlowSherwoodNumber() {
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.initFlowCalc();

    InterphaseStratifiedFlow interphase = new InterphaseStratifiedFlow(node);

    // Test gas phase Sherwood number with typical Reynolds and Schmidt numbers
    double Re = 10000.0; // Turbulent flow
    double Sc = 0.7; // Typical gas Schmidt number
    double ShGas = interphase.calcSherwoodNumber(0, Re, Sc, node);
    assertTrue(ShGas > 0, "Gas phase Sherwood number should be positive");

    // Test liquid phase Sherwood number
    double ScLiquid = 1000.0; // Typical liquid Schmidt number
    double ShLiquid = interphase.calcSherwoodNumber(1, Re, ScLiquid, node);
    assertTrue(ShLiquid > 0, "Liquid phase Sherwood number should be positive");
  }

  @Test
  void testDropletFlowSherwoodNumber() {
    DropletFlowNode node = new DropletFlowNode(testSystem, pipe);
    node.setAverageDropletDiameter(0.0001); // 100 micron
    node.initFlowCalc();

    InterphaseDropletFlow interphase = new InterphaseDropletFlow(node);

    // Ranz-Marshall correlation: Sh = 2 + 0.6 * Re^0.5 * Sc^0.33
    // For Re = 0, Sh should be 2 (stagnant droplet)
    double Sh = interphase.calcSherwoodNumber(0, 0.0, 0.7, node);
    assertTrue(Sh >= 2.0, "Sherwood number for droplet should be at least 2 (stagnant limit)");
  }

  @Test
  void testAnnularFlowSherwoodNumber() {
    AnnularFlow node = new AnnularFlow(testSystem, pipe);
    node.initFlowCalc();

    InterphaseAnnularFlow interphase = new InterphaseAnnularFlow(node);

    // Test gas core Sherwood number
    double Re = 50000.0; // Typical high gas velocity
    double Sc = 0.7;
    double ShGas = interphase.calcSherwoodNumber(0, Re, Sc, node);
    assertTrue(ShGas > 0, "Gas core Sherwood number should be positive");

    // Test liquid film Sherwood number
    double ShLiquid = interphase.calcSherwoodNumber(1, Re, 1000.0, node);
    assertTrue(ShLiquid > 0, "Liquid film Sherwood number should be positive");
  }

  @Test
  void testSherwoodNumberIncreaseWithReynolds() {
    // Higher Reynolds number should generally lead to higher Sherwood number
    DropletFlowNode node = new DropletFlowNode(testSystem, pipe);
    node.setAverageDropletDiameter(0.0001);
    node.initFlowCalc();

    InterphaseDropletFlow interphase = new InterphaseDropletFlow(node);
    double Sc = 0.7;

    double ShLowRe = interphase.calcSherwoodNumber(0, 100.0, Sc, node);
    double ShHighRe = interphase.calcSherwoodNumber(0, 10000.0, Sc, node);

    assertTrue(ShHighRe > ShLowRe, "Higher Reynolds should give higher Sherwood number");
    assertTrue(ShLowRe < 10000, "Sherwood number should be within reasonable range");
  }

  @Test
  void testNusseltNumberCalculation() {
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.initFlowCalc();

    InterphaseTwoPhasePipeFlow interphase = new InterphaseTwoPhasePipeFlow(node);

    // Test Nusselt number for heat transfer (analogous to Sherwood)
    double Re = 10000.0;
    double Pr = 0.7; // Prandtl number for gas
    double Nu = interphase.calcNusseltNumber(0, Re, Pr, node);
    assertTrue(Nu >= 0, "Nusselt number should be non-negative");
  }

  @Test
  void testWallSherwoodNumber() {
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.initFlowCalc();

    InterphaseTwoPhasePipeFlow interphase = new InterphaseTwoPhasePipeFlow(node);

    // Test wall Sherwood number
    double Re = 10000.0;
    double Sc = 1000.0;
    double ShWall = interphase.calcWallSherwoodNumber(1, Re, Sc, node);
    assertTrue(ShWall >= 0, "Wall Sherwood number should be non-negative");
  }

  @Test
  void testLaminarFlowSherwoodNumber() {
    StratifiedFlowNode node = new StratifiedFlowNode(testSystem, pipe);
    node.initFlowCalc();

    InterphaseTwoPhasePipeFlow interphase = new InterphaseTwoPhasePipeFlow(node);

    // For laminar flow (Re < 2300), Sherwood should be 3.66 (fully developed)
    double Re = 1000.0;
    double Sc = 0.7;
    double Sh = interphase.calcSherwoodNumber(0, Re, Sc, node);
    assertTrue(Math.abs(Sh - 3.66) < 0.01, "Laminar flow Sherwood should be 3.66");
  }
}
