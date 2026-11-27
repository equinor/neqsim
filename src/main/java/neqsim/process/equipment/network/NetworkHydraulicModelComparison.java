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
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.ProfileQuantity;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.SpatialConvergenceStatus;
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
  private static final double GRID_RATIO_TOLERANCE = 1.0e-10;
  private static final double GRID_ROUNDOFF_THRESHOLD = 1.0e-10;
  private static final double GRID_CONVERGENCE_SAFETY_FACTOR = 1.25;

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

    /**
     * Select one normalized profile quantity.
     *
     * @param quantity requested quantity
     * @return owned immutable profile array
     */
    private double[] values(ProfileQuantity quantity) {
      switch (quantity) {
      case PRESSURE:
        return pressurePa;
      case TEMPERATURE:
        return temperatureK;
      case LIQUID_HOLDUP:
        return liquidHoldup;
      case GAS_VELOCITY:
        return gasSuperficialVelocityMs;
      case LIQUID_VELOCITY:
        return liquidSuperficialVelocityMs;
      default:
        throw new IllegalArgumentException("Unsupported profile quantity " + quantity);
      }
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

    /**
     * Quantify three-grid convergence for one hydraulic model, edge and profile quantity.
     *
     * <p>
     * The requested segment counts must increase with one constant refinement ratio. The comparison uses the same
     * symmetric common-position maximum discrepancy as {@link TwoFluidPipeNetworkRefinementStudy}: each profile is
     * interpolated onto the other grid and the larger discrepancy is normalized by the largest magnitude across all
     * three profiles. A fine-grid convergence index is published only for a resolved monotonic sequence.
     * </p>
     *
     * @param coarseSegments coarsest segment count
     * @param mediumSegments intermediate segment count
     * @param fineSegments finest segment count
     * @param edgeId canonical compared edge identifier
     * @param modelType either {@link PipeModelType#BEGGS_BRILL} or {@link PipeModelType#TWO_FLUID}
     * @param quantity profile quantity
     * @return immutable three-grid convergence receipt
     * @throws IllegalArgumentException when a mesh is missing, ratios are unequal, the model is unsupported or the
     * profiles are invalid
     */
    public HydraulicSpatialConvergenceReport getSpatialConvergenceReport(int coarseSegments, int mediumSegments,
        int fineSegments, String edgeId, PipeModelType modelType, ProfileQuantity quantity) {
      if (edgeId == null || edgeId.trim().isEmpty()) {
        throw new IllegalArgumentException("Spatial convergence requires a canonical edge identifier");
      }
      if (modelType == null || quantity == null) {
        throw new IllegalArgumentException("Spatial convergence requires a hydraulic model and profile quantity");
      }
      if (coarseSegments >= mediumSegments || mediumSegments >= fineSegments) {
        throw new IllegalArgumentException("Spatial convergence segment counts must increase from coarse to fine");
      }
      double coarseMediumRatio = (double) mediumSegments / coarseSegments;
      double mediumFineRatio = (double) fineSegments / mediumSegments;
      double ratioScale = Math.max(coarseMediumRatio, mediumFineRatio);
      if (coarseMediumRatio <= 1.0 || mediumFineRatio <= 1.0
          || Math.abs(coarseMediumRatio - mediumFineRatio) > GRID_RATIO_TOLERANCE * ratioScale) {
        throw new IllegalArgumentException(
            "Spatial convergence requires one constant refinement ratio greater than one");
      }
      ModelProfiles coarse = getProfiles(getResult(coarseSegments), edgeId, modelType);
      ModelProfiles medium = getProfiles(getResult(mediumSegments), edgeId, modelType);
      ModelProfiles fine = getProfiles(getResult(fineSegments), edgeId, modelType);
      validateCommonLength(coarse, medium, fine);
      double scale = maximumMagnitude(coarse.values(quantity), medium.values(quantity), fine.values(quantity));
      if (!Double.isFinite(scale) || scale <= 0.0) {
        throw new IllegalArgumentException("Spatial convergence normalization scale must be finite and positive");
      }
      MaximumDifference coarseMediumMaximum = maximumDifference(coarse, medium, quantity);
      MaximumDifference mediumFineMaximum = maximumDifference(medium, fine, quantity);
      double coarseMediumDifference = coarseMediumMaximum.absoluteDifference / scale;
      double mediumFineDifference = mediumFineMaximum.absoluteDifference / scale;
      if (!Double.isFinite(coarseMediumDifference) || !Double.isFinite(mediumFineDifference)
          || coarseMediumDifference < 0.0 || mediumFineDifference < 0.0) {
        throw new IllegalArgumentException("Spatial convergence discrepancies must be finite and non-negative");
      }
      SpatialConvergenceStatus status;
      double observedOrder = Double.NaN;
      double fineGridConvergenceIndex = Double.NaN;
      if (coarseMediumDifference <= GRID_ROUNDOFF_THRESHOLD || mediumFineDifference <= GRID_ROUNDOFF_THRESHOLD) {
        status = SpatialConvergenceStatus.ROUND_OFF_LIMITED;
      } else {
        observedOrder = Math.log(coarseMediumDifference / mediumFineDifference) / Math.log(coarseMediumRatio);
        if (Double.isFinite(observedOrder) && observedOrder > 0.0) {
          double denominator = Math.pow(coarseMediumRatio, observedOrder) - 1.0;
          fineGridConvergenceIndex = GRID_CONVERGENCE_SAFETY_FACTOR * mediumFineDifference / denominator;
          status = SpatialConvergenceStatus.MONOTONIC;
        } else {
          status = SpatialConvergenceStatus.NON_MONOTONIC;
        }
      }
      return new HydraulicSpatialConvergenceReport(edgeId, modelType, quantity, coarseSegments, mediumSegments,
          fineSegments, coarseMediumRatio, scale, coarseMediumDifference, coarseMediumMaximum.signedDifference,
          coarseMediumMaximum.positionM, mediumFineDifference, mediumFineMaximum.signedDifference,
          mediumFineMaximum.positionM, observedOrder, fineGridConvergenceIndex, status);
    }

    /**
     * Select normalized profiles for one accepted model result.
     *
     * @param result accepted mesh result
     * @param edgeId canonical edge identifier
     * @param modelType requested hydraulic model
     * @return normalized profiles
     */
    private static ModelProfiles getProfiles(Result result, String edgeId, PipeModelType modelType) {
      EdgeComparison edge = result.getEdgeComparison(edgeId);
      if (modelType == PipeModelType.BEGGS_BRILL) {
        return edge.getBeggsBrillProfiles();
      }
      if (modelType == PipeModelType.TWO_FLUID) {
        return edge.getTwoFluidProfiles();
      }
      throw new IllegalArgumentException("Spatial convergence supports only Beggs-Brill and two-fluid profiles");
    }

    /**
     * Require the three profile grids to represent one unchanged physical edge length.
     *
     * @param coarse coarsest profile
     * @param medium intermediate profile
     * @param fine finest profile
     */
    private static void validateCommonLength(ModelProfiles coarse, ModelProfiles medium, ModelProfiles fine) {
      double coarseLength = sum(coarse.cellLengthM);
      double mediumLength = sum(medium.cellLengthM);
      double fineLength = sum(fine.cellLengthM);
      double scale = Math.max(coarseLength, Math.max(mediumLength, fineLength));
      if (!(scale > 0.0) || Math.abs(coarseLength - mediumLength) > GRID_RATIO_TOLERANCE * scale
          || Math.abs(mediumLength - fineLength) > GRID_RATIO_TOLERANCE * scale) {
        throw new IllegalArgumentException("Spatial convergence profiles must preserve one positive edge length");
      }
    }
  }

  /**
   * Immutable three-grid convergence evidence for one same-topology hydraulic model profile.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class HydraulicSpatialConvergenceReport {
    private final String edgeId;
    private final PipeModelType modelType;
    private final ProfileQuantity quantity;
    private final int coarseSegments;
    private final int mediumSegments;
    private final int fineSegments;
    private final double refinementRatio;
    private final double normalizationScale;
    private final double coarseMediumDifference;
    private final double coarseMediumSignedDifference;
    private final double coarseMediumMaximumDifferencePositionM;
    private final double mediumFineDifference;
    private final double mediumFineSignedDifference;
    private final double mediumFineMaximumDifferencePositionM;
    private final double observedOrder;
    private final double fineGridConvergenceIndex;
    private final SpatialConvergenceStatus status;

    /**
     * Create one immutable hydraulic spatial-convergence receipt.
     *
     * @param edgeId canonical edge identifier
     * @param modelType compared hydraulic model
     * @param quantity compared profile quantity
     * @param coarseSegments coarsest segment count
     * @param mediumSegments intermediate segment count
     * @param fineSegments finest segment count
     * @param refinementRatio constant grid refinement ratio
     * @param normalizationScale maximum absolute quantity magnitude across all three profiles
     * @param coarseMediumDifference normalized coarse-to-medium discrepancy
     * @param coarseMediumSignedDifference signed coarse-minus-medium discrepancy in quantity units at its maximum
     * @param coarseMediumMaximumDifferencePositionM position of maximum coarse-to-medium discrepancy in m
     * @param mediumFineDifference normalized medium-to-fine discrepancy
     * @param mediumFineSignedDifference signed medium-minus-fine discrepancy in quantity units at its maximum
     * @param mediumFineMaximumDifferencePositionM position of maximum medium-to-fine discrepancy in m
     * @param observedOrder observed order, or {@link Double#NaN}
     * @param fineGridConvergenceIndex safety-factored fine-grid index, or {@link Double#NaN}
     * @param status explicit convergence classification
     */
    private HydraulicSpatialConvergenceReport(String edgeId, PipeModelType modelType, ProfileQuantity quantity,
        int coarseSegments, int mediumSegments, int fineSegments, double refinementRatio, double normalizationScale,
        double coarseMediumDifference, double coarseMediumSignedDifference,
        double coarseMediumMaximumDifferencePositionM, double mediumFineDifference, double mediumFineSignedDifference,
        double mediumFineMaximumDifferencePositionM, double observedOrder, double fineGridConvergenceIndex,
        SpatialConvergenceStatus status) {
      this.edgeId = edgeId;
      this.modelType = modelType;
      this.quantity = quantity;
      this.coarseSegments = coarseSegments;
      this.mediumSegments = mediumSegments;
      this.fineSegments = fineSegments;
      this.refinementRatio = refinementRatio;
      this.normalizationScale = normalizationScale;
      this.coarseMediumDifference = coarseMediumDifference;
      this.coarseMediumSignedDifference = coarseMediumSignedDifference;
      this.coarseMediumMaximumDifferencePositionM = coarseMediumMaximumDifferencePositionM;
      this.mediumFineDifference = mediumFineDifference;
      this.mediumFineSignedDifference = mediumFineSignedDifference;
      this.mediumFineMaximumDifferencePositionM = mediumFineMaximumDifferencePositionM;
      this.observedOrder = observedOrder;
      this.fineGridConvergenceIndex = fineGridConvergenceIndex;
      this.status = status;
    }

    /** @return canonical compared edge identifier */
    public String getEdgeId() {
      return edgeId;
    }

    /** @return compared hydraulic model */
    public PipeModelType getModelType() {
      return modelType;
    }

    /** @return compared profile quantity */
    public ProfileQuantity getQuantity() {
      return quantity;
    }

    /** @return coarsest segment count */
    public int getCoarseSegments() {
      return coarseSegments;
    }

    /** @return intermediate segment count */
    public int getMediumSegments() {
      return mediumSegments;
    }

    /** @return finest segment count */
    public int getFineSegments() {
      return fineSegments;
    }

    /** @return constant grid refinement ratio */
    public double getRefinementRatio() {
      return refinementRatio;
    }

    /** @return maximum absolute quantity magnitude used to normalize both discrepancies */
    public double getNormalizationScale() {
      return normalizationScale;
    }

    /** @return normalized coarse-to-medium discrepancy */
    public double getCoarseMediumDifference() {
      return coarseMediumDifference;
    }

    /** @return signed coarse-minus-medium discrepancy in quantity units at the reported maximum */
    public double getCoarseMediumSignedDifference() {
      return coarseMediumSignedDifference;
    }

    /** @return position of maximum coarse-to-medium discrepancy in m */
    public double getCoarseMediumMaximumDifferencePositionM() {
      return coarseMediumMaximumDifferencePositionM;
    }

    /** @return normalized medium-to-fine discrepancy */
    public double getMediumFineDifference() {
      return mediumFineDifference;
    }

    /** @return signed medium-minus-fine discrepancy in quantity units at the reported maximum */
    public double getMediumFineSignedDifference() {
      return mediumFineSignedDifference;
    }

    /** @return position of maximum medium-to-fine discrepancy in m */
    public double getMediumFineMaximumDifferencePositionM() {
      return mediumFineMaximumDifferencePositionM;
    }

    /** @return observed convergence order, or {@link Double#NaN} */
    public double getObservedOrder() {
      return observedOrder;
    }

    /** @return safety-factored fine-grid convergence index, or {@link Double#NaN} */
    public double getFineGridConvergenceIndex() {
      return fineGridConvergenceIndex;
    }

    /** @return explicit convergence status */
    public SpatialConvergenceStatus getStatus() {
      return status;
    }

    /** @return true only for a resolved positive-order sequence */
    public boolean isMonotonicallyConvergent() {
      return status == SpatialConvergenceStatus.MONOTONIC;
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
   * Return the largest absolute value across one or more profile arrays.
   *
   * @param profiles profile arrays
   * @return positive finite-or-roundoff scale
   */
  private static double maximumMagnitude(double[]... profiles) {
    double scale = 1.0e-30;
    for (double[] profile : profiles) {
      for (double value : profile) {
        scale = Math.max(scale, Math.abs(value));
      }
    }
    return scale;
  }

  /**
   * Calculate the symmetric common-position maximum absolute discrepancy.
   *
   * @param first first profile
   * @param second second profile
   * @param quantity requested quantity
   * @return maximum signed and absolute discrepancy with its physical position
   */
  private static MaximumDifference maximumDifference(ModelProfiles first, ModelProfiles second,
      ProfileQuantity quantity) {
    double[] firstValues = first.values(quantity);
    double[] secondValues = second.values(quantity);
    MaximumDifference maximum = directionalMaximumDifference(first.positionM, firstValues, second.positionM,
        secondValues, true);
    return maximum.selectLarger(
        directionalMaximumDifference(second.positionM, secondValues, first.positionM, firstValues, false));
  }

  /**
   * Interpolate one source profile at every target position and return the maximum discrepancy.
   *
   * @param sourcePositions source cell-centre positions
   * @param sourceValues source values
   * @param targetPositions target cell-centre positions
   * @param targetValues target values
   * @param sourceIsFirst whether source values belong to the first profile in the signed first-minus-second convention
   * @return maximum signed and absolute interpolated discrepancy with its physical position
   */
  private static MaximumDifference directionalMaximumDifference(double[] sourcePositions, double[] sourceValues,
      double[] targetPositions, double[] targetValues, boolean sourceIsFirst) {
    if (sourcePositions.length == 0 || sourcePositions.length != sourceValues.length || targetPositions.length == 0
        || targetPositions.length != targetValues.length) {
      throw new IllegalArgumentException("Hydraulic profile positions and values must be non-empty and aligned");
    }
    MaximumDifference maximum = new MaximumDifference(0.0, targetPositions[0]);
    for (int index = 0; index < targetPositions.length; index++) {
      double interpolatedDifference = interpolate(sourcePositions, sourceValues, targetPositions[index])
          - targetValues[index];
      double signedDifference = sourceIsFirst ? interpolatedDifference : -interpolatedDifference;
      maximum = maximum.selectLarger(new MaximumDifference(signedDifference, targetPositions[index]));
    }
    return maximum;
  }

  /**
   * Immutable maximum profile discrepancy with a consistent first-minus-second sign convention.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class MaximumDifference {
    private final double absoluteDifference;
    private final double signedDifference;
    private final double positionM;

    /**
     * Create one discrepancy candidate.
     *
     * @param signedDifference signed first-minus-second discrepancy in quantity units
     * @param positionM physical comparison position in m
     */
    private MaximumDifference(double signedDifference, double positionM) {
      this.absoluteDifference = Math.abs(signedDifference);
      this.signedDifference = signedDifference;
      this.positionM = positionM;
    }

    /**
     * Select the larger discrepancy, breaking exact ties by the upstream-most position.
     *
     * @param candidate competing discrepancy
     * @return selected immutable discrepancy
     */
    private MaximumDifference selectLarger(MaximumDifference candidate) {
      if (candidate.absoluteDifference > absoluteDifference
          || (candidate.absoluteDifference == absoluteDifference && candidate.positionM < positionM)) {
        return candidate;
      }
      return this;
    }
  }

  /**
   * Linearly interpolate one non-empty monotonic cell-centre profile.
   *
   * @param positions source positions in m
   * @param values source values
   * @param target target position in m
   * @return interpolated value with endpoint extrapolation by nearest sample
   */
  private static double interpolate(double[] positions, double[] values, double target) {
    if (positions.length == 0 || positions.length != values.length) {
      throw new IllegalArgumentException("Hydraulic profile positions and values must be non-empty and aligned");
    }
    if (target <= positions[0]) {
      return values[0];
    }
    int lastIndex = positions.length - 1;
    if (target >= positions[lastIndex]) {
      return values[lastIndex];
    }
    int upper = 1;
    while (positions[upper] < target) {
      upper++;
    }
    int lower = upper - 1;
    double fraction = (target - positions[lower]) / (positions[upper] - positions[lower]);
    return values[lower] + fraction * (values[upper] - values[lower]);
  }

  /**
   * Sum one finite profile array.
   *
   * @param values values to sum
   * @return arithmetic sum
   */
  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
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
