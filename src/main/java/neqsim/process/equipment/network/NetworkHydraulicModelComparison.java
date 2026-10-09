package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.process.equipment.network.LoopedPipeNetwork.NetworkPipe.HydraulicProfile;
import neqsim.process.equipment.network.LoopedPipeNetwork.NetworkPipe;
import neqsim.process.equipment.network.LoopedPipeNetwork.PipeModelType;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.thermo.system.SystemInterface;

/**
 * Compare Beggs-Brill and two-fluid hydraulics on detached copies of one field topology.
 *
 * <p>
 * The comparison changes only the requested pipe edges. All other edge models, typed field identities, geometry,
 * boundary conditions and explicitly assigned node fluids are replayed from the same canonical
 * {@link FieldNetworkTopology}. The caller's topology is never executed or mutated. This permits mixed-fidelity
 * qualification without rebuilding a field definition.
 * </p>
 *
 * <p>
 * Profile pressures are normalized to Pa, temperatures to K and velocities to m/s. Phase velocities are superficial:
 * Beggs-Brill supplies these directly, while two-fluid phase velocities are multiplied by their solved phase area
 * fractions.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class NetworkHydraulicModelComparison {
  /** Immutable normalized profile evidence for one hydraulic model. */
  public static final class ModelProfiles {
    private final double[] positionM;
    private final double[] cellLengthM;
    private final double[] pressurePa;
    private final double[] temperatureK;
    private final double[] liquidHoldup;
    private final double[] gasSuperficialVelocityMs;
    private final double[] liquidSuperficialVelocityMs;

    /**
     * Create immutable profile evidence.
     *
     * @param positionM cell-centre distance from the physical inlet in m
     * @param cellLengthM represented cell length in m
     * @param pressurePa pressure profile in Pa
     * @param temperatureK temperature profile in K
     * @param liquidHoldup liquid holdup profile
     * @param gasSuperficialVelocityMs superficial gas velocity profile in m/s
     * @param liquidSuperficialVelocityMs superficial liquid velocity profile in m/s
     */
    private ModelProfiles(double[] positionM, double[] cellLengthM, double[] pressurePa, double[] temperatureK,
        double[] liquidHoldup, double[] gasSuperficialVelocityMs, double[] liquidSuperficialVelocityMs) {
      this.positionM = positionM.clone();
      this.cellLengthM = cellLengthM.clone();
      this.pressurePa = pressurePa.clone();
      this.temperatureK = temperatureK.clone();
      this.liquidHoldup = liquidHoldup.clone();
      this.gasSuperficialVelocityMs = gasSuperficialVelocityMs.clone();
      this.liquidSuperficialVelocityMs = liquidSuperficialVelocityMs.clone();
    }

    /**
     * Get distance from the physical inlet.
     *
     * @return position in m
     */
    public double[] getPositionM() {
      return positionM.clone();
    }

    /**
     * Get route length represented by each cell-centre sample.
     *
     * @return cell lengths in m
     */
    public double[] getCellLengthM() {
      return cellLengthM.clone();
    }

    /**
     * Get pressure profile.
     *
     * @return pressure in Pa
     */
    public double[] getPressurePa() {
      return pressurePa.clone();
    }

    /**
     * Get temperature profile.
     *
     * @return temperature in K
     */
    public double[] getTemperatureK() {
      return temperatureK.clone();
    }

    /**
     * Get liquid holdup profile.
     *
     * @return liquid holdup fraction
     */
    public double[] getLiquidHoldup() {
      return liquidHoldup.clone();
    }

    /**
     * Get superficial gas velocity profile.
     *
     * @return gas velocity in m/s
     */
    public double[] getGasSuperficialVelocityMs() {
      return gasSuperficialVelocityMs.clone();
    }

    /**
     * Get superficial liquid velocity profile.
     *
     * @return liquid velocity in m/s
     */
    public double[] getLiquidSuperficialVelocityMs() {
      return liquidSuperficialVelocityMs.clone();
    }

    /**
     * Get mean liquid holdup.
     *
     * @return distance-weighted profile mean
     */
    public double getAverageLiquidHoldup() {
      return distanceWeightedAverage(cellLengthM, liquidHoldup);
    }

    /**
     * Get mean superficial gas velocity.
     *
     * @return distance-weighted profile mean in m/s
     */
    public double getAverageGasSuperficialVelocityMs() {
      return distanceWeightedAverage(cellLengthM, gasSuperficialVelocityMs);
    }

    /**
     * Get mean superficial liquid velocity.
     *
     * @return distance-weighted profile mean in m/s
     */
    public double getAverageLiquidSuperficialVelocityMs() {
      return distanceWeightedAverage(cellLengthM, liquidSuperficialVelocityMs);
    }
  }

  /**
   * Immutable collection of same-topology comparisons evaluated at several edge meshes.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class MeshSensitivityResult {
    private final Map<Integer, Result> resultsBySegmentCount;

    /**
     * Create mesh-sensitivity evidence.
     *
     * @param results comparisons keyed by requested segment count
     */
    private MeshSensitivityResult(Map<Integer, Result> results) {
      resultsBySegmentCount = Collections.unmodifiableMap(new LinkedHashMap<Integer, Result>(results));
    }

    /**
     * Get evaluated segment counts in deterministic order.
     *
     * @return immutable segment-count set
     */
    public Set<Integer> getSegmentCounts() {
      return Collections.unmodifiableSet(new LinkedHashSet<Integer>(resultsBySegmentCount.keySet()));
    }

    /**
     * Get the comparison at one requested mesh.
     *
     * @param segmentCount requested number of segments per selected edge
     * @return comparison result
     * @throws IllegalArgumentException when the mesh was not evaluated
     */
    public Result getResult(int segmentCount) {
      Result result = resultsBySegmentCount.get(segmentCount);
      if (result == null) {
        throw new IllegalArgumentException("Segment count " + segmentCount + " was not evaluated");
      }
      return result;
    }
  }

  /** Immutable comparison evidence for one canonical edge. */
  public static final class EdgeComparison {
    private final String edgeId;
    private final double beggsBrillFlowRateKgS;
    private final double twoFluidFlowRateKgS;
    private final double beggsBrillPressureDropPa;
    private final double twoFluidPressureDropPa;
    private final ModelProfiles beggsBrillProfiles;
    private final ModelProfiles twoFluidProfiles;

    /**
     * Create one edge comparison.
     *
     * @param edgeId canonical edge identifier
     * @param beggsBrillEdge solved Beggs-Brill edge
     * @param twoFluidEdge solved two-fluid edge
     */
    private EdgeComparison(String edgeId, NetworkPipe beggsBrillEdge, NetworkPipe twoFluidEdge) {
      this.edgeId = edgeId;
      beggsBrillFlowRateKgS = beggsBrillEdge.getFlowRate();
      twoFluidFlowRateKgS = twoFluidEdge.getFlowRate();
      beggsBrillPressureDropPa = Math.abs(beggsBrillEdge.getHeadLoss());
      twoFluidPressureDropPa = Math.abs(twoFluidEdge.getHeadLoss());
      beggsBrillProfiles = fromHydraulicProfile(beggsBrillEdge.getHydraulicProfile());
      twoFluidProfiles = fromHydraulicProfile(twoFluidEdge.getHydraulicProfile());
    }

    /**
     * Get canonical edge identifier.
     *
     * @return edge identifier
     */
    public String getEdgeId() {
      return edgeId;
    }

    /**
     * Get Beggs-Brill solved flow rate.
     *
     * @return signed flow rate in kg/s
     */
    public double getBeggsBrillFlowRateKgS() {
      return beggsBrillFlowRateKgS;
    }

    /**
     * Get two-fluid solved flow rate.
     *
     * @return signed flow rate in kg/s
     */
    public double getTwoFluidFlowRateKgS() {
      return twoFluidFlowRateKgS;
    }

    /**
     * Get Beggs-Brill absolute pressure drop.
     *
     * @return pressure drop in Pa
     */
    public double getBeggsBrillPressureDropPa() {
      return beggsBrillPressureDropPa;
    }

    /**
     * Get two-fluid absolute pressure drop.
     *
     * @return pressure drop in Pa
     */
    public double getTwoFluidPressureDropPa() {
      return twoFluidPressureDropPa;
    }

    /**
     * Get absolute pressure-drop difference.
     *
     * @return two-fluid minus Beggs-Brill pressure drop in Pa
     */
    public double getPressureDropDifferencePa() {
      return twoFluidPressureDropPa - beggsBrillPressureDropPa;
    }

    /**
     * Get pressure-drop difference relative to Beggs-Brill.
     *
     * @return signed relative difference
     */
    public double getRelativePressureDropDifference() {
      return relativeDifference(twoFluidPressureDropPa, beggsBrillPressureDropPa);
    }

    /**
     * Get outlet-temperature difference.
     *
     * @return two-fluid minus Beggs-Brill outlet temperature in K
     */
    public double getOutletTemperatureDifferenceK() {
      return last(twoFluidProfiles.temperatureK) - last(beggsBrillProfiles.temperatureK);
    }

    /**
     * Get average liquid-holdup difference.
     *
     * @return two-fluid minus Beggs-Brill mean liquid holdup
     */
    public double getAverageLiquidHoldupDifference() {
      return twoFluidProfiles.getAverageLiquidHoldup() - beggsBrillProfiles.getAverageLiquidHoldup();
    }

    /**
     * Get mean superficial gas-velocity difference relative to Beggs-Brill.
     *
     * @return signed relative difference
     */
    public double getRelativeAverageGasSuperficialVelocityDifference() {
      return relativeDifference(twoFluidProfiles.getAverageGasSuperficialVelocityMs(),
          beggsBrillProfiles.getAverageGasSuperficialVelocityMs());
    }

    /**
     * Get mean superficial liquid-velocity difference relative to Beggs-Brill.
     *
     * @return signed relative difference
     */
    public double getRelativeAverageLiquidSuperficialVelocityDifference() {
      return relativeDifference(twoFluidProfiles.getAverageLiquidSuperficialVelocityMs(),
          beggsBrillProfiles.getAverageLiquidSuperficialVelocityMs());
    }

    /**
     * Get Beggs-Brill normalized profiles.
     *
     * @return Beggs-Brill profiles
     */
    public ModelProfiles getBeggsBrillProfiles() {
      return beggsBrillProfiles;
    }

    /**
     * Get two-fluid normalized profiles.
     *
     * @return two-fluid profiles
     */
    public ModelProfiles getTwoFluidProfiles() {
      return twoFluidProfiles;
    }
  }

  /** Result from two detached, converged topology solves. */
  public static final class Result {
    private final Map<String, EdgeComparison> edgeComparisons;
    private final Map<String, TwoFluidPipe> initializedTwoFluidPipes;
    private final double beggsBrillMassBalanceErrorKgS;
    private final double twoFluidMassBalanceErrorKgS;

    /**
     * Create comparison result.
     *
     * @param comparisons per-edge evidence
     * @param initializedPipes converged two-fluid edge states
     * @param beggsBrillMassBalanceErrorKgS Beggs-Brill network residual
     * @param twoFluidMassBalanceErrorKgS two-fluid network residual
     */
    private Result(Map<String, EdgeComparison> comparisons, Map<String, TwoFluidPipe> initializedPipes,
        double beggsBrillMassBalanceErrorKgS, double twoFluidMassBalanceErrorKgS) {
      edgeComparisons = Collections.unmodifiableMap(new LinkedHashMap<String, EdgeComparison>(comparisons));
      initializedTwoFluidPipes = new LinkedHashMap<String, TwoFluidPipe>(initializedPipes);
      this.beggsBrillMassBalanceErrorKgS = beggsBrillMassBalanceErrorKgS;
      this.twoFluidMassBalanceErrorKgS = twoFluidMassBalanceErrorKgS;
    }

    /**
     * Get compared edge identifiers in deterministic order.
     *
     * @return immutable edge identifier set
     */
    public Set<String> getEdgeIds() {
      return Collections.unmodifiableSet(new LinkedHashSet<String>(edgeComparisons.keySet()));
    }

    /**
     * Get comparison for one edge.
     *
     * @param edgeId canonical edge identifier
     * @return comparison evidence
     * @throws IllegalArgumentException when the edge was not compared
     */
    public EdgeComparison getEdgeComparison(String edgeId) {
      EdgeComparison comparison = edgeComparisons.get(edgeId);
      if (comparison == null) {
        throw new IllegalArgumentException("Edge '" + edgeId + "' was not compared");
      }
      return comparison;
    }

    /**
     * Get Beggs-Brill network mass-balance residual.
     *
     * @return residual in kg/s
     */
    public double getBeggsBrillMassBalanceErrorKgS() {
      return beggsBrillMassBalanceErrorKgS;
    }

    /**
     * Get two-fluid network mass-balance residual.
     *
     * @return residual in kg/s
     */
    public double getTwoFluidMassBalanceErrorKgS() {
      return twoFluidMassBalanceErrorKgS;
    }

    /**
     * Create a fresh transient-ready pipe from an accepted two-fluid edge state.
     *
     * @param edgeId compared edge identifier
     * @return independent initialized two-fluid pipe
     * @throws IllegalArgumentException when the edge was not compared
     */
    public TwoFluidPipe createInitializedTwoFluidPipe(String edgeId) {
      TwoFluidPipe pipe = initializedTwoFluidPipes.get(edgeId);
      if (pipe == null) {
        throw new IllegalArgumentException("Edge '" + edgeId + "' was not compared");
      }
      return SerializationUtils.clone(pipe);
    }
  }

  private final FieldNetworkTopology topology;

  /**
   * Create a comparison runner for one canonical field topology.
   *
   * @param topology typed topology to replay
   */
  public NetworkHydraulicModelComparison(FieldNetworkTopology topology) {
    this.topology = Objects.requireNonNull(topology, "topology cannot be null");
  }

  /**
   * Compare selected pipe edges using Beggs-Brill and two-fluid hydraulics.
   *
   * <p>
   * Other pipe edges retain their configured hydraulic models in both solves. Both complete networks must converge and
   * every selected edge must report its requested model as converged; fallback or stale profile evidence is rejected.
   * </p>
   *
   * @param edgeIds canonical pipe edge identifiers
   * @param id calculation identifier applied to both detached solves
   * @return immutable comparison and initialization evidence
   */
  public Result compare(List<String> edgeIds, UUID id) {
    Objects.requireNonNull(edgeIds, "edgeIds cannot be null");
    Objects.requireNonNull(id, "calculation id cannot be null");
    LinkedHashSet<String> selectedEdges = new LinkedHashSet<String>(edgeIds);
    if (selectedEdges.isEmpty() || selectedEdges.contains(null)) {
      throw new IllegalArgumentException("At least one non-null edge identifier is required");
    }
    topology.validateForExecution();
    for (String edgeId : selectedEdges) {
      topology.getEdge(edgeId);
    }

    FieldNetworkTopology beggsBrillTopology = copyWithBoundaryFluids();
    FieldNetworkTopology twoFluidTopology = copyWithBoundaryFluids();
    LoopedPipeNetwork beggsBrillNetwork = beggsBrillTopology.getHydraulicNetwork();
    LoopedPipeNetwork twoFluidNetwork = twoFluidTopology.getHydraulicNetwork();
    for (String edgeId : selectedEdges) {
      beggsBrillNetwork.getPipe(edgeId).setHydraulicModelType(PipeModelType.BEGGS_BRILL);
      twoFluidNetwork.getPipe(edgeId).setHydraulicModelType(PipeModelType.TWO_FLUID);
    }

    beggsBrillNetwork.run(id);
    twoFluidNetwork.run(id);
    if (!beggsBrillNetwork.isConverged() || !twoFluidNetwork.isConverged()) {
      throw new IllegalStateException("Both hydraulic comparison networks must converge");
    }

    Map<String, EdgeComparison> comparisons = new LinkedHashMap<String, EdgeComparison>();
    Map<String, TwoFluidPipe> initializedPipes = new LinkedHashMap<String, TwoFluidPipe>();
    for (String edgeId : selectedEdges) {
      NetworkPipe beggsBrillEdge = beggsBrillNetwork.getPipe(edgeId);
      NetworkPipe twoFluidEdge = twoFluidNetwork.getPipe(edgeId);
      if (!"BEGGS_BRILL".equals(beggsBrillEdge.getHydraulicModelStatus())
          || beggsBrillEdge.getHydraulicProfile() == null) {
        throw new IllegalStateException("Edge '" + edgeId + "' did not produce accepted Beggs-Brill evidence");
      }
      if (!"TWO_FLUID_CONVERGED".equals(twoFluidEdge.getHydraulicModelStatus())
          || twoFluidEdge.getTwoFluidModel() == null || twoFluidEdge.getHydraulicProfile() == null) {
        throw new IllegalStateException("Edge '" + edgeId + "' did not produce accepted two-fluid evidence");
      }
      comparisons.put(edgeId, new EdgeComparison(edgeId, beggsBrillEdge, twoFluidEdge));
      initializedPipes.put(edgeId, twoFluidEdge.createInitializedTwoFluidPipe());
    }
    return new Result(comparisons, initializedPipes, beggsBrillNetwork.getMassBalanceError(),
        twoFluidNetwork.getMassBalanceError());
  }

  /**
   * Compare selected edges at several discretizations without mutating the caller topology.
   *
   * <p>
   * Every requested count is applied to every selected edge on a detached topology before the normal fail-closed
   * Beggs-Brill/two-fluid comparison is executed. Unselected edge meshes and hydraulic models remain unchanged.
   * </p>
   *
   * @param edgeIds canonical pipe edge identifiers
   * @param segmentCounts distinct segment counts, each at least two
   * @param id calculation identifier applied to the detached solves
   * @return immutable comparison results keyed by segment count
   */
  public MeshSensitivityResult compareMeshSensitivity(List<String> edgeIds, List<Integer> segmentCounts, UUID id) {
    Objects.requireNonNull(edgeIds, "edgeIds cannot be null");
    Objects.requireNonNull(segmentCounts, "segmentCounts cannot be null");
    Objects.requireNonNull(id, "calculation id cannot be null");
    LinkedHashSet<String> selectedEdges = new LinkedHashSet<String>(edgeIds);
    if (selectedEdges.isEmpty() || selectedEdges.contains(null)) {
      throw new IllegalArgumentException("At least one non-null edge identifier is required");
    }
    topology.validateForExecution();
    for (String edgeId : selectedEdges) {
      topology.getEdge(edgeId);
    }
    LinkedHashSet<Integer> uniqueCounts = new LinkedHashSet<Integer>(segmentCounts);
    if (uniqueCounts.size() < 2 || uniqueCounts.contains(null)) {
      throw new IllegalArgumentException("At least two distinct non-null segment counts are required");
    }
    Map<Integer, Result> results = new LinkedHashMap<Integer, Result>();
    for (Integer segmentCount : uniqueCounts) {
      if (segmentCount < 2) {
        throw new IllegalArgumentException("Hydraulic mesh segment counts must be at least two");
      }
      FieldNetworkTopology meshTopology = copyWithBoundaryFluids();
      for (String edgeId : selectedEdges) {
        meshTopology.getHydraulicNetwork().getPipe(edgeId).setMultiphaseSegments(segmentCount);
      }
      Result result = new NetworkHydraulicModelComparison(meshTopology).compare(new ArrayList<String>(selectedEdges),
          id);
      results.put(segmentCount, result);
    }
    return new MeshSensitivityResult(results);
  }

  /**
   * Copy the serializable topology and restore runtime fluid boundaries.
   *
   * @return detached topology ready for a comparison solve
   */
  private FieldNetworkTopology copyWithBoundaryFluids() {
    LoopedPipeNetwork source = topology.getHydraulicNetwork();
    FieldNetworkTopology copy = topology.copyDefinition();
    LoopedPipeNetwork target = copy.getHydraulicNetwork();
    SystemInterface fluidTemplate = source.getFluidTemplate();
    if (fluidTemplate != null) {
      target.setFluidTemplate(fluidTemplate.clone());
    }
    for (String nodeName : source.getAssignedNodeFluidNames()) {
      SystemInterface nodeFluid = source.getNodeFluid(nodeName);
      if (nodeFluid != null) {
        target.setNodeFluid(nodeName, nodeFluid.clone());
      }
    }
    return copy;
  }

  /**
   * Copy canonical normalized edge evidence into the comparison result.
   *
   * @param profile accepted edge profile
   * @return comparison profile
   */
  private static ModelProfiles fromHydraulicProfile(HydraulicProfile profile) {
    if (profile == null) {
      throw new IllegalStateException("Accepted hydraulic comparison profile is missing");
    }
    return new ModelProfiles(profile.getPositionM(), profile.getCellLengthM(), profile.getPressurePa(),
        profile.getTemperatureK(), profile.getLiquidHoldup(), profile.getGasSuperficialVelocityMs(),
        profile.getLiquidSuperficialVelocityMs());
  }

  /**
   * Calculate a cell-length-weighted mean.
   *
   * @param cellLengthM represented cell lengths in m
   * @param values values to average
   * @return distance-weighted mean, or NaN for an empty profile
   */
  private static double distanceWeightedAverage(double[] cellLengthM, double[] values) {
    if (values.length == 0) {
      return Double.NaN;
    }
    double weightedTotal = 0.0;
    double totalLength = 0.0;
    for (int index = 0; index < values.length; index++) {
      weightedTotal += cellLengthM[index] * values[index];
      totalLength += cellLengthM[index];
    }
    return totalLength > 0.0 ? weightedTotal / totalLength : Double.NaN;
  }

  /**
   * Return the last value in a non-empty profile.
   *
   * @param values profile values
   * @return final value
   */
  private static double last(double[] values) {
    if (values.length == 0) {
      throw new IllegalStateException("Hydraulic comparison profile is empty");
    }
    return values[values.length - 1];
  }

  /**
   * Calculate a signed difference normalized to a reference magnitude.
   *
   * @param value candidate value
   * @param reference reference value
   * @return relative difference
   */
  private static double relativeDifference(double value, double reference) {
    if (Math.abs(reference) < 1.0e-15) {
      return value == reference ? 0.0 : Math.copySign(Double.POSITIVE_INFINITY, value);
    }
    return (value - reference) / Math.abs(reference);
  }
}
