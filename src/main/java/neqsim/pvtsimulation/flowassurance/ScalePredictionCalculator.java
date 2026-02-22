package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Scale tendency prediction for common oilfield mineral scales.
 *
 * <p>
 * Predicts the saturation index (SI) for common mineral scales encountered in oil and gas
 * production systems:
 * </p>
 * <ul>
 * <li>Calcium carbonate (CaCO3 - calcite)</li>
 * <li>Barium sulphate (BaSO4 - barite)</li>
 * <li>Strontium sulphate (SrSO4 - celestite)</li>
 * <li>Calcium sulphate (CaSO4 - anhydrite/gypsum)</li>
 * <li>Iron carbonate (FeCO3 - siderite)</li>
 * </ul>
 *
 * <p>
 * The Saturation Index (SI) is defined as:
 * </p>
 *
 * <pre>
 * {@code
 * SI = log10(IAP / Ksp)
 * }
 * </pre>
 *
 * <p>
 * where IAP is the Ion Activity Product and Ksp is the temperature/pressure-dependent solubility
 * product. SI greater than 0 indicates supersaturation (scaling tendency), SI less than 0 indicates
 * undersaturation (no scaling tendency).
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * ScalePredictionCalculator calc = new ScalePredictionCalculator();
 * calc.setTemperatureCelsius(80.0);
 * calc.setPressureBara(100.0);
 * calc.setCalciumConcentration(1000.0); // mg/L
 * calc.setBicarbonateConcentration(500.0); // mg/L
 * calc.setBariumConcentration(50.0); // mg/L
 * calc.setSulphateConcentration(200.0); // mg/L
 * calc.setCO2PartialPressure(2.0); // bar
 * calc.setTotalDissolvedSolids(50000.0); // mg/L
 * calc.calculate();
 * System.out.println(calc.getCaCO3SaturationIndex());
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScalePredictionCalculator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // --- Input: Water Chemistry (mg/L) ---

  /** Calcium concentration in mg/L. */
  private double calciumMgL = 1000.0;

  /** Barium concentration in mg/L. */
  private double bariumMgL = 0.0;

  /** Strontium concentration in mg/L. */
  private double strontiumMgL = 0.0;

  /** Iron (Fe2+) concentration in mg/L. */
  private double ironMgL = 0.0;

  /** Bicarbonate (HCO3-) concentration in mg/L. */
  private double bicarbonateMgL = 500.0;

  /** Sulphate (SO4 2-) concentration in mg/L. */
  private double sulphateMgL = 100.0;

  /** Total dissolved solids in mg/L. */
  private double tdsMgL = 50000.0;

  // --- Input: Conditions ---

  /** Temperature in Celsius. */
  private double temperatureC = 80.0;

  /** Pressure in bara. */
  private double pressureBara = 100.0;

  /** CO2 partial pressure in bar. */
  private double co2PartialPressure = 2.0;

  /** pH of the water. */
  private double pH = 6.5;

  /** Whether to auto-calculate pH from CO2 (override manual pH). */
  private boolean autoCalcPH = false;

  // --- Results ---

  /** CaCO3 saturation index. */
  private double siCaCO3 = Double.NaN;

  /** BaSO4 saturation index. */
  private double siBaSO4 = Double.NaN;

  /** SrSO4 saturation index. */
  private double siSrSO4 = Double.NaN;

  /** CaSO4 saturation index. */
  private double siCaSO4 = Double.NaN;

  /** FeCO3 saturation index. */
  private double siFeCO3 = Double.NaN;

  /** Has been calculated. */
  private boolean calculated = false;

  /**
   * Creates a new ScalePredictionCalculator with default parameters.
   */
  public ScalePredictionCalculator() {}

  /**
   * Sets the temperature.
   *
   * @param tempC temperature in Celsius
   */
  public void setTemperatureCelsius(double tempC) {
    this.temperatureC = tempC;
    this.calculated = false;
  }

  /**
   * Sets the pressure.
   *
   * @param pressBar pressure in bara
   */
  public void setPressureBara(double pressBar) {
    this.pressureBara = pressBar;
    this.calculated = false;
  }

  /**
   * Sets the calcium concentration.
   *
   * @param mgPerL calcium in mg/L
   */
  public void setCalciumConcentration(double mgPerL) {
    this.calciumMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the barium concentration.
   *
   * @param mgPerL barium in mg/L
   */
  public void setBariumConcentration(double mgPerL) {
    this.bariumMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the strontium concentration.
   *
   * @param mgPerL strontium in mg/L
   */
  public void setStrontiumConcentration(double mgPerL) {
    this.strontiumMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the iron (Fe2+) concentration.
   *
   * @param mgPerL iron in mg/L
   */
  public void setIronConcentration(double mgPerL) {
    this.ironMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the bicarbonate concentration.
   *
   * @param mgPerL bicarbonate in mg/L
   */
  public void setBicarbonateConcentration(double mgPerL) {
    this.bicarbonateMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the sulphate concentration.
   *
   * @param mgPerL sulphate in mg/L
   */
  public void setSulphateConcentration(double mgPerL) {
    this.sulphateMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the total dissolved solids.
   *
   * @param mgPerL TDS in mg/L
   */
  public void setTotalDissolvedSolids(double mgPerL) {
    this.tdsMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the CO2 partial pressure.
   *
   * @param pressBar CO2 partial pressure in bar
   */
  public void setCO2PartialPressure(double pressBar) {
    this.co2PartialPressure = pressBar;
    this.calculated = false;
  }

  /**
   * Sets the pH.
   *
   * @param pH pH value
   */
  public void setPH(double pH) {
    this.pH = pH;
    this.autoCalcPH = false;
    this.calculated = false;
  }

  /**
   * Enables automatic pH calculation from CO2 partial pressure and bicarbonate.
   */
  public void enableAutoPH() {
    this.autoCalcPH = true;
    this.calculated = false;
  }

  /**
   * Calculates saturation indices for all scale types.
   */
  public void calculate() {
    // Ionic strength
    double ionicStrength = estimateIonicStrength();

    // Activity coefficients (Davies equation)
    double gammaDivalent = calculateActivityCoefficient(2, ionicStrength);
    double gammaMonovalent = calculateActivityCoefficient(1, ionicStrength);

    // Temperature in Kelvin
    double TK = temperatureC + 273.15;

    // Auto-calculate pH if requested
    if (autoCalcPH && co2PartialPressure > 0 && bicarbonateMgL > 0) {
      pH = estimatePH(TK, ionicStrength, gammaMonovalent);
    }

    // Concentrations in mol/L
    double cCa = calciumMgL / 40078.0; // MW Ca = 40.078
    double cBa = bariumMgL / 137327.0; // MW Ba = 137.327
    double cSr = strontiumMgL / 87620.0; // MW Sr = 87.62
    double cFe = ironMgL / 55845.0; // MW Fe = 55.845
    double cHCO3 = bicarbonateMgL / 61017.0; // MW HCO3 = 61.017
    double cSO4 = sulphateMgL / 96060.0; // MW SO4 = 96.06
    double cCO3 = estimateCarbonateFromBicarbonate(cHCO3, TK, gammaMonovalent);

    // 1. CaCO3 (calcite) saturation index
    double kspCaCO3 = calciteKsp(TK);
    double iapCaCO3 = (cCa * gammaDivalent) * (cCO3 * gammaDivalent);
    siCaCO3 = (iapCaCO3 > 0 && kspCaCO3 > 0) ? Math.log10(iapCaCO3 / kspCaCO3) : Double.NaN;

    // 2. BaSO4 (barite) saturation index
    if (bariumMgL > 0 && sulphateMgL > 0) {
      double kspBaSO4 = bariteKsp(TK);
      double iapBaSO4 = (cBa * gammaDivalent) * (cSO4 * gammaDivalent);
      siBaSO4 = (iapBaSO4 > 0 && kspBaSO4 > 0) ? Math.log10(iapBaSO4 / kspBaSO4) : Double.NaN;
    } else {
      siBaSO4 = Double.NaN;
    }

    // 3. SrSO4 (celestite) saturation index
    if (strontiumMgL > 0 && sulphateMgL > 0) {
      double kspSrSO4 = celestiteKsp(TK);
      double iapSrSO4 = (cSr * gammaDivalent) * (cSO4 * gammaDivalent);
      siSrSO4 = (iapSrSO4 > 0 && kspSrSO4 > 0) ? Math.log10(iapSrSO4 / kspSrSO4) : Double.NaN;
    } else {
      siSrSO4 = Double.NaN;
    }

    // 4. CaSO4 (anhydrite) saturation index
    if (sulphateMgL > 0) {
      double kspCaSO4 = anhydriteKsp(TK);
      double iapCaSO4 = (cCa * gammaDivalent) * (cSO4 * gammaDivalent);
      siCaSO4 = (iapCaSO4 > 0 && kspCaSO4 > 0) ? Math.log10(iapCaSO4 / kspCaSO4) : Double.NaN;
    } else {
      siCaSO4 = Double.NaN;
    }

    // 5. FeCO3 (siderite) saturation index
    if (ironMgL > 0) {
      double kspFeCO3 = sideriteKsp(TK);
      double iapFeCO3 = (cFe * gammaDivalent) * (cCO3 * gammaDivalent);
      siFeCO3 = (iapFeCO3 > 0 && kspFeCO3 > 0) ? Math.log10(iapFeCO3 / kspFeCO3) : Double.NaN;
    } else {
      siFeCO3 = Double.NaN;
    }

    calculated = true;
  }

  /**
   * Estimates ionic strength from TDS.
   *
   * <p>
   * Approximate relation: I = TDS(mg/L) / 40000 for typical oilfield brines.
   * </p>
   *
   * @return ionic strength in mol/L
   */
  private double estimateIonicStrength() {
    return tdsMgL / 40000.0;
  }

  /**
   * Calculates activity coefficient using the Davies equation.
   *
   * <pre>
   * {@code
   * log10(gamma) = -A * z ^ 2 * (sqrt(I) / (1 + sqrt(I)) - 0.3 * I)
   * }
   * </pre>
   *
   * @param charge ion charge
   * @param ionicStrength ionic strength mol/L
   * @return activity coefficient
   */
  private double calculateActivityCoefficient(int charge, double ionicStrength) {
    // Debye-Huckel A parameter (approximately 0.509 at 25 C, varies with T)
    double A = 0.509 + 0.0006 * (temperatureC - 25.0);
    double sqrtI = Math.sqrt(ionicStrength);
    double logGamma = -A * charge * charge * (sqrtI / (1.0 + sqrtI) - 0.3 * ionicStrength);
    return Math.pow(10, logGamma);
  }

  /**
   * Estimates CO3 2- concentration from HCO3- concentration and pH.
   *
   * <pre>
   * {@code
   *   [CO3 2-] = K2 * [HCO3-] / [H+]
   * }
   * </pre>
   *
   * @param cHCO3 bicarbonate concentration in mol/L
   * @param TK temperature in Kelvin
   * @param gammaMonovalent activity coefficient for monovalent ions
   * @return carbonate concentration in mol/L
   */
  private double estimateCarbonateFromBicarbonate(double cHCO3, double TK, double gammaMonovalent) {
    double K2 = calcK2Carbonate(TK);
    double hConc = Math.pow(10, -pH);
    if (hConc > 0) {
      return K2 * cHCO3 / hConc;
    }
    return 0.0;
  }

  /**
   * Estimates pH from CO2 partial pressure and bicarbonate concentration.
   *
   * @param TK temperature in Kelvin
   * @param ionicStrength ionic strength mol/L
   * @param gamma activity coefficient for monovalent ions
   * @return estimated pH
   */
  private double estimatePH(double TK, double ionicStrength, double gamma) {
    double KH = calcHenryCO2(TK);
    double K1 = calcK1Carbonate(TK);
    double co2aq = KH * co2PartialPressure;
    double cHCO3 = bicarbonateMgL / 61017.0;

    // pH = -log10([H+]), where [H+] = K1 * [CO2(aq)] / [HCO3-]
    if (cHCO3 > 0 && co2aq > 0) {
      double hConc = K1 * co2aq / cHCO3;
      return -Math.log10(hConc);
    }
    return 7.0;
  }

  // --- Solubility Products (Ksp) ---

  /**
   * Calculates calcite (CaCO3) solubility product.
   *
   * <p>
   * Based on Plummer and Busenberg (1982): log10(Ksp) = -171.9065 - 0.077993*T + 2839.319/T +
   * 71.595*log10(T)
   * </p>
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double calciteKsp(double TK) {
    double logKsp = -171.9065 - 0.077993 * TK + 2839.319 / TK + 71.595 * Math.log10(TK);
    return Math.pow(10, logKsp);
  }

  /**
   * Calculates barite (BaSO4) solubility product.
   *
   * <p>
   * Simplified temperature dependence: log10(Ksp) = -9.97 - 0.003 * (T - 298.15)
   * </p>
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double bariteKsp(double TK) {
    double logKsp = -9.97 - 0.003 * (TK - 298.15);
    return Math.pow(10, logKsp);
  }

  /**
   * Calculates celestite (SrSO4) solubility product.
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double celestiteKsp(double TK) {
    double logKsp = -6.63 - 0.002 * (TK - 298.15);
    return Math.pow(10, logKsp);
  }

  /**
   * Calculates anhydrite (CaSO4) solubility product.
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double anhydriteKsp(double TK) {
    double logKsp = -4.36 - 0.002 * (TK - 298.15);
    return Math.pow(10, logKsp);
  }

  /**
   * Calculates siderite (FeCO3) solubility product.
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double sideriteKsp(double TK) {
    double logKsp = -10.89 + 0.003 * (TK - 298.15);
    return Math.pow(10, logKsp);
  }

  // --- Carbonate Equilibrium Constants ---

  /**
   * Calculates the first dissociation constant of carbonic acid.
   *
   * @param TK temperature in Kelvin
   * @return K1 in mol/L
   */
  private double calcK1Carbonate(double TK) {
    // Plummer & Busenberg (1982) simplified
    double logK1 = -356.3094 - 0.06091964 * TK + 21834.37 / TK + 126.8339 * Math.log10(TK)
        - 1684915.0 / (TK * TK);
    return Math.pow(10, logK1);
  }

  /**
   * Calculates the second dissociation constant of carbonic acid.
   *
   * @param TK temperature in Kelvin
   * @return K2 in mol/L
   */
  private double calcK2Carbonate(double TK) {
    double logK2 = -107.8871 - 0.03252849 * TK + 5151.79 / TK + 38.92561 * Math.log10(TK)
        - 563713.9 / (TK * TK);
    return Math.pow(10, logK2);
  }

  /**
   * Calculates CO2 Henry's law constant in mol/L/bar.
   *
   * @param TK temperature in Kelvin
   * @return Henry's constant
   */
  private double calcHenryCO2(double TK) {
    // Simplified: KH decreases with temperature
    double logKH = -6.8346 + 1684.88 / TK + 21.6215 * Math.log10(TK) - 0.012174 * TK;
    return Math.pow(10, logKH);
  }

  // --- Getters ---

  /**
   * Returns the CaCO3 saturation index.
   *
   * @return SI value; positive means scaling tendency
   */
  public double getCaCO3SaturationIndex() {
    if (!calculated) {
      calculate();
    }
    return siCaCO3;
  }

  /**
   * Returns the BaSO4 saturation index.
   *
   * @return SI value; positive means scaling tendency, NaN if barium not present
   */
  public double getBaSO4SaturationIndex() {
    if (!calculated) {
      calculate();
    }
    return siBaSO4;
  }

  /**
   * Returns the SrSO4 saturation index.
   *
   * @return SI value; positive means scaling tendency, NaN if strontium not present
   */
  public double getSrSO4SaturationIndex() {
    if (!calculated) {
      calculate();
    }
    return siSrSO4;
  }

  /**
   * Returns the CaSO4 saturation index.
   *
   * @return SI value; positive means scaling tendency
   */
  public double getCaSO4SaturationIndex() {
    if (!calculated) {
      calculate();
    }
    return siCaSO4;
  }

  /**
   * Returns the FeCO3 saturation index.
   *
   * @return SI value; positive means scaling tendency, NaN if iron not present
   */
  public double getFeCO3SaturationIndex() {
    if (!calculated) {
      calculate();
    }
    return siFeCO3;
  }

  /**
   * Returns a list of scale types that show positive saturation index.
   *
   * @return list of scale types at risk
   */
  public List<String> getScaleRisks() {
    if (!calculated) {
      calculate();
    }
    List<String> risks = new ArrayList<String>();
    if (!Double.isNaN(siCaCO3) && siCaCO3 > 0) {
      risks.add(String.format("CaCO3 (calcite): SI=%.2f", siCaCO3));
    }
    if (!Double.isNaN(siBaSO4) && siBaSO4 > 0) {
      risks.add(String.format("BaSO4 (barite): SI=%.2f", siBaSO4));
    }
    if (!Double.isNaN(siSrSO4) && siSrSO4 > 0) {
      risks.add(String.format("SrSO4 (celestite): SI=%.2f", siSrSO4));
    }
    if (!Double.isNaN(siCaSO4) && siCaSO4 > 0) {
      risks.add(String.format("CaSO4 (anhydrite/gypsum): SI=%.2f", siCaSO4));
    }
    if (!Double.isNaN(siFeCO3) && siFeCO3 > 0) {
      risks.add(String.format("FeCO3 (siderite): SI=%.2f", siFeCO3));
    }
    return risks;
  }

  /**
   * Returns whether any scale type shows supersaturation.
   *
   * @return true if any SI is greater than 0
   */
  public boolean hasScalingRisk() {
    return !getScaleRisks().isEmpty();
  }

  /**
   * Returns a comprehensive JSON report.
   *
   * @return JSON string
   */
  public String toJson() {
    if (!calculated) {
      calculate();
    }

    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Water chemistry
    Map<String, Object> chemistry = new LinkedHashMap<String, Object>();
    chemistry.put("calcium_mgL", calciumMgL);
    chemistry.put("barium_mgL", bariumMgL);
    chemistry.put("strontium_mgL", strontiumMgL);
    chemistry.put("iron_mgL", ironMgL);
    chemistry.put("bicarbonate_mgL", bicarbonateMgL);
    chemistry.put("sulphate_mgL", sulphateMgL);
    chemistry.put("TDS_mgL", tdsMgL);
    chemistry.put("pH", pH);
    result.put("waterChemistry", chemistry);

    // Conditions
    Map<String, Object> cond = new LinkedHashMap<String, Object>();
    cond.put("temperature_C", temperatureC);
    cond.put("pressure_bara", pressureBara);
    cond.put("co2PartialPressure_bar", co2PartialPressure);
    cond.put("ionicStrength_molL", estimateIonicStrength());
    result.put("conditions", cond);

    // Saturation indices
    Map<String, Object> indices = new LinkedHashMap<String, Object>();
    addSI(indices, "CaCO3_calcite", siCaCO3);
    addSI(indices, "BaSO4_barite", siBaSO4);
    addSI(indices, "SrSO4_celestite", siSrSO4);
    addSI(indices, "CaSO4_anhydrite", siCaSO4);
    addSI(indices, "FeCO3_siderite", siFeCO3);
    result.put("saturationIndices", indices);

    // Risk summary
    result.put("scaleRisks", getScaleRisks());
    result.put("hasScalingRisk", hasScalingRisk());

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(result);
  }

  /**
   * Adds a saturation index entry to a map.
   *
   * @param map target map
   * @param name scale type name
   * @param si saturation index value
   */
  private void addSI(Map<String, Object> map, String name, double si) {
    Map<String, Object> entry = new LinkedHashMap<String, Object>();
    entry.put("SI", Double.isNaN(si) ? "N/A" : si);
    entry.put("tendency",
        Double.isNaN(si) ? "N/A" : (si > 0.5 ? "High" : (si > 0 ? "Moderate" : "None")));
    map.put(name, entry);
  }
}
