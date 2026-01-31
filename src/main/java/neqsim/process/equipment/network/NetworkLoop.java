package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an independent loop in a pipeline network.
 *
 * <p>
 * A loop consists of a set of pipelines that form a closed path in the network. Each pipeline in
 * the loop has a direction (+1 or -1) indicating whether its positive flow direction aligns with
 * the loop traversal direction.
 * </p>
 *
 * <p>
 * This class is used by the Hardy Cross method to balance head losses around each independent loop
 * in a looped network topology.
 * </p>
 *
 * <h2>Hardy Cross Method</h2>
 * <p>
 * For each loop, the method:
 * </p>
 * <ol>
 * <li>Calculates the algebraic sum of head losses around the loop</li>
 * <li>Calculates a flow correction using: &Delta;Q = -&sum;H / (2 &middot; &sum;|H/Q|)</li>
 * <li>Applies the correction to all pipes in the loop</li>
 * </ol>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Cross, H. (1936). "Analysis of Flow in Networks of Conduits or Conductors."</li>
 * <li>Todini, E. and Pilati, S. (1988). "A gradient algorithm for the analysis of pipe
 * networks."</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see LoopDetector
 * @see LoopedPipeNetwork
 */
public class NetworkLoop implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Represents a pipeline member of a loop with its traversal direction.
   */
  public static class LoopMember implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The pipeline name. */
    private final String pipeName;

    /** Direction in the loop: +1 = same as loop direction, -1 = opposite. */
    private final int direction;

    /**
     * Create a loop member.
     *
     * @param pipeName the pipeline name
     * @param direction the direction (+1 or -1)
     */
    public LoopMember(String pipeName, int direction) {
      this.pipeName = pipeName;
      this.direction = direction;
    }

    /**
     * Get the pipeline name.
     *
     * @return the pipeline name
     */
    public String getPipeName() {
      return pipeName;
    }

    /**
     * Get the direction in the loop.
     *
     * @return +1 if same as loop direction, -1 if opposite
     */
    public int getDirection() {
      return direction;
    }
  }

  /** Unique identifier for this loop. */
  private final String loopId;

  /** Pipes in this loop with their directions. */
  private final List<LoopMember> members;

  /** Last calculated head loss imbalance (Pa). */
  private double lastHeadLossImbalance = 0.0;

  /** Last calculated flow correction (kg/s). */
  private double lastFlowCorrection = 0.0;

  /** Convergence tolerance for head loss balance (Pa). */
  private double tolerance = 100.0; // 100 Pa default

  /**
   * Create a new network loop.
   *
   * @param loopId unique identifier
   */
  public NetworkLoop(String loopId) {
    this.loopId = loopId;
    this.members = new ArrayList<>();
  }

  /**
   * Add a pipeline to this loop.
   *
   * @param pipeName the pipeline name
   * @param direction +1 if flow direction aligns with loop traversal, -1 otherwise
   */
  public void addMember(String pipeName, int direction) {
    members.add(new LoopMember(pipeName, direction));
  }

  /**
   * Get the loop identifier.
   *
   * @return loop ID
   */
  public String getLoopId() {
    return loopId;
  }

  /**
   * Get all members of this loop.
   *
   * @return unmodifiable list of loop members
   */
  public List<LoopMember> getMembers() {
    return Collections.unmodifiableList(members);
  }

  /**
   * Get the number of pipes in this loop.
   *
   * @return number of pipes
   */
  public int size() {
    return members.size();
  }

  /**
   * Check if the loop is balanced within tolerance.
   *
   * @param imbalance current head loss imbalance in Pa
   * @return true if |head loss imbalance| is less than tolerance
   */
  public boolean isBalanced(double imbalance) {
    return Math.abs(imbalance) < tolerance;
  }

  /**
   * Get the last calculated head loss imbalance.
   *
   * @return head loss imbalance in Pa
   */
  public double getLastHeadLossImbalance() {
    return lastHeadLossImbalance;
  }

  /**
   * Set the last head loss imbalance.
   *
   * @param imbalance head loss imbalance in Pa
   */
  public void setLastHeadLossImbalance(double imbalance) {
    this.lastHeadLossImbalance = imbalance;
  }

  /**
   * Get the last calculated flow correction.
   *
   * @return flow correction in kg/s
   */
  public double getLastFlowCorrection() {
    return lastFlowCorrection;
  }

  /**
   * Set the last flow correction.
   *
   * @param correction flow correction in kg/s
   */
  public void setLastFlowCorrection(double correction) {
    this.lastFlowCorrection = correction;
  }

  /**
   * Set the convergence tolerance for head loss balance.
   *
   * @param tolerance tolerance in Pa
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Get the convergence tolerance.
   *
   * @return tolerance in Pa
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Get a string representation of the loop for debugging.
   *
   * @return string describing the loop
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Loop-").append(loopId).append(" [");
    for (int i = 0; i < members.size(); i++) {
      if (i > 0) {
        sb.append(" -> ");
      }
      LoopMember m = members.get(i);
      sb.append(m.getPipeName());
      sb.append(m.getDirection() > 0 ? "(+)" : "(-)");
    }
    sb.append("]");
    return sb.toString();
  }
}
