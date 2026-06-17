package neqsim.process.safety.risk.fta;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Node in a fault tree — either a basic event (with a probability) or a gate (AND / OR / VOTING)
 * with child nodes.
 *
 * <p>
 * Build trees programmatically via static factory methods and pass the root to
 * {@link FaultTreeAnalyzer#topEventProbability(FaultTreeNode)}.
 *
 * @author ESOL
 * @version 1.0
 */
public class FaultTreeNode implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Node label (basic event ID for leaves; description for gates). */
  public final String name;
  /** Gate type or {@code null} for basic events. */
  public final FaultTreeAnalyzer.GateType gate;
  /** Basic-event failure probability (only used if gate == null). */
  public final double basicProbability;
  /** Child nodes for non-leaf nodes. */
  public final List<FaultTreeNode> children = new ArrayList<>();
  /** k for k-of-n voting gates. */
  public int kOfN;
  /** β-factor for common-cause-failure correction at OR gates (0 = independent). */
  public double betaCCF;

  private FaultTreeNode(String name, FaultTreeAnalyzer.GateType gate, double basicProbability) {
    this.name = name;
    this.gate = gate;
    this.basicProbability = basicProbability;
  }

  /**
   * Create a basic event leaf.
   *
   * @param name basic event name / ID
   * @param probability failure probability
   * @return new basic-event node
   */
  public static FaultTreeNode basic(String name, double probability) {
    if (probability < 0.0 || probability > 1.0) {
      throw new IllegalArgumentException("probability must be in [0,1]");
    }
    return new FaultTreeNode(name, null, probability);
  }

  /**
   * Create an AND gate (top fails only if all children fail).
   *
   * @param name gate description
   * @param children child nodes
   * @return new AND-gate node
   */
  public static FaultTreeNode and(String name, FaultTreeNode... children) {
    FaultTreeNode g = new FaultTreeNode(name, FaultTreeAnalyzer.GateType.AND, 0.0);
    for (FaultTreeNode c : children) {
      g.children.add(c);
    }
    return g;
  }

  /**
   * Create an OR gate (top fails if any child fails).
   *
   * @param name gate description
   * @param children child nodes
   * @return new OR-gate node
   */
  public static FaultTreeNode or(String name, FaultTreeNode... children) {
    FaultTreeNode g = new FaultTreeNode(name, FaultTreeAnalyzer.GateType.OR, 0.0);
    for (FaultTreeNode c : children) {
      g.children.add(c);
    }
    return g;
  }

  /**
   * Create a k-of-n voting gate.
   *
   * @param name gate description
   * @param k number of failed inputs that triggers gate
   * @param children child nodes (n)
   * @return new voting-gate node
   */
  public static FaultTreeNode voting(String name, int k, FaultTreeNode... children) {
    FaultTreeNode g = new FaultTreeNode(name, FaultTreeAnalyzer.GateType.VOTING, 0.0);
    for (FaultTreeNode c : children) {
      g.children.add(c);
    }
    g.kOfN = k;
    return g;
  }

  /**
   * Apply a β-factor common-cause failure correction to this gate (only valid for OR / VOTING).
   *
   * @param beta β factor in [0, 1]
   * @return this node for chaining
   */
  public FaultTreeNode withCCF(double beta) {
    if (beta < 0.0 || beta > 1.0) {
      throw new IllegalArgumentException("β must be in [0,1]");
    }
    this.betaCCF = beta;
    return this;
  }

  /**
   * Whether this node is a basic event (leaf).
   *
   * @return true for basic events
   */
  public boolean isBasic() {
    return gate == null;
  }
}
