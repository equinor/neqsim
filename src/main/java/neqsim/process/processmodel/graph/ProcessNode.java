package neqsim.process.processmodel.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Represents a node in the process flowsheet graph. Each node corresponds to a
 * {@link ProcessEquipmentInterface} unit operation.
 *
 * <p>
 * This class provides:
 * <ul>
 * <li>Unique identification via index and equipment name</li>
 * <li>Tracking of incoming and outgoing edges (stream connections)</li>
 * <li>Graph-neural-network compatible feature vectors</li>
 * <li>Execution state tracking for topological ordering</li>
 * </ul>
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ProcessNode implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Unique index of this node in the graph. */
  private final int index;

  /** The process equipment this node represents. */
  private final ProcessEquipmentInterface equipment;

  /** Edges representing incoming streams (dependencies). */
  private final List<ProcessEdge> incomingEdges = new ArrayList<>();

  /** Edges representing outgoing streams (dependents). */
  private final List<ProcessEdge> outgoingEdges = new ArrayList<>();

  /** Whether this node has been visited during graph traversal. */
  private transient boolean visited = false;

  /** Whether this node is currently on the DFS stack (for cycle detection). */
  private transient boolean onStack = false;

  /** Topological sort order (-1 if not yet sorted). */
  private transient int topologicalOrder = -1;

  /** Strongly connected component index (-1 if not yet assigned). */
  private transient int sccIndex = -1;

  /**
   * Creates a new process node.
   *
   * @param index unique index of this node in the graph
   * @param equipment the process equipment this node represents
   */
  public ProcessNode(int index, ProcessEquipmentInterface equipment) {
    this.index = index;
    this.equipment = Objects.requireNonNull(equipment, "equipment cannot be null");
  }

  /**
   * Gets the unique index of this node.
   *
   * @return the node index
   */
  public int getIndex() {
    return index;
  }

  /**
   * Gets the name of the equipment.
   *
   * @return equipment name
   */
  public String getName() {
    return equipment.getName();
  }

  /**
   * Gets the process equipment this node represents.
   *
   * @return the process equipment
   */
  public ProcessEquipmentInterface getEquipment() {
    return equipment;
  }

  /**
   * Gets the equipment type (simple class name).
   *
   * @return the equipment type
   */
  public String getEquipmentType() {
    return equipment.getClass().getSimpleName();
  }

  /**
   * Adds an incoming edge (dependency) to this node.
   *
   * @param edge the incoming edge
   */
  void addIncomingEdge(ProcessEdge edge) {
    if (!incomingEdges.contains(edge)) {
      incomingEdges.add(edge);
    }
  }

  /**
   * Adds an outgoing edge (dependent) from this node.
   *
   * @param edge the outgoing edge
   */
  void addOutgoingEdge(ProcessEdge edge) {
    if (!outgoingEdges.contains(edge)) {
      outgoingEdges.add(edge);
    }
  }

  /**
   * Gets all incoming edges (dependencies).
   *
   * @return unmodifiable list of incoming edges
   */
  public List<ProcessEdge> getIncomingEdges() {
    return Collections.unmodifiableList(incomingEdges);
  }

  /**
   * Gets all outgoing edges (dependents).
   *
   * @return unmodifiable list of outgoing edges
   */
  public List<ProcessEdge> getOutgoingEdges() {
    return Collections.unmodifiableList(outgoingEdges);
  }

  /**
   * Gets the in-degree (number of incoming edges/dependencies).
   *
   * @return the in-degree
   */
  public int getInDegree() {
    return incomingEdges.size();
  }

  /**
   * Gets the out-degree (number of outgoing edges/dependents).
   *
   * @return the out-degree
   */
  public int getOutDegree() {
    return outgoingEdges.size();
  }

  /**
   * Checks if this is a source node (no incoming edges).
   *
   * @return true if this is a source node
   */
  public boolean isSource() {
    return incomingEdges.isEmpty();
  }

  /**
   * Checks if this is a sink node (no outgoing edges).
   *
   * @return true if this is a sink node
   */
  public boolean isSink() {
    return outgoingEdges.isEmpty();
  }

  /**
   * Gets all predecessor nodes (sources of incoming edges).
   *
   * @return list of predecessor nodes
   */
  public List<ProcessNode> getPredecessors() {
    List<ProcessNode> predecessors = new ArrayList<>();
    for (ProcessEdge edge : incomingEdges) {
      predecessors.add(edge.getSource());
    }
    return predecessors;
  }

  /**
   * Gets all successor nodes (targets of outgoing edges).
   *
   * @return list of successor nodes
   */
  public List<ProcessNode> getSuccessors() {
    List<ProcessNode> successors = new ArrayList<>();
    for (ProcessEdge edge : outgoingEdges) {
      successors.add(edge.getTarget());
    }
    return successors;
  }

  /**
   * Generates a feature vector for graph neural network compatibility.
   *
   * <p>
   * Features include:
   * <ul>
   * <li>One-hot encoded equipment type</li>
   * <li>In-degree and out-degree</li>
   * <li>Operating conditions (if available)</li>
   * </ul>
   * </p>
   *
   * @param typeMapping mapping of equipment types to indices
   * @param numTypes total number of equipment types
   * @return feature vector
   */
  public double[] getFeatureVector(java.util.Map<String, Integer> typeMapping, int numTypes) {
    // Base features: one-hot type + degrees + operating conditions
    int baseFeatures = numTypes + 2 + 3; // type + in/out degree + T/P/flow
    double[] features = new double[baseFeatures];

    // One-hot encode equipment type
    String type = getEquipmentType();
    Integer typeIndex = typeMapping.get(type);
    if (typeIndex != null && typeIndex < numTypes) {
      features[typeIndex] = 1.0;
    }

    // Degree features (normalized)
    features[numTypes] = Math.min(1.0, getInDegree() / 10.0);
    features[numTypes + 1] = Math.min(1.0, getOutDegree() / 10.0);

    // Operating conditions (normalized, if available)
    try {
      double temp = equipment.getTemperature("K");
      features[numTypes + 2] = Math.min(1.0, temp / 1000.0); // Normalize to ~1000K
    } catch (Exception e) {
      features[numTypes + 2] = 0.0;
    }

    try {
      double pressure = equipment.getPressure();
      features[numTypes + 3] = Math.min(1.0, pressure / 100.0); // Normalize to ~100 bar
    } catch (Exception e) {
      features[numTypes + 3] = 0.0;
    }

    try {
      if (equipment.getThermoSystem() != null) {
        double flow = equipment.getThermoSystem().getFlowRate("kg/hr");
        features[numTypes + 4] = Math.min(1.0, flow / 100000.0); // Normalize to ~100 t/hr
      }
    } catch (Exception e) {
      features[numTypes + 4] = 0.0;
    }

    return features;
  }

  // Graph traversal state methods (package-private for use by ProcessGraph)

  boolean isVisited() {
    return visited;
  }

  void setVisited(boolean visited) {
    this.visited = visited;
  }

  boolean isOnStack() {
    return onStack;
  }

  void setOnStack(boolean onStack) {
    this.onStack = onStack;
  }

  int getTopologicalOrder() {
    return topologicalOrder;
  }

  void setTopologicalOrder(int order) {
    this.topologicalOrder = order;
  }

  int getSccIndex() {
    return sccIndex;
  }

  void setSccIndex(int sccIndex) {
    this.sccIndex = sccIndex;
  }

  /**
   * Resets traversal state for a new graph analysis.
   */
  void resetTraversalState() {
    visited = false;
    onStack = false;
    topologicalOrder = -1;
    sccIndex = -1;
  }

  @Override
  public String toString() {
    return String.format("ProcessNode[%d: %s (%s), in=%d, out=%d]", index, getName(),
        getEquipmentType(), getInDegree(), getOutDegree());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ProcessNode other = (ProcessNode) obj;
    return index == other.index && Objects.equals(equipment, other.equipment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, equipment);
  }
}
