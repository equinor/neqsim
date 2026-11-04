package neqsim.pvtsimulation.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, EOS-independent component accounting for an isothermal gas-condensate CVD experiment.
 *
 * <p>
 * The first observation is the saturated, all-vapor reference with zero withdrawal and zero liquid volume. Each later
 * observation describes the cell AFTER vapor withdrawal restores that same reference volume. The withdrawn vapor has
 * that stage's equilibrium vapor composition; cumulative production is moles per initial mole, not standard gas volume.
 * Liquid volume is a fraction of the initial saturated cell volume; do not substitute a pre-withdrawal fraction or
 * reservoir liquid saturation. These are the Whitson-Torp CVD material-balance conventions (JPT 1983, DOI
 * 10.2118/10067-PA). No separator conversion, EOS flash, smoothing, normalization or parameter fitting is performed.
 * </p>
 *
 * <p>
 * Negative inventories and unresolved liquid amounts are retained as failed diagnostics. A zero/vanishing liquid amount
 * never supplies an invented composition. Bounds on an optional final liquid analysis are caller-specified absolute
 * mole-fraction differences; they are not inferred sigmas or propagated measurement uncertainty. Passing arithmetic
 * does not establish measured accuracy, sample validity, critical behavior or laboratory-to-field qualification. This
 * opt-in operation adds no work to ordinary simulation paths.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CvdMaterialBalance implements Serializable {
  /** Serialization compatibility identifier. */
  private static final long serialVersionUID = 1L;
  /** Absolute mole-fraction sum tolerance; never used to normalize inputs. */
  private static final double COMPOSITION_TOLERANCE = 1e-10;
  /** Immutable sample/composition revision identity. */
  private final String sampleReference;
  /** Complete experiment and physical protocol source reference. */
  private final String experimentReference;
  /** Public access, data-use and laboratory adjustment provenance. */
  private final String dataUseReference;
  /** Constant cell temperature in K. */
  private final double temperatureK;
  /** Unique ordered component identities for all primitive arrays. */
  private final String[] componentNames;
  /** Complete immutable ordered post-withdrawal observation set. */
  private final Observation[] observations;
  /** Absolute component/inventory tolerance in mol per initial mol. */
  private final double balanceTolerance;
  /** Strict lower resolution limit for liquid composition, mol per initial mol. */
  private final double minimumLiquidMoles;
  /** Optional independently attributed final liquid comparison. */
  private final FinalLiquidAnalysis finalAnalysis;
  /** Complete immutable signed accounting results. */
  private final Stage[] stages;

  /**
   * Freezes the complete experiment and reconstructs all inventories on a one-initial-mole basis.
   *
   * @param sampleReference immutable sample/composition revision identity
   * @param experimentReference whole experiment, protocol and source/page/table reference
   * @param dataUseReference public access, data-use provenance and any source adjustments
   * @param temperatureK constant cell temperature in K
   * @param componentNames unique component identities in every composition array's order
   * @param observations saturated reference followed by strictly decreasing absolute pressures
   * @param balanceTolerance nonnegative absolute inventory tolerance in mol per initial mol, less than one
   * @param minimumLiquidMoles minimum resolved liquid amount, greater than balanceTolerance and at most one
   * @param finalAnalysis optional separately identified final liquid analysis; null makes no measured comparison
   * @throws IllegalArgumentException if inputs are missing, structurally invalid or arithmetic is unrepresentable
   */
  public CvdMaterialBalance(String sampleReference, String experimentReference, String dataUseReference,
      double temperatureK, String[] componentNames, Observation[] observations, double balanceTolerance,
      double minimumLiquidMoles, FinalLiquidAnalysis finalAnalysis) {
    this.sampleReference = CalibratedPvtModel.reference(sampleReference);
    this.experimentReference = CalibratedPvtModel.reference(experimentReference);
    this.dataUseReference = CalibratedPvtModel.reference(dataUseReference);
    if (!Double.isFinite(temperatureK) || temperatureK <= 0 || !Double.isFinite(balanceTolerance)
        || balanceTolerance < 0 || balanceTolerance >= 1 || !Double.isFinite(minimumLiquidMoles)
        || minimumLiquidMoles <= balanceTolerance || minimumLiquidMoles > 1 || componentNames == null
        || componentNames.length == 0 || observations == null || observations.length < 2) {
      throw new IllegalArgumentException(
          "CVD requires temperature, components, complete observations and valid limits");
    }
    this.temperatureK = temperatureK;
    this.balanceTolerance = balanceTolerance;
    this.minimumLiquidMoles = minimumLiquidMoles;
    this.componentNames = componentNames.clone();
    Set<String> names = new HashSet<String>();
    for (int j = 0; j < this.componentNames.length; j++) {
      this.componentNames[j] = CalibratedPvtModel.reference(this.componentNames[j]);
      if (!names.add(this.componentNames[j])) {
        throw new IllegalArgumentException("Duplicate CVD component identity");
      }
    }
    this.observations = observations.clone();
    Set<String> rows = new HashSet<String>();
    for (int k = 0; k < this.observations.length; k++) {
      Observation o = this.observations[k];
      if (o == null || o.vaporComposition.length != componentNames.length || !rows.add(o.rowId)
          || (k > 0 && (o.pressurePa >= this.observations[k - 1].pressurePa
              || o.cumulativeProduced < this.observations[k - 1].cumulativeProduced))) {
        throw new IllegalArgumentException("CVD row dimensions, identities, pressure or cumulative production invalid");
      }
    }
    Observation reference = this.observations[0];
    if (reference.liquidVolumeFraction != 0 || reference.cumulativeProduced != 0) {
      throw new IllegalArgumentException("First CVD row must be the all-vapor saturation reference before withdrawal");
    }
    if (finalAnalysis != null && finalAnalysis.composition.length != componentNames.length) {
      throw new IllegalArgumentException("Final liquid analysis component order/dimension must match the experiment");
    }
    this.finalAnalysis = finalAnalysis;
    this.stages = reconstruct();
  }

  /**
   * Returns the frozen sample identity for optional checkpoint attachment.
   *
   * @return sample reference
   */
  public String getSampleReference() {
    return sampleReference;
  }

  /**
   * Returns the component identities used by every primitive-array result.
   *
   * @return defensive copy of ordered names
   */
  public String[] getComponentNames() {
    return componentNames.clone();
  }

  /**
   * Returns the complete observation count, including the saturation reference and failed rows.
   *
   * @return observation count
   */
  public int getObservationCount() {
    return observations.length;
  }

  /**
   * Returns a retained immutable stage, including a failed stage.
   *
   * @param index zero-based original observation index
   * @return stage inventories and liquid resolution status
   */
  public Stage getStage(int index) {
    return stages[index];
  }

  /**
   * Checks all stages and the optional final liquid comparison without discarding failing observations.
   *
   * @return true only if inventories close physically and any supplied final analysis agrees within its bounds
   */
  public boolean isCompatible() {
    for (Stage stage : stages) {
      if (!stage.compatible) {
        return false;
      }
    }
    if (finalAnalysis != null) {
      Stage last = stages[stages.length - 1];
      if (last.liquidState != LiquidState.RESOLVED) {
        return false;
      }
      for (int j = 0; j < componentNames.length; j++) {
        if (Math.abs(finalAnalysis.composition[j] - last.liquidComposition[j]) > finalAnalysis.absoluteBounds[j]) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Rejects an optional checkpoint handoff while leaving the original report available to inspect.
   *
   * @throws IllegalStateException if any stage or supplied final analysis is incompatible
   */
  public void requireCompatible() {
    if (!isCompatible()) {
      throw new IllegalStateException("Incompatible CVD material-balance evidence: " + experimentReference);
    }
  }

  /**
   * Reconstructs gas, liquid and named produced/retained component amounts without an EOS.
   *
   * @return immutable stage array
   */
  private Stage[] reconstruct() {
    Stage[] result = new Stage[observations.length];
    Observation reference = observations[0];
    double[] produced = new double[componentNames.length];
    for (int k = 0; k < result.length; k++) {
      Observation o = observations[k];
      double withdrawal = k == 0 ? 0 : o.cumulativeProduced - observations[k - 1].cumulativeProduced;
      double vapor = (o.pressurePa / reference.pressurePa) * (reference.gasZ / o.gasZ) * (1 - o.liquidVolumeFraction);
      double total = 1 - o.cumulativeProduced;
      double liquid = total - vapor;
      if (!Double.isFinite(vapor) || (vapor == 0 && o.liquidVolumeFraction < 1)) {
        throw new IllegalArgumentException("CVD vapor inventory overflows or underflows at " + o.rowId);
      }
      double[] remaining = new double[componentNames.length];
      double[] liquidComponents = new double[componentNames.length];
      boolean compatible = vapor >= 0 && vapor <= total + balanceTolerance && liquid >= -balanceTolerance;
      double componentSum = 0;
      for (int j = 0; j < componentNames.length; j++) {
        produced[j] += withdrawal * o.vaporComposition[j];
        remaining[j] = reference.vaporComposition[j] - produced[j];
        liquidComponents[j] = remaining[j] - vapor * o.vaporComposition[j];
        componentSum += liquidComponents[j];
        compatible &= remaining[j] >= -balanceTolerance && liquidComponents[j] >= -balanceTolerance;
      }
      double closure = componentSum - liquid;
      double remainingClosure = sum(remaining) - total;
      double producedClosure = sum(produced) - o.cumulativeProduced;
      double vaporClosure = vapor * sum(o.vaporComposition) - vapor;
      if (!Double.isFinite(closure) || !Double.isFinite(vaporClosure)) {
        throw new IllegalArgumentException("CVD component accounting is unrepresentable at " + o.rowId);
      }
      compatible &= Math.abs(closure) <= balanceTolerance && Math.abs(remainingClosure) <= balanceTolerance
          && Math.abs(producedClosure) <= balanceTolerance && Math.abs(vaporClosure) <= balanceTolerance;
      LiquidState state;
      double[] composition = null;
      if (!compatible || (o.liquidVolumeFraction == 0 && liquid > balanceTolerance)) {
        state = LiquidState.INCONSISTENT;
        compatible = false;
      } else if (o.liquidVolumeFraction == 0 && Math.abs(liquid) <= balanceTolerance) {
        state = LiquidState.ABSENT;
        for (double amount : liquidComponents) {
          compatible &= Math.abs(amount) <= balanceTolerance;
        }
        if (!compatible) {
          state = LiquidState.INCONSISTENT;
        }
      } else if (liquid <= minimumLiquidMoles) {
        state = LiquidState.UNRESOLVED;
        compatible = false;
      } else {
        state = LiquidState.RESOLVED;
        composition = new double[componentNames.length];
        for (int j = 0; j < composition.length; j++) {
          composition[j] = liquidComponents[j] / liquid;
          if (composition[j] < 0 || composition[j] > 1) {
            compatible = false;
          }
        }
        if (!compatible) {
          state = LiquidState.INCONSISTENT;
          composition = null;
        }
      }
      result[k] = new Stage(total, vapor, liquid, withdrawal, produced, remaining, liquidComponents, composition,
          closure, state, compatible);
    }
    return result;
  }

  /**
   * Exports full input provenance, raw observations and signed inventory diagnostics with explicit units.
   *
   * @return JSON report; absent/unresolved compositions are JSON null, never zero or fabricated phase values
   */
  public String toJson() {
    JsonObject report = new JsonObject();
    report.addProperty("schemaVersion", "cvd-material-balance/1");
    report.addProperty("sampleReference", sampleReference);
    report.addProperty("experimentReference", experimentReference);
    report.addProperty("dataUseReference", dataUseReference);
    report.addProperty("protocol",
        "isothermal saturated-vapor reference; endpoint-composition vapor withdrawal; fixed cell");
    report.addProperty("temperatureK", temperatureK);
    report.addProperty("amountBasis", "mol per initial mol; liquid volume per initial saturated cell volume");
    report.addProperty("balanceTolerance", balanceTolerance);
    report.addProperty("minimumLiquidMoles", minimumLiquidMoles);
    report.addProperty("allCompatible", isCompatible());
    JsonArray names = new JsonArray();
    for (String name : componentNames) {
      names.add(name);
    }
    report.add("componentNames", names);
    JsonArray rows = new JsonArray();
    for (int k = 0; k < stages.length; k++) {
      Observation o = observations[k];
      Stage s = stages[k];
      JsonObject row = new JsonObject();
      row.addProperty("rowId", o.rowId);
      row.addProperty("pressurePa", o.pressurePa);
      row.addProperty("gasZ", o.gasZ);
      row.addProperty("liquidVolumeFraction", o.liquidVolumeFraction);
      row.addProperty("cumulativeProducedMoleFraction", o.cumulativeProduced);
      row.add("vaporMoleFractions", array(o.vaporComposition));
      row.addProperty("totalRemainingMoles", s.total);
      row.addProperty("vaporMoles", s.vapor);
      row.addProperty("liquidMoles", s.liquid);
      row.addProperty("stageWithdrawnMoles", s.withdrawal);
      row.add("producedComponentMoles", array(s.produced));
      row.add("remainingComponentMoles", array(s.remaining));
      row.add("liquidComponentMoles", array(s.liquidComponents));
      row.add("liquidMoleFractions", s.liquidComposition == null ? null : array(s.liquidComposition));
      row.addProperty("componentSumMinusLiquidMoles", s.closure);
      row.addProperty("remainingComponentSumMinusTotalMoles", sum(s.remaining) - s.total);
      row.addProperty("producedComponentSumMinusWithdrawnMoles", sum(s.produced) - o.cumulativeProduced);
      row.addProperty("vaporComponentSumMinusVaporMoles", s.vapor * sum(o.vaporComposition) - s.vapor);
      row.addProperty("liquidState", s.liquidState.name());
      row.addProperty("compatible", s.compatible);
      rows.add(row);
    }
    report.add("rows", rows);
    if (finalAnalysis != null) {
      JsonObject comparison = new JsonObject();
      comparison.addProperty("sourceReference", finalAnalysis.sourceReference);
      comparison.addProperty("boundsReference", finalAnalysis.boundsReference);
      comparison.add("measuredMoleFractions", array(finalAnalysis.composition));
      comparison.add("absoluteMoleFractionBounds", array(finalAnalysis.absoluteBounds));
      Stage last = stages[stages.length - 1];
      double[] residual = null;
      if (last.liquidComposition != null) {
        residual = new double[componentNames.length];
        for (int j = 0; j < residual.length; j++) {
          residual[j] = finalAnalysis.composition[j] - last.liquidComposition[j];
        }
      }
      comparison.add("measuredMinusReconstructed", residual == null ? null : array(residual));
      report.add("finalLiquidAnalysis", comparison);
    }
    return report.toString();
  }

  /**
   * Copies finite values into a JSON array.
   *
   * @param values primitive values
   * @return JSON array
   */
  private static JsonArray array(double[] values) {
    JsonArray result = new JsonArray();
    for (double value : values) {
      result.add(value);
    }
    return result;
  }

  /**
   * Sums an already frozen component vector without modifying its normalization.
   *
   * @param values component amounts or fractions
   * @return arithmetic sum on the vector's original basis
   */
  private static double sum(double[] values) {
    double result = 0;
    for (double value : values) {
      result += value;
    }
    return result;
  }

  /**
   * Validates fractions without normalizing or clipping.
   *
   * @param values fractions to validate
   * @param composition whether the vector must sum to one
   */
  private static void fractions(double[] values, boolean composition) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("Missing CVD fractions");
    }
    double sum = 0;
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0 || value > 1) {
        throw new IllegalArgumentException("CVD fractions must lie in [0,1]");
      }
      sum += value;
    }
    if (composition && Math.abs(sum - 1) > COMPOSITION_TOLERANCE) {
      throw new IllegalArgumentException("CVD composition must already sum to one; no automatic normalization");
    }
  }

  /**
   * Liquid composition availability, separate from the raw signed component inventories.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public enum LiquidState {
    /** Zero liquid volume and no resolved liquid inventory. */
    ABSENT,
    /** Positive liquid volume and amount above the caller's resolution floor. */
    RESOLVED,
    /** Positive liquid volume but insufficient liquid moles to resolve composition. */
    UNRESOLVED,
    /** Contradictory phase amounts or component inventory. */
    INCONSISTENT
  }

  /**
   * One immutable post-withdrawal observation with an exact source-row identity.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Observation implements Serializable {
    /** Serialization compatibility identifier. */
    private static final long serialVersionUID = 1L;
    /** Unique source-row identity within the complete experiment. */
    private final String rowId;
    /** Absolute post-withdrawal cell pressure in Pa. */
    private final double pressurePa;
    /** Equilibrium vapor compressibility factor. */
    private final double gasZ;
    /** Liquid volume per initial saturated cell volume. */
    private final double liquidVolumeFraction;
    /** Cumulative withdrawn moles per initial mol. */
    private final double cumulativeProduced;
    /** Equilibrium and withdrawn vapor mole fractions in declared component order. */
    private final double[] vaporComposition;

    /**
     * Freezes observed inputs without normalization.
     *
     * @param rowId unique source row identity
     * @param pressurePa absolute cell pressure in Pa
     * @param gasZ equilibrium vapor compressibility factor
     * @param liquidVolumeFraction liquid volume per initial saturated cell volume in [0,1)
     * @param cumulativeProduced cumulative withdrawn moles per initial mol in [0,1)
     * @param vaporComposition equilibrium/withdrawn vapor mole fractions in the declared component order
     */
    public Observation(String rowId, double pressurePa, double gasZ, double liquidVolumeFraction,
        double cumulativeProduced, double[] vaporComposition) {
      this.rowId = CalibratedPvtModel.reference(rowId);
      if (!Double.isFinite(pressurePa) || pressurePa <= 0 || !Double.isFinite(gasZ) || gasZ <= 0
          || !Double.isFinite(liquidVolumeFraction) || liquidVolumeFraction < 0 || liquidVolumeFraction >= 1
          || !Double.isFinite(cumulativeProduced) || cumulativeProduced < 0 || cumulativeProduced >= 1) {
        throw new IllegalArgumentException("CVD requires finite positive p/Z and volume/production fractions in [0,1)");
      }
      fractions(vaporComposition, true);
      this.pressurePa = pressurePa;
      this.gasZ = gasZ;
      this.liquidVolumeFraction = liquidVolumeFraction;
      this.cumulativeProduced = cumulativeProduced;
      this.vaporComposition = vaporComposition.clone();
    }
  }

  /**
   * Separately attributed final liquid analysis and explicit absolute comparison bounds.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class FinalLiquidAnalysis implements Serializable {
    /** Serialization compatibility identifier. */
    private static final long serialVersionUID = 1L;
    /** Final liquid analysis source and sample/protocol identity. */
    private final String sourceReference;
    /** Interpretation and justification of absolute comparison bounds. */
    private final String boundsReference;
    /** Final measured liquid mole fractions, without normalization. */
    private final double[] composition;
    /** Absolute bounds on measured-minus-reconstructed mole-fraction differences. */
    private final double[] absoluteBounds;

    /**
     * Freezes an analysis; bounds cover the entire observed-minus-reconstructed difference, not just printing.
     *
     * @param sourceReference independent analysis source and sample/protocol identity
     * @param boundsReference justification/interpretation of all supplied comparison bounds
     * @param composition measured final liquid mole fractions
     * @param absoluteBounds nonnegative absolute mole-fraction comparison bounds in the same order
     */
    public FinalLiquidAnalysis(String sourceReference, String boundsReference, double[] composition,
        double[] absoluteBounds) {
      this.sourceReference = CalibratedPvtModel.reference(sourceReference);
      this.boundsReference = CalibratedPvtModel.reference(boundsReference);
      fractions(composition, true);
      fractions(absoluteBounds, false);
      if (absoluteBounds.length != composition.length) {
        throw new IllegalArgumentException("Final liquid analysis and comparison bounds must align");
      }
      this.composition = composition.clone();
      this.absoluteBounds = absoluteBounds.clone();
    }
  }

  /**
   * Immutable signed accounting result; all amounts are mol per initial mol.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Stage implements Serializable {
    /** Serialization compatibility identifier. */
    private static final long serialVersionUID = 1L;
    /** Total retained moles per initial mol. */
    private final double total;
    /** Retained vapor moles per initial mol. */
    private final double vapor;
    /** Signed inferred liquid moles per initial mol. */
    private final double liquid;
    /** Current stage withdrawal in mol per initial mol. */
    private final double withdrawal;
    /** Cumulative produced named component moles per initial mol. */
    private final double[] produced;
    /** Retained named component moles per initial mol. */
    private final double[] remaining;
    /** Signed inferred liquid component moles per initial mol. */
    private final double[] liquidComponents;
    /** Resolved liquid mole fractions, or null when unavailable. */
    private final double[] liquidComposition;
    /** Component sum minus liquid amount, mol per initial mol. */
    private final double closure;
    /** Physical availability and resolution status of liquid composition. */
    private final LiquidState liquidState;
    /** Whether this stage satisfies the stated accounting and phase limits. */
    private final boolean compatible;

    /**
     * Copies one complete result.
     *
     * @param total total remaining amount
     * @param vapor vapor amount
     * @param liquid signed inferred liquid amount
     * @param withdrawal current stage withdrawal
     * @param produced cumulative produced components
     * @param remaining retained components
     * @param liquidComponents signed inferred liquid components
     * @param liquidComposition resolved fractions or null
     * @param closure component-sum minus liquid amount
     * @param liquidState liquid resolution state
     * @param compatible physical accounting flag
     */
    private Stage(double total, double vapor, double liquid, double withdrawal, double[] produced, double[] remaining,
        double[] liquidComponents, double[] liquidComposition, double closure, LiquidState liquidState,
        boolean compatible) {
      this.total = total;
      this.vapor = vapor;
      this.liquid = liquid;
      this.withdrawal = withdrawal;
      this.produced = produced.clone();
      this.remaining = remaining.clone();
      this.liquidComponents = liquidComponents.clone();
      this.liquidComposition = liquidComposition == null ? null : liquidComposition.clone();
      this.closure = closure;
      this.liquidState = liquidState;
      this.compatible = compatible;
    }

    /**
     * Returns the signed liquid amount, including failed diagnostics.
     *
     * @return mol per initial mol
     */
    public double getLiquidMoles() {
      return liquid;
    }

    /**
     * Returns total retained amount.
     *
     * @return mol per initial mol
     */
    public double getTotalRemainingMoles() {
      return total;
    }

    /**
     * Returns retained vapor amount.
     *
     * @return mol per initial mol
     */
    public double getVaporMoles() {
      return vapor;
    }

    /**
     * Returns this stage's withdrawal, excluding previous withdrawals.
     *
     * @return mol per initial mol
     */
    public double getStageWithdrawnMoles() {
      return withdrawal;
    }

    /**
     * Returns cumulative named produced component amounts.
     *
     * @return defensive copy, mol per initial mol
     */
    public double[] getProducedComponentMoles() {
      return produced.clone();
    }

    /**
     * Returns retained named component amounts, including failed signed diagnostics.
     *
     * @return defensive copy, mol per initial mol
     */
    public double[] getRemainingComponentMoles() {
      return remaining.clone();
    }

    /**
     * Returns signed inferred liquid component amounts without clipping.
     *
     * @return defensive copy in declared component order, mol per initial mol
     */
    public double[] getLiquidComponentMoles() {
      return liquidComponents.clone();
    }

    /**
     * Returns composition only when the liquid amount is resolved and accounting is compatible.
     *
     * @return defensive copy of liquid mole fractions
     * @throws IllegalStateException if the liquid is absent, unresolved or inconsistent
     */
    public double[] getLiquidMoleFractions() {
      if (liquidState != LiquidState.RESOLVED) {
        throw new IllegalStateException("Liquid composition unavailable: " + liquidState);
      }
      return liquidComposition.clone();
    }

    /**
     * Returns the liquid resolution status.
     *
     * @return status
     */
    public LiquidState getLiquidState() {
      return liquidState;
    }
  }
}
