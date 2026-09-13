package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.graph.ProcessEdge;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessNode;

/**
 * Finds feedback loops in a flowsheet and closes them with explicit {@link Recycle} tear streams.
 *
 * <p>
 * A loop that is left as an implicit tear (a stream wired straight back into an upstream mixer) has no convergence
 * criterion of its own: only the outer sweep can close it, with no acceleration and no per-tear tolerance. This class
 * locates such loops - inside a {@link ProcessSystem} through strongly connected components of its
 * {@link ProcessGraph}, and across the areas of a {@link ProcessModel} through streams that flow from a later area back
 * into an earlier one - and rewires each one through a seeded tear stream plus a {@code Recycle}.
 * </p>
 *
 * <p>
 * Only inlets that can be rewired are torn: {@link MixerInterface} and {@link Manifold} expose
 * {@code replaceStream(int, StreamInterface)}. A loop that closes on any other equipment type is reported and left
 * untouched rather than silently mis-wired.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class AutoRecycleBuilder {
  private static final Logger logger = LogManager.getLogger(AutoRecycleBuilder.class);

  /** Default relative tear tolerance used when none is given. */
  public static final double DEFAULT_TOLERANCE = 1.0e-2;

  /**
   * Absolute flow tolerance given to a generated recycle, as a fraction of the largest flow in its process system.
   * Matches the automatic convergence tuning fraction, so a near-zero tear leg converges on its absolute change instead
   * of chasing a relative error it can never meet.
   */
  private static final double AUTO_ABSOLUTE_FLOW_FRACTION = 1.0e-6;

  /** Upper bound on tear rounds, so a pathological topology cannot spin forever. */
  private static final int MAX_TEAR_ROUNDS = 500;

  /**
   * Utility class - not instantiable.
   */
  private AutoRecycleBuilder() {
  }

  /**
   * Inserts recycles on every feedback loop inside one process system that has none.
   *
   * <p>
   * One edge is torn per round and the loop structure is recomputed afterwards, so an area containing several nested
   * cycles gets exactly the tears it needs and no more - every extra tear is another sub-iteration at run time.
   * </p>
   *
   * @param process the flowsheet to rewire; ignored when null
   * @param tolerance relative tear tolerance passed to {@link Recycle#setTolerance(double)}, must be positive
   * @return the recycles created, empty when the flowsheet already closes every loop
   */
  public static List<Recycle> insertRecycles(ProcessSystem process, double tolerance) {
    List<Recycle> created = new ArrayList<Recycle>();
    if (process == null) {
      return created;
    }
    Set<String> unbreakable = new HashSet<String>();
    for (int round = 0; round < MAX_TEAR_ROUNDS; round++) {
      // Wiring a stream into an existing mixer does not mark the cached graph dirty.
      process.invalidateGraph();
      ProcessGraph graph = process.buildGraph();
      ProcessEdge tearEdge = null;
      for (List<ProcessNode> loop : graph.findStronglyConnectedComponents().getRecycleLoops()) {
        if (containsRecycle(loop) || unbreakable.contains(describe(loop))) {
          continue;
        }
        tearEdge = selectTearEdge(loop);
        if (tearEdge == null) {
          unbreakable.add(describe(loop));
          logger.warn("no rewireable inlet in recycle loop " + describe(loop)
              + " - add a Mixer at the loop closure point or an explicit Recycle");
          continue;
        }
        break;
      }
      if (tearEdge == null) {
        return created;
      }
      Recycle recycle = breakEdge(process, tearEdge.getStream(), tearEdge.getTarget().getEquipment(), tolerance);
      if (recycle == null) {
        unbreakable.add(tearEdge.getStream().getName());
        continue;
      }
      created.add(recycle);
    }
    logger.warn("stopped after " + MAX_TEAR_ROUNDS + " tear rounds in " + process.getName());
    return created;
  }

  /**
   * Inserts recycles on cross-area feedback streams and then on every loop inside each area.
   *
   * <p>
   * A stream produced by an area that runs after its consumer is a tear of the outer Gauss-Seidel sweep. Each one is
   * replaced by a seeded tear stream in the consumer and a {@code Recycle} registered in the producing area, so the
   * loop gets its own convergence criterion and acceleration.
   * </p>
   *
   * @param model the multi-area model to rewire; ignored when null
   * @param tolerance relative tear tolerance passed to {@link Recycle#setTolerance(double)}, must be positive
   * @return the recycles created, empty when the model already closes every loop
   */
  public static List<Recycle> insertRecycles(ProcessModel model, double tolerance) {
    List<Recycle> created = new ArrayList<Recycle>();
    if (model == null) {
      return created;
    }
    List<String> areaNames = model.getProcessSystemNames();
    Map<StreamInterface, Integer> producerArea = new IdentityHashMap<StreamInterface, Integer>();
    Set<StreamInterface> recycleOutlets = Collections.newSetFromMap(new IdentityHashMap<StreamInterface, Boolean>());
    for (int areaIndex = 0; areaIndex < areaNames.size(); areaIndex++) {
      ProcessSystem area = model.get(areaNames.get(areaIndex));
      if (area == null) {
        continue;
      }
      for (ProcessEquipmentInterface unit : area.getUnitOperations()) {
        for (StreamInterface outlet : unit.getOutletStreams()) {
          if (outlet == null) {
            continue;
          }
          if (unit instanceof Recycle) {
            recycleOutlets.add(outlet);
          }
          if (!producerArea.containsKey(outlet)) {
            producerArea.put(outlet, Integer.valueOf(areaIndex));
          }
        }
      }
    }

    for (int areaIndex = 0; areaIndex < areaNames.size(); areaIndex++) {
      ProcessSystem area = model.get(areaNames.get(areaIndex));
      if (area == null) {
        continue;
      }
      for (ProcessEquipmentInterface unit : new ArrayList<ProcessEquipmentInterface>(area.getUnitOperations())) {
        if (unit instanceof Recycle) {
          continue;
        }
        List<StreamInterface> inlets = new ArrayList<StreamInterface>(unit.getInletStreams());
        for (int i = 0; i < inlets.size(); i++) {
          StreamInterface inlet = inlets.get(i);
          if (inlet == null || recycleOutlets.contains(inlet)) {
            continue;
          }
          Integer source = producerArea.get(inlet);
          if (source == null || source.intValue() <= areaIndex) {
            continue;
          }
          if (!supportsReplace(unit)) {
            logger.warn("stream " + inlet.getName() + " feeds " + unit.getName() + " in area "
                + areaNames.get(areaIndex) + " from the later area " + areaNames.get(source.intValue())
                + " but the inlet cannot be rewired - route it through a Mixer to get an automatic Recycle");
            continue;
          }
          Recycle recycle = breakEdge(model.get(areaNames.get(source.intValue())), inlet, unit, tolerance);
          if (recycle != null) {
            created.add(recycle);
          }
        }
      }
    }

    for (String areaName : areaNames) {
      created.addAll(insertRecycles(model.get(areaName), tolerance));
    }
    model.invalidateTopology();
    return created;
  }

  /**
   * Checks whether a loop already has a recycle controlling it.
   *
   * @param loop the strongly connected component to inspect
   * @return true when at least one member is a {@link Recycle}
   */
  private static boolean containsRecycle(List<ProcessNode> loop) {
    for (ProcessNode node : loop) {
      if (node.getEquipment() instanceof Recycle) {
        return true;
      }
    }
    return false;
  }

  /**
   * Picks the material edge inside a loop that is cheapest to tear.
   *
   * <p>
   * Candidates are limited to edges that end on a rewireable inlet. The one with the smallest recycle ratio - tear flow
   * divided by the total flow into the consuming unit - is chosen, because the convergence rate of a
   * direct-substitution tear is governed by that ratio: a small side stream entering a large mixer contracts in a few
   * passes, while tearing the main line makes the loop iterate on its own throughput.
   * </p>
   *
   * @param loop the strongly connected component to tear
   * @return the selected edge, or null when no edge in the loop can be rewired
   */
  private static ProcessEdge selectTearEdge(List<ProcessNode> loop) {
    Set<ProcessNode> members = new HashSet<ProcessNode>(loop);
    ProcessEdge best = null;
    double bestRatio = Double.MAX_VALUE;
    for (ProcessNode node : loop) {
      for (ProcessEdge edge : node.getOutgoingEdges()) {
        if (edge.getEdgeType() != ProcessEdge.EdgeType.MATERIAL || !members.contains(edge.getTarget())
            || edge.getStream() == null || !supportsReplace(edge.getTarget().getEquipment())) {
          continue;
        }
        double ratio = recycleRatio(edge);
        if (best == null || ratio < bestRatio) {
          best = edge;
          bestRatio = ratio;
        }
      }
    }
    return best;
  }

  /**
   * Estimates how strongly a candidate tear couples the loop.
   *
   * @param edge the candidate edge
   * @return tear flow divided by the total inlet flow of the consuming unit, or the tear flow itself when that total
   * cannot be read
   */
  private static double recycleRatio(ProcessEdge edge) {
    double tearFlow = flowRate(edge.getStream());
    if (tearFlow == Double.MAX_VALUE) {
      return Double.MAX_VALUE;
    }
    double total = 0.0;
    for (StreamInterface inlet : edge.getTarget().getEquipment().getInletStreams()) {
      double flow = flowRate(inlet);
      if (flow != Double.MAX_VALUE) {
        total += flow;
      }
    }
    return total > 0.0 ? tearFlow / total : tearFlow;
  }

  /**
   * Replaces one inlet by a seeded tear stream and registers the matching recycle.
   *
   * @param owner the process system that will own the tear stream and the recycle
   * @param source the stream that closes the loop
   * @param consumer the unit that consumes {@code source}
   * @param tolerance relative tear tolerance
   * @return the created recycle, or null when the edge could not be rewired
   */
  private static Recycle breakEdge(ProcessSystem owner, StreamInterface source, ProcessEquipmentInterface consumer,
      double tolerance) {
    if (owner == null || source == null || consumer == null) {
      return null;
    }
    if (source.getFluid() == null) {
      logger.warn("cannot tear " + source.getName() + " - it has no fluid yet, run the flowsheet once first");
      return null;
    }
    int inletIndex = inletIndexOf(consumer, source);
    if (inletIndex < 0) {
      return null;
    }

    String tearName = uniqueName(owner, source.getName() + " tear");
    Stream tear = new Stream(tearName, source.getFluid().clone());
    try {
      tear.run();
    } catch (Exception ex) {
      logger.warn("seeding tear stream " + tearName + " failed: " + ex.getMessage(), ex);
    }
    replaceInlet(consumer, inletIndex, tear);

    Recycle recycle = new Recycle(uniqueName(owner, tearName + " recycle"));
    recycle.addStream(source);
    recycle.setOutletStream(tear);
    tuneRecycle(owner, recycle, tolerance);

    int consumerPosition = owner.getUnitOperations().indexOf(consumer);
    if (consumerPosition >= 0) {
      owner.add(consumerPosition, tear);
    } else {
      owner.add(tear);
    }
    owner.add(recycle);
    return recycle;
  }

  /**
   * Configures a generated recycle for fast and stable convergence.
   *
   * <p>
   * The loop starts on direct substitution, which is the robust choice while the tear is still far from its fixed
   * point, and adaptive acceleration lets it switch itself to Wegstein once its flow error stops contracting - that is
   * exactly the oscillating low-flow loop that otherwise iterates to the budget. An absolute flow tolerance scaled to
   * the largest flow in the owning area is added as well, so a tear on a near-zero leg converges on its absolute change
   * rather than on a relative error that stays at order one.
   * </p>
   *
   * @param owner the process system that owns the recycle, used for the flow scale
   * @param recycle the recycle to configure
   * @param tolerance relative tear tolerance
   */
  private static void tuneRecycle(ProcessSystem owner, Recycle recycle, double tolerance) {
    recycle.setTolerance(tolerance);
    recycle.setAdaptiveAcceleration(true);
    double scale = owner.getMaxStreamFlowRate();
    if (Double.isFinite(scale) && scale > 0.0) {
      recycle.applyAutoAbsoluteFlowTolerance(scale * AUTO_ABSOLUTE_FLOW_FRACTION);
    }
  }

  /**
   * Finds the inlet position of a stream on a unit using identity comparison.
   *
   * @param unit the consuming unit
   * @param stream the stream to locate
   * @return zero-based inlet index, or -1 when the stream is not an inlet of the unit
   */
  private static int inletIndexOf(ProcessEquipmentInterface unit, StreamInterface stream) {
    List<StreamInterface> inlets = unit.getInletStreams();
    for (int i = 0; i < inlets.size(); i++) {
      if (inlets.get(i) == stream) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Checks whether an inlet stream of the unit can be swapped for a tear stream.
   *
   * @param unit the unit to test
   * @return true for equipment exposing {@code replaceStream(int, StreamInterface)}
   */
  private static boolean supportsReplace(ProcessEquipmentInterface unit) {
    return unit instanceof MixerInterface || unit instanceof Manifold;
  }

  /**
   * Swaps one inlet stream of a rewireable unit.
   *
   * @param unit the consuming unit, must satisfy {@link #supportsReplace(ProcessEquipmentInterface)}
   * @param index zero-based inlet index
   * @param stream the replacement stream
   */
  private static void replaceInlet(ProcessEquipmentInterface unit, int index, StreamInterface stream) {
    if (unit instanceof Manifold) {
      ((Manifold) unit).replaceStream(index, stream);
    } else {
      ((MixerInterface) unit).replaceStream(index, stream);
    }
  }

  /**
   * Reads a stream mass flow, treating an unreadable value as very large so it is not chosen as tear.
   *
   * @param stream the stream to read
   * @return mass flow in kg/hr, or {@link Double#MAX_VALUE} when unavailable
   */
  private static double flowRate(StreamInterface stream) {
    try {
      double flow = stream.getFlowRate("kg/hr");
      return Double.isFinite(flow) ? flow : Double.MAX_VALUE;
    } catch (Exception ex) {
      return Double.MAX_VALUE;
    }
  }

  /**
   * Builds a unit name that is not yet used in the process system.
   *
   * @param process the process system to check against
   * @param preferred the desired name
   * @return {@code preferred}, or {@code preferred} with a numeric suffix when taken
   */
  private static String uniqueName(ProcessSystem process, String preferred) {
    if (!process.hasUnitName(preferred)) {
      return preferred;
    }
    for (int i = 2; i < 1000; i++) {
      String candidate = preferred + " " + i;
      if (!process.hasUnitName(candidate)) {
        return candidate;
      }
    }
    return preferred + " " + System.identityHashCode(process);
  }

  /**
   * Formats the unit names of a loop for logging.
   *
   * @param loop the strongly connected component
   * @return comma separated unit names in brackets
   */
  private static String describe(List<ProcessNode> loop) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < loop.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(loop.get(i).getName());
    }
    return sb.append(']').toString();
  }
}
