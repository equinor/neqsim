package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Promotes statistical lead-lag relationships to causal candidates using flowsheet topology.
 *
 * <p>
 * {@link RelationshipGraph} finds which tags move together and which moves first, but statistical correlation is not
 * causation. This class overlays the <b>physical</b> process connectivity (which equipment feeds which) on the
 * statistical edges: a lead-lag edge whose leading tag sits on equipment that is upstream of the following tag's
 * equipment is promoted to a {@link Verdict#CAUSAL_CANDIDATE}; a strong edge that does not follow any process path is
 * flagged as {@link Verdict#COMMON_CAUSE_OR_ARTIFACT} (a shared hidden driver or an instrument artifact rather than a
 * direct cause).
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * Map&lt;String, Set&lt;String&gt;&gt; downstream = CausalTopologyModel.buildDownstreamAdjacency(processSystem);
 * CausalTopologyModel model = new CausalTopologyModel(downstream, tagToEquipment);
 * List&lt;CausalTopologyModel.CausalEdge&gt; edges = model.classify(relationships);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see RelationshipGraph
 * @see FailurePropagationTracer
 */
public class CausalTopologyModel implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(CausalTopologyModel.class);

  /** Directed adjacency: equipment name to the set of directly downstream equipment names. */
  private final Map<String, Set<String>> downstreamAdjacency;

  /** Map of tag name to owning equipment name. */
  private final Map<String, String> tagToEquipment;

  /**
   * Creates a causal topology model.
   *
   * @param downstreamAdjacency directed adjacency of equipment to directly downstream equipment
   * @param tagToEquipment map of tag name to owning equipment name
   */
  public CausalTopologyModel(Map<String, Set<String>> downstreamAdjacency, Map<String, String> tagToEquipment) {
    this.downstreamAdjacency = downstreamAdjacency != null ? downstreamAdjacency : new HashMap<String, Set<String>>();
    this.tagToEquipment = tagToEquipment != null ? tagToEquipment : new HashMap<String, String>();
  }

  /**
   * Classifies each discovered relationship against the process topology.
   *
   * @param relationships discovered relationships, typically from {@link RelationshipGraph#analyze(Map)}
   * @return causal edges, one per input relationship
   */
  public List<CausalEdge> classify(List<RelationshipGraph.Relationship> relationships) {
    List<CausalEdge> edges = new ArrayList<CausalEdge>();
    if (relationships == null) {
      return edges;
    }
    for (RelationshipGraph.Relationship r : relationships) {
      edges.add(classifyOne(r));
    }
    logger.info("CausalTopologyModel classified {} relationships against topology", edges.size());
    return edges;
  }

  /**
   * Classifies a single relationship.
   *
   * @param r the relationship to classify
   * @return the resulting causal edge
   */
  private CausalEdge classifyOne(RelationshipGraph.Relationship r) {
    String leaderEq = tagToEquipment.get(r.getSource());
    String followerEq = tagToEquipment.get(r.getTarget());

    if (leaderEq == null || followerEq == null) {
      return new CausalEdge(r, Verdict.UNKNOWN, leaderEq, followerEq,
          "tag-to-equipment mapping missing for one or both tags");
    }
    if (leaderEq.equals(followerEq)) {
      return new CausalEdge(r, Verdict.LOCAL, leaderEq, followerEq,
          "both tags belong to the same equipment (" + leaderEq + ")");
    }
    if (isReachable(leaderEq, followerEq)) {
      return new CausalEdge(r, Verdict.CAUSAL_CANDIDATE, leaderEq, followerEq,
          leaderEq + " is upstream of " + followerEq + " and moves first");
    }
    if (isReachable(followerEq, leaderEq)) {
      // Statistics say leader moves first, but topology flows the other way: counter-flow / feedback signal.
      return new CausalEdge(r, Verdict.COUNTER_FLOW, leaderEq, followerEq,
          "lead-lag direction opposes process flow (" + followerEq + " is upstream of " + leaderEq + ")");
    }
    return new CausalEdge(r, Verdict.COMMON_CAUSE_OR_ARTIFACT, leaderEq, followerEq,
        "no process path connects " + leaderEq + " and " + followerEq + "; likely shared driver or artifact");
  }

  /**
   * Determines whether {@code target} is reachable from {@code source} by following downstream edges.
   *
   * @param source starting equipment name
   * @param target target equipment name
   * @return true when a directed path exists from source to target
   */
  private boolean isReachable(String source, String target) {
    if (source.equals(target)) {
      return true;
    }
    Set<String> visited = new HashSet<String>();
    Deque<String> queue = new ArrayDeque<String>();
    queue.add(source);
    visited.add(source);
    while (!queue.isEmpty()) {
      String current = queue.poll();
      Set<String> neighbors = downstreamAdjacency.get(current);
      if (neighbors == null) {
        continue;
      }
      for (String n : neighbors) {
        if (n.equals(target)) {
          return true;
        }
        if (visited.add(n)) {
          queue.add(n);
        }
      }
    }
    return false;
  }

  /**
   * Builds a directed downstream-adjacency map from a process system by matching shared stream objects.
   *
   * <p>
   * Equipment {@code B} is downstream of {@code A} when an outlet stream of {@code A} is the same object as an inlet
   * stream of {@code B} (process systems share stream objects by reference).
   * </p>
   *
   * @param processSystem the process system to analyze
   * @return map of equipment name to the set of directly downstream equipment names
   */
  public static Map<String, Set<String>> buildDownstreamAdjacency(ProcessSystem processSystem) {
    Map<String, Set<String>> adjacency = new HashMap<String, Set<String>>();
    if (processSystem == null) {
      return adjacency;
    }
    List<ProcessEquipmentInterface> units = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      units.add(eq);
    }
    for (ProcessEquipmentInterface a : units) {
      List<StreamInterface> outlets = safeStreams(a, true);
      if (outlets.isEmpty()) {
        continue;
      }
      Set<String> downstream = new HashSet<String>();
      for (ProcessEquipmentInterface b : units) {
        if (a == b) {
          continue;
        }
        List<StreamInterface> inlets = safeStreams(b, false);
        if (sharesStream(outlets, inlets)) {
          downstream.add(b.getName());
        }
      }
      if (!downstream.isEmpty()) {
        adjacency.put(a.getName(), downstream);
      }
    }
    return adjacency;
  }

  /**
   * Returns the inlet or outlet streams of a unit, tolerating equipment that does not expose them.
   *
   * @param unit the process equipment
   * @param outlet true for outlet streams, false for inlet streams
   * @return the stream list, or an empty list on failure
   */
  private static List<StreamInterface> safeStreams(ProcessEquipmentInterface unit, boolean outlet) {
    try {
      List<StreamInterface> streams = outlet ? unit.getOutletStreams() : unit.getInletStreams();
      return streams != null ? streams : new ArrayList<StreamInterface>();
    } catch (RuntimeException ex) {
      return new ArrayList<StreamInterface>();
    }
  }

  /**
   * Determines whether any outlet stream is the same object as any inlet stream.
   *
   * @param outlets outlet streams of the upstream candidate
   * @param inlets inlet streams of the downstream candidate
   * @return true when the two lists share a stream object
   */
  private static boolean sharesStream(List<StreamInterface> outlets, List<StreamInterface> inlets) {
    for (StreamInterface o : outlets) {
      if (o == null) {
        continue;
      }
      for (StreamInterface i : inlets) {
        if (o == i) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Topology-informed verdict for a statistical relationship.
   */
  public enum Verdict {
    /** Leader is upstream of follower and moves first: a candidate direct cause. */
    CAUSAL_CANDIDATE,
    /** Both tags belong to the same equipment. */
    LOCAL,
    /** Lead-lag direction opposes the process flow direction (feedback / counter-flow). */
    COUNTER_FLOW,
    /** No process path connects the two: likely a shared hidden driver or an instrument artifact. */
    COMMON_CAUSE_OR_ARTIFACT,
    /** Tag-to-equipment mapping is missing, so topology could not be applied. */
    UNKNOWN
  }

  /**
   * A relationship classified against the process topology.
   */
  public static final class CausalEdge implements Serializable {

    private static final long serialVersionUID = 1000L;

    /** Underlying statistical relationship. */
    private final RelationshipGraph.Relationship relationship;

    /** Topology-informed verdict. */
    private final Verdict verdict;

    /** Equipment owning the leading tag, or null. */
    private final String leaderEquipment;

    /** Equipment owning the following tag, or null. */
    private final String followerEquipment;

    /** Human-readable rationale. */
    private final String rationale;

    /**
     * Creates a causal edge.
     *
     * @param relationship underlying statistical relationship
     * @param verdict topology-informed verdict
     * @param leaderEquipment equipment owning the leading tag, or null
     * @param followerEquipment equipment owning the following tag, or null
     * @param rationale human-readable rationale
     */
    public CausalEdge(RelationshipGraph.Relationship relationship, Verdict verdict, String leaderEquipment,
        String followerEquipment, String rationale) {
      this.relationship = relationship;
      this.verdict = verdict;
      this.leaderEquipment = leaderEquipment;
      this.followerEquipment = followerEquipment;
      this.rationale = rationale;
    }

    /**
     * Returns the underlying statistical relationship.
     *
     * @return the relationship
     */
    public RelationshipGraph.Relationship getRelationship() {
      return relationship;
    }

    /**
     * Returns the topology-informed verdict.
     *
     * @return the verdict
     */
    public Verdict getVerdict() {
      return verdict;
    }

    /**
     * Returns the equipment owning the leading tag.
     *
     * @return leader equipment name, or null
     */
    public String getLeaderEquipment() {
      return leaderEquipment;
    }

    /**
     * Returns the equipment owning the following tag.
     *
     * @return follower equipment name, or null
     */
    public String getFollowerEquipment() {
      return followerEquipment;
    }

    /**
     * Returns the human-readable rationale.
     *
     * @return rationale
     */
    public String getRationale() {
      return rationale;
    }

    /**
     * Returns a human-readable summary of the causal edge.
     *
     * @return formatted summary
     */
    @Override
    public String toString() {
      return String.format("[%s] %s: %s", verdict, relationship.toString(), rationale);
    }
  }
}
