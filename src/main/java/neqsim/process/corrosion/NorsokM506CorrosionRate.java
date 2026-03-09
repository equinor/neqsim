package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * CO2 corrosion rate prediction model per NORSOK M-506 (2005/2017).
 *
 * <p>
 * Implements the NORSOK M-506 standard "CO2 corrosion rate calculation model" for internal
 * corrosion of carbon steel pipelines and process piping in CO2-containing environments with free
 * water. The model is based on the de Waard-Milliams-Lotz equations with NORSOK-specific
 * corrections.
 * </p>
 *
 * <p>
 * The model calculates:
 * </p>
 * <ul>
 * <li>CO2 fugacity from partial pressure using a simplified Peng-Robinson correction</li>
 * <li>In-situ pH of CO2-saturated water considering temperature, fugacity, bicarbonate
 * concentration, and ionic strength</li>
 * <li>Baseline corrosion rate from the de Waard-Milliams equation (different regimes for T below
 * and above 20 degrees C)</li>
 * <li>pH correction factor (Fpht) with asymmetric formula per NORSOK M-506</li>
 * <li>Scaling temperature (Tscale) for protective FeCO3 film formation</li>
 * <li>Wall shear stress and flow correction factor</li>
 * <li>Glycol/MEG correction for reduced water activity</li>
 * <li>Inhibitor efficiency</li>
 * <li>Corrosion allowance for pipeline design</li>
 * </ul>
 *
 * <p>
 * Applicable range per NORSOK M-506:
 * </p>
 * <ul>
 * <li>Temperature: 5 to 150 degrees C</li>
 * <li>CO2 partial pressure: up to 10 bar</li>
 * <li>pH: 3.5 to 6.5</li>
 * <li>Total pressure: up to 1000 bar</li>
 * <li>Carbon steel only (for CRA materials, see {@link NorsokM001MaterialSelection})</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
 * model.setTemperatureCelsius(60.0);
 * model.setTotalPressureBara(100.0);
 * model.setCO2MoleFraction(0.02);
 * model.setFlowVelocityMs(3.0);
 * model.setPipeDiameterM(0.254);
 * model.calculate();
 *
 * double rate = model.getCorrectedCorrosionRate(); // mm/yr
 * double pH = model.getCalculatedPH();
 * String json = model.toJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see neqsim.pvtsimulation.flowassurance.DeWaardMilliamsCorrosion
 * @see NorsokM001MaterialSelection
 */
