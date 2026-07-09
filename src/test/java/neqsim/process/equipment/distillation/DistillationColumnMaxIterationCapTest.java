package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that an explicitly user-set {@code maxNumberOfIterations} acts as a hard iteration cap for the sequential
 * (damped/direct substitution) column solver.
 *
 * <p>
 * Historically {@code setMaxNumberOfIterations} was only a lower bound: the solver applied an adaptive tray-based floor
 * ({@code trays * 5}) and an overflow expansion, so a non-converging column could run far more iterations than the
 * caller requested. When the caller opts in via {@code setMaxNumberOfIterations(int, true)} (or
 * {@code setHardIterationCap(true)}) it must be honored as a hard maximum.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnMaxIterationCapTest {

  /**
   * A tight temperature tolerance prevents convergence, so without a hard cap the solver would run to the
   * tray-based/overflow ceiling. With an explicit small cap the iteration count must not exceed it.
   */
  @Test
  public void explicitMaxIterationsIsHardCap() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 25.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("cap column", 8, true, false);
    column.addFeedStream(feed, 8);
    column.getReboiler().setOutTemperature(273.15 + 90.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    // Impossibly tight temperature tolerance so the column cannot converge.
    column.setTemperatureTolerance(1.0e-12);
    int cap = 6;
    column.setMaxNumberOfIterations(cap, true);
    column.run();

    assertTrue(column.getLastIterationCount() <= cap, "explicit maxNumberOfIterations must be a hard cap, but ran "
        + column.getLastIterationCount() + " iterations (cap=" + cap + ")");
  }
}
