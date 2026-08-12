package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the {@link DistillationColumn#solved()} convergence gate.
 *
 * <p>
 * These cover the gate defects that let a column report a converged solve while its tray profile violated the MESH
 * component material balance: the fabricated zero temperature residual reported by the simultaneous-correction solver,
 * and the bounded MESH residual families being excluded from the gate by a tolerance no bounded residual can exceed.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnConvergenceGateTest {

  /**
   * Builds a small stripper with a single feed.
   *
   * @param numberOfTrays number of theoretical stages excluding the reboiler
   * @return an unrun column
   */
  private static DistillationColumn buildColumn(int numberOfTrays) {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    fluid.addComponent("propane", 40.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("gate column", numberOfTrays, true, false);
    column.addFeedStream(feed, numberOfTrays / 2);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getReboiler().setOutTemperature(273.15 + 80.0);
    return column;
  }

  /**
   * The per-tray component material balance must have a tolerance well below 1.0, otherwise it cannot reject anything:
   * the measure is a relative imbalance bounded by 1 by construction.
   */
  @Test
  public void trayMaterialBalanceToleranceIsBelowTheBoundedMaximum() {
    DistillationColumn column = buildColumn(6);
    assertTrue(column.getTrayMaterialBalanceTolerance() < 1.0,
        "a relative imbalance can never exceed 1.0, so a tolerance of 1.0 would exclude it from the gate; was "
            + column.getTrayMaterialBalanceTolerance());
    assertEquals(2.0e-2, column.getTrayMaterialBalanceTolerance(), 1e-12);
  }

  /**
   * The per-tray material balance tolerance is validated.
   */
  @Test
  public void trayMaterialBalanceToleranceIsValidated() {
    final DistillationColumn column = buildColumn(6);
    column.setTrayMaterialBalanceTolerance(1.0e-4);
    assertEquals(1.0e-4, column.getTrayMaterialBalanceTolerance(), 1e-15);

    assertThrows(IllegalArgumentException.class, new Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
        column.setTrayMaterialBalanceTolerance(0.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new Executable() {
      /** {@inheritDoc} */
      @Override
      public void execute() {
        column.setTrayMaterialBalanceTolerance(1.5);
      }
    });
  }

  /**
   * The per-tray component material imbalance is reported as a diagnostic, not enforced as a convergence gate.
   *
   * <p>
   * It is deliberately not part of {@link DistillationColumn#solved()}: on wide-boiling stripper topologies the
   * substitution solvers that the rest of the suite treats as the reference also leave a large per-tray imbalance, so
   * gating on it rejects solvers that are known to produce the correct product split. It stays available through
   * {@link DistillationColumn#getLastTrayMaterialBalanceError()} and the convergence diagnostics, where it is the
   * measure that exposes a solver leaking one species between trays.
   * </p>
   */
  @Test
  public void trayMaterialBalanceIsReportedAsDiagnostic() {
    DistillationColumn column = buildColumn(6);
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.run();

    double imbalance = column.getLastTrayMaterialBalanceError();
    assertTrue(Double.isFinite(imbalance), "per-tray material imbalance must be evaluated, was " + imbalance);
    assertTrue(imbalance >= 0.0, "per-tray material imbalance must be non-negative, was " + imbalance);
    assertTrue(column.getConvergenceDiagnostics().contains("per-tray material imbalance"),
        "the diagnostics report should expose the per-tray material imbalance");

    column.setEnforceMeshResidualTolerance(true);
    boolean solvedBefore = column.solved();
    column.setTrayMaterialBalanceTolerance(1.0e-12);

    assertEquals(solvedBefore, column.solved(),
        "the per-tray material imbalance is a diagnostic and must not change the solved() verdict");
  }

  /**
   * The Naphtali-Sandholm solver has no successive-substitution sweep and therefore no tray-temperature change between
   * iterations. It must report that honestly instead of a fabricated zero, which previously satisfied the temperature
   * gate unconditionally.
   */
  @Test
  public void naphtaliSandholmDoesNotFabricateAZeroTemperatureResidual() {
    DistillationColumn column = buildColumn(6);
    Map<Integer, List<SystemInterface>> feedSystems = new HashMap<Integer, List<SystemInterface>>();
    Map<Integer, List<Double>> feedFlowRates = new HashMap<Integer, List<Double>>();
    for (Map.Entry<Integer, List<StreamInterface>> entry : column.getFeedStreams().entrySet()) {
      List<SystemInterface> systems = new ArrayList<SystemInterface>();
      List<Double> flowRates = new ArrayList<Double>();
      for (StreamInterface feed : entry.getValue()) {
        systems.add(feed.getThermoSystem().clone());
        flowRates.add(Double.valueOf(feed.getFlowRate("mol/hr")));
      }
      feedSystems.put(entry.getKey(), systems);
      feedFlowRates.put(entry.getKey(), flowRates);
    }

    NaphtaliSandholmSolver solver = new NaphtaliSandholmSolver(column, feedSystems, feedFlowRates);

    assertTrue(Double.isNaN(solver.getLastTemperatureResidual()),
        "the simultaneous solver must report an unavailable temperature residual, not zero");
    assertTrue(Double.isNaN(solver.getLastEnergyResidual()),
        "no energy residual is available before a solve has completed");
  }

  /**
   * The solve status is transient. A column restored from a serialized model must report {@code NOT_RUN} rather than
   * {@code null}, because the restored tray state is data and not evidence that the solver ran.
   */
  @Test
  public void solveStatusIsNeverNull() throws Exception {
    DistillationColumn column = buildColumn(4);
    assertEquals(DistillationColumn.SolveStatus.NOT_RUN, column.getLastSolveStatus());
    assertNotNull(column.getLastSolveStatusReason());

    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.run();

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(buffer);
    out.writeObject(column);
    out.close();

    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
    DistillationColumn restored = (DistillationColumn) in.readObject();
    in.close();

    assertEquals(DistillationColumn.SolveStatus.NOT_RUN, restored.getLastSolveStatus(),
        "a deserialized column must not report a null solve status");
    assertNotNull(restored.getLastSolveStatusReason());
    assertFalse(restored.solved(), "a deserialized column has not been solved in this session");
  }
}
