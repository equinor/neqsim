package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Deterministic iteration gate and repeated-median diagnostics for native reboiler-only sum-rates. */
@Tag("slow")
public class ReboilerOnlySumRatesPerformanceTest {
  private static final Logger logger = LogManager.getLogger(ReboilerOnlySumRatesPerformanceTest.class);
  private static final int WARMUP_RUNS = 2;
  private static final int MEASURED_RUNS = 3;

  /** Fresh workload factory used by the repeated-median harness. */
  private interface Workload {
    void run();
  }

  /** Column and feeds used to assemble container workloads. */
  private static final class BenchmarkCase {
    private final Stream gasFeed;
    private final Stream solventFeed;
    private final DistillationColumn column;

    private BenchmarkCase(Stream gasFeed, Stream solventFeed, DistillationColumn column) {
      this.gasFeed = gasFeed;
      this.solventFeed = solventFeed;
      this.column = column;
    }

    private ProcessSystem createProcessSystem() {
      ProcessSystem process = new ProcessSystem();
      process.add(gasFeed);
      process.add(solventFeed);
      process.add(column);
      return process;
    }
  }

  private static SystemInterface createFluid(double temperature, double[] moles) {
    String[] components = { "methane", "ethane", "propane", "n-butane", "nC10" };
    SystemInterface fluid = new SystemSrkEos(temperature, 30.0);
    for (int componentIndex = 0; componentIndex < components.length; componentIndex++) {
      fluid.addComponent(components[componentIndex], moles[componentIndex]);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static Stream createFeed(String name, SystemInterface fluid, double flowRate) {
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(flowRate, "kg/hr");
    feed.setPressure(30.0, "bara");
    feed.run();
    return feed;
  }

  private static BenchmarkCase createCase(String name, double gasTemperature, double solventFlowRate,
      DistillationColumn.SolverType solverType) {
    Stream gasFeed = createFeed(name + " rich gas",
        createFluid(gasTemperature, new double[] { 0.70, 0.15, 0.10, 0.05, 1.0e-10 }), 1000.0);
    Stream solventFeed = createFeed(name + " lean solvent",
        createFluid(298.15, new double[] { 1.0e-10, 1.0e-10, 1.0e-10, 1.0e-10, 1.0 }), solventFlowRate);
    DistillationColumn column = new DistillationColumn(name, 10, true, false);
    column.addFeedStream(gasFeed, 1);
    column.addFeedStream(solventFeed, column.getNumberOfTrays() - 1);
    column.getReboiler().setOutTemperature(330.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(30.0);
    column.setMaxNumberOfIterations(400);
    column.setTemperatureTolerance(1.0e-4);
    column.setMassBalanceTolerance(1.0e-1);
    column.setEnthalpyBalanceTolerance(10.0);
    column.setSolverType(solverType);
    return new BenchmarkCase(gasFeed, solventFeed, column);
  }

  private static double medianSeconds(Workload workload) {
    for (int warmup = 0; warmup < WARMUP_RUNS; warmup++) {
      workload.run();
    }
    long[] elapsedNanos = new long[MEASURED_RUNS];
    for (int run = 0; run < MEASURED_RUNS; run++) {
      long start = System.nanoTime();
      workload.run();
      elapsedNanos[run] = System.nanoTime() - start;
    }
    Arrays.sort(elapsedNanos);
    return elapsedNanos[MEASURED_RUNS / 2] / 1.0e9;
  }

  private static double benchmarkColumn(final DistillationColumn.SolverType solverType) {
    return medianSeconds(new Workload() {
      @Override
      public void run() {
        createCase("column " + solverType, 313.15, 1200.0, solverType).column.run();
      }
    });
  }

  private static double benchmarkProcessSystem(final DistillationColumn.SolverType solverType) {
    return medianSeconds(new Workload() {
      @Override
      public void run() {
        createCase("process " + solverType, 313.15, 1200.0, solverType).createProcessSystem().run();
      }
    });
  }

  private static double benchmarkProcessModel(final DistillationColumn.SolverType solverType) {
    return medianSeconds(new Workload() {
      @Override
      public void run() {
        BenchmarkCase areaA = createCase("area A " + solverType, 313.15, 1200.0, solverType);
        BenchmarkCase areaB = createCase("area B " + solverType, 318.15, 1300.0, solverType);
        ProcessModel model = new ProcessModel();
        model.add("area A", areaA.createProcessSystem());
        model.add("area B", areaB.createProcessSystem());
        model.run();
      }
    });
  }

  private static void assertDeterministicIterationReduction() {
    BenchmarkCase damped = createCase("damped iteration baseline", 313.15, 1200.0,
        DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    BenchmarkCase sumRates = createCase("sum-rates iteration candidate", 313.15, 1200.0,
        DistillationColumn.SolverType.SUM_RATES);
    damped.column.run();
    sumRates.column.run();

    assertTrue(damped.column.solved(), "Damped baseline must converge");
    assertTrue(sumRates.column.solved(), "Native sum-rates candidate must converge");
    int dampedIterations = damped.column.getLastIterationCount();
    int sumRatesIterations = sumRates.column.getLastIterationCount();
    logger.info("Deterministic iteration count: damped={}, sum-rates={}", Integer.valueOf(dampedIterations),
        Integer.valueOf(sumRatesIterations));
    assertTrue(sumRatesIterations * 4 <= dampedIterations * 3,
        "SUM_RATES must use at least 25% fewer iterations; damped=" + dampedIterations + ", sum-rates="
            + sumRatesIterations);
  }

  private static void logMeasuredReduction(String workload, double referenceSeconds, double acceleratedSeconds) {
    double reduction = 1.0 - acceleratedSeconds / referenceSeconds;
    logger.info("{} repeated median: damped={} s, accelerated={} s, reduction={}%", workload,
        Double.valueOf(referenceSeconds), Double.valueOf(acceleratedSeconds), Double.valueOf(100.0 * reduction));
  }

  /** Verify deterministic work reduction and record cold end-to-end timing diagnostics. */
  @Test
  public void nativeSumRatesImprovesColdEndToEndWorkloads() {
    assertDeterministicIterationReduction();

    double dampedColumn = benchmarkColumn(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    double sumRatesColumn = benchmarkColumn(DistillationColumn.SolverType.SUM_RATES);
    logMeasuredReduction("explicit column", dampedColumn, sumRatesColumn);

    double autoColumn = benchmarkColumn(DistillationColumn.SolverType.AUTO);
    logMeasuredReduction("AUTO column", dampedColumn, autoColumn);

    double dampedProcess = benchmarkProcessSystem(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    double sumRatesProcess = benchmarkProcessSystem(DistillationColumn.SolverType.SUM_RATES);
    logMeasuredReduction("ProcessSystem", dampedProcess, sumRatesProcess);

    double dampedModel = benchmarkProcessModel(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    double autoModel = benchmarkProcessModel(DistillationColumn.SolverType.AUTO);
    logMeasuredReduction("ProcessModel", dampedModel, autoModel);
  }
}
