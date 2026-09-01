package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests automatic selection between level-based and dataflow process execution. */
class ProcessSystemDataflowDispatchTest {
  /** Process system that records the selected concrete dispatcher. */
  private static final class RecordingProcessSystem extends ProcessSystem {
    private static final long serialVersionUID = 1000L;
    private int parallelRuns;
    private int dataflowRuns;
    private int sequentialRuns;

    /** Creates an empty recording process. */
    RecordingProcessSystem() {
      super("dispatch recording process");
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void runParallel(UUID id) {
      parallelRuns++;
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void runDataflow(UUID id) {
      dataflowRuns++;
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void runSequential(UUID id) {
      sequentialRuns++;
      setCalculationIdentifier(id);
    }
  }

  /**
   * Creates a simple gas fluid for graph construction.
   *
   * @return configured gas fluid
   */
  private static SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Adds one serial stream/heater chain.
   *
   * @param process process to populate
   * @param prefix unique unit-name prefix
   * @param heaterCount number of heaters after the feed
   */
  private static void addSerialChain(ProcessSystem process, String prefix, int heaterCount) {
    StreamInterface current = new Stream(prefix + " feed", createFluid());
    process.add(current);
    for (int i = 0; i < heaterCount; i++) {
      Heater heater = new Heater(prefix + " heater " + i, current);
      process.add(heater);
      current = heater.getOutletStream();
    }
  }

  /** Verifies a large serial process avoids dataflow and invalidates that decision on branching. */
  @Test
  void serialProcessUsesParallelLevelsUntilTopologyBecomesWide() {
    RecordingProcessSystem process = new RecordingProcessSystem();
    addSerialChain(process, "serial", 8);

    process.runOptimized(UUID.randomUUID());

    assertEquals(1, process.parallelRuns, "serial topology should avoid CompletableFuture scheduling");
    assertEquals(0, process.dataflowRuns);

    addSerialChain(process, "branch", 1);
    process.runOptimized(UUID.randomUUID());

    assertEquals(1, process.parallelRuns);
    assertEquals(1, process.dataflowRuns, "topology mutation must enable dataflow for independent tasks");
  }

  /** Verifies a genuinely wide process retains dataflow dispatch. */
  @Test
  void independentChainsUseDataflow() {
    RecordingProcessSystem process = new RecordingProcessSystem();
    addSerialChain(process, "left", 4);
    addSerialChain(process, "right", 4);

    process.runOptimized(UUID.randomUUID());

    assertEquals(0, process.parallelRuns);
    assertEquals(1, process.dataflowRuns);
  }

  /** Verifies multi-input equipment retains level-based parallel dispatch. */
  @Test
  void multiInputEquipmentUsesParallelLevels() {
    RecordingProcessSystem process = new RecordingProcessSystem();
    Stream firstFeed = new Stream("first feed", createFluid());
    Stream secondFeed = new Stream("second feed", createFluid());
    StaticMixer mixer = new StaticMixer("mixer");
    mixer.addStream(firstFeed);
    mixer.addStream(secondFeed);
    process.add(firstFeed);
    process.add(secondFeed);
    process.add(mixer);

    process.runOptimized(UUID.randomUUID());

    assertEquals(1, process.parallelRuns);
    assertEquals(0, process.dataflowRuns);
    assertEquals(0, process.sequentialRuns, "multi-input topology should not disable parallel execution");
  }
}
