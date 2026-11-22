package neqsim.process.diagnostics;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Meter;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Residual;

/**
 * Joint bounded-error balance on an oriented network. One shared meter has one signed error in both balances. Residual
 * paths solve the bounded-flow feasibility and the two candidate-rate extrema. Capacity and demand arithmetic uses the
 * decimal representations of the supplied doubles, with no absolute solver cutoff. Returned rate endpoints are rounded
 * to the nearest double. Upstream integration and data retain their input precision.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
final class JointBalanceScreen {
  /** Reference for comparing signed capacities. */
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  /** Index of each endpoint; index zero represents the exterior. */
  private final Map<String, Integer> nodes = new LinkedHashMap<String, Integer>();
  /** Measured balance terms indexed by inventory signal. */
  private final Map<String, Residual> residuals;
  /** Independent error budget per inventory including the external meters. */
  private final Map<String, Double> localBounds;
  /** Internal meters with positive uncertainty. */
  private final List<Meter> shared = new ArrayList<Meter>();

  /**
   * Constructs the joint error model from validated balances and meter endpoints.
   *
   * @param residuals signed rate residuals by inventory tag
   * @param meters directed flowmeters
   * @param localBounds absolute local budgets from inventory and external flows, in kg/s
   */
  JointBalanceScreen(Map<String, Residual> residuals, List<Meter> meters, Map<String, Double> localBounds) {
    this.residuals = residuals;
    this.localBounds = localBounds;
    nodes.put(null, 0);
    for (String tag : residuals.keySet()) {
      nodes.put(tag, nodes.size());
    }
    for (Meter meter : meters) {
      if (meter.getFrom() != null && meter.getTo() != null && meter.getErrorKgPerS() > 0.0) {
        shared.add(meter);
      }
    }
  }

  /**
   * Checks whether all observations admit a common set of healthy errors.
   *
   * @return true when the joint error budget explains the residual vector
   */
  boolean withinBounds() {
    for (Residual residual : residuals.values()) {
      if (Math.abs(residual.getResidualKgPerS()) > residual.getBoundKgPerS()) {
        return false;
      }
    }
    return shared.isEmpty() || interval(null, null, 0.0, 0.0) != null;
  }

  /**
   * Intersects the already screened marginal interval with joint endpoint constraints.
   *
   * @param from upstream tag or null
   * @param to downstream tag or null
   * @param lower finite marginal minimum in kg/s
   * @param upper finite marginal maximum in kg/s
   * @return joint inclusive interval in kg/s, or null when infeasible
   */
  double[] interval(String from, String to, double lower, double upper) {
    if (shared.isEmpty()) {
      return new double[] {lower, upper};
    }
    int count = nodes.size();
    final int source = count;
    final int sink = count + 1;
    Network net = new Network(count + 2);
    BigDecimal[] demand = new BigDecimal[count];
    Arrays.fill(demand, ZERO);
    for (Map.Entry<String, Residual> entry : residuals.entrySet()) {
      int node = nodes.get(entry.getKey());
      demand[node] = BigDecimal.valueOf(entry.getValue().getResidualKgPerS());
      demand[0] = demand[0].subtract(demand[node]);
      BigDecimal limit = BigDecimal.valueOf(localBounds.get(entry.getKey()));
      net.add(0, node, limit.negate(), limit, ZERO);
    }
    for (Meter meter : shared) {
      BigDecimal limit = BigDecimal.valueOf(meter.getErrorKgPerS());
      net.add(nodes.get(meter.getFrom()), nodes.get(meter.getTo()), limit.negate(), limit, ZERO);
    }

    int tail = nodes.get(from);
    int head = nodes.get(to);
    BigDecimal low = BigDecimal.valueOf(lower);
    BigDecimal high = BigDecimal.valueOf(upper);
    BigDecimal start = ZERO.max(low).min(high);
    Edge candidate = null;
    if (tail != head) {
      candidate = net.add(tail, head, low, high, start);
    }
    BigDecimal[] remaining = demand.clone();
    remaining[tail] = remaining[tail].add(start);
    remaining[head] = remaining[head].subtract(start);
    BigDecimal required = ZERO;
    for (int i = 0; i < count; i++) {
      if (remaining[i].signum() > 0) {
        net.add(i, sink, ZERO, remaining[i], ZERO);
        required = required.add(remaining[i]);
      } else if (remaining[i].signum() < 0) {
        net.add(source, i, ZERO, remaining[i].negate(), ZERO);
      }
    }
    if (net.augment(source, sink, required, count + 2, null).compareTo(required) != 0) {
      return null;
    }
    net.verify(demand);
    if (candidate == null || low.compareTo(high) == 0) {
      return new double[] {lower, upper};
    }
    BigDecimal decrease = net.augment(tail, head, candidate.flow.subtract(low), count, candidate);
    candidate.flow = candidate.flow.subtract(decrease);
    net.verify(demand);
    double minimum = candidate.flow.doubleValue();
    BigDecimal increase = net.augment(head, tail, high.subtract(candidate.flow), count, candidate);
    candidate.flow = candidate.flow.add(increase);
    net.verify(demand);
    return new double[] {minimum, candidate.flow.doubleValue()};
  }

  /** Oriented bounded edge, with a signed current flow from tail to head. */
  private static final class Edge {
    /** Node where a positive flow leaves. */
    private final int tail;
    /** Node where a positive flow arrives. */
    private final int head;
    /** Inclusive minimum signed rate. */
    private final BigDecimal low;
    /** Inclusive maximum signed rate. */
    private final BigDecimal high;
    /** Current signed flow on this edge. */
    private BigDecimal flow;

    /**
     * Initializes an edge at a feasible operating point.
     *
     * @param tail upstream node
     * @param head downstream node
     * @param low lower bound
     * @param high upper bound
     * @param start initial flow
     */
    private Edge(int tail, int head, BigDecimal low, BigDecimal high, BigDecimal start) {
      this.tail = tail;
      this.head = head;
      this.low = low;
      this.high = high;
      this.flow = start;
    }

    /**
     * Gets the endpoint on the other side of a node.
     *
     * @param node incident node
     * @return opposite node
     */
    private int other(int node) {
      return node == tail ? head : tail;
    }

    /**
     * Calculates the remaining capacity in the direction entering a node.
     *
     * @param destination node to enter
     * @return nonnegative capacity
     */
    private BigDecimal capacityInto(int destination) {
      return destination == head ? high.subtract(flow) : flow.subtract(low);
    }
  }

  /** Sparse graph supporting parallel physical meter connections. */
  private static final class Network {
    /** Edges incident on each node. */
    private final List<List<Edge>> adjacent = new ArrayList<List<Edge>>();

    /**
     * Allocates adjacency lists.
     *
     * @param count node count including external and auxiliary source/sink nodes
     */
    private Network(int count) {
      for (int node = 0; node < count; node++) {
        adjacent.add(new ArrayList<Edge>());
      }
    }

    /**
     * Adds an oriented bounded flow edge.
     *
     * @param tail upstream vertex
     * @param head downstream vertex
     * @param low minimum signed flow
     * @param high maximum signed flow
     * @param start initial signed flow
     * @return new edge
     */
    private Edge add(int tail, int head, BigDecimal low, BigDecimal high, BigDecimal start) {
      Edge edge = new Edge(tail, head, low, high, start);
      adjacent.get(tail).add(edge);
      adjacent.get(head).add(edge);
      return edge;
    }

    /**
     * Pushes flow along shortest residual paths (Edmonds-Karp) until the quota or a cut is reached.
     *
     * @param source start node
     * @param sink destination node
     * @param quota upper limit on additional flow
     * @param count number of active vertices, excluding auxiliary nodes when optimizing
     * @param exclude candidate arc fixed during return-path optimization, or null
     * @return amount pushed
     */
    private BigDecimal augment(int source, int sink, BigDecimal quota, int count, Edge exclude) {
      BigDecimal sent = ZERO;
      while (sent.compareTo(quota) < 0) {
        Edge[] parent = new Edge[count];
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(source);
        while (!queue.isEmpty() && parent[sink] == null) {
          int node = queue.remove();
          for (Edge edge : adjacent.get(node)) {
            int next = edge.other(node);
            if (edge != exclude && next < count && next != source && parent[next] == null
                && edge.capacityInto(next).signum() > 0) {
              parent[next] = edge;
              queue.add(next);
            }
          }
        }
        if (parent[sink] == null) {
          break;
        }
        BigDecimal step = quota.subtract(sent);
        for (int node = sink; node != source; node = parent[node].other(node)) {
          step = step.min(parent[node].capacityInto(node));
        }
        for (int node = sink; node != source; node = parent[node].other(node)) {
          Edge edge = parent[node];
          edge.flow = node == edge.head ? edge.flow.add(step) : edge.flow.subtract(step);
        }
        sent = sent.add(step);
      }
      return sent;
    }

    /**
     * Checks each physical edge budget and reconstructs each original signed balance.
     *
     * @param demand residuals represented by the physical network, including its external boundary
     * @throws IllegalStateException if the reconstructed solution violates any constraint
     */
    private void verify(BigDecimal[] demand) {
      for (int node = 0; node < demand.length; node++) {
        BigDecimal balance = ZERO;
        for (Edge edge : adjacent.get(node)) {
          if (edge.other(node) < demand.length) {
            if (edge.flow.compareTo(edge.low) < 0 || edge.flow.compareTo(edge.high) > 0) {
              throw new IllegalStateException("joint balance solution exceeds edge budget");
            }
            balance = node == edge.head ? balance.add(edge.flow) : balance.subtract(edge.flow);
          }
        }
        if (balance.compareTo(demand[node]) != 0) {
          throw new IllegalStateException("joint balance solution does not reconstruct residual");
        }
      }
    }
  }
}
