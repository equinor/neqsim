package neqsim.process.equipment.distillation;

import java.io.FileWriter;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Speed benchmark comparing all solver types on deethanizer columns with 5 and 10 trays. Results
 * are written to target/solver_benchmark.txt for analysis.
 */
public class SolverSpeedBenchmark {

  private SystemInterface createDeethanizerFeed() {
    SystemInterface gas = new SystemSrkEos(216, 30.00);
    gas.addComponent("nitrogen", 1.67366E-3);
    gas.addComponent("CO2", 1.06819E-4);
    gas.addComponent("methane", 5.14168E-1);
    gas.addComponent("ethane", 1.92528E-1);
    gas.addComponent("propane", 1.70001E-1);
    gas.addComponent("i-butane", 3.14561E-2);
    gas.addComponent("n-butane", 5.58678E-2);
    gas.addComponent("i-pentane", 1.29573E-2);
    gas.addComponent("n-pentane", 1.23719E-2);
    gas.addComponent("n-hexane", 5.12878E-3);
    gas.addComponent("n-heptane", 1.0E-2);
    gas.setMixingRule("classic");
    return gas;
  }

  @Test
  public void benchmarkAllSolvers() throws Exception {
    int[] trayCounts = {5, 10};

    // All solver configurations to benchmark
    String[] labels = {"DIRECT", "DAMPED", "IO", "IO(inner=3)", "WEGSTEIN", "SUM_RATES", "NEWTON"};
    DistillationColumn.SolverType[] solverTypes = {
        DistillationColumn.SolverType.DIRECT_SUBSTITUTION,
        DistillationColumn.SolverType.DAMPED_SUBSTITUTION, DistillationColumn.SolverType.INSIDE_OUT,
        DistillationColumn.SolverType.INSIDE_OUT, DistillationColumn.SolverType.WEGSTEIN,
        DistillationColumn.SolverType.SUM_RATES, DistillationColumn.SolverType.NEWTON};
    int[] innerLoopSteps = {0, 0, 0, 3, 0, 0, 0};

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%-20s %6s %10s %12s %12s %12s  %s%n", "Solver", "Trays", "Iters",
        "Time(s)", "GasFlow", "LiqFlow", "Converged"));
    sb.append(
        "--------------------------------------------------------------------------------------------\n");

    for (int nTrays : trayCounts) {
      for (int s = 0; s < labels.length; s++) {
        Stream feed =
            new Stream("feed_" + labels[s] + "_" + nTrays, createDeethanizerFeed().clone());
        feed.setFlowRate(100.0, "kg/hr");
        feed.run();

        DistillationColumn column =
            new DistillationColumn("bench_" + labels[s] + "_" + nTrays, nTrays, true, false);
        column.addFeedStream(feed, nTrays);
        column.getReboiler().setOutTemperature(105.0 + 273.15);
        column.setTopPressure(30.0);
        column.setBottomPressure(32.0);
        column.setMaxNumberOfIterations(nTrays <= 5 ? 50 : 80);
        column.setSolverType(solverTypes[s]);
        if (innerLoopSteps[s] > 0) {
          column.setInnerLoopSteps(innerLoopSteps[s]);
        }
        column.run();

        double gasFlow = column.getGasOutStream().getFlowRate("kg/hr");
        double liqFlow = column.getLiquidOutStream().getFlowRate("kg/hr");

        sb.append(String.format("%-20s %6d %10d %12.3f %12.2f %12.2f  %s%n", labels[s], nTrays,
            column.getLastIterationCount(), column.getLastSolveTimeSeconds(), gasFlow, liqFlow,
            column.solved() ? "YES" : "NO"));
      }
      sb.append("\n");
    }

    String report = sb.toString();
    System.out.println(report);

    try (PrintWriter pw = new PrintWriter(new FileWriter("target/solver_benchmark.txt"))) {
      pw.print(report);
    }
  }
}
