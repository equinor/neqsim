package neqsim.process.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Linear proxy network assembled from frozen per-unit split factors.
 *
 * <p>
 * The network treats every process unit as a node and every stream as a directed edge. For a given component {@code k},
 * the routing matrix {@code A_k} encodes which fraction of each unit's inlet leaves toward every other unit, including
 * recycle / reflux loops. With the split factors frozen from one rigorous base run, the map "component-in &#8594;
 * component on each outlet" is linear, so the steady-state inlet flow vector {@code v_k} for any set of source
 * injections {@code x_k} satisfies:
 * </p>
 *
 * <p>
 * $$(I - A_k)\,v_k = B_k\,x_k,\qquad y_k = C_k\,v_k$$
 * </p>
 *
 * <p>
 * where {@code B_k} maps source injections onto their entry units and {@code C_k} selects custody outlets. Because each
 * unit conserves component mass (the split factors sum to one over outlets) and at least one outlet of the network is a
 * terminal custody stream, the loop gain is strictly below one and {@code (I - A_k)} is non-singular — guaranteeing a
 * unique solution by direct solve or convergent fixed-point iteration.
 * </p>
 *
 * <p>
 * This class only assembles the topology and the per-component {@code A_k} matrices plus the source-entry and
 * custody-outlet lookups. The actual linear solve lives in {@link LinearAllocationSolver}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class AllocationNetwork implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(AllocationNetwork.class);

  /** Frozen split-factor source. */
  private final RecoveryFactorExtractor factors;

  /** Node units in network order. */
  private final List<ProcessEquipmentInterface> units;

  /** Map from unit name to node index. */
  private final Map<String, Integer> unitIndex;

  /** Identity map from outlet stream to its producing (unitIndex, outletIndex). */
  private final Map<StreamInterface, int[]> streamProducer;

  /** Identity map from inlet stream to the list of consuming unit indices. */
  private final Map<StreamInterface, List<Integer>> streamConsumers;

  /** Number of nodes. */
  private final int n;

  /** Number of components on the master slate. */
  private final int numComponents;

  /**
   * Builds an allocation network from extracted recovery factors.
   *
   * @param factors the frozen split factors and master slate; must be non-null and already extracted
   */
  public AllocationNetwork(RecoveryFactorExtractor factors) {
    this.factors = factors;
    this.units = new ArrayList<>(factors.getNodeUnits());
    this.n = units.size();
    this.numComponents = factors.getComponentNames().size();
    this.unitIndex = new LinkedHashMap<>();
    this.streamProducer = new IdentityHashMap<>();
    this.streamConsumers = new IdentityHashMap<>();
    buildTopology();
  }

  /**
   * Indexes the nodes and builds identity-based producer / consumer lookups for every stream.
   */
  private void buildTopology() {
    for (int u = 0; u < n; u++) {
      unitIndex.put(units.get(u).getName(), u);
    }
    for (int u = 0; u < n; u++) {
      UnitSplit split = factors.getUnitSplits().get(units.get(u).getName());
      List<StreamInterface> outlets = split.getOutletStreams();
      for (int s = 0; s < outlets.size(); s++) {
        streamProducer.put(outlets.get(s), new int[] { u, s });
      }
      for (StreamInterface in : split.getInletStreams()) {
        List<Integer> consumers = streamConsumers.get(in);
        if (consumers == null) {
          consumers = new ArrayList<>();
          streamConsumers.put(in, consumers);
        }
        if (!consumers.contains(u)) {
          consumers.add(u);
        }
      }
    }
    logger.info("Allocation network: {} nodes, {} components", n, numComponents);
  }

  /**
   * Builds the routing matrix {@code A_k} for a single component.
   *
   * <p>
   * {@code A[u][w]} is the fraction of unit {@code w}'s component-{@code k} inlet that is routed to unit {@code u}
   * through the stream(s) connecting them. Terminal outlet streams (custody / product streams not consumed by any node)
   * contribute no row, which is precisely what makes the loop gain less than one.
   * </p>
   *
   * @param componentIndex zero-based component index on the master slate
   * @return an {@code n x n} routing matrix
   */
  public double[][] buildRoutingMatrix(int componentIndex) {
    double[][] a = new double[n][n];
    for (int w = 0; w < n; w++) {
      UnitSplit split = factors.getUnitSplits().get(units.get(w).getName());
      List<StreamInterface> outlets = split.getOutletStreams();
      for (int s = 0; s < outlets.size(); s++) {
        List<Integer> consumers = streamConsumers.get(outlets.get(s));
        if (consumers == null) {
          continue; // terminal / custody outlet — leaves the network
        }
        double f = split.getFactor(s, componentIndex);
        for (Integer u : consumers) {
          a[u][w] += f;
        }
      }
    }
    return a;
  }

  /**
   * Finds the node index that the given source feed stream enters. A source stream feeds exactly one mixing node in a
   * well-formed flowsheet; if it feeds several, the first is returned.
   *
   * @param feedStream the source feed stream; must be non-null
   * @return the entry node index, or {@code -1} if the stream is not connected to any node inlet
   */
  public int findEntryUnit(StreamInterface feedStream) {
    List<Integer> consumers = streamConsumers.get(feedStream);
    if (consumers != null && !consumers.isEmpty()) {
      return consumers.get(0);
    }
    return -1;
  }

  /**
   * Locates the producing node and outlet index of a custody (product) stream.
   *
   * @param stream the custody stream; must be non-null
   * @return a two-element array {@code {unitIndex, outletIndex}}, or {@code null} if the stream is not produced by any
   * node
   */
  public int[] findProducer(StreamInterface stream) {
    return streamProducer.get(stream);
  }

  /**
   * Gets the split factor of a custody outlet for a component.
   *
   * @param producer a {@code {unitIndex, outletIndex}} pair from {@link #findProducer(StreamInterface)}
   * @param componentIndex zero-based component index on the master slate
   * @return the split factor {@code f_w(s,k)}
   */
  public double getCustodyFactor(int[] producer, int componentIndex) {
    UnitSplit split = factors.getUnitSplits().get(units.get(producer[0]).getName());
    return split.getFactor(producer[1], componentIndex);
  }

  /**
   * Finds candidate source streams: streams that feed a node but are not produced by any node (i.e. external process
   * feeds). Returned in first-seen node order.
   *
   * @return a list of terminal inlet (feed) streams
   */
  public List<StreamInterface> findSourceStreams() {
    List<StreamInterface> sources = new ArrayList<>();
    for (int u = 0; u < n; u++) {
      UnitSplit split = factors.getUnitSplits().get(units.get(u).getName());
      for (StreamInterface in : split.getInletStreams()) {
        if (!streamProducer.containsKey(in) && !containsIdentity(sources, in)) {
          sources.add(in);
        }
      }
    }
    return sources;
  }

  /**
   * Finds candidate custody streams: streams produced by a node but not consumed by any node (i.e. terminal product
   * streams). Returned in first-seen node order.
   *
   * @return a list of terminal outlet (product) streams
   */
  public List<StreamInterface> findCustodyStreams() {
    List<StreamInterface> custody = new ArrayList<>();
    for (int u = 0; u < n; u++) {
      UnitSplit split = factors.getUnitSplits().get(units.get(u).getName());
      for (StreamInterface out : split.getOutletStreams()) {
        if (!streamConsumers.containsKey(out) && !containsIdentity(custody, out)) {
          custody.add(out);
        }
      }
    }
    return custody;
  }

  /**
   * Checks whether a stream is already present in a list by object identity.
   *
   * @param list the list to search; must be non-null
   * @param stream the stream to look for; must be non-null
   * @return {@code true} if the same instance is already present
   */
  private static boolean containsIdentity(List<StreamInterface> list, StreamInterface stream) {
    for (StreamInterface s : list) {
      if (s == stream) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the number of nodes in the network.
   *
   * @return the node count
   */
  public int getNodeCount() {
    return n;
  }

  /**
   * Gets the number of components on the master slate.
   *
   * @return the component count
   */
  public int getComponentCount() {
    return numComponents;
  }

  /**
   * Gets the underlying recovery-factor extractor.
   *
   * @return the extractor
   */
  public RecoveryFactorExtractor getFactors() {
    return factors;
  }

  /**
   * Gets the node units in network order.
   *
   * @return an unmodifiable list of node units
   */
  public List<ProcessEquipmentInterface> getUnits() {
    return Collections.unmodifiableList(units);
  }
}
