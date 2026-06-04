package neqsim.process.equipment.watertreatment;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * First-order inventory model for chemical accumulation and lag after demulsifier setpoint changes.
 *
 * <p>
 * The model treats the separator or upstream produced-water volume as a well-mixed hold-up. A dose
 * setpoint enters with the water flow, chemical leaves with the water flow, and optional
 * first-order deactivation represents adsorption, degradation, or loss to oil. The outlet
 * concentration is the effective dose seen by the separation process.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemicalDoseLagModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Well-mixed liquid hold-up volume in m3. */
  private double holdUpVolumeM3 = 100.0;

  /** Produced-water density in kg/m3. */
  private double waterDensityKgm3 = 1000.0;

  /** First-order chemical deactivation half-life in hours. */
  private double decayHalfLifeHours = Double.POSITIVE_INFINITY;

  /** Current chemical inventory in the hold-up volume in kg. */
  private double chemicalMassKg = 0.0;

  /** Last calculated effective outlet dose in ppm. */
  private double effectiveDosePpm = 0.0;

  /** Last calculated inlet chemical mass flow in kg/h. */
  private double lastInputMassFlowKgH = 0.0;

  /** Last calculated outlet chemical mass flow in kg/h. */
  private double lastOutletMassFlowKgH = 0.0;

  /** Last time step used in hours. */
  private double lastTimeStepHours = 0.0;

  /**
   * Creates a lag model with default produced-water hold-up assumptions.
   */
  public ChemicalDoseLagModel() {}

  /**
   * Advances the chemical inventory over one time step.
   *
   * @param setpointDosePpm injected chemical dose in ppm by produced-water mass
   * @param waterRateM3h produced-water flow rate in m3/h
   * @param timeStepHours time step in hours
   * @return effective outlet dose in ppm
   */
  public double step(double setpointDosePpm, double waterRateM3h, double timeStepHours) {
    if (waterRateM3h <= 0.0 || timeStepHours <= 0.0) {
      return effectiveDosePpm;
    }
    double safeDose = Math.max(0.0, setpointDosePpm);
    double waterMassFlowKgH = waterRateM3h * waterDensityKgm3;
    lastInputMassFlowKgH = safeDose * waterMassFlowKgH / 1.0e6;
    lastTimeStepHours = timeStepHours;

    double outletRate = calculateOutletRate(waterRateM3h);
    double decayRate = calculateDecayRate();
    double totalRate = outletRate + decayRate;

    if (totalRate <= 0.0) {
      chemicalMassKg += lastInputMassFlowKgH * timeStepHours;
      lastOutletMassFlowKgH = 0.0;
      effectiveDosePpm = 0.0;
      return effectiveDosePpm;
    }

    double steadyMassKg = lastInputMassFlowKgH / totalRate;
    double decayFactor = Math.exp(-totalRate * timeStepHours);
    chemicalMassKg = steadyMassKg + (chemicalMassKg - steadyMassKg) * decayFactor;
    chemicalMassKg = Math.max(0.0, chemicalMassKg);
    lastOutletMassFlowKgH = chemicalMassKg * outletRate;
    effectiveDosePpm = lastOutletMassFlowKgH * 1.0e6 / waterMassFlowKgH;
    return effectiveDosePpm;
  }

  /**
   * Resets the model to steady state at a specified setpoint and water rate.
   *
   * @param setpointDosePpm injected chemical dose in ppm
   * @param waterRateM3h produced-water flow rate in m3/h
   */
  public void resetToSteadyState(double setpointDosePpm, double waterRateM3h) {
    if (waterRateM3h <= 0.0) {
      chemicalMassKg = 0.0;
      effectiveDosePpm = 0.0;
      return;
    }
    double outletRate = calculateOutletRate(waterRateM3h);
    double totalRate = outletRate + calculateDecayRate();
    double waterMassFlowKgH = waterRateM3h * waterDensityKgm3;
    double inputMassFlowKgH = Math.max(0.0, setpointDosePpm) * waterMassFlowKgH / 1.0e6;
    chemicalMassKg = totalRate > 0.0 ? inputMassFlowKgH / totalRate : 0.0;
    lastOutletMassFlowKgH = chemicalMassKg * outletRate;
    effectiveDosePpm =
        waterMassFlowKgH > 0.0 ? lastOutletMassFlowKgH * 1.0e6 / waterMassFlowKgH : 0.0;
  }

  /**
   * Returns a copy suitable for non-mutating scenario calculations.
   *
   * @return copied model
   */
  public ChemicalDoseLagModel copy() {
    ChemicalDoseLagModel copy = new ChemicalDoseLagModel();
    copy.holdUpVolumeM3 = holdUpVolumeM3;
    copy.waterDensityKgm3 = waterDensityKgm3;
    copy.decayHalfLifeHours = decayHalfLifeHours;
    copy.chemicalMassKg = chemicalMassKg;
    copy.effectiveDosePpm = effectiveDosePpm;
    copy.lastInputMassFlowKgH = lastInputMassFlowKgH;
    copy.lastOutletMassFlowKgH = lastOutletMassFlowKgH;
    copy.lastTimeStepHours = lastTimeStepHours;
    return copy;
  }

  /**
   * Gets the nominal hydraulic residence time.
   *
   * @param waterRateM3h produced-water flow rate in m3/h
   * @return residence time in hours
   */
  public double getResidenceTimeHours(double waterRateM3h) {
    if (waterRateM3h <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return holdUpVolumeM3 / waterRateM3h;
  }

  /**
   * Serializes the lag model state to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("holdUpVolumeM3", holdUpVolumeM3);
    data.put("waterDensityKgm3", waterDensityKgm3);
    data.put("decayHalfLifeHours", decayHalfLifeHours);
    data.put("chemicalMassKg", chemicalMassKg);
    data.put("effectiveDosePpm", effectiveDosePpm);
    data.put("lastInputMassFlowKgH", lastInputMassFlowKgH);
    data.put("lastOutletMassFlowKgH", lastOutletMassFlowKgH);
    data.put("lastTimeStepHours", lastTimeStepHours);
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(data);
  }

  /**
   * Calculates the outlet exchange rate.
   *
   * @param waterRateM3h produced-water flow rate in m3/h
   * @return outlet rate in 1/h
   */
  private double calculateOutletRate(double waterRateM3h) {
    if (holdUpVolumeM3 <= 0.0) {
      return 0.0;
    }
    return Math.max(0.0, waterRateM3h) / holdUpVolumeM3;
  }

  /**
   * Calculates first-order deactivation rate.
   *
   * @return decay rate in 1/h
   */
  private double calculateDecayRate() {
    if (Double.isInfinite(decayHalfLifeHours) || decayHalfLifeHours <= 0.0) {
      return 0.0;
    }
    return Math.log(2.0) / decayHalfLifeHours;
  }

  /**
   * Sets the well-mixed hold-up volume.
   *
   * @param holdUpVolumeM3 hold-up volume in m3
   */
  public void setHoldUpVolumeM3(double holdUpVolumeM3) {
    this.holdUpVolumeM3 = Math.max(0.0, holdUpVolumeM3);
  }

  /**
   * Gets the hold-up volume.
   *
   * @return hold-up volume in m3
   */
  public double getHoldUpVolumeM3() {
    return holdUpVolumeM3;
  }

  /**
   * Sets produced-water density.
   *
   * @param waterDensityKgm3 water density in kg/m3
   */
  public void setWaterDensityKgm3(double waterDensityKgm3) {
    this.waterDensityKgm3 = Math.max(1.0, waterDensityKgm3);
  }

  /**
   * Gets produced-water density.
   *
   * @return water density in kg/m3
   */
  public double getWaterDensityKgm3() {
    return waterDensityKgm3;
  }

  /**
   * Sets chemical deactivation half-life.
   *
   * @param decayHalfLifeHours half-life in hours, or positive infinity for no decay
   */
  public void setDecayHalfLifeHours(double decayHalfLifeHours) {
    this.decayHalfLifeHours =
        decayHalfLifeHours <= 0.0 ? Double.POSITIVE_INFINITY : decayHalfLifeHours;
  }

  /**
   * Gets chemical deactivation half-life.
   *
   * @return half-life in hours
   */
  public double getDecayHalfLifeHours() {
    return decayHalfLifeHours;
  }

  /**
   * Gets current chemical inventory.
   *
   * @return chemical inventory in kg
   */
  public double getChemicalMassKg() {
    return chemicalMassKg;
  }

  /**
   * Gets last effective dose.
   *
   * @return effective dose in ppm
   */
  public double getEffectiveDosePpm() {
    return effectiveDosePpm;
  }

  /**
   * Gets last inlet chemical mass flow.
   *
   * @return mass flow in kg/h
   */
  public double getLastInputMassFlowKgH() {
    return lastInputMassFlowKgH;
  }

  /**
   * Gets last outlet chemical mass flow.
   *
   * @return mass flow in kg/h
   */
  public double getLastOutletMassFlowKgH() {
    return lastOutletMassFlowKgH;
  }
}
