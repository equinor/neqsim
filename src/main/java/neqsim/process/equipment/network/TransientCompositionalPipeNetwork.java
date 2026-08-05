package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Conservative transient component transport through a prescribed-flow gas gathering network.
 *
 * <p>
 * The v1 model is intentionally bounded to directed acyclic networks with strictly positive flow, one outgoing edge per
 * source or mixing node, one gas phase, and one common temperature. Each edge owns fixed gas mass in equal-volume
 * finite-volume cells. A first-order implicit upwind balance advances every named component while retaining edge
 * linepack delay:
 * </p>
 *
 * <p>
 * {@code M_j (Y_j^(n+1) - Y_j^n) = dt q (Y_(j-1)^(n+1) - Y_j^(n+1))}.
 * </p>
 *
 * <p>
 * Junction inlet component masses are summed by component name and become the downstream edge boundary state in the
 * same accepted timestep. The implementation reports edge, junction, and cumulative whole-network residuals and throws
 * before accepting a state that violates balance, boundedness, phase, topology, or flow-direction criteria. Hydraulic
 * coupling, branching flow splits, recirculation, reverse flow, thermal transport, dispersion, and phase appearance are
 * outside this first validated scope.
 * </p>
 */
public final class TransientCompositionalPipeNetwork implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double MINIMUM_SCALE_KG = 1.0e-12;
  private static final double TEMPERATURE_TOLERANCE_K = 1.0e-8;

  private final String name;
  private final Map<String, NetworkNode> nodes = new LinkedHashMap<String, NetworkNode>();
  private final Map<String, TransientEdge> edges = new LinkedHashMap<String, TransientEdge>();
  private final Map<String, SourceSchedule> sourceSchedules = new LinkedHashMap<String, SourceSchedule>();
  private double conservationTolerance = 1.0e-8;
  private TransientCompositionalPipeNetworkHistory speciesHistory = TransientCompositionalPipeNetworkHistory.empty();

  /**
   * Create a prescribed-flow transient species network.
   *
   * @param name network name
   */
  public TransientCompositionalPipeNetwork(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Network name cannot be blank");
    }
    this.name = name;
  }

  /** @return network name */
  public String getName() {
    return name;
  }

  /**
   * Add a named source, junction, or sink node.
   *
   * @param nodeName unique node name
   */
  public void addNode(String nodeName) {
    requireName(nodeName, "Node");
    if (nodes.containsKey(nodeName)) {
      throw new IllegalArgumentException("Node '" + nodeName + "' already exists");
    }
    nodes.put(nodeName, new NetworkNode(nodeName));
  }

  /**
   * Add a directed pipe edge with its initial gas state.
   *
   * <p>
   * The number of cells is the number of physical finite volumes. Edge mass is initialized from the flashed gas density
   * and internal geometric volume and then held fixed while named species are transported.
   * </p>
   *
   * @param edgeName unique edge name
   * @param fromNode upstream node
   * @param toNode downstream node
   * @param lengthMeters internal pipe length in m
   * @param diameterMeters internal diameter in m
   * @param numberOfCells number of finite-volume cells
   * @param initialFluid initial one-phase gas composition, pressure, and temperature
   */
  public void addPipe(String edgeName, String fromNode, String toNode, double lengthMeters, double diameterMeters,
      int numberOfCells, SystemInterface initialFluid) {
    requireName(edgeName, "Edge");
    if (edges.containsKey(edgeName)) {
      throw new IllegalArgumentException("Edge '" + edgeName + "' already exists");
    }
    NetworkNode upstream = requireNode(fromNode);
    NetworkNode downstream = requireNode(toNode);
    if (fromNode.equals(toNode)) {
      throw new IllegalArgumentException("Edge '" + edgeName + "' cannot connect a node to itself");
    }
    requirePositiveFinite(lengthMeters, "Pipe length");
    requirePositiveFinite(diameterMeters, "Pipe diameter");
    if (numberOfCells < 1) {
      throw new IllegalArgumentException("Pipe edge requires at least one finite-volume cell");
    }
    if (initialFluid == null) {
      throw new IllegalArgumentException("Initial pipe fluid cannot be null");
    }
    TransientEdge edge = new TransientEdge(edgeName, upstream, downstream, lengthMeters, diameterMeters, numberOfCells,
        initialFluid.clone());
    edges.put(edgeName, edge);
    upstream.outgoing.add(edge);
    downstream.incoming.add(edge);
  }

  /**
   * Assign a piecewise-constant composition and positive mass-flow schedule to a source node.
   *
   * <p>
   * Event times are elapsed seconds and must start at zero. A value remains active until the next event. Component
   * identity is read from each thermodynamic state by name, so array order may differ between schedule entries.
   * </p>
   *
   * @param nodeName source node without incoming edges
   * @param eventTimesSeconds strictly increasing event times beginning at zero
   * @param fluids one-phase gas states at the events
   * @param massFlowRatesKgS strictly positive source mass rates at the events
   */
  public void setSourceSchedule(String nodeName, double[] eventTimesSeconds, SystemInterface[] fluids,
      double[] massFlowRatesKgS) {
    requireNode(nodeName);
    if (eventTimesSeconds == null || fluids == null || massFlowRatesKgS == null || eventTimesSeconds.length == 0
        || eventTimesSeconds.length != fluids.length || eventTimesSeconds.length != massFlowRatesKgS.length) {
      throw new IllegalArgumentException("Source schedule arrays must have the same non-zero length");
    }
    if (Math.abs(eventTimesSeconds[0]) > 1.0e-12) {
      throw new IllegalArgumentException("Source schedule for '" + nodeName + "' must start at time zero");
    }
    double previousTime = -1.0;
    SystemInterface[] scheduleFluids = new SystemInterface[fluids.length];
    for (int index = 0; index < eventTimesSeconds.length; index++) {
      double eventTime = eventTimesSeconds[index];
      if (!Double.isFinite(eventTime) || eventTime < 0.0 || eventTime <= previousTime) {
        throw new IllegalArgumentException("Source schedule event times must be finite and strictly increasing");
      }
      if (fluids[index] == null) {
        throw new IllegalArgumentException("Source schedule fluid at index " + index + " cannot be null");
      }
      if (!Double.isFinite(massFlowRatesKgS[index]) || massFlowRatesKgS[index] <= 0.0) {
        throw new IllegalArgumentException(
            "Unsupported reverse flow at source '" + nodeName + "': prescribed mass flow must be strictly positive");
      }
      scheduleFluids[index] = fluids[index].clone();
      previousTime = eventTime;
    }
    sourceSchedules.put(nodeName, new SourceSchedule(eventTimesSeconds, scheduleFluids, massFlowRatesKgS));
  }

  /**
   * Set the fail-loud relative component-balance tolerance.
   *
   * @param tolerance positive finite relative tolerance
   */
  public void setConservationTolerance(double tolerance) {
    requirePositiveFinite(tolerance, "Conservation tolerance");
    conservationTolerance = tolerance;
  }

  /** @return configured relative component-balance tolerance */
  public double getConservationTolerance() {
    return conservationTolerance;
  }

  /**
   * Run from the configured initial linepack using a uniform timestep.
   *
   * <p>
   * Every invocation reconstructs the initial component inventories, making repeated runs deterministic. The end time
   * must contain an integer number of timesteps. Schedule events are sampled at accepted-step start times.
   * </p>
   *
   * @param endTimeSeconds final elapsed time in seconds
   * @param timeStepSeconds uniform timestep in seconds
   */
  public void run(double endTimeSeconds, double timeStepSeconds) {
    requirePositiveFinite(endTimeSeconds, "End time");
    requirePositiveFinite(timeStepSeconds, "Timestep");
    double rawStepCount = endTimeSeconds / timeStepSeconds;
    int stepCount = (int) Math.round(rawStepCount);
    if (stepCount < 1 || Math.abs(rawStepCount - stepCount) > 1.0e-10 * Math.max(1.0, rawStepCount)) {
      throw new IllegalArgumentException("End time must contain an integer number of timesteps");
    }

    List<NetworkNode> topologicalNodes = validateAndSortTopology();
    PreparedModel prepared = prepareModel();
    String[] componentNames = prepared.componentNames;
    double[] initialNetworkInventory = initializeEdgeInventories(componentNames, prepared.preparedInitialFluids);

    double[] elapsedTimes = new double[stepCount];
    Map<String, double[][]> nodeHistory = new LinkedHashMap<String, double[][]>();
    Map<String, List<TransientSpeciesConservationReport>> edgeHistory = new LinkedHashMap<>();
    Map<String, List<TransientSpeciesConservationReport>> junctionHistory = new LinkedHashMap<>();
    Map<String, double[]> cumulativeJunctionInlet = new LinkedHashMap<String, double[]>();
    Map<String, double[]> cumulativeJunctionOutlet = new LinkedHashMap<String, double[]>();
    List<TransientSpeciesConservationReport> networkHistory = new ArrayList<TransientSpeciesConservationReport>();

    for (String nodeName : nodes.keySet()) {
      nodeHistory.put(nodeName, new double[stepCount][componentNames.length]);
      NetworkNode node = nodes.get(nodeName);
      if (!sourceSchedules.containsKey(nodeName) && !node.outgoing.isEmpty()) {
        junctionHistory.put(nodeName, new ArrayList<TransientSpeciesConservationReport>());
        cumulativeJunctionInlet.put(nodeName, new double[componentNames.length]);
        cumulativeJunctionOutlet.put(nodeName, new double[componentNames.length]);
      }
    }
    for (String edgeName : edges.keySet()) {
      edgeHistory.put(edgeName, new ArrayList<TransientSpeciesConservationReport>());
    }

    double[] cumulativeExternalInlet = new double[componentNames.length];
    double[] cumulativeExternalOutlet = new double[componentNames.length];

    for (int step = 0; step < stepCount; step++) {
      double stepStartTime = step * timeStepSeconds;
      double stepEndTime = (step + 1) * timeStepSeconds;
      elapsedTimes[step] = stepEndTime;

      for (NetworkNode node : topologicalNodes) {
        NodeBoundary boundary;
        SourceSchedule sourceSchedule = sourceSchedules.get(node.name);
        if (sourceSchedule != null) {
          int scheduleIndex = sourceSchedule.indexAt(stepStartTime);
          SystemInterface sourceFluid = prepared.preparedSourceFluids.get(node.name)[scheduleIndex];
          boundary = new NodeBoundary(massFractions(sourceFluid, componentNames),
              sourceSchedule.massFlowRatesKgS[scheduleIndex], null);
        } else {
          boundary = mixIncomingBoundary(node, componentNames, timeStepSeconds);
        }

        nodeHistory.get(node.name)[step] = Arrays.copyOf(boundary.massFractions, boundary.massFractions.length);

        if (node.outgoing.isEmpty()) {
          if (sourceSchedule != null) {
            throw new IllegalStateException("Source node '" + node.name + "' must have one outgoing pipe edge");
          }
          addInPlace(cumulativeExternalOutlet, boundary.integratedIncomingMassKg);
          continue;
        }

        TransientEdge edge = node.outgoing.get(0);
        TransientSpeciesConservationReport edgeReport = edge.advance(boundary.massFractions, boundary.massFlowRateKgS,
            timeStepSeconds, stepEndTime, componentNames, conservationTolerance);
        edgeHistory.get(edge.name).add(edgeReport);
        if (!edgeReport.isConverged()) {
          throw new IllegalStateException(edgeReport.getMessage());
        }

        if (sourceSchedule != null) {
          addInPlace(cumulativeExternalInlet, edge.lastStepInletMassKg);
        } else {
          addInPlace(cumulativeJunctionInlet.get(node.name), boundary.integratedIncomingMassKg);
          addInPlace(cumulativeJunctionOutlet.get(node.name), edge.lastStepInletMassKg);
          TransientSpeciesConservationReport junctionReport = createJunctionReport(node.name, stepEndTime,
              componentNames, cumulativeJunctionInlet.get(node.name), cumulativeJunctionOutlet.get(node.name));
          junctionHistory.get(node.name).add(junctionReport);
          if (!junctionReport.isConverged()) {
            throw new IllegalStateException(junctionReport.getMessage());
          }
        }
      }

      double[] currentNetworkInventory = totalNetworkInventory(componentNames.length);
      TransientSpeciesConservationReport networkReport = createNetworkReport(stepEndTime, componentNames,
          initialNetworkInventory, currentNetworkInventory, cumulativeExternalInlet, cumulativeExternalOutlet);
      networkHistory.add(networkReport);
      if (!networkReport.isConverged()) {
        throw new IllegalStateException(networkReport.getMessage());
      }
    }

    speciesHistory = new TransientCompositionalPipeNetworkHistory(elapsedTimes, componentNames, nodeHistory,
        edgeHistory, junctionHistory, networkHistory);
  }

  /** @return immutable history from the latest completed run, empty before the first run */
  public TransientCompositionalPipeNetworkHistory getSpeciesHistory() {
    return speciesHistory;
  }

  /**
   * Get the current physical-cell mass-fraction profile for an edge.
   *
   * @param edgeName edge name
   * @return defensive component-by-cell profile
   */
  public double[][] getEdgeMassFractionProfile(String edgeName) {
    TransientEdge edge = edges.get(edgeName);
    if (edge == null) {
      throw new IllegalArgumentException("Edge '" + edgeName + "' does not exist");
    }
    if (edge.massFractionProfile == null) {
      throw new IllegalStateException("Transient compositional network has not run");
    }
    return copy(edge.massFractionProfile);
  }

  /**
   * Get fixed cell gas masses for a pipe edge.
   *
   * @param edgeName edge name
   * @return defensive cell-mass array in kg
   */
  public double[] getEdgeCellMassesKg(String edgeName) {
    TransientEdge edge = edges.get(edgeName);
    if (edge == null) {
      throw new IllegalArgumentException("Edge '" + edgeName + "' does not exist");
    }
    if (edge.cellMassesKg == null) {
      throw new IllegalStateException("Transient compositional network has not run");
    }
    return Arrays.copyOf(edge.cellMassesKg, edge.cellMassesKg.length);
  }

  private List<NetworkNode> validateAndSortTopology() {
    if (nodes.isEmpty() || edges.isEmpty()) {
      throw new IllegalStateException("Transient compositional network requires nodes and pipe edges");
    }
    for (NetworkNode node : nodes.values()) {
      boolean source = sourceSchedules.containsKey(node.name);
      if (source && !node.incoming.isEmpty()) {
        throw new IllegalStateException("Source node '" + node.name + "' cannot have incoming pipe edges");
      }
      if (source && node.outgoing.size() != 1) {
        throw new IllegalStateException("Source node '" + node.name + "' must have exactly one outgoing pipe edge");
      }
      if (!source && node.incoming.isEmpty()) {
        throw new IllegalStateException("Node '" + node.name + "' has no incoming edge or source schedule");
      }
      if (node.outgoing.size() > 1) {
        throw new IllegalStateException(
            "Branching flow splits at node '" + node.name + "' are outside the validated one-outgoing-edge scope");
      }
    }

    Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
    Deque<NetworkNode> ready = new ArrayDeque<NetworkNode>();
    for (NetworkNode node : nodes.values()) {
      indegree.put(node.name, node.incoming.size());
      if (node.incoming.isEmpty()) {
        ready.addLast(node);
      }
    }
    List<NetworkNode> order = new ArrayList<NetworkNode>();
    while (!ready.isEmpty()) {
      NetworkNode node = ready.removeFirst();
      order.add(node);
      for (TransientEdge edge : node.outgoing) {
        int remaining = indegree.get(edge.to.name) - 1;
        indegree.put(edge.to.name, remaining);
        if (remaining == 0) {
          ready.addLast(edge.to);
        }
      }
    }
    if (order.size() != nodes.size()) {
      throw new IllegalStateException("Recirculation is outside the validated directed-acyclic-network scope");
    }
    return order;
  }

  private PreparedModel prepareModel() {
    TreeSet<String> componentSet = new TreeSet<String>();
    Map<String, SystemInterface> preparedInitialFluids = new LinkedHashMap<String, SystemInterface>();
    Map<String, SystemInterface[]> preparedSourceFluids = new LinkedHashMap<String, SystemInterface[]>();
    double referenceTemperature = Double.NaN;

    for (TransientEdge edge : edges.values()) {
      SystemInterface prepared = prepareOnePhaseFluid(edge.initialFluid, "initial fluid for edge '" + edge.name + "'");
      referenceTemperature = validateTemperature(referenceTemperature, prepared.getTemperature(), edge.name);
      collectComponentNames(prepared, componentSet);
      preparedInitialFluids.put(edge.name, prepared);
    }
    for (Map.Entry<String, SourceSchedule> entry : sourceSchedules.entrySet()) {
      SystemInterface[] preparedStates = new SystemInterface[entry.getValue().fluids.length];
      for (int index = 0; index < preparedStates.length; index++) {
        preparedStates[index] = prepareOnePhaseFluid(entry.getValue().fluids[index],
            "source schedule '" + entry.getKey() + "' at index " + index);
        referenceTemperature = validateTemperature(referenceTemperature, preparedStates[index].getTemperature(),
            entry.getKey());
        collectComponentNames(preparedStates[index], componentSet);
      }
      preparedSourceFluids.put(entry.getKey(), preparedStates);
    }
    if (componentSet.size() < 2) {
      throw new IllegalStateException("Transient species transport requires at least two named components");
    }
    return new PreparedModel(componentSet.toArray(new String[componentSet.size()]), preparedInitialFluids,
        preparedSourceFluids);
  }

  private double[] initializeEdgeInventories(String[] componentNames,
      Map<String, SystemInterface> preparedInitialFluids) {
    double[] totalInventory = new double[componentNames.length];
    for (TransientEdge edge : edges.values()) {
      SystemInterface fluid = preparedInitialFluids.get(edge.name);
      double densityKgM3 = fluid.getDensity("kg/m3");
      requirePositiveFinite(densityKgM3, "Initial gas density for edge '" + edge.name + "'");
      double internalVolumeM3 = Math.PI * edge.diameterMeters * edge.diameterMeters * edge.lengthMeters / 4.0;
      double cellMassKg = densityKgM3 * internalVolumeM3 / edge.numberOfCells;
      edge.cellMassesKg = new double[edge.numberOfCells];
      Arrays.fill(edge.cellMassesKg, cellMassKg);
      double[] initialMassFractions = massFractions(fluid, componentNames);
      edge.massFractionProfile = new double[componentNames.length][edge.numberOfCells];
      for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
        Arrays.fill(edge.massFractionProfile[componentIndex], initialMassFractions[componentIndex]);
        totalInventory[componentIndex] += cellMassKg * edge.numberOfCells * initialMassFractions[componentIndex];
      }
      edge.lastReport = null;
      edge.runInitialInventoryKg = edge.inventory();
      edge.cumulativeInletMassKg = new double[componentNames.length];
      edge.cumulativeOutletMassKg = new double[componentNames.length];
      edge.lastStepInletMassKg = new double[componentNames.length];
      edge.lastStepOutletMassKg = new double[componentNames.length];
    }
    return totalInventory;
  }

  private NodeBoundary mixIncomingBoundary(NetworkNode node, String[] componentNames, double timeStepSeconds) {
    double[] incomingMass = new double[componentNames.length];
    for (TransientEdge edge : node.incoming) {
      if (edge.lastReport == null) {
        throw new IllegalStateException("Upstream edge '" + edge.name + "' has no accepted current-step state");
      }
      addInPlace(incomingMass, edge.lastStepOutletMassKg);
    }
    double totalMass = sum(incomingMass);
    if (!(totalMass > 0.0) || !Double.isFinite(totalMass)) {
      throw new IllegalStateException("Junction '" + node.name + "' requires strictly positive incoming flow");
    }
    double[] fractions = new double[componentNames.length];
    for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
      fractions[componentIndex] = incomingMass[componentIndex] / totalMass;
    }
    return new NodeBoundary(fractions, totalMass / timeStepSeconds, incomingMass);
  }

  private TransientSpeciesConservationReport createJunctionReport(String nodeName, double elapsedTimeSeconds,
      String[] componentNames, double[] incomingMass, double[] outgoingMass) {
    double[] residual = new double[componentNames.length];
    double[] relative = new double[componentNames.length];
    double maximumRelativeResidual = 0.0;
    for (int index = 0; index < componentNames.length; index++) {
      residual[index] = outgoingMass[index] - incomingMass[index];
      double scale = Math.max(MINIMUM_SCALE_KG, Math.max(Math.abs(incomingMass[index]), Math.abs(outgoingMass[index])));
      relative[index] = residual[index] / scale;
      maximumRelativeResidual = Math.max(maximumRelativeResidual, Math.abs(relative[index]));
    }
    boolean converged = maximumRelativeResidual <= conservationTolerance;
    String message = "Junction '" + nodeName + "' component-name mixing " + (converged ? "converged" : "failed")
        + " with maximum relative residual=" + maximumRelativeResidual + ".";
    return new TransientSpeciesConservationReport(nodeName, TransientSpeciesConservationReport.LocationType.JUNCTION,
        elapsedTimeSeconds, componentNames, new double[0][0], new double[componentNames.length],
        new double[componentNames.length], incomingMass, outgoingMass, residual, relative, maximumRelativeResidual,
        Double.NaN, Double.NaN, Double.NaN, converged, message);
  }

  private TransientSpeciesConservationReport createNetworkReport(double elapsedTimeSeconds, String[] componentNames,
      double[] initialInventory, double[] finalInventory, double[] cumulativeInlet, double[] cumulativeOutlet) {
    double[] residual = new double[componentNames.length];
    double[] relative = new double[componentNames.length];
    double maximumRelativeResidual = 0.0;
    for (int index = 0; index < componentNames.length; index++) {
      residual[index] = finalInventory[index] - initialInventory[index] - cumulativeInlet[index]
          + cumulativeOutlet[index];
      double scale = balanceScale(initialInventory[index], finalInventory[index], cumulativeInlet[index],
          cumulativeOutlet[index]);
      relative[index] = residual[index] / scale;
      maximumRelativeResidual = Math.max(maximumRelativeResidual, Math.abs(relative[index]));
    }
    boolean converged = maximumRelativeResidual <= conservationTolerance;
    String message = "Network '" + name + "' cumulative component balance " + (converged ? "converged" : "failed")
        + " with maximum relative residual=" + maximumRelativeResidual + ".";
    return new TransientSpeciesConservationReport(name, TransientSpeciesConservationReport.LocationType.NETWORK,
        elapsedTimeSeconds, componentNames, new double[0][0], initialInventory, finalInventory, cumulativeInlet,
        cumulativeOutlet, residual, relative, maximumRelativeResidual, Double.NaN, Double.NaN, Double.NaN, converged,
        message);
  }

  private double[] totalNetworkInventory(int componentCount) {
    double[] inventory = new double[componentCount];
    for (TransientEdge edge : edges.values()) {
      for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
        for (int cellIndex = 0; cellIndex < edge.numberOfCells; cellIndex++) {
          inventory[componentIndex] += edge.cellMassesKg[cellIndex]
              * edge.massFractionProfile[componentIndex][cellIndex];
        }
      }
    }
    return inventory;
  }

  private static SystemInterface prepareOnePhaseFluid(SystemInterface source, String context) {
    SystemInterface fluid = source.clone();
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.TPflash();
      fluid.initProperties();
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to initialize " + context + ": " + exception.getMessage(), exception);
    }
    if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalArgumentException("Unsupported phase appearance in " + context
          + ": conservative transient network transport requires exactly one gas phase");
    }
    return fluid;
  }

  private static double validateTemperature(double referenceTemperature, double temperature, String context) {
    requirePositiveFinite(temperature, "Temperature for '" + context + "'");
    if (!Double.isFinite(referenceTemperature)) {
      return temperature;
    }
    if (Math.abs(referenceTemperature - temperature) > TEMPERATURE_TOLERANCE_K) {
      throw new IllegalArgumentException("Thermal transport is unsupported: '" + context + "' has temperature "
          + temperature + " K instead of the network isothermal temperature " + referenceTemperature + " K");
    }
    return referenceTemperature;
  }

  private static void collectComponentNames(SystemInterface fluid, TreeSet<String> componentNames) {
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      componentNames.add(fluid.getPhase(0).getComponent(index).getComponentName());
    }
  }

  private static double[] massFractions(SystemInterface fluid, String[] componentNames) {
    Map<String, Double> fractionsByName = new LinkedHashMap<String, Double>();
    double[] molarComposition = fluid.getMolarComposition();
    double totalMass = 0.0;
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      String componentName = fluid.getPhase(0).getComponent(index).getComponentName();
      double componentMass = molarComposition[index] * fluid.getPhase(0).getComponent(index).getMolarMass();
      fractionsByName.put(componentName, componentMass);
      totalMass += componentMass;
    }
    if (!(totalMass > 0.0) || !Double.isFinite(totalMass)) {
      throw new IllegalStateException("Cannot calculate positive finite source component mass fractions");
    }
    double[] result = new double[componentNames.length];
    for (int index = 0; index < componentNames.length; index++) {
      Double componentMass = fractionsByName.get(componentNames[index]);
      result[index] = componentMass == null ? 0.0 : componentMass / totalMass;
    }
    return result;
  }

  private NetworkNode requireNode(String nodeName) {
    NetworkNode node = nodes.get(nodeName);
    if (node == null) {
      throw new IllegalArgumentException("Node '" + nodeName + "' does not exist");
    }
    return node;
  }

  private static void requireName(String value, String kind) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(kind + " name cannot be blank");
    }
  }

  private static void requirePositiveFinite(double value, String description) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(description + " must be positive and finite");
    }
  }

  private static void addInPlace(double[] target, double[] increment) {
    if (target == null || increment == null || target.length != increment.length) {
      throw new IllegalArgumentException("Component arrays must have identical dimensions");
    }
    for (int index = 0; index < target.length; index++) {
      target[index] += increment[index];
    }
  }

  private static double sum(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value;
    }
    return result;
  }

  private static double balanceScale(double initial, double closing, double inlet, double outlet) {
    return Math.max(MINIMUM_SCALE_KG,
        Math.max(Math.max(Math.abs(initial), Math.abs(closing)), Math.max(Math.abs(inlet), Math.abs(outlet))));
  }

  private static double[][] copy(double[][] values) {
    double[][] result = new double[values.length][];
    for (int index = 0; index < values.length; index++) {
      result[index] = Arrays.copyOf(values[index], values[index].length);
    }
    return result;
  }

  private static final class NetworkNode implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final List<TransientEdge> incoming = new ArrayList<TransientEdge>();
    private final List<TransientEdge> outgoing = new ArrayList<TransientEdge>();

    private NetworkNode(String name) {
      this.name = name;
    }
  }

  private static final class SourceSchedule implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] eventTimesSeconds;
    private final SystemInterface[] fluids;
    private final double[] massFlowRatesKgS;

    private SourceSchedule(double[] eventTimesSeconds, SystemInterface[] fluids, double[] massFlowRatesKgS) {
      this.eventTimesSeconds = Arrays.copyOf(eventTimesSeconds, eventTimesSeconds.length);
      this.fluids = Arrays.copyOf(fluids, fluids.length);
      this.massFlowRatesKgS = Arrays.copyOf(massFlowRatesKgS, massFlowRatesKgS.length);
    }

    private int indexAt(double elapsedTimeSeconds) {
      int result = 0;
      for (int index = 1; index < eventTimesSeconds.length; index++) {
        if (eventTimesSeconds[index] > elapsedTimeSeconds) {
          break;
        }
        result = index;
      }
      return result;
    }
  }

  private static final class TransientEdge implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final NetworkNode from;
    private final NetworkNode to;
    private final double lengthMeters;
    private final double diameterMeters;
    private final int numberOfCells;
    private final SystemInterface initialFluid;
    private double[] cellMassesKg;
    private double[][] massFractionProfile;
    private TransientSpeciesConservationReport lastReport;
    private double[] runInitialInventoryKg;
    private double[] cumulativeInletMassKg;
    private double[] cumulativeOutletMassKg;
    private double[] lastStepInletMassKg;
    private double[] lastStepOutletMassKg;

    private TransientEdge(String name, NetworkNode from, NetworkNode to, double lengthMeters, double diameterMeters,
        int numberOfCells, SystemInterface initialFluid) {
      this.name = name;
      this.from = from;
      this.to = to;
      this.lengthMeters = lengthMeters;
      this.diameterMeters = diameterMeters;
      this.numberOfCells = numberOfCells;
      this.initialFluid = initialFluid;
    }

    private TransientSpeciesConservationReport advance(double[] inletMassFractions, double massFlowRateKgS,
        double timeStepSeconds, double elapsedTimeSeconds, String[] componentNames, double tolerance) {
      if (!Double.isFinite(massFlowRateKgS) || massFlowRateKgS <= 0.0) {
        throw new IllegalArgumentException(
            "Unsupported reverse flow on edge '" + name + "': mass flow must remain strictly positive");
      }
      double[] stepInitialInventory = inventory();
      double[] inletMass = new double[componentNames.length];
      double[] outletMass = new double[componentNames.length];
      double[][] updatedProfile = new double[componentNames.length][numberOfCells];

      for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
        double upstreamMassFraction = inletMassFractions[componentIndex];
        inletMass[componentIndex] = timeStepSeconds * massFlowRateKgS * upstreamMassFraction;
        for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
          double transportedMassKg = timeStepSeconds * massFlowRateKgS;
          double updatedMassFraction = (cellMassesKg[cellIndex] * massFractionProfile[componentIndex][cellIndex]
              + transportedMassKg * upstreamMassFraction) / (cellMassesKg[cellIndex] + transportedMassKg);
          updatedProfile[componentIndex][cellIndex] = updatedMassFraction;
          upstreamMassFraction = updatedMassFraction;
        }
        outletMass[componentIndex] = timeStepSeconds * massFlowRateKgS * upstreamMassFraction;
      }

      massFractionProfile = updatedProfile;
      double[] finalInventory = inventory();
      double maximumStepRelativeResidual = 0.0;
      for (int index = 0; index < componentNames.length; index++) {
        double stepResidual = finalInventory[index] - stepInitialInventory[index] - inletMass[index]
            + outletMass[index];
        double stepScale = balanceScale(stepInitialInventory[index], finalInventory[index], inletMass[index],
            outletMass[index]);
        maximumStepRelativeResidual = Math.max(maximumStepRelativeResidual, Math.abs(stepResidual / stepScale));
      }
      lastStepInletMassKg = Arrays.copyOf(inletMass, inletMass.length);
      lastStepOutletMassKg = Arrays.copyOf(outletMass, outletMass.length);
      addInPlace(cumulativeInletMassKg, inletMass);
      addInPlace(cumulativeOutletMassKg, outletMass);
      double[] residual = new double[componentNames.length];
      double[] relative = new double[componentNames.length];
      double maximumRelativeResidual = 0.0;
      for (int index = 0; index < componentNames.length; index++) {
        residual[index] = finalInventory[index] - runInitialInventoryKg[index] - cumulativeInletMassKg[index]
            + cumulativeOutletMassKg[index];
        double scale = balanceScale(runInitialInventoryKg[index], finalInventory[index], cumulativeInletMassKg[index],
            cumulativeOutletMassKg[index]);
        relative[index] = residual[index] / scale;
        maximumRelativeResidual = Math.max(maximumRelativeResidual, Math.abs(relative[index]));
      }

      double minimumMassFraction = Double.POSITIVE_INFINITY;
      double maximumMassFraction = Double.NEGATIVE_INFINITY;
      double maximumSumError = 0.0;
      for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
        double sum = 0.0;
        for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
          double value = updatedProfile[componentIndex][cellIndex];
          minimumMassFraction = Math.min(minimumMassFraction, value);
          maximumMassFraction = Math.max(maximumMassFraction, value);
          sum += value;
        }
        maximumSumError = Math.max(maximumSumError, Math.abs(sum - 1.0));
      }
      boolean bounded = minimumMassFraction >= -tolerance && maximumMassFraction <= 1.0 + tolerance
          && maximumSumError <= tolerance;
      boolean converged = maximumRelativeResidual <= tolerance && maximumStepRelativeResidual <= tolerance && bounded;
      String message = "Edge '" + name + "' conservative species step " + (converged ? "converged" : "failed")
          + "; cumulative maximum relative residual=" + maximumRelativeResidual + ", step maximum relative residual="
          + maximumStepRelativeResidual + ", mass-fraction range=[" + minimumMassFraction + ", " + maximumMassFraction
          + "], maximum sum error=" + maximumSumError + ".";
      lastReport = new TransientSpeciesConservationReport(name, TransientSpeciesConservationReport.LocationType.EDGE,
          elapsedTimeSeconds, componentNames, updatedProfile, runInitialInventoryKg, finalInventory,
          cumulativeInletMassKg, cumulativeOutletMassKg, residual, relative, maximumRelativeResidual,
          minimumMassFraction, maximumMassFraction, maximumSumError, converged, message);
      return lastReport;
    }

    private double[] inventory() {
      double[] result = new double[massFractionProfile.length];
      for (int componentIndex = 0; componentIndex < massFractionProfile.length; componentIndex++) {
        for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
          result[componentIndex] += cellMassesKg[cellIndex] * massFractionProfile[componentIndex][cellIndex];
        }
      }
      return result;
    }
  }

  private static final class NodeBoundary {
    private final double[] massFractions;
    private final double massFlowRateKgS;
    private final double[] integratedIncomingMassKg;

    private NodeBoundary(double[] massFractions, double massFlowRateKgS, double[] integratedIncomingMassKg) {
      this.massFractions = massFractions;
      this.massFlowRateKgS = massFlowRateKgS;
      this.integratedIncomingMassKg = integratedIncomingMassKg;
    }
  }

  private static final class PreparedModel {
    private final String[] componentNames;
    private final Map<String, SystemInterface> preparedInitialFluids;
    private final Map<String, SystemInterface[]> preparedSourceFluids;

    private PreparedModel(String[] componentNames, Map<String, SystemInterface> preparedInitialFluids,
        Map<String, SystemInterface[]> preparedSourceFluids) {
      this.componentNames = componentNames;
      this.preparedInitialFluids = Collections.unmodifiableMap(preparedInitialFluids);
      this.preparedSourceFluids = Collections.unmodifiableMap(preparedSourceFluids);
    }
  }
}
