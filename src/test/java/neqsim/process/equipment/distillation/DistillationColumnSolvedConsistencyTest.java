package neqsim.process.equipment.distillation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Regression tests guarding the consistency of {@link DistillationColumn#solved()} with the solve status and the
 * residuals reported by the public getters.
 *
 * <p>
 * A column that reports {@code RIGOROUS_CONVERGED} and residuals inside their tolerances must also report
 * {@code solved() == true}. When that contract was broken, enclosing recycle and process loops kept iterating a
 * converged column until they hit an iteration cap or a wall-clock timeout and then returned a partially converged
 * state.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class DistillationColumnSolvedConsistencyTest extends neqsim.NeqSimTest {
  /**
   * Build a small TEG regeneration column with a reboiler and a condenser.
   *
   * @return a runnable process system whose single column unit is named "TEG regeneration column"
   */
  private ProcessSystem createTegRegenerationProcess() {
    SystemInterface richTeg = new SystemSrkCPAstatoil(273.15 + 100.0, 1.05);
    richTeg.addComponent("methane", 0.001);
    richTeg.addComponent("water", 0.05);
    richTeg.addComponent("TEG", 0.949);
    richTeg.setMixingRule(10);
    richTeg.setMultiPhaseCheck(false);

    Stream richTegFeed = new Stream("rich TEG feed", richTeg);
    richTegFeed.setFlowRate(5000.0, "kg/hr");
    richTegFeed.setTemperature(100.0, "C");
    richTegFeed.setPressure(1.05, "bara");

    DistillationColumn column = new DistillationColumn("TEG regeneration column", 1, true, true);
    column.addFeedStream(richTegFeed, 1);
    column.getReboiler().setOutTemperature(273.15 + 202.0);
    column.getCondenser().setOutTemperature(273.15 + 95.0);
    column.setTopPressure(1.05);
    column.setBottomPressure(1.05);

    ProcessSystem process = new ProcessSystem();
    process.add(richTegFeed);
    process.add(column);
    return process;
  }

  /**
   * A column that reports a converged solve status with residuals inside tolerance must also report
   * {@code solved() == true}.
   */
  @Test
  public void solvedAgreesWithReportedStatusAndResiduals() {
    ProcessSystem process = createTegRegenerationProcess();
    process.run();

    DistillationColumn column = (DistillationColumn) process.getUnit("TEG regeneration column");

    boolean convergedStatus = column.getLastSolveStatus() == DistillationColumn.SolveStatus.RIGOROUS_CONVERGED
        || column.getLastSolveStatus() == DistillationColumn.SolveStatus.RECONCILED_PRODUCTS;
    Assertions.assertTrue(convergedStatus,
        "Column did not converge, cannot check the solved()/status contract. Status was " + column.getLastSolveStatus()
            + ": " + column.getLastSolveStatusReason());

    Assertions.assertTrue(column.getLastTemperatureResidual() < column.getTemperatureTolerance(),
        "Reported temperature residual should be inside tolerance for a converged column");
    Assertions.assertTrue(column.getLastMassResidual() <= column.getMassBalanceTolerance(),
        "Reported mass residual should be inside tolerance for a converged column");

    Assertions.assertTrue(column.solved(),
        "solved() must agree with the reported solve status and residuals. Diagnostics:\n"
            + column.getConvergenceDiagnostics());
  }

  /**
   * Running a converged column a second time must not flip it back to an unsolved state. Recycle loops re-run the
   * column on every outer iteration and rely on this.
   */
  @Test
  public void solvedIsStableAcrossRepeatedRuns() {
    ProcessSystem process = createTegRegenerationProcess();
    process.run();

    DistillationColumn column = (DistillationColumn) process.getUnit("TEG regeneration column");

    boolean convergedStatus = column.getLastSolveStatus() == DistillationColumn.SolveStatus.RIGOROUS_CONVERGED
        || column.getLastSolveStatus() == DistillationColumn.SolveStatus.RECONCILED_PRODUCTS;
    Assertions.assertTrue(convergedStatus,
        "Column did not converge on the first run, cannot check solved() stability. Status was "
            + column.getLastSolveStatus() + ": " + column.getLastSolveStatusReason());

    boolean firstSolved = column.solved();
    Assertions.assertTrue(firstSolved,
        "Column should be solved() on the first run when status/residuals indicate convergence. Diagnostics:\n"
            + column.getConvergenceDiagnostics());

    process.run();

    convergedStatus = column.getLastSolveStatus() == DistillationColumn.SolveStatus.RIGOROUS_CONVERGED
        || column.getLastSolveStatus() == DistillationColumn.SolveStatus.RECONCILED_PRODUCTS;
    Assertions.assertTrue(convergedStatus,
        "Column did not converge on the second run, cannot check solved() stability. Status was "
            + column.getLastSolveStatus() + ": " + column.getLastSolveStatusReason());

    Assertions.assertEquals(firstSolved, column.solved(),
        "solved() flipped on a warm re-solve of an unchanged column. Diagnostics:\n"
            + column.getConvergenceDiagnostics());
  }

  /**
   * The reboiler and condenser duties of a converged column must be finite numbers.
   */
  @Test
  public void columnEndDutiesAreFinite() {
    ProcessSystem process = createTegRegenerationProcess();
    process.run();

    DistillationColumn column = (DistillationColumn) process.getUnit("TEG regeneration column");
    Assertions.assertTrue(Double.isFinite(column.getReboiler().getDuty()),
        "Reboiler duty must be finite, was " + column.getReboiler().getDuty());
    Assertions.assertTrue(Double.isFinite(column.getCondenser().getDuty()),
        "Condenser duty must be finite, was " + column.getCondenser().getDuty());
  }
}
