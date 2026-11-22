package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regressions for flow-driven valve closure, vessel withdrawal and reopening.
 *
 * <p>
 * The vessel checks use external valve outlets as boundary fluxes. Merely using the separator's own withdrawal streams
 * would conceal a closed valve that still removes stale flow from the vessel inventory.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ThrottlingValveZeroFlowInventoryTest {
  /** Verifies exact closure and hydraulic recovery for independently initialized gas flows. */
  @Test
  void transientGasClosurePublishesZeroAtBothPortsAndCanReopen() {
    double[] flowRatesKgPerHour = {1.0, 1000.0, 1000000.0};
    for (double flowRate : flowRatesKgPerHour) {
      ValveFixture fixture = gasValve(flowRate);
      fixture.valve.runTransient(0.25, UUID.randomUUID());
      double openFlow = fixture.valve.getOutletStream().getFlowRate("kg/hr");
      assertTrue(openFlow > 0.0, "An initialized open gas valve must carry flow");

      fixture.valve.setPercentValveOpening(0.0);
      fixture.valve.runTransient(0.25, UUID.randomUUID());
      assertZeroAtBothPorts(fixture.valve);
      fixture.valve.runTransient(0.25, UUID.randomUUID());
      assertZeroAtBothPorts(fixture.valve);

      fixture.valve.setPercentValveOpening(50.0);
      fixture.valve.runTransient(0.25, UUID.randomUUID());
      assertSamePositivePortFlow(fixture.valve);
      assertEquals(openFlow, fixture.valve.getOutletStream().getFlowRate("kg/hr"), Math.max(1.0e-8, openFlow * 1.0e-6),
          "Reopening must recover the same hydraulic boundary state");
    }
  }

  /** Verifies a liquid valve's closure and recovery without inferring flow from its opening tag alone. */
  @Test
  void transientLiquidClosurePublishesZeroAtBothPortsAndCanReopen() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("n-heptane", 1.0);
    fluid.setMixingRule("classic");
    ValveFixture fixture = valve(fluid, 1000.0);
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    double initialFlow = fixture.valve.getOutletStream().getFlowRate("kg/hr");
    assertTrue(initialFlow > 0.0);

    fixture.valve.setPercentValveOpening(0.0);
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertZeroAtBothPorts(fixture.valve);
    fixture.valve.setPercentValveOpening(50.0);
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertSamePositivePortFlow(fixture.valve);
    assertEquals(initialFlow, fixture.valve.getOutletStream().getFlowRate("kg/hr"), initialFlow * 1.0e-6);
  }

  /** Verifies a blocked gas outlet accumulates physical inventory and raises vessel pressure. */
  @Test
  void blockedGasOutletConservesExternalInventoryAndRaisesPressure() {
    VesselFixture fixture = vessel();
    double initialPressure = fixture.separator.getPressure();
    double initialMass = fixture.separator.getThermoSystem().getMass("kg");
    fixture.gasValve.setPercentValveOpening(0.0);
    assertExternalInventoryBalance(fixture, 8, 0.25);

    assertTrue(fixture.separator.getPressure() > initialPressure + 0.01,
        "A continuing feed cannot leave blocked-vessel pressure pinned at its initial value");
    assertTrue(fixture.separator.getThermoSystem().getMass("kg") > initialMass + 0.1,
        "The mass entering a blocked gas outlet must accumulate in vessel inventory");
    assertZeroAtBothPorts(fixture.gasValve);

    fixture.gasValve.setPercentValveOpening(50.0);
    assertExternalInventoryBalance(fixture, 8, 0.25);
    assertSamePositivePortFlow(fixture.gasValve);
  }

  /** Verifies the corresponding liquid withdrawal contract against the same external boundaries. */
  @Test
  void blockedLiquidOutletRetainsLiquidAndConservesExternalInventory() {
    VesselFixture fixture = vessel();
    double initialLevel = fixture.separator.getLiquidLevel();
    fixture.liquidValve.setPercentValveOpening(0.0);
    assertExternalInventoryBalance(fixture, 16, 0.25);

    assertTrue(fixture.separator.getLiquidLevel() > initialLevel,
        "Liquid must accumulate while feed continues and the liquid outlet is closed");
    assertZeroAtBothPorts(fixture.liquidValve);
    fixture.liquidValve.setPercentValveOpening(50.0);
    assertExternalInventoryBalance(fixture, 8, 0.25);
    assertSamePositivePortFlow(fixture.liquidValve);
  }

  /**
   * Verifies a sealed adiabatic vessel has no hidden mass or cached enthalpy boundary flux.
   *
   * <p>
   * The inlet closes through the native ESD stroke state machine. Conservation is assessed only after every valve port
   * is actually zero, so actuator travel and the explicit scan's preceding flux are not mistaken for a sealed boundary.
   * Internal energy is checked to a declared relative numerical tolerance of 1e-5 with a 0.001 J floor.
   * </p>
   */
  @Test
  void fullyIsolatedVesselPreservesInventoryAndEnergyAndCanReadmitFeed() {
    VesselFixture fixture = vessel();
    double timeStepSeconds = 0.25;
    fixture.inletValve.setStrokeTime(1.0);
    fixture.inletValve.trip();
    fixture.gasValve.setPercentValveOpening(0.0);
    fixture.liquidValve.setPercentValveOpening(0.0);
    for (int step = 0; step < 4; step++) {
      fixture.process.runTransient(timeStepSeconds, UUID.randomUUID());
    }
    assertTrue(fixture.inletValve.hasTripCompleted(), "The declared one-second native ESD stroke must complete");
    assertZeroAtBothPorts(fixture.inletValve);
    assertZeroAtBothPorts(fixture.gasValve);
    assertZeroAtBothPorts(fixture.liquidValve);

    SystemInterface isolatedState = fixture.separator.getThermoSystem();
    double isolatedMass = isolatedState.getMass("kg");
    double isolatedEnergy = isolatedState.getInternalEnergy();
    double energyToleranceJoules = Math.max(0.001, Math.abs(isolatedEnergy) * 1.0e-5);
    Map<String, Double> isolatedComponents = componentMoles(isolatedState);
    for (int step = 0; step < 20; step++) {
      fixture.process.runTransient(timeStepSeconds, UUID.randomUUID());
      assertZeroAtBothPorts(fixture.inletValve);
      assertZeroAtBothPorts(fixture.gasValve);
      assertZeroAtBothPorts(fixture.liquidValve);
      SystemInterface current = fixture.separator.getThermoSystem();
      assertEquals(isolatedMass, current.getMass("kg"), 1.0e-7, "A sealed vessel cannot acquire or lose physical mass");
      assertEquals(isolatedEnergy, current.getInternalEnergy(), energyToleranceJoules,
          "Cached properties of zero-flow streams cannot inject or remove physical energy");
      Map<String, Double> currentComponents = componentMoles(current);
      for (String name : isolatedComponents.keySet()) {
        assertEquals(isolatedComponents.get(name).doubleValue(), currentComponents.get(name).doubleValue(), 1.0e-6,
            "A sealed vessel cannot acquire or lose component " + name);
      }
      assertTrue(Double.isFinite(current.getPressure("bara")) && current.getPressure("bara") > 0.0);
      assertTrue(Double.isFinite(current.getTemperature()) && current.getTemperature() > 0.0);
      assertTrue(fixture.separator.getLiquidLevel() >= 0.0 && fixture.separator.getLiquidLevel() <= 1.0);
    }

    fixture.inletValve.energize();
    fixture.inletValve.setPercentValveOpening(50.0);
    assertExternalInventoryBalance(fixture, 1, timeStepSeconds);
    assertSamePositivePortFlow(fixture.inletValve);
    assertTrue(fixture.separator.getThermoSystem().getMass("kg") > isolatedMass,
        "Reopening the inlet must admit physical feed to the isolated inventory");
  }

  /** Verifies recovery from zero driving pressure without prescribing a new upstream flow. */
  @Test
  void zeroPressureDifferentialRecoversWhenDrivingPressureReturns() {
    assertPressureDifferentialRecovery(20.0);
  }

  /** Verifies opposing pressure produces no reverse flow and can later recover forward flow. */
  @Test
  void opposingPressureDifferentialRecoversWhenDrivingPressureReturns() {
    assertPressureDifferentialRecovery(25.0);
  }

  /** Verifies an independently dry inlet is not mistaken for a valve-owned zero withdrawal. */
  @Test
  void independentlyDryInletRemainsDryAtPositivePressureDifferential() {
    ValveFixture fixture = gasValve(1000.0);
    fixture.inlet.setFlowRate(0.0, "kg/hr");
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertZeroAtBothPorts(fixture.valve);
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertZeroAtBothPorts(fixture.valve);
  }

  /** Verifies steady flow-given evaluation does not acquire transient upstream-withdrawal semantics. */
  @Test
  void steadyFlowGivenContractPreservesItsSpecifiedUpstreamFlow() {
    ValveFixture fixture = gasValve(1000.0);
    fixture.valve.setCalculateSteadyState(true);
    fixture.valve.setPercentValveOpening(0.0);
    fixture.valve.run(UUID.randomUUID());

    assertEquals(1000.0, fixture.inlet.getFlowRate("kg/hr"), 1.0e-8,
        "Legacy steady flow-given evaluation must preserve its prescribed inlet flow");
    assertEquals(1000.0, fixture.valve.getOutletStream().getFlowRate("kg/hr"), 1.0e-8,
        "Steady pressure reduction retains its existing given-flow contract");
  }

  /** Verifies public rollback restores both enabled and disabled valve-owned recovery behavior. */
  @Test
  void snapshotRestoresValveOwnedZeroFlowRecoveryState() {
    ValveFixture fixture = gasValve(1000.0);
    ThrottlingValve.TransientState beforeOwnedClosure = fixture.valve.captureTransientState();
    fixture.valve.setPercentValveOpening(0.0);
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertZeroAtBothPorts(fixture.valve);
    ThrottlingValve.TransientState ownedClosure = fixture.valve.captureTransientState();
    Stream.TransientState closedInlet = fixture.inlet.captureTransientState();

    // An externally dry source with pre-closure valve history must not be rehydrated.
    fixture.valve.restoreTransientState(beforeOwnedClosure);
    fixture.inlet.restoreTransientState(closedInlet);
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertZeroAtBothPorts(fixture.valve);

    // Inlet stream state is an external participant and is restored explicitly.
    fixture.valve.restoreTransientState(ownedClosure);
    fixture.inlet.restoreTransientState(closedInlet);
    fixture.valve.setPercentValveOpening(50.0);
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertSamePositivePortFlow(fixture.valve);
  }

  /**
   * Tests one nonpositive pressure-difference state followed by a valid forward state.
   *
   * @param blockedOutletPressureBara outlet pressure equal to or greater than the inlet pressure
   */
  private static void assertPressureDifferentialRecovery(double blockedOutletPressureBara) {
    ValveFixture fixture = gasValve(1000.0);
    fixture.valve.setOutletPressure(blockedOutletPressureBara, "bara");
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertZeroAtBothPorts(fixture.valve);
    assertEquals(20.0, fixture.inlet.getPressure("bara"), 1.0e-10,
        "Opposing boundary pressure must not create upstream pressure or reverse flow");
    fixture.valve.setOutletPressure(10.0, "bara");
    fixture.valve.runTransient(0.25, UUID.randomUUID());
    assertSamePositivePortFlow(fixture.valve);
  }

  /**
   * Asserts exact zero at both material ports and a finite retained pressure boundary.
   *
   * @param valve valve whose material ports are observed
   */
  private static void assertZeroAtBothPorts(ThrottlingValve valve) {
    assertEquals(0.0, valve.getInletStream().getFlowRate("kg/sec"), 0.0,
        "Zero downstream discharge must also mean zero upstream withdrawal");
    assertEquals(0.0, valve.getOutletStream().getFlowRate("kg/sec"), 0.0);
    assertEquals(0.0, valve.getInletStream().getFluid().getTotalNumberOfMoles(), 0.0);
    assertEquals(0.0, valve.getOutletStream().getFluid().getTotalNumberOfMoles(), 0.0);
    assertTrue(Double.isFinite(valve.getInletStream().getPressure("bara")));
    assertTrue(Double.isFinite(valve.getOutletStream().getPressure("bara")));
  }

  /**
   * Asserts finite positive hydraulic discharge and continuity between the two valve ports.
   *
   * @param valve valve whose material ports are observed
   */
  private static void assertSamePositivePortFlow(ThrottlingValve valve) {
    double inlet = valve.getInletStream().getFlowRate("kg/sec");
    double outlet = valve.getOutletStream().getFlowRate("kg/sec");
    assertTrue(Double.isFinite(inlet) && inlet > 0.0, "Recovered withdrawal must be finite and positive");
    assertTrue(Double.isFinite(outlet) && outlet > 0.0, "Recovered discharge must be finite and positive");
    assertEquals(inlet, outlet, Math.max(1.0e-12, inlet * 1.0e-9));
  }

  /**
   * Advances the native process and compares inventory with external material boundary fluxes.
   *
   * <p>
   * The explicit sequence consumes the current inlet flux and the preceding outlet fluxes. Reading the previous
   * external valve discharges therefore checks the integrator's boundary convention without assuming a separator's
   * private withdrawal state is correct.
   * </p>
   *
   * @param fixture initialized independent vessel slice
   * @param stepCount number of physical steps to execute
   * @param timeStepSeconds physical timestep in seconds
   */
  private static void assertExternalInventoryBalance(VesselFixture fixture, int stepCount, double timeStepSeconds) {
    double initialMass = fixture.separator.getThermoSystem().getMass("kg");
    Map<String, Double> initialComponents = componentMoles(fixture.separator.getThermoSystem());
    Map<String, Double> integratedComponents = new LinkedHashMap<String, Double>();
    for (String name : initialComponents.keySet()) {
      integratedComponents.put(name, Double.valueOf(0.0));
    }
    double integratedNetMass = 0.0;
    for (int step = 0; step < stepCount; step++) {
      double previousGasDischarge = fixture.gasValve.getOutletStream().getFlowRate("kg/sec");
      double previousLiquidDischarge = fixture.liquidValve.getOutletStream().getFlowRate("kg/sec");
      Map<String, Double> previousGasComponents = componentMoles(fixture.gasValve.getOutletStream().getFluid());
      Map<String, Double> previousLiquidComponents = componentMoles(fixture.liquidValve.getOutletStream().getFluid());
      fixture.process.runTransient(timeStepSeconds, UUID.randomUUID());
      double currentInlet = fixture.inletValve.getOutletStream().getFlowRate("kg/sec");
      integratedNetMass += timeStepSeconds * (currentInlet - previousGasDischarge - previousLiquidDischarge);
      Map<String, Double> currentInletComponents = componentMoles(fixture.inletValve.getOutletStream().getFluid());
      for (String name : initialComponents.keySet()) {
        double increment = timeStepSeconds * (currentInletComponents.get(name).doubleValue()
            - previousGasComponents.get(name).doubleValue() - previousLiquidComponents.get(name).doubleValue());
        integratedComponents.put(name, Double.valueOf(integratedComponents.get(name).doubleValue() + increment));
      }
      SystemInterface state = fixture.separator.getThermoSystem();
      assertEquals(initialMass + integratedNetMass, state.getMass("kg"), 1.0e-7,
          "Vessel mass must close against external inlet and outlet valve boundaries");
      Map<String, Double> currentComponents = componentMoles(state);
      for (String name : initialComponents.keySet()) {
        assertEquals(initialComponents.get(name).doubleValue() + integratedComponents.get(name).doubleValue(),
            currentComponents.get(name).doubleValue(), 1.0e-6, "Component inventory must close for " + name);
      }
      assertTrue(Double.isFinite(state.getPressure("bara")) && state.getPressure("bara") > 0.0);
      assertTrue(Double.isFinite(state.getTemperature()) && state.getTemperature() > 0.0);
      assertTrue(Double.isFinite(state.getInternalEnergy()));
      assertTrue(fixture.separator.getLiquidLevel() >= 0.0 && fixture.separator.getLiquidLevel() <= 1.0);
    }
  }

  /**
   * Extracts the native component amount of an inventory or component molar rate of a stream.
   *
   * @param fluid native extensive thermodynamic state
   * @return component-name to amount or rate in moles or moles per second
   */
  private static Map<String, Double> componentMoles(SystemInterface fluid) {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      String name = fluid.getComponent(component).getComponentName();
      result.put(name, Double.valueOf(fluid.getComponent(component).getNumberOfmoles()));
    }
    return result;
  }

  /**
   * Builds a gas valve with a controlled given-flow steady initialization.
   *
   * @param flowKgPerHour given upstream mass flow in kilograms per hour
   * @return independent valve and inlet fixture
   */
  private static ValveFixture gasValve(double flowKgPerHour) {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return valve(fluid, flowKgPerHour);
  }

  /**
   * Initializes a valve's Cv from a declared design point without dynamic source-flow mutation.
   *
   * @param fluid initialized-composition source fluid
   * @param flowKgPerHour given upstream mass flow in kilograms per hour
   * @return independent valve and inlet fixture
   */
  private static ValveFixture valve(SystemInterface fluid, double flowKgPerHour) {
    Stream inlet = new Stream("source", fluid);
    inlet.setFlowRate(flowKgPerHour, "kg/hr");
    inlet.run();
    ThrottlingValve valve = new ThrottlingValve("flow-driven valve", inlet);
    valve.setOutletPressure(10.0, "bara");
    valve.autoSize(1.0, 50.0);
    valve.setCalculateSteadyState(false);
    return new ValveFixture(inlet, valve);
  }

  /**
   * Builds the same synthetic rich-gas inventory slice used by the dynamic P&amp;ID study.
   *
   * @return independent initialized vessel, valves and native process
   */
  private static VesselFixture vessel() {
    SystemInterface fluid = new SystemSrkEos(308.15, 45.0);
    fluid.addComponent("methane", 0.780);
    fluid.addComponent("ethane", 0.080);
    fluid.addComponent("propane", 0.050);
    fluid.addComponent("n-butane", 0.025);
    fluid.addComponent("i-butane", 0.015);
    fluid.addComponent("n-pentane", 0.025);
    fluid.addComponent("n-hexane", 0.015);
    fluid.addComponent("CO2", 0.008);
    fluid.addComponent("nitrogen", 0.002);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    ESDValve inletValve = new ESDValve("20-ESDV-001", feed);
    inletValve.setOutletPressure(40.0, "bara");
    Separator separator = new Separator("20-V-101", inletValve.getOutletStream());
    separator.setInternalDiameter(1.0);
    separator.setSeparatorLength(3.0);
    separator.setOrientation("horizontal");
    ThrottlingValve gasValve = new ThrottlingValve("20-PCV-001", separator.getGasOutStream());
    gasValve.setOutletPressure(35.0, "bara");
    ThrottlingValve liquidValve = new ThrottlingValve("20-LCV-001", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(20.0, "bara");
    SetPoint pressureEquality = new SetPoint("inlet pressure equality", inletValve, "pressure",
        separator.getGasOutStream());
    ProcessSystem process = new ProcessSystem("V101 closed-valve inventory regression");
    process.add(feed);
    process.add(pressureEquality);
    process.add(inletValve);
    process.add(separator);
    process.add(gasValve);
    process.add(liquidValve);
    process.run();
    inletValve.autoSize(1.0, 50.0);
    gasValve.autoSize(1.0, 50.0);
    liquidValve.autoSize(1.0, 50.0);
    process.run();
    inletValve.setCalculateSteadyState(false);
    separator.setCalculateSteadyState(false);
    gasValve.setCalculateSteadyState(false);
    liquidValve.setCalculateSteadyState(false);
    separator.setLiquidLevel(0.5);
    separator.initializeTransientCalculation();
    return new VesselFixture(process, separator, inletValve, gasValve, liquidValve);
  }

  /**
   * Independent valve fixture retaining the externally owned upstream stream.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ValveFixture {
    private final Stream inlet;
    private final ThrottlingValve valve;

    /**
     * Retains the public objects participating in a valve-only regression.
     *
     * @param inlet externally owned upstream stream
     * @param valve tested flow-driven valve
     */
    private ValveFixture(Stream inlet, ThrottlingValve valve) {
      this.inlet = inlet;
      this.valve = valve;
    }
  }

  /**
   * Independent native vessel slice with observable external material boundaries.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class VesselFixture {
    private final ProcessSystem process;
    private final Separator separator;
    private final ESDValve inletValve;
    private final ThrottlingValve gasValve;
    private final ThrottlingValve liquidValve;

    /**
     * Retains the public objects participating in the native vessel regression.
     *
     * @param process native transient process
     * @param separator physical inventory
     * @param inletValve upstream supply/isolation valve
     * @param gasValve gas withdrawal valve
     * @param liquidValve liquid withdrawal valve
     */
    private VesselFixture(ProcessSystem process, Separator separator, ESDValve inletValve, ThrottlingValve gasValve,
        ThrottlingValve liquidValve) {
      this.process = process;
      this.separator = separator;
      this.inletValve = inletValve;
      this.gasValve = gasValve;
      this.liquidValve = liquidValve;
    }
  }
}
