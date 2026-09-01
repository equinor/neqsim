package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the adaptive acceleration that lets a stalling direct-substitution recycle upgrade itself to Wegstein
 * instead of requiring the caller to enable it on every loop.
 *
 * @author NeqSim
 * @version 1.0
 */
class RecycleAdaptiveAccelerationTest {

  /**
   * Creates a small two-component gas fluid.
   *
   * @return configured gas fluid
   */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds a process with a single gas recycle loop around a separator.
   *
   * @return a runnable process containing a recycle named "recycle"
   */
  private static ProcessSystem buildRecycleProcess() {
    return buildRecycleProcess(0.1);
  }

  /**
   * Builds a process with a configurable fraction of separator gas returned through the recycle.
   *
   * @param recycleFraction separator gas fraction returned to the mixer
   * @return a runnable process containing a recycle named "recycle"
   */
  private static ProcessSystem buildRecycleProcess(double recycleFraction) {
    ProcessSystem process = new ProcessSystem("recycle process");

    Stream feed = new Stream("feed", createGasFluid());
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    Stream recycleGas = new Stream("recycle gas", createGasFluid());
    recycleGas.setFlowRate(5000.0, "kg/hr");
    recycleGas.setTemperature(25.0, "C");
    recycleGas.setPressure(50.0, "bara");
    process.add(recycleGas);

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleGas);
    process.add(mixer);

    Cooler cooler = new Cooler("cooler", mixer.getOutletStream());
    cooler.setOutTemperature(15.0, "C");
    process.add(cooler);

    Separator separator = new Separator("separator", cooler.getOutletStream());
    process.add(separator);

