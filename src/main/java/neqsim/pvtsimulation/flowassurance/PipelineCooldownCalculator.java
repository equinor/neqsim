package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calculates pipeline thermal cooldown from initial operating temperature to ambient.
 *
 * <p>
 * Pipeline cooldown after shutdown is a key flow assurance concern because it determines:
 * </p>
 * <ul>
 * <li>Available time before fluid reaches WAT (wax appearance temperature)</li>
 * <li>Available time before fluid reaches hydrate formation temperature</li>
 * <li>Required deadleg insulation specifications</li>
 * <li>Restart planning: whether chemical injection or depressurisation is needed</li>
 * </ul>
 *
 * <p>
 * The model uses a lumped-parameter radial heat transfer approach treating the pipeline
 * cross-section as nested cylindrical shells (fluid, steel wall, insulation, external coating) with
 * an overall heat transfer coefficient (U-value).
 * </p>
 *
 * <p>
 * For subsea pipelines the external boundary is seawater (forced or natural convection). For buried
 * pipelines the external boundary is soil at a specified depth.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * PipelineCooldownCalculator calc = new PipelineCooldownCalculator();
 * calc.setInternalDiameter(0.254); // 10 inch
 * calc.setWallThickness(0.0127); // 12.7 mm
 * calc.setInsulationThickness(0.050); // 50 mm PUF
 * calc.setInsulationConductivity(0.17); // W/mK
 * calc.setOverallUValue(3.0); // W/m2K
 * calc.setInitialFluidTemperature(80.0 + 273.15);
 * calc.setAmbientTemperature(4.0 + 273.15);
 * calc.setFluidDensity(750.0);
 * calc.setFluidSpecificHeat(2200.0);
 * calc.setTimeStepMinutes(5.0);
 * calc.setTotalTimeHours(48.0);
 * calc.calculate();
 *
 * double hydTemp = 20.0 + 273.15;
 * double timeToHydrate = calc.getTimeToReachTemperature(hydTemp);
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PipelineCooldownCalculator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // --- Geometry ---

  /** Internal diameter in metres. */
  private double internalDiameter = 0.254;

  /** Steel wall thickness in metres. */
  private double wallThickness = 0.0127;

  /** Insulation thickness in metres. */
  private double insulationThickness = 0.050;

  /** External coating thickness in metres. */
  private double coatingThickness = 0.006;

  // --- Material Properties ---

  /** Steel density in kg/m3. */
  private double steelDensity = 7850.0;

  /** Steel specific heat in J/kgK. */
  private double steelSpecificHeat = 500.0;

  /** Steel thermal conductivity in W/mK. */
  private double steelConductivity = 50.0;

  /** Insulation thermal conductivity in W/mK. */
  private double insulationConductivity = 0.17;

  /** Insulation density in kg/m3. */
  private double insulationDensity = 600.0;

  /** Insulation specific heat in J/kgK. */
  private double insulationSpecificHeat = 1700.0;

  // --- Fluid Properties ---

  /** Fluid density at shutdown in kg/m3. */
  private double fluidDensity = 750.0;

  /** Fluid specific heat in J/kgK. */
  private double fluidSpecificHeat = 2200.0;

  // --- Boundary Conditions ---

  /** Initial fluid temperature in Kelvin. */
  private double initialFluidTemperature = 273.15 + 80.0;

  /** Ambient temperature in Kelvin (seawater or soil). */
  private double ambientTemperature = 273.15 + 4.0;

  /** Overall heat transfer coefficient W/m2K (referenced to OD). */
  private double overallUValue = 3.0;

  /** External convection coefficient W/m2K (if U-value not set directly). */
  private double externalHTC = 300.0;

  /** Whether to use overall U-value (true) or calculate from layers (false). */
  private boolean useOverallU = true;

  // --- Time Parameters ---

  /** Time step in minutes. */
  private double timeStepMinutes = 5.0;

  /** Total simulation time in hours. */
  private double totalTimeHours = 48.0;

  // --- Results ---

  /** Time array in hours. */
  private double[] timeHours;

  /** Fluid temperature array in Kelvin. */
  private double[] fluidTemperature;

  /** Steel wall mean temperature in Kelvin. */
  private double[] wallTemperature;

  /** Has been calculated. */
  private boolean calculated = false;

  /**
   * Creates a new PipelineCooldownCalculator with default parameters.
   */
  public PipelineCooldownCalculator() {}

  /**
   * Sets the internal diameter.
   *
   * @param diameter internal diameter in metres
   */
  public void setInternalDiameter(double diameter) {
    this.internalDiameter = diameter;
  }

  /**
   * Sets the wall thickness.
   *
   * @param thickness wall thickness in metres
   */
  public void setWallThickness(double thickness) {
    this.wallThickness = thickness;
  }

  /**
   * Sets the insulation thickness.
   *
   * @param thickness insulation thickness in metres (0 for bare pipe)
   */
  public void setInsulationThickness(double thickness) {
    this.insulationThickness = thickness;
  }

  /**
   * Sets the insulation thermal conductivity.
   *
   * @param conductivity insulation conductivity in W/mK
   */
  public void setInsulationConductivity(double conductivity) {
    this.insulationConductivity = conductivity;
  }

  /**
   * Sets the overall U-value directly, bypassing layer calculations.
   *
   * @param uValue overall heat transfer coefficient in W/m2K (referenced to outer diameter)
   */
  public void setOverallUValue(double uValue) {
    this.overallUValue = uValue;
    this.useOverallU = true;
  }

  /**
   * Enables calculation of U-value from individual layer resistances.
   */
  public void useLayerCalculation() {
    this.useOverallU = false;
  }

  /**
   * Sets the external heat transfer coefficient.
   *
   * @param htc external HTC in W/m2K
   */
  public void setExternalHTC(double htc) {
    this.externalHTC = htc;
  }

  /**
   * Sets the initial fluid temperature at shutdown.
   *
   * @param temperatureK initial temperature in Kelvin
   */
  public void setInitialFluidTemperature(double temperatureK) {
    this.initialFluidTemperature = temperatureK;
  }

  /**
   * Sets the ambient temperature (seawater or soil).
   *
   * @param temperatureK ambient temperature in Kelvin
   */
  public void setAmbientTemperature(double temperatureK) {
    this.ambientTemperature = temperatureK;
  }

  /**
   * Sets the fluid density.
   *
   * @param density fluid density in kg/m3
   */
  public void setFluidDensity(double density) {
    this.fluidDensity = density;
  }

  /**
   * Sets the fluid specific heat.
   *
   * @param cp specific heat in J/kgK
   */
  public void setFluidSpecificHeat(double cp) {
    this.fluidSpecificHeat = cp;
  }

  /**
   * Sets the time step.
   *
   * @param minutes time step in minutes
   */
  public void setTimeStepMinutes(double minutes) {
    this.timeStepMinutes = minutes;
  }

  /**
   * Sets the total simulation time.
   *
   * @param hours total time in hours
   */
  public void setTotalTimeHours(double hours) {
    this.totalTimeHours = hours;
  }

  /**
   * Sets the coating thickness.
   *
   * @param thickness coating thickness in metres
   */
  public void setCoatingThickness(double thickness) {
    this.coatingThickness = thickness;
  }

  /**
   * Sets the steel density.
   *
   * @param density steel density in kg/m3
   */
  public void setSteelDensity(double density) {
    this.steelDensity = density;
  }

  /**
   * Sets the steel specific heat.
   *
   * @param cp steel specific heat in J/kgK
   */
  public void setSteelSpecificHeat(double cp) {
    this.steelSpecificHeat = cp;
  }

  /**
   * Runs the cooldown calculation using an explicit Euler time-marching scheme.
   *
   * <p>
   * The lumped thermal model treats the fluid and steel wall as a combined thermal mass losing heat
   * through the insulation and external boundary to the ambient. At each time step:
   * </p>
   *
   * <pre>
   * {@code
   * Q_loss = U * A_outer * (T_fluid - T_ambient) [W/m]
   * dT/dt = -Q_loss / (m_fluid*Cp_fluid + m_steel*Cp_steel) [K/s]
   * T_new = T_old + dT/dt * dt
   * }
   * </pre>
   */
  public void calculate() {
    double dt = timeStepMinutes * 60.0; // seconds
    int nSteps = (int) Math.ceil(totalTimeHours * 3600.0 / dt);

    timeHours = new double[nSteps + 1];
    fluidTemperature = new double[nSteps + 1];
    wallTemperature = new double[nSteps + 1];

    // Geometry
    double ri = internalDiameter / 2.0;
    double ro = ri + wallThickness;
    double rIns = ro + insulationThickness;
    double rOuter = rIns + coatingThickness;

    // Overall U-value calculation (per unit length, referenced to outer diameter)
    double uPerLength;
    if (useOverallU) {
      uPerLength = overallUValue * 2.0 * Math.PI * rOuter;
    } else {
      // Resistance from steel wall
      double rSteel = Math.log(ro / ri) / (2.0 * Math.PI * steelConductivity);
      // Resistance from insulation
      double rIns_res = 0.0;
      if (insulationThickness > 0.001) {
        rIns_res = Math.log(rIns / ro) / (2.0 * Math.PI * insulationConductivity);
      }
      // External convection resistance
      double rExt = 1.0 / (2.0 * Math.PI * rOuter * externalHTC);
      uPerLength = 1.0 / (rSteel + rIns_res + rExt);
    }

    // Thermal mass per unit length
    double areaFluid = Math.PI * ri * ri;
    double areaSteel = Math.PI * (ro * ro - ri * ri);
    double areaIns = Math.PI * (rIns * rIns - ro * ro);

    double massCpFluid = fluidDensity * areaFluid * fluidSpecificHeat;
    double massCpSteel = steelDensity * areaSteel * steelSpecificHeat;
    double massCpIns = insulationDensity * areaIns * insulationSpecificHeat;

    double totalMassCp = massCpFluid + massCpSteel + massCpIns;

    // Initial conditions
    fluidTemperature[0] = initialFluidTemperature;
    wallTemperature[0] = initialFluidTemperature;
    timeHours[0] = 0.0;

    for (int i = 0; i < nSteps; i++) {
      double T = fluidTemperature[i];
      double qLoss = uPerLength * (T - ambientTemperature); // W/m
      double dTdt = -qLoss / totalMassCp; // K/s
      fluidTemperature[i + 1] = T + dTdt * dt;
      wallTemperature[i + 1] = fluidTemperature[i + 1]; // lumped model
      timeHours[i + 1] = (i + 1) * dt / 3600.0;
    }

    calculated = true;
  }

  /**
   * Returns the time in hours to reach a specified temperature.
   *
   * @param targetTemperatureK target temperature in Kelvin
   * @return time in hours to reach the target, or -1 if not reached within simulation time
   */
  public double getTimeToReachTemperature(double targetTemperatureK) {
    if (!calculated) {
      calculate();
    }
    for (int i = 0; i < fluidTemperature.length; i++) {
      if (fluidTemperature[i] <= targetTemperatureK) {
        if (i == 0) {
          return 0.0;
        }
        // Linear interpolation
        double t1 = timeHours[i - 1];
        double t2 = timeHours[i];
        double T1 = fluidTemperature[i - 1];
        double T2 = fluidTemperature[i];
        return t1 + (t2 - t1) * (T1 - targetTemperatureK) / (T1 - T2);
      }
    }
    return -1.0; // Not reached
  }

  /**
   * Returns the fluid temperature at a given time.
   *
   * @param hours time in hours
   * @return fluid temperature in Kelvin, or NaN if out of range
   */
  public double getTemperatureAtTime(double hours) {
    if (!calculated) {
      calculate();
    }
    if (hours < 0 || hours > totalTimeHours) {
      return Double.NaN;
    }
    for (int i = 0; i < timeHours.length - 1; i++) {
      if (hours >= timeHours[i] && hours <= timeHours[i + 1]) {
        double frac = (hours - timeHours[i]) / (timeHours[i + 1] - timeHours[i]);
        return fluidTemperature[i] + frac * (fluidTemperature[i + 1] - fluidTemperature[i]);
      }
    }
    return fluidTemperature[fluidTemperature.length - 1];
  }

  /**
   * Returns the analytical infinite-time asymptotic cooldown time constant (tau).
   *
   * <p>
   * For a lumped system: tau = (m*Cp) / (U*A) in seconds. The fluid temperature follows T(t) =
   * T_amb + (T_init - T_amb) * exp(-t/tau).
   * </p>
   *
   * @return time constant in hours
   */
  public double getTimeConstantHours() {
    double ri = internalDiameter / 2.0;
    double ro = ri + wallThickness;
    double rIns = ro + insulationThickness;
    double rOuter = rIns + coatingThickness;

    double uPerLength;
    if (useOverallU) {
      uPerLength = overallUValue * 2.0 * Math.PI * rOuter;
    } else {
      double rSteel = Math.log(ro / ri) / (2.0 * Math.PI * steelConductivity);
      double rInsRes = insulationThickness > 0.001
          ? Math.log(rIns / ro) / (2.0 * Math.PI * insulationConductivity)
          : 0.0;
      double rExt = 1.0 / (2.0 * Math.PI * rOuter * externalHTC);
      uPerLength = 1.0 / (rSteel + rInsRes + rExt);
    }

    double areaFluid = Math.PI * ri * ri;
    double areaSteel = Math.PI * (ro * ro - ri * ri);
    double areaIns = Math.PI * (rIns * rIns - ro * ro);
    double totalMassCp =
        fluidDensity * areaFluid * fluidSpecificHeat + steelDensity * areaSteel * steelSpecificHeat
            + insulationDensity * areaIns * insulationSpecificHeat;

    double tauSeconds = totalMassCp / uPerLength;
    return tauSeconds / 3600.0;
  }

  /**
   * Returns the time array in hours.
   *
   * @return time array
   */
  public double[] getTimeHours() {
    if (!calculated) {
      calculate();
    }
    return timeHours;
  }

  /**
   * Returns the fluid temperature array in Kelvin.
   *
   * @return temperature array
   */
  public double[] getFluidTemperature() {
    if (!calculated) {
      calculate();
    }
    return fluidTemperature;
  }

  /**
   * Returns the calculated overall U-value from layer properties.
   *
   * @return overall U-value in W/m2K referenced to outer diameter
   */
  public double getCalculatedUValue() {
    double ri = internalDiameter / 2.0;
    double ro = ri + wallThickness;
    double rIns = ro + insulationThickness;
    double rOuter = rIns + coatingThickness;

    double rSteel = Math.log(ro / ri) / (2.0 * Math.PI * steelConductivity);
    double rInsRes =
        insulationThickness > 0.001 ? Math.log(rIns / ro) / (2.0 * Math.PI * insulationConductivity)
            : 0.0;
    double rExt = 1.0 / (2.0 * Math.PI * rOuter * externalHTC);
    double uPerLength = 1.0 / (rSteel + rInsRes + rExt);
    return uPerLength / (2.0 * Math.PI * rOuter);
  }

  /**
   * Returns JSON representation of cooldown results.
   *
   * @return JSON string
   */
  public String toJson() {
    if (!calculated) {
      calculate();
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("initialTemperature_C", initialFluidTemperature - 273.15);
    result.put("ambientTemperature_C", ambientTemperature - 273.15);
    result.put("internalDiameter_m", internalDiameter);
    result.put("wallThickness_mm", wallThickness * 1000.0);
    result.put("insulationThickness_mm", insulationThickness * 1000.0);
    result.put("overallUValue_Wm2K", useOverallU ? overallUValue : getCalculatedUValue());
    result.put("timeConstant_hours", getTimeConstantHours());
    result.put("totalSimulationTime_hours", totalTimeHours);

    // Cooldown profile (sampled)
    List<Map<String, Object>> profile = new ArrayList<Map<String, Object>>();
    int sampleInterval = Math.max(1, timeHours.length / 100);
    for (int i = 0; i < timeHours.length; i += sampleInterval) {
      Map<String, Object> point = new LinkedHashMap<String, Object>();
      point.put("time_hours", Math.round(timeHours[i] * 100.0) / 100.0);
      point.put("fluidTemperature_C", Math.round((fluidTemperature[i] - 273.15) * 100.0) / 100.0);
      profile.add(point);
    }
    result.put("cooldownProfile", profile);

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(result);
  }
}
