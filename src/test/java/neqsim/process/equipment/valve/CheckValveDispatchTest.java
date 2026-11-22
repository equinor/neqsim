package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.dynamics.DynamicCapability;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies passive non-return behavior through the actual native dispatch paths.
 *
 * @author NeqSim study validation
 * @version 1.0
 */
class CheckValveDispatchTest {
  /**
   * Builds a single-phase gas valve with an explicit downstream pressure boundary.
   *
   * @return initialized native check valve
   */
  private CheckValve valve() {
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("NRV-FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    CheckValve valve = new CheckValve("NRV", feed);
    valve.setCv(100.0);
    valve.setCrackingPressure(0.5);
    valve.setOutletPressure(9.0, "bara");
    valve.getMechanicalDesign().getValveSizingMethod().setAllowChoked(true);
    return valve;
  }

  /** Checks actual UUID open/closed state and exact ideal zero discharge. */
  @Test
  void uuidDispatchUsesConfiguredCrackingPressure() {
    CheckValve valve = valve();
    UUID first = UUID.randomUUID();
    valve.run(first);
    assertTrue(valve.isOpen());
    assertEquals(first, valve.getCalculationIdentifier());
    valve.setOutletPressure(9.75, "bara");
    UUID second = UUID.randomUUID();
    valve.run(second);
    assertFalse(valve.isOpen());
    assertEquals(0.0, valve.getOutletStream().getFlowRate("mole/sec"), 0.0);
    assertEquals(9.75, valve.getOutletStream().getPressure("bara"), 1.0e-12);
    assertEquals(100.0, valve.getCv(), 1.0e-12);
    assertEquals(second, valve.getOutletStream().getCalculationIdentifier());
  }

  /** Checks that the no-argument path can reopen after a zero-flow outlet. */
  @Test
  void convenienceRunDoesNotInventDownstreamPressureAfterClosure() {
    CheckValve valve = valve();
    valve.setOutletPressure(9.75);
    valve.run();
    assertFalse(valve.isOpen());
    assertEquals(0.0, valve.getOutletStream().getFlowRate("mole/sec"), 0.0);
    valve.setOutletPressure(9.0);
    valve.run();
    assertTrue(valve.isOpen());
    assertTrue(valve.getOutletStream().getFlowRate("kg/hr") > 0.0);
  }

  /** Checks exact transient withdrawal and recovery using retained intensive state. */
  @Test
  void transientClosesBothPortsAndReopensFromValidZeroDemand() {
    CheckValve valve = valve();
    valve.setCalculateSteadyState(false);
    valve.runTransient(0.1, UUID.randomUUID());
    assertTrue(valve.isOpen());
    double flow = valve.getOutletStream().getFlowRate("mole/sec");
    assertTrue(flow > 0.0);
    valve.setOutletPressure(12.0, "bara");
    UUID closedId = UUID.randomUUID();
    valve.runTransient(0.1, closedId);
    assertFalse(valve.isOpen());
    assertEquals(0.0, valve.getInletStream().getFlowRate("mole/sec"), 0.0);
    assertEquals(0.0, valve.getOutletStream().getFlowRate("mole/sec"), 0.0);
    assertEquals(12.0, valve.getOutletStream().getPressure("bara"), 1.0e-12);
    valve.setOutletPressure(9.0, "bara");
    valve.runTransient(0.1, UUID.randomUUID());
    assertTrue(valve.isOpen());
    assertEquals(flow, valve.getOutletStream().getFlowRate("mole/sec"), flow * 1.0e-9);
  }

  /** Checks unit-independent thresholds, including a zero-cracking equal-pressure seat. */
  @Test
  void absoluteUnitsAndThresholdDoNotAllowZeroDifferentialFlow() {
    CheckValve valve = valve();
    valve.setOutletPressure(950000.0, "Pa");
    valve.run(UUID.randomUUID());
    assertTrue(valve.isOpen());
    valve.setCrackingPressure(0.0);
    valve.setOutletPressure(1000000.0, "Pa");
    valve.run(UUID.randomUUID());
    assertFalse(valve.isOpen());
    assertEquals(0.0, valve.getOutletStream().getFlowRate("mole/sec"), 0.0);
  }

  /** Checks configuration/time validation occurs before hydraulic mutation. */
  @Test
  void invalidConfigurationAndTimeStepsAreRejected() {
    CheckValve valve = valve();
    assertThrows(IllegalArgumentException.class, () -> valve.setCrackingPressure(-0.1));
    assertThrows(IllegalArgumentException.class, () -> valve.setCrackingPressure(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> valve.runTransient(-0.1, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> valve.runTransient(Double.NaN, UUID.randomUUID()));
    assertEquals(1000.0, valve.getInletStream().getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Checks an owned checkpoint restores passive state and stream aliases. */
  @Test
  void snapshotRestoresCrackStateAndPreservesOutletIdentity() {
    CheckValve valve = valve();
    valve.setCalculateSteadyState(false);
    valve.runTransient(0.1, UUID.randomUUID());
    ThrottlingValve.TransientState state = valve.captureTransientState();
    Stream outlet = (Stream) valve.getOutletStream();
    double originalFlow = outlet.getFlowRate("mole/sec");
    valve.setCrackingPressure(3.0);
    valve.setOutletPressure(12.0);
    valve.runTransient(0.1, UUID.randomUUID());
    assertFalse(valve.isOpen());
    valve.restoreTransientState(state);
    assertSame(outlet, valve.getOutletStream());
    assertTrue(valve.isOpen());
    assertEquals(0.5, valve.getCrackingPressure(), 0.0);
    assertEquals(originalFlow, outlet.getFlowRate("mole/sec"), 0.0);
  }

  /** Checks ProcessSystem dispatch uses passive state instead of the parent valve path. */
  @Test
  void processSystemExecutesNonReturnBehavior() {
    CheckValve valve = valve();
    ProcessSystem process = new ProcessSystem();
    process.add(valve.getInletStream());
    process.add(valve);
    process.run();
    assertTrue(valve.isOpen());
    valve.setCalculateSteadyState(false);
    valve.setOutletPressure(12.0);
    process.runTransient(0.1, UUID.randomUUID());
    assertFalse(valve.isOpen());
    assertNotNull(valve.getCalculationIdentifier());
    assertEquals(0.0, valve.getOutletStream().getFlowRate("mole/sec"), 0.0);
  }

  /** Checks unsupported actuator ownership is rejected and the audited native class is discoverable. */
  @Test
  void rejectsExternalActuationAndDeclaresAuditedCapability() {
    CheckValve valve = valve();
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, valve.getDynamicCapability());
    valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    assertThrows(IllegalStateException.class, () -> valve.run(UUID.randomUUID()));
    valve.setTravelModel(ValveTravelModel.NONE);
    valve.setController(new ControllerDeviceBaseClass("FORBIDDEN-CONTROLLER"));
    assertThrows(IllegalStateException.class, () -> valve.runTransient(0.1, UUID.randomUUID()));
  }

  /** Checks a flash-free capacity query respects seating without publishing flow or execution credit. */
  @Test
  void capacityQueryAppliesGateWithoutWithdrawingInventory() {
    CheckValve valve = valve();
    double inletFlow = valve.getInletStream().getFlowRate("mole/sec");
    double outletFlow = valve.getOutletStream().getFlowRate("mole/sec");
    UUID calculationId = valve.getCalculationIdentifier();
    valve.setOutletPressure(9.75);
    assertEquals(0.0, valve.calculateMolarFlow(), 0.0);
    assertFalse(valve.isOpen());
    valve.setOutletPressure(9.0);
    double capacity = valve.calculateMolarFlow();
    assertTrue(capacity > 0.0);
    assertTrue(valve.isOpen());
    assertEquals(inletFlow, valve.getInletStream().getFlowRate("mole/sec"), 0.0);
    assertEquals(outletFlow, valve.getOutletStream().getFlowRate("mole/sec"), 0.0);
    assertEquals(calculationId, valve.getCalculationIdentifier());
    assertEquals(100.0, valve.getCv(), 0.0);
    valve.setCalculateSteadyState(false);
    valve.runTransient(0.0, UUID.randomUUID());
    assertEquals(capacity, valve.getOutletStream().getFlowRate("mole/sec"), capacity * 1.0e-9);
  }

  /** Checks zero/tiny/positive seed rates have the same capacity without changing connected physical state. */
  @Test
  void capacityQueryIsSeedInvariantAfterPhysicalClosure() {
    CheckValve valve = valve();
    valve.setCalculateSteadyState(false);
    valve.setOutletPressure(12.0);
    valve.runTransient(0.1, UUID.randomUUID());
    double reference = Double.NaN;
    for (double seed : new double[] {0.0, 1.0e-50, 1.0, 100.0, 10000.0}) {
      valve.getInletStream().getFluid().setTotalNumberOfMoles(seed);
      valve.setOutletPressure(9.0);
      double beforeEnthalpy = valve.getInletStream().getFluid().getEnthalpy();
      UUID beforeId = valve.getCalculationIdentifier();
      double query = valve.calculateMolarFlow();
      assertTrue(Double.isFinite(query) && query > 0.0);
      if (Double.isNaN(reference)) {
        reference = query;
      }
      assertEquals(reference, query, reference * 1.0e-10);
      assertEquals(seed, valve.getInletStream().getFlowRate("mole/sec"), Math.abs(seed) * 1.0e-12);
      assertEquals(beforeEnthalpy, valve.getInletStream().getFluid().getEnthalpy(), 0.0);
      assertEquals(beforeId, valve.getCalculationIdentifier());
      valve.runTransient(0.0, UUID.randomUUID());
      assertEquals(query, valve.getOutletStream().getFlowRate("mole/sec"), query * 1.0e-9);
    }
  }
}
