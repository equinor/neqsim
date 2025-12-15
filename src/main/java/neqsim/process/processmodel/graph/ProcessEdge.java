package neqsim.process.processmodel.graph;

import java.io.Serializable;
import java.util.Objects;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Represents a directed edge in the process flowsheet graph. Each edge corresponds to a stream
 * connection between two equipment units.
 *
 * <p>
 * This class provides:
 * <ul>
 * <li>Source and target node references</li>
 * <li>Optional reference to the underlying StreamInterface</li>
 * <li>Edge type classification (material, energy, signal)</li>
 * <li>Graph-neural-network compatible edge features</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ProcessEdge implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Edge types representing different connection types in process flowsheets.
   */
  public enum EdgeType {
    /** Material stream (fluid flow). */
    MATERIAL,
    /** Energy stream (heat, work). */
    ENERGY,
    /** Signal/control connection. */
    SIGNAL,
    /** Recycle stream (creates cycle in graph). */
    RECYCLE,
    /** Unknown connection type. */
    UNKNOWN
  }

  /** The source node (upstream equipment). */
  private final ProcessNode source;

  /** The target node (downstream equipment). */
  private final ProcessNode target;

  /** The stream this edge represents (may be null for control signals). */
  private final StreamInterface stream;

  /** Name of this edge/stream. */
  private final String name;

  /** Type of this edge. */
  private final EdgeType edgeType;

  /** Unique index of this edge in the graph. */
  private final int index;

  /** Whether this edge is part of a cycle (back edge in DFS). */
  private transient boolean isBackEdge = false;

  /**
   * Creates a new process edge.
   *
   * @param index unique index of this edge
   * @param source source node
   * @param target target node
   * @param stream the stream this edge represents (may be null)
   * @param name edge name
   * @param edgeType type of edge
   */
  public ProcessEdge(int index, ProcessNode source, ProcessNode target, StreamInterface stream,
      String name, EdgeType edgeType) {
    this.index = index;
    this.source = Objects.requireNonNull(source, "source cannot be null");
    this.target = Objects.requireNonNull(target, "target cannot be null");
    this.stream = stream;
    this.name = name != null ? name : generateName();
    this.edgeType = edgeType != null ? edgeType : EdgeType.UNKNOWN;
  }

  /**
   * Creates a material edge with automatic type detection.
   *
   * @param index unique index
   * @param source source node
   * @param target target node
   * @param stream the stream
   */
  public ProcessEdge(int index, ProcessNode source, ProcessNode target, StreamInterface stream) {
    this(index, source, target, stream, stream != null ? stream.getName() : null,
        detectEdgeType(stream, source, target));
  }

  /**
   * Creates an edge without a stream (e.g., control signal).
   *
   * @param index unique index
   * @param source source node
   * @param target target node
   * @param name edge name
   * @param edgeType type of edge
   */
  public ProcessEdge(int index, ProcessNode source, ProcessNode target, String name,
      EdgeType edgeType) {
    this(index, source, target, null, name, edgeType);
  }

  private String generateName() {
    return source.getName() + " -> " + target.getName();
  }

  private static EdgeType detectEdgeType(StreamInterface stream, ProcessNode source,
      ProcessNode target) {
    if (stream == null) {
      return EdgeType.UNKNOWN;
    }

    // Check if this is a recycle stream based on equipment type
    String sourceType = source.getEquipmentType().toLowerCase();
    String targetType = target.getEquipmentType().toLowerCase();

    if (sourceType.contains("recycle") || targetType.contains("recycle")) {
      return EdgeType.RECYCLE;
    }

    // Check if it's an energy stream
    if (stream.getClass().getSimpleName().toLowerCase().contains("energy")) {
      return EdgeType.ENERGY;
    }

    return EdgeType.MATERIAL;
  }

  /**
   * Gets the unique index of this edge.
   *
   * @return the edge index
   */
  public int getIndex() {
    return index;
  }

  /**
   * Gets the source node.
   *
   * @return source node
   */
  public ProcessNode getSource() {
    return source;
  }

  /**
   * Gets the target node.
   *
   * @return target node
   */
  public ProcessNode getTarget() {
    return target;
  }

  /**
   * Gets the stream this edge represents.
   *
   * @return the stream, or null if this is not a stream connection
   */
  public StreamInterface getStream() {
    return stream;
  }

  /**
   * Gets the edge name.
   *
   * @return edge name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the edge type.
   *
   * @return edge type
   */
  public EdgeType getEdgeType() {
    return edgeType;
  }

  /**
   * Checks if this edge is a recycle edge.
   *
   * @return true if this is a recycle edge
   */
  public boolean isRecycle() {
    return edgeType == EdgeType.RECYCLE;
  }

  /**
   * Checks if this edge is a back edge (creates a cycle in the graph).
   *
   * @return true if this is a back edge
   */
  public boolean isBackEdge() {
    return isBackEdge;
  }

  /**
   * Sets whether this edge is a back edge.
   *
   * @param isBackEdge true if this is a back edge
   */
  void setBackEdge(boolean isBackEdge) {
    this.isBackEdge = isBackEdge;
  }

  /**
   * Gets the source node index.
   *
   * @return source node index
   */
  public int getSourceIndex() {
    return source.getIndex();
  }

  /**
   * Gets the target node index.
   *
   * @return target node index
   */
  public int getTargetIndex() {
    return target.getIndex();
  }

  /**
   * Generates edge feature vector for GNN compatibility.
   *
   * <p>
   * Features include:
   * <ul>
   * <li>One-hot encoded edge type</li>
   * <li>Stream properties (temperature, pressure, flow rate)</li>
   * <li>Back edge indicator</li>
   * </ul>
   *
   * @return feature vector
   */
  public double[] getFeatureVector() {
    // Features: edge type one-hot (5) + T + P + flow + back edge
    double[] features = new double[9];

    // One-hot encode edge type
    features[edgeType.ordinal()] = 1.0;

    // Stream properties (if available)
    if (stream != null) {
      try {
        features[5] = Math.min(1.0, stream.getTemperature("K") / 1000.0);
      } catch (Exception e) {
        features[5] = 0.0;
      }

      try {
        features[6] = Math.min(1.0, stream.getPressure() / 100.0);
      } catch (Exception e) {
        features[6] = 0.0;
      }

      try {
        if (stream.getThermoSystem() != null) {
          features[7] = Math.min(1.0, stream.getThermoSystem().getFlowRate("kg/hr") / 100000.0);
        }
      } catch (Exception e) {
        features[7] = 0.0;
      }
    }

    // Back edge indicator
    features[8] = isBackEdge ? 1.0 : 0.0;

    return features;
  }

  /**
   * Gets edge as index pair [source, target] for sparse graph representation.
   *
   * @return array with [sourceIndex, targetIndex]
   */
  public int[] getIndexPair() {
    return new int[] {source.getIndex(), target.getIndex()};
  }

  @Override
  public String toString() {
    return String.format("ProcessEdge[%d: %s -> %s (%s)%s]", index, source.getName(),
        target.getName(), edgeType, isBackEdge ? " BACK" : "");
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ProcessEdge other = (ProcessEdge) obj;
    return index == other.index && Objects.equals(source, other.source)
        && Objects.equals(target, other.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, source, target);
  }
}
