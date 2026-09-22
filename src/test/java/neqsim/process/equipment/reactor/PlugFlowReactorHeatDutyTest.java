package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.reactor.PlugFlowReactor.EnergyMode;
import neqsim.process.equipment.reactor.PlugFlowReactor.IntegrationMethod;
import neqsim.process.equipment.reactor.PlugFlowReactor.ThermodynamicCoupling;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/** Energy-ledger regressions for issue #3859; kinetic parameters are illustrative. */
class PlugFlowReactorHeatDutyTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @EnumSource(IntegrationMethod.class)
  void parallelChannelsUseTheirOwnExtents(IntegrationMethod method) {
    for (ThermodynamicCoupling coupling : ThermodynamicCoupling.values()) {
      PlugFlowReactor reactor = parallelReactor(method, coupling, 60);
      reactor.run();
      assertParallelBalances(reactor);
      if (method == IntegrationMethod.RK4 && coupling == ThermodynamicCoupling.FULLY_COUPLED) {
        assertEquals(0.3227404763455448, flow(reactor, "CO2"), 1e-7);
        assertEquals(0.20291727348042354, flow(reactor, "CO"), 1e-7);
        assertEquals(-18813.491596985403, reactor.getHeatDuty(), 0.02);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(IntegrationMethod.class)
  void serialAndOverlappingChannelsCloseEnergyLedger(IntegrationMethod method) {
    for (boolean includeDirectChannel : new boolean[] {false, true}) {
      PlugFlowReactor reactor = reactor(method, ThermodynamicCoupling.FULLY_COUPLED, 80);
      reactor.addReaction(decomposition("first", "CO2", "hydrogen", 1e10, 125000.0, 30000.0));
      KineticReaction shift = new KineticReaction("reverse water gas shift");
      shift.addReactant("CO2", 1.0, 1.0);
      shift.addReactant("hydrogen", 1.0, 1.0);
      shift.addProduct("CO", 1.0);
      shift.addProduct("water", 1.0);
      shift.setPreExponentialFactor(0.1);
      shift.setActivationEnergy(0.0);
      shift.setHeatOfReaction(15000.0);
      reactor.addReaction(shift);
      if (includeDirectChannel) {
        reactor.addReaction(decomposition("direct", "CO", "water", 4e10, 135000.0, 45000.0));
      }
      reactor.run();
      assertTrue(flow(reactor, "CO") > 1e-4, "The serial reaction must actually proceed");
      // Hess-consistent enthalpies allow an independent outlet-species energy ledger
      // even when the direct and serial paths have the same net stoichiometry.
      assertParallelBalances(reactor);
    }
  }

  @ParameterizedTest
  @EnumSource(IntegrationMethod.class)
  void gridRefinementConvergesCompositionAndDuty(IntegrationMethod method) {
    PlugFlowReactor reference = parallelReactor(IntegrationMethod.RK4, ThermodynamicCoupling.FULLY_COUPLED, 320);
    reference.run();
    double previousFlowError = Double.POSITIVE_INFINITY;
    double previousDutyError = Double.POSITIVE_INFINITY;
    for (int steps : new int[] {20, 40, 80}) {
      PlugFlowReactor reactor = parallelReactor(method, ThermodynamicCoupling.FULLY_COUPLED, steps);
      reactor.run();
      assertParallelBalances(reactor);
      double flowError = Math.abs(flow(reactor, "CO2") - flow(reference, "CO2"));
      double dutyError = Math.abs(reactor.getHeatDuty() - reference.getHeatDuty());
      assertTrue(flowError < previousFlowError, "Species profile must converge with refinement");
      assertTrue(dutyError < previousDutyError, "Duty must converge independently with refinement");
      previousFlowError = flowError;
      previousDutyError = dutyError;
    }
  }

  @ParameterizedTest
  @EnumSource(IntegrationMethod.class)
  void singleReactionPreservesStoichiometrySignAndUnits(IntegrationMethod method) {
    PlugFlowReactor reactor = reactor(method, ThermodynamicCoupling.FULLY_COUPLED, 60);
    KineticReaction reaction = new KineticReaction("two moles per reaction");
    reaction.addReactant("formic acid", 2.0, 1.0);
    reaction.addProduct("CO2", 2.0);
    reaction.addProduct("hydrogen", 2.0);
    reaction.setPreExponentialFactor(1e10);
    reaction.setActivationEnergy(125000.0);
    reaction.setHeatOfReaction(-60000.0);
    reactor.addReaction(reaction);
    reactor.run();
    double extent = (1.0 - flow(reactor, "formic acid")) / 2.0;
    double expected = 60000.0 * extent;
    assertTrue(expected > 1.0);
    assertEquals(expected, reactor.getHeatDuty(), 1e-7);
    assertEquals(expected, reactor.getHeatDuty("W"), 1e-7);
    assertEquals(expected / 1e3, reactor.getHeatDuty("kW"), 1e-10);
    assertEquals(expected / 1e6, reactor.getHeatDuty("MW"), 1e-13);
    reactor.run();
    assertEquals(expected, reactor.getHeatDuty(), 1e-7, "A second run must not accumulate old duty");
    reaction.setHeatOfReaction(0.0);
    reactor.setEnergyMode(EnergyMode.ADIABATIC);
    reactor.run();
    assertEquals(0.0, reactor.getHeatDuty(), 0.0, "Mode changes must not expose an old isothermal duty");
  }

  @Test
  void catalystRateScalingAndMultipleTubesUseSameEnergyLedger() {
    PlugFlowReactor reactor = reactor(IntegrationMethod.RK4, ThermodynamicCoupling.FULLY_COUPLED, 80);
    CatalystBed catalyst = new CatalystBed(10.0, 0.5, 800.0);
    catalyst.setActivityFactor(0.7);
    reactor.setCatalystBed(catalyst);
    reactor.setCatalystEffectivenessEnabled(true);
    reactor.setNumberOfTubes(2);
    KineticReaction first = decomposition("mass basis", "CO2", "hydrogen", 1e10 / 800.0, 125000.0, 30000.0);
    first.setRateBasis(KineticReaction.RateBasis.CATALYST_MASS);
    KineticReaction second = decomposition("volume basis", "CO", "water", 4e10, 135000.0, 45000.0);
    reactor.addReaction(first);
    reactor.addReaction(second);
    reactor.run();
    assertParallelBalances(reactor);
  }

  private void assertParallelBalances(PlugFlowReactor reactor) {
    double firstExtent = flow(reactor, "CO2");
    double secondExtent = flow(reactor, "CO");
    assertTrue(firstExtent > 0.0 && secondExtent > 0.0);
    assertEquals(1.0, flow(reactor, "formic acid") + firstExtent + secondExtent, 1e-10);
    assertEquals(firstExtent, flow(reactor, "hydrogen"), 1e-10);
    assertEquals(secondExtent, flow(reactor, "water"), 1e-10);
    assertEquals(9.0, flow(reactor, "nitrogen"), 1e-10);
    double expected = -(30000.0 * firstExtent + 45000.0 * secondExtent);
    assertEquals(expected, reactor.getHeatDuty(), 1e-7, "Energy must close separately from species balances");
    assertEquals(650.0, reactor.getOutletTemperature(), 1e-10);
  }

  private double flow(PlugFlowReactor reactor, String component) {
    return reactor.getOutletStream().getFluid().getComponent(component).getNumberOfmoles();
  }

  private PlugFlowReactor parallelReactor(IntegrationMethod method, ThermodynamicCoupling coupling, int steps) {
    PlugFlowReactor reactor = reactor(method, coupling, steps);
    reactor.addReaction(decomposition("channel 1", "CO2", "hydrogen", 1e10, 125000.0, 30000.0));
    reactor.addReaction(decomposition("channel 2", "CO", "water", 4e10, 135000.0, 45000.0));
    return reactor;
  }

  private PlugFlowReactor reactor(IntegrationMethod method, ThermodynamicCoupling coupling, int steps) {
    SystemInterface fluid = new SystemPrEos(650.0, 2.0);
    fluid.addComponent("formic acid", 1.0);
    fluid.addComponent("CO2", 0.0);
    fluid.addComponent("hydrogen", 0.0);
    fluid.addComponent("CO", 0.0);
    fluid.addComponent("water", 0.0);
    fluid.addComponent("nitrogen", 9.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.run();
    PlugFlowReactor reactor = new PlugFlowReactor("reactor", feed);
    reactor.setLength(2.0, "m");
    reactor.setDiameter(0.3, "m");
    reactor.setNumberOfSteps(steps);
    reactor.setIntegrationMethod(method.name());
    reactor.setThermodynamicCoupling(coupling);
    reactor.setEnergyMode(EnergyMode.ISOTHERMAL);
    return reactor;
  }

  private KineticReaction decomposition(String name, String firstProduct, String secondProduct,
      double preExponentialFactor, double activationEnergy, double heatOfReaction) {
    KineticReaction reaction = new KineticReaction(name);
    reaction.addReactant("formic acid", 1.0, 1.0);
    reaction.addProduct(firstProduct, 1.0);
    reaction.addProduct(secondProduct, 1.0);
    reaction.setPreExponentialFactor(preExponentialFactor);
    reaction.setActivationEnergy(activationEnergy);
    reaction.setHeatOfReaction(heatOfReaction);
    return reaction;
  }
}
