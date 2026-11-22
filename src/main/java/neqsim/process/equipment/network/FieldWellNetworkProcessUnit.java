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

  /** Route-configuration selectors over existing canonical network edges. */
  private final Map<String, RouteConfigurationSelector> routeSelectors = new LinkedHashMap<String, RouteConfigurationSelector>();

  /**
   * Immutable named line-up of available edges in one canonical field network.
   *
   * <p>
   * Every listed edge is assigned exact availability {@code 1.0}. Other edges controlled by the same selector are
   * assigned exact availability {@code 0.0}. Edges outside the selector remain unchanged.
   * </p>
   */
  public static final class RouteConfiguration implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Stable configuration name. */
    private final String name;

    /** Existing edges that are available in this configuration. */
    private final List<String> availableEdgeNames;

    /**
     * Create one named exact route configuration.
     *
     * @param name stable configuration name
     * @param availableEdgeNames existing edges that are fully available in this configuration
     */
    public RouteConfiguration(String name, List<String> availableEdgeNames) {
      this.name = requireName(name);
      if (availableEdgeNames == null || availableEdgeNames.isEmpty()) {
        throw new IllegalArgumentException("Route configuration requires at least one available edge");
      }
      List<String> validatedEdges = new ArrayList<String>();
      for (String edgeName : availableEdgeNames) {
        String normalizedEdge = requireName(edgeName);
        if (validatedEdges.contains(normalizedEdge)) {
          throw new IllegalArgumentException("Route configuration contains duplicate edge: " + normalizedEdge);
        }
        validatedEdges.add(normalizedEdge);
      }
      this.availableEdgeNames = Collections.unmodifiableList(validatedEdges);
    }

    /**
     * Get the stable configuration name.
     *
     * @return configuration name
     */
    public String getName() {
      return name;
    }

    /**
     * Get the edges assigned exact availability one by this configuration.
     *
     * @return immutable available-edge list
     */
    public List<String> getAvailableEdgeNames() {
      return availableEdgeNames;
    }
  }

  /** Immutable definition of one route-configuration selector. */
  private static final class RouteConfigurationSelector implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Configurations in discrete selection order. */
    private final List<RouteConfiguration> configurations;

    /** Union of every edge controlled by the configurations. */
    private final List<String> controlledEdgeNames;

    /** Engineering source for treating the edges as mutually exclusive routes. */
    private final String provenance;

    /** Whether this selector was registered through the compatible one-edge route API. */
    private final boolean exclusiveSingleEdge;

    /**
     * Create one immutable selector definition.
     *
     * @param configurations configurations in selection order
     * @param controlledEdgeNames union of controlled edges
     * @param provenance engineering source for the line-ups
     * @param exclusiveSingleEdge true for the compatible one-edge route API
     */
    private RouteConfigurationSelector(List<RouteConfiguration> configurations, List<String> controlledEdgeNames,
        String provenance, boolean exclusiveSingleEdge) {
      this.configurations = Collections.unmodifiableList(new ArrayList<RouteConfiguration>(configurations));
      this.controlledEdgeNames = Collections.unmodifiableList(new ArrayList<String>(controlledEdgeNames));
      this.provenance = provenance;
      this.exclusiveSingleEdge = exclusiveSingleEdge;
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
   * Get the solved shaft-power demand of canonical pumps and compressors inside the field network.
   *
   * @param unit requested power unit
   * @return total network pump and compressor shaft power
   */
  public double getPower(String unit) {
    double power = getHydraulicNetwork().getPower(unit);
    return power == 0.0 || solved() ? power : Double.NaN;
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
    for (Map.Entry<String, RouteConfigurationSelector> entry : routeSelectors.entrySet()) {
      RouteConfigurationSelector selector = entry.getValue();
      if (selector.exclusiveSingleEdge) {
        copy.registerExclusiveRouteSelector(entry.getKey(), selector.controlledEdgeNames, selector.provenance);
      } else {
        copy.registerRouteConfigurationSelector(entry.getKey(), selector.configurations, selector.provenance);
      }
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
    if (edgeNames == null || edgeNames.size() < 2) {
      throw new IllegalArgumentException("Exclusive route selector requires at least two route edges");
    }
    List<RouteConfiguration> configurations = new ArrayList<RouteConfiguration>();
    for (String edgeName : edgeNames) {
      String normalizedEdge = requireName(edgeName);
      configurations.add(new RouteConfiguration(normalizedEdge, Collections.singletonList(normalizedEdge)));
    }
    registerRouteConfigurationSelector(selectorName, configurations, provenance, true);
    return this;
  }

  /**
   * Register named, discrete multi-edge route configurations over the existing canonical network.
   *
   * <p>
   * A configuration may represent a direct path, brownfield reroute, daisy-chain line-up, or redundant loop by listing
   * every controlled edge that must have availability {@code 1.0}. Every other edge appearing in any option is set to
   * {@code 0.0}. Registration requires the current edge state to match exactly one option and rejects overlap with
   * another selector. The selector owns no topology or hydraulic model.
   * </p>
   *
   * @param selectorName stable selector identity used by process automation
   * @param configurations at least two distinct named edge line-ups in selection order
   * @param provenance engineering source for the route line-ups
   * @return this process unit for chaining
   */
  public FieldWellNetworkProcessUnit registerRouteConfigurationSelector(String selectorName,
      List<RouteConfiguration> configurations, String provenance) {
    registerRouteConfigurationSelector(selectorName, configurations, provenance, false);
    return this;
  }

  /**
   * Register and validate one selector, preserving the compatible one-edge route surface when requested.
   *
   * @param selectorName stable selector identity
   * @param configurations candidate line-ups in selection order
   * @param provenance engineering source for the line-ups
   * @param exclusiveSingleEdge true when registered by the compatible one-edge route API
   */
  private void registerRouteConfigurationSelector(String selectorName, List<RouteConfiguration> configurations,
      String provenance, boolean exclusiveSingleEdge) {
    String normalizedName = requireName(selectorName);
    String normalizedProvenance = requireName(provenance);
    if (routeSelectors.containsKey(normalizedName)) {
      throw new IllegalArgumentException("Route configuration selector is already registered: " + normalizedName);
    }
    if (configurations == null || configurations.size() < 2) {
      throw new IllegalArgumentException("Route configuration selector requires at least two configurations");
    }

    List<RouteConfiguration> validatedConfigurations = new ArrayList<RouteConfiguration>();
    List<String> configurationNames = new ArrayList<String>();
    List<List<String>> signatures = new ArrayList<List<String>>();
    List<String> controlledEdges = new ArrayList<String>();
    for (RouteConfiguration configuration : configurations) {
      if (configuration == null) {
        throw new IllegalArgumentException("Route configurations cannot contain null");
      }
      if (configurationNames.contains(configuration.getName())) {
        throw new IllegalArgumentException("Duplicate route configuration name: " + configuration.getName());
      }
      configurationNames.add(configuration.getName());
      List<String> sortedActiveEdges = new ArrayList<String>(configuration.getAvailableEdgeNames());
      Collections.sort(sortedActiveEdges);
      if (signatures.contains(sortedActiveEdges)) {
        throw new IllegalArgumentException("Route configurations must define distinct edge line-ups");
      }
      signatures.add(sortedActiveEdges);
      for (String edgeName : configuration.getAvailableEdgeNames()) {
        if (getHydraulicNetwork().getPipe(edgeName) == null) {
          throw new IllegalArgumentException("Route configuration edge does not exist: " + edgeName);
        }
        if (!controlledEdges.contains(edgeName)) {
          controlledEdges.add(edgeName);
        }
      }
      validatedConfigurations.add(configuration);
    }
    for (Map.Entry<String, RouteConfigurationSelector> entry : routeSelectors.entrySet()) {
      for (String edgeName : controlledEdges) {
        if (entry.getValue().controlledEdgeNames.contains(edgeName)) {
          throw new IllegalArgumentException(
              "Route configuration edge '" + edgeName + "' is already controlled by selector '" + entry.getKey() + "'");
        }
      }
    }
    RouteConfigurationSelector selector = new RouteConfigurationSelector(validatedConfigurations, controlledEdges,
        normalizedProvenance, exclusiveSingleEdge);
    requireSelectedConfiguration(normalizedName, selector);
    routeSelectors.put(normalizedName, selector);
  }

  /**
   * Get exclusive route-selector names in registration order.
   *
   * @return immutable selector-name list
   */
  public List<String> getExclusiveRouteSelectorNames() {
    List<String> names = new ArrayList<String>();
    for (Map.Entry<String, RouteConfigurationSelector> entry : routeSelectors.entrySet()) {
      if (entry.getValue().exclusiveSingleEdge) {
        names.add(entry.getKey());
      }
    }
    return Collections.unmodifiableList(names);
  }

  /**
   * Get the declared route-edge names for one selector.
   *
   * @param selectorName registered selector identity
   * @return immutable route-edge list in discrete selection order
   */
  public List<String> getExclusiveRouteEdges(String selectorName) {
    RouteConfigurationSelector selector = getRouteSelector(selectorName);
    if (!selector.exclusiveSingleEdge) {
      throw new IllegalArgumentException("Selector is not an exclusive one-edge route selector: " + selectorName);
    }
    return selector.controlledEdgeNames;
  }

  /**
   * Get the provenance for one selector.
   *
   * @param selectorName registered selector identity
   * @return engineering source for the exclusive line-up
   */
  public String getExclusiveRouteProvenance(String selectorName) {
    RouteConfigurationSelector selector = getRouteSelector(selectorName);
    if (!selector.exclusiveSingleEdge) {
      throw new IllegalArgumentException("Selector is not an exclusive one-edge route selector: " + selectorName);
    }
    return selector.provenance;
  }

  /**
   * Get the selected route index.
   *
   * @param selectorName registered selector identity
   * @return zero-based selected route index
   * @throws IllegalStateException when the route availabilities are not exactly one-hot
   */
  public int getExclusiveRouteSelection(String selectorName) {
    RouteConfigurationSelector selector = getRouteSelector(selectorName);
    if (!selector.exclusiveSingleEdge) {
      throw new IllegalArgumentException("Selector is not an exclusive one-edge route selector: " + selectorName);
    }
    return requireSelectedConfiguration(selectorName, selector);
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
    RouteConfigurationSelector selector = getRouteSelector(selectorName);
    if (!selector.exclusiveSingleEdge) {
      throw new IllegalArgumentException("Selector is not an exclusive one-edge route selector: " + selectorName);
    }
    setSelectedConfiguration(selectorName, selector, selectedRoute);
  }

  /**
   * Get multi-edge route-configuration selector names in registration order.
   *
   * @return immutable selector-name list
   */
  public List<String> getRouteConfigurationSelectorNames() {
    List<String> names = new ArrayList<String>();
    for (Map.Entry<String, RouteConfigurationSelector> entry : routeSelectors.entrySet()) {
      if (!entry.getValue().exclusiveSingleEdge) {
        names.add(entry.getKey());
      }
    }
    return Collections.unmodifiableList(names);
  }

  /**
   * Get configuration names for one multi-edge selector.
   *
   * @param selectorName registered selector identity
   * @return immutable configuration-name list in discrete selection order
   */
  public List<String> getRouteConfigurationNames(String selectorName) {
    RouteConfigurationSelector selector = getConfigurationSelector(selectorName);
    List<String> names = new ArrayList<String>();
    for (RouteConfiguration configuration : selector.configurations) {
      names.add(configuration.getName());
    }
    return Collections.unmodifiableList(names);
  }

  /**
   * Get the immutable configuration definitions for one multi-edge selector.
   *
   * @param selectorName registered selector identity
   * @return immutable configurations in discrete selection order
   */
  public List<RouteConfiguration> getRouteConfigurations(String selectorName) {
    return getConfigurationSelector(selectorName).configurations;
  }

  /**
   * Get every canonical edge controlled by one multi-edge selector.
   *
   * @param selectorName registered selector identity
   * @return immutable controlled-edge list
   */
  public List<String> getRouteConfigurationEdges(String selectorName) {
    return getConfigurationSelector(selectorName).controlledEdgeNames;
  }

  /**
   * Get provenance for one multi-edge selector.
   *
   * @param selectorName registered selector identity
   * @return engineering source for the line-ups
   */
  public String getRouteConfigurationProvenance(String selectorName) {
    return getConfigurationSelector(selectorName).provenance;
  }

  /**
   * Get the selected multi-edge route configuration.
   *
   * @param selectorName registered selector identity
   * @return zero-based selected configuration index
   */
  public int getRouteConfigurationSelection(String selectorName) {
    RouteConfigurationSelector selector = getConfigurationSelector(selectorName);
    return requireSelectedConfiguration(selectorName, selector);
  }

  /**
   * Atomically apply one named multi-edge route configuration.
   *
   * @param selectorName registered selector identity
   * @param selectedConfiguration zero-based configuration index
   */
  public void setRouteConfigurationSelection(String selectorName, double selectedConfiguration) {
    RouteConfigurationSelector selector = getConfigurationSelector(selectorName);
    setSelectedConfiguration(selectorName, selector, selectedConfiguration);
  }

  /**
   * Resolve one registered selector.
   *
   * @param selectorName registered selector identity
   * @return immutable selector definition
   */
  private RouteConfigurationSelector getRouteSelector(String selectorName) {
    RouteConfigurationSelector selector = routeSelectors.get(selectorName);
    if (selector == null) {
      throw new IllegalArgumentException("Unknown route configuration selector: " + selectorName);
    }
    return selector;
  }

  /**
   * Resolve one registered multi-edge configuration selector.
   *
   * @param selectorName registered selector identity
   * @return immutable multi-edge selector definition
   */
  private RouteConfigurationSelector getConfigurationSelector(String selectorName) {
    RouteConfigurationSelector selector = getRouteSelector(selectorName);
    if (selector.exclusiveSingleEdge) {
      throw new IllegalArgumentException("Selector is not a multi-edge route configuration: " + selectorName);
    }
    return selector;
  }

  /**
   * Require and return one exactly matching declared route configuration.
   *
   * @param selectorName selector identity used in diagnostics
   * @param selector immutable selector definition
   * @return zero-based selected configuration index
   */
  private int requireSelectedConfiguration(String selectorName, RouteConfigurationSelector selector) {
    int selectedIndex = -1;
    for (String edgeName : selector.controlledEdgeNames) {
      double availability = getHydraulicNetwork().getPipe(edgeName).getAvailability();
      if (availability != 0.0 && availability != 1.0) {
        throw new IllegalStateException("Route configuration selector requires exact zero-or-one availability: "
            + selectorName + " edge=" + edgeName);
      }
    }
    for (int index = 0; index < selector.configurations.size(); index++) {
      RouteConfiguration configuration = selector.configurations.get(index);
      boolean matches = true;
      for (String edgeName : selector.controlledEdgeNames) {
        double expected = configuration.availableEdgeNames.contains(edgeName) ? 1.0 : 0.0;
        if (getHydraulicNetwork().getPipe(edgeName).getAvailability() != expected) {
          matches = false;
          break;
        }
      }
      if (matches) {
        if (selectedIndex >= 0) {
          throw new IllegalStateException(
              "Route configuration selector has ambiguous matching line-ups: " + selectorName);
        }
        selectedIndex = index;
      }
    }
    if (selectedIndex < 0) {
      throw new IllegalStateException(
          "Route configuration selector does not match a declared line-up: " + selectorName);
    }
    return selectedIndex;
  }

  /**
   * Apply one configuration transactionally and restore every edge after a failed write or read-back.
   *
   * @param selectorName selector identity used in diagnostics
   * @param selector immutable selector definition
   * @param selectedConfiguration zero-based configuration index
   */
  private void setSelectedConfiguration(String selectorName, RouteConfigurationSelector selector,
      double selectedConfiguration) {
    if (!Double.isFinite(selectedConfiguration) || selectedConfiguration != Math.rint(selectedConfiguration)
        || selectedConfiguration < 0.0 || selectedConfiguration >= selector.configurations.size()) {
      throw new IllegalArgumentException("Route configuration selection must be a declared integer index");
    }
    int selectedIndex = (int) selectedConfiguration;
    double[] baseline = new double[selector.controlledEdgeNames.size()];
    for (int index = 0; index < selector.controlledEdgeNames.size(); index++) {
      baseline[index] = getHydraulicNetwork().getPipe(selector.controlledEdgeNames.get(index)).getAvailability();
    }
    RouteConfiguration configuration = selector.configurations.get(selectedIndex);
    try {
      for (String edgeName : selector.controlledEdgeNames) {
        getHydraulicNetwork().getPipe(edgeName)
            .setAvailability(configuration.availableEdgeNames.contains(edgeName) ? 1.0 : 0.0);
      }
      if (requireSelectedConfiguration(selectorName, selector) != selectedIndex) {
        throw new IllegalStateException("Route configuration read-back did not match the requested line-up");
      }
      isSolved = false;
    } catch (RuntimeException exception) {
      for (int index = 0; index < selector.controlledEdgeNames.size(); index++) {
        getHydraulicNetwork().getPipe(selector.controlledEdgeNames.get(index)).setAvailability(baseline[index]);
      }
      throw exception;
    }
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
