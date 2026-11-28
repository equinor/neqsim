package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Coupled film between natural gas and a small aqueous phase of water and triethylene glycol (CPA).
 *
 * <p>
 * The interface fractions span nine orders of magnitude here (TEG in the gas, hydrocarbons in the liquid). These are
 * equation and direction checks, not measured transfer rates.
 * </p>
 */
class CoupledGasAqueousFilmTest {
  private static final String[] NAMES = {"nitrogen", "CO2", "methane", "ethane", "propane", "n-butane", "water", "TEG"};
  private static final double[] FRACTIONS = {0.005, 0.015, 0.903, 0.050, 0.020, 0.007, 150.0e-6, 20.0e-6};
  private static final int WATER = 6;
  private static final int TEG = 7;

  /** Gas and aqueous phase in equilibrium exchange nothing. */
  @Test
  void equilibriumGivesNoFlux() {
    FluidBoundaryInterface boundary = solve(100.0, 278.15, 2.0);
    for (int component = 0; component < NAMES.length; component++) {
      assertEquals(0.0, boundary.getInterphaseMolarFlux(component), 1.0e-12, NAMES[component]);
    }
    assertEquals(278.15, boundary.getInterphaseSystem().getTemperature(), 1.0e-6);
  }

  /**
   * Water leaves the liquid when the pressure falls or the temperature rises and enters it in the opposite cases, for
   * fast and for slow gas. The transfer is controlled by the gas side, so it follows the gas speed.
   */
  @Test
  void waterFollowsPressureAndTemperatureInBothDirections() {
    double[][] states = {{95.0, 278.15, -1.0}, {60.0, 278.15, -1.0}, {100.0, 283.15, -1.0}, {105.0, 278.15, 1.0},
        {140.0, 278.15, 1.0}, {100.0, 273.15, 1.0}};
    for (double[] state : states) {
      double fast = solve(state[0], state[1], 3.0).getInterphaseMolarFlux(WATER);
      double slow = solve(state[0], state[1], 0.2).getInterphaseMolarFlux(WATER);
      String label = state[0] + " bara, " + state[1] + " K";
      assertTrue(fast * state[2] > 0.0, label + ": water flux " + fast);
      assertTrue(slow * state[2] > 0.0, label + ": water flux at low gas speed " + slow);
      assertTrue(Math.abs(fast) > 5.0 * Math.abs(slow), label + ": fast " + fast + ", slow " + slow);
    }
  }

  /** The glycol is all but involatile: its flux is orders of magnitude below that of water. */
  @Test
  void glycolBarelyCrossesTheInterface() {
    FluidBoundaryInterface boundary = solve(60.0, 278.15, 3.0);
    assertTrue(
        Math.abs(boundary.getInterphaseMolarFlux(TEG)) < 1.0e-3 * Math.abs(boundary.getInterphaseMolarFlux(WATER)));
    SystemInterface interfaceState = boundary.getInterphaseSystem();
    assertTrue(interfaceState.getPhase(0).getComponent(TEG).getx() < 1.0e-6);
    assertTrue(interfaceState.getPhase(1).getComponent(TEG).getx() > 0.1);
  }

  /** An oil and an aqueous liquid together are not a two-phase film problem. */
  @Test
  void threePhaseBulkIsRejected() {
    SystemInterface fluid = fluid(100.0, 278.15);
    fluid.addComponent("n-nonane", 0.01);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(3, fluid.getNumberOfPhases());
    StratifiedFlowNode node = new StratifiedFlowNode(fluid, pipe());
    node.setVelocity(0, 2.0);
    node.setVelocity(1, 0.1);
    node.init();
    FluidBoundaryInterface boundary = node.getFluidBoundary();
    boundary.setMassTransferCalc(true);
    boundary.setHeatTransferCalc(true);
    boundary.setCoupledMassHeatTransferEnabled(true);
    assertThrows(IllegalStateException.class, boundary::solve);
  }

  private static PipeData pipe() {
    PipeData pipe = new PipeData(1.0, 4.5e-5);
    pipe.setNodeLength(1.0);
    return pipe;
  }

  private static SystemInterface fluid(double pressure, double temperature) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temperature, pressure);
    for (int component = 0; component < NAMES.length; component++) {
      fluid.addComponent(NAMES[component], FRACTIONS[component]);
    }
    return fluid;
  }

  /**
   * Solve the film between gas and aqueous phase that were in equilibrium at 100 bara and 278.15 K.
   *
   * @param pressure pressure in bara at which the two phases are held
   * @param temperature temperature in K at which the two phases are held
   * @param gasVelocity gas speed in m/s
   * @return solved boundary
   */
  private static FluidBoundaryInterface solve(double pressure, double temperature, double gasVelocity) {
    SystemInterface fluid = fluid(100.0, 278.15);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(2, fluid.getNumberOfPhases());
    int gas = fluid.getPhase(0).getType() == PhaseType.GAS ? 0 : 1;
    assertEquals(PhaseType.AQUEOUS, fluid.getPhase(1 - gas).getType());
    SystemInterface bulk = fluid.clone();
    bulk.setEmptyFluid();
    for (int component = 0; component < NAMES.length; component++) {
      bulk.addComponent(component, 1000.0 * fluid.getPhase(gas).getComponent(component).getx(), 0);
      bulk.addComponent(component, fluid.getPhase(1 - gas).getComponent(component).getx(), 1);
    }
    bulk.setTemperature(temperature);
    bulk.setPressure(pressure);
    bulk.setNumberOfPhases(2);
    bulk.setMaxNumberOfPhases(2);
    bulk.setPhaseType(0, PhaseType.GAS);
    bulk.setPhaseType(1, PhaseType.AQUEOUS);
    bulk.setForcePhaseTypes(true);
    bulk.init_x_y();
    bulk.initBeta();
    bulk.init(1);
    bulk.initProperties();
    StratifiedFlowNode node = new StratifiedFlowNode(bulk, pipe());
    node.setPhaseFraction(0, 1.0 - 2.0e-4);
    node.setPhaseFraction(1, 2.0e-4);
    node.setVelocity(0, gasVelocity);
    node.setVelocity(1, 0.05);
    node.init();
    FluidBoundaryInterface boundary = node.getFluidBoundary();
    boundary.setMassTransferCalc(true);
    boundary.setHeatTransferCalc(true);
    boundary.useFiniteFluxCorrection(true);
    boundary.useThermodynamicCorrections(true);
    boundary.setCoupledMassHeatTransferEnabled(true);
    boundary.solve();
    return boundary;
  }
}
