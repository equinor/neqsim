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

  /** Magnesium concentration in mg/L. */
  private double magnesiumMgL = 0.0;

  /** Sodium concentration in mg/L. */
  private double sodiumMgL = 0.0;

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
   * Sets the magnesium concentration.
   *
   * @param mgPerL magnesium in mg/L
   */
  public void setMagnesiumConcentration(double mgPerL) {
    this.magnesiumMgL = mgPerL;
    this.calculated = false;
  }

  /**
   * Sets the sodium concentration.
   *
   * @param mgPerL sodium in mg/L
   */
  public void setSodiumConcentration(double mgPerL) {
    this.sodiumMgL = mgPerL;
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
    double cMg = magnesiumMgL / 24305.0; // MW Mg = 24.305
    double cNa = sodiumMgL / 22990.0; // MW Na = 22.990
    double cHCO3 = bicarbonateMgL / 61017.0; // MW HCO3 = 61.017
    double cSO4 = sulphateMgL / 96060.0; // MW SO4 = 96.06
    double cCO3 = estimateCarbonateFromBicarbonate(cHCO3, TK, gammaMonovalent);

    // Apply ion pairing corrections to get free ion concentrations
    // Ion pairs: CaSO4⁰, MgSO4⁰, NaSO4⁻, CaHCO3⁺, MgHCO3⁺, CaCO3⁰
    double[] freeConc = correctForIonPairing(cCa, cMg, cNa, cBa, cSr, cFe, cSO4, cCO3, cHCO3, TK,
        gammaDivalent, gammaMonovalent);
    cCa = freeConc[0];
    cSO4 = freeConc[1];
    cCO3 = freeConc[2];
    cHCO3 = freeConc[3];

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
   * Corrects total analytical concentrations for ion pairing.
   *
   * <p>
   * Accounts for aqueous complexes: CaSO4(aq), MgSO4(aq), NaSO4(-), CaHCO3(+), MgHCO3(+),
   * CaCO3(aq). Uses association constants from Garrels &amp; Thompson (1962) with T-corrections.
   * </p>
   *
   * @param cCa total calcium mol/L
   * @param cMg total magnesium mol/L
   * @param cNa total sodium mol/L
   * @param cBa total barium mol/L
   * @param cSr total strontium mol/L
   * @param cFe total iron mol/L
   * @param cSO4 total sulphate mol/L
   * @param cCO3 total carbonate mol/L
   * @param cHCO3 total bicarbonate mol/L
   * @param TK temperature in Kelvin
   * @param gammaDival activity coefficient for divalent ions
   * @param gammaMono activity coefficient for monovalent ions
   * @return array [freeCa, freeSO4, freeCO3, freeHCO3]
   */
  private double[] correctForIonPairing(double cCa, double cMg, double cNa, double cBa, double cSr,
      double cFe, double cSO4, double cCO3, double cHCO3, double TK, double gammaDival,
      double gammaMono) {
    // Association constants at 25°C (Garrels & Thompson, 1962; Plummer et al., 1988)
    // K_assoc = [MX] / ([M²+]*[X²-]) for neutral pairs
    // log10(K) values at 25°C with simple T-correction: log10K(T) = log10K(25) +
    // dH/(2.303R)*(1/298-1/T)

    // CaSO4(aq): log10K = 2.31 at 25°C, ΔH = 6.1 kJ/mol
    double logKCaSO4 = 2.31 + 6100.0 / (2.303 * 8.314) * (1.0 / 298.15 - 1.0 / TK);
    double KCaSO4 = Math.pow(10.0, logKCaSO4);

    // MgSO4(aq): log10K = 2.37 at 25°C, ΔH = 5.6 kJ/mol
    double logKMgSO4 = 2.37 + 5600.0 / (2.303 * 8.314) * (1.0 / 298.15 - 1.0 / TK);
    double KMgSO4 = Math.pow(10.0, logKMgSO4);

    // NaSO4(-): log10K = 0.70 at 25°C, ΔH = 3.0 kJ/mol
    double logKNaSO4 = 0.70 + 3000.0 / (2.303 * 8.314) * (1.0 / 298.15 - 1.0 / TK);
    double KNaSO4 = Math.pow(10.0, logKNaSO4);

    // CaHCO3(+): log10K = 1.11 at 25°C, ΔH = 3.6 kJ/mol
    double logKCaHCO3 = 1.11 + 3600.0 / (2.303 * 8.314) * (1.0 / 298.15 - 1.0 / TK);
    double KCaHCO3 = Math.pow(10.0, logKCaHCO3);

    // MgHCO3(+): log10K = 1.16 at 25°C, ΔH = 2.9 kJ/mol
    double logKMgHCO3 = 1.16 + 2900.0 / (2.303 * 8.314) * (1.0 / 298.15 - 1.0 / TK);
    double KMgHCO3 = Math.pow(10.0, logKMgHCO3);

    // CaCO3(aq): log10K = 3.22 at 25°C, ΔH = 14.0 kJ/mol
    double logKCaCO3 = 3.22 + 14000.0 / (2.303 * 8.314) * (1.0 / 298.15 - 1.0 / TK);
    double KCaCO3 = Math.pow(10.0, logKCaCO3);

    // Iterative free ion calculation (3 iterations is sufficient for convergence)
    double freeCa = cCa;
    double freeMg = cMg;
    double freeNa = cNa;
    double freeSO4 = cSO4;
    double freeCO3 = cCO3;
    double freeHCO3 = cHCO3;
    double g2 = gammaDival * gammaDival; // γ²(divalent) for neutral product
    double gm2 = gammaMono * gammaMono;

    for (int iter = 0; iter < 5; iter++) {
      // Amount of SO4 complexed
      double cCaSO4 = KCaSO4 * freeCa * g2 * freeSO4 * g2;
      double cMgSO4 = KMgSO4 * freeMg * g2 * freeSO4 * g2;
      double cNaSO4 = KNaSO4 * freeNa * gammaMono * freeSO4 * g2 / gammaMono;

      // Amount of CO3/HCO3 complexed
      double cCaHCO3 = KCaHCO3 * freeCa * g2 * freeHCO3 * gammaMono / gammaMono;
      double cMgHCO3 = KMgHCO3 * freeMg * g2 * freeHCO3 * gammaMono / gammaMono;
      double cCaCO3aq = KCaCO3 * freeCa * g2 * freeCO3 * g2;

      // Free concentrations
      freeCa = cCa / (1.0 + KCaSO4 * g2 * freeSO4 * g2
          + KCaHCO3 * g2 * freeHCO3 * gammaMono / gammaMono + KCaCO3 * g2 * freeCO3 * g2);
      if (freeCa < 0) {
        freeCa = cCa * 0.01;
      }

      freeMg = cMg / (1.0 + KMgSO4 * g2 * freeSO4 * g2 + KMgHCO3 * g2 * freeHCO3 * gm2 / gm2);
      if (freeMg < 0) {
        freeMg = cMg * 0.01;
      }

      freeNa = cNa / (1.0 + KNaSO4 * gammaMono * freeSO4 * g2 / gammaMono);
      if (freeNa < 0) {
        freeNa = cNa * 0.01;
      }

      freeSO4 = cSO4 / (1.0 + KCaSO4 * freeCa * g2 * g2 + KMgSO4 * freeMg * g2 * g2
          + KNaSO4 * freeNa * gammaMono * g2 / gammaMono);
      if (freeSO4 < 0) {
        freeSO4 = cSO4 * 0.01;
      }

      freeCO3 = cCO3 / (1.0 + KCaCO3 * freeCa * g2 * g2);
      if (freeCO3 < 0) {
        freeCO3 = cCO3 * 0.01;
      }

      freeHCO3 =
          cHCO3 / (1.0 + KCaHCO3 * freeCa * g2 * gammaMono / gammaMono + KMgHCO3 * freeMg * g2);
      if (freeHCO3 < 0) {
        freeHCO3 = cHCO3 * 0.01;
      }
    }

    return new double[] {freeCa, freeSO4, freeCO3, freeHCO3};
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
    // Debye-Hückel A parameter with proper T-dependence via water properties
    double A = debyeHuckelA(temperatureC + 273.15);
    double sqrtI = Math.sqrt(ionicStrength);
    double logGamma = -A * charge * charge * (sqrtI / (1.0 + sqrtI) - 0.3 * ionicStrength);
    return Math.pow(10, logGamma);
  }

  /**
   * Calculates the Debye-Hückel A parameter as a function of temperature.
   *
   * <p>
   * Uses the correlation from Robinson &amp; Stokes based on water density and dielectric constant:
   * A = 1.8246e6 * sqrt(ρ) / (ε*T)^(3/2) where ρ is water density in g/cm³ and ε is the static
   * dielectric constant.
   * </p>
   *
   * @param TK temperature in Kelvin
   * @return Debye-Hückel A in log10 units (kg^0.5/mol^0.5)
   */
  private double debyeHuckelA(double TK) {
    // Water density (g/cm³) from Kell (1975) simplified correlation
    double TC = TK - 273.15;
    double rho = 0.99983 + 5.0948e-5 * TC - 7.5722e-6 * TC * TC + 3.8907e-8 * TC * TC * TC
        - 1.2e-10 * TC * TC * TC * TC;
    if (rho < 0.85) {
      rho = 0.85; // lower bound for high T
    }

    // Static dielectric constant of water from Archer & Wang (1990)
    double eps = 87.740 - 0.40008 * TC + 9.398e-4 * TC * TC - 1.410e-6 * TC * TC * TC;
    if (eps < 20.0) {
      eps = 20.0; // lower bound
    }

    // A = 1.8246e6 * sqrt(ρ) / (ε*T)^(3/2), converts to log10 base
    double epsT = eps * TK;
    double A = 1.8246e6 * Math.sqrt(rho) / Math.pow(epsT, 1.5);
    return A;
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
   * Applies pressure correction to a solubility product.
   *
   * <p>
   * Uses the partial molar volume change formula: ln(Ksp(P)/Ksp(P0)) = -ΔV°(P-P0)/(R*T) where ΔV°
   * is in cm³/mol, P in bara, R = 83.1446 cm³·bar/(mol·K).
   * </p>
   *
   * @param ksp0 solubility product at 1 atm
   * @param TK temperature in Kelvin
   * @param vDelta molar volume change in cm³/mol
   * @return pressure-corrected Ksp
   */
  private double applyPressureCorrection(double ksp0, double TK, double vDelta) {
    if (pressureBara <= 1.013 || Math.abs(vDelta) < 1e-10) {
      return ksp0;
    }
    double R_cm3bar = 83.1446;
    double deltaP = pressureBara - 1.01325;
    double lnCorr = -vDelta * deltaP / (R_cm3bar * TK);
    return ksp0 * Math.exp(lnCorr);
  }

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
    return applyPressureCorrection(Math.pow(10, logKsp), TK, -58.4);
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
    // Monnin (1999): log10(Ksp) = 136.035 - 7680.41/T - 48.595*log10(T)
    double logKsp = 136.035 - 7680.41 / TK - 48.595 * Math.log10(TK);
    return applyPressureCorrection(Math.pow(10, logKsp), TK, -46.4);
  }

  /**
   * Calculates celestite (SrSO4) solubility product.
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double celestiteKsp(double TK) {
    // Monnin (1999): log10(Ksp) = 155.889 - 7862.38/T - 56.625*log10(T)
    double logKsp = 155.889 - 7862.38 / TK - 56.625 * Math.log10(TK);
    return applyPressureCorrection(Math.pow(10, logKsp), TK, -47.0);
  }

  /**
   * Calculates anhydrite (CaSO4) solubility product.
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double anhydriteKsp(double TK) {
    // Blount & Dickson (1973): log10(Ksp) = 85.685 - 4279.82/T - 30.219*log10(T)
    double logKsp = 85.685 - 4279.82 / TK - 30.219 * Math.log10(TK);
    return applyPressureCorrection(Math.pow(10, logKsp), TK, -52.4);
  }

  /**
   * Calculates siderite (FeCO3) solubility product.
   *
   * @param TK temperature in Kelvin
   * @return Ksp in (mol/L)^2
   */
  private double sideriteKsp(double TK) {
    // Greenberg & Tomson (1992):
    // log10(Ksp) = -59.3498 - 0.041377*T + 2.1963/T + 24.5724*log10(T) + 2.518e-5*T^2
    double logKsp =
        -59.3498 - 0.041377 * TK + 2.1963 / TK + 24.5724 * Math.log10(TK) + 2.518e-5 * TK * TK;
    return applyPressureCorrection(Math.pow(10, logKsp), TK, -52.9);
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
