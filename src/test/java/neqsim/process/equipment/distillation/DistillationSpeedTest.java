package neqsim.process.equipment.distillation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.distillation.DistillationColumn.SolverType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkCPAstatoil;

@Tag("slow")
public class DistillationSpeedTest {
  @Test
  public void compareSolvers() {
    int warmupRuns = 2;
    int benchmarkRuns = 5;

    System.out.println("Warming up...");
    runBenchmark(SolverType.DIRECT_SUBSTITUTION, warmupRuns);
    runBenchmark(SolverType.INSIDE_OUT, warmupRuns);

    System.out.println("Benchmarking DIRECT_SUBSTITUTION...");
    double directTime = runBenchmark(SolverType.DIRECT_SUBSTITUTION, benchmarkRuns);

    System.out.println("Benchmarking INSIDE_OUT...");
    double insideOutTime = runBenchmark(SolverType.INSIDE_OUT, benchmarkRuns);

    System.out.println("\n--- Results ---");
    System.out.printf("DIRECT_SUBSTITUTION avg time: %.2f ms%n", directTime);
    System.out.printf("INSIDE_OUT avg time:          %.2f ms%n", insideOutTime);
    if (insideOutTime > 0) {
      System.out.printf("Speedup (IO vs Direct):       %.2fx%n", directTime / insideOutTime);
    }
  }

  private double runBenchmark(SolverType solver, int runs) {
    long totalTime = 0;

    for (int i = 0; i < runs; i++) {
      DistillationColumn column = setupColumn();
      column.setSolverType(solver);

      long start = System.nanoTime();
      column.run();
      long end = System.nanoTime();

      if (!column.solved()) {
        System.out.println("Warning: Solver " + solver + " did not converge in run " + i);
      }

      totalTime += (end - start);
      System.out.printf("Run %d: %.2f ms%n", i, (end - start) / 1e6);
    }

    return (double) totalTime / runs / 1e6;
  }

  private DistillationColumn setupColumn() {
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule(1);
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(273.15 + 50.0);
    feed.setPressure(10.0, "bara");
    feed.run();

    // Use 9 trays as found optimal in other tests
    DistillationColumn column = new DistillationColumn("DePropanizer", 9, true, true);

    // Use auto-feed assignment
    column.addFeedStream(feed);

    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.getCondenser().setRefluxRatio(2.0);
    column.getReboiler().setRefluxRatio(2.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);

    return column;
  }
}
