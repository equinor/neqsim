package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for how a low-flow bypass is reported by the convergence and mass-balance diagnostics.
 *
 * <p>
 * A section that has been deliberately switched off with a low-flow threshold must not make the flowsheet look broken:
 * the owning {@link ProcessSystem} must still report {@code solved()}, a {@link ProcessModel} must still report all
 * areas solved, and the bypassed units must not appear as mass-balance failures (their percentage balance degenerates
 * towards 100 % on a near-zero leg).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LowFlowBypassConvergenceTest {

  /**
   * Builds a small gas fluid used by all tests in this class.
   *
   * @return a three-component SRK gas system
   */
  private static SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * A unit whose inlet flow is below its low-flow threshold auto-bypasses, and the owning process must still report
   * solved instead of NOT SOLVED.
   */
  @Test
  public void bypassedUnitDoesNotBlockProcessSolved() {
    Stream feed = new Stream("feed", gas());
    feed.setFlowRate(0.5, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");

    Heater heater = new Heater("dead leg heater", feed);
    heater.setOutTemperature(40.0, "C");
    heater.setMinimumFlow(50.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    assertFalse(heater.isActive(), "heater inlet is below its low-flow threshold and must bypass");
    assertTrue(process.getBypassedUnits().contains("dead leg heater"));
    assertTrue(process.solved(), "a bypassed unit must not make the process report NOT SOLVED");
  }

  /**
   * A recycle whose loop flow collapses below its minimum flow is deactivated. It must report zero residuals and
   * {@code solved() == true}, otherwise the process keeps iterating on a dead leg and reports NOT SOLVED forever.
   */
  @Test
  public void recycleDeactivatedOnLowFlowReportsSolved() {
    Stream tearFeed = new Stream("tear feed", gas());
    tearFeed.setFlowRate(1.0e-6, "kg/hr");
    tearFeed.setTemperature(25.0, "C");
    tearFeed.setPressure(10.0, "bara");

    Stream tear = new Stream("tear", gas().clone());
    tear.setFlowRate(1.0e-6, "kg/hr");
    tear.setTemperature(25.0, "C");
    tear.setPressure(10.0, "bara");

    Recycle recycle = new Recycle("dead recycle");
    recycle.addStream(tearFeed);
    recycle.setOutletStream(tear);
    recycle.setMinimumFlow(1.0e-3);

    ProcessSystem process = new ProcessSystem();
    process.add(tearFeed);
    process.add(tear);
    process.add(recycle);
    process.run();

    assertFalse(recycle.isActive(), "recycle flow is below minimumFlow and must deactivate");
    assertTrue(recycle.solved(), "a deactivated recycle has nothing left to converge");
    assertTrue(process.solved(), "a deactivated recycle must not make the process report NOT SOLVED");
  }

  /**
   * The multi-area convergence report must mark a bypassed area as solved and name the bypassed units instead of
   * listing them as unsolved.
   */
  @Test
  public void convergenceReportSeparatesBypassedFromUnsolved() {
    Stream feed = new Stream("feed", gas());
    feed.setFlowRate(0.5, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");

    Heater heater = new Heater("dead leg heater", feed);
    heater.setOutTemperature(40.0, "C");
    heater.setMinimumFlow(50.0);

    ProcessSystem area = new ProcessSystem();
    area.add(feed);
    area.add(heater);

    ProcessModel model = new ProcessModel();
    model.add("stagnant area", area);
    model.runUntilConverged(5, 1.0e-3);

    String summary = model.getConvergenceSummary();
    assertTrue(summary.contains("All process areas solved: YES"),
        "bypassed units must not flip the all-areas-solved flag:\n" + summary);
    assertTrue(summary.contains("bypassed on low flow"), "summary must explain the bypass:\n" + summary);
    assertFalse(summary.contains("Unsolved units:"), "a bypassed unit is not an unsolved unit:\n" + summary);

    String json = model.getConvergenceReportJson();
    assertTrue(json.contains("bypassedUnits"), "JSON report must expose the bypassed units: " + json);
    assertTrue(json.contains("dead leg heater"), "JSON report must name the bypassed unit: " + json);
  }

  /**
   * A bypassed unit never executed, so it must not be reported as a mass-balance failure even though its percentage
   * balance on a near-zero leg degenerates towards 100 %.
   */
  @Test
  public void bypassedUnitIsNotAMassBalanceFailure() {
    Stream feed = new Stream("feed", gas());
    feed.setFlowRate(0.5, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");

    Heater heater = new Heater("dead leg heater", feed);
    heater.setOutTemperature(40.0, "C");
    heater.setMinimumFlow(50.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    Map<String, ProcessSystem.MassBalanceResult> all = process.checkMassBalance("kg/hr");
    assertTrue(all.get("dead leg heater").isBypassed(), "the result must be tagged as bypassed");

    Map<String, ProcessSystem.MassBalanceResult> failed = process.getFailedMassBalance("kg/hr", 0.1);
    assertFalse(failed.containsKey("dead leg heater"),
        "a bypassed unit must not be reported as a mass-balance failure");
  }

  /**
   * A manifold must report its mass balance across its own boundary (split outlets minus mixer inlets), with the
   * correct sign.
   *
   * <p>
   * The balance used to be derived from the internal mixer's residual, which double-counted that residual and flipped
   * the sign whenever the internal mixer was not itself perfectly balanced - exactly the mid-iteration state a
   * mass-balance check exists to detect.
   * </p>
   */
  @Test
  public void manifoldMassBalanceIsTakenAcrossItsOwnBoundary() {
    Stream feedA = new Stream("feed A", gas());
    feedA.setFlowRate(1000.0, "kg/hr");
    feedA.setTemperature(25.0, "C");
    feedA.setPressure(10.0, "bara");
    feedA.run();

    Stream feedB = new Stream("feed B", gas().clone());
    feedB.setFlowRate(500.0, "kg/hr");
    feedB.setTemperature(25.0, "C");
    feedB.setPressure(10.0, "bara");
    feedB.run();

    Manifold manifold = new Manifold("test manifold");
    manifold.addStream(feedA);
    manifold.addStream(feedB);
    manifold.setSplitFactors(new double[] { 0.6, 0.4 });
    manifold.run();

    assertEquals(0.0, manifold.getMassBalance("kg/hr"), 1.0e-6,
        "a converged manifold must balance exactly across its own boundary");

    // Perturb one inlet WITHOUT re-running the manifold. This reproduces the
    // mid-iteration state in which an upstream area has already refreshed a feed: the
    // manifold now passes 100 kg/hr more than it receives, so the balance must be
    // +100 kg/hr, not -100 kg/hr.
    feedB.setFlowRate(400.0, "kg/hr");
    feedB.run();
    assertEquals(100.0, manifold.getMassBalance("kg/hr"), 1.0e-3,
        "manifold balance must be outlets - inlets, with the correct sign");
  }

  /**
   * A recycle must offer a scale-independent (absolute, kg/hr) flow criterion alongside the legacy residual.
   *
   * <p>
   * {@link Recycle#flowBalanceCheck()} returns an absolute kg/sec residual below 1 kg/sec and a percentage above it, so
   * a single {@code setTolerance()} value means different things on different loops. The absolute criterion is opt-in
   * (default 0.0 keeps the legacy behaviour) and satisfies the flow gate when the loop flow moves by less than the
   * configured kg/hr.
   * </p>
   */
  @Test
  public void recycleAcceptsAnAbsoluteFlowTolerance() {
    Recycle recycle = new Recycle("tolerance probe");
    assertEquals(0.0, recycle.getAbsoluteFlowTolerance(), 1.0e-12,
        "the absolute criterion must be disabled by default");

    recycle.setAbsoluteFlowTolerance(2.5);
    assertEquals(2.5, recycle.getAbsoluteFlowTolerance(), 1.0e-12);

    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        recycle.setAbsoluteFlowTolerance(-1.0);
      }
    });
  }

  /**
   * The absolute flow change must be reported in kg/hr and must let a large, slowly-moving loop converge on the
   * absolute criterion even when the legacy percentage residual is still above its tolerance.
   */
  @Test
  public void recycleConvergesOnTheAbsoluteFlowCriterion() {
    Stream loopFeed = new Stream("loop feed", gas());
    loopFeed.setFlowRate(50000.0, "kg/hr");
    loopFeed.setTemperature(25.0, "C");
    loopFeed.setPressure(10.0, "bara");
    loopFeed.run();

    Stream tear = new Stream("tear", gas().clone());
    tear.setFlowRate(50000.0, "kg/hr");
    tear.setTemperature(25.0, "C");
    tear.setPressure(10.0, "bara");
    tear.run();

    Recycle recycle = new Recycle("big loop");
    recycle.addStream(loopFeed);
    recycle.setOutletStream(tear);
    // Percentage residual gate so tight it cannot be met by the step below.
    recycle.setTolerance(1.0e-8);
    recycle.run();

    // Move the loop by 1 kg/hr on a 50 t/hr stream: 0.002 % - real convergence, but
    // above the 1e-8 percentage gate.
    loopFeed.setFlowRate(50001.0, "kg/hr");
    loopFeed.run();
    recycle.run();

    assertEquals(1.0, recycle.getAbsoluteFlowChange(), 1.0e-6, "the absolute change must be reported in kg/hr");
    assertFalse(recycle.solved(), "with the absolute criterion disabled the tight percentage gate must still fail");

    recycle.setAbsoluteFlowTolerance(5.0);
    assertTrue(recycle.solved(), "a 1 kg/hr move on a 50 t/hr loop must satisfy a 5 kg/hr absolute tolerance");
  }
}
