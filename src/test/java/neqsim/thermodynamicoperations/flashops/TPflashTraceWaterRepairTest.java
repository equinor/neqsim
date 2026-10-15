package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Public lifecycle qualification and failure contracts for the trace-water repair in PR 4320.
 *
 * <p>
 * Public lifecycle cases complement the deliberately corrupted endpoint regression. They do not claim to reproduce the
 * unavailable original process snapshot. Reflection is confined to explicit guard and failure-injection checks.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TPflashTraceWaterRepairTest {
  /** Reservoir-to-producer scaling must leave a normalized feed for the public TP flash. */
  @Test
  void publicReservoirGasProducerPreservesFeedAndEquilibrium() {
    SystemInterface fluid = new SystemSrkEos(363.15, 250.0);
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("ethane", 6.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    SimpleReservoir reservoir = new SimpleReservoir("public trace-water reservoir");
    reservoir.setReservoirFluid(fluid, 3.0e8, 0.0, 1.0e7);
    double[] expected = fluid.getPhase("gas").getMolarComposition();
    StreamInterface producer = reservoir.addGasProducer("gas");
    producer.setFlowRate(4.0, "MSm3/day");
    SystemInterface gas = producer.getFluid();
    TPflash flash = new TPflash(gas);
    for (double temperature : new double[] {363.15, 363.151, 363.15}) {
      gas.setTemperature(temperature);
      flash.run();
      assertQualifiedEndpoint(gas);
      for (int i = 0; i < expected.length; i++) {
        assertEquals(expected[i], gas.getPhase(0).getComponent(i).getz(), 1.0e-12);
      }
    }
  }

  /**
   * Builds and flashes a synthetic trace-water oil using the requested EOS.
   *
   * @param model zero for SRK, one for PR, two for CPA
   * @return equilibrated synthetic oil/water fluid
   */
  private SystemInterface fluid(int model) {
    SystemInterface fluid = model == 0 ? new SystemSrkEos(307.0, 10.5)
        : model == 1 ? new SystemPrEos(307.0, 10.5) : new SystemSrkCPAstatoil(307.0, 10.5);
    fluid.addComponent("n-heptane", 0.997);
    fluid.addComponent("water", 0.003);
    fluid.setMixingRule(model == 2 ? 10 : 2);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    return fluid;
  }

  /** Public repeated, changed-state and cold-reference checks across SRK, PR and CPA. */
  @Test
  void publicTemperatureSweepAndPressureReturnPreserveEquilibrium() {
    for (int model = 0; model < 3; model++) {
      SystemInterface warm = fluid(model);
      TPflash operation = new TPflash(warm);
      double previousEntropy = Double.NEGATIVE_INFINITY;
      for (double temperature : new double[] {306.999, 307.0, 307.001}) {
        warm.setTemperature(temperature);
        operation.run();
        warm.init(2);
        assertQualified(warm);
        assertTrue(warm.getEntropy() > previousEntropy);
        previousEntropy = warm.getEntropy();
        SystemInterface cold = fluid(model);
        cold.setTemperature(temperature);
        new ThermodynamicOperations(cold).TPflash();
        cold.init(2);
        assertEquals(cold.getGibbsEnergy(), warm.getGibbsEnergy(), 1.0e-7);
        assertEquals(cold.getEntropy(), warm.getEntropy(), 1.0e-8);
      }
      warm.setPressure(40.0);
      operation.run();
      assertQualified(warm);
      warm.setPressure(10.5);
      warm.setTemperature(307.0);
      operation.run();
      assertQualified(warm);
      SystemInterface reference = fluid(model);
      warm.init(2);
      assertEquals(reference.getEntropy(), warm.getEntropy(), 1.0e-8);
    }
  }

  /** Checks actual pump PS closure, inventory and energy for the public synthetic fluid. */
  @Test
  void publicPumpClosesEntropyMassAndEnergy() {
    for (int model = 0; model < 3; model++) {
      Stream feed = new Stream("trace-water oil", fluid(model));
      feed.setFlowRate(1000.0, "kg/hr");
      feed.run();
      feed.getFluid().init(3);
      double entropy = feed.getFluid().getEntropy();
      double enthalpy = feed.getFluid().getEnthalpy();
      Pump pump = new Pump("trace-water pump", feed);
      pump.setOutletPressure(40.0);
      pump.run();
      SystemInterface outlet = pump.getOutletStream().getFluid();
      outlet.init(3);
      assertQualified(outlet);
      assertEquals(entropy, outlet.getEntropy(), PSFlash.entropyTolerance(outlet, entropy));
      assertEquals(feed.getFlowRate("kg/hr"), outlet.getFlowRate("kg/hr"), 1.0e-8);
      assertEquals(outlet.getEnthalpy() - enthalpy, pump.getPower(), 1.0e-6);
      assertTrue(pump.getPower() > 0.0);
      assertTrue(outlet.getTemperature() > feed.getTemperature());
      assertTrue(outlet.getTemperature() - feed.getTemperature() < 2.0);
    }
  }

  /**
   * A successful repair cannot satisfy only the looser pre-existing 1e-8 acceptance threshold.
   *
   * @throws Exception if a private contract cannot be inspected
   */
  @Test
  void repairAcceptanceRejectsSmallButOutOfContractInventoryError() throws Exception {
    SystemInterface candidate = fluid(0);
    candidate.setBeta(0, candidate.getBeta(0) + 5.0e-9);
    candidate.setBeta(1, candidate.getBeta(1) - 5.0e-9);
    assertFalse((Boolean) invoke(new TPflash(candidate), "isQualifiedTraceWaterRepair",
        new Class<?>[] {SystemInterface.class}, candidate));
  }

  /**
   * Non-finite feed data fail the repair contract instead of being accepted through a comparison with NaN.
   *
   * @throws Exception if a private contract cannot be inspected
   */
  @Test
  void exhaustedRepairIsObservableAndOperationCanBeReused() throws Exception {
    SystemInterface candidate = fluid(0);
    double fraction = candidate.getPhase(0).getComponent(0).getz();
    candidate.getPhase(0).getComponent(0).setz(Double.NaN);
    TPflash flash = new TPflash(candidate);
    invoke(flash, "rescueWaterRichEndpoint", new Class<?>[0]);
    InvocationTargetException exception = assertThrows(InvocationTargetException.class,
        () -> invoke(flash, "validateTraceWaterAqueousRepair", new Class<?>[0]));
    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertTrue(exception.getCause().getMessage().contains("trace-water aqueous repair"));
    candidate.getPhase(0).getComponent(0).setz(fraction);
    flash.run();
    assertQualified(candidate);
  }

  /**
   * Balanced endpoints and explicitly constrained phase states must not clone or reinitialize in this guard.
   *
   * @throws Exception if a private contract cannot be inspected
   */
  @Test
  void cheapGuardDoesNotCloneOrInitializeBalancedOrLockedStates() throws Exception {
    CountingSystem candidate = new CountingSystem();
    candidate.addComponent("n-heptane", 0.997);
    candidate.addComponent("water", 0.003);
    candidate.setMixingRule(2);
    candidate.setMultiPhaseCheck(true);
    new ThermodynamicOperations(candidate).TPflash();
    candidate.clones = 0;
    candidate.initializations = 0;
    invoke(new TPflash(candidate), "rescueWaterRichEndpoint", new Class<?>[0]);
    assertEquals(0, candidate.clones);
    assertEquals(0, candidate.initializations);
    candidate.allowPhaseShift(false);
    for (int phase = 0; phase < 2; phase++) {
      candidate.setBeta(phase, candidate.getPhase(phase).getType() == PhaseType.AQUEOUS ? 0.9999996 : 0.0000004);
    }
    invoke(new TPflash(candidate), "rescueWaterRichEndpoint", new Class<?>[0]);
    assertEquals(0, candidate.clones);
    assertEquals(0, candidate.initializations);
    assertEquals(0.9999996, candidate.getBeta(candidate.getPhaseNumberOfPhase("aqueous")), 0.0);
  }

  /**
   * A repaired gas/aqueous active set must be copied without replaying an ordinary flash.
   *
   * @throws Exception if the recovery contract cannot be invoked
   */
  @Test
  void gasAqueousRepairDoesNotDiscardQualifiedCandidate() throws Exception {
    CountingSystem candidate = new CountingSystem();
    candidate.setTemperature(363.15);
    candidate.setPressure(250.0);
    candidate.addComponent("methane", 0.993);
    candidate.addComponent("water", 0.007);
    candidate.setMixingRule(2);
    candidate.setMultiPhaseCheck(true);
    new ThermodynamicOperations(candidate).TPflash();
    assertQualified(candidate);
    double referenceBeta = candidate.getBeta(0);
    candidate.setBeta(0, 0.5);
    candidate.setBeta(1, 0.5);
    candidate.clones = 0;
    candidate.initializations = 0;
    invoke(new TPflash(candidate), "rescueWaterRichEndpoint", new Class<?>[0]);
    assertQualified(candidate);
    assertEquals(referenceBeta, candidate.getBeta(0), 1.0e-10);
    assertEquals(1, candidate.clones, "Only the detached repair candidate is needed");
    assertEquals(1, candidate.initializations, "Copying the accepted endpoint needs one live initialization");
  }

  /**
   * Inspects a private guard only for explicit numerical contract tests.
   *
   * @param flash operation owning the guard
   * @param name method name
   * @param types argument types
   * @param arguments argument values
   * @return method result
   * @throws Exception if the guard cannot be invoked or fails
   */
  private Object invoke(TPflash flash, String name, Class<?>[] types, Object... arguments) throws Exception {
    Method method = TPflash.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(flash, arguments);
  }

  /**
   * Asserts all phase and component balances and equilibrium residuals without normalizing the result.
   *
   * @param fluid returned equilibrium to inspect
   */
  private void assertQualified(SystemInterface fluid) {
    assertEquals(2, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertQualifiedEndpoint(fluid);
  }

  /**
   * Checks closure while allowing a saturated producer to retain a single gas phase.
   *
   * @param fluid returned equilibrium to inspect
   */
  private void assertQualifiedEndpoint(SystemInterface fluid) {
    assertTrue(fluid.getNumberOfPhases() >= 1 && fluid.getNumberOfPhases() <= 3);
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double beta = fluid.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaSum += beta;
      double sum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction >= 0.0 && fraction <= 1.0);
        sum += fraction;
      }
      assertEquals(1.0, sum, 1.0e-12);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double inventory = 0.0;
      double reference = Double.NaN;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        inventory += fluid.getBeta(phase) * fraction;
        double logFugacity = Math.log(fraction)
            + Math.log(fluid.getPhase(phase).getComponent(component).getFugacityCoefficient());
        assertTrue(Double.isFinite(logFugacity));
        if (phase == 0) {
          reference = logFugacity;
        } else {
          assertEquals(reference, logFugacity, 1.0e-8);
        }
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), inventory, 1.0e-10);
    }
  }

  /** Test-local work counters; no production telemetry or shared static cache. */
  private static final class CountingSystem extends SystemSrkEos {
    private static final long serialVersionUID = 1L;
    private int clones;
    private int initializations;

    /** Creates the synthetic reference state. */
    private CountingSystem() {
      super(307.0, 10.5);
    }

    /** {@inheritDoc} */
    @Override
    public CountingSystem clone() {
      clones++;
      return (CountingSystem) super.clone();
    }

    /** {@inheritDoc} */
    @Override
    public void init(int type) {
      initializations++;
      super.init(type);
    }
  }
}
