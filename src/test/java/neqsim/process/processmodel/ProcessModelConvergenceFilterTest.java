package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Tests for the boundary-stream convergence filters on {@link ProcessModel}: the configurable boundary flow floor and
 * the absolute flow tolerance.
 *
 * <p>
 * The scenario mirrors a real full-plant symptom: a large export train with a genuine 443 kg/hr residual (0.32 % of
 * 137709 kg/hr) is masked by a stagnant HT injection leg carrying 0.1 kg/hr whose 0.0066 kg/hr wobble is a 6.56 %
 * relative error and therefore dominates the plant-wide maximum.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class ProcessModelConvergenceFilterTest extends neqsim.NeqSimTest {

  /** ProcessSystem that counts topology-version reads. */
  private static final class CountingStructureProcessSystem extends ProcessSystem {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Number of topology-version reads. */
    private final AtomicInteger structureVersionReads = new AtomicInteger();

    /** Creates a counting process area. */
    CountingStructureProcessSystem(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public long getStructureVersion() {
      structureVersionReads.incrementAndGet();
      return super.getStructureVersion();
    }

    /** Resets the topology-version read count. */
    void resetStructureVersionReads() {
      structureVersionReads.set(0);
    }

    /** Returns the topology-version read count. */
    int getStructureVersionReads() {
      return structureVersionReads.get();
    }
  }

  /** Flow of the real export stream on the previous outer iteration, kg/hr. */
  private static final double BIG_PREVIOUS_FLOW = 137709.1;
  /** Flow of the real export stream on the current outer iteration, kg/hr. */
  private static final double BIG_CURRENT_FLOW = 137266.3;
  /** Flow of the stagnant dead leg on the previous outer iteration, kg/hr. */
  private static final double TINY_PREVIOUS_FLOW = 0.1;
  /** Flow of the stagnant dead leg on the current outer iteration, kg/hr. */
  private static final double TINY_CURRENT_FLOW = 0.1 * (1.0 - 0.0656);

  /** Boundary stream key representing the real export residual. */
  private final StreamInterface bigStream = new Stream("NGL mixer mixed stream");
  /** Boundary stream key representing the stagnant HT injection dead leg. */
  private final StreamInterface tinyStream = new Stream("gas injection ht");

  /**
   * Builds the "previous outer iteration" boundary-stream state map.
   *
   * @return map of stream key to {flow, temperature, pressure}
   */
  private Map<StreamInterface, double[]> previousStates() {
    Map<StreamInterface, double[]> previous = new LinkedHashMap<StreamInterface, double[]>();
    previous.put(bigStream, new double[] { BIG_PREVIOUS_FLOW, 300.0, 60.0 });
    previous.put(tinyStream, new double[] { TINY_PREVIOUS_FLOW, 300.0, 60.0 });
    return previous;
  }

  /**
   * Builds the "current outer iteration" boundary-stream state map.
   *
   * @return map of stream key to {flow, temperature, pressure}
   */
  private Map<StreamInterface, double[]> currentStates() {
    Map<StreamInterface, double[]> current = new LinkedHashMap<StreamInterface, double[]>();
    current.put(bigStream, new double[] { BIG_CURRENT_FLOW, 300.0, 60.0 });
    current.put(tinyStream, new double[] { TINY_CURRENT_FLOW, 300.0, 60.0 });
    return current;
  }

  @Test
  public void defaultsPreserveLegacyRelativeOnlyBehaviour() {
    ProcessModel model = new ProcessModel();
    assertEquals(ProcessModel.DEFAULT_BOUNDARY_FLOW_FLOOR, model.getBoundaryFlowFloor(), 0.0);
    assertEquals(0.0, model.getAbsoluteFlowTolerance(), 0.0);

    double[] errors = model.calculateConvergenceErrors(previousStates(), currentStates());

    // The stagnant 0.1 kg/hr leg (6.56 %) dominates the real 0.32 % residual.
    assertEquals(0.0656, errors[0], 1e-6);
    assertEquals(2, model.getLastBoundaryStreamErrors().size());
  }

  @Test
  public void convergenceDiagnosticsResolveAreaPlanOncePerBatch() {
    ProcessModel model = new ProcessModel();
    CountingStructureProcessSystem first = new CountingStructureProcessSystem("first");
    CountingStructureProcessSystem second = new CountingStructureProcessSystem("second");
    model.add("first", first);
    model.add("second", second);
    first.resetStructureVersionReads();
    second.resetStructureVersionReads();

    Map<StreamInterface, double[]> previous = new LinkedHashMap<StreamInterface, double[]>();
    Map<StreamInterface, double[]> current = new LinkedHashMap<StreamInterface, double[]>();
    for (int streamIndex = 0; streamIndex < 3; streamIndex++) {
      StreamInterface stream = new Stream("boundary " + streamIndex);
      previous.put(stream, new double[] { 1000.0 + streamIndex, 300.0, 60.0 });
      current.put(stream, new double[] { 1000.1 + streamIndex, 300.1, 60.1 });
    }

    model.calculateConvergenceErrors(previous, current);

    assertEquals(1, first.getStructureVersionReads());
    assertEquals(1, second.getStructureVersionReads());
    assertEquals(3, model.getLastBoundaryStreamErrors().size());
  }

  @Test
  public void boundaryFlowFloorExcludesStagnantLegEntirely() {
    ProcessModel model = new ProcessModel();
    model.setBoundaryFlowFloor(1.0);

    double[] errors = model.calculateConvergenceErrors(previousStates(), currentStates());

    // Only the real export residual remains: 442.8 / 137709.1 = 0.003215.
    assertEquals(0.003215, errors[0], 1e-5);

    List<ProcessModel.BoundaryStreamError> recorded = model.getLastBoundaryStreamErrors();
    assertEquals(1, recorded.size());
    assertEquals("NGL mixer mixed stream", recorded.get(0).getStreamName());
  }

  @Test
  public void repeatedDiagnosticsFollowCurrentFlowFloor() {
    ProcessModel model = new ProcessModel();

    model.calculateConvergenceErrors(previousStates(), currentStates());
    assertEquals(2, model.getLastBoundaryStreamErrors().size());

    model.setBoundaryFlowFloor(1.0);
    model.calculateConvergenceErrors(previousStates(), currentStates());
    assertEquals(1, model.getLastBoundaryStreamErrors().size());
    assertEquals("NGL mixer mixed stream", model.getLastBoundaryStreamErrors().get(0).getStreamName());

    model.setBoundaryFlowFloor(ProcessModel.DEFAULT_BOUNDARY_FLOW_FLOOR);
    model.calculateConvergenceErrors(previousStates(), currentStates());
    assertEquals(2, model.getLastBoundaryStreamErrors().size());
  }

  @Test
  public void absoluteFlowToleranceIgnoresNegligibleAbsoluteChange() {
    ProcessModel model = new ProcessModel();
    model.setAbsoluteFlowTolerance(1.0);

    double[] errors = model.calculateConvergenceErrors(previousStates(), currentStates());

    // 0.0066 kg/hr change is below the 1 kg/hr absolute tolerance, so it no longer
    // contributes to the reported maximum; 442.8 kg/hr still does.
    assertEquals(0.003215, errors[0], 1e-5);

    // The stagnant stream is still recorded with its true relative error so the
    // per-stream diagnostics stay honest.
    List<ProcessModel.BoundaryStreamError> recorded = model.getLastBoundaryStreamErrors();
    assertEquals(2, recorded.size());
    ProcessModel.BoundaryStreamError tiny = findByName(recorded, "gas injection ht");
    assertEquals(0.0656, tiny.getFlowError(), 1e-6);
    assertEquals(0.00656, tiny.getAbsoluteFlowChange(), 1e-6);
  }

  /**
   * Looks up a recorded boundary-stream error by stream name.
   *
   * @param errors recorded boundary-stream errors
   * @param name stream name to look for
   * @return the matching record
   * @throws AssertionError if no record with that name exists
   */
  private static ProcessModel.BoundaryStreamError findByName(List<ProcessModel.BoundaryStreamError> errors,
      String name) {
    for (ProcessModel.BoundaryStreamError error : errors) {
      if (name.equals(error.getStreamName())) {
        return error;
      }
    }
    throw new AssertionError("No boundary stream error recorded for '" + name + "'");
  }

  @Test
  public void offenderListHonoursAbsoluteFlowTolerance() {
    ProcessModel model = new ProcessModel();
    model.calculateConvergenceErrors(previousStates(), currentStates());
    assertEquals(2, model.getNonConvergedBoundaryStreamErrors().size());

    model.setAbsoluteFlowTolerance(1.0);
    List<ProcessModel.BoundaryStreamError> offenders = model.getNonConvergedBoundaryStreamErrors();
    assertEquals(1, offenders.size());
    assertEquals("NGL mixer mixed stream", offenders.get(0).getStreamName());
  }

  @Test
  public void convergenceSummaryReportsActiveFlowFilters() {
    ProcessModel model = new ProcessModel();
    model.setAbsoluteFlowTolerance(1.0);
    model.setBoundaryFlowFloor(0.5);
    String summary = model.getConvergenceSummary();
    assertTrue(summary.contains("Flow filters"));

    ProcessModel defaults = new ProcessModel();
    assertFalse(defaults.getConvergenceSummary().contains("Flow filters"));
  }

  @Test
  public void runUntilConvergedOverloadAppliesAbsoluteTolerance() {
    ProcessModel model = new ProcessModel();
    model.runUntilConverged(1, 1e-3, 2.5);
    assertEquals(2.5, model.getAbsoluteFlowTolerance(), 0.0);
  }

  @Test
  public void invalidFilterValuesAreRejected() {
    final ProcessModel model = new ProcessModel();
    assertThrows(IllegalArgumentException.class, () -> model.setBoundaryFlowFloor(-1.0));
    assertThrows(IllegalArgumentException.class, () -> model.setBoundaryFlowFloor(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> model.setAbsoluteFlowTolerance(-1.0));
    assertThrows(IllegalArgumentException.class, () -> model.setAbsoluteFlowTolerance(Double.POSITIVE_INFINITY));
  }
}
