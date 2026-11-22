package neqsim.pvtsimulation.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Opt-in, model-independent consistency gate for reported gas-phase PVT measurements.
 *
 * <p>
 * Checks rho = p M / (Z R T) using positive, closed input intervals. All five quantities must describe the same gas
 * phase at the same equilibrium state. Pressure is absolute Pa, temperature K, molar mass kg/mol and density kg/m3.
 * This is not a flash, a regression, a measurement-accuracy test or a substitute for protocol qualification. The caller
 * identifies the sample, complete experiment, state, source and meaning of the supplied bounds. Bounds may represent
 * printing precision or justified measurement limits; they are not automatically interpreted as standard deviations or
 * confidence intervals. Interval propagation permits every combination of inputs and is therefore conservative when
 * inputs are correlated. No ordinary simulation path calls this gate.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PvtGasDensityConsistency implements Serializable {
  private static final long serialVersionUID = 1L;
  /** SI molar gas constant, J/(mol K), from the exact SI defining constants. */
  public static final double GAS_CONSTANT = 8.31446261815324;
  private final String sampleReference;
  private final String boundsReference;
  private final String dataUseReference;
  private final Observation[] observations;

  /**
   * Freezes a complete caller-selected set of gas-density checks, including incompatible rows.
   *
   * @param sampleReference sample/composition revision identity
   * @param boundsReference source and interpretation of all supplied bounds
   * @param dataUseReference public source/access and data-use provenance
   * @param observations nonempty same-sample observations with unique row identities
   */
  public PvtGasDensityConsistency(String sampleReference, String boundsReference, String dataUseReference,
      Observation[] observations) {
    this.sampleReference = CalibratedPvtModel.reference(sampleReference);
    this.boundsReference = CalibratedPvtModel.reference(boundsReference);
    this.dataUseReference = CalibratedPvtModel.reference(dataUseReference);
    if (observations == null || observations.length == 0) {
      throw new IllegalArgumentException("At least one gas-phase observation is required");
    }
    this.observations = observations.clone();
    Set<String> ids = new HashSet<String>();
    for (Observation observation : this.observations) {
      if (observation == null || !ids.add(observation.rowId)) {
        throw new IllegalArgumentException("Null or duplicate gas-density observation");
      }
    }
  }

  /**
   * Returns the sample identity to bind the evidence to its EOS checkpoint bundle.
   *
   * @return sample reference
   */
  public String getSampleReference() {
    return sampleReference;
  }

  /**
   * Tests all rows without deleting incompatible observations.
   *
   * @return true only when every density interval overlaps its physically implied interval
   */
  public boolean isCompatible() {
    for (Observation observation : observations) {
      if (!observation.isCompatible()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Stops an opt-in handoff if any row is internally incompatible.
   *
   * @throws IllegalStateException if one or more supplied rows fail the identity check
   */
  public void requireCompatible() {
    StringBuilder failed = new StringBuilder();
    for (Observation observation : observations) {
      if (!observation.isCompatible()) {
        if (failed.length() > 0) {
          failed.append(", ");
        }
        failed.append(observation.rowId);
      }
    }
    if (failed.length() > 0) {
      throw new IllegalStateException("Incompatible gas-density observations: " + failed);
    }
  }

  /**
   * Exports immutable, source-identified measurements and diagnostics in explicit SI units.
   *
   * @return JSON evidence; compatibility is necessary internal consistency, not measured accuracy
   */
  public String toJson() {
    JsonObject result = new JsonObject();
    result.addProperty("schemaVersion", "gas-density-consistency/1");
    result.addProperty("sampleReference", sampleReference);
    result.addProperty("boundsReference", boundsReference);
    result.addProperty("dataUseReference", dataUseReference);
    result.addProperty("identity", "densityKgPerM3 = pressurePa * molarMassKgPerMol / (gasZ * R * temperatureK)");
    result.addProperty("gasConstantJPerMolK", GAS_CONSTANT);
    result.addProperty("allCompatible", isCompatible());
    JsonArray rows = new JsonArray();
    for (Observation observation : observations) {
      rows.add(observation.toJson());
    }
    result.add("rows", rows);
    return result.toString();
  }

  /**
   * Positive finite central value with a symmetric absolute bound in the quantity's SI units.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Quantity implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double value;
    private final double bound;

    /**
     * Defines a positive closed interval; a zero bound represents a point value.
     *
     * @param value central value in the field's documented units
     * @param bound nonnegative absolute half-width, not an inferred sigma
     */
    public Quantity(double value, double bound) {
      if (!Double.isFinite(value) || !Double.isFinite(bound) || bound < 0.0 || value - bound <= 0.0
          || !Double.isFinite(value + bound) || (bound > 0.0 && !Double.isFinite(Math.nextUp(value + bound)))) {
        throw new IllegalArgumentException("Quantity requires finite positive endpoints and nonnegative bound");
      }
      this.value = value;
      this.bound = bound;
    }

    /**
     * Returns the nominal observed value.
     *
     * @return central value
     */
    public double getValue() {
      return value;
    }

    /**
     * Returns the lower endpoint, rounded outward when the bound is nonzero.
     *
     * @return positive lower endpoint
     */
    public double getLower() {
      return bound == 0.0 ? value : Math.max(Double.MIN_VALUE, Math.nextDown(value - bound));
    }

    /**
     * Returns the upper endpoint, rounded outward when the bound is nonzero.
     *
     * @return upper endpoint
     */
    public double getUpper() {
      return bound == 0.0 ? value : Math.nextUp(value + bound);
    }

    /**
     * Exports the unchanged nominal value and declared bound.
     *
     * @return quantity JSON
     */
    private JsonObject toJson() {
      JsonObject result = new JsonObject();
      result.addProperty("value", value);
      result.addProperty("absoluteBound", bound);
      return result;
    }
  }

  /**
   * One attributed gas-phase tuple and its conservatively propagated density interval.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Observation implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String rowId;
    private final String experimentId;
    private final String stateReference;
    private final String sourceReference;
    private final Quantity pressurePa;
    private final Quantity temperatureK;
    private final Quantity gasZ;
    private final Quantity molarMassKgPerMol;
    private final Quantity densityKgPerM3;
    private final double impliedLower;
    private final double impliedUpper;
    private final double impliedCentral;

    /**
     * Checks one tuple; bulk two-phase density or a different sampled phase is not admissible.
     *
     * @param rowId unique observation identity, not used to split calibration/holdout experiments
     * @param experimentId complete experiment identity
     * @param stateReference gas-phase equilibrium state and protocol/sample location
     * @param sourceReference source revision, page/table/row and any transcription/conversion record
     * @param pressurePa absolute pressure in Pa
     * @param temperatureK temperature in K
     * @param gasZ dimensionless gas-phase compressibility factor on the same molar-volume basis
     * @param molarMassKgPerMol gas-phase molar mass in kg/mol
     * @param densityKgPerM3 gas-phase mass density in kg/m3
     */
    public Observation(String rowId, String experimentId, String stateReference, String sourceReference,
        Quantity pressurePa, Quantity temperatureK, Quantity gasZ, Quantity molarMassKgPerMol,
        Quantity densityKgPerM3) {
      this.rowId = CalibratedPvtModel.reference(rowId);
      this.experimentId = CalibratedPvtModel.reference(experimentId);
      this.stateReference = CalibratedPvtModel.reference(stateReference);
      this.sourceReference = CalibratedPvtModel.reference(sourceReference);
      if (pressurePa == null || temperatureK == null || gasZ == null || molarMassKgPerMol == null
          || densityKgPerM3 == null) {
        throw new IllegalArgumentException("All five gas-phase quantities are required");
      }
      this.pressurePa = pressurePa;
      this.temperatureK = temperatureK;
      this.gasZ = gasZ;
      this.molarMassKgPerMol = molarMassKgPerMol;
      this.densityKgPerM3 = densityKgPerM3;
      impliedCentral = pressurePa.value * molarMassKgPerMol.value / gasZ.value / GAS_CONSTANT / temperatureK.value;
      impliedLower = endpoint(false);
      impliedUpper = endpoint(true);
      if (!Double.isFinite(impliedCentral) || impliedCentral <= 0.0 || impliedLower <= 0.0
          || !Double.isFinite(impliedUpper)) {
        throw new IllegalArgumentException("Gas-density interval cannot be represented as positive finite doubles");
      }
    }

    /**
     * Propagates monotone endpoints with outward rounding after each operation.
     *
     * @param upper true for the upper endpoint
     * @return conservative implied density endpoint
     */
    private double endpoint(boolean upper) {
      double p = upper ? pressurePa.getUpper() : pressurePa.getLower();
      double m = upper ? molarMassKgPerMol.getUpper() : molarMassKgPerMol.getLower();
      double z = upper ? gasZ.getLower() : gasZ.getUpper();
      double t = upper ? temperatureK.getLower() : temperatureK.getUpper();
      double value = round(p * m, upper);
      value = round(value / z, upper);
      value = round(value / GAS_CONSTANT, upper);
      return round(value / t, upper);
    }

    /**
     * Rounds a finite arithmetic result outward; unrepresentable results are rejected by the constructor.
     *
     * @param value result of one arithmetic operation
     * @param upper direction of the enclosing bound
     * @return outward-rounded result
     */
    private static double round(double value, boolean upper) {
      return upper ? Math.nextUp(value) : Math.nextDown(value);
    }

    /**
     * Returns density implied by the unchanged nominal p, T, Z and molar mass.
     *
     * @return kg/m3
     */
    public double getImpliedDensityKgPerM3() {
      return impliedCentral;
    }

    /**
     * Returns the conservative lower physical bound.
     *
     * @return kg/m3
     */
    public double getImpliedLowerKgPerM3() {
      return impliedLower;
    }

    /**
     * Returns the conservative upper physical bound.
     *
     * @return kg/m3
     */
    public double getImpliedUpperKgPerM3() {
      return impliedUpper;
    }

    /**
     * Tests closed-interval overlap, including a shared endpoint.
     *
     * @return internal compatibility under the declared bounds
     */
    public boolean isCompatible() {
      return densityKgPerM3.getLower() <= impliedUpper && densityKgPerM3.getUpper() >= impliedLower;
    }

    /**
     * Exports nominal measurements, bounds and diagnostics without replacing any observation.
     *
     * @return complete row evidence
     */
    private JsonObject toJson() {
      JsonObject row = new JsonObject();
      row.addProperty("rowId", rowId);
      row.addProperty("experimentId", experimentId);
      row.addProperty("stateReference", stateReference);
      row.addProperty("sourceReference", sourceReference);
      row.add("pressurePa", pressurePa.toJson());
      row.add("temperatureK", temperatureK.toJson());
      row.add("gasZ", gasZ.toJson());
      row.add("molarMassKgPerMol", molarMassKgPerMol.toJson());
      row.add("densityKgPerM3", densityKgPerM3.toJson());
      row.addProperty("impliedDensityKgPerM3", impliedCentral);
      row.addProperty("impliedLowerKgPerM3", impliedLower);
      row.addProperty("impliedUpperKgPerM3", impliedUpper);
      row.addProperty("observedMinusImpliedKgPerM3", densityKgPerM3.value - impliedCentral);
      row.addProperty("compatible", isCompatible());
      return row;
    }
  }
}
