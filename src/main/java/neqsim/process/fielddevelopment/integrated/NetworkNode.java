package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;

/**
 * A node in an integrated production network.
 *
 * <p>
 * Nodes are connection points between branches ({@link NetworkBranch}). A node carries a pressure
 * that is either fixed (a boundary condition such as a reservoir datum pressure or an export sink
 * pressure) or solved for by {@link NetworkNewtonSolver}. An optional external rate models a source
 * (injection, positive) or sink (offtake, negative) attached directly to the node.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see NetworkNewtonSolver
 * @see NetworkBranch
 */
public class NetworkNode implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Classification of a network node. */
  public enum NodeType {
    /** Reservoir datum with a fixed (boundary) pressure that drives inflow. */
    RESERVOIR,
    /** A pipe/manifold junction whose pressure is solved for. */
    JUNCTION,
    /** A gathering manifold whose pressure is solved for. */
    MANIFOLD,
    /** A fixed-pressure boundary sink (separator inlet, export header). */
    SINK,
    /** A fixed-pressure boundary source. */
    SOURCE
  }

  private final String name;
  private NodeType type;
  private boolean pressureFixed;
  private double pressure; // bara
  private double externalRate; // Sm3/day, positive = injection into node, negative = offtake

  /**
   * Creates a network node.
   *
   * @param name unique node name
   * @param type node classification
   * @param pressureBara initial or fixed pressure in bara
   * @param pressureFixed true if the pressure is a boundary condition (not solved for)
   */
  public NetworkNode(String name, NodeType type, double pressureBara, boolean pressureFixed) {
    this.name = name;
    this.type = type;
    this.pressure = pressureBara;
    this.pressureFixed = pressureFixed;
  }

  /**
   * Creates a fixed-pressure reservoir boundary node.
   *
   * @param name unique node name
   * @param reservoirPressureBara reservoir datum pressure in bara
   * @return a reservoir node
   */
  public static NetworkNode reservoir(String name, double reservoirPressureBara) {
    return new NetworkNode(name, NodeType.RESERVOIR, reservoirPressureBara, true);
  }

  /**
   * Creates a solved-for manifold node.
   *
   * @param name unique node name
   * @param initialPressureBara initial pressure guess in bara
   * @return a manifold node
   */
  public static NetworkNode manifold(String name, double initialPressureBara) {
    return new NetworkNode(name, NodeType.MANIFOLD, initialPressureBara, false);
  }

  /**
   * Creates a fixed-pressure sink (export/separator) boundary node.
   *
   * @param name unique node name
   * @param sinkPressureBara fixed sink pressure in bara
   * @return a sink node
   */
  public static NetworkNode sink(String name, double sinkPressureBara) {
    return new NetworkNode(name, NodeType.SINK, sinkPressureBara, true);
  }

  /**
   * Returns the node name.
   *
   * @return node name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the node type.
   *
   * @return node type
   */
  public NodeType getType() {
    return type;
  }

  /**
   * Returns whether the node pressure is a fixed boundary condition.
   *
   * @return true if fixed
   */
  public boolean isPressureFixed() {
    return pressureFixed;
  }

  /**
   * Sets whether the node pressure is fixed.
   *
   * @param fixed true to fix the pressure as a boundary condition
   */
  public void setPressureFixed(boolean fixed) {
    this.pressureFixed = fixed;
  }

  /**
   * Returns the node pressure.
   *
   * @return pressure in bara
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Sets the node pressure.
   *
   * @param pressureBara pressure in bara
   */
  public void setPressure(double pressureBara) {
    this.pressure = pressureBara;
  }

  /**
   * Returns the external rate attached to the node.
   *
   * @return external rate in Sm3/day (positive = injection, negative = offtake)
   */
  public double getExternalRate() {
    return externalRate;
  }

  /**
   * Sets an external source/sink rate at the node.
   *
   * @param rateSm3PerDay external rate in Sm3/day (positive = injection, negative = offtake)
   */
  public void setExternalRate(double rateSm3PerDay) {
    this.externalRate = rateSm3PerDay;
  }
}
