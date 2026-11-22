package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Energy-balance tests for passive generic edges in {@link LoopedPipeNetwork}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class LoopedPipeNetworkEnergyBalanceTest {
  private static final double GRAVITY_M_S2 = 9.81;

  /**
   * An uphill Darcy edge must exchange enthalpy for gravitational potential energy.
   */
  @Test
  void testForwardUphillEdgeConservesEnthalpyPlusPotentialEnergy() {
    LoopedPipeNetwork network = solveNetwork(210.0, -300.0, 150.0, 100.0);
    LoopedPipeNetwork.NetworkPipe pipe = network.getPipe("riser");

    assertTrue(pipe.getFlowRate() > 0.0);
    assertTrue(pipe.isThermodynamicStateForward());
    assertSteadyAdiabaticEnergyBalance(pipe, -300.0, 100.0);
  }

  /**
   * Reversing the pressure gradient must reverse the physical inlet and the potential-energy term.
   */
  @Test
  void testReverseDownhillEdgeConservesEnthalpyPlusPotentialEnergy() {
    LoopedPipeNetwork network = solveNetwork(120.0, -300.0, 210.0, 100.0);
    LoopedPipeNetwork.NetworkPipe pipe = network.getPipe("riser");

    assertTrue(pipe.getFlowRate() < 0.0);
    assertFalse(pipe.isThermodynamicStateForward());
    assertSteadyAdiabaticEnergyBalance(pipe, 100.0, -300.0);
  }

  /**
   * Build and solve a pressure-driven water line with thermal-state propagation enabled.
   *
   * @param fromPressureBara from-node pressure in bara
   * @param fromElevationM from-node elevation in m
   * @param toPressureBara to-node pressure in bara
   * @param toElevationM to-node elevation in m
   * @return solved network
   */
  private LoopedPipeNetwork solveNetwork(double fromPressureBara, double fromElevationM, double toPressureBara,
      double toElevationM) {
    SystemSrkEos water = new SystemSrkEos(303.15, Math.max(fromPressureBara, toPressureBara));
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    water.setMultiPhaseCheck(false);

    LoopedPipeNetwork network = new LoopedPipeNetwork("vertical injection line");
    network.setFluidTemplate(water);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(100);
    network.setThermalHydraulicsEnabled(true);
    network.setCouplingMaxIterations(8);
    network.setCouplingTolerances(1.0e-5, 1.0);
    network.addSourceNode("from", fromPressureBara, 0.0, fromElevationM);
    network.addFixedPressureSinkNode("to", toPressureBara, toElevationM);
    network.addPipe("from", "to", "riser", 1500.0, 0.20);
    network.setNodeFluid("from", water);
    network.setNodeFluid("to", water);
    network.run();
    return network;
  }

  /**
   * Verify h + gz conservation on the physical inlet/outlet states.
   *
   * @param pipe solved edge
   * @param inletElevationM physical inlet elevation in m
   * @param outletElevationM physical outlet elevation in m
   */
  private void assertSteadyAdiabaticEnergyBalance(LoopedPipeNetwork.NetworkPipe pipe, double inletElevationM,
      double outletElevationM) {
    SystemInterface inlet = pipe.getInletFluid();
    SystemInterface outlet = pipe.getOutletFluid();
    double inletMechanicalEnergy = inlet.getEnthalpy("J/kg") + GRAVITY_M_S2 * inletElevationM;
    double outletMechanicalEnergy = outlet.getEnthalpy("J/kg") + GRAVITY_M_S2 * outletElevationM;
    assertEquals(inletMechanicalEnergy, outletMechanicalEnergy, 2.0,
        "adiabatic generic edge must conserve specific enthalpy plus gravitational potential energy");
  }
}
