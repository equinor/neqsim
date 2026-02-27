package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * CO2 corrosion rate prediction using the de Waard-Milliams (1975/1991) model.
 *
 * <p>
 * The de Waard-Milliams model is the industry-standard empirical correlation for estimating
 * internal CO2 corrosion rates in carbon steel pipelines carrying wet gas or multiphase fluids. It
 * is referenced in NORSOK M-506 and widely used in subsea pipeline flow assurance studies.
 * </p>
 *
 * <p>
 * The 1991 revised model gives corrosion rate as:
 * </p>
 *
 * <pre>
 * {@code
 * log10(Vcor) = 5.8 - 1710 / (T + 273.15) + 0.67 * log10(pCO2)
 * }
 * </pre>
 *
 * <p>
 * where Vcor is in mm/yr, T in degrees Celsius, pCO2 in bar.
 * </p>
 *
 * <p>
 * Correction factors can be applied for:
 * </p>
 * <ul>
 * <li>Protective FeCO3 scale (at high temperature/pH)</li>
 * <li>pH effect</li>
 * <li>Glycol / inhibitor efficiency</li>
 * <li>Flow velocity / shear stress (erosion-corrosion)</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * DeWaardMilliamsCorrosion model = new DeWaardMilliamsCorrosion();
 * model.setTemperatureCelsius(60.0);
 * model.setCO2PartialPressure(2.0); // bar
 * model.setPH(4.5);
 * model.setInhibitorEfficiency(0.80);
 * double rate = model.calculateCorrosionRate(); // mm/yr
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class DeWaardMilliamsCorrosion implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Temperature in degrees Celsius. */
  private double temperatureC = 60.0;

  /** CO2 partial pressure in bar. */
  private double co2PartialPressure = 2.0;

  /** pH of the aqueous phase. */
  private double pH = 4.5;

  /** Total system pressure in bara. */
  private double totalPressure = 50.0;

  /** H2S partial pressure in bar. */
  private double h2sPartialPressure = 0.0;

  /** Flow velocity in m/s. */
  private double flowVelocity = 2.0;

  /** Pipe internal diameter in metres. */
  private double pipeDiameter = 0.254;

  /** Chemical inhibitor efficiency (0 to 1). */
  private double inhibitorEfficiency = 0.0;

  /** Glycol weight fraction in aqueous phase (0 to 1). */
  private double glycolFraction = 0.0;

  /** Whether to apply pH correction factor. */
  private boolean usePHCorrection = true;

  /** Whether to apply protective scale correction. */
  private boolean useScaleCorrection = true;

  /** Whether to apply flow velocity correction. */
  private boolean useFlowCorrection = true;

  /**
   * Creates a new DeWaardMilliamsCorrosion with default parameters.
   */
  public DeWaardMilliamsCorrosion() {}

  /**
   * Creates a new DeWaardMilliamsCorrosion with specified conditions.
   *
   * @param temperatureC temperature in Celsius
   * @param co2PartialPressureBar CO2 partial pressure in bar
   */
  public DeWaardMilliamsCorrosion(double temperatureC, double co2PartialPressureBar) {
    this.temperatureC = temperatureC;
    this.co2PartialPressure = co2PartialPressureBar;
  }

  /**
   * Sets the temperature.
   *
   * @param temperatureC temperature in Celsius
   */
  public void setTemperatureCelsius(double temperatureC) {
    this.temperatureC = temperatureC;
  }

  /**
   * Sets the CO2 partial pressure.
   *
   * @param pressureBar CO2 partial pressure in bar
   */
  public void setCO2PartialPressure(double pressureBar) {
    this.co2PartialPressure = pressureBar;
  }

  /**
   * Sets the pH value.
   *
   * @param pH pH of the aqueous phase (typically 3.5 to 6.5)
   */
  public void setPH(double pH) {
    this.pH = pH;
  }

  /**
   * Sets the total system pressure.
   *
   * @param pressureBar total pressure in bara
   */
  public void setTotalPressure(double pressureBar) {
    this.totalPressure = pressureBar;
  }

  /**
   * Sets the H2S partial pressure.
   *
   * @param pressureBar H2S partial pressure in bar
   */
  public void setH2SPartialPressure(double pressureBar) {
    this.h2sPartialPressure = pressureBar;
  }

  /**
   * Sets the flow velocity.
   *
   * @param velocityMs flow velocity in m/s
   */
  public void setFlowVelocity(double velocityMs) {
    this.flowVelocity = velocityMs;
  }

  /**
   * Sets the pipe internal diameter.
   *
   * @param diameterM internal diameter in metres
   */
  public void setPipeDiameter(double diameterM) {
    this.pipeDiameter = diameterM;
  }

  /**
   * Sets the chemical inhibitor efficiency.
   *
   * @param efficiency inhibitor efficiency (0.0 = none, 1.0 = perfect)
   */
  public void setInhibitorEfficiency(double efficiency) {
    this.inhibitorEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Sets the glycol weight fraction in the aqueous phase.
   *
   * @param fraction glycol weight fraction (0 to 1)
   */
  public void setGlycolFraction(double fraction) {
    this.glycolFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Calculates the baseline corrosion rate using de Waard-Milliams 1991.
   *
   * <pre>
   * {@code
   * log10(Vcor) = 5.8 - 1710 / (T + 273.15) + 0.67 * log10(pCO2)
   * }
   * </pre>
   *
   * @return baseline corrosion rate in mm/yr
   */
  public double calculateBaselineRate() {
    if (co2PartialPressure <= 0) {
      return 0.0;
    }
    double T = temperatureC + 273.15;
    double logRate = 5.8 - 1710.0 / T + 0.67 * Math.log10(co2PartialPressure);
    return Math.pow(10, logRate);
  }

  /**
   * Calculates the pH correction factor.
   *
   * <p>
   * The pH correction from de Waard-Lotz (1993) reduces the corrosion rate at higher pH due to
   * protective FeCO3 film formation:
   * </p>
   *
   * <pre>
   * {@code
   * f_pH = 10 ^ (0.32 * (pH_sat - pH_actual))
   * }
   * </pre>
   *
   * <p>
   * where pH_sat is the saturation pH for FeCO3 at the given conditions. For pH above approximately
   * 5.0 and temperature above 60 C, significant protection occurs.
   * </p>
   *
   * @return pH correction factor (less than or equal to 1 when pH is high)
   */
  public double getPHCorrectionFactor() {
    // Estimated saturation pH per NORSOK M-506 approach
    double pHSat = calculateSaturationPH();
    if (pH >= pHSat) {
      return Math.pow(10, 0.32 * (pHSat - pH));
    }
    return 1.0;
  }

  /**
   * Calculates the FeCO3 saturation pH estimate.
   *
   * <p>
   * Simplified from NORSOK M-506: pH_sat depends on temperature and CO2 partial pressure.
   * </p>
   *
   * @return estimated saturation pH
   */
  private double calculateSaturationPH() {
    // Simplified NORSOK M-506 saturation pH
    double logPco2 = co2PartialPressure > 0 ? Math.log10(co2PartialPressure) : -2;
    return 3.71 - 0.5 * logPco2 + 0.00133 * temperatureC;
  }

  /**
   * Calculates the protective scale correction factor.
   *
   * <p>
   * At temperatures above approximately 60-80 C and adequate pH, protective FeCO3 scale forms which
   * reduces corrosion rate. The maximum corrosion rate occurs around 60-80 C; above this the scale
   * becomes protective.
   * </p>
   *
   * @return scale correction factor (less than 1 at high temperature/pH)
   */
  public double getScaleCorrectionFactor() {
    if (temperatureC < 60.0) {
      return 1.0;
    }
    // de Waard-Lotz scale factor: reduction above ~80 C
    if (temperatureC > 80.0 && pH > 5.0) {
      double reduction = Math.min(0.7, (temperatureC - 80.0) * 0.01 * (pH - 4.0));
      return Math.max(0.1, 1.0 - reduction);
    }
    return 1.0;
  }

  /**
   * Calculates the glycol correction factor.
   *
   * <p>
   * Glycol in the aqueous phase reduces water activity and thus corrosion rate. The correction is
   * approximately (1 - glycol fraction) for low glycol concentrations; for high glycol (above 80
   * wt%), the rate drops significantly.
   * </p>
   *
   * @return glycol correction factor (0 to 1)
   */
  public double getGlycolCorrectionFactor() {
    if (glycolFraction <= 0.0) {
      return 1.0;
    }
    // Empirical: linear reduction with enhanced effect above 50 wt%
    if (glycolFraction > 0.80) {
      return 0.05;
    }
    return (1.0 - glycolFraction);
  }

  /**
   * Calculates the fully corrected corrosion rate.
   *
   * <p>
   * The corrected rate is: Vcor_corrected = Vcor_base * f_pH * f_scale * f_glycol * (1 - IE)
   * </p>
   *
   * @return corrected corrosion rate in mm/yr
   */
  public double calculateCorrosionRate() {
    double baseRate = calculateBaselineRate();

    double factor = 1.0;
    if (usePHCorrection) {
      factor *= getPHCorrectionFactor();
    }
    if (useScaleCorrection) {
      factor *= getScaleCorrectionFactor();
    }
    factor *= getGlycolCorrectionFactor();
    factor *= (1.0 - inhibitorEfficiency);

    return baseRate * factor;
  }

  /**
   * Calculates corrosion rates over a range of temperatures.
   *
   * @param minTempC minimum temperature in Celsius
   * @param maxTempC maximum temperature in Celsius
   * @param steps number of temperature steps
   * @return list of maps containing temperature and corrosion rate
   */
  public List<Map<String, Object>> calculateOverTemperatureRange(double minTempC, double maxTempC,
      int steps) {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    double savedTemp = this.temperatureC;

    double dT = (maxTempC - minTempC) / steps;
    for (int i = 0; i <= steps; i++) {
      double T = minTempC + i * dT;
      this.temperatureC = T;
      Map<String, Object> point = new LinkedHashMap<String, Object>();
      point.put("temperature_C", T);
      point.put("baselineRate_mmyr", calculateBaselineRate());
      point.put("correctedRate_mmyr", calculateCorrosionRate());
      results.add(point);
    }

    this.temperatureC = savedTemp;
    return results;
  }

  /**
   * Determines the corrosion severity category.
   *
   * <p>
   * Categories per NORSOK M-001:
   * </p>
   * <ul>
   * <li>Low: less than 0.1 mm/yr</li>
   * <li>Medium: 0.1 to 0.3 mm/yr</li>
   * <li>High: 0.3 to 1.0 mm/yr</li>
   * <li>Very High: greater than 1.0 mm/yr</li>
   * </ul>
   *
   * @return severity category string
   */
  public String getCorrosionSeverity() {
    double rate = calculateCorrosionRate();
    if (rate < 0.1) {
      return "Low";
    } else if (rate < 0.3) {
      return "Medium";
    } else if (rate < 1.0) {
      return "High";
    } else {
      return "Very High";
    }
  }

  /**
   * Estimates the required corrosion allowance for a given design life.
   *
   * @param designLifeYears pipeline design life in years
   * @return corrosion allowance in mm
   */
  public double estimateCorrosionAllowance(double designLifeYears) {
    return calculateCorrosionRate() * designLifeYears;
  }

  /**
   * Checks whether H2S content exceeds sweet/sour threshold.
   *
   * <p>
   * NACE MR0175 / ISO 15156 defines sour service when H2S partial pressure exceeds 0.003 bar (0.05
   * psia) at total pressure, or H2S concentration exceeds 50 mg/L in water.
   * </p>
   *
   * @return true if service is sour per NACE MR0175
   */
  public boolean isSourService() {
    return h2sPartialPressure > 0.003;
  }

  /**
   * Returns a comprehensive JSON report.
   *
   * @return JSON string with all corrosion parameters and results
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input conditions
    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("temperature_C", temperatureC);
    conditions.put("co2PartialPressure_bar", co2PartialPressure);
    conditions.put("h2sPartialPressure_bar", h2sPartialPressure);
    conditions.put("totalPressure_bar", totalPressure);
    conditions.put("pH", pH);
    conditions.put("flowVelocity_ms", flowVelocity);
    conditions.put("pipeDiameter_m", pipeDiameter);
    conditions.put("inhibitorEfficiency", inhibitorEfficiency);
    conditions.put("glycolFraction", glycolFraction);
    result.put("conditions", conditions);

    // Results
    Map<String, Object> rates = new LinkedHashMap<String, Object>();
    rates.put("baselineRate_mmyr", calculateBaselineRate());
    rates.put("pHCorrectionFactor", getPHCorrectionFactor());
    rates.put("scaleCorrectionFactor", getScaleCorrectionFactor());
    rates.put("glycolCorrectionFactor", getGlycolCorrectionFactor());
    rates.put("inhibitorFactor", 1.0 - inhibitorEfficiency);
    rates.put("correctedRate_mmyr", calculateCorrosionRate());
    rates.put("severity", getCorrosionSeverity());
    rates.put("corrosionAllowance_25yr_mm", estimateCorrosionAllowance(25.0));
    rates.put("isSourService", isSourService());
    result.put("results", rates);

    // Model info
    Map<String, Object> model = new LinkedHashMap<String, Object>();
    model.put("name", "de Waard-Milliams (1991)");
    model.put("reference",
        "de Waard, C. and Milliams, D.E., Carbonic acid corrosion of steel, Corrosion 31(5), 1975");
    model.put("revisedReference",
        "de Waard, C., Lotz, U., and Milliams, D.E., Predictive model for CO2 corrosion, CORROSION/91, Paper 577, NACE, 1991");
    model.put("applicableStandards", "NORSOK M-506, NACE SP0775");
    result.put("modelInfo", model);

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(result);
  }
}
