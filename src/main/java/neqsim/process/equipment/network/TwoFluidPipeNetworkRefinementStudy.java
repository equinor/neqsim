package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Replays one accepted two-fluid network definition at common physical times with different outer steps, branch
 * subcycle factors and branch spatial meshes.
 *
 * <p>
 * The study snapshots the supplied canonical {@link TwoFluidPipeNetwork} through serialization. Every case starts from
 * that same snapshot, changes only the outer time step, existing per-branch subcycle factors and explicitly requested
 * section counts, and retains measured runtime, conservation residuals, node pressures and full branch profiles. A mesh
 * override preserves length and resamples an explicit cell-face elevation route. Flat legacy elevation arrays are
 * converted to the equivalent constant face route; non-flat legacy arrays fail closed because their section-indexed
 * geometry cannot be changed without interpretation. The study does not change topology, boundary states, physical
 * closures or solver tolerances. Results therefore support numerical refinement and runtime screening; they are not
 * independent physical qualification.
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

  /** Quantities available for common-position branch-profile comparison. */
  public enum ProfileQuantity {
    /** Mechanical pressure in Pa. */
    PRESSURE,
    /** Fluid temperature in K. */
    TEMPERATURE,
    /** Total liquid volume fraction. */
    LIQUID_HOLDUP,
    /** Gas phase velocity in m/s. */
    GAS_VELOCITY,
    /** Liquid phase velocity in m/s. */
    LIQUID_VELOCITY
  }

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
    return addConfiguration(name, outerTimeStepSeconds, branchSubcycles, null);
  }

  /**
   * Add a numerical configuration with optional spatial mesh overrides.
   *
   * <p>
   * Omitted branches retain their captured section count. A mesh override is applied only to an independently restored
   * case and never mutates the captured network. Explicit cell-face terrain is interpolated onto the new uniform mesh.
   * Non-flat legacy section-indexed terrain is rejected because it cannot be reinterpreted as physical faces without a
   * user decision.
   * </p>
   *
   * @param name unique case name
   * @param outerTimeStepSeconds positive outer time step in seconds
   * @param branchSubcycles positive integer factors keyed by branch name
   * @param branchSectionCounts section counts of at least two keyed by branch name
   * @return this study
   * @throws IllegalArgumentException for invalid or duplicate inputs
   */
  public TwoFluidPipeNetworkRefinementStudy addConfiguration(String name, double outerTimeStepSeconds,
      Map<String, Integer> branchSubcycles, Map<String, Integer> branchSectionCounts) {
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
    Map<String, Integer> copiedSections = copyPositiveBranchIntegers(branchSectionCounts, "Section count", 2);
    configurations.add(new Configuration(name, outerTimeStepSeconds, copied, copiedSections));
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
    for (Map.Entry<String, Integer> entry : configuration.branchSectionCounts.entrySet()) {
      applyUniformMesh(network.getPipe(entry.getKey()), entry.getValue().intValue(), entry.getKey());
    }
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
   * Copy and validate an optional branch-to-positive-integer map.
   *
   * @param values optional source map
   * @param label value label used in diagnostics
   * @param minimum smallest accepted integer
   * @return independently allocated validated map
   */
  private Map<String, Integer> copyPositiveBranchIntegers(Map<String, Integer> values, String label, int minimum) {
    Map<String, Integer> copied = new LinkedHashMap<String, Integer>();
    if (values == null) {
      return copied;
    }
    for (Map.Entry<String, Integer> entry : values.entrySet()) {
      if (!branchNames.contains(entry.getKey())) {
        throw new IllegalArgumentException("Unknown branch '" + entry.getKey() + "'");
      }
      if (entry.getValue() == null || entry.getValue().intValue() < minimum) {
        throw new IllegalArgumentException(label + " for '" + entry.getKey() + "' must be at least " + minimum);
      }
      copied.put(entry.getKey(), entry.getValue());
    }
    return copied;
  }

  /**
   * Apply one physically aligned uniform mesh to an unadvanced restored pipe.
   *
   * @param pipe independently restored pipe
   * @param sectionCount requested section count
   * @param branchName branch name used in diagnostics
   */
  private static void applyUniformMesh(TwoFluidPipe pipe, int sectionCount, String branchName) {
    double length = pipe.getLength();
    if (!Double.isFinite(length) || length <= 0.0) {
      throw new IllegalArgumentException("Branch '" + branchName + "' must have a positive finite length");
    }
    double[] targetLengths = new double[sectionCount];
    Arrays.fill(targetLengths, length / sectionCount);
    double[] faceElevations = pipe.getCellFaceElevationProfile();
    double[] legacyElevations = pipe.getElevationProfile();
    double[] resampledFaces = null;
    if (faceElevations != null) {
      double[] sourceLengths = physicalCellLengths(pipe, faceElevations.length - 1);
      resampledFaces = resampleFaceValues(faceElevations, sourceLengths, length, sectionCount);
    } else if (legacyElevations != null) {
      if (!isConstant(legacyElevations)) {
        throw new IllegalArgumentException("Branch '" + branchName
            + "' uses non-flat legacy section-indexed terrain; set an explicit cell-face elevation profile before spatial refinement");
      }
      resampledFaces = new double[sectionCount + 1];
      Arrays.fill(resampledFaces, legacyElevations[0]);
    }
    pipe.setSectionLengths(targetLengths);
    if (resampledFaces != null) {
      pipe.setCellFaceElevationProfile(resampledFaces);
    }
  }

  /**
   * Read physical source-cell lengths, expanding a uniform mesh when necessary.
   *
   * @param pipe pipe definition
   * @param sectionCount expected number of cells
   * @return physical cell lengths in m
   */
  private static double[] physicalCellLengths(TwoFluidPipe pipe, int sectionCount) {
    double[] lengths = pipe.getSectionLengths();
    if (lengths != null) {
      if (lengths.length != sectionCount) {
        throw new IllegalArgumentException("Cell-face terrain and section lengths have inconsistent dimensions");
      }
      return lengths;
    }
    lengths = new double[sectionCount];
    Arrays.fill(lengths, pipe.getLength() / sectionCount);
    return lengths;
  }

  /**
   * Interpolate physical face values from one mesh onto another uniform mesh.
   *
   * @param sourceValues values at original cell faces
   * @param sourceLengths original physical cell lengths in m
   * @param totalLength branch length in m
   * @param targetSections requested uniform section count
   * @return values interpolated at target cell faces
   */
  private static double[] resampleFaceValues(double[] sourceValues, double[] sourceLengths, double totalLength,
      int targetSections) {
    double[] sourcePositions = new double[sourceValues.length];
    for (int index = 1; index < sourcePositions.length; index++) {
      sourcePositions[index] = sourcePositions[index - 1] + sourceLengths[index - 1];
    }
    double[] targetValues = new double[targetSections + 1];
    for (int index = 0; index <= targetSections; index++) {
      targetValues[index] = interpolate(sourcePositions, sourceValues, totalLength * index / targetSections);
    }
    return targetValues;
  }

  /**
   * Return whether every value equals the first within scale-aware roundoff.
   *
   * @param values candidate values
   * @return true when the array is constant within roundoff
   */
  private static boolean isConstant(double[] values) {
    for (int index = 1; index < values.length; index++) {
      double scale = Math.max(1.0, Math.max(Math.abs(values[0]), Math.abs(values[index])));
      if (Math.abs(values[index] - values[0]) > 1.0e-12 * scale) {
        return false;
      }
    }
    return true;
  }

  /**
   * Linearly interpolate one monotonic profile with endpoint clamping.
   *
   * @param positions monotonic physical positions
   * @param values values aligned with positions
   * @param target requested physical position
   * @return interpolated or endpoint-clamped value
   */
  private static double interpolate(double[] positions, double[] values, double target) {
    if (target <= positions[0]) {
      return values[0];
    }
    if (target >= positions[positions.length - 1]) {
      return values[values.length - 1];
    }
    int upper = 1;
    while (positions[upper] < target) {
      upper++;
    }
    double fraction = (target - positions[upper - 1]) / (positions[upper] - positions[upper - 1]);
    return values[upper - 1] + fraction * (values[upper] - values[upper - 1]);
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
    private final Map<String, Integer> branchSectionCounts;

    /**
     * Create a configuration.
     *
     * @param name case name
     * @param outerTimeStepSeconds outer time step in seconds
     * @param branchSubcycles branch subcycle factors
     * @param branchSectionCounts branch spatial section counts
     */
    private Configuration(String name, double outerTimeStepSeconds, Map<String, Integer> branchSubcycles,
        Map<String, Integer> branchSectionCounts) {
      this.name = name;
      this.outerTimeStepSeconds = outerTimeStepSeconds;
      this.branchSubcycles = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(branchSubcycles));
      this.branchSectionCounts = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(branchSectionCounts));
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

    /**
     * Compare complete final branch profiles on both cases' physical cell-centre positions.
     *
     * <p>
     * Each profile is linearly interpolated onto the other mesh and the larger absolute difference is normalized by the
     * largest magnitude present in either result. This symmetric measure avoids selecting one mesh as the hidden
     * reference and remains dimensionless for every supported quantity.
     * </p>
     *
     * @param firstName first case
     * @param secondName second case
     * @param branchName branch name
     * @param quantity profile quantity
     * @return symmetric maximum normalized profile difference
     */
    public double getMaximumNormalizedBranchProfileDifference(String firstName, String secondName, String branchName,
        ProfileQuantity quantity) {
      BranchProfile first = getCase(firstName).getBranchProfile(branchName);
      BranchProfile second = getCase(secondName).getBranchProfile(branchName);
      return maximumNormalizedDifference(first, second, quantity);
    }

    /**
     * Calculate a symmetric maximum normalized profile difference.
     *
     * @param first first profile
     * @param second second profile
     * @param quantity requested quantity
     * @return symmetric normalized difference
     */
    private static double maximumNormalizedDifference(BranchProfile first, BranchProfile second,
        ProfileQuantity quantity) {
      double[] firstValues = first.values(quantity);
      double[] secondValues = second.values(quantity);
      double scale = 1.0e-30;
      for (double value : firstValues) {
        scale = Math.max(scale, Math.abs(value));
      }
      for (double value : secondValues) {
        scale = Math.max(scale, Math.abs(value));
      }
      double maximum = directionalMaximumDifference(first.positionM, firstValues, second.positionM, secondValues);
      maximum = Math.max(maximum,
          directionalMaximumDifference(second.positionM, secondValues, first.positionM, firstValues));
      return maximum / scale;
    }

    /**
     * Interpolate a source profile at every target position and return the maximum absolute difference.
     *
     * @param sourcePositions source cell-centre positions
     * @param sourceValues source values
     * @param targetPositions target cell-centre positions
     * @param targetValues target values
     * @return maximum absolute interpolated difference
     */
    private static double directionalMaximumDifference(double[] sourcePositions, double[] sourceValues,
        double[] targetPositions, double[] targetValues) {
      double maximum = 0.0;
      for (int index = 0; index < targetPositions.length; index++) {
        maximum = Math.max(maximum,
            Math.abs(interpolate(sourcePositions, sourceValues, targetPositions[index]) - targetValues[index]));
      }
      return maximum;
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
     * Get explicitly configured branch section counts.
     *
     * @return immutable configured branch section-count map
     */
    public Map<String, Integer> getBranchSectionCounts() {
      return configuration.branchSectionCounts;
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
    private final double[] positionM;
    private final double[] cellLengthM;
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
      cellLengthM = physicalCellLengths(pipe, pressurePa.length);
      positionM = new double[pressurePa.length];
      double distance = 0.0;
      for (int index = 0; index < positionM.length; index++) {
        positionM[index] = distance + 0.5 * cellLengthM[index];
        distance += cellLengthM[index];
      }
      temperatureK = pipe.getTemperatureProfile();
      liquidHoldup = pipe.getLiquidHoldupProfile();
      gasVelocityMPerS = pipe.getGasVelocityProfile();
      liquidVelocityMPerS = pipe.getLiquidVelocityProfile();
    }

    /**
     * Get physical cell-centre positions.
     *
     * @return cell-centre positions in m from the branch inlet
     */
    public double[] getPositionM() {
      return positionM.clone();
    }

    /**
     * Get represented physical cell lengths.
     *
     * @return cell lengths in m
     */
    public double[] getCellLengthM() {
      return cellLengthM.clone();
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

    /**
     * Select one internally retained profile without exposing its mutable array.
     *
     * @param quantity requested quantity
     * @return internally retained immutable-by-contract array
     */
    private double[] values(ProfileQuantity quantity) {
      if (quantity == null) {
        throw new IllegalArgumentException("Profile quantity cannot be null");
      }
      switch (quantity) {
      case PRESSURE:
        return pressurePa;
      case TEMPERATURE:
        return temperatureK;
      case LIQUID_HOLDUP:
        return liquidHoldup;
      case GAS_VELOCITY:
        return gasVelocityMPerS;
      case LIQUID_VELOCITY:
        return liquidVelocityMPerS;
      default:
        throw new IllegalArgumentException("Unsupported profile quantity " + quantity);
      }
    }
  }
}
