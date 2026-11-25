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
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport;
import neqsim.process.equipment.pipeline.TwoFluidConservativeEnergyReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
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

  /** Immutable evidence for one outer time step in a controlled two-fluid rate step. */
  public static final class TransientTimeStepResult {
    private final double outerTimeStepSeconds;
    private final double elapsedTimeSeconds;
    private final int outerIntervalCount;
    private final double massResidualKg;
    private final double maximumRelativeMassResidual;
    private final Map<String, Double> componentResidualKg;
    private final double maximumRelativeComponentResidual;
    private final double energyResidualJ;
    private final double maximumRelativeEnergyResidual;
    private final double maximumRelativeVolumeResidual;
    private final double maximumRelativeThermodynamicEnergyResidual;
    private final ModelProfiles finalProfiles;

    /**
     * Create one transient time-step result.
     *
     * @param outerTimeStepSeconds requested outer time step in s
     * @param elapsedTimeSeconds accepted common time horizon in s
     * @param outerIntervalCount number of accepted outer calls
     * @param massResidualKg signed sum of accepted total-mass residuals in kg
     * @param maximumRelativeMassResidual worst accepted total-mass relative residual
     * @param componentResidualKg signed accumulated named-component residuals in kg
     * @param maximumRelativeComponentResidual worst accepted component-inventory relative residual
     * @param energyResidualJ signed sum of accepted conservative-energy residuals in J
     * @param maximumRelativeEnergyResidual worst accepted relative energy residual
     * @param maximumRelativeVolumeResidual worst accepted occupied-volume residual
     * @param maximumRelativeThermodynamicEnergyResidual worst accepted thermodynamic recovery residual
     * @param finalProfiles final normalized distributed state
     */
    private TransientTimeStepResult(double outerTimeStepSeconds, double elapsedTimeSeconds, int outerIntervalCount,
        double massResidualKg, double maximumRelativeMassResidual, Map<String, Double> componentResidualKg,
        double maximumRelativeComponentResidual, double energyResidualJ, double maximumRelativeEnergyResidual,
        double maximumRelativeVolumeResidual, double maximumRelativeThermodynamicEnergyResidual,
        ModelProfiles finalProfiles) {
      this.outerTimeStepSeconds = outerTimeStepSeconds;
      this.elapsedTimeSeconds = elapsedTimeSeconds;
      this.outerIntervalCount = outerIntervalCount;
      this.massResidualKg = massResidualKg;
      this.maximumRelativeMassResidual = maximumRelativeMassResidual;
      this.componentResidualKg = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(componentResidualKg));
      this.maximumRelativeComponentResidual = maximumRelativeComponentResidual;
      this.energyResidualJ = energyResidualJ;
      this.maximumRelativeEnergyResidual = maximumRelativeEnergyResidual;
      this.maximumRelativeVolumeResidual = maximumRelativeVolumeResidual;
      this.maximumRelativeThermodynamicEnergyResidual = maximumRelativeThermodynamicEnergyResidual;
      this.finalProfiles = finalProfiles;
    }

    /** @return requested outer time step in s */
    public double getOuterTimeStepSeconds() {
      return outerTimeStepSeconds;
    }

    /** @return accepted elapsed time in s */
    public double getElapsedTimeSeconds() {
      return elapsedTimeSeconds;
    }

    /** @return number of accepted outer transient calls */
    public int getOuterIntervalCount() {
      return outerIntervalCount;
    }

    /** @return signed sum of total-mass residuals in kg */
    public double getMassResidualKg() {
      return massResidualKg;
    }

    /** @return worst accepted total-mass relative residual */
    public double getMaximumRelativeMassResidual() {
      return maximumRelativeMassResidual;
    }

    /** @return immutable named-component set in deterministic report order */
    public Set<String> getComponentNames() {
      return Collections.unmodifiableSet(new LinkedHashSet<String>(componentResidualKg.keySet()));
    }

    /**
     * Get the signed accumulated inventory residual for one named component.
     *
     * @param componentName NeqSim component name
     * @return signed residual in kg
     * @throws IllegalArgumentException when the component was not reported
     */
    public double getComponentResidualKg(String componentName) {
      Double residual = componentResidualKg.get(componentName);
      if (residual == null) {
        throw new IllegalArgumentException("Component '" + componentName + "' was not reported");
      }
      return residual;
    }

    /** @return worst accepted component-inventory relative residual */
    public double getMaximumRelativeComponentResidual() {
      return maximumRelativeComponentResidual;
    }

    /** @return signed sum of conservative-energy residuals in J */
    public double getEnergyResidualJ() {
      return energyResidualJ;
    }

    /** @return worst accepted relative conservative-energy residual */
    public double getMaximumRelativeEnergyResidual() {
      return maximumRelativeEnergyResidual;
    }

    /** @return worst accepted occupied-volume relative residual */
    public double getMaximumRelativeVolumeResidual() {
      return maximumRelativeVolumeResidual;
    }

    /** @return worst accepted thermodynamic recovery relative residual */
    public double getMaximumRelativeThermodynamicEnergyResidual() {
      return maximumRelativeThermodynamicEnergyResidual;
    }

    /** @return final normalized two-fluid profiles */
    public ModelProfiles getFinalProfiles() {
      return finalProfiles;
    }
  }

  /** Immutable same-initial-state rate-step results at two or more outer time steps. */
  public static final class TransientTimeStepSensitivityResult {
    private final String edgeId;
    private final double initialMassFlowKgS;
    private final double steppedMassFlowKgS;
    private final Map<Double, TransientTimeStepResult> resultsByTimeStep;

    /**
     * Create rate-step time-step sensitivity evidence.
     *
     * @param edgeId canonical edge identifier
     * @param initialMassFlowKgS converged steady two-fluid mass flow in kg/s
     * @param steppedMassFlowKgS applied inlet mass flow in kg/s
     * @param results results keyed by requested outer time step
     */
    private TransientTimeStepSensitivityResult(String edgeId, double initialMassFlowKgS, double steppedMassFlowKgS,
        Map<Double, TransientTimeStepResult> results) {
      this.edgeId = edgeId;
      this.initialMassFlowKgS = initialMassFlowKgS;
      this.steppedMassFlowKgS = steppedMassFlowKgS;
      resultsByTimeStep = Collections.unmodifiableMap(new LinkedHashMap<Double, TransientTimeStepResult>(results));
    }

    /** @return canonical edge identifier */
    public String getEdgeId() {
      return edgeId;
    }

    /** @return converged steady two-fluid flow in kg/s */
    public double getInitialMassFlowKgS() {
      return initialMassFlowKgS;
    }

    /** @return applied rate-step inlet flow in kg/s */
    public double getSteppedMassFlowKgS() {
      return steppedMassFlowKgS;
    }

    /** @return immutable requested outer time steps in deterministic order */
    public Set<Double> getOuterTimeStepsSeconds() {
      return Collections.unmodifiableSet(new LinkedHashSet<Double>(resultsByTimeStep.keySet()));
    }

    /**
     * Get evidence for one requested outer time step.
     *
     * @param outerTimeStepSeconds requested outer time step in s
     * @return accepted transient evidence
     * @throws IllegalArgumentException when the time step was not evaluated
     */
    public TransientTimeStepResult getResult(double outerTimeStepSeconds) {
      TransientTimeStepResult result = resultsByTimeStep.get(outerTimeStepSeconds);
      if (result == null) {
        throw new IllegalArgumentException("Outer time step " + outerTimeStepSeconds + " s was not evaluated");
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

    /**
     * Create a conservative-energy transient pipe from an accepted two-fluid edge state.
     *
     * <p>
     * The returned copy retains the comparison's converged steady fields without rerunning hydraulics. Its distributed
     * named-component inventory is initialized from the accepted phase state and its transient controls are set to the
     * supported coupled-Euler configuration.
     * </p>
     *
     * @param edgeId compared edge identifier
     * @return independent conservative-energy pipe initialized from the accepted steady state
     * @throws IllegalArgumentException when the edge was not compared
     * @throws IllegalStateException when the accepted pipe cannot enter the supported conservative transient mode
     */
    public TwoFluidPipe createConservativeEnergyInitializedTwoFluidPipe(String edgeId) {
      TwoFluidPipe pipe = createInitializedTwoFluidPipe(edgeId);
      pipe.prepareConservativeEnergyTransientFromSteadyState();
      return pipe;
    }

    /**
     * Apply one controlled inlet-rate step to independent copies of an accepted steady two-fluid state.
     *
     * <p>
     * Every requested outer time step starts from the same converged distributed pressure, temperature, holdup,
     * velocity, component-inventory and energy state. The pipe uses the supported constant-flow inlet and retained
     * pressure outlet, and each run ends at the same requested physical time. Accepted mass, named-component and
     * conservative-energy reports are accumulated without mutating the comparison result or caller topology.
     * </p>
     *
     * <p>
     * This is numerical initialization and time-step evidence. It does not independently qualify two-fluid closures,
     * slug tracking, fluids, geometry or operating envelopes.
     * </p>
     *
     * @param edgeId compared edge identifier
     * @param rateMultiplier positive multiplier applied to the converged signed edge mass flow
     * @param durationSeconds positive common transient horizon in s
     * @param outerTimeStepsSeconds at least two distinct positive outer time steps in s
     * @param id calculation identifier reused for the independent transient runs
     * @return immutable time-step sensitivity evidence
     */
    public TransientTimeStepSensitivityResult compareTransientRateStep(String edgeId, double rateMultiplier,
        double durationSeconds, List<Double> outerTimeStepsSeconds, UUID id) {
      Objects.requireNonNull(outerTimeStepsSeconds, "outerTimeStepsSeconds cannot be null");
      Objects.requireNonNull(id, "calculation id cannot be null");
      EdgeComparison edge = getEdgeComparison(edgeId);
      if (!(rateMultiplier > 0.0) || !Double.isFinite(rateMultiplier)) {
        throw new IllegalArgumentException("Rate multiplier must be positive and finite");
      }
      if (!(durationSeconds > 0.0) || !Double.isFinite(durationSeconds)) {
        throw new IllegalArgumentException("Transient duration must be positive and finite");
      }
      LinkedHashSet<Double> uniqueTimeSteps = new LinkedHashSet<Double>(outerTimeStepsSeconds);
      if (uniqueTimeSteps.size() < 2 || uniqueTimeSteps.contains(null)) {
        throw new IllegalArgumentException("At least two distinct non-null outer time steps are required");
      }
      for (Double timeStep : uniqueTimeSteps) {
        if (!(timeStep > 0.0) || !Double.isFinite(timeStep)) {
          throw new IllegalArgumentException("Outer time steps must be positive and finite");
        }
      }
      double initialMassFlowKgS = edge.getTwoFluidFlowRateKgS();
      double steppedMassFlowKgS = initialMassFlowKgS * rateMultiplier;
      if (!Double.isFinite(steppedMassFlowKgS)) {
        throw new IllegalArgumentException("Stepped inlet mass flow must be finite");
      }
      Map<Double, TransientTimeStepResult> results = new LinkedHashMap<Double, TransientTimeStepResult>();
      for (Double timeStep : uniqueTimeSteps) {
        results.put(timeStep,
            runTransientRateStep(edgeId, steppedMassFlowKgS, durationSeconds, timeStep.doubleValue(), id));
      }
      return new TransientTimeStepSensitivityResult(edgeId, initialMassFlowKgS, steppedMassFlowKgS, results);
    }

    /**
     * Execute one accepted-state rate-step run and accumulate its conservation diagnostics.
     *
     * @param edgeId compared edge identifier
     * @param steppedMassFlowKgS constant inlet mass flow in kg/s
     * @param durationSeconds common time horizon in s
     * @param outerTimeStepSeconds requested outer time step in s
     * @param id calculation identifier
     * @return accepted transient evidence
     */
    private TransientTimeStepResult runTransientRateStep(String edgeId, double steppedMassFlowKgS,
        double durationSeconds, double outerTimeStepSeconds, UUID id) {
      TwoFluidPipe pipe = createConservativeEnergyInitializedTwoFluidPipe(edgeId);
      pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
      pipe.setInletMassFlow(steppedMassFlowKgS);
      double elapsed = 0.0;
      int intervals = 0;
      double massResidualKg = 0.0;
      double maximumRelativeMassResidual = 0.0;
      Map<String, Double> componentResidualKg = new LinkedHashMap<String, Double>();
      double maximumRelativeComponentResidual = 0.0;
      double energyResidualJ = 0.0;
      double maximumRelativeEnergyResidual = 0.0;
      double maximumRelativeVolumeResidual = 0.0;
      double maximumRelativeThermodynamicEnergyResidual = 0.0;
      double timeTolerance = 16.0 * Math.ulp(durationSeconds);
      while (durationSeconds - elapsed > timeTolerance) {
        double interval = Math.min(outerTimeStepSeconds, durationSeconds - elapsed);
        pipe.runTransient(interval, id);
        TwoFluidMassBalanceReport mass = pipe.getLastMassBalanceReport();
        TwoFluidComponentConservationReport components = pipe.getLastComponentConservationReport();
        TwoFluidConservativeEnergyReport energy = pipe.getLastConservativeEnergyReport();
        if (mass == null || components == null || energy == null || !components.isConverged()) {
          throw new IllegalStateException("Two-fluid rate step did not produce accepted conservation evidence");
        }
        massResidualKg += mass.getResidualKg(TwoFluidMassBalanceReport.Phase.TOTAL);
        maximumRelativeMassResidual = Math.max(maximumRelativeMassResidual,
            mass.getRelativeResidual(TwoFluidMassBalanceReport.Phase.TOTAL));
        maximumRelativeComponentResidual = Math.max(maximumRelativeComponentResidual,
            components.getMaximumRelativeInventoryResidual());
        accumulateComponentResiduals(componentResidualKg, components);
        energyResidualJ += energy.getResidualJ();
        maximumRelativeEnergyResidual = Math.max(maximumRelativeEnergyResidual, energy.getRelativeResidual());
        maximumRelativeVolumeResidual = Math.max(maximumRelativeVolumeResidual, energy.getMaxRelativeVolumeResidual());
        maximumRelativeThermodynamicEnergyResidual = Math.max(maximumRelativeThermodynamicEnergyResidual,
            energy.getMaxRelativeThermodynamicEnergyResidual());
        elapsed += interval;
        intervals++;
      }
      if (Math.abs(durationSeconds - elapsed) <= timeTolerance) {
        elapsed = durationSeconds;
      }
      return new TransientTimeStepResult(outerTimeStepSeconds, elapsed, intervals, massResidualKg,
          maximumRelativeMassResidual, componentResidualKg, maximumRelativeComponentResidual, energyResidualJ,
          maximumRelativeEnergyResidual, maximumRelativeVolumeResidual, maximumRelativeThermodynamicEnergyResidual,
          fromTwoFluidPipe(pipe));
    }

    /**
     * Accumulate signed named-component inventory residuals with fail-closed component identity.
     *
     * @param accumulated residuals accumulated across accepted outer calls
     * @param report accepted interval component report
     */
    private void accumulateComponentResiduals(Map<String, Double> accumulated,
        TwoFluidComponentConservationReport report) {
      String[] names = report.getComponentNames();
      double[] residuals = report.getInventoryResidualKg();
      boolean initialized = !accumulated.isEmpty();
      if (initialized && accumulated.size() != names.length) {
        throw new IllegalStateException("Two-fluid rate step changed its reported component identity");
      }
      for (int index = 0; index < names.length; index++) {
        Double previous = accumulated.get(names[index]);
        if (previous == null && initialized) {
          throw new IllegalStateException("Two-fluid rate step changed its reported component identity");
        }
        accumulated.put(names[index], (previous == null ? 0.0 : previous.doubleValue()) + residuals[index]);
      }
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
   * Normalize the distributed state of a transient two-fluid pipe.
   *
   * @param pipe accepted transient pipe
   * @return normalized final profiles
   */
  private static ModelProfiles fromTwoFluidPipe(TwoFluidPipe pipe) {
    double[] holdup = pipe.getLiquidHoldupProfile();
    double[] gasVelocity = pipe.getGasVelocityProfile();
    double[] liquidVelocity = pipe.getLiquidVelocityProfile();
    double[] gasSuperficialVelocity = new double[holdup.length];
    double[] liquidSuperficialVelocity = new double[holdup.length];
    for (int index = 0; index < holdup.length; index++) {
      gasSuperficialVelocity[index] = gasVelocity[index] * (1.0 - holdup[index]);
      liquidSuperficialVelocity[index] = liquidVelocity[index] * holdup[index];
    }
    return new ModelProfiles(pipe.getPositionProfile(), sectionLengths(pipe), pipe.getPressureProfile(),
        pipe.getTemperatureProfile(), holdup, gasSuperficialVelocity, liquidSuperficialVelocity);
  }

  /**
   * Read physical cell lengths from the accepted two-fluid state, including a uniform mesh.
   *
   * @param pipe accepted transient pipe
   * @return cell lengths in m
   */
  private static double[] sectionLengths(TwoFluidPipe pipe) {
    TwoFluidSection[] sections = pipe.getSectionSnapshots();
    double[] lengths = new double[sections.length];
    for (int index = 0; index < sections.length; index++) {
      lengths[index] = sections[index].getLength();
    }
    return lengths;
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
