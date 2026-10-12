package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.CouplingResult;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.WellResult;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Process-system adapter for one live {@link FieldWellNetworkCoupler}.
 *
 * <p>
 * The adapter owns no additional graph or pressure-flow calculation. Its {@link #run(UUID)} method executes the bound
 * live wells against the canonical {@link LoopedPipeNetwork}, exposes the network's stable sink streams to downstream
 * separators and compressors, and reports the process unit unsolved unless the complete coupled result is converged and
 * finite. {@link neqsim.process.automation.ProcessAutomation} exposes the wrapped network's existing source, choke,
 * route-availability, regulator, compressor and pump addresses through this unit name.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class FieldWellNetworkProcessUnit extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Existing live well/network coupling calculation. */
  private final FieldWellNetworkCoupler coupler;

  /** Exclusive route selectors over existing canonical network edges. */
  private final Map<String, ExclusiveRouteSelector> routeSelectors = new LinkedHashMap<String, ExclusiveRouteSelector>();

  /** Immutable definition of one exclusive route selector. */
  private static final class ExclusiveRouteSelector implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Existing network edges in discrete selection order. */
    private final List<String> edgeNames;

    /** Engineering source for treating the edges as mutually exclusive routes. */
    private final String provenance;

    /**
     * Create one immutable selector definition.
     *
     * @param edgeNames existing network edges in selection order
     * @param provenance engineering source for the exclusive line-up
     */
    private ExclusiveRouteSelector(List<String> edgeNames, String provenance) {
      this.edgeNames = Collections.unmodifiableList(new ArrayList<String>(edgeNames));
      this.provenance = provenance;
    }
  }

  /**
   * Create a process-system unit over one existing coupler.
   *
   * @param name process equipment name used by automation addresses
   * @param coupler configured live well/network coupler
   */
  public FieldWellNetworkProcessUnit(String name, FieldWellNetworkCoupler coupler) {
    super(requireName(name));
    if (coupler == null) {
      throw new IllegalArgumentException("Field well/network coupler cannot be null");
    }
    this.coupler = coupler;
  }

  /**
   * Get the wrapped coupling calculation.
   *
   * @return configured live well/network coupler
   */
  public FieldWellNetworkCoupler getCoupler() {
    return coupler;
  }

  /**
   * Get the canonical field topology.
   *
   * @return topology used by the wrapped coupler
   */
  public FieldNetworkTopology getTopology() {
    return coupler.getTopology();
  }

  /**
   * Get the sole hydraulic network.
   *
   * @return canonical hydraulic graph used by the wrapped coupler
   */
  public LoopedPipeNetwork getHydraulicNetwork() {
    return getTopology().getHydraulicNetwork();
  }

  /**
   * Get the most recent coupled solve evidence.
   *
   * @return immutable result, or {@code null} before the first run
   */
  public CouplingResult getLastCouplingResult() {
    return coupler.getLastResult();
  }

  /** {@inheritDoc} */
  @Override
  public ProcessEquipmentInterface copy() {
    FieldWellNetworkProcessUnit copy = new FieldWellNetworkProcessUnit(getName(), coupler.copy());
    for (Map.Entry<String, ExclusiveRouteSelector> entry : routeSelectors.entrySet()) {
      copy.registerExclusiveRouteSelector(entry.getKey(), entry.getValue().edgeNames, entry.getValue().provenance);
    }
    return copy;
  }

  /**
   * Register one discrete, mutually exclusive route selector over existing network edges.
   *
   * <p>
   * Exactly one declared edge must currently have availability {@code 1.0}; every other declared edge must have
   * availability {@code 0.0}. The selector only changes those existing edge availabilities. It does not create a second
   * topology or alter any hydraulic model.
   * </p>
   *
   * @param selectorName stable selector identity used by process automation
   * @param edgeNames existing alternative route edges in selection order
   * @param provenance engineering source for the mutually exclusive line-up
   * @return this process unit for chaining
   */
  public FieldWellNetworkProcessUnit registerExclusiveRouteSelector(String selectorName, List<String> edgeNames,
      String provenance) {
    String normalizedName = requireName(selectorName);
    String normalizedProvenance = requireName(provenance);
    if (routeSelectors.containsKey(normalizedName)) {
      throw new IllegalArgumentException("Exclusive route selector is already registered: " + normalizedName);
    }
    if (edgeNames == null || edgeNames.size() < 2) {
      throw new IllegalArgumentException("Exclusive route selector requires at least two route edges");
    }
    List<String> validatedEdges = new ArrayList<String>();
    for (String edgeName : edgeNames) {
      String normalizedEdge = requireName(edgeName);
      if (getHydraulicNetwork().getPipe(normalizedEdge) == null) {
        throw new IllegalArgumentException("Exclusive route edge does not exist: " + normalizedEdge);
      }
      if (validatedEdges.contains(normalizedEdge)) {
        throw new IllegalArgumentException("Exclusive route selector contains duplicate edge: " + normalizedEdge);
      }
      validatedEdges.add(normalizedEdge);
    }
    ExclusiveRouteSelector selector = new ExclusiveRouteSelector(validatedEdges, normalizedProvenance);
    requireSelectedRoute(normalizedName, selector);
    routeSelectors.put(normalizedName, selector);
    return this;
  }

  /**
   * Get exclusive route-selector names in registration order.
   *
   * @return immutable selector-name list
   */
  public List<String> getExclusiveRouteSelectorNames() {
    return Collections.unmodifiableList(new ArrayList<String>(routeSelectors.keySet()));
  }

  /**
   * Get the declared route-edge names for one selector.
   *
   * @param selectorName registered selector identity
   * @return immutable route-edge list in discrete selection order
   */
  public List<String> getExclusiveRouteEdges(String selectorName) {
    return getRouteSelector(selectorName).edgeNames;
  }

  /**
   * Get the provenance for one selector.
   *
   * @param selectorName registered selector identity
   * @return engineering source for the exclusive line-up
   */
  public String getExclusiveRouteProvenance(String selectorName) {
    return getRouteSelector(selectorName).provenance;
  }

  /**
   * Get the selected route index.
   *
   * @param selectorName registered selector identity
   * @return zero-based selected route index
   * @throws IllegalStateException when the route availabilities are not exactly one-hot
   */
  public int getExclusiveRouteSelection(String selectorName) {
    return requireSelectedRoute(selectorName, getRouteSelector(selectorName));
  }

  /**
   * Atomically select one existing route and make every alternative unavailable.
   *
   * <p>
   * The candidate must be an exact zero-based integer route index. All affected edge availabilities are captured before
   * mutation, written as an exact one-hot line-up, and verified. Any write/read-back failure restores every captured
   * availability before the exception is propagated. A successful change marks this process unit unsolved; the
   * surrounding process model must rerun the live well/network coupling before using the candidate.
   * </p>
   *
   * @param selectorName registered selector identity
   * @param selectedRoute zero-based route index
   */
  public void setExclusiveRouteSelection(String selectorName, double selectedRoute) {
    ExclusiveRouteSelector selector = getRouteSelector(selectorName);
    if (!Double.isFinite(selectedRoute) || selectedRoute != Math.rint(selectedRoute) || selectedRoute < 0.0
        || selectedRoute >= selector.edgeNames.size()) {
      throw new IllegalArgumentException("Exclusive route selection must be a declared integer route index");
    }
    int selectedIndex = (int) selectedRoute;
    double[] baseline = new double[selector.edgeNames.size()];
    for (int index = 0; index < selector.edgeNames.size(); index++) {
      baseline[index] = getHydraulicNetwork().getPipe(selector.edgeNames.get(index)).getAvailability();
    }
    try {
      for (int index = 0; index < selector.edgeNames.size(); index++) {
        getHydraulicNetwork().getPipe(selector.edgeNames.get(index))
            .setAvailability(index == selectedIndex ? 1.0 : 0.0);
      }
      if (requireSelectedRoute(selectorName, selector) != selectedIndex) {
        throw new IllegalStateException("Exclusive route selection read-back did not match the requested route");
      }
      isSolved = false;
    } catch (RuntimeException exception) {
      for (int index = 0; index < selector.edgeNames.size(); index++) {
        getHydraulicNetwork().getPipe(selector.edgeNames.get(index)).setAvailability(baseline[index]);
      }
      throw exception;
    }
  }

  /**
   * Resolve one registered selector.
   *
   * @param selectorName registered selector identity
   * @return immutable selector definition
   */
  private ExclusiveRouteSelector getRouteSelector(String selectorName) {
    ExclusiveRouteSelector selector = routeSelectors.get(selectorName);
    if (selector == null) {
      throw new IllegalArgumentException("Unknown exclusive route selector: " + selectorName);
    }
    return selector;
  }

  /**
   * Require and return an exact one-hot route selection.
   *
   * @param selectorName selector identity used in diagnostics
   * @param selector immutable selector definition
   * @return zero-based selected route index
   */
  private int requireSelectedRoute(String selectorName, ExclusiveRouteSelector selector) {
    int selectedIndex = -1;
    for (int index = 0; index < selector.edgeNames.size(); index++) {
      double availability = getHydraulicNetwork().getPipe(selector.edgeNames.get(index)).getAvailability();
      if (availability == 1.0) {
        if (selectedIndex >= 0) {
          throw new IllegalStateException("Exclusive route selector has multiple available routes: " + selectorName);
        }
        selectedIndex = index;
      } else if (availability != 0.0) {
        throw new IllegalStateException(
            "Exclusive route selector requires exact zero-or-one availability: " + selectorName);
      }
    }
    if (selectedIndex < 0) {
      throw new IllegalStateException("Exclusive route selector has no available route: " + selectorName);
    }
    return selectedIndex;
  }

  /**
   * Get one stable solved sink stream for downstream processing.
   *
   * @param sinkNodeName canonical sink-node identity
   * @return sink stream, or {@code null} before the network has produced it
   */
  public StreamInterface getOutletStream(String sinkNodeName) {
    return getHydraulicNetwork().getOutletStream(sinkNodeName);
  }

  /**
   * Get the first solved sink stream.
   *
   * @return first sink stream, or {@code null} before the network has produced one
   */
  public StreamInterface getOutletStream() {
    return getHydraulicNetwork().getOutletStream();
  }

  /**
   * Get all stable solved sink streams.
   *
   * @return immutable sink-stream list
   */
  @Override
  public List<StreamInterface> getOutletStreams() {
    return getHydraulicNetwork().getOutletStreams();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    CouplingResult result = coupler.run(id);
    isSolved = isCompleteFiniteResult(result);
    setCalculationIdentifier(id);
  }

  /**
   * Check the complete coupled result before allowing the surrounding process model to converge.
   *
   * @param result candidate coupling evidence
   * @return true only for converged finite network and per-well evidence
   */
  private boolean isCompleteFiniteResult(CouplingResult result) {
    if (result == null || !result.isConverged() || !Double.isFinite(result.getMaximumRateResidualKgS())
        || !Double.isFinite(result.getMaximumPressureResidualBar()) || !Double.isFinite(result.getNetworkResidualPa())
        || !Double.isFinite(result.getMassBalanceResidualKgS())) {
      return false;
    }
    for (WellResult well : result.getWellResults()) {
      if (!Double.isFinite(well.getNodePressureBara()) || !Double.isFinite(well.getTargetRateKgS())
          || !Double.isFinite(well.getAppliedRateKgS()) || !Double.isFinite(well.getRateResidualKgS())
          || !Double.isFinite(well.getInnerPressureResidualBar()) || !well.isInnerConverged()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validate and normalize a process-unit name.
   *
   * @param name requested name
   * @return trimmed non-empty name
   */
  private static String requireName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Field well/network process-unit name cannot be empty");
    }
    return name.trim();
  }
}
