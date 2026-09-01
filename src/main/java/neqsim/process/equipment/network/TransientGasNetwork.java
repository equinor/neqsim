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
import java.util.Set;
import java.util.TreeSet;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;

/**
 * Positive-flow, one-phase, isothermal transient gas-network hydraulic and composition solver.
 *
 * <p>
 * This first coupled network stage is bounded to a directed acyclic gathering tree with one outgoing pipe per source or
 * junction and one fixed-pressure sink. Source mass rate and composition are piecewise-constant schedules. Every
 * accepted timestep solves node pressures, edge-average Darcy flow, inlet/outlet face flow, and compressible edge
 * linepack simultaneously. Conservative implicit upwind transport then advances named-component inventories using the
 * solved face flows and cell masses.
 * </p>
 *
 * <p>
 * Momentum is quasi-steady and isothermal. A local EOS linearization is rebuilt from the accepted edge composition and
 * average pressure before each timestep: density is proportional to pressure during that implicit step while viscosity
 * and {@code p/rho} remain frozen. This retains real-gas linepack capacitance without claiming acoustic-wave, thermal,
 * compressor-control, reverse-flow, or phase-appearance capability.
 * </p>
 */
public final class TransientGasNetwork implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double DEFAULT_ROUGHNESS_M = 5.0e-5;
  private static final double MINIMUM_PRESSURE_PA = 1.0e4;
  private static final double MINIMUM_SCALE = 1.0e-12;
  private static final double TEMPERATURE_TOLERANCE_K = 1.0e-8;

  private final String name;
  private final Map<String, NetworkNode> nodes = new LinkedHashMap<String, NetworkNode>();
  private final Map<String, TransientEdge> edges = new LinkedHashMap<String, TransientEdge>();
  private final Map<String, SourceSchedule> sourceSchedules = new LinkedHashMap<String, SourceSchedule>();
  private final Map<String, Double> fixedPressurePa = new LinkedHashMap<String, Double>();
  private final Map<String, Double> initialPressurePa = new LinkedHashMap<String, Double>();
  private final Map<String, PressureLimits> sourcePressureLimits = new LinkedHashMap<String, PressureLimits>();

  private double conservationTolerance = 1.0e-8;
  private double hydraulicToleranceKgS = 1.0e-6;
  private int maximumHydraulicIterations = 40;
  private String lastDiagnostic = "Transient gas network has not run";
  private TransientGasNetworkHistory history = TransientGasNetworkHistory.empty();

  /**
   * Create a transient gas network.
   *
   * @param name network name
   */
  public TransientGasNetwork(String name) {
    requireName(name, "Network");
    this.name = name;
  }

  /** @return network name */
  public String getName() {
    return name;
  }

  /**
   * Add a source, junction, or sink node.
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
   * Add a directed pipe with geometry, roughness, grid, and initial one-phase gas state.
   *
   * @param edgeName unique edge name
   * @param fromNode upstream node
   * @param toNode downstream node
   * @param lengthMeters internal length in m
   * @param diameterMeters internal diameter in m
   * @param roughnessMeters absolute internal roughness in m
   * @param numberOfCells number of physical finite-volume cells
   * @param initialFluid initial gas composition, temperature, and pressure
   */
  public void addPipe(String edgeName, String fromNode, String toNode, double lengthMeters, double diameterMeters,
      double roughnessMeters, int numberOfCells, SystemInterface initialFluid) {
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
    if (!Double.isFinite(roughnessMeters) || roughnessMeters < 0.0) {
      throw new IllegalArgumentException("Pipe roughness must be finite and non-negative");
    }
    if (numberOfCells < 1) {
      throw new IllegalArgumentException("Pipe edge requires at least one finite-volume cell");
    }
    if (initialFluid == null) {
      throw new IllegalArgumentException("Initial pipe fluid cannot be null");
    }
    TransientEdge edge = new TransientEdge(edgeName, upstream, downstream, lengthMeters, diameterMeters,
        roughnessMeters, numberOfCells, initialFluid.clone());
    edges.put(edgeName, edge);
    upstream.outgoing.add(edge);
    downstream.incoming.add(edge);
  }

  /**
   * Add a directed pipe using the default 50 micrometre roughness.
   *
   * @param edgeName unique edge name
   * @param fromNode upstream node
   * @param toNode downstream node
   * @param lengthMeters internal length in m
   * @param diameterMeters internal diameter in m
   * @param numberOfCells number of physical finite-volume cells
   * @param initialFluid initial gas composition, temperature, and pressure
   */
  public void addPipe(String edgeName, String fromNode, String toNode, double lengthMeters, double diameterMeters,
      int numberOfCells, SystemInterface initialFluid) {
    addPipe(edgeName, fromNode, toNode, lengthMeters, diameterMeters, DEFAULT_ROUGHNESS_M, numberOfCells, initialFluid);
  }

  /**
   * Assign a piecewise-constant source mass-rate and gas-composition schedule.
   *
   * @param nodeName source node without incoming edges
   * @param eventTimesSeconds strictly increasing event times beginning at zero
   * @param fluids one-phase gas states at the events
   * @param massFlowRatesKgS strictly positive source mass rates in kg/s
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
            "Unsupported reverse flow at source '" + nodeName + "': scheduled mass flow must be strictly positive");
      }
      scheduleFluids[index] = fluids[index].clone();
      previousTime = eventTime;
    }
    sourceSchedules.put(nodeName, new SourceSchedule(eventTimesSeconds, scheduleFluids, massFlowRatesKgS));
  }

  /**
   * Set a fixed absolute-pressure boundary, initially supported at the single sink.
   *
   * @param nodeName sink node name
   * @param pressure pressure value
   * @param unit pressure unit such as bara or Pa
   */
  public void setFixedPressureBoundary(String nodeName, double pressure, String unit) {
    requireNode(nodeName);
    double pressureBara = new PressureUnit(pressure, unit).getValue("bara");
    requirePositiveFinite(pressureBara, "Fixed pressure");
    fixedPressurePa.put(nodeName, pressureBara * 1.0e5);
  }

  /**
   * Set an initial pressure guess. This is not a pressure boundary and does not prescribe an event.
   *
   * @param nodeName node name
   * @param pressure pressure value
   * @param unit pressure unit such as bara or Pa
   */
  public void setInitialNodePressure(String nodeName, double pressure, String unit) {
    requireNode(nodeName);
    double pressureBara = new PressureUnit(pressure, unit).getValue("bara");
    requirePositiveFinite(pressureBara, "Initial pressure guess");
    initialPressurePa.put(nodeName, pressureBara * 1.0e5);
  }

  /**
   * Set allowed solved pressure bounds for a scheduled source.
   *
   * @param nodeName source node name
   * @param minimumPressure minimum absolute pressure
   * @param maximumPressure maximum absolute pressure
   * @param unit pressure unit such as bara or Pa
   */
  public void setSourcePressureLimits(String nodeName, double minimumPressure, double maximumPressure, String unit) {
    requireNode(nodeName);
    double minimumBara = new PressureUnit(minimumPressure, unit).getValue("bara");
    double maximumBara = new PressureUnit(maximumPressure, unit).getValue("bara");
    requirePositiveFinite(minimumBara, "Minimum source pressure");
    requirePositiveFinite(maximumBara, "Maximum source pressure");
    if (maximumBara <= minimumBara) {
      throw new IllegalArgumentException("Maximum source pressure must exceed the minimum");
    }
    sourcePressureLimits.put(nodeName, new PressureLimits(minimumBara * 1.0e5, maximumBara * 1.0e5));
  }

  /**
   * Set a fail-loud average-velocity capacity limit for an edge.
   *
   * @param edgeName edge name
   * @param maximumVelocityMPerS positive maximum velocity in m/s
   */
  public void setMaximumEdgeVelocity(String edgeName, double maximumVelocityMPerS) {
    requirePositiveFinite(maximumVelocityMPerS, "Maximum edge velocity");
    requireEdge(edgeName).maximumVelocityMPerS = maximumVelocityMPerS;
  }

  /**
   * Configure nonlinear and conservation tolerances.
   *
   * @param maximumIterations positive nonlinear iteration limit
   * @param hydraulicToleranceKgS positive node-balance tolerance in kg/s
   * @param componentConservationTolerance positive relative inventory tolerance
   */
  public void setSolverControls(int maximumIterations, double hydraulicToleranceKgS,
      double componentConservationTolerance) {
    if (maximumIterations < 1) {
      throw new IllegalArgumentException("Maximum hydraulic iterations must be positive");
    }
    requirePositiveFinite(hydraulicToleranceKgS, "Hydraulic tolerance");
    requirePositiveFinite(componentConservationTolerance, "Conservation tolerance");
    this.maximumHydraulicIterations = maximumIterations;
    this.hydraulicToleranceKgS = hydraulicToleranceKgS;
    this.conservationTolerance = componentConservationTolerance;
  }

  /** @return configured relative component-conservation tolerance */
  public double getConservationTolerance() {
    return conservationTolerance;
  }

  /** @return configured node-balance tolerance in kg/s */
  public double getHydraulicToleranceKgS() {
    return hydraulicToleranceKgS;
  }

  /** @return immutable history from the latest completed run */
  public TransientGasNetworkHistory getHistory() {
    return history;
  }

  /** @return latest completion or fail-loud diagnostic */
  public String getLastDiagnostic() {
    return lastDiagnostic;
  }

  /**
   * Run from a steady initial hydraulic state using a uniform timestep.
   *
   * <p>
   * Every invocation rebuilds the initial state, so repeated runs are deterministic. The end time must contain an
   * integer number of timesteps. Schedule events are sampled at accepted-step start times.
   * </p>
   *
   * @param endTimeSeconds final elapsed time in s
   * @param timeStepSeconds uniform timestep in s
   */
  public void run(double endTimeSeconds, double timeStepSeconds) {
    requirePositiveFinite(endTimeSeconds, "End time");
    requirePositiveFinite(timeStepSeconds, "Timestep");
    double rawStepCount = endTimeSeconds / timeStepSeconds;
    int stepCount = (int) Math.round(rawStepCount);
    if (stepCount < 1 || Math.abs(rawStepCount - stepCount) > 1.0e-10 * Math.max(1.0, rawStepCount)) {
      throw new IllegalArgumentException("End time must contain an integer number of timesteps");
    }

    history = TransientGasNetworkHistory.empty();
    lastDiagnostic = "Transient gas network run started";
    try {
      List<NetworkNode> topologicalNodes = validateAndSortTopology();
      PreparedModel prepared = prepareModel();
      initializeCompositionProfiles(prepared);

      Map<String, Double> pressuresPa = initialPressureGuesses(prepared);
      Map<String, Double> initialRates = sourceRatesAt(0.0);
      Map<String, HydraulicProperties> initialProperties = buildHydraulicProperties(pressuresPa, prepared);
      SolveResult steady = solvePressures(pressuresPa, initialRates, initialProperties,
          Collections.<String, Double>emptyMap(), 1.0, false);
      pressuresPa = steady.pressuresPa;
      initialProperties = buildHydraulicProperties(pressuresPa, prepared);
      steady = solvePressures(pressuresPa, initialRates, initialProperties, Collections.<String, Double>emptyMap(), 1.0,
          false);
      pressuresPa = steady.pressuresPa;
      initializeEdgeMasses(pressuresPa, initialProperties, prepared.componentNames);
      checkPressureAndCapacity(pressuresPa, steady.edgeStates);

      double initialTotalLinepackKg = totalLinepackKg();
      double[] initialComponentInventoryKg = totalComponentInventory(prepared.componentNames.length);
      HistoryAccumulator accumulator = new HistoryAccumulator(stepCount, prepared.componentNames, nodes, edges,
          sourceSchedules);
      accumulator.initialTotalLinepackKg = initialTotalLinepackKg;
      accumulator.initialComponentInventoryKg = Arrays.copyOf(initialComponentInventoryKg,
          initialComponentInventoryKg.length);
      Map<String, HydraulicProperties> properties = buildHydraulicProperties(pressuresPa, prepared);

      for (int step = 0; step < stepCount; step++) {
        double stepStartSeconds = step * timeStepSeconds;
        double stepEndSeconds = (step + 1) * timeStepSeconds;
        Map<String, Double> rates = sourceRatesAt(stepStartSeconds);
        Map<String, Double> previousLinepack = currentLinepackByEdge();
        SolveResult solved = solvePressures(pressuresPa, rates, properties, previousLinepack, timeStepSeconds, true);
        checkPressureAndCapacity(solved.pressuresPa, solved.edgeStates);

        advanceComponents(topologicalNodes, prepared, solved, stepStartSeconds, stepEndSeconds, timeStepSeconds,
            accumulator);
        pressuresPa = solved.pressuresPa;
        properties = buildHydraulicProperties(pressuresPa, prepared);
        recordAcceptedStep(step, stepEndSeconds, solved, pressuresPa, accumulator);
      }

      history = accumulator.toHistory();
      lastDiagnostic = "Transient gas network '" + name + "' completed " + stepCount
          + " accepted step(s); final relative mass residual="
          + history.getFinalStepReport().getRelativeTotalMassResidual() + ", final maximum component residual="
          + history.getFinalStepReport().getMaximumComponentRelativeResidual() + ".";
    } catch (RuntimeException exception) {
      lastDiagnostic = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
      throw exception;
    }
  }

  private List<NetworkNode> validateAndSortTopology() {
    if (nodes.isEmpty() || edges.isEmpty()) {
      throw new IllegalStateException("Transient gas network requires nodes and pipe edges");
    }
    if (fixedPressurePa.size() != 1) {
      throw new IllegalStateException(
          "Transient gas network currently requires exactly one fixed-pressure sink boundary");
    }
    String sinkName = fixedPressurePa.keySet().iterator().next();
    for (NetworkNode node : nodes.values()) {
      boolean source = sourceSchedules.containsKey(node.name);
      boolean fixed = fixedPressurePa.containsKey(node.name);
      if (source && !node.incoming.isEmpty()) {
        throw new IllegalStateException("Source node '" + node.name + "' cannot have incoming pipe edges");
      }
      if (source && node.outgoing.size() != 1) {
        throw new IllegalStateException("Source node '" + node.name + "' must have exactly one outgoing pipe edge");
      }
      if (fixed && !node.outgoing.isEmpty()) {
        throw new IllegalStateException("Fixed-pressure sink '" + node.name + "' cannot have outgoing pipe edges");
      }
      if (fixed && node.incoming.isEmpty()) {
        throw new IllegalStateException("Fixed-pressure sink '" + node.name + "' requires an incoming pipe edge");
      }
      if (!source && !fixed && (node.incoming.isEmpty() || node.outgoing.size() != 1)) {
        throw new IllegalStateException(
            "Junction '" + node.name + "' requires at least one incoming edge and exactly one outgoing edge");
      }
      if (!source && node.incoming.isEmpty()) {
        throw new IllegalStateException("Node '" + node.name + "' requires a source schedule or an incoming edge");
      }
    }
    if (!nodes.containsKey(sinkName)) {
      throw new IllegalStateException("Fixed-pressure sink does not exist");
    }

    Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
    Deque<NetworkNode> queue = new ArrayDeque<NetworkNode>();
    for (NetworkNode node : nodes.values()) {
      indegree.put(node.name, node.incoming.size());
      if (node.incoming.isEmpty()) {
        queue.addLast(node);
      }
    }
    List<NetworkNode> ordered = new ArrayList<NetworkNode>();
    while (!queue.isEmpty()) {
      NetworkNode node = queue.removeFirst();
      ordered.add(node);
      for (TransientEdge edge : node.outgoing) {
        int remaining = indegree.get(edge.to.name) - 1;
        indegree.put(edge.to.name, remaining);
        if (remaining == 0) {
          queue.addLast(edge.to);
        }
      }
    }
    if (ordered.size() != nodes.size()) {
      throw new IllegalStateException("Recirculation is unsupported: transient gas-network topology must be acyclic");
    }
    if (!ordered.get(ordered.size() - 1).name.equals(sinkName)) {
      throw new IllegalStateException(
          "Every positive-flow path must terminate at the configured fixed-pressure sink '" + sinkName + "'");
    }
    return ordered;
  }

  private PreparedModel prepareModel() {
    Map<String, SystemInterface> preparedInitialFluids = new LinkedHashMap<String, SystemInterface>();
    Map<String, SystemInterface[]> preparedSourceFluids = new LinkedHashMap<String, SystemInterface[]>();
    TreeSet<String> componentNames = new TreeSet<String>();
    Set<String> requiredSlate = null;
    double referenceTemperature = Double.NaN;

    for (TransientEdge edge : edges.values()) {
      SystemInterface fluid = prepareOnePhaseFluid(edge.initialFluid, "initial fluid for edge '" + edge.name + "'");
      referenceTemperature = validateTemperature(referenceTemperature, fluid.getTemperature(), edge.name);
      TreeSet<String> slate = componentSlate(fluid);
      if (requiredSlate == null) {
        requiredSlate = slate;
      } else if (!requiredSlate.equals(slate)) {
        throw new IllegalArgumentException("Transient hydraulic coupling currently requires one identical component "
            + "slate on every initial and scheduled gas state; edge '" + edge.name + "' differs");
      }
      componentNames.addAll(slate);
      preparedInitialFluids.put(edge.name, fluid);
    }

    for (Map.Entry<String, SourceSchedule> entry : sourceSchedules.entrySet()) {
      SourceSchedule schedule = entry.getValue();
      SystemInterface[] fluids = new SystemInterface[schedule.fluids.length];
      for (int index = 0; index < fluids.length; index++) {
        fluids[index] = prepareOnePhaseFluid(schedule.fluids[index],
            "source schedule '" + entry.getKey() + "' index " + index);
        referenceTemperature = validateTemperature(referenceTemperature, fluids[index].getTemperature(),
            entry.getKey());
        TreeSet<String> slate = componentSlate(fluids[index]);
        if (!requiredSlate.equals(slate)) {
          throw new IllegalArgumentException("Transient hydraulic coupling currently requires one identical component "
              + "slate on every initial and scheduled gas state; source '" + entry.getKey() + "' index " + index
              + " differs");
        }
      }
      preparedSourceFluids.put(entry.getKey(), fluids);
    }
    if (componentNames.size() < 2) {
      throw new IllegalStateException(
          "Transient gas-network composition transport requires at least two named components");
    }
    String[] names = componentNames.toArray(new String[componentNames.size()]);
    Map<String, Double> molarMassKgMol = molarMasses(names, preparedInitialFluids.values().iterator().next());
    return new PreparedModel(names, preparedInitialFluids, preparedSourceFluids, molarMassKgMol, referenceTemperature);
  }

  private void initializeCompositionProfiles(PreparedModel prepared) {
    for (TransientEdge edge : edges.values()) {
      double[] initialFractions = massFractions(prepared.preparedInitialFluids.get(edge.name), prepared.componentNames);
      edge.massFractionProfile = new double[prepared.componentNames.length][edge.numberOfCells];
      for (int componentIndex = 0; componentIndex < prepared.componentNames.length; componentIndex++) {
        Arrays.fill(edge.massFractionProfile[componentIndex], initialFractions[componentIndex]);
      }
      edge.cellMassesKg = null;
      edge.runInitialComponentInventoryKg = null;
      edge.cumulativeInletComponentMassKg = new double[prepared.componentNames.length];
      edge.cumulativeOutletComponentMassKg = new double[prepared.componentNames.length];
      edge.lastStepInletComponentMassKg = new double[prepared.componentNames.length];
      edge.lastStepOutletComponentMassKg = new double[prepared.componentNames.length];
    }
  }

  private Map<String, Double> initialPressureGuesses(PreparedModel prepared) {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    double maximumInitialPressurePa = MINIMUM_PRESSURE_PA;
    for (SystemInterface fluid : prepared.preparedInitialFluids.values()) {
      maximumInitialPressurePa = Math.max(maximumInitialPressurePa, fluid.getPressure("bara") * 1.0e5);
    }
    for (NetworkNode node : nodes.values()) {
      Double fixed = fixedPressurePa.get(node.name);
      Double initial = initialPressurePa.get(node.name);
      if (fixed != null) {
        result.put(node.name, fixed);
      } else if (initial != null) {
        result.put(node.name, initial);
      } else {
        result.put(node.name, maximumInitialPressurePa);
      }
    }
    return result;
  }

  private void initializeEdgeMasses(Map<String, Double> pressuresPa, Map<String, HydraulicProperties> properties,
      String[] componentNames) {
    for (TransientEdge edge : edges.values()) {
      double fromPressure = pressuresPa.get(edge.from.name);
      double toPressure = pressuresPa.get(edge.to.name);
      HydraulicProperties property = properties.get(edge.name);
      double linepackKg = property.linepackKg(edge, fromPressure, toPressure);
      edge.cellMassesKg = distributeLinepack(edge, linepackKg, fromPressure, toPressure);
      edge.runInitialComponentInventoryKg = edge.componentInventory();
      edge.cumulativeInletComponentMassKg = new double[componentNames.length];
      edge.cumulativeOutletComponentMassKg = new double[componentNames.length];
      edge.lastStepInletComponentMassKg = new double[componentNames.length];
      edge.lastStepOutletComponentMassKg = new double[componentNames.length];
    }
  }

  private Map<String, HydraulicProperties> buildHydraulicProperties(Map<String, Double> pressuresPa,
      PreparedModel prepared) {
    Map<String, HydraulicProperties> result = new LinkedHashMap<String, HydraulicProperties>();
    for (TransientEdge edge : edges.values()) {
      double averagePressurePa = 0.5 * (pressuresPa.get(edge.from.name) + pressuresPa.get(edge.to.name));
      double[] averageMassFractions = edge.averageMassFractions();
      SystemInterface fluid = prepared.preparedInitialFluids.get(edge.name).clone();
      double[] molarComposition = molarCompositionInFluidOrder(fluid, averageMassFractions, prepared.componentNames,
          prepared.molarMassKgMol);
      fluid.setMolarComposition(molarComposition);
      fluid.setTemperature(prepared.temperatureK, "K");
      fluid.setPressure(averagePressurePa / 1.0e5, "bara");
      try {
        ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
        operations.TPflash();
        fluid.initProperties();
      } catch (Exception exception) {
        throw new IllegalStateException(
            "Unable to update compressible gas state for edge '" + edge.name + "': " + exception.getMessage(),
            exception);
      }
      if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
        throw new IllegalStateException("Unsupported phase appearance on edge '" + edge.name
            + "': transient gas-network hydraulics requires exactly one gas phase");
      }
      double densityKgM3 = fluid.getDensity("kg/m3");
      double viscosityKgMS = fluid.getViscosity("kg/msec");
      requirePositiveFinite(densityKgM3, "Gas density for edge '" + edge.name + "'");
      requirePositiveFinite(viscosityKgMS, "Gas viscosity for edge '" + edge.name + "'");
      result.put(edge.name, new HydraulicProperties(averagePressurePa, densityKgM3, viscosityKgMS));
    }
    return result;
  }

  private SolveResult solvePressures(Map<String, Double> initialPressuresPa, Map<String, Double> sourceRatesKgS,
      Map<String, HydraulicProperties> properties, Map<String, Double> previousLinepackKg, double timeStepSeconds,
      boolean transientStep) {
    List<NetworkNode> unknownNodes = new ArrayList<NetworkNode>();
    for (NetworkNode node : nodes.values()) {
      if (!fixedPressurePa.containsKey(node.name) && !sourceSchedules.containsKey(node.name)) {
        unknownNodes.add(node);
      }
    }
    double[] pressureVector = new double[unknownNodes.size()];
    for (int index = 0; index < unknownNodes.size(); index++) {
      pressureVector[index] = initialPressuresPa.get(unknownNodes.get(index).name);
    }

    ResidualEvaluation evaluation = evaluateHydraulicResidual(pressureVector, unknownNodes, sourceRatesKgS, properties,
        previousLinepackKg, timeStepSeconds, transientStep);
    for (int iteration = 0; iteration < maximumHydraulicIterations; iteration++) {
      if (evaluation.maximumAbsoluteResidualKgS <= hydraulicToleranceKgS) {
        return new SolveResult(evaluation.pressuresPa, evaluation.edgeStates, iteration,
            evaluation.maximumAbsoluteResidualKgS, evaluation.maximumRelativeResidual);
      }
      int dimension = pressureVector.length;
      double[][] jacobian = new double[dimension][dimension];
      for (int column = 0; column < dimension; column++) {
        double original = pressureVector[column];
        double perturbation = Math.max(0.1, Math.abs(original) * 1.0e-8);
        pressureVector[column] = original + perturbation;
        ResidualEvaluation perturbed = evaluateHydraulicResidual(pressureVector, unknownNodes, sourceRatesKgS,
            properties, previousLinepackKg, timeStepSeconds, transientStep);
        pressureVector[column] = original;
        for (int row = 0; row < dimension; row++) {
          jacobian[row][column] = (perturbed.residualsKgS[row] - evaluation.residualsKgS[row]) / perturbation;
        }
      }
      double[] rightHandSide = new double[evaluation.residualsKgS.length];
      for (int index = 0; index < rightHandSide.length; index++) {
        rightHandSide[index] = -evaluation.residualsKgS[index];
      }
      double[] pressureCorrection = solveLinearSystem(jacobian, rightHandSide);

      double acceptedAlpha = 0.0;
      ResidualEvaluation accepted = null;
      double alpha = 1.0;
      while (alpha >= 1.0e-4) {
        double[] trial = Arrays.copyOf(pressureVector, pressureVector.length);
        boolean validPressure = true;
        for (int index = 0; index < trial.length; index++) {
          trial[index] += alpha * pressureCorrection[index];
          validPressure &= Double.isFinite(trial[index]) && trial[index] > MINIMUM_PRESSURE_PA;
        }
        if (validPressure) {
          ResidualEvaluation trialEvaluation = evaluateHydraulicResidual(trial, unknownNodes, sourceRatesKgS,
              properties, previousLinepackKg, timeStepSeconds, transientStep);
          if (squaredNorm(trialEvaluation.residualsKgS) < squaredNorm(evaluation.residualsKgS)) {
            acceptedAlpha = alpha;
            accepted = trialEvaluation;
            pressureVector = trial;
            break;
          }
        }
        alpha *= 0.5;
      }
      if (accepted == null) {
        throw new IllegalStateException("Transient gas-network hydraulic solve failed line search after "
            + (iteration + 1) + " iteration(s); maximum node mass residual=" + evaluation.maximumAbsoluteResidualKgS
            + " kg/s; state=" + hydraulicStateDescription(unknownNodes, pressureVector, evaluation.residualsKgS));
      }
      if (!(acceptedAlpha > 0.0)) {
        throw new IllegalStateException("Transient gas-network hydraulic solve accepted no pressure update");
      }
      evaluation = accepted;
    }
    throw new IllegalStateException("Transient gas-network hydraulic solve did not converge after "
        + maximumHydraulicIterations + " iteration(s); maximum node mass residual="
        + evaluation.maximumAbsoluteResidualKgS + " kg/s exceeds tolerance " + hydraulicToleranceKgS + " kg/s");
  }

  private ResidualEvaluation evaluateHydraulicResidual(double[] pressureVector, List<NetworkNode> unknownNodes,
      Map<String, Double> sourceRatesKgS, Map<String, HydraulicProperties> properties,
      Map<String, Double> previousLinepackKg, double timeStepSeconds, boolean transientStep) {
    Map<String, Double> pressures = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, Double> entry : fixedPressurePa.entrySet()) {
      pressures.put(entry.getKey(), entry.getValue());
    }
    for (int index = 0; index < unknownNodes.size(); index++) {
      pressures.put(unknownNodes.get(index).name, pressureVector[index]);
    }

    double maximumSourceResidual = 0.0;
    double maximumSourceRelativeResidual = 0.0;
    for (Map.Entry<String, Double> sourceEntry : sourceRatesKgS.entrySet()) {
      NetworkNode source = nodes.get(sourceEntry.getKey());
      TransientEdge sourceEdge = source.outgoing.get(0);
      double sourcePressurePa = solveSourcePressure(source, sourceEdge, pressures.get(sourceEdge.to.name),
          sourceEntry.getValue(), properties.get(sourceEdge.name), previousLinepackKg, timeStepSeconds, transientStep);
      pressures.put(source.name, sourcePressurePa);
    }

    Map<String, HydraulicEdgeState> edgeStates = new LinkedHashMap<String, HydraulicEdgeState>();
    for (TransientEdge edge : edges.values()) {
      edgeStates.put(edge.name, evaluateEdgeState(edge, pressures.get(edge.from.name), pressures.get(edge.to.name),
          properties.get(edge.name), previousLinepackKg, timeStepSeconds, transientStep));
    }
    for (Map.Entry<String, Double> sourceEntry : sourceRatesKgS.entrySet()) {
      NetworkNode source = nodes.get(sourceEntry.getKey());
      HydraulicEdgeState state = edgeStates.get(source.outgoing.get(0).name);
      double residual = sourceEntry.getValue() - state.inletMassFlowKgS;
      maximumSourceResidual = Math.max(maximumSourceResidual, Math.abs(residual));
      maximumSourceRelativeResidual = Math.max(maximumSourceRelativeResidual,
          Math.abs(residual) / Math.max(1.0, sourceEntry.getValue()));
    }

    double[] residuals = new double[unknownNodes.size()];
    double maximumAbsolute = maximumSourceResidual;
    double maximumRelative = maximumSourceRelativeResidual;
    for (int index = 0; index < unknownNodes.size(); index++) {
      NetworkNode node = unknownNodes.get(index);
      double incoming = 0.0;
      double outgoing = 0.0;
      for (TransientEdge edge : node.incoming) {
        incoming += edgeStates.get(edge.name).outletMassFlowKgS;
      }
      for (TransientEdge edge : node.outgoing) {
        outgoing += edgeStates.get(edge.name).inletMassFlowKgS;
      }
      Double sourceRate = sourceRatesKgS.get(node.name);
      double externalSource = sourceRate == null ? 0.0 : sourceRate;
      residuals[index] = externalSource + incoming - outgoing;
      maximumAbsolute = Math.max(maximumAbsolute, Math.abs(residuals[index]));
      double scale = Math.max(1.0, Math.max(Math.abs(externalSource) + Math.abs(incoming), Math.abs(outgoing)));
      maximumRelative = Math.max(maximumRelative, Math.abs(residuals[index]) / scale);
    }
    return new ResidualEvaluation(residuals, pressures, edgeStates, maximumAbsolute, maximumRelative);
  }

  private double solveSourcePressure(NetworkNode source, TransientEdge edge, double downstreamPressurePa,
      double sourceRateKgS, HydraulicProperties property, Map<String, Double> previousLinepackKg,
      double timeStepSeconds, boolean transientStep) {
    double lowerPressurePa = downstreamPressurePa + Math.max(1.0e-3, downstreamPressurePa * 1.0e-12);
    double lowerResidualKgS = sourceRateKgS - evaluateEdgeState(edge, lowerPressurePa, downstreamPressurePa, property,
        previousLinepackKg, timeStepSeconds, transientStep).inletMassFlowKgS;
    if (lowerResidualKgS < 0.0) {
      throw new IllegalStateException("Unsupported reverse flow at source '" + source.name
          + "': its scheduled rate is below the minimum positive-flow linepack response");
    }

    PressureLimits limits = sourcePressureLimits.get(source.name);
    double upperLimitPa = limits == null ? 1.0e9 : limits.maximumPressurePa;
    Double initial = initialPressurePa.get(source.name);
    double upperPressurePa = Math.max(lowerPressurePa * 1.05, initial == null ? lowerPressurePa : initial);
    upperPressurePa = Math.min(upperPressurePa, upperLimitPa);
    double upperResidualKgS = sourceRateKgS - evaluateEdgeState(edge, upperPressurePa, downstreamPressurePa, property,
        previousLinepackKg, timeStepSeconds, transientStep).inletMassFlowKgS;
    while (upperResidualKgS > 0.0 && upperPressurePa < upperLimitPa) {
      upperPressurePa = Math.min(upperLimitPa, Math.max(upperPressurePa * 1.25, upperPressurePa + 1.0e5));
      upperResidualKgS = sourceRateKgS - evaluateEdgeState(edge, upperPressurePa, downstreamPressurePa, property,
          previousLinepackKg, timeStepSeconds, transientStep).inletMassFlowKgS;
    }
    if (upperResidualKgS > 0.0) {
      throw new IllegalStateException("Infeasible source pressure at '" + source.name + "': scheduled rate "
          + sourceRateKgS + " kg/s requires a pressure above " + upperLimitPa / 1.0e5 + " bara");
    }

    double pressurePa = upperPressurePa;
    for (int iteration = 0; iteration < 80; iteration++) {
      pressurePa = 0.5 * (lowerPressurePa + upperPressurePa);
      HydraulicEdgeState state = evaluateEdgeState(edge, pressurePa, downstreamPressurePa, property, previousLinepackKg,
          timeStepSeconds, transientStep);
      double residualKgS = sourceRateKgS - state.inletMassFlowKgS;
      if (Math.abs(residualKgS) <= 0.1 * hydraulicToleranceKgS) {
        return pressurePa;
      }
      if (residualKgS > 0.0) {
        lowerPressurePa = pressurePa;
      } else {
        upperPressurePa = pressurePa;
      }
    }
    return pressurePa;
  }

  private static HydraulicEdgeState evaluateEdgeState(TransientEdge edge, double fromPressurePa, double toPressurePa,
      HydraulicProperties property, Map<String, Double> previousLinepackKg, double timeStepSeconds,
      boolean transientStep) {
    double averageFlowKgS = property.massFlowKgS(edge, fromPressurePa, toPressurePa);
    double newLinepackKg = property.linepackKg(edge, fromPressurePa, toPressurePa);
    double storageRateKgS = 0.0;
    if (transientStep) {
      Double previous = previousLinepackKg.get(edge.name);
      if (previous == null) {
        throw new IllegalStateException("Missing previous linepack for edge '" + edge.name + "'");
      }
      storageRateKgS = (newLinepackKg - previous) / timeStepSeconds;
    }
    double inletFlowKgS = averageFlowKgS + 0.5 * storageRateKgS;
    double outletFlowKgS = averageFlowKgS - 0.5 * storageRateKgS;
    double[] targetCellMasses = transientStep ? distributeLinepack(edge, newLinepackKg, fromPressurePa, toPressurePa)
        : new double[0];
    return new HydraulicEdgeState(inletFlowKgS, averageFlowKgS, outletFlowKgS, newLinepackKg, targetCellMasses,
        property.averageDensityKgM3(fromPressurePa, toPressurePa));
  }

  private static double[] solveLinearSystem(double[][] matrix, double[] rightHandSide) {
    int dimension = rightHandSide.length;
    double[][] augmented = new double[dimension][dimension + 1];
    for (int row = 0; row < dimension; row++) {
      System.arraycopy(matrix[row], 0, augmented[row], 0, dimension);
      augmented[row][dimension] = rightHandSide[row];
    }
    for (int pivot = 0; pivot < dimension; pivot++) {
      int bestRow = pivot;
      for (int row = pivot + 1; row < dimension; row++) {
        if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[bestRow][pivot])) {
          bestRow = row;
        }
      }
      if (Math.abs(augmented[bestRow][pivot]) < 1.0e-16) {
        throw new IllegalStateException("Transient gas-network hydraulic Jacobian is singular");
      }
      double[] swap = augmented[pivot];
      augmented[pivot] = augmented[bestRow];
      augmented[bestRow] = swap;
      for (int row = pivot + 1; row < dimension; row++) {
        double factor = augmented[row][pivot] / augmented[pivot][pivot];
        for (int column = pivot; column <= dimension; column++) {
          augmented[row][column] -= factor * augmented[pivot][column];
        }
      }
    }
    double[] solution = new double[dimension];
    for (int row = dimension - 1; row >= 0; row--) {
      double value = augmented[row][dimension];
      for (int column = row + 1; column < dimension; column++) {
        value -= augmented[row][column] * solution[column];
      }
      solution[row] = value / augmented[row][row];
    }
    return solution;
  }

  private static double squaredNorm(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value * value;
    }
    return result;
  }

  private static String hydraulicStateDescription(List<NetworkNode> unknownNodes, double[] pressuresPa,
      double[] residualsKgS) {
    StringBuilder result = new StringBuilder("{");
    for (int index = 0; index < unknownNodes.size(); index++) {
      if (index > 0) {
        result.append(", ");
      }
      result.append(unknownNodes.get(index).name).append("=(pressureBara=").append(pressuresPa[index] / 1.0e5)
          .append(", residualKgS=").append(residualsKgS[index]).append(")");
    }
    return result.append('}').toString();
  }

  private void advanceComponents(List<NetworkNode> topologicalNodes, PreparedModel prepared, SolveResult solved,
      double stepStartSeconds, double stepEndSeconds, double timeStepSeconds, HistoryAccumulator accumulator) {
    int stepIndex = accumulator.acceptedSteps;
    for (NetworkNode node : topologicalNodes) {
      NodeBoundary boundary;
      SourceSchedule sourceSchedule = sourceSchedules.get(node.name);
      if (sourceSchedule != null) {
        int scheduleIndex = sourceSchedule.indexAt(stepStartSeconds);
        SystemInterface sourceFluid = prepared.preparedSourceFluids.get(node.name)[scheduleIndex];
        boundary = new NodeBoundary(massFractions(sourceFluid, prepared.componentNames), null);
      } else {
        boundary = mixIncomingBoundary(node, prepared.componentNames);
      }
      accumulator.nodeMassFractionHistory.get(node.name)[stepIndex] = Arrays.copyOf(boundary.massFractions,
          boundary.massFractions.length);

      if (node.outgoing.isEmpty()) {
        if (sourceSchedule != null) {
          throw new IllegalStateException("Source node '" + node.name + "' cannot be a fixed-pressure sink");
        }
        addInPlace(accumulator.cumulativeExternalOutletComponentKg, boundary.integratedIncomingComponentMassKg);
        accumulator.cumulativeExternalOutletMassKg += sum(boundary.integratedIncomingComponentMassKg);
        continue;
      }

      TransientEdge edge = node.outgoing.get(0);
      HydraulicEdgeState edgeState = solved.edgeStates.get(edge.name);
      TransientSpeciesConservationReport edgeReport = edge.advanceComponents(boundary.massFractions, edgeState,
          timeStepSeconds, stepEndSeconds, prepared.componentNames, conservationTolerance);
      accumulator.edgeSpeciesReports.get(edge.name).add(edgeReport);
      if (!edgeReport.isConverged()) {
        throw new IllegalStateException(edgeReport.getMessage());
      }

      if (sourceSchedule != null) {
        addInPlace(accumulator.cumulativeExternalInletComponentKg, edge.lastStepInletComponentMassKg);
        accumulator.cumulativeExternalInletMassKg += sum(edge.lastStepInletComponentMassKg);
      } else {
        double[] cumulativeInlet = accumulator.cumulativeJunctionInletComponentKg.get(node.name);
        double[] cumulativeOutlet = accumulator.cumulativeJunctionOutletComponentKg.get(node.name);
        addInPlace(cumulativeInlet, boundary.integratedIncomingComponentMassKg);
        addInPlace(cumulativeOutlet, edge.lastStepInletComponentMassKg);
        TransientSpeciesConservationReport junctionReport = createJunctionReport(node.name, stepEndSeconds,
            prepared.componentNames, cumulativeInlet, cumulativeOutlet);
        accumulator.junctionSpeciesReports.get(node.name).add(junctionReport);
        if (!junctionReport.isConverged()) {
          throw new IllegalStateException(junctionReport.getMessage());
        }
      }
    }

    double[] currentComponentInventory = totalComponentInventory(prepared.componentNames.length);
    TransientSpeciesConservationReport networkReport = createNetworkReport(stepEndSeconds, prepared.componentNames,
        accumulator.initialComponentInventoryKg, currentComponentInventory,
        accumulator.cumulativeExternalInletComponentKg, accumulator.cumulativeExternalOutletComponentKg);
    accumulator.networkSpeciesReports.add(networkReport);
    if (!networkReport.isConverged()) {
      throw new IllegalStateException(networkReport.getMessage());
    }
  }

  private NodeBoundary mixIncomingBoundary(NetworkNode node, String[] componentNames) {
    double[] incomingComponentMassKg = new double[componentNames.length];
    for (TransientEdge edge : node.incoming) {
      addInPlace(incomingComponentMassKg, edge.lastStepOutletComponentMassKg);
    }
    double totalIncomingMassKg = sum(incomingComponentMassKg);
    if (!(totalIncomingMassKg > 0.0) || !Double.isFinite(totalIncomingMassKg)) {
      throw new IllegalStateException("Junction or sink '" + node.name + "' requires strictly positive incoming flow");
    }
    double[] fractions = new double[componentNames.length];
    for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
      fractions[componentIndex] = incomingComponentMassKg[componentIndex] / totalIncomingMassKg;
    }
    return new NodeBoundary(fractions, incomingComponentMassKg);
  }

  private void recordAcceptedStep(int stepIndex, double elapsedTimeSeconds, SolveResult solved,
      Map<String, Double> pressuresPa, HistoryAccumulator accumulator) {
    accumulator.elapsedTimeSeconds[stepIndex] = elapsedTimeSeconds;
    for (NetworkNode node : nodes.values()) {
      accumulator.nodePressureBaraHistory.get(node.name)[stepIndex] = pressuresPa.get(node.name) / 1.0e5;
    }
    double sinkMassFlowKgS = 0.0;
    for (TransientEdge edge : edges.values()) {
      HydraulicEdgeState state = solved.edgeStates.get(edge.name);
      accumulator.edgeInletMassFlowKgSHistory.get(edge.name)[stepIndex] = state.inletMassFlowKgS;
      accumulator.edgeAverageMassFlowKgSHistory.get(edge.name)[stepIndex] = state.averageMassFlowKgS;
      accumulator.edgeOutletMassFlowKgSHistory.get(edge.name)[stepIndex] = state.outletMassFlowKgS;
      accumulator.edgeLinepackKgHistory.get(edge.name)[stepIndex] = state.linepackKg;
      if (fixedPressurePa.containsKey(edge.to.name)) {
        sinkMassFlowKgS += state.outletMassFlowKgS;
      }
    }

    double currentLinepackKg = totalLinepackKg();
    double totalMassResidualKg = currentLinepackKg - accumulator.initialTotalLinepackKg
        - accumulator.cumulativeExternalInletMassKg + accumulator.cumulativeExternalOutletMassKg;
    double totalMassScaleKg = Math.max(MINIMUM_SCALE,
        Math.max(Math.max(Math.abs(accumulator.initialTotalLinepackKg), Math.abs(currentLinepackKg)),
            Math.max(Math.abs(accumulator.cumulativeExternalInletMassKg),
                Math.abs(accumulator.cumulativeExternalOutletMassKg))));
    double relativeTotalMassResidual = totalMassResidualKg / totalMassScaleKg;
    TransientSpeciesConservationReport networkSpecies = accumulator.networkSpeciesReports
        .get(accumulator.networkSpeciesReports.size() - 1);
    double maximumJunctionResidual = 0.0;
    for (List<TransientSpeciesConservationReport> reports : accumulator.junctionSpeciesReports.values()) {
      if (!reports.isEmpty()) {
        maximumJunctionResidual = Math.max(maximumJunctionResidual,
            reports.get(reports.size() - 1).getMaximumRelativeInventoryResidual());
      }
    }
    boolean converged = solved.maximumAbsoluteResidualKgS <= hydraulicToleranceKgS
        && Math.abs(relativeTotalMassResidual) <= conservationTolerance
        && networkSpecies.getMaximumRelativeInventoryResidual() <= conservationTolerance
        && maximumJunctionResidual <= conservationTolerance;
    String message = "Transient gas-network step " + (converged ? "converged" : "failed") + " at t="
        + elapsedTimeSeconds + " s; hydraulic residual=" + solved.maximumAbsoluteResidualKgS
        + " kg/s, relative total-mass residual=" + relativeTotalMassResidual + ", maximum component residual="
        + networkSpecies.getMaximumRelativeInventoryResidual() + ", maximum junction residual="
        + maximumJunctionResidual + ".";
    TransientGasNetworkStepReport stepReport = new TransientGasNetworkStepReport(elapsedTimeSeconds, solved.iterations,
        solved.maximumAbsoluteResidualKgS, solved.maximumRelativeResidual, totalMassResidualKg,
        relativeTotalMassResidual, networkSpecies.getMaximumRelativeInventoryResidual(), maximumJunctionResidual,
        sinkMassFlowKgS, converged, message);
    accumulator.stepReports.add(stepReport);
    if (!converged) {
      throw new IllegalStateException(message);
    }
    accumulator.acceptedSteps++;
  }

  private TransientSpeciesConservationReport createJunctionReport(String nodeName, double elapsedTimeSeconds,
      String[] componentNames, double[] incomingMassKg, double[] outgoingMassKg) {
    double[] residualKg = new double[componentNames.length];
    double[] relativeResidual = new double[componentNames.length];
    double maximumRelativeResidual = 0.0;
    for (int index = 0; index < componentNames.length; index++) {
      residualKg[index] = outgoingMassKg[index] - incomingMassKg[index];
      double scale = Math.max(MINIMUM_SCALE,
          Math.max(Math.abs(incomingMassKg[index]), Math.abs(outgoingMassKg[index])));
      relativeResidual[index] = residualKg[index] / scale;
      maximumRelativeResidual = Math.max(maximumRelativeResidual, Math.abs(relativeResidual[index]));
    }
    boolean converged = maximumRelativeResidual <= conservationTolerance;
    String message = "Junction '" + nodeName + "' component-name mixing " + (converged ? "converged" : "failed")
        + " with maximum relative residual=" + maximumRelativeResidual + ".";
    return new TransientSpeciesConservationReport(nodeName, TransientSpeciesConservationReport.LocationType.JUNCTION,
        elapsedTimeSeconds, componentNames, new double[0][0], new double[componentNames.length],
        new double[componentNames.length], incomingMassKg, outgoingMassKg, residualKg, relativeResidual,
        maximumRelativeResidual, Double.NaN, Double.NaN, Double.NaN, converged, message);
  }

  private TransientSpeciesConservationReport createNetworkReport(double elapsedTimeSeconds, String[] componentNames,
      double[] initialInventoryKg, double[] finalInventoryKg, double[] cumulativeInletKg, double[] cumulativeOutletKg) {
    double[] residualKg = new double[componentNames.length];
    double[] relativeResidual = new double[componentNames.length];
    double maximumRelativeResidual = 0.0;
    for (int index = 0; index < componentNames.length; index++) {
      residualKg[index] = finalInventoryKg[index] - initialInventoryKg[index] - cumulativeInletKg[index]
          + cumulativeOutletKg[index];
      double scale = balanceScale(initialInventoryKg[index], finalInventoryKg[index], cumulativeInletKg[index],
          cumulativeOutletKg[index]);
      relativeResidual[index] = residualKg[index] / scale;
      maximumRelativeResidual = Math.max(maximumRelativeResidual, Math.abs(relativeResidual[index]));
    }
    boolean converged = maximumRelativeResidual <= conservationTolerance;
    String message = "Network '" + name + "' cumulative component balance " + (converged ? "converged" : "failed")
        + " with maximum relative residual=" + maximumRelativeResidual + ".";
    return new TransientSpeciesConservationReport(name, TransientSpeciesConservationReport.LocationType.NETWORK,
        elapsedTimeSeconds, componentNames, new double[0][0], initialInventoryKg, finalInventoryKg, cumulativeInletKg,
        cumulativeOutletKg, residualKg, relativeResidual, maximumRelativeResidual, Double.NaN, Double.NaN, Double.NaN,
        converged, message);
  }

  private void checkPressureAndCapacity(Map<String, Double> pressuresPa, Map<String, HydraulicEdgeState> edgeStates) {
    for (Map.Entry<String, PressureLimits> entry : sourcePressureLimits.entrySet()) {
      if (!sourceSchedules.containsKey(entry.getKey())) {
        throw new IllegalStateException("Pressure limits are configured for non-source node '" + entry.getKey() + "'");
      }
      double pressurePa = pressuresPa.get(entry.getKey());
      PressureLimits limits = entry.getValue();
      if (pressurePa < limits.minimumPressurePa || pressurePa > limits.maximumPressurePa) {
        throw new IllegalStateException(
            "Infeasible source pressure at '" + entry.getKey() + "': solved " + pressurePa / 1.0e5 + " bara outside ["
                + limits.minimumPressurePa / 1.0e5 + ", " + limits.maximumPressurePa / 1.0e5 + "] bara");
      }
    }
    for (TransientEdge edge : edges.values()) {
      HydraulicEdgeState state = edgeStates.get(edge.name);
      if (!(state.inletMassFlowKgS > 0.0) || !(state.averageMassFlowKgS > 0.0) || !(state.outletMassFlowKgS > 0.0)) {
        throw new IllegalStateException(
            "Unsupported reverse flow on edge '" + edge.name + "': solved inlet/average/outlet mass flows are "
                + state.inletMassFlowKgS + "/" + state.averageMassFlowKgS + "/" + state.outletMassFlowKgS + " kg/s");
      }
      double areaM2 = edge.areaM2();
      double maximumFlowKgS = Math.max(state.inletMassFlowKgS,
          Math.max(state.averageMassFlowKgS, state.outletMassFlowKgS));
      double velocityMPerS = maximumFlowKgS / (state.averageDensityKgM3 * areaM2);
      if (velocityMPerS > edge.maximumVelocityMPerS) {
        throw new IllegalStateException("Infeasible edge capacity on '" + edge.name + "': solved average-gas velocity "
            + velocityMPerS + " m/s exceeds limit " + edge.maximumVelocityMPerS + " m/s");
      }
    }
  }

  private Map<String, Double> sourceRatesAt(double elapsedTimeSeconds) {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, SourceSchedule> entry : sourceSchedules.entrySet()) {
      int scheduleIndex = entry.getValue().indexAt(elapsedTimeSeconds);
      result.put(entry.getKey(), entry.getValue().massFlowRatesKgS[scheduleIndex]);
    }
    return result;
  }

  private Map<String, Double> currentLinepackByEdge() {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    for (TransientEdge edge : edges.values()) {
      result.put(edge.name, sum(edge.cellMassesKg));
    }
    return result;
  }

  private double totalLinepackKg() {
    double result = 0.0;
    for (TransientEdge edge : edges.values()) {
      result += sum(edge.cellMassesKg);
    }
    return result;
  }

  private double[] totalComponentInventory(int componentCount) {
    double[] result = new double[componentCount];
    for (TransientEdge edge : edges.values()) {
      addInPlace(result, edge.componentInventory());
    }
    return result;
  }

  private static double[] distributeLinepack(TransientEdge edge, double linepackKg, double fromPressurePa,
      double toPressurePa) {
    requirePositiveFinite(linepackKg, "Edge linepack for '" + edge.name + "'");
    double[] weights = new double[edge.numberOfCells];
    double weightSum = 0.0;
    for (int cellIndex = 0; cellIndex < edge.numberOfCells; cellIndex++) {
      double fraction = (cellIndex + 0.5) / edge.numberOfCells;
      double cellPressurePa = fromPressurePa + fraction * (toPressurePa - fromPressurePa);
      if (!(cellPressurePa > MINIMUM_PRESSURE_PA) || !Double.isFinite(cellPressurePa)) {
        throw new IllegalStateException("Non-physical cell pressure on edge '" + edge.name + "'");
      }
      weights[cellIndex] = cellPressurePa;
      weightSum += cellPressurePa;
    }
    double[] result = new double[edge.numberOfCells];
    for (int cellIndex = 0; cellIndex < edge.numberOfCells; cellIndex++) {
      result[cellIndex] = linepackKg * weights[cellIndex] / weightSum;
    }
    return result;
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
          + ": transient gas-network hydraulics requires exactly one gas phase");
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
          + temperature + " K instead of network temperature " + referenceTemperature + " K");
    }
    return referenceTemperature;
  }

  private static TreeSet<String> componentSlate(SystemInterface fluid) {
    TreeSet<String> result = new TreeSet<String>();
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      result.add(fluid.getPhase(0).getComponent(index).getComponentName());
    }
    return result;
  }

  private static Map<String, Double> molarMasses(String[] componentNames, SystemInterface fluid) {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    for (String componentName : componentNames) {
      double molarMass = fluid.getPhase(0).getComponent(componentName).getMolarMass();
      requirePositiveFinite(molarMass, "Molar mass for '" + componentName + "'");
      result.put(componentName, molarMass);
    }
    return result;
  }

  private static double[] massFractions(SystemInterface fluid, String[] componentNames) {
    Map<String, Double> componentMassByName = new LinkedHashMap<String, Double>();
    double[] molarComposition = fluid.getMolarComposition();
    double totalMass = 0.0;
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      String componentName = fluid.getPhase(0).getComponent(index).getComponentName();
      double componentMass = molarComposition[index] * fluid.getPhase(0).getComponent(index).getMolarMass();
      componentMassByName.put(componentName, componentMass);
      totalMass += componentMass;
    }
    requirePositiveFinite(totalMass, "Composition mass basis");
    double[] result = new double[componentNames.length];
    for (int index = 0; index < componentNames.length; index++) {
      result[index] = componentMassByName.get(componentNames[index]) / totalMass;
    }
    return result;
  }

  private static double[] molarCompositionInFluidOrder(SystemInterface fluid, double[] massFractions,
      String[] componentNames, Map<String, Double> molarMassKgMol) {
    Map<String, Integer> canonicalIndex = new LinkedHashMap<String, Integer>();
    for (int index = 0; index < componentNames.length; index++) {
      canonicalIndex.put(componentNames[index], index);
    }
    double[] result = new double[fluid.getNumberOfComponents()];
    double totalMoles = 0.0;
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      String componentName = fluid.getPhase(0).getComponent(index).getComponentName();
      Integer sourceIndex = canonicalIndex.get(componentName);
      if (sourceIndex == null) {
        throw new IllegalStateException("Component '" + componentName + "' is not in the canonical network slate");
      }
      result[index] = massFractions[sourceIndex] / molarMassKgMol.get(componentName);
      totalMoles += result[index];
    }
    requirePositiveFinite(totalMoles, "Molar composition basis");
    for (int index = 0; index < result.length; index++) {
      result[index] /= totalMoles;
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

  private TransientEdge requireEdge(String edgeName) {
    TransientEdge edge = edges.get(edgeName);
    if (edge == null) {
      throw new IllegalArgumentException("Edge '" + edgeName + "' does not exist");
    }
    return edge;
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
    return Math.max(MINIMUM_SCALE,
        Math.max(Math.max(Math.abs(initial), Math.abs(closing)), Math.max(Math.abs(inlet), Math.abs(outlet))));
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

  private static final class PressureLimits implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double minimumPressurePa;
    private final double maximumPressurePa;

    private PressureLimits(double minimumPressurePa, double maximumPressurePa) {
      this.minimumPressurePa = minimumPressurePa;
      this.maximumPressurePa = maximumPressurePa;
    }
  }

  private static final class TransientEdge implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final NetworkNode from;
    private final NetworkNode to;
    private final double lengthMeters;
    private final double diameterMeters;
    private final double roughnessMeters;
    private final int numberOfCells;
    private final SystemInterface initialFluid;
    private double maximumVelocityMPerS = Double.POSITIVE_INFINITY;
    private double[] cellMassesKg;
    private double[][] massFractionProfile;
    private double[] runInitialComponentInventoryKg;
    private double[] cumulativeInletComponentMassKg;
    private double[] cumulativeOutletComponentMassKg;
    private double[] lastStepInletComponentMassKg;
    private double[] lastStepOutletComponentMassKg;

    private TransientEdge(String name, NetworkNode from, NetworkNode to, double lengthMeters, double diameterMeters,
        double roughnessMeters, int numberOfCells, SystemInterface initialFluid) {
      this.name = name;
      this.from = from;
      this.to = to;
      this.lengthMeters = lengthMeters;
      this.diameterMeters = diameterMeters;
      this.roughnessMeters = roughnessMeters;
      this.numberOfCells = numberOfCells;
      this.initialFluid = initialFluid;
    }

    private double areaM2() {
      return Math.PI * diameterMeters * diameterMeters / 4.0;
    }

    private double volumeM3() {
      return areaM2() * lengthMeters;
    }

    private double[] averageMassFractions() {
      double[] result = new double[massFractionProfile.length];
      if (cellMassesKg == null) {
        for (int componentIndex = 0; componentIndex < result.length; componentIndex++) {
          double total = 0.0;
          for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
            total += massFractionProfile[componentIndex][cellIndex];
          }
          result[componentIndex] = total / numberOfCells;
        }
        return result;
      }
      double totalMassKg = sum(cellMassesKg);
      for (int componentIndex = 0; componentIndex < result.length; componentIndex++) {
        for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
          result[componentIndex] += cellMassesKg[cellIndex] * massFractionProfile[componentIndex][cellIndex];
        }
        result[componentIndex] /= totalMassKg;
      }
      return result;
    }

    private double[] componentInventory() {
      double[] result = new double[massFractionProfile.length];
      for (int componentIndex = 0; componentIndex < massFractionProfile.length; componentIndex++) {
        for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
          result[componentIndex] += cellMassesKg[cellIndex] * massFractionProfile[componentIndex][cellIndex];
        }
      }
      return result;
    }

    private TransientSpeciesConservationReport advanceComponents(double[] inletMassFractions,
        HydraulicEdgeState hydraulicState, double timeStepSeconds, double elapsedTimeSeconds, String[] componentNames,
        double tolerance) {
      double[] oldCellMassesKg = Arrays.copyOf(cellMassesKg, cellMassesKg.length);
      double[][] oldProfile = copy(massFractionProfile);
      double[] stepInitialInventoryKg = componentInventory();
      double[] newCellMassesKg = Arrays.copyOf(hydraulicState.targetCellMassesKg,
          hydraulicState.targetCellMassesKg.length);
      double[] faceMassFlowKgS = new double[numberOfCells + 1];
      faceMassFlowKgS[0] = hydraulicState.inletMassFlowKgS;
      for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
        double storageRateKgS = (newCellMassesKg[cellIndex] - oldCellMassesKg[cellIndex]) / timeStepSeconds;
        faceMassFlowKgS[cellIndex + 1] = faceMassFlowKgS[cellIndex] - storageRateKgS;
      }
      double outletFlowDifferenceKgS = faceMassFlowKgS[numberOfCells] - hydraulicState.outletMassFlowKgS;
      if (Math.abs(outletFlowDifferenceKgS) > 1.0e-7 * Math.max(1.0, Math.abs(hydraulicState.outletMassFlowKgS))) {
        throw new IllegalStateException("Edge '" + name + "' cell linepack does not close solved outlet flow; residual="
            + outletFlowDifferenceKgS + " kg/s");
      }
      for (double faceFlowKgS : faceMassFlowKgS) {
        if (!(faceFlowKgS > 0.0) || !Double.isFinite(faceFlowKgS)) {
          throw new IllegalStateException(
              "Unsupported reverse flow on edge '" + name + "': a finite-volume face flow is " + faceFlowKgS + " kg/s");
        }
      }

      double[] inletComponentMassKg = new double[componentNames.length];
      double[] outletComponentMassKg = new double[componentNames.length];
      double[][] updatedProfile = new double[componentNames.length][numberOfCells];
      for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
        double upstreamMassFraction = inletMassFractions[componentIndex];
        inletComponentMassKg[componentIndex] = timeStepSeconds * faceMassFlowKgS[0] * upstreamMassFraction;
        for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
          double numerator = oldCellMassesKg[cellIndex] * oldProfile[componentIndex][cellIndex]
              + timeStepSeconds * faceMassFlowKgS[cellIndex] * upstreamMassFraction;
          double denominator = newCellMassesKg[cellIndex] + timeStepSeconds * faceMassFlowKgS[cellIndex + 1];
          double updatedMassFraction = numerator / denominator;
          updatedProfile[componentIndex][cellIndex] = updatedMassFraction;
          upstreamMassFraction = updatedMassFraction;
        }
        outletComponentMassKg[componentIndex] = timeStepSeconds * faceMassFlowKgS[numberOfCells] * upstreamMassFraction;
      }

      cellMassesKg = newCellMassesKg;
      massFractionProfile = updatedProfile;
      double[] finalInventoryKg = componentInventory();
      lastStepInletComponentMassKg = Arrays.copyOf(inletComponentMassKg, inletComponentMassKg.length);
      lastStepOutletComponentMassKg = Arrays.copyOf(outletComponentMassKg, outletComponentMassKg.length);
      addInPlace(cumulativeInletComponentMassKg, inletComponentMassKg);
      addInPlace(cumulativeOutletComponentMassKg, outletComponentMassKg);

      double maximumStepRelativeResidual = 0.0;
      double[] cumulativeResidualKg = new double[componentNames.length];
      double[] cumulativeRelativeResidual = new double[componentNames.length];
      double maximumCumulativeRelativeResidual = 0.0;
      for (int index = 0; index < componentNames.length; index++) {
        double stepResidualKg = finalInventoryKg[index] - stepInitialInventoryKg[index] - inletComponentMassKg[index]
            + outletComponentMassKg[index];
        double stepScale = balanceScale(stepInitialInventoryKg[index], finalInventoryKg[index],
            inletComponentMassKg[index], outletComponentMassKg[index]);
        maximumStepRelativeResidual = Math.max(maximumStepRelativeResidual, Math.abs(stepResidualKg / stepScale));
        cumulativeResidualKg[index] = finalInventoryKg[index] - runInitialComponentInventoryKg[index]
            - cumulativeInletComponentMassKg[index] + cumulativeOutletComponentMassKg[index];
        double cumulativeScale = balanceScale(runInitialComponentInventoryKg[index], finalInventoryKg[index],
            cumulativeInletComponentMassKg[index], cumulativeOutletComponentMassKg[index]);
        cumulativeRelativeResidual[index] = cumulativeResidualKg[index] / cumulativeScale;
        maximumCumulativeRelativeResidual = Math.max(maximumCumulativeRelativeResidual,
            Math.abs(cumulativeRelativeResidual[index]));
      }

      double minimumMassFraction = Double.POSITIVE_INFINITY;
      double maximumMassFraction = Double.NEGATIVE_INFINITY;
      double maximumSumError = 0.0;
      for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
        double cellSum = 0.0;
        for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
          double value = updatedProfile[componentIndex][cellIndex];
          minimumMassFraction = Math.min(minimumMassFraction, value);
          maximumMassFraction = Math.max(maximumMassFraction, value);
          cellSum += value;
        }
        maximumSumError = Math.max(maximumSumError, Math.abs(cellSum - 1.0));
      }
      boolean bounded = minimumMassFraction >= -tolerance && maximumMassFraction <= 1.0 + tolerance
          && maximumSumError <= tolerance;
      boolean converged = maximumStepRelativeResidual <= tolerance && maximumCumulativeRelativeResidual <= tolerance
          && bounded;
      String message = "Edge '" + name + "' coupled linepack/species step " + (converged ? "converged" : "failed")
          + "; cumulative maximum relative residual=" + maximumCumulativeRelativeResidual
          + ", step maximum relative residual=" + maximumStepRelativeResidual + ", mass-fraction range=["
          + minimumMassFraction + ", " + maximumMassFraction + "], maximum sum error=" + maximumSumError + ".";
      return new TransientSpeciesConservationReport(name, TransientSpeciesConservationReport.LocationType.EDGE,
          elapsedTimeSeconds, componentNames, updatedProfile, runInitialComponentInventoryKg, finalInventoryKg,
          cumulativeInletComponentMassKg, cumulativeOutletComponentMassKg, cumulativeResidualKg,
          cumulativeRelativeResidual, maximumCumulativeRelativeResidual, minimumMassFraction, maximumMassFraction,
          maximumSumError, converged, message);
    }

    private static double[][] copy(double[][] values) {
      double[][] result = new double[values.length][];
      for (int index = 0; index < values.length; index++) {
        result[index] = Arrays.copyOf(values[index], values[index].length);
      }
      return result;
    }
  }

  private static final class HydraulicProperties {
    private final double referencePressurePa;
    private final double referenceDensityKgM3;
    private final double viscosityKgMS;

    private HydraulicProperties(double referencePressurePa, double referenceDensityKgM3, double viscosityKgMS) {
      this.referencePressurePa = referencePressurePa;
      this.referenceDensityKgM3 = referenceDensityKgM3;
      this.viscosityKgMS = viscosityKgMS;
    }

    private double averageDensityKgM3(double fromPressurePa, double toPressurePa) {
      return referenceDensityKgM3 * 0.5 * (fromPressurePa + toPressurePa) / referencePressurePa;
    }

    private double linepackKg(TransientEdge edge, double fromPressurePa, double toPressurePa) {
      return averageDensityKgM3(fromPressurePa, toPressurePa) * edge.volumeM3();
    }

    private double massFlowKgS(TransientEdge edge, double fromPressurePa, double toPressurePa) {
      double pressureSquaredDifference = fromPressurePa * fromPressurePa - toPressurePa * toPressurePa;
      if (Math.abs(pressureSquaredDifference) < 1.0e-12) {
        return 0.0;
      }
      double sign = Math.signum(pressureSquaredDifference);
      double absoluteDifference = Math.abs(pressureSquaredDifference);
      double pressureDensityRatio = referencePressurePa / referenceDensityKgM3;
      double areaM2 = edge.areaM2();
      double frictionFactor = 0.012;
      double flowKgS = 0.0;
      for (int iteration = 0; iteration < 6; iteration++) {
        double resistance = frictionFactor * edge.lengthMeters / edge.diameterMeters * pressureDensityRatio
            / (areaM2 * areaM2);
        flowKgS = Math.sqrt(absoluteDifference / resistance);
        double reynoldsNumber = flowKgS * edge.diameterMeters / (areaM2 * viscosityKgMS);
        if (reynoldsNumber < 1.0e-12) {
          frictionFactor = 0.012;
        } else if (reynoldsNumber < 2300.0) {
          frictionFactor = 64.0 / reynoldsNumber;
        } else {
          double relativeRoughness = edge.roughnessMeters / edge.diameterMeters;
          double term = relativeRoughness / 3.7 + 5.74 / Math.pow(reynoldsNumber, 0.9);
          frictionFactor = 0.25 / Math.pow(Math.log10(term), 2.0);
        }
      }
      return sign * flowKgS;
    }
  }

  private static final class HydraulicEdgeState {
    private final double inletMassFlowKgS;
    private final double averageMassFlowKgS;
    private final double outletMassFlowKgS;
    private final double linepackKg;
    private final double[] targetCellMassesKg;
    private final double averageDensityKgM3;

    private HydraulicEdgeState(double inletMassFlowKgS, double averageMassFlowKgS, double outletMassFlowKgS,
        double linepackKg, double[] targetCellMassesKg, double averageDensityKgM3) {
      this.inletMassFlowKgS = inletMassFlowKgS;
      this.averageMassFlowKgS = averageMassFlowKgS;
      this.outletMassFlowKgS = outletMassFlowKgS;
      this.linepackKg = linepackKg;
      this.targetCellMassesKg = targetCellMassesKg;
      this.averageDensityKgM3 = averageDensityKgM3;
    }
  }

  private static final class ResidualEvaluation {
    private final double[] residualsKgS;
    private final Map<String, Double> pressuresPa;
    private final Map<String, HydraulicEdgeState> edgeStates;
    private final double maximumAbsoluteResidualKgS;
    private final double maximumRelativeResidual;

    private ResidualEvaluation(double[] residualsKgS, Map<String, Double> pressuresPa,
        Map<String, HydraulicEdgeState> edgeStates, double maximumAbsoluteResidualKgS, double maximumRelativeResidual) {
      this.residualsKgS = residualsKgS;
      this.pressuresPa = pressuresPa;
      this.edgeStates = edgeStates;
      this.maximumAbsoluteResidualKgS = maximumAbsoluteResidualKgS;
      this.maximumRelativeResidual = maximumRelativeResidual;
    }
  }

  private static final class SolveResult {
    private final Map<String, Double> pressuresPa;
    private final Map<String, HydraulicEdgeState> edgeStates;
    private final int iterations;
    private final double maximumAbsoluteResidualKgS;
    private final double maximumRelativeResidual;

    private SolveResult(Map<String, Double> pressuresPa, Map<String, HydraulicEdgeState> edgeStates, int iterations,
        double maximumAbsoluteResidualKgS, double maximumRelativeResidual) {
      this.pressuresPa = pressuresPa;
      this.edgeStates = edgeStates;
      this.iterations = iterations;
      this.maximumAbsoluteResidualKgS = maximumAbsoluteResidualKgS;
      this.maximumRelativeResidual = maximumRelativeResidual;
    }
  }

  private static final class NodeBoundary {
    private final double[] massFractions;
    private final double[] integratedIncomingComponentMassKg;

    private NodeBoundary(double[] massFractions, double[] integratedIncomingComponentMassKg) {
      this.massFractions = massFractions;
      this.integratedIncomingComponentMassKg = integratedIncomingComponentMassKg;
    }
  }

  private static final class PreparedModel {
    private final String[] componentNames;
    private final Map<String, SystemInterface> preparedInitialFluids;
    private final Map<String, SystemInterface[]> preparedSourceFluids;
    private final Map<String, Double> molarMassKgMol;
    private final double temperatureK;

    private PreparedModel(String[] componentNames, Map<String, SystemInterface> preparedInitialFluids,
        Map<String, SystemInterface[]> preparedSourceFluids, Map<String, Double> molarMassKgMol, double temperatureK) {
      this.componentNames = Arrays.copyOf(componentNames, componentNames.length);
      this.preparedInitialFluids = Collections.unmodifiableMap(preparedInitialFluids);
      this.preparedSourceFluids = Collections.unmodifiableMap(preparedSourceFluids);
      this.molarMassKgMol = Collections.unmodifiableMap(molarMassKgMol);
      this.temperatureK = temperatureK;
    }
  }

  private static final class HistoryAccumulator {
    private final double[] elapsedTimeSeconds;
    private final String[] componentNames;
    private final Map<String, double[]> nodePressureBaraHistory = new LinkedHashMap<String, double[]>();
    private final Map<String, double[][]> nodeMassFractionHistory = new LinkedHashMap<String, double[][]>();
    private final Map<String, double[]> edgeInletMassFlowKgSHistory = new LinkedHashMap<String, double[]>();
    private final Map<String, double[]> edgeAverageMassFlowKgSHistory = new LinkedHashMap<String, double[]>();
    private final Map<String, double[]> edgeOutletMassFlowKgSHistory = new LinkedHashMap<String, double[]>();
    private final Map<String, double[]> edgeLinepackKgHistory = new LinkedHashMap<String, double[]>();
    private final List<TransientGasNetworkStepReport> stepReports = new ArrayList<TransientGasNetworkStepReport>();
    private final Map<String, List<TransientSpeciesConservationReport>> edgeSpeciesReports = new LinkedHashMap<String, List<TransientSpeciesConservationReport>>();
    private final Map<String, List<TransientSpeciesConservationReport>> junctionSpeciesReports = new LinkedHashMap<String, List<TransientSpeciesConservationReport>>();
    private final List<TransientSpeciesConservationReport> networkSpeciesReports = new ArrayList<TransientSpeciesConservationReport>();
    private final Map<String, double[]> cumulativeJunctionInletComponentKg = new LinkedHashMap<String, double[]>();
    private final Map<String, double[]> cumulativeJunctionOutletComponentKg = new LinkedHashMap<String, double[]>();
    private final double[] cumulativeExternalInletComponentKg;
    private final double[] cumulativeExternalOutletComponentKg;
    private double initialTotalLinepackKg;
    private double[] initialComponentInventoryKg;
    private double cumulativeExternalInletMassKg;
    private double cumulativeExternalOutletMassKg;
    private int acceptedSteps;

    private HistoryAccumulator(int stepCount, String[] componentNames, Map<String, NetworkNode> nodes,
        Map<String, TransientEdge> edges, Map<String, SourceSchedule> sourceSchedules) {
      this.elapsedTimeSeconds = new double[stepCount];
      this.componentNames = Arrays.copyOf(componentNames, componentNames.length);
      this.cumulativeExternalInletComponentKg = new double[componentNames.length];
      this.cumulativeExternalOutletComponentKg = new double[componentNames.length];
      for (NetworkNode node : nodes.values()) {
        nodePressureBaraHistory.put(node.name, new double[stepCount]);
        nodeMassFractionHistory.put(node.name, new double[stepCount][componentNames.length]);
        if (!sourceSchedules.containsKey(node.name) && !node.outgoing.isEmpty()) {
          junctionSpeciesReports.put(node.name, new ArrayList<TransientSpeciesConservationReport>());
          cumulativeJunctionInletComponentKg.put(node.name, new double[componentNames.length]);
          cumulativeJunctionOutletComponentKg.put(node.name, new double[componentNames.length]);
        }
      }
      for (TransientEdge edge : edges.values()) {
        edgeInletMassFlowKgSHistory.put(edge.name, new double[stepCount]);
        edgeAverageMassFlowKgSHistory.put(edge.name, new double[stepCount]);
        edgeOutletMassFlowKgSHistory.put(edge.name, new double[stepCount]);
        edgeLinepackKgHistory.put(edge.name, new double[stepCount]);
        edgeSpeciesReports.put(edge.name, new ArrayList<TransientSpeciesConservationReport>());
      }
    }

    private TransientGasNetworkHistory toHistory() {
      return new TransientGasNetworkHistory(elapsedTimeSeconds, componentNames, nodePressureBaraHistory,
          nodeMassFractionHistory, edgeInletMassFlowKgSHistory, edgeAverageMassFlowKgSHistory,
          edgeOutletMassFlowKgSHistory, edgeLinepackKgHistory, stepReports, edgeSpeciesReports, junctionSpeciesReports,
          networkSpeciesReports);
    }
  }
}
