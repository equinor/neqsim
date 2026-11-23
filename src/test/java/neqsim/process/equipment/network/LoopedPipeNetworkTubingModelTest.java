package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.LoopedPipeNetwork.ArtificialLiftType;
import neqsim.process.equipment.network.LoopedPipeNetwork.NetworkPipe;
import neqsim.process.equipment.network.LoopedPipeNetwork.TubingModel;
import neqsim.process.equipment.pipeline.PipeHagedornBrown;
import neqsim.process.equipment.pipeline.VfpTable;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.integrated.GasLiftPerformanceCurve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests correlation, gas-lift and VFP-table vertical lift models of {@link LoopedPipeNetwork} tubing elements.
 *
 * @author ESOL
 * @version 1.0
 */
class LoopedPipeNetworkTubingModelTest {

  /**
   * Creates a live black oil.
   *
   * @param methane methane moles, controlling the solution gas
   * @return oil fluid at 80 C
   */
  private static SystemInterface oil(double methane) {
    SystemInterface fluid = new SystemSrkEos(353.15, 100.0);
    fluid.addComponent("methane", methane);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-hexane", 0.12);
    fluid.addComponent("n-heptane", 0.25);
    fluid.addComponent("n-decane", 0.35);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds reservoir, IPR, tubing and a fixed tubing-head pressure.
   *
   * @param fluid network fluid
   * @param model tubing model
   * @return network with elements "ipr" and "tubing" and nodes "BH" and "WH"
   */
  private static LoopedPipeNetwork oilWell(SystemInterface fluid, TubingModel model) {
    LoopedPipeNetwork network = new LoopedPipeNetwork("oil well");
    network.setFluidTemplate(fluid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);
    network.addSourceNode("reservoir", 220.0, 0.0);
    network.addJunctionNode("BH");
    network.addFixedPressureSinkNode("WH", 20.0);
    network.addWellIPR("reservoir", "BH", "ipr", 2.0e-6, false);
    network.addTubing("BH", "WH", "tubing", 2500.0, 0.1, 90.0, model);
    return network;
  }

  /** The network operating point must satisfy the stand-alone Hagedorn-Brown pipe at the solved rate and BHP. */
  @Test
  void hagedornBrownTubingMatchesStandaloneCorrelation() {
    LoopedPipeNetwork network = oilWell(oil(0.20), TubingModel.HAGEDORN_BROWN);
    network.run();

    assertTrue(network.isConverged());
    NetworkPipe tubing = network.getPipe("tubing");
    assertEquals("TUBING_HAGEDORN_BROWN", tubing.getTubingModelStatus());
    double bhp = network.getNodePressure("BH");
    double rateKgHr = network.getPipeFlowRate("ipr");
    assertTrue(rateKgHr > 0.0 && bhp > 20.0 && bhp < 220.0, "rate " + rateKgHr + " BHP " + bhp);

    SystemInterface inlet = oil(0.20);
    inlet.setPressure(bhp, "bara");
    inlet.setTemperature(network.getNode("BH").getTemperature(), "K");
    Stream stream = new Stream("inlet", inlet);
    stream.setFlowRate(rateKgHr, "kg/hr");
    stream.run();
    PipeHagedornBrown reference = new PipeHagedornBrown("reference", stream);
    reference.setLength(2500.0);
    reference.setDiameter(0.1);
    reference.setPipeWallRoughness(tubing.getRoughness());
    reference.setElevation(2500.0);
    reference.setNumberOfIncrements(tubing.getTubingSegments());
    reference.run();
    assertEquals(20.0, reference.getOutletStream().getPressure("bara"), 0.5);
  }

  /** Gas lift mixed in at the valve raises oil rate, and the network gas-lift response feeds the allocator. */
  @Test
  void rigorousGasLiftIncreasesProductionAndGivesResponseCurve() {
    SystemInterface liftGas = new SystemSrkEos(353.15, 100.0);
    liftGas.addComponent("methane", 0.9);
    liftGas.addComponent("ethane", 0.1);
    liftGas.setMixingRule("classic");

    LoopedPipeNetwork natural = oilWell(oil(0.05), TubingModel.BEGGS_BRILL);
    natural.run();
    double naturalRate = natural.getPipeFlowRate("ipr");

    LoopedPipeNetwork lifted = oilWell(oil(0.05), TubingModel.BEGGS_BRILL);
    lifted.setLiftGasFluid(liftGas);
    NetworkPipe tubing = lifted.getPipe("tubing");
    tubing.setArtificialLiftType(ArtificialLiftType.GAS_LIFT);
    tubing.setGasLiftRate(1.0);
    tubing.setGasLiftInjectionDepth(2000.0);
    lifted.run();

    assertTrue(natural.isConverged() && lifted.isConverged());
    assertEquals("TUBING_BEGGS_BRILL_GASLIFT", tubing.getTubingModelStatus());
    assertTrue(lifted.getPipeFlowRate("ipr") > naturalRate * 1.05,
        "lifted " + lifted.getPipeFlowRate("ipr") + " natural " + naturalRate);

    double[] liftRates = {0.0, 50000.0, 100000.0};
    double[] oilRates = lifted.calculateGasLiftResponse("tubing", "ipr", liftRates);
    assertTrue(oilRates[1] > oilRates[0], oilRates[0] + " -> " + oilRates[1]);
    GasLiftPerformanceCurve curve = new GasLiftPerformanceCurve(liftRates, oilRates);
    assertEquals(oilRates[1], curve.oilRateAt(50000.0), 1.0e-9);
    assertEquals(1.0, tubing.getGasLiftRate(), 0.0);
    assertEquals(ArtificialLiftType.GAS_LIFT, tubing.getArtificialLiftType());
    assertThrows(IllegalArgumentException.class,
        () -> lifted.calculateGasLiftResponse("tubing", "ipr", new double[] {100.0, 50.0}));
  }

  /** A VFP well reproduces the linear table BHP = 1.2 THP + 10 + 20 q at the solved rate, also when extrapolating. */
  @Test
  void vfpTableTubingFollowsTableAndExtrapolatesRate() {
    String deck = "VFPPROD\n1 0 'GAS' 'WGR' 'OGR' 'THP' '' 'METRIC' 'BHP' /\n"
        + "500000 1000000 /\n20 80 /\n0 /\n0 /\n0 /\n" + "1 1 1 1 44 54 /\n2 1 1 1 116 126 /\n";
    SystemInterface gas = new SystemSrkEos(330.0, 100.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");

    LoopedPipeNetwork network = new LoopedPipeNetwork("gas well");
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);
    network.addSourceNode("reservoir", 250.0, 0.0);
    network.addJunctionNode("BH");
    network.addFixedPressureSinkNode("WH", 50.0);
    network.addWellIPR("reservoir", "BH", "ipr", 3.0e-14, true);
    network.addTubingVfp("BH", "WH", "tubing", VfpTable.fromString(deck));
    network.run();

    assertTrue(network.isConverged());
    SystemInterface standard = gas.clone();
    standard.setTemperature(288.15, "K");
    standard.setPressure(1.01325, "bara");
    new ThermodynamicOperations(standard).TPflash();
    standard.initProperties();
    double rateMSm3d = network.getPipeFlowRate("ipr") * 24.0 / standard.getDensity("kg/m3") / 1.0e6;
    assertTrue(rateMSm3d > 1.0, "rate is outside the table axis: " + rateMSm3d);
    assertEquals(1.2 * 50.0 + 10.0 + 20.0 * rateMSm3d, network.getNodePressure("BH"), 0.05);
    assertEquals("TUBING_VFP_TABLE_EXTRAPOLATED", network.getPipe("tubing").getTubingModelStatus());
  }
}
