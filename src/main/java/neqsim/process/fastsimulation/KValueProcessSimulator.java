package neqsim.process.fastsimulation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Fast process simulator that reuses cached unit K-values from one rigorous base-case run.
 *
 * <p>
 * The simulator walks the same process graph as the original {@link ProcessSystem}. Source streams inject component
 * molar flows, separator-like units use frozen {@code K = y/x} values with Rachford-Rice routing, and other units use
 * conservative frozen split factors. Recycle loops are handled by fixed-point iteration over stream component flows.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class KValueProcessSimulator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Small flow used for numerical guards. */
  private static final double MIN_FLOW = 1.0e-30;

  /** Base-case process. */
  private final ProcessSystem process;

  /** Component names used by all vectors. */
  private final String[] componentNames;

  /** Component molar masses in kg/mol. */
  private final double[] molarMass;

  /** Unit profiles in process order. */
  private final List<KValueUnitProfile> unitProfiles;

  /** Source streams in process order. */
  private final List<StreamInterface> sourceStreams = new ArrayList<StreamInterface>();

  /** Source streams by name. */
  private final Map<String, StreamInterface> sourceByName = new LinkedHashMap<String, StreamInterface>();

  /** Base source component flows in mole/sec keyed by source stream name. */
  private final Map<String, double[]> baseSourceComponentFlows = new LinkedHashMap<String, double[]>();

  /** Stream producers keyed by stream object identity. */
  private final Map<StreamInterface, KValueUnitProfile> streamProducer = new IdentityHashMap<StreamInterface, KValueUnitProfile>();

  /** Outlet index for each produced stream keyed by stream object identity. */
  private final Map<StreamInterface, Integer> streamProducerOutletIndex = new IdentityHashMap<StreamInterface, Integer>();

  /** Stream consumers keyed by stream object identity. */
  private final Map<StreamInterface, List<KValueUnitProfile>> streamConsumers = new IdentityHashMap<StreamInterface, List<KValueUnitProfile>>();

  /** Base fluid-object aliases from wrapper streams to internal outlet keys. */
  private final Map<SystemInterface, String> fluidAliasKeys = new IdentityHashMap<SystemInterface, String>();

  /** Terminal outlet keys. */
  private final List<String> terminalStreamNames = new ArrayList<String>();

  /** Maximum fixed-point iterations. */
  private int maxIterations = 50;

  /** Fixed-point convergence tolerance. */
  private double tolerance = 1.0e-9;

  /**
   * Creates a simulator from already extracted data.
   *
   * @param process solved base-case process
   * @param componentNames component names
   * @param molarMass component molar masses in kg/mol
   * @param unitProfiles unit profiles in process order
   */
  private KValueProcessSimulator(ProcessSystem process, String[] componentNames, double[] molarMass,
      List<KValueUnitProfile> unitProfiles) {
    this.process = process;
    this.componentNames = componentNames.clone();
    this.molarMass = molarMass.clone();
    this.unitProfiles = new ArrayList<KValueUnitProfile>(unitProfiles);
    buildTopology();
    identifySources();
    buildAliasMap();
  }

  /**
   * Extracts a K-value simulator from a solved base-case process.
   *
   * @param process solved base-case process; must be non-null
   * @return K-value fast simulator
   */
  public static KValueProcessSimulator fromBaseCase(ProcessSystem process) {
    List<ProcessEquipmentInterface> nodeUnits = findNodeUnits(process);
    Map<String, Double> molarMassByComponent = buildComponentSlate(process, nodeUnits);
    String[] componentNames = molarMassByComponent.keySet().toArray(new String[0]);
    double[] molarMass = new double[componentNames.length];
    for (int i = 0; i < componentNames.length; i++) {
      molarMass[i] = molarMassByComponent.get(componentNames[i]);
    }

    List<KValueUnitProfile> profiles = new ArrayList<KValueUnitProfile>();
    for (ProcessEquipmentInterface unit : nodeUnits) {
      profiles.add(buildProfile(unit, componentNames));
    }
    return new KValueProcessSimulator(process, componentNames, molarMass, profiles);
  }

  /**
   * Sets the maximum number of fixed-point iterations.
   *
   * @param maxIterations maximum iteration count; must be positive
   */
  public void setMaxIterations(int maxIterations) {
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("maxIterations must be positive");
    }
    this.maxIterations = maxIterations;
  }

  /**
   * Sets the fixed-point convergence tolerance.
   *
   * @param tolerance convergence tolerance; must be positive
   */
  public void setTolerance(double tolerance) {
    if (tolerance <= 0.0) {
      throw new IllegalArgumentException("tolerance must be positive");
    }
    this.tolerance = tolerance;
  }

  /**
   * Gets the unit profiles.
   *
   * @return unmodifiable profile list
   */
  public List<KValueUnitProfile> getUnitProfiles() {
    return Collections.unmodifiableList(unitProfiles);
  }

  /**
   * Gets the source stream names.
   *
   * @return source stream names
   */
  public String[] getSourceNames() {
    return sourceByName.keySet().toArray(new String[0]);
  }

  /**
   * Gets the component names.
   *
   * @return component names
   */
  public String[] getComponentNames() {
    return componentNames.clone();
  }

  /**
   * Gets terminal stream names.
   *
   * @return unmodifiable terminal stream names
   */
  public List<String> getTerminalStreamNames() {
    return Collections.unmodifiableList(terminalStreamNames);
  }

  /**
   * Runs the fast process simulation at the base-case source flows.
   *
   * @return fast simulation result
   */
  public KValueProcessResult run() {
    return runWithSourceFlowMultipliers(Collections.<String, Double>emptyMap());
  }

  /**
   * Runs the fast process simulation with source-flow multipliers.
   *
   * @param sourceFlowMultipliers source-name to multiplier map; missing sources use {@code 1.0}
   * @return fast simulation result
   */
  public KValueProcessResult runWithSourceFlowMultipliers(Map<String, Double> sourceFlowMultipliers) {
    Map<String, double[]> sourceFlows = new LinkedHashMap<String, double[]>();
    for (String sourceName : sourceByName.keySet()) {
      Double multiplier = sourceFlowMultipliers == null ? null : sourceFlowMultipliers.get(sourceName);
      double scale = multiplier == null ? 1.0 : multiplier.doubleValue();
      if (scale < 0.0) {
	throw new IllegalArgumentException("Source multiplier must be non-negative for " + sourceName);
      }
      sourceFlows.put(sourceName, scale(baseSourceComponentFlows.get(sourceName), scale));
    }
    return runFromSourceFlows(sourceFlows);
  }

  /**
   * Runs the fast process simulation with replacement source total flow rates.
   *
   * @param sourceFlowRates source-name to total flow-rate map; missing sources use base flow
   * @param unit flow-rate unit: {@code mole/sec}, {@code mole/hr}, {@code kg/sec}, or {@code kg/hr}
   * @return fast simulation result
   */
  public KValueProcessResult runWithSourceFlowRates(Map<String, Double> sourceFlowRates, String unit) {
    Map<String, double[]> sourceFlows = new LinkedHashMap<String, double[]>();
    for (String sourceName : sourceByName.keySet()) {
      double[] baseFlow = baseSourceComponentFlows.get(sourceName);
      Double requestedFlow = sourceFlowRates == null ? null : sourceFlowRates.get(sourceName);
      if (requestedFlow == null) {
	sourceFlows.put(sourceName, baseFlow.clone());
      } else {
	if (requestedFlow.doubleValue() < 0.0) {
	  throw new IllegalArgumentException("Source flow rate must be non-negative for " + sourceName);
	}
	sourceFlows.put(sourceName, setTotalFlow(baseFlow, requestedFlow.doubleValue(), unit));
      }
    }
    return runFromSourceFlows(sourceFlows);
  }

  /**
   * Benchmarks fast source multiplier cases against rigorous process reruns.
   *
   * @param sourceName source stream to perturb
   * @param multipliers source total-flow multipliers to test
   * @return benchmark result with timings and terminal mass consistency
   */
  public KValueProcessBenchmarkResult benchmarkSourceFlowMultipliers(String sourceName, double[] multipliers) {
    if (!sourceByName.containsKey(sourceName)) {
      throw new IllegalArgumentException("Unknown source: " + sourceName);
    }
    if (multipliers == null || multipliers.length == 0) {
      throw new IllegalArgumentException("At least one multiplier is required");
    }

    Map<String, Double> baseKgPerHr = new LinkedHashMap<String, Double>();
    for (String name : sourceByName.keySet()) {
      baseKgPerHr.put(name, componentTotal(baseSourceComponentFlows.get(name), "kg/hr"));
    }

    long proxyTotal = 0L;
    long rigorousTotal = 0L;
    double maxMassDeviation = 0.0;
    try {
      for (int i = 0; i < multipliers.length; i++) {
	Map<String, Double> scenario = new LinkedHashMap<String, Double>();
	scenario.put(sourceName, multipliers[i]);

	long proxyStart = System.nanoTime();
	KValueProcessResult proxyResult = runWithSourceFlowMultipliers(scenario);
	proxyTotal += System.nanoTime() - proxyStart;

	sourceByName.get(sourceName).setFlowRate(baseKgPerHr.get(sourceName) * multipliers[i], "kg/hr");
	long rigorousStart = System.nanoTime();
	process.run();
	rigorousTotal += System.nanoTime() - rigorousStart;

	double proxyMass = proxyResult.getTerminalTotalFlow("kg/hr");
	double rigorousMass = rigorousTerminalMassFlow();
	double denominator = Math.max(Math.max(Math.abs(proxyMass), Math.abs(rigorousMass)), 1.0e-30);
	maxMassDeviation = Math.max(maxMassDeviation, Math.abs(proxyMass - rigorousMass) / denominator);
      }
    } finally {
      restoreSourceFlows(baseKgPerHr);
    }
    return new KValueProcessBenchmarkResult(multipliers.length, rigorousTotal, proxyTotal, maxMassDeviation);
  }

  /**
   * Builds stream producer and consumer maps.
   */
  private void buildTopology() {
    for (KValueUnitProfile profile : unitProfiles) {
      List<StreamInterface> outlets = profile.getOutletStreams();
      for (int outlet = 0; outlet < outlets.size(); outlet++) {
	streamProducer.put(outlets.get(outlet), profile);
	streamProducerOutletIndex.put(outlets.get(outlet), Integer.valueOf(outlet));
      }
      for (StreamInterface inlet : profile.getInletStreams()) {
	List<KValueUnitProfile> consumers = streamConsumers.get(inlet);
	if (consumers == null) {
	  consumers = new ArrayList<KValueUnitProfile>();
	  streamConsumers.put(inlet, consumers);
	}
	if (!consumers.contains(profile)) {
	  consumers.add(profile);
	}
      }
    }

    for (KValueUnitProfile profile : unitProfiles) {
      List<StreamInterface> outlets = profile.getOutletStreams();
      for (int outlet = 0; outlet < outlets.size(); outlet++) {
	if (!streamConsumers.containsKey(outlets.get(outlet))) {
	  terminalStreamNames.add(profile.getOutletKey(outlet));
	}
      }
    }
  }

  /**
   * Identifies external source streams.
   */
  private void identifySources() {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface)) {
	continue;
      }
      StreamInterface stream = (StreamInterface) unit;
      if (streamConsumers.containsKey(stream) && !streamProducer.containsKey(stream)) {
	sourceStreams.add(stream);
	sourceByName.put(stream.getName(), stream);
	baseSourceComponentFlows.put(stream.getName(), componentFlows(stream));
      }
    }
  }

  /**
   * Builds aliases from wrapper stream fluid objects to internal outlet keys.
   */
  private void buildAliasMap() {
    for (KValueUnitProfile profile : unitProfiles) {
      List<StreamInterface> outlets = profile.getOutletStreams();
      for (int outlet = 0; outlet < outlets.size(); outlet++) {
	SystemInterface fluid = outlets.get(outlet).getFluid();
	if (fluid != null) {
	  fluidAliasKeys.put(fluid, profile.getOutletKey(outlet));
	}
      }
    }
  }

  /**
   * Runs the fixed-point graph simulation from explicit source component flows.
   *
   * @param sourceFlows source component flows in mole/sec keyed by source name
   * @return fast simulation result
   */
  private KValueProcessResult runFromSourceFlows(Map<String, double[]> sourceFlows) {
    long startTime = System.nanoTime();
    Map<StreamInterface, double[]> streamFlows = new IdentityHashMap<StreamInterface, double[]>();
    for (StreamInterface source : sourceStreams) {
      streamFlows.put(source, sourceFlows.get(source.getName()).clone());
    }
    for (KValueUnitProfile profile : unitProfiles) {
      for (StreamInterface outlet : profile.getOutletStreams()) {
	streamFlows.put(outlet, componentFlows(outlet));
      }
    }

    double residual = Double.POSITIVE_INFINITY;
    int iteration = 0;
    for (; iteration < maxIterations; iteration++) {
      residual = 0.0;
      for (KValueUnitProfile profile : unitProfiles) {
	double[] inlet = sumInletFlows(profile, streamFlows);
	double[][] routed = profile.route(inlet);
	List<StreamInterface> outlets = profile.getOutletStreams();
	for (int outlet = 0; outlet < outlets.size(); outlet++) {
	  double[] previous = streamFlows.get(outlets.get(outlet));
	  if (previous == null) {
	    previous = new double[componentNames.length];
	  }
	  residual = Math.max(residual, relativeChange(previous, routed[outlet]));
	  streamFlows.put(outlets.get(outlet), routed[outlet]);
	}
      }
      if (residual < tolerance) {
	iteration++;
	break;
      }
    }

    long runTime = System.nanoTime() - startTime;
    return buildResult(streamFlows, sourceFlows, iteration, residual, runTime);
  }

  /**
   * Builds a named result from identity-keyed stream flows.
   *
   * @param streamFlows identity-keyed stream flow map
   * @param sourceFlows source flows keyed by source name
   * @param iterations fixed-point iterations used
   * @param residual final residual
   * @param runTime runtime in nanoseconds
   * @return result object
   */
  private KValueProcessResult buildResult(Map<StreamInterface, double[]> streamFlows, Map<String, double[]> sourceFlows,
      int iterations, double residual, long runTime) {
    Map<String, double[]> namedFlows = new LinkedHashMap<String, double[]>();
    for (String sourceName : sourceByName.keySet()) {
      namedFlows.put(sourceName, sourceFlows.get(sourceName).clone());
    }
    for (KValueUnitProfile profile : unitProfiles) {
      List<StreamInterface> outlets = profile.getOutletStreams();
      for (int outlet = 0; outlet < outlets.size(); outlet++) {
	double[] flow = streamFlows.get(outlets.get(outlet));
	namedFlows.put(profile.getOutletKey(outlet), flow == null ? new double[componentNames.length] : flow.clone());
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface)) {
	continue;
      }
      StreamInterface stream = (StreamInterface) unit;
      if (namedFlows.containsKey(stream.getName())) {
	continue;
      }
      double[] directFlow = streamFlows.get(stream);
      if (directFlow != null) {
	namedFlows.put(stream.getName(), directFlow.clone());
	continue;
      }
      SystemInterface fluid = stream.getFluid();
      String aliasKey = fluid == null ? null : fluidAliasKeys.get(fluid);
      if (aliasKey != null && namedFlows.containsKey(aliasKey)) {
	namedFlows.put(stream.getName(), namedFlows.get(aliasKey).clone());
      }
    }
    List<String> sourceNames = new ArrayList<String>(sourceByName.keySet());
    return new KValueProcessResult(componentNames, molarMass, namedFlows, terminalStreamNames, sourceNames, iterations,
	residual, runTime);
  }

  /**
   * Sums all inlet flows to a unit profile.
   *
   * @param profile unit profile
   * @param streamFlows current stream flows
   * @return component flow vector in mole/sec
   */
  private double[] sumInletFlows(KValueUnitProfile profile, Map<StreamInterface, double[]> streamFlows) {
    double[] inlet = new double[componentNames.length];
    for (StreamInterface stream : profile.getInletStreams()) {
      double[] flow = streamFlows.get(stream);
      if (flow == null) {
	continue;
      }
      for (int component = 0; component < componentNames.length; component++) {
	inlet[component] += flow[component];
      }
    }
    return inlet;
  }

  /**
   * Restores source stream total flow rates after a benchmark.
   *
   * @param baseKgPerHr base total source rates in kg/hr
   */
  private void restoreSourceFlows(Map<String, Double> baseKgPerHr) {
    for (Map.Entry<String, Double> entry : baseKgPerHr.entrySet()) {
      sourceByName.get(entry.getKey()).setFlowRate(entry.getValue(), "kg/hr");
    }
    process.run();
  }

  /**
   * Gets rigorous terminal mass flow from the current process state.
   *
   * @return terminal mass flow in kg/hr
   */
  private double rigorousTerminalMassFlow() {
    double total = 0.0;
    for (KValueUnitProfile profile : unitProfiles) {
      List<StreamInterface> outlets = profile.getOutletStreams();
      for (StreamInterface outlet : outlets) {
	if (!streamConsumers.containsKey(outlet)) {
	  total += outlet.getFlowRate("kg/hr");
	}
      }
    }
    return total;
  }

  /**
   * Finds process units that should act as proxy nodes.
   *
   * @param process process to scan
   * @return node units in process order
   */
  private static List<ProcessEquipmentInterface> findNodeUnits(ProcessSystem process) {
    List<ProcessEquipmentInterface> nodes = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface) {
	continue;
      }
      List<StreamInterface> outlets = safeOutlets(unit);
      if (!outlets.isEmpty()) {
	nodes.add(unit);
      }
    }
    return nodes;
  }

  /**
   * Builds a component slate and molar-mass map.
   *
   * @param process process to scan
   * @param nodeUnits node units to scan
   * @return component molar masses keyed by component name
   */
  private static Map<String, Double> buildComponentSlate(ProcessSystem process,
      List<ProcessEquipmentInterface> nodeUnits) {
    Map<String, Double> molarMassByComponent = new LinkedHashMap<String, Double>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface) {
	collectComponents((StreamInterface) unit, molarMassByComponent);
      }
    }
    for (ProcessEquipmentInterface unit : nodeUnits) {
      for (StreamInterface stream : safeInlets(unit)) {
	collectComponents(stream, molarMassByComponent);
      }
      for (StreamInterface stream : safeOutlets(unit)) {
	collectComponents(stream, molarMassByComponent);
      }
    }
    return molarMassByComponent;
  }

  /**
   * Adds stream components to a slate.
   *
   * @param stream stream to scan
   * @param molarMassByComponent component molar-mass map to update
   */
  private static void collectComponents(StreamInterface stream, Map<String, Double> molarMassByComponent) {
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      return;
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      String name = fluid.getComponent(component).getComponentName();
      if (!molarMassByComponent.containsKey(name)) {
	molarMassByComponent.put(name, fluid.getComponent(component).getMolarMass());
      }
    }
  }

  /**
   * Builds one unit profile.
   *
   * @param unit unit to profile
   * @param componentNames component slate
   * @return unit profile
   */
  private static KValueUnitProfile buildProfile(ProcessEquipmentInterface unit, String[] componentNames) {
    List<StreamInterface> inlets = safeInlets(unit);
    List<StreamInterface> outlets = safeOutlets(unit);
    double[] inletComponentFlow = new double[componentNames.length];
    for (int component = 0; component < componentNames.length; component++) {
      for (StreamInterface inlet : inlets) {
	inletComponentFlow[component] += componentFlow(inlet, componentNames[component]);
      }
    }
    double[][] fallbackFactors = buildFallbackFactors(outlets, inletComponentFlow, componentNames);
    int gasOutletIndex = findGasOutletIndex(outlets);
    int liquidOutletIndex = outlets.size() == 2 && gasOutletIndex >= 0 ? 1 - gasOutletIndex : -1;
    int waterOutletIndex = -1;
    double[] kValues = null;
    double[] waterKValues = null;
    if (outlets.size() == 3 && gasOutletIndex >= 0) {
      waterOutletIndex = findWaterOutletIndex(outlets);
      if (waterOutletIndex >= 0 && waterOutletIndex != gasOutletIndex) {
	liquidOutletIndex = findRemainingOutletIndex(outlets.size(), gasOutletIndex, waterOutletIndex);
	if (liquidOutletIndex >= 0) {
	  kValues = extractKValues(outlets.get(gasOutletIndex), outlets.get(liquidOutletIndex), componentNames);
	  waterKValues = extractKValues(outlets.get(waterOutletIndex), outlets.get(liquidOutletIndex), componentNames);
	}
      }
    }
    if (gasOutletIndex >= 0 && liquidOutletIndex >= 0) {
      if (kValues == null) {
	kValues = extractKValues(outlets.get(gasOutletIndex), outlets.get(liquidOutletIndex), componentNames);
      }
    }
    return new KValueUnitProfile(unit.getName(), unit.getClass().getSimpleName(), inlets, outlets, componentNames,
	fallbackFactors, kValues, waterKValues, gasOutletIndex, liquidOutletIndex, waterOutletIndex,
	inletComponentFlow);
  }

  /**
   * Builds frozen fallback split factors.
   *
   * @param outlets outlet streams
   * @param inletComponentFlow inlet component flows in mole/sec
   * @param componentNames component slate
   * @return split factors indexed by outlet and component
   */
  private static double[][] buildFallbackFactors(List<StreamInterface> outlets, double[] inletComponentFlow,
      String[] componentNames) {
    double[][] factors = new double[outlets.size()][componentNames.length];
    for (int component = 0; component < componentNames.length; component++) {
      if (inletComponentFlow[component] > MIN_FLOW) {
	for (int outlet = 0; outlet < outlets.size(); outlet++) {
	  factors[outlet][component] = componentFlow(outlets.get(outlet), componentNames[component])
	      / inletComponentFlow[component];
	}
      } else if (outlets.size() == 1) {
	factors[0][component] = 1.0;
      }
    }
    return factors;
  }

  /**
   * Extracts cached K-values from gas and liquid outlet compositions.
   *
   * @param gasOutlet gas outlet stream
   * @param liquidOutlet liquid outlet stream
   * @param componentNames component slate
   * @return K-value vector
   */
  private static double[] extractKValues(StreamInterface gasOutlet, StreamInterface liquidOutlet,
      String[] componentNames) {
    double[] gasFlow = componentFlows(gasOutlet, componentNames);
    double[] liquidFlow = componentFlows(liquidOutlet, componentNames);
    double gasTotal = sum(gasFlow);
    double liquidTotal = sum(liquidFlow);
    if (gasTotal <= MIN_FLOW || liquidTotal <= MIN_FLOW) {
      return null;
    }

    double[] kValues = new double[componentNames.length];
    for (int component = 0; component < componentNames.length; component++) {
      double y = gasFlow[component] / gasTotal;
      double x = liquidFlow[component] / liquidTotal;
      if (x <= MIN_FLOW && y <= MIN_FLOW) {
	kValues[component] = 1.0;
      } else if (x <= MIN_FLOW) {
	kValues[component] = 1.0e12;
      } else if (y <= MIN_FLOW) {
	kValues[component] = 1.0e-12;
      } else {
	kValues[component] = y / x;
      }
    }
    return kValues;
  }

  /**
   * Finds the gas outlet in a two- or three-outlet unit.
   *
   * @param outlets outlet streams
   * @return gas outlet index, or {@code -1} when not found or ambiguous
   */
  private static int findGasOutletIndex(List<StreamInterface> outlets) {
    int gasIndex = -1;
    for (int outlet = 0; outlet < outlets.size(); outlet++) {
      if (isGasOutlet(outlets.get(outlet))) {
	if (gasIndex >= 0) {
	  return -1;
	}
	gasIndex = outlet;
      }
    }
    return gasIndex;
  }

  /**
   * Finds the water outlet in a three-outlet unit.
   *
   * @param outlets outlet streams
   * @return water outlet index, or {@code -1} when not found or ambiguous
   */
  private static int findWaterOutletIndex(List<StreamInterface> outlets) {
    int waterIndex = -1;
    for (int outlet = 0; outlet < outlets.size(); outlet++) {
      if (isWaterOutlet(outlets.get(outlet))) {
	if (waterIndex >= 0) {
	  return -1;
	}
	waterIndex = outlet;
      }
    }
    return waterIndex;
  }

  /**
   * Finds the remaining outlet index after gas and water have been identified.
   *
   * @param outletCount number of outlets
   * @param gasOutletIndex gas outlet index
   * @param waterOutletIndex water outlet index
   * @return remaining outlet index, or {@code -1} if the indexes are invalid
   */
  private static int findRemainingOutletIndex(int outletCount, int gasOutletIndex, int waterOutletIndex) {
    for (int outlet = 0; outlet < outletCount; outlet++) {
      if (outlet != gasOutletIndex && outlet != waterOutletIndex) {
	return outlet;
      }
    }
    return -1;
  }

  /**
   * Tests whether an outlet stream is gas-like.
   *
   * @param stream stream to classify
   * @return {@code true} for gas-like streams
   */
  private static boolean isGasOutlet(StreamInterface stream) {
    String streamName = stream.getName() == null ? "" : stream.getName().toLowerCase();
    if (streamName.contains("gas")) {
      return true;
    }
    SystemInterface fluid = stream.getFluid();
    if (fluid == null || fluid.getNumberOfPhases() == 0) {
      return false;
    }
    try {
      String phaseTypeName = fluid.getPhase(0).getPhaseTypeName();
      return phaseTypeName != null && phaseTypeName.toLowerCase().contains("gas");
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Tests whether an outlet stream is water-like.
   *
   * @param stream stream to classify
   * @return {@code true} for water-like streams
   */
  private static boolean isWaterOutlet(StreamInterface stream) {
    String streamName = stream.getName() == null ? "" : stream.getName().toLowerCase();
    if (streamName.contains("water") || streamName.contains("aqueous")) {
      return true;
    }
    SystemInterface fluid = stream.getFluid();
    if (fluid == null || fluid.getNumberOfPhases() == 0) {
      return false;
    }
    try {
      String phaseTypeName = fluid.getPhase(0).getPhaseTypeName();
      if (phaseTypeName != null && phaseTypeName.toLowerCase().contains("aqueous")) {
	return true;
      }
    } catch (Exception ex) {
      return false;
    }
    return false;
  }

  /**
   * Reads component flows from a stream using the simulator slate.
   *
   * @param stream stream to read
   * @return component molar flows in mole/sec
   */
  private double[] componentFlows(StreamInterface stream) {
    return componentFlows(stream, componentNames);
  }

  /**
   * Reads component flows from a stream.
   *
   * @param stream stream to read
   * @param componentNames component slate
   * @return component molar flows in mole/sec
   */
  private static double[] componentFlows(StreamInterface stream, String[] componentNames) {
    double[] flows = new double[componentNames.length];
    for (int component = 0; component < componentNames.length; component++) {
      flows[component] = componentFlow(stream, componentNames[component]);
    }
    return flows;
  }

  /**
   * Gets a named component flow from a stream.
   *
   * @param stream stream to read
   * @param componentName component name
   * @return component molar flow in mole/sec
   */
  private static double componentFlow(StreamInterface stream, String componentName) {
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      return 0.0;
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      if (fluid.getComponent(component).getComponentName().equals(componentName)) {
	return Math.max(0.0, fluid.getComponent(component).getTotalFlowRate("mole/sec"));
      }
    }
    return 0.0;
  }

  /**
   * Returns unit inlet streams without throwing.
   *
   * @param unit unit to query
   * @return inlet stream list, or an empty list
   */
  private static List<StreamInterface> safeInlets(ProcessEquipmentInterface unit) {
    try {
      List<StreamInterface> inlets = unit.getInletStreams();
      return inlets == null ? Collections.<StreamInterface>emptyList() : inlets;
    } catch (Exception ex) {
      return Collections.<StreamInterface>emptyList();
    }
  }

  /**
   * Returns unit outlet streams without throwing.
   *
   * @param unit unit to query
   * @return outlet stream list, or an empty list
   */
  private static List<StreamInterface> safeOutlets(ProcessEquipmentInterface unit) {
    try {
      List<StreamInterface> outlets = unit.getOutletStreams();
      return outlets == null ? Collections.<StreamInterface>emptyList() : outlets;
    } catch (Exception ex) {
      return Collections.<StreamInterface>emptyList();
    }
  }

  /**
   * Scales a component vector.
   *
   * @param values component vector
   * @param scale scale factor
   * @return scaled vector
   */
  private double[] scale(double[] values, double scale) {
    double[] scaled = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      scaled[i] = values[i] * scale;
    }
    return scaled;
  }

  /**
   * Rescales a source vector to a new total flow.
   *
   * @param baseFlow base component vector in mole/sec
   * @param totalFlow new total flow
   * @param unit total-flow unit
   * @return rescaled component vector in mole/sec
   */
  private double[] setTotalFlow(double[] baseFlow, double totalFlow, String unit) {
    double baseTotal = sum(baseFlow);
    if (baseTotal <= MIN_FLOW) {
      return baseFlow.clone();
    }
    double newTotalMolePerSec = convertTotalFlowToMolePerSec(baseFlow, totalFlow, unit);
    return scale(baseFlow, newTotalMolePerSec / baseTotal);
  }

  /**
   * Converts a total source flow to mole/sec.
   *
   * @param baseFlow base component vector in mole/sec
   * @param totalFlow total flow value
   * @param unit flow unit
   * @return total molar flow in mole/sec
   */
  private double convertTotalFlowToMolePerSec(double[] baseFlow, double totalFlow, String unit) {
    String normalizedUnit = unit == null ? "mole/sec" : unit.trim().toLowerCase();
    if (normalizedUnit.equals("mole/sec") || normalizedUnit.equals("mol/sec")) {
      return totalFlow;
    }
    if (normalizedUnit.equals("mole/hr") || normalizedUnit.equals("mol/hr")) {
      return totalFlow / 3600.0;
    }
    if (normalizedUnit.equals("kg/sec") || normalizedUnit.equals("kg/hr")) {
      double molarMassValue = sourceMolarMass(baseFlow);
      double massPerSec = normalizedUnit.equals("kg/hr") ? totalFlow / 3600.0 : totalFlow;
      return molarMassValue <= MIN_FLOW ? 0.0 : massPerSec / molarMassValue;
    }
    throw new IllegalArgumentException("Unsupported unit: " + unit);
  }

  /**
   * Calculates a source molar mass from a component vector.
   *
   * @param flow component vector in mole/sec
   * @return molar mass in kg/mol
   */
  private double sourceMolarMass(double[] flow) {
    double total = sum(flow);
    if (total <= MIN_FLOW) {
      return 0.0;
    }
    double mass = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      mass += flow[component] * molarMass[component];
    }
    return mass / total;
  }

  /**
   * Calculates the total of a component vector in a unit.
   *
   * @param flow component vector in mole/sec
   * @param unit requested unit
   * @return total flow in the requested unit
   */
  private double componentTotal(double[] flow, String unit) {
    String normalizedUnit = unit == null ? "mole/sec" : unit.trim().toLowerCase();
    double moleTotal = sum(flow);
    if (normalizedUnit.equals("mole/sec") || normalizedUnit.equals("mol/sec")) {
      return moleTotal;
    }
    if (normalizedUnit.equals("mole/hr") || normalizedUnit.equals("mol/hr")) {
      return moleTotal * 3600.0;
    }
    double massPerSec = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      massPerSec += flow[component] * molarMass[component];
    }
    if (normalizedUnit.equals("kg/sec")) {
      return massPerSec;
    }
    if (normalizedUnit.equals("kg/hr")) {
      return massPerSec * 3600.0;
    }
    throw new IllegalArgumentException("Unsupported unit: " + unit);
  }

  /**
   * Sums a vector.
   *
   * @param values values to sum
   * @return sum of positive values
   */
  private static double sum(double[] values) {
    double total = 0.0;
    for (int i = 0; i < values.length; i++) {
      total += Math.max(0.0, values[i]);
    }
    return total;
  }

  /**
   * Computes the maximum relative change between two component vectors.
   *
   * @param previous previous vector
   * @param current current vector
   * @return maximum relative change
   */
  private double relativeChange(double[] previous, double[] current) {
    double max = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      double denominator = Math.max(Math.max(Math.abs(previous[component]), Math.abs(current[component])), MIN_FLOW);
      max = Math.max(max, Math.abs(current[component] - previous[component]) / denominator);
    }
    return max;
  }
}
