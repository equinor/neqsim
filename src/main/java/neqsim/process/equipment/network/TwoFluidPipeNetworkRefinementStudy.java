package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;

/**
 * Replays one accepted two-fluid network definition at common physical times with different outer steps and branch
 * subcycle factors.
 *
 * <p>
 * The study snapshots the supplied canonical {@link TwoFluidPipeNetwork} through serialization. Every case starts from
 * that same snapshot, changes only the outer time step and existing per-branch subcycle factors, and retains measured
 * runtime, conservation residuals, node pressures and full branch profiles. It does not change topology, meshes,
 * boundary states, physical closures or solver tolerances. Results therefore support numerical refinement and runtime
 * screening; they are not independent physical qualification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TwoFluidPipeNetworkRefinementStudy implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double INTEGER_TOLERANCE = 1.0e-10;
  private final byte[] networkSnapshot;
  private final Set<String> nodeNames;
  private final Set<String> branchNames;
  private final List<Configuration> configurations = new ArrayList<Configuration>();

  /**
   * Create a study from an unadvanced canonical network definition.
   *
   * @param network network to snapshot defensively
   * @throws IllegalArgumentException if the network is null or has already advanced in time
   */
  public TwoFluidPipeNetworkRefinementStudy(TwoFluidPipeNetwork network) {
    if (network == null) {
      throw new IllegalArgumentException("Network cannot be null");
    }
    if (network.getSimulationTimeSeconds() != 0.0) {
      throw new IllegalArgumentException("Refinement studies require an unadvanced network");
    }
    networkSnapshot = SerializationUtils.serialize(network);
    nodeNames = network.getNodeNames();
    branchNames = network.getBranchNames();
    if (branchNames.isEmpty()) {
      throw new IllegalArgumentException("Refinement studies require at least one branch");
    }
  }

  /**
   * Add a numerical configuration.
   *
   * <p>
   * Omitted branches retain factor one. Supplied branch names must belong to the captured topology.
   * </p>
   *
   * @param name unique case name
   * @param outerTimeStepSeconds positive outer time step in seconds
   * @param branchSubcycles positive integer factors keyed by branch name
   * @return this study
   * @throws IllegalArgumentException for invalid or duplicate inputs
   */
  public TwoFluidPipeNetworkRefinementStudy addConfiguration(String name, double outerTimeStepSeconds,
      Map<String, Integer> branchSubcycles) {
    requireName(name, "Configuration");
    requirePositiveFinite(outerTimeStepSeconds, "Outer time step");
    for (Configuration existing : configurations) {
      if (existing.name.equals(name)) {
        throw new IllegalArgumentException("Configuration '" + name + "' already exists");
      }
    }
    Map<String, Integer> copied = new LinkedHashMap<String, Integer>();
    if (branchSubcycles != null) {
      for (Map.Entry<String, Integer> entry : branchSubcycles.entrySet()) {
        if (!branchNames.contains(entry.getKey())) {
          throw new IllegalArgumentException("Unknown branch '" + entry.getKey() + "'");
        }
        if (entry.getValue() == null || entry.getValue().intValue() <= 0) {
          throw new IllegalArgumentException("Subcycle factor for '" + entry.getKey() + "' must be positive");
        }
        copied.put(entry.getKey(), entry.getValue());
      }
    }
    configurations.add(new Configuration(name, outerTimeStepSeconds, copied));
    return this;
  }

  /**
   * Run every configuration to one common physical duration.
   *
   * @param durationSeconds positive duration exactly divisible by every outer time step within numerical tolerance
   * @return immutable study result in configuration order
   * @throws IllegalArgumentException when the duration is invalid or not divisible by a case step
   * @throws IllegalStateException when no configuration has been added
   */
  public Result run(double durationSeconds) {
    requirePositiveFinite(durationSeconds, "Study duration");
    if (configurations.isEmpty()) {
      throw new IllegalStateException("At least one refinement configuration is required");
    }
    List<CaseResult> caseResults = new ArrayList<CaseResult>();
    for (Configuration configuration : configurations) {
      double exactSteps = durationSeconds / configuration.outerTimeStepSeconds;
      long stepCount = Math.round(exactSteps);
      if (stepCount <= 0L || Math.abs(exactSteps - stepCount) > INTEGER_TOLERANCE * Math.max(1.0, exactSteps)) {
        throw new IllegalArgumentException("Duration must be an integer multiple of every outer time step");
      }
      caseResults.add(runCase(configuration, stepCount));
    }
    return new Result(durationSeconds, caseResults);
  }

  /**
   * Execute one independently restored numerical case.
   *
   * @param configuration case configuration
   * @param stepCount number of outer intervals
   * @return immutable case result
   */
  private CaseResult runCase(Configuration configuration, long stepCount) {
    TwoFluidPipeNetwork network = SerializationUtils.deserialize(networkSnapshot);
    for (Map.Entry<String, Integer> entry : configuration.branchSubcycles.entrySet()) {
      network.setBranchSubcycles(entry.getKey(), entry.getValue().intValue());
    }
    double maximumMassResidual = 0.0;
    double maximumComponentResidual = 0.0;
    double maximumEnergyResidual = 0.0;
    double maximumJunctionResidual = 0.0;
    long startedNanoseconds = System.nanoTime();
    for (long step = 0L; step < stepCount; step++) {
      network.runTransient(configuration.outerTimeStepSeconds,
          new UUID((long) configuration.name.hashCode(), step + 1L));
      maximumMassResidual = Math.max(maximumMassResidual,
          network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL));
      TwoFluidPipeNetwork.ThermodynamicBalanceReport thermodynamic = network.getLastThermodynamicBalanceReport();
      if (thermodynamic != null) {
        for (String componentName : thermodynamic.getComponentNames()) {
          maximumComponentResidual = Math.max(maximumComponentResidual,
              thermodynamic.getRelativeComponentResidual(componentName));
        }
        maximumEnergyResidual = Math.max(maximumEnergyResidual, thermodynamic.getRelativeEnergyResidual());
      }
      for (String nodeName : network.getLastAlgebraicJunctionNames()) {
        TwoFluidPipeNetwork.AlgebraicJunctionReport junction = network.getLastAlgebraicJunctionReport(nodeName);
        maximumJunctionResidual = Math.max(maximumJunctionResidual, Math.max(junction.getMaximumRelativePhaseResidual(),
            Math.max(junction.getMaximumRelativeComponentResidual(), junction.getRelativeEnergyResidual())));
      }
    }
    long runtimeNanoseconds = System.nanoTime() - startedNanoseconds;
    Map<String, Double> nodePressuresPa = new LinkedHashMap<String, Double>();
    for (String nodeName : nodeNames) {
      nodePressuresPa.put(nodeName, Double.valueOf(network.getNodePressurePa(nodeName)));
    }
    Map<String, BranchProfile> branchProfiles = new LinkedHashMap<String, BranchProfile>();
    for (String branchName : branchNames) {
      branchProfiles.put(branchName, new BranchProfile(network.getPipe(branchName)));
    }
    return new CaseResult(configuration, stepCount, runtimeNanoseconds, maximumMassResidual, maximumComponentResidual,
        maximumEnergyResidual, maximumJunctionResidual, nodePressuresPa, branchProfiles);
  }

  /**
   * Require a nonblank name.
   *
   * @param value candidate name
   * @param label field label
   */
  private static void requireName(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(label + " name cannot be blank");
    }
  }

  /**
   * Require a positive finite number.
   *
   * @param value candidate value
   * @param label field label
   */
  private static void requirePositiveFinite(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be positive and finite");
    }
  }

  /** Immutable numerical configuration. */
  private static final class Configuration implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double outerTimeStepSeconds;
    private final Map<String, Integer> branchSubcycles;

    /**
     * Create a configuration.
     *
     * @param name case name
     * @param outerTimeStepSeconds outer time step in seconds
     * @param branchSubcycles branch subcycle factors
     */
    private Configuration(String name, double outerTimeStepSeconds, Map<String, Integer> branchSubcycles) {
      this.name = name;
      this.outerTimeStepSeconds = outerTimeStepSeconds;
      this.branchSubcycles = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(branchSubcycles));
    }
  }

  /**
   * Immutable collection of independently replayed cases.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double durationSeconds;
    private final List<CaseResult> cases;

    /**
     * Create a result.
     *
     * @param durationSeconds common physical duration
     * @param cases case results
     */
    private Result(double durationSeconds, List<CaseResult> cases) {
      this.durationSeconds = durationSeconds;
      this.cases = Collections.unmodifiableList(new ArrayList<CaseResult>(cases));
    }

    /**
     * Get the common physical duration.
     *
     * @return common physical duration in seconds
     */
    public double getDurationSeconds() {
      return durationSeconds;
    }

    /**
     * Get case results in registration order.
     *
     * @return immutable case list in registration order
     */
    public List<CaseResult> getCases() {
      return cases;
    }

    /**
     * Get one named case.
     *
     * @param name case name
     * @return matching case result
     * @throws IllegalArgumentException when no case has that name
     */
    public CaseResult getCase(String name) {
      for (CaseResult candidate : cases) {
        if (candidate.getName().equals(name)) {
          return candidate;
        }
      }
      throw new IllegalArgumentException("Unknown refinement case '" + name + "'");
    }

    /**
     * Compare final node pressures between two cases.
     *
     * @param firstName first case
     * @param secondName second case
     * @param nodeName node name
     * @return absolute pressure difference divided by the second-case magnitude
     */
    public double getRelativeNodePressureDifference(String firstName, String secondName, String nodeName) {
      double first = getCase(firstName).getNodePressurePa(nodeName);
      double second = getCase(secondName).getNodePressurePa(nodeName);
      return Math.abs(first - second) / Math.max(Math.abs(second), 1.0e-30);
    }

    /**
     * Compare final branch outlet pressures between two cases.
     *
     * @param firstName first case
     * @param secondName second case
     * @param branchName branch name
     * @return absolute outlet-pressure difference divided by the second-case magnitude
     */
    public double getRelativeBranchOutletPressureDifference(String firstName, String secondName, String branchName) {
      double[] firstProfile = getCase(firstName).getBranchProfile(branchName).getPressurePa();
      double[] secondProfile = getCase(secondName).getBranchProfile(branchName).getPressurePa();
      double first = firstProfile[firstProfile.length - 1];
      double second = secondProfile[secondProfile.length - 1];
      return Math.abs(first - second) / Math.max(Math.abs(second), 1.0e-30);
    }
  }

  /**
   * Immutable runtime, conservation and final-state receipt for one case.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class CaseResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Configuration configuration;
    private final long stepCount;
    private final long runtimeNanoseconds;
    private final double maximumMassResidual;
    private final double maximumComponentResidual;
    private final double maximumEnergyResidual;
    private final double maximumJunctionResidual;
    private final Map<String, Double> nodePressuresPa;
    private final Map<String, BranchProfile> branchProfiles;

    /**
     * Create a case result.
     *
     * @param configuration numerical configuration
     * @param stepCount accepted outer steps
     * @param runtimeNanoseconds measured wall-clock runtime
     * @param maximumMassResidual worst total-mass relative residual
     * @param maximumComponentResidual worst component relative residual
     * @param maximumEnergyResidual worst energy relative residual
     * @param maximumJunctionResidual worst reported junction relative residual
     * @param nodePressuresPa final node pressures
     * @param branchProfiles final branch profiles
     */
    private CaseResult(Configuration configuration, long stepCount, long runtimeNanoseconds, double maximumMassResidual,
        double maximumComponentResidual, double maximumEnergyResidual, double maximumJunctionResidual,
        Map<String, Double> nodePressuresPa, Map<String, BranchProfile> branchProfiles) {
      this.configuration = configuration;
      this.stepCount = stepCount;
      this.runtimeNanoseconds = runtimeNanoseconds;
      this.maximumMassResidual = maximumMassResidual;
      this.maximumComponentResidual = maximumComponentResidual;
      this.maximumEnergyResidual = maximumEnergyResidual;
      this.maximumJunctionResidual = maximumJunctionResidual;
      this.nodePressuresPa = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(nodePressuresPa));
      this.branchProfiles = Collections.unmodifiableMap(new LinkedHashMap<String, BranchProfile>(branchProfiles));
    }

    /**
     * Get the case name.
     *
     * @return case name
     */
    public String getName() {
      return configuration.name;
    }

    /**
     * Get the outer time step.
     *
     * @return outer time step in seconds
     */
    public double getOuterTimeStepSeconds() {
      return configuration.outerTimeStepSeconds;
    }

    /**
     * Get explicitly configured branch subcycle factors.
     *
     * @return immutable configured branch subcycle map
     */
    public Map<String, Integer> getBranchSubcycles() {
      return configuration.branchSubcycles;
    }

    /**
     * Get the accepted outer interval count.
     *
     * @return number of accepted outer intervals
     */
    public long getStepCount() {
      return stepCount;
    }

    /**
     * Get the measured wall-clock runtime.
     *
     * @return measured wall-clock runtime in nanoseconds
     */
    public long getRuntimeNanoseconds() {
      return runtimeNanoseconds;
    }

    /**
     * Get the worst total-mass residual.
     *
     * @return worst total-mass relative residual
     */
    public double getMaximumMassResidual() {
      return maximumMassResidual;
    }

    /**
     * Get the worst named-component residual.
     *
     * @return worst named-component relative residual, or zero when not enabled
     */
    public double getMaximumComponentResidual() {
      return maximumComponentResidual;
    }

    /**
     * Get the worst whole-network energy residual.
     *
     * @return worst whole-network energy relative residual, or zero when not enabled
     */
    public double getMaximumEnergyResidual() {
      return maximumEnergyResidual;
    }

    /**
     * Get the worst reported algebraic-junction residual.
     *
     * @return worst reported algebraic-junction relative residual, or zero without a junction
     */
    public double getMaximumJunctionResidual() {
      return maximumJunctionResidual;
    }

    /**
     * Get one final node pressure.
     *
     * @param nodeName node name
     * @return pressure in Pa
     * @throws IllegalArgumentException for an unknown node
     */
    public double getNodePressurePa(String nodeName) {
      Double value = nodePressuresPa.get(nodeName);
      if (value == null) {
        throw new IllegalArgumentException("Unknown node '" + nodeName + "'");
      }
      return value.doubleValue();
    }

    /**
     * Get one final branch profile.
     *
     * @param branchName branch name
     * @return immutable profile snapshot
     * @throws IllegalArgumentException for an unknown branch
     */
    public BranchProfile getBranchProfile(String branchName) {
      BranchProfile value = branchProfiles.get(branchName);
      if (value == null) {
        throw new IllegalArgumentException("Unknown branch '" + branchName + "'");
      }
      return value;
    }
  }

  /**
   * Immutable final profiles for one branch.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class BranchProfile implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] pressurePa;
    private final double[] temperatureK;
    private final double[] liquidHoldup;
    private final double[] gasVelocityMPerS;
    private final double[] liquidVelocityMPerS;

    /**
     * Snapshot a pipe profile.
     *
     * @param pipe accepted pipe
     */
    private BranchProfile(TwoFluidPipe pipe) {
      pressurePa = pipe.getPressureProfile();
      temperatureK = pipe.getTemperatureProfile();
      liquidHoldup = pipe.getLiquidHoldupProfile();
      gasVelocityMPerS = pipe.getGasVelocityProfile();
      liquidVelocityMPerS = pipe.getLiquidVelocityProfile();
    }

    /**
     * Get the pressure profile.
     *
     * @return pressure profile in Pa
     */
    public double[] getPressurePa() {
      return pressurePa.clone();
    }

    /**
     * Get the temperature profile.
     *
     * @return temperature profile in K
     */
    public double[] getTemperatureK() {
      return temperatureK.clone();
    }

    /**
     * Get the liquid-holdup profile.
     *
     * @return liquid-holdup profile
     */
    public double[] getLiquidHoldup() {
      return liquidHoldup.clone();
    }

    /**
     * Get the gas-velocity profile.
     *
     * @return gas-velocity profile in m/s
     */
    public double[] getGasVelocityMPerS() {
      return gasVelocityMPerS.clone();
    }

    /**
     * Get the liquid-velocity profile.
     *
     * @return liquid-velocity profile in m/s
     */
    public double[] getLiquidVelocityMPerS() {
      return liquidVelocityMPerS.clone();
    }
  }
}