    Splitter splitter = new Splitter("splitter", separator.getGasOutStream());
    splitter.setSplitFactors(new double[] { 1.0 - recycleFraction, recycleFraction });
    process.add(splitter);

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(recycleGas);
    recycle.setTolerance(1e-4);
    process.add(recycle);
    return process;
  }

  /** Ordinary process runs retain legacy direct substitution until automatic tuning or the caller opts in. */
  @Test
  void testAdaptiveAccelerationIsOffByDefault() {
    Recycle recycle = new Recycle("recycle");
    assertFalse(recycle.isAdaptiveAcceleration());
    assertFalse(recycle.isAccelerationAutoUpgraded());
    assertEquals(AccelerationMethod.DIRECT_SUBSTITUTION, recycle.getAccelerationMethod());
  }

  /** An explicit acceleration method must pin the loop and stand the adaptive upgrade down. */
  @Test
  void testExplicitMethodDisablesAutoUpgrade() {
    ProcessSystem process = buildRecycleProcess();
    Recycle recycle = (Recycle) process.getUnit("recycle");
    recycle.setAccelerationMethod(AccelerationMethod.DIRECT_SUBSTITUTION);
    process.run();
    assertEquals(AccelerationMethod.DIRECT_SUBSTITUTION, recycle.getAccelerationMethod());
    assertFalse(recycle.isAccelerationAutoUpgraded(),
        "an explicitly configured method must never be replaced by the adaptive logic");
  }

  /** Disabling adaptive acceleration must keep the loop on direct substitution however badly it stalls. */
  @Test
  void testAdaptiveAccelerationCanBeDisabled() {
    ProcessSystem process = buildRecycleProcess();
    Recycle recycle = (Recycle) process.getUnit("recycle");
    recycle.setAdaptiveAcceleration(false);
    process.run();
    assertFalse(recycle.isAdaptiveAcceleration());
    assertEquals(AccelerationMethod.DIRECT_SUBSTITUTION, recycle.getAccelerationMethod());
    assertFalse(recycle.isAccelerationAutoUpgraded());
  }

  /** A recycle loop must still converge with the adaptive logic active. */
  @Test
  void testRecycleStillConvergesWithAdaptiveAcceleration() {
    ProcessSystem process = buildRecycleProcess();
    Recycle recycle = (Recycle) process.getUnit("recycle");
    recycle.setAdaptiveAcceleration(true);
    process.run();
    assertTrue(recycle.solved(), "recycle should converge with adaptive acceleration enabled");
  }

  /** A slowly contracting recycle should detect the stall, upgrade itself, and still converge. */
  @Test
  void testAdaptiveAccelerationUpgradesSlowRecycle() {
    ProcessSystem adaptiveProcess = buildRecycleProcess(0.8);
    Recycle adaptiveRecycle = (Recycle) adaptiveProcess.getUnit("recycle");
    adaptiveRecycle.setAdaptiveAcceleration(true);
    adaptiveProcess.run();

    assertTrue(adaptiveRecycle.isAccelerationAutoUpgraded(),
        "A recycle contracting by only 20% per pass should trigger adaptive acceleration");
    assertEquals(AccelerationMethod.WEGSTEIN, adaptiveRecycle.getAccelerationMethod());
    assertTrue(adaptiveRecycle.solved(), "The accelerated recycle should converge");

    adaptiveRecycle.setAdaptiveAcceleration(false);
    assertEquals(AccelerationMethod.DIRECT_SUBSTITUTION, adaptiveRecycle.getAccelerationMethod(),
        "Disabling adaptive acceleration must undo an automatically selected method");
  }

  /** Resetting the iteration counter must not destroy the cross-pass stall bookkeeping. */
  @Test
  void testResetIterationsKeepsAdaptiveState() {
    ProcessSystem process = buildRecycleProcess();
    Recycle recycle = (Recycle) process.getUnit("recycle");
    recycle.setAdaptiveAcceleration(true);
    process.run();
    recycle.resetIterations();
    assertEquals(0, recycle.getIterations());
    assertTrue(recycle.isAdaptiveAcceleration());
  }

  /** An explicit adaptive reset must return an auto-upgraded loop to direct substitution. */
  @Test
  void testResetAdaptiveAccelerationRestoresDirectSubstitution() {
    ProcessSystem process = buildRecycleProcess();
    Recycle recycle = (Recycle) process.getUnit("recycle");
    recycle.setAdaptiveAcceleration(true);
    process.run();
    recycle.resetAdaptiveAcceleration();
    assertEquals(AccelerationMethod.DIRECT_SUBSTITUTION, recycle.getAccelerationMethod());
    assertFalse(recycle.isAccelerationAutoUpgraded());
  }

  /** Automatic tuning may enable adaptive acceleration, but an explicit caller opt-out always wins. */
  @Test
  void testAutoAdaptiveAccelerationRespectsExplicitChoice() {
    Recycle automatic = new Recycle("automatic");
    assertTrue(automatic.applyAutoAdaptiveAcceleration());
    assertTrue(automatic.isAdaptiveAcceleration());
    assertTrue(automatic.isAdaptiveAccelerationAutoManaged());
    assertTrue(automatic.resetAutoAdaptiveAcceleration());
    assertFalse(automatic.isAdaptiveAcceleration());
    assertFalse(automatic.isAdaptiveAccelerationAutoManaged());

    Recycle configured = new Recycle("configured");
    configured.setAdaptiveAcceleration(false);
    assertFalse(configured.applyAutoAdaptiveAcceleration());
    assertFalse(configured.isAdaptiveAcceleration());
  }

  /** The auto-tuner may set an absolute flow tolerance, but must never overwrite an explicit one. */
  @Test
  void testAutoAbsoluteFlowToleranceRespectsExplicitValue() {
    Recycle auto = new Recycle("auto");
    assertEquals(0.0, auto.getAbsoluteFlowTolerance());
    assertTrue(auto.applyAutoAbsoluteFlowTolerance(5.0));
    assertEquals(5.0, auto.getAbsoluteFlowTolerance());

    Recycle configured = new Recycle("configured");
    configured.setAbsoluteFlowTolerance(2.0);
    assertFalse(configured.applyAutoAbsoluteFlowTolerance(5.0));
    assertEquals(2.0, configured.getAbsoluteFlowTolerance());
  }

  /** The process must hand its flow noise floor to every recycle it manages. */
  @Test
  void testProcessSystemAppliesRecycleFlowTolerance() {
    ProcessSystem process = buildRecycleProcess();
    assertEquals(1, process.applyAutoRecycleFlowTolerance(3.0));
    assertEquals(3.0, ((Recycle) process.getUnit("recycle")).getAbsoluteFlowTolerance());
  }
}