public class NorsokM506CorrosionRate implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // --- Constants ---

  /** Debye-Huckel constant at 25 degrees C for activity coefficient correction. */
  private static final double DEBYE_HUCKEL_A = 0.509;

  /** Steel density in kg/m3 for wall loss conversion. */
  private static final double STEEL_DENSITY_KG_M3 = 7850.0;

  // --- Input parameters ---

  /** Operating temperature in degrees Celsius. */
  private double temperatureC = 60.0;

  /** Total system pressure in bara. */
  private double totalPressureBara = 100.0;

  /** CO2 mole fraction in the gas phase (0 to 1). */
  private double co2MoleFraction = 0.02;

  /** H2S mole fraction in the gas phase (0 to 1) for sour service check. */
  private double h2sMoleFraction = 0.0;

  /** Actual pH of the aqueous phase (if known); use -1 to calculate from equilibrium. */
  private double actualPH = -1.0;

  /** Bicarbonate concentration in mg/L (for pH adjustment from formation water). */
  private double bicarbonateConcentrationMgL = 0.0;

  /** Ionic strength in mol/L (for activity coefficient correction). */
  private double ionicStrengthMolL = 0.0;

  /** Flow velocity in m/s. */
  private double flowVelocityMs = 2.0;

  /** Pipe internal diameter in metres. */
  private double pipeDiameterM = 0.254;

  /** Liquid density in kg/m3 (for shear stress calculation). */
  private double liquidDensityKgM3 = 1000.0;

  /** Liquid dynamic viscosity in Pa.s (for Reynolds number). */
  private double liquidViscosityPas = 0.001;

  /** Chemical inhibitor efficiency (0 to 1). */
  private double inhibitorEfficiency = 0.0;

  /** Glycol (MEG/DEG) weight fraction in aqueous phase (0 to 1). */
  private double glycolWeightFraction = 0.0;

  /** Whether to apply pH correction factor. */
  private boolean usePHCorrection = true;

  /** Whether to apply scale correction factor. */
  private boolean useScaleCorrection = true;

  /** Whether to apply flow correction factor. */
  private boolean useFlowCorrection = true;

  // --- Calculated results (populated by calculate()) ---

  /** CO2 fugacity in bar. */
  private double co2FugacityBar = 0.0;

  /** CO2 fugacity coefficient (dimensionless). */
  private double co2FugacityCoeff = 1.0;

  /** Calculated pH of CO2-saturated water. */
  private double calculatedPH = 4.5;

  /** Baseline (uncorrected) corrosion rate in mm/yr. */
  private double baselineCorrosionRate = 0.0;

  /** pH correction factor (Fpht). */
  private double phCorrectionFactor = 1.0;

  /** Scale correction factor (Fscale). */
  private double scaleCorrectionFactor = 1.0;

  /** Flow correction factor. */
  private double flowCorrectionFactor = 1.0;

  /** Glycol correction factor. */
  private double glycolCorrectionFactor = 1.0;

  /** Fully corrected corrosion rate in mm/yr. */
  private double correctedCorrosionRate = 0.0;

  /** Scaling temperature in degrees C (protective FeCO3 formation). */
  private double scalingTemperatureC = 0.0;

  /** Wall shear stress in Pa. */
  private double wallShearStressPa = 0.0;

  /** Whether the model has been calculated. */
  private boolean hasBeenCalculated = false;

  /**
   * Creates a new NorsokM506CorrosionRate with default parameters.
   */
  public NorsokM506CorrosionRate() {}

  /**
   * Creates a new NorsokM506CorrosionRate with specified conditions.
   *
   * @param temperatureC operating temperature in Celsius (5 to 150)
   * @param totalPressureBara total system pressure in bara
   * @param co2MoleFraction CO2 mole fraction in gas phase (0 to 1)
   */
  public NorsokM506CorrosionRate(double temperatureC, double totalPressureBara,
      double co2MoleFraction) {
    this.temperatureC = temperatureC;
    this.totalPressureBara = totalPressureBara;
    this.co2MoleFraction = co2MoleFraction;
  }

  // --- Setters ---

  /**
   * Sets the operating temperature.
   *
   * @param temperatureC temperature in Celsius (valid range: 5 to 150)
   */
  public void setTemperatureCelsius(double temperatureC) {
    this.temperatureC = temperatureC;
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the total system pressure.
   *
   * @param pressureBara total pressure in bara (valid range: 1 to 1000)
   */
  public void setTotalPressureBara(double pressureBara) {
    this.totalPressureBara = pressureBara;
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the CO2 mole fraction in the gas phase.
   *
   * @param moleFraction CO2 mole fraction (0 to 1)
   */
  public void setCO2MoleFraction(double moleFraction) {
    this.co2MoleFraction = Math.max(0.0, Math.min(1.0, moleFraction));
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the H2S mole fraction in the gas phase (for sour service classification).
   *
   * @param moleFraction H2S mole fraction (0 to 1)
   */
  public void setH2SMoleFraction(double moleFraction) {
    this.h2sMoleFraction = Math.max(0.0, Math.min(1.0, moleFraction));
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the actual pH of the aqueous phase.
   *
   * <p>
   * If set to a positive value, this overrides the equilibrium pH calculation. Set to -1 to use the
   * calculated equilibrium pH from CO2-water chemistry.
   * </p>
   *
   * @param pH actual pH (3.0 to 7.0, or -1 to calculate)
   */
  public void setActualPH(double pH) {
    this.actualPH = pH;
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the bicarbonate concentration in formation water.
   *
   * <p>
   * Bicarbonate ions raise the pH above the pure CO2-water value, which reduces the corrosion rate.
   * This is important for fields with high bicarbonate formation water.
   * </p>
   *
   * @param concentrationMgL bicarbonate concentration in mg/L (0 to 10000)
   */
  public void setBicarbonateConcentrationMgL(double concentrationMgL) {
    this.bicarbonateConcentrationMgL = Math.max(0.0, concentrationMgL);
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the ionic strength of the aqueous phase.
   *
   * <p>
   * Ionic strength affects activity coefficients and thus pH. Typical seawater has I approximately
   * 0.7 mol/L.
   * </p>
   *
   * @param ionicStrength ionic strength in mol/L (0 to 5)
   */
  public void setIonicStrengthMolL(double ionicStrength) {
    this.ionicStrengthMolL = Math.max(0.0, ionicStrength);
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the flow velocity.
   *
   * @param velocityMs flow velocity in m/s (0 to 30)
   */
  public void setFlowVelocityMs(double velocityMs) {
    this.flowVelocityMs = Math.max(0.0, velocityMs);
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the pipe internal diameter.
   *
   * @param diameterM pipe inner diameter in metres (0.01 to 2.0)
   */
  public void setPipeDiameterM(double diameterM) {
    this.pipeDiameterM = Math.max(0.001, diameterM);
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the liquid density (used for wall shear stress calculation).
   *
   * @param densityKgM3 liquid density in kg/m3 (500 to 1500)
   */
  public void setLiquidDensityKgM3(double densityKgM3) {
    this.liquidDensityKgM3 = densityKgM3;
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the liquid dynamic viscosity (used for Reynolds number calculation).
   *
   * @param viscosityPas dynamic viscosity in Pa.s (0.0001 to 0.1)
   */
  public void setLiquidViscosityPas(double viscosityPas) {
    this.liquidViscosityPas = Math.max(1e-6, viscosityPas);
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the chemical inhibitor efficiency.
   *
   * @param efficiency inhibitor efficiency factor (0.0 = no inhibitor, 1.0 = perfect)
   */
  public void setInhibitorEfficiency(double efficiency) {
    this.inhibitorEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
    this.hasBeenCalculated = false;
  }

  /**
   * Sets the glycol (MEG/DEG) weight fraction in the aqueous phase.
   *
   * <p>
   * Glycol reduces water activity and thus the corrosion rate. Typical MEG injection gives 40-80
   * wt%.
   * </p>
   *
   * @param weightFraction glycol weight fraction (0.0 to 1.0)
   */
  public void setGlycolWeightFraction(double weightFraction) {
    this.glycolWeightFraction = Math.max(0.0, Math.min(1.0, weightFraction));
    this.hasBeenCalculated = false;
  }

  /**
   * Sets whether to apply pH correction factor.
   *
   * @param use true to apply pH correction (default true)
   */
  public void setUsePHCorrection(boolean use) {
    this.usePHCorrection = use;
    this.hasBeenCalculated = false;
  }

  /**
   * Sets whether to apply scale (FeCO3) correction factor.
   *
   * @param use true to apply scale correction (default true)
   */
  public void setUseScaleCorrection(boolean use) {
    this.useScaleCorrection = use;
    this.hasBeenCalculated = false;
  }

  /**
   * Sets whether to apply flow velocity correction factor.
   *
   * @param use true to apply flow correction (default true)
   */
  public void setUseFlowCorrection(boolean use) {
    this.useFlowCorrection = use;
    this.hasBeenCalculated = false;
  }

  // --- Core calculation methods ---

  /**
   * Runs all calculation steps per NORSOK M-506.
   *
   * <p>
   * Calculates CO2 fugacity, in-situ pH, baseline rate, all correction factors, and the final
   * corrected corrosion rate. Results are available via getter methods after calling this method.
   * </p>
   */
  public void calculate() {
    co2FugacityCoeff = calculateFugacityCoefficient();
    co2FugacityBar = co2MoleFraction * totalPressureBara * co2FugacityCoeff;
    calculatedPH = calculateEquilibriumPH();
    scalingTemperatureC = calculateScalingTemperature();
    baselineCorrosionRate = calculateBaselineRate();

    double effectivePH = (actualPH > 0) ? actualPH : calculatedPH;

    if (usePHCorrection) {
      phCorrectionFactor = calculatePHCorrectionFactor(effectivePH);
    } else {
      phCorrectionFactor = 1.0;
    }

    if (useScaleCorrection) {
      scaleCorrectionFactor = calculateScaleCorrectionFactor();
    } else {
      scaleCorrectionFactor = 1.0;
    }

    if (useFlowCorrection) {
      wallShearStressPa = calculateWallShearStress();
      flowCorrectionFactor = calculateFlowCorrectionFactor();
    } else {
      flowCorrectionFactor = 1.0;
    }

    glycolCorrectionFactor = calculateGlycolCorrectionFactor();

    correctedCorrosionRate = baselineCorrosionRate * phCorrectionFactor * scaleCorrectionFactor
        * flowCorrectionFactor * glycolCorrectionFactor * (1.0 - inhibitorEfficiency);

    hasBeenCalculated = true;
  }

  /**
   * Calculates the CO2 fugacity coefficient using simplified Peng-Robinson.
   *
   * <p>
   * Per NORSOK M-506:
   * </p>
   *
   * <pre>
   * {@code
   * ln(phi_CO2) = P * (0.0031 - 1.4 / T)
   * }
   * </pre>
   *
   * <p>
   * where P is total pressure in bar and T is temperature in Kelvin.
   * </p>
   *
   * @return CO2 fugacity coefficient (dimensionless)
   */
  public double calculateFugacityCoefficient() {
    double tempK = temperatureC + 273.15;
    double lnPhi = totalPressureBara * (0.0031 - 1.4 / tempK);
    return Math.exp(lnPhi);
  }

  /**
   * Calculates the CO2 fugacity.
   *
   * <pre>
   * {@code
   * f_CO2 = y_CO2 * P_total * phi_CO2
   * }
   * </pre>
   *
   * @return CO2 fugacity in bar
   */
  public double calculateCO2Fugacity() {
    double phi = calculateFugacityCoefficient();
    return co2MoleFraction * totalPressureBara * phi;
  }

  /**
   * Calculates the in-situ pH of CO2-saturated water at the given temperature and CO2 fugacity.
   *
   * <p>
   * Uses the combined equilibrium approach where pH of CO2-saturated water is calculated from the
   * product of Henry's law constant (KH) and the apparent first dissociation constant (Ka1') of
   * carbonic acid:
   * </p>
   *
   * <pre>
   * {@code
   * pH = A(T) - 0.5 * log10(fCO2)
   *
   * where A(T) = 0.5 * (pKa1' + pKH) is a temperature-dependent function
   * }
   * </pre>
   *
   * <p>
   * The correlation is calibrated against literature data for CO2-water systems (de Waard-Lotz
   * 1993, Dugstad et al.) giving pH approximately 3.9 at 25 degrees C and 1 bar CO2, increasing
   * slightly with temperature.
   * </p>
   *
   * <p>
   * When bicarbonate is present, the pH is calculated from the charge balance. The ionic strength
   * correction uses the extended Debye-Huckel equation.
   * </p>
   *
   * @return calculated pH
   */
  public double calculateEquilibriumPH() {
    double fCO2 = co2MoleFraction * totalPressureBara * calculateFugacityCoefficient();

    if (fCO2 <= 0.0) {
      return 7.0; // neutral if no CO2
    }

    // Combined temperature-dependent function A(T) = 0.5*(pKa1' + pKH)
    // Calibrated to give pH ~3.92 at 25°C, 1 bar CO2
    // pKH: CO2 solubility in water (mol/L/bar)
    // pKH = 1.12 + 0.01623*TC - 8.93e-5*TC^2
    // pKa1': apparent first dissociation of carbonic acid
    // pKa1' ~ 6.37 (weak temperature dependence)
    double pKH = 1.12 + 0.01623 * temperatureC - 8.93e-5 * temperatureC * temperatureC;
    double pKa1 = 6.41 + 0.0004 * temperatureC + 5.0e-6 * temperatureC * temperatureC;

    // Apply ionic strength correction (extended Debye-Huckel)
    if (ionicStrengthMolL > 0.0) {
      double correction =
          DEBYE_HUCKEL_A * Math.sqrt(ionicStrengthMolL) / (1.0 + Math.sqrt(ionicStrengthMolL));
      // Ionic strength increases apparent Ka1 (lowers pKa1) and slightly affects KH
      pKa1 -= 2.0 * correction;
    }

    if (bicarbonateConcentrationMgL > 0.0) {
      // Convert bicarbonate from mg/L to mol/L (MW of HCO3- = 61.02 g/mol)
      double hco3MolL = bicarbonateConcentrationMgL / 61020.0;

      // Ka1'*KH product
      double ka1KH = Math.pow(10, -(pKa1 + pKH));

      // Charge balance: [H+]^2 + [HCO3-]*[H+] - Ka1'*KH*fCO2 = 0
      double product = ka1KH * fCO2;
      double discriminant = hco3MolL * hco3MolL + 4.0 * product;
      double hPlus = (-hco3MolL + Math.sqrt(discriminant)) / 2.0;

      if (hPlus <= 0.0) {
        return 7.0;
      }
      return -Math.log10(hPlus);
    } else {
      // Pure CO2-water: pH = 0.5*(pKa1 + pKH) - 0.5*log10(fCO2)
      return 0.5 * (pKa1 + pKH) - 0.5 * Math.log10(fCO2);
    }
  }

  /**
   * Calculates the baseline (uncorrected) corrosion rate per NORSOK M-506.
   *
   * <p>
   * Uses the de Waard-Milliams (1991) equation:
   * </p>
   *
   * <pre>
   * {@code
   * log10(CR) = 5.8 - 1710 / T + 0.67 * log10(fCO2)
   * }
   * </pre>
   *
   * <p>
   * where CR is in mm/yr, T in Kelvin, fCO2 (CO2 fugacity) in bar. This equation is valid for the
   * full temperature range of 5 to 150 degrees C per NORSOK M-506.
   * </p>
   *
   * @return baseline corrosion rate in mm/yr
   */
  public double calculateBaselineRate() {
    double fCO2 = (co2FugacityBar > 0) ? co2FugacityBar : calculateCO2Fugacity();

    if (fCO2 <= 0.0) {
      return 0.0;
    }

    double tempK = temperatureC + 273.15;
    double logRate = 5.8 - 1710.0 / tempK + 0.67 * Math.log10(fCO2);

    return Math.max(0.0, Math.pow(10, logRate));
  }

  /**
   * Calculates the scaling temperature where protective FeCO3 scale begins to form.
   *
   * <p>
   * Per NORSOK M-506:
   * </p>
   *
   * <pre>
   * {@code
   * Tscale = 2400 / (6.7 + 0.644 * log10(fCO2)) - 273.15   (degrees C)
   * }
   * </pre>
   *
   * <p>
   * Above this temperature, protective FeCO3 scale forms and reduces the corrosion rate.
   * </p>
   *
   * @return scaling temperature in degrees Celsius
   */
  public double calculateScalingTemperature() {
    double fCO2 = (co2FugacityBar > 0) ? co2FugacityBar : calculateCO2Fugacity();

    if (fCO2 <= 0.0) {
      return 150.0; // no CO2, no scale temperature defined
    }

    double denominator = 6.7 + 0.644 * Math.log10(fCO2);
    if (denominator <= 0.0) {
      return 150.0;
    }

    return 2400.0 / denominator - 273.15;
  }

  /**
   * Calculates the pH correction factor (Fpht) per NORSOK M-506.
   *
   * <p>
   * The correction is asymmetric around the CO2-saturated pH:
   * </p>
   *
   * <pre>
   * {@code
   * If pH_actual < pH_CO2:
   *   Fpht = 10^(0.34 * (pH_CO2 - pH_actual))       [more acidic -> higher rate]
   *
   * If pH_actual >= pH_CO2:
   *   Fpht = 10^(1.23 * (pH_CO2 - pH_actual))       [more alkaline -> lower rate]
   * }
   * </pre>
   *
   * @param effectivePH the effective pH to use (actual or calculated)
   * @return pH correction factor (Fpht)
   */
  public double calculatePHCorrectionFactor(double effectivePH) {
    double pHco2 = calculatedPH;
    double deltaPH = pHco2 - effectivePH;

    double fpht;
    if (effectivePH < pHco2) {
      // More acidic than saturation: higher corrosion
      fpht = Math.pow(10, 0.34 * deltaPH);
    } else {
      // More alkaline (e.g. from bicarbonate): lower corrosion
      fpht = Math.pow(10, 1.23 * deltaPH);
    }

    // Cap the correction factor at reasonable bounds
    return Math.max(0.001, Math.min(fpht, 10.0));
  }

  /**
   * Calculates the scale correction factor (Fscale) for protective FeCO3 film.
   *
   * <p>
   * Per NORSOK M-506, when the operating temperature exceeds the scaling temperature, a protective
   * FeCO3 scale forms that reduces the corrosion rate:
   * </p>
   * <ul>
   * <li>T below Tscale: Fscale = 1.0 (no protective scale)</li>
   * <li>T above Tscale: Fscale decreases as scale becomes more protective</li>
   * </ul>
   *
   * @return scale correction factor (0.01 to 1.0)
   */
  public double calculateScaleCorrectionFactor() {
    double tScale = (scalingTemperatureC > 0) ? scalingTemperatureC : calculateScalingTemperature();

    if (temperatureC < tScale) {
      return 1.0; // no protective scale
    }

    // Above scaling temperature: protective FeCO3 reduces rate
    // The reduction increases with temperature above Tscale
    double excessTemp = temperatureC - tScale;
    double tScaleK = tScale + 273.15;

    // Exponential reduction per NORSOK M-506 approach
    double scaleFactor = Math.exp(-0.04 * excessTemp * excessTemp / (tScaleK * 0.1));

    return Math.max(0.01, Math.min(scaleFactor, 1.0));
  }

  /**
   * Calculates the wall shear stress in the pipe.
   *
   * <p>
   * Uses the Fanning friction factor for turbulent flow:
   * </p>
   *
   * <pre>
   * {@code
   * Re = rho * v * D / mu
   * f = 0.046 * Re^(-0.2)     (Blasius correlation for turbulent flow)
   * tau = 0.5 * f * rho * v^2  (Pa)
   * }
   * </pre>
   *
   * @return wall shear stress in Pa
   */
  public double calculateWallShearStress() {
    if (flowVelocityMs <= 0.0 || pipeDiameterM <= 0.0) {
      return 0.0;
    }
    if (liquidDensityKgM3 <= 0.0 || liquidViscosityPas <= 0.0) {
      return 0.0;
    }

    double re = liquidDensityKgM3 * flowVelocityMs * pipeDiameterM / liquidViscosityPas;

    double fanningF;
    if (re < 2300) {
      // Laminar flow
      fanningF = 16.0 / re;
    } else {
      // Turbulent: Blasius correlation
      fanningF = 0.046 * Math.pow(re, -0.2);
    }

    return 0.5 * fanningF * liquidDensityKgM3 * flowVelocityMs * flowVelocityMs;
  }

  /**
   * Calculates the flow correction factor based on wall shear stress.
   *
   * <p>
   * Higher flow velocity increases mass transfer of CO2 to the wall and can remove protective
   * scales. Per NORSOK M-506, the flow correction is based on wall shear stress relative to a
   * reference stress of 1 Pa. The correction is capped at a factor of 5 per the standard.
   * </p>
   *
   * @return flow correction factor (1.0 to 5.0)
   */
  public double calculateFlowCorrectionFactor() {
    double tau = (wallShearStressPa > 0) ? wallShearStressPa : calculateWallShearStress();

    if (tau <= 1.0) {
      return 1.0;
    }

    // Flow correction: (tau / tau_ref)^0.5, capped at 5
    double factor = Math.pow(tau / 1.0, 0.5);
    return Math.min(factor, 5.0);
  }

  /**
   * Calculates the glycol (MEG/DEG) correction factor.
   *
   * <p>
   * Per NORSOK M-506, glycol reduces water activity and thus the corrosion rate. The correction
   * follows an empirical relationship:
   * </p>
   * <ul>
   * <li>Below 50 wt%: Fglyc = 1 - wg (linear reduction)</li>
   * <li>50-80 wt%: Fglyc = 0.5 * (1 - wg)^2 (accelerated reduction)</li>
   * <li>Above 80 wt%: Fglyc = 0.05 (minimal residual corrosion)</li>
   * </ul>
   *
   * @return glycol correction factor (0.05 to 1.0)
   */
  public double calculateGlycolCorrectionFactor() {
    if (glycolWeightFraction <= 0.0) {
      return 1.0;
    }

    if (glycolWeightFraction >= 0.80) {
      return 0.05;
    }

    if (glycolWeightFraction >= 0.50) {
      double remainder = 1.0 - glycolWeightFraction;
      return 0.5 * remainder * remainder + 0.05;
    }

    return 1.0 - glycolWeightFraction;
  }

  // --- Getters for results ---

  /**
   * Returns the CO2 fugacity.
   *
   * @return CO2 fugacity in bar
   */
  public double getCO2FugacityBar() {
    ensureCalculated();
    return co2FugacityBar;
  }

  /**
   * Returns the CO2 fugacity coefficient.
   *
   * @return fugacity coefficient (dimensionless)
   */
  public double getCO2FugacityCoefficient() {
    ensureCalculated();
    return co2FugacityCoeff;
  }

  /**
   * Returns the calculated equilibrium pH.
   *
   * @return calculated pH of CO2-saturated water
   */
  public double getCalculatedPH() {
    ensureCalculated();
    return calculatedPH;
  }

  /**
   * Returns the effective pH used in the corrosion calculation.
   *
   * @return effective pH (user-specified actual pH or calculated equilibrium pH)
   */
  public double getEffectivePH() {
    ensureCalculated();
    return (actualPH > 0) ? actualPH : calculatedPH;
  }

  /**
   * Returns the baseline (uncorrected) corrosion rate.
   *
   * @return baseline corrosion rate in mm/yr
   */
  public double getBaselineCorrosionRate() {
    ensureCalculated();
    return baselineCorrosionRate;
  }

  /**
   * Returns the fully corrected corrosion rate.
   *
   * @return corrected corrosion rate in mm/yr
   */
  public double getCorrectedCorrosionRate() {
    ensureCalculated();
    return correctedCorrosionRate;
  }

  /**
   * Returns the scaling temperature for protective FeCO3 formation.
   *
   * @return scaling temperature in degrees Celsius
   */
  public double getScalingTemperatureC() {
    ensureCalculated();
    return scalingTemperatureC;
  }

  /**
   * Returns the wall shear stress.
   *
   * @return wall shear stress in Pa
   */
  public double getWallShearStressPa() {
    ensureCalculated();
    return wallShearStressPa;
  }

  /**
   * Returns the pH correction factor.
   *
   * @return pH correction factor (Fpht)
   */
  public double getPHCorrectionFactor() {
    ensureCalculated();
    return phCorrectionFactor;
  }

  /**
   * Returns the scale correction factor.
   *
   * @return scale correction factor (Fscale)
   */
  public double getScaleCorrectionFactor() {
    ensureCalculated();
    return scaleCorrectionFactor;
  }

  /**
   * Returns the flow correction factor.
   *
   * @return flow correction factor
   */
  public double getFlowCorrectionFactor() {
    ensureCalculated();
    return flowCorrectionFactor;
  }

  /**
   * Returns the glycol correction factor.
   *
   * @return glycol correction factor
   */
  public double getGlycolCorrectionFactor() {
    ensureCalculated();
    return glycolCorrectionFactor;
  }

  // --- Derived calculations ---

  /**
   * Calculates the required corrosion allowance for a given design life.
   *
   * <p>
   * Per NORSOK M-001, the corrosion allowance (CA) is typically:
   * </p>
   * <ul>
   * <li>CA = CR * design_life (mm)</li>
   * <li>Minimum CA: 1.0 mm for carbon steel per NORSOK M-001</li>
   * <li>Maximum practical CA: typically 6.0 mm (above this, consider CRA material)</li>
   * </ul>
   *
   * @param designLifeYears design life in years (typically 20-30)
   * @return corrosion allowance in mm
   */
  public double calculateCorrosionAllowance(double designLifeYears) {
    ensureCalculated();
    double ca = correctedCorrosionRate * designLifeYears;
    return Math.max(1.0, ca); // minimum 1 mm per NORSOK M-001
  }

  /**
   * Returns the corrosion severity classification per NORSOK M-001.
   *
   * <p>
   * Severity categories:
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
    ensureCalculated();
    if (correctedCorrosionRate < 0.1) {
      return "Low";
    } else if (correctedCorrosionRate < 0.3) {
      return "Medium";
    } else if (correctedCorrosionRate < 1.0) {
      return "High";
    } else {
      return "Very High";
    }
  }

  /**
   * Determines the CO2 partial pressure in bar.
   *
   * @return CO2 partial pressure in bar
   */
  public double getCO2PartialPressureBar() {
    return co2MoleFraction * totalPressureBara;
  }

  /**
   * Determines the H2S partial pressure in bar.
   *
   * @return H2S partial pressure in bar
   */
  public double getH2SPartialPressureBar() {
    return h2sMoleFraction * totalPressureBara;
  }

  /**
   * Checks whether the service is sour per NACE MR0175/ISO 15156.
   *
   * <p>
   * NACE defines sour service when H2S partial pressure exceeds 0.003 bar (0.3 kPa).
   * </p>
   *
   * @return true if sour service conditions
   */
  public boolean isSourService() {
    return getH2SPartialPressureBar() > 0.003;
  }

  /**
   * Returns the H2S sour severity classification per NACE MR0175/ISO 15156.
   *
   * <ul>
   * <li>Non-sour: H2S pp less than 0.3 kPa (0.003 bar)</li>
   * <li>Mild sour: 0.3 to 1.0 kPa (0.003 to 0.01 bar)</li>
   * <li>Moderate sour: 1.0 to 10 kPa (0.01 to 0.1 bar)</li>
   * <li>Severe sour: greater than 10 kPa (0.1 bar)</li>
   * </ul>
   *
   * @return sour severity classification string
   */
  public String getSourSeverityClassification() {
    double h2sPP = getH2SPartialPressureBar();
    if (h2sPP < 0.003) {
      return "Non-sour";
    } else if (h2sPP < 0.01) {
      return "Mild sour";
    } else if (h2sPP < 0.1) {
      return "Moderate sour";
    } else {
      return "Severe sour";
    }
  }

  /**
   * Checks whether the model is within the applicable range per NORSOK M-506.
   *
   * @return map of range checks with parameter name to boolean (true = within range)
   */
  public Map<String, Boolean> checkApplicableRange() {
    Map<String, Boolean> checks = new LinkedHashMap<String, Boolean>();
    checks.put("temperature_5_to_150C", temperatureC >= 5.0 && temperatureC <= 150.0);
    checks.put("co2PP_up_to_10bar", getCO2PartialPressureBar() <= 10.0);
    double effPH = (actualPH > 0) ? actualPH : calculatedPH;
    checks.put("pH_3.5_to_6.5", effPH >= 3.5 && effPH <= 6.5);
    checks.put("pressure_up_to_1000bar", totalPressureBara <= 1000.0);
    return checks;
  }

  /**
   * Calculates corrosion rate over a temperature range for parameter study.
   *
   * @param minTempC minimum temperature in Celsius
   * @param maxTempC maximum temperature in Celsius
   * @param steps number of temperature steps
   * @return list of maps with temperature and corrosion results at each step
   */
  public List<Map<String, Object>> runTemperatureSweep(double minTempC, double maxTempC,
      int steps) {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    double savedTemp = this.temperatureC;

    double dT = (maxTempC - minTempC) / Math.max(1, steps);
    for (int i = 0; i <= steps; i++) {
      double temp = minTempC + i * dT;
      this.temperatureC = temp;
      this.hasBeenCalculated = false;
      calculate();

      Map<String, Object> point = new LinkedHashMap<String, Object>();
      point.put("temperature_C", temp);
      point.put("co2Fugacity_bar", co2FugacityBar);
      point.put("pH", getEffectivePH());
      point.put("scalingTemperature_C", scalingTemperatureC);
      point.put("baselineRate_mmyr", baselineCorrosionRate);
      point.put("correctedRate_mmyr", correctedCorrosionRate);
      point.put("severity", getCorrosionSeverity());
      results.add(point);
    }

    this.temperatureC = savedTemp;
    this.hasBeenCalculated = false;
    calculate();
    return results;
  }

  /**
   * Calculates corrosion rate over a pressure range for parameter study.
   *
   * @param minPressure minimum total pressure in bara
   * @param maxPressure maximum total pressure in bara
   * @param steps number of pressure steps
   * @return list of maps with pressure and corrosion results at each step
   */
  public List<Map<String, Object>> runPressureSweep(double minPressure, double maxPressure,
      int steps) {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    double savedPressure = this.totalPressureBara;

    double dP = (maxPressure - minPressure) / Math.max(1, steps);
    for (int i = 0; i <= steps; i++) {
      double pressure = minPressure + i * dP;
      this.totalPressureBara = pressure;
      this.hasBeenCalculated = false;
      calculate();

      Map<String, Object> point = new LinkedHashMap<String, Object>();
      point.put("totalPressure_bara", pressure);
      point.put("co2PartialPressure_bar", getCO2PartialPressureBar());
      point.put("co2Fugacity_bar", co2FugacityBar);
      point.put("pH", getEffectivePH());
      point.put("baselineRate_mmyr", baselineCorrosionRate);
      point.put("correctedRate_mmyr", correctedCorrosionRate);
      point.put("severity", getCorrosionSeverity());
      results.add(point);
    }

    this.totalPressureBara = savedPressure;
    this.hasBeenCalculated = false;
    calculate();
    return results;
  }

  // --- JSON output ---

  /**
   * Returns all input parameters and calculated results as a map.
   *
   * @return map of all parameters and results
   */
  public Map<String, Object> toMap() {
    ensureCalculated();

    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input conditions
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("temperature_C", temperatureC);
    inputs.put("totalPressure_bara", totalPressureBara);
    inputs.put("co2MoleFraction", co2MoleFraction);
    inputs.put("co2PartialPressure_bar", getCO2PartialPressureBar());
    inputs.put("h2sMoleFraction", h2sMoleFraction);
    inputs.put("h2sPartialPressure_bar", getH2SPartialPressureBar());
    inputs.put("flowVelocity_ms", flowVelocityMs);
    inputs.put("pipeDiameter_m", pipeDiameterM);
    inputs.put("liquidDensity_kgm3", liquidDensityKgM3);
    inputs.put("liquidViscosity_Pas", liquidViscosityPas);
    inputs.put("inhibitorEfficiency", inhibitorEfficiency);
    inputs.put("glycolWeightFraction", glycolWeightFraction);
    inputs.put("bicarbonateConcentration_mgL", bicarbonateConcentrationMgL);
    inputs.put("ionicStrength_molL", ionicStrengthMolL);
    if (actualPH > 0) {
      inputs.put("actualPH_specified", actualPH);
    }
    result.put("inputConditions", inputs);

    // Calculated intermediate values
    Map<String, Object> intermediates = new LinkedHashMap<String, Object>();
    intermediates.put("co2FugacityCoefficient", co2FugacityCoeff);
    intermediates.put("co2Fugacity_bar", co2FugacityBar);
    intermediates.put("calculatedEquilibriumPH", calculatedPH);
    intermediates.put("effectivePH", getEffectivePH());
    intermediates.put("scalingTemperature_C", scalingTemperatureC);
    intermediates.put("wallShearStress_Pa", wallShearStressPa);
    result.put("intermediateResults", intermediates);

    // Correction factors
    Map<String, Object> factors = new LinkedHashMap<String, Object>();
    factors.put("phCorrectionFactor_Fpht", phCorrectionFactor);
    factors.put("scaleCorrectionFactor_Fscale", scaleCorrectionFactor);
    factors.put("flowCorrectionFactor", flowCorrectionFactor);
    factors.put("glycolCorrectionFactor_Fglyc", glycolCorrectionFactor);
    factors.put("inhibitorFactor", 1.0 - inhibitorEfficiency);
    result.put("correctionFactors", factors);

    // Corrosion rates
    Map<String, Object> rates = new LinkedHashMap<String, Object>();
    rates.put("baselineRate_mmyr", baselineCorrosionRate);
    rates.put("correctedRate_mmyr", correctedCorrosionRate);
    rates.put("severity", getCorrosionSeverity());
    rates.put("corrosionAllowance_25yr_mm", calculateCorrosionAllowance(25.0));
    result.put("corrosionRates", rates);

    // H2S assessment
    Map<String, Object> h2sAssess = new LinkedHashMap<String, Object>();
    h2sAssess.put("isSourService", isSourService());
    h2sAssess.put("sourSeverity", getSourSeverityClassification());
    result.put("h2sAssessment", h2sAssess);

    // Range check
    result.put("applicableRangeChecks", checkApplicableRange());

    // Model info
    Map<String, Object> modelInfo = new LinkedHashMap<String, Object>();
    modelInfo.put("standard", "NORSOK M-506 (2005/2017)");
    modelInfo.put("title", "CO2 Corrosion Rate Calculation Model");
    modelInfo.put("baseModel", "de Waard-Milliams-Lotz (1991/1993)");
    modelInfo.put("applicableMaterial", "Carbon steel");
    modelInfo.put("temperatureRange_C", "5 to 150");
    modelInfo.put("co2PPRange_bar", "up to 10");
    modelInfo.put("pHRange", "3.5 to 6.5");
    result.put("modelInfo", modelInfo);

    return result;
  }

  /**
   * Returns a comprehensive JSON report of input conditions and calculated results.
   *
   * @return JSON string with all parameters and results
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(toMap());
  }

  /**
   * Ensures the model has been calculated, running calculate() if necessary.
   */
  private void ensureCalculated() {
    if (!hasBeenCalculated) {
      calculate();
    }
  }
}
