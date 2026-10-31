package neqsim.pvtsimulation.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Serializable;
import java.util.Arrays;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable original/tuned EOS checkpoints and independently identified laboratory calibration evidence.
 *
 * <p>
 * This class does not fit an EOS or accept reservoir history as laboratory data. It reuses {@link PVTFluidStudy} to
 * compare both checkpoints with complete held-out laboratory experiments. Experiment identities must identify whole
 * experiments, not individual rows of a depletion path. Identity disjointness is checked; the caller remains
 * responsible for actual laboratory provenance and independence. Supported fluid models and standard conditions are
 * those of PVTFluidStudy. Native checkpoints require a compatible NeqSim/software version.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CalibratedPvtModel implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_URI = "https://raw.githubusercontent.com/equinor/neqsim/master/src/main/resources/neqsim/pvt/calibrated-pvt-evidence-v1.schema.json";
  private final byte[] original;
  private final byte[] tuned;
  private final String sampleReference;
  private final String originalRevision;
  private final String tunedRevision;
  private final String calibrationReference;
  private final String[] calibrationExperiments;
  private final String gasDensityConsistency;

  /**
   * Freezes independently calibrated fluid versions and their evidence references.
   *
   * @param original original characterized sample model
   * @param tuned model calibrated against laboratory data only
   * @param sampleReference sample/composition/characterization revision reference
   * @param originalRevision original model/software revision
   * @param tunedRevision accepted tuned model/software revision
   * @param calibrationReference laboratory protocols, separator basis and fitting report reference
   * @param calibrationExperiments complete experiment identities used in tuning
   */
  public CalibratedPvtModel(SystemInterface original, SystemInterface tuned, String sampleReference,
      String originalRevision, String tunedRevision, String calibrationReference, String[] calibrationExperiments) {
    this(original, tuned, sampleReference, originalRevision, tunedRevision, calibrationReference,
        calibrationExperiments, null);
  }

  /**
   * Freezes model versions after an optional, source-identified gas-density consistency gate.
   *
   * <p>
   * Non-null evidence must match the sample identity and every supplied row must pass. Its complete JSON is frozen in
   * the checkpoint provenance and therefore also in held-out comparisons. This gate does not establish measurement
   * accuracy, laboratory independence or complete experiment qualification. A null value preserves the legacy
   * constructor's behavior and makes no consistency claim. Never omit failing rows merely to pass this gate.
   * </p>
   *
   * @param original original characterized sample model
   * @param tuned model calibrated against laboratory data only
   * @param sampleReference sample/composition/characterization revision reference
   * @param originalRevision original model/software revision
   * @param tunedRevision accepted tuned model/software revision
   * @param calibrationReference laboratory protocols, separator basis and fitting report reference
   * @param calibrationExperiments complete experiment identities used in tuning
   * @param gasDensityConsistency optional same-sample gas-phase measurement consistency evidence
   */
  public CalibratedPvtModel(SystemInterface original, SystemInterface tuned, String sampleReference,
      String originalRevision, String tunedRevision, String calibrationReference, String[] calibrationExperiments,
      PvtGasDensityConsistency gasDensityConsistency) {
    this.sampleReference = reference(sampleReference);
    if (gasDensityConsistency != null) {
      if (!this.sampleReference.equals(gasDensityConsistency.getSampleReference())) {
        throw new IllegalArgumentException("Gas-density evidence belongs to a different sample");
      }
      gasDensityConsistency.requireCompatible();
    }
    this.gasDensityConsistency = gasDensityConsistency == null ? null : gasDensityConsistency.toJson();
    this.originalRevision = reference(originalRevision);
    this.tunedRevision = reference(tunedRevision);
    this.calibrationReference = reference(calibrationReference);
    if (original == null || tuned == null || calibrationExperiments == null || calibrationExperiments.length == 0) {
      throw new IllegalArgumentException("Both fluids and calibration experiment identities are required");
    }
    this.calibrationExperiments = calibrationExperiments.clone();
    for (int i = 0; i < this.calibrationExperiments.length; i++) {
      this.calibrationExperiments[i] = reference(this.calibrationExperiments[i]);
      for (int j = 0; j < i; j++) {
        if (this.calibrationExperiments[i].equals(this.calibrationExperiments[j])) {
          throw new IllegalArgumentException("Duplicate calibration experiment identity");
        }
      }
    }
    // Serialize first: even metadata inspection must not mutate either supplied model.
    this.original = SerializationUtils.serialize(original);
    this.tuned = SerializationUtils.serialize(tuned);
    PVTFluidStudy.capture(getOriginalFluid());
    PVTFluidStudy.capture(getTunedFluid());
  }

  /**
   * Restores an independent copy of the original model, including EOS parameters.
   *
   * @return original fluid copy
   */
  public SystemInterface getOriginalFluid() {
    return SerializationUtils.deserialize(original);
  }

  /**
   * Restores an independent copy of the accepted tuned model.
   *
   * @return tuned fluid copy
   */
  public SystemInterface getTunedFluid() {
    return SerializationUtils.deserialize(tuned);
  }

  /**
   * Returns immutable provenance as JSON, without mutable fluid references.
   *
   * @return sample/model/calibration identities and calibration experiment list
   */
  public String getProvenance() {
    JsonObject result = new JsonObject();
    result.addProperty("schemaVersion", "1.0");
    result.addProperty("schemaUri", SCHEMA_URI);
    result.addProperty("sampleReference", sampleReference);
    result.addProperty("originalRevision", originalRevision);
    result.addProperty("tunedRevision", tunedRevision);
    result.addProperty("calibrationReference", calibrationReference);
    JsonArray identities = new JsonArray();
    for (String identity : calibrationExperiments) {
      identities.add(identity);
    }
    result.add("calibrationExperiments", identities);
    if (gasDensityConsistency != null) {
      result.add("gasDensityConsistency", JsonParser.parseString(gasDensityConsistency));
    }
    return result.toString();
  }

  /**
   * Replays a complete held-out CCE, CVD or DLE protocol for both models.
   *
   * <p>
   * Every observation is aligned to the full ordered pressure schedule, including its initial point. The property name
   * and units are the normalized PVTFluidStudy JSON keys. Uncertainty is a supplied positive one-standard-deviation
   * measurement scale, not an inferred confidence interval. No automatic calibration acceptance threshold is imposed.
   * </p>
   *
   * @param experimentId whole held-out experiment identity, absent from calibration
   * @param kind CCE, CVD or DLE (case sensitive)
   * @param temperatureK laboratory temperature in K
   * @param pressuresBara complete decreasing laboratory pressure schedule in bara
   * @param property normalized property key, for example boM3PerSm3 for DLE
   * @param measured measured values in that property's units, one per pressure
   * @param uncertainty positive measurement standard deviations in the same units
   * @return JSON predictions, observed-minus-predicted residuals and standardized residuals
   */
  public String compareHoldout(String experimentId, String kind, double temperatureK, double[] pressuresBara,
      String property, double[] measured, double[] uncertainty) {
    requireHoldout(experimentId);
    String allowed = "CCE".equals(kind)
        ? "relativeVolume liquidRelativeVolume phaseZeroDensityKgPerM3 oilDensityKgPerM3 gasZ yFactor"
        : "CVD".equals(kind) ? "relativeVolume liquidRelativeVolume gasZ mixtureZ cumulativeMolePercentDepleted"
            : "DLE".equals(kind) ? "boM3PerSm3 rsSm3PerSm3 bgM3PerSm3 oilDensityKgPerM3 gasZ" : "";
    if (property == null || !Arrays.asList(allowed.split(" ")).contains(property) || allowed.isEmpty()) {
      throw new IllegalArgumentException("Unsupported held-out protocol/property");
    }
    if (pressuresBara == null || measured == null || measured.length != pressuresBara.length) {
      throw new IllegalArgumentException("Measurements must cover the complete pressure schedule");
    }
    requireMeasurements(measured, uncertainty);
    JsonObject originalResult = JsonParser
        .parseString(PVTFluidStudy.simulate(getOriginalFluid(), kind, temperatureK, pressuresBara)).getAsJsonObject();
    JsonObject tunedResult = JsonParser
        .parseString(PVTFluidStudy.simulate(getTunedFluid(), kind, temperatureK, pressuresBara)).getAsJsonObject();
    JsonObject result = comparison(experimentId, property, measured, uncertainty,
        originalResult.getAsJsonObject("values").getAsJsonArray(property),
        tunedResult.getAsJsonObject("values").getAsJsonArray(property));
    result.addProperty("kind", kind);
    result.addProperty("temperatureK", temperatureK);
    result.add("pressuresBara", originalResult.get("pressuresBara"));
    return result.toString();
  }

  /**
   * Replays a complete held-out separator/stock-tank train on the 15 C, 1.01325 bara standard basis.
   *
   * @param experimentId whole held-out experiment identity
   * @param reservoirTemperatureK initial reservoir temperature in K
   * @param reservoirPressureBara initial reservoir pressure in bara
   * @param stagePressuresBara decreasing separator pressures ending at 1.01325 bara
   * @param stageTemperaturesK stage temperatures ending at 288.15 K
   * @param property totalGORSm3PerSm3, boM3PerSm3 or stockTankOilDensityKgPerM3
   * @param measured laboratory value in the property units
   * @param uncertainty positive measurement standard deviation in those units
   * @return JSON predictions and residuals with the complete declared separator path
   */
  public String compareSeparatorHoldout(String experimentId, double reservoirTemperatureK, double reservoirPressureBara,
      double[] stagePressuresBara, double[] stageTemperaturesK, String property, double measured, double uncertainty) {
    requireHoldout(experimentId);
    if (!Arrays.asList("totalGORSm3PerSm3", "boM3PerSm3", "stockTankOilDensityKgPerM3").contains(property)) {
      throw new IllegalArgumentException("Unsupported separator property");
    }
    requireMeasurements(new double[] {measured}, new double[] {uncertainty});
    MaterialBalancePvtHistory.requireSeparatorBasis(stagePressuresBara, stageTemperaturesK);
    if (!Double.isFinite(reservoirPressureBara) || reservoirPressureBara < stagePressuresBara[0]) {
      throw new IllegalArgumentException("Reservoir pressure must be at least the first separator pressure");
    }
    JsonObject a = JsonParser.parseString(PVTFluidStudy.simulateSeparator(getOriginalFluid(), reservoirTemperatureK,
        reservoirPressureBara, stagePressuresBara, stageTemperaturesK)).getAsJsonObject();
    JsonObject b = JsonParser.parseString(PVTFluidStudy.simulateSeparator(getTunedFluid(), reservoirTemperatureK,
        reservoirPressureBara, stagePressuresBara, stageTemperaturesK)).getAsJsonObject();
    JsonArray originalValues = new JsonArray();
    originalValues.add(a.get(property));
    JsonArray tunedValues = new JsonArray();
    tunedValues.add(b.get(property));
    JsonObject result = comparison(experimentId, property, new double[] {measured}, new double[] {uncertainty},
        originalValues, tunedValues);
    result.addProperty("kind", "SEPARATOR");
    result.addProperty("reservoirTemperatureK", reservoirTemperatureK);
    result.addProperty("reservoirPressureBara", reservoirPressureBara);
    JsonArray stages = new JsonArray();
    for (int i = 0; i < stagePressuresBara.length; i++) {
      JsonObject stage = new JsonObject();
      stage.addProperty("pressureBara", stagePressuresBara[i]);
      stage.addProperty("temperatureK", stageTemperaturesK[i]);
      stages.add(stage);
    }
    result.add("separatorStages", stages);
    return result.toString();
  }

  /**
   * Builds finite residual evidence without fitting either model.
   *
   * @param id held-out experiment identity
   * @param property normalized property name
   * @param measured laboratory observations
   * @param uncertainty supplied standard deviations
   * @param a original predictions
   * @param b tuned predictions
   * @return comparison evidence
   */
  private JsonObject comparison(String id, String property, double[] measured, double[] uncertainty, JsonArray a,
      JsonArray b) {
    if (a == null || b == null || a.size() != measured.length || b.size() != measured.length) {
      throw new IllegalStateException("PVT protocol returned an incomplete property history");
    }
    JsonObject result = new JsonObject();
    result.add("provenance", JsonParser.parseString(getProvenance()));
    result.addProperty("schemaVersion", "1.0");
    result.addProperty("schemaUri", SCHEMA_URI);
    result.addProperty("experimentId", reference(id));
    result.addProperty("property", property);
    result.addProperty("residualDefinition", "measured minus predicted; standardized by supplied measurement sigma");
    JsonArray rows = new JsonArray();
    for (int i = 0; i < measured.length; i++) {
      double originalValue = a.get(i).getAsDouble();
      double tunedValue = b.get(i).getAsDouble();
      double originalResidual = measured[i] - originalValue;
      double tunedResidual = measured[i] - tunedValue;
      double originalStandardized = originalResidual / uncertainty[i];
      double tunedStandardized = tunedResidual / uncertainty[i];
      if (!Double.isFinite(originalValue) || !Double.isFinite(tunedValue) || !Double.isFinite(originalStandardized)
          || !Double.isFinite(tunedStandardized)) {
        throw new IllegalStateException("Unavailable/non-finite PVT prediction or residual at row " + i);
      }
      JsonObject row = new JsonObject();
      row.addProperty("measured", measured[i]);
      row.addProperty("sigma", uncertainty[i]);
      row.addProperty("originalPrediction", originalValue);
      row.addProperty("tunedPrediction", tunedValue);
      row.addProperty("originalResidual", originalResidual);
      row.addProperty("tunedResidual", tunedResidual);
      row.addProperty("originalStandardizedResidual", originalStandardized);
      row.addProperty("tunedStandardizedResidual", tunedStandardized);
      rows.add(row);
    }
    result.add("rows", rows);
    return result;
  }

  /**
   * Rejects reuse of a calibration experiment as independent validation.
   *
   * @param id whole experiment identity
   */
  private void requireHoldout(String id) {
    if (Arrays.asList(calibrationExperiments).contains(reference(id))) {
      throw new IllegalArgumentException("Held-out experiment was used for calibration");
    }
  }

  /**
   * Checks finite measured values and strictly positive measurement scales.
   *
   * @param measured observations
   * @param uncertainty measurement standard deviations
   */
  private static void requireMeasurements(double[] measured, double[] uncertainty) {
    if (measured == null || measured.length == 0 || uncertainty == null || uncertainty.length != measured.length) {
      throw new IllegalArgumentException("Every measurement requires a positive standard deviation");
    }
    for (int i = 0; i < measured.length; i++) {
      if (!Double.isFinite(measured[i]) || !Double.isFinite(uncertainty[i]) || uncertainty[i] <= 0.0) {
        throw new IllegalArgumentException("Measurements must be finite with positive finite uncertainty");
      }
    }
  }

  /**
   * Normalizes a required evidence identity.
   *
   * @param value caller-supplied reference
   * @return nonempty trimmed reference
   */
  static String reference(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Nonempty provenance reference is required");
    }
    return value.trim();
  }
}
