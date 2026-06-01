package neqsim.process.safety.risk.fta;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fault Tree Analysis (FTA) — top-event probability evaluator with optional common-cause β-factor.
 *
 * <p>
 * Build a tree of {@link FaultTreeNode} objects (basic events with probabilities and gates AND /
 * OR / VOTING(k-of-n)) and call {@link #topEventProbability(FaultTreeNode)} to recursively compute
 * the top-event probability.
 *
 * <p>
 * Common-cause failures (CCF) can be applied at OR / VOTING gates using the β-factor model
 * (IEC 61508 Annex F): P_total = (1 − β) · P_independent + β · P_basic.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>ISO 31010 §B.17 — Fault tree analysis</li>
 * <li>IEC 61508-6 Annex F — Common-cause failures</li>
 * <li>NUREG-0492 — Fault Tree Handbook</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class FaultTreeAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logical gate types for non-leaf nodes. */
  public enum GateType {
    /** Logical AND — top fails only if all inputs fail. */
    AND,
    /** Logical OR — top fails if any input fails. */
    OR,
    /** k-of-n voting — top fails if at least k inputs fail. */
    VOTING
  }

  /**
   * Compute top-event probability using gate-specific combinatorics, with rare-event approximation
   * for OR gates (P ≈ Σ p_i bounded ≤ 1).
   *
   * @param node root fault tree node
   * @return probability in [0, 1]
   */
  public double topEventProbability(FaultTreeNode node) {
    if (node.isBasic()) {
      return node.basicProbability;
    }
    List<Double> childProbs = new ArrayList<>();
    for (FaultTreeNode child : node.children) {
      childProbs.add(topEventProbability(child));
    }
    switch (node.gate) {
      case AND:
        double pAnd = 1.0;
        for (Double p : childProbs) {
          pAnd *= p;
        }
        return pAnd;
      case OR:
        double pOr = 1.0;
        for (Double p : childProbs) {
          pOr *= (1.0 - p);
        }
        double pInd = 1.0 - pOr;
        // Apply β-factor common-cause if configured
        if (node.betaCCF > 0.0) {
          double pBasicMax = 0.0;
          for (Double p : childProbs) {
            pBasicMax = Math.max(pBasicMax, p);
          }
          return (1.0 - node.betaCCF) * pInd + node.betaCCF * pBasicMax;
        }
        return pInd;
      case VOTING:
        return votingGate(childProbs, node.kOfN);
      default:
        throw new IllegalStateException("Unknown gate: " + node.gate);
    }
  }

  /**
   * Probability of at least k of n independent failures (Poisson-binomial sum).
   *
   * @param probs list of n failure probabilities
   * @param k votes required
   * @return P(at least k failures)
   */
  private double votingGate(List<Double> probs, int k) {
    int n = probs.size();
    double total = 0.0;
    for (int mask = 0; mask < (1 << n); mask++) {
      int count = Integer.bitCount(mask);
      if (count < k) {
        continue;
      }
      double p = 1.0;
      for (int i = 0; i < n; i++) {
        boolean fail = ((mask >> i) & 1) == 1;
        p *= fail ? probs.get(i) : (1.0 - probs.get(i));
      }
      total += p;
    }
    return total;
  }

  /**
   * Enumerate minimal cut sets (sets of basic events whose simultaneous failure causes the top).
   * Implementation: brute-force enumeration up to a maximum cardinality (kept small for tractability).
   *
   * @param root root fault tree node
   * @param maxCardinality maximum cut-set size to enumerate
   * @return set of cut sets, each as a sorted list of basic-event names
   */
  public Set<List<String>> minimalCutSets(FaultTreeNode root, int maxCardinality) {
    List<String> basics = new ArrayList<>();
    collectBasicNames(root, basics);
    Set<List<String>> cutSets = new HashSet<>();
    int n = basics.size();
    for (int mask = 1; mask < (1 << n); mask++) {
      if (Integer.bitCount(mask) > maxCardinality) {
        continue;
      }
      // Build temporary root where the selected basics are TRUE, others FALSE
      Set<String> selected = new HashSet<>();
      for (int i = 0; i < n; i++) {
        if (((mask >> i) & 1) == 1) {
          selected.add(basics.get(i));
        }
      }
      if (evaluateLogical(root, selected)) {
        List<String> sorted = new ArrayList<>(selected);
        java.util.Collections.sort(sorted);
        // Drop supersets of existing cut sets
        boolean isSuper = false;
        for (List<String> existing : cutSets) {
          if (sorted.containsAll(existing)) {
            isSuper = true;
            break;
          }
        }
        if (!isSuper) {
          cutSets.add(sorted);
        }
      }
    }
    return cutSets;
  }

  private void collectBasicNames(FaultTreeNode node, List<String> out) {
    if (node.isBasic()) {
      if (!out.contains(node.name)) {
        out.add(node.name);
      }
      return;
    }
    for (FaultTreeNode c : node.children) {
      collectBasicNames(c, out);
    }
  }

  private boolean evaluateLogical(FaultTreeNode node, Set<String> failedSet) {
    if (node.isBasic()) {
      return failedSet.contains(node.name);
    }
    int trueCount = 0;
    for (FaultTreeNode c : node.children) {
      if (evaluateLogical(c, failedSet)) {
        trueCount++;
      }
    }
    switch (node.gate) {
      case AND: return trueCount == node.children.size();
      case OR: return trueCount >= 1;
      case VOTING: return trueCount >= node.kOfN;
      default: return false;
    }
  }
}
