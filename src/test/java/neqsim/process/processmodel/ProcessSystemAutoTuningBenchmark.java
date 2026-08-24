package neqsim.process.processmodel;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.Setter;

/** Lightweight benchmark for the unit-subset scans performed by automatic convergence tuning. */
public final class ProcessSystemAutoTuningBenchmark {
  private static final int UNIT_COUNT = Integer.getInteger("units", 600);
  private static final int RECYCLE_COUNT = Integer.getInteger("recycles", 0);

  /** Cheap unit used to isolate orchestration overhead from thermodynamic work. */
  private static final class ProbeUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1L;

    ProbeUnit(String name) {
      super(name);
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }
  }

  private static ProcessSystem createProcess() {
    ProcessSystem process = new ProcessSystem("auto-tuning benchmark");
    int ordinaryUnits = Math.max(0, UNIT_COUNT - RECYCLE_COUNT);
    for (int unitIndex = 0; unitIndex < ordinaryUnits; unitIndex++) {
      process.add(new ProbeUnit("unit-" + unitIndex));
    }
    for (int recycleIndex = 0; recycleIndex < RECYCLE_COUNT; recycleIndex++) {
      process.add(new Recycle("recycle-" + recycleIndex));
    }
    return process;
  }

  private static long tuneCandidate(ProcessSystem process, double threshold) {
    long checksum = process.resetAutoLowFlowThreshold();
    checksum += process.resetAutoRecycleFlowTolerance();
    checksum += process.resetAutoRecycleAdaptiveAcceleration();
    checksum += process.applyAutoLowFlowThreshold(threshold);
    checksum += process.applyAutoRecycleFlowTolerance(threshold);
    checksum += process.applyAutoRecycleAdaptiveAcceleration();
    return checksum;
  }

  /** Reproduces the six pre-cache type-filter passes for an in-process A/B control. */
  private static long tuneBaseline(ProcessSystem process, double threshold) {
    long checksum = 0L;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof ProcessEquipmentBaseClass && ((ProcessEquipmentBaseClass) unit).resetAutoMinimumFlow()) {
        checksum++;
        if (!unit.isLockedInactive()) {
          unit.isActive(true);
        }
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle && ((Recycle) unit).resetAutoAbsoluteFlowTolerance()) {
        checksum++;
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle && ((Recycle) unit).resetAutoAdaptiveAcceleration()) {
        checksum++;
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof Setter) && unit instanceof ProcessEquipmentBaseClass
          && ((ProcessEquipmentBaseClass) unit).applyAutoMinimumFlow(threshold)) {
        checksum++;
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle && ((Recycle) unit).applyAutoAbsoluteFlowTolerance(threshold)) {
        checksum++;
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle && ((Recycle) unit).applyAutoAdaptiveAcceleration()) {
        checksum++;
      }
    }
    return checksum;
  }

  private static long tune(ProcessSystem process, double threshold, boolean baseline) {
    return baseline ? tuneBaseline(process, threshold) : tuneCandidate(process, threshold);
  }

  private static int parseIntOrDefault(String value, int defaultValue) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return defaultValue;
    }
  }

  public static void main(String[] args) {
    int warmups = args.length > 0 ? parseIntOrDefault(args[0], 1000) : 1000;
    int measured = args.length > 1 ? parseIntOrDefault(args[1], 10000) : 10000;
    String mode = args.length > 2 ? args[2] : "candidate";
    boolean baseline = "baseline".equals(mode);
    ProcessSystem process = createProcess();
    for (int iteration = 0; iteration < warmups; iteration++) {
      tune(process, 1.0 + (iteration & 1), baseline);
    }

    com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long allocatedBefore = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    long start = System.nanoTime();
    long checksum = 0L;
    for (int iteration = 0; iteration < measured; iteration++) {
      checksum += tune(process, 1.0 + (iteration & 1), baseline);
    }
    long elapsed = System.nanoTime() - start;
    long allocatedAfter = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    System.out.printf(Locale.US, "mode=%s units=%d recycles=%d nsPerTune=%.3f bytesPerTune=%.3f checksum=%d%n", mode,
        UNIT_COUNT, RECYCLE_COUNT, elapsed / (double) measured, (allocatedAfter - allocatedBefore) / (double) measured,
        checksum);
  }

  private ProcessSystemAutoTuningBenchmark() {
  }
}
