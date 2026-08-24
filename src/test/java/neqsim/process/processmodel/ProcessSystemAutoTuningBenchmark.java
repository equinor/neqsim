package neqsim.process.processmodel;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.util.Recycle;

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

  private static long tune(ProcessSystem process, double threshold) {
    long checksum = process.resetAutoLowFlowThreshold();
    checksum += process.resetAutoRecycleFlowTolerance();
    checksum += process.resetAutoRecycleAdaptiveAcceleration();
    checksum += process.applyAutoLowFlowThreshold(threshold);
    checksum += process.applyAutoRecycleFlowTolerance(threshold);
    checksum += process.applyAutoRecycleAdaptiveAcceleration();
    return checksum;
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
    ProcessSystem process = createProcess();
    for (int iteration = 0; iteration < warmups; iteration++) {
      tune(process, 1.0 + (iteration & 1));
    }

    com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long allocatedBefore = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    long start = System.nanoTime();
    long checksum = 0L;
    for (int iteration = 0; iteration < measured; iteration++) {
      checksum += tune(process, 1.0 + (iteration & 1));
    }
    long elapsed = System.nanoTime() - start;
    long allocatedAfter = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    System.out.printf(Locale.US,
        "units=%d recycles=%d nsPerTune=%.3f bytesPerTune=%.3f checksum=%d%n", UNIT_COUNT, RECYCLE_COUNT,
        elapsed / (double) measured, (allocatedAfter - allocatedBefore) / (double) measured, checksum);
  }

  private ProcessSystemAutoTuningBenchmark() {}
}
