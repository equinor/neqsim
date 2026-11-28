package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundary;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Interface initialisation of a liquid in contact with gas that would hold all of it at equilibrium.
 *
 * <p>
 * The bulk as a whole is then single phase, so a flash of the bulk gives no interface compositions. The fluid boundary
 * has to find a mixture of the two bulk phases that does split and start from its tie line.
 * </p>
 */
class InterfaceTieLineInitialisationTest {
  private static final String[] NAMES = {"nitrogen", "CO2", "methane", "ethane", "propane", "n-butane", "n-pentane",
      "n-heptane"};
  private static final double[] FRACTIONS = {0.004, 0.06, 0.846, 0.050, 0.020, 0.010, 0.005, 0.005};

  /**
   * Gas and oil in equilibrium at 80 bara are compressed to 120 bara with little oil present: the mixture is single
   * phase at equilibrium, the two phases are not miscible, and the coupled film converges to an interface with two
   * distinct phases across which the heavy component evaporates.
   */
  @Test
  void undersaturatedGasOverLiquidStartsFromATieLineOnTheMixingLine() {
    SystemInterface bulk = bulk(120.0, 0.01);
    SystemInterface whole = bulk.clone();
    whole.setForcePhaseTypes(false);
    new ThermodynamicOperations(whole).TPflash();
    assertEquals(1, whole.getNumberOfPhases(), "the bulk as a whole must be single phase for this test");

    PipeData pipe = new PipeData(1.0, 4.5e-5);
    pipe.setNodeLength(1.0);
    StratifiedFlowNode node = new StratifiedFlowNode(bulk, pipe);
    node.setPhaseFraction(0, 0.995);
    node.setPhaseFraction(1, 0.005);
    node.setVelocity(0, 2.0);
    node.setVelocity(1, 0.3);
    node.init();
    FluidBoundaryInterface boundary = node.getFluidBoundary();
    boundary.setMassTransferCalc(true);
    boundary.setHeatTransferCalc(true);
    boundary.useFiniteFluxCorrection(true);
    boundary.useThermodynamicCorrections(true);
    boundary.setCoupledMassHeatTransferEnabled(true);

    // The starting interface is a tie line: every component has the same fugacity on its two sides.
    ((FluidBoundary) boundary).initInterphaseSystem();
    SystemInterface start = boundary.getInterphaseSystem();
    assertEquals(2, start.getNumberOfPhases());
    for (int component = 0; component < NAMES.length; component++) {
      double gasFugacity = start.getPhase(0).getComponent(component).getx()
          * start.getPhase(0).getComponent(component).getFugacityCoefficient();
      double oilFugacity = start.getPhase(1).getComponent(component).getx()
          * start.getPhase(1).getComponent(component).getFugacityCoefficient();
      assertEquals(1.0, gasFugacity / oilFugacity, 1.0e-6, "fugacity ratio of " + NAMES[component]);
    }
    assertTrue(start.getPhase(1).getDensity() > 1.2 * start.getPhase(0).getDensity());

    boundary.solve();

    SystemInterface interfaceState = boundary.getInterphaseSystem();
    double gasDensity = interfaceState.getPhase(0).getDensity();
    double oilDensity = interfaceState.getPhase(1).getDensity();
    assertTrue(oilDensity > 1.2 * gasDensity,
        "interface gas " + gasDensity + " kg/m3 and oil " + oilDensity + " kg/m3 must be distinct phases");
    int heavy = NAMES.length - 1;
    double ratio = interfaceState.getPhase(0).getComponent(heavy).getx()
        / interfaceState.getPhase(1).getComponent(heavy).getx();
    assertTrue(ratio < 0.5, "n-heptane equilibrium ratio at the interface " + ratio);
    assertTrue(boundary.getInterphaseMolarFlux(heavy) < 0.0,
        "n-heptane must leave the oil, flux " + boundary.getInterphaseMolarFlux(heavy));
  }

  /**
   * Build gas and oil that were in equilibrium at 80 bara and hold them at another pressure.
   *
   * @param pressure pressure in bara of the returned bulk
   * @param oilMolesPerGasMole moles of oil per mole of gas
   * @return two-phase bulk with forced gas and oil phase types
   */
  private static SystemInterface bulk(double pressure, double oilMolesPerGasMole) {
    SystemInterface fluid = new SystemSrkEos(277.15, 80.0);
    for (int component = 0; component < NAMES.length; component++) {
      fluid.addComponent(NAMES[component], FRACTIONS[component]);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(2, fluid.getNumberOfPhases());
    SystemInterface bulk = fluid.clone();
    bulk.setEmptyFluid();
    for (int component = 0; component < NAMES.length; component++) {
      bulk.addComponent(component, fluid.getPhase(0).getComponent(component).getx(), 0);
      bulk.addComponent(component, oilMolesPerGasMole * fluid.getPhase(1).getComponent(component).getx(), 1);
    }
    bulk.setPressure(pressure);
    bulk.setNumberOfPhases(2);
    bulk.setMaxNumberOfPhases(2);
    bulk.setPhaseType(0, PhaseType.GAS);
    bulk.setPhaseType(1, PhaseType.OIL);
    bulk.setForcePhaseTypes(true);
    bulk.init_x_y();
    bulk.initBeta();
    bulk.init(1);
    bulk.initProperties();
    return bulk;
  }
}
