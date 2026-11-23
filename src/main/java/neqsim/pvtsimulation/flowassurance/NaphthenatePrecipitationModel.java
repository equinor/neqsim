package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Equilibrium model for precipitation of organic acid (naphthenate) salts from crude oil in contact with produced
 * water.
 *
 * <p>
 * Two acid classes are followed:
 * </p>
 * <ul>
 * <li><b>Monoprotic naphthenic acids (HA)</b>, quantified by the total acid number (TAN). They partition between oil
 * and water, dissociate in water and can form a calcium carboxylate soap CaA2. The dissociated acid in the water is
 * also the driver for sodium carboxylate soap emulsions.</li>
 * <li><b>Tetraprotic ARN acids (H4A)</b>, the C80 tetraacids responsible for hard calcium naphthenate deposits. They
 * stay in the oil and react at the oil-water interface with calcium.</li>
 * </ul>
 *
 * <h2>Equilibria</h2>
 *
 * <pre>
 * HA(oil)  = HA(water)                   P     = [HA]oil / [HA]water
 * HA(water) = H+ + A-                    Ka    = a(H+) a(A-) / [HA]water
 * CaA2(s)  = Ca2+ + 2 A-                 Ksp   = a(Ca2+) a(A-)^2
 * H4A(oil) + 2 Ca2+ = Ca2A(s) + 4 H+     K_ARN = a(H+)^4 / ([H4A]oil a(Ca2+)^2)
 * CO2(g) + H2O = H+ + HCO3-              K1 KH = a(H+) a(HCO3-) / p(CO2)
 * </pre>
 *
 * <p>
 * The monoacid part has the structure of the thermodynamic naphthenate model of Mohammed and Sorbie (2010). Ion
 * activity coefficients are from the Davies equation. The water pH is either given, or calculated from the CO2 partial
 * pressure and the bicarbonate alkalinity of the brine; in the latter case the protons released by acid dissociation
 * and salt formation consume alkalinity, so the pH is solved together with the precipitation. The CO2 route describes
 * the main field mechanism: pressure reduction strips CO2 from the water, the pH rises and the acids dissociate.
 * </p>
 *
 * <h2>Predictive and calibrated use</h2>
 * <p>
 * The default parameters are screening values. The dissociation constant (pKa 4.9) is a measured property of naphthenic
 * acids (Havre et al., 2003). The partition coefficient, the solubility product of the calcium soap and the ARN
 * constant are not universal: they depend on the acid structure distribution of the crude and are set to defaults that
 * reproduce typical field onset behaviour (see the setter documentation). Until parameters are regressed to bottle-test
 * or field data with {@link NaphthenateModelFitting}, results are a ranking and sensitivity tool, and
 * {@link #isCalibrated()} returns false.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Mohammed, M.A., Sorbie, K.S. (2010). Thermodynamic modelling of calcium naphthenate formation: model predictions
 * and experimental results. Colloids and Surfaces A, 369, 1-10.</li>
 * <li>Havre, T.E., Sjoblom, J., Vindstad, J.E. (2003). Oil/water-partitioning and interfacial behavior of naphthenic
 * acids. J. Dispersion Sci. Technol., 24(6), 789-801.</li>
 * <li>Sjoblom, J., Simon, S., Xu, Z. (2014). The chemistry of tetrameric acids in petroleum. Adv. Colloid Interface
 * Sci., 205, 319-338.</li>
 * <li>Turner, M.S., Smith, P.C. (2005). Controls on soap scale formation, including naphthenate soaps - drivers and
 * mitigation. SPE 94339.</li>
 * <li>Plummer, L.N., Busenberg, E. (1982). The solubilities of calcite, aragonite and vaterite in CO2-H2O solutions.
 * Geochim. Cosmochim. Acta, 46, 1011-1040.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class NaphthenatePrecipitationModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Gas constant in J/(mol K). */
  private static final double R_GAS = 8.314462618;
  /** Mass of KOH that neutralises one mole of acid (mg/mol). */
  private static final double KOH_MG_PER_MOL = 56105.6;
  /** Molar mass of calcium (g/mol). */
  private static final double CALCIUM_MOLAR_MASS = 40.078;
  /** Molar mass of sodium (g/mol). */
  private static final double SODIUM_MOLAR_MASS = 22.990;
  /** Molar mass of bicarbonate (g/mol). */
  private static final double BICARBONATE_MOLAR_MASS = 61.017;
  /** Molar mass of hydrogen (g/mol). */
  private static final double HYDROGEN_MOLAR_MASS = 1.008;
  /** Reference temperature for the temperature dependent constants (K). */
  private static final double REFERENCE_TEMPERATURE = 298.15;

  /**
   * Adjustable model parameters.
   */
  public enum Parameter {
    /** Negative logarithm of the dissociation constant of the monoprotic acids in water. */
    PKA,
    /** Base-10 logarithm of the oil/water partition coefficient of the undissociated monoprotic acid. */
    LOG_PARTITION_COEFFICIENT,
    /** Negative logarithm of the solubility product of the calcium soap CaA2 at 298.15 K. */
    PKSP_CALCIUM_SOAP,
    /** Negative logarithm of the ARN calcium naphthenate formation constant at 298.15 K. */
    PK_ARN,
    /** Dissolution enthalpy of the calcium soap (J/mol), temperature dependence of its solubility product. */
    CALCIUM_SOAP_DISSOLUTION_ENTHALPY,
    /** Reaction enthalpy of ARN calcium naphthenate formation (J/mol). */
    ARN_FORMATION_ENTHALPY
  }

  /**
   * Risk classification of naphthenate and soap formation.
   */
  public enum NaphthenateRisk {
    /** Water pH is more than 0.5 units below every onset pH. */
    NO_RISK("No naphthenate or soap formation expected"),
    /** Water pH is within 0.5 units below an onset pH. */
    LOW_RISK("Close to onset - sensitive to pH increase from CO2 flashing"),
    /** Calcium carboxylate soap of monoprotic acids is supersaturated. */
    SOAP_RISK("Calcium carboxylate soap or sludge formation, emulsion stabilisation"),
    /** ARN calcium naphthenate is supersaturated. */
    ARN_DEPOSIT_RISK("ARN calcium naphthenate deposition");

    /** Description of the risk level. */
    private final String description;

    /**
     * Creates a risk level.
     *
     * @param description description of the risk level
     */
    NaphthenateRisk(String description) {
      this.description = description;
    }

    /**
     * Gets the description of the risk level.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }
  }

  // ---- input ----
  /** Oil volume (m3). */
  private double oilVolume = 0.5;
  /** Water volume (m3). */
  private double waterVolume = 0.5;
  /** Oil density (kg/m3). */
  private double oilDensity = 850.0;
  /** Total acid number of the oil (mg KOH/g). */
  private double totalAcidNumber = 1.0;
  /** Mean molar mass of the monoprotic acids (g/mol). */
  private double acidMolarMass = 300.0;
  /** ARN tetraacid concentration in the oil (mg/kg). */
  private double arnConcentration = 0.0;
  /** Molar mass of the ARN tetraacid (g/mol). */
  private double arnMolarMass = 1230.0;
  /** Calcium concentration of the water (mg/L). */
  private double calciumConcentration = 1000.0;
  /** Sodium concentration of the water (mg/L). */
  private double sodiumConcentration = 10000.0;
  /** Bicarbonate alkalinity of the water before contact with the oil acids (mg/L as HCO3-). */
  private double bicarbonateConcentration = 500.0;
  /** User defined ionic strength (mol/L), NaN to estimate it from the ion concentrations. */
  private double userIonicStrength = Double.NaN;
  /** Temperature (K). */
  private double temperature = 333.15;
  /** Given water pH, used when {@link #phFromCO2} is false. */
  private double specifiedPH = 6.0;
  /** Whether the pH is calculated from the CO2 partial pressure and the alkalinity. */
  private boolean phFromCO2 = false;
  /** CO2 partial pressure (bara). */
  private double co2PartialPressure = 1.0;

  // ---- parameters ----
  /** Negative logarithm of the acid dissociation constant. */
  private double pKa = 4.9;
  /** Base-10 logarithm of the oil/water partition coefficient, NaN to use the molar mass correlation. */
  private double logPartitionCoefficient = Double.NaN;
  /** Negative logarithm of the calcium soap solubility product at 298.15 K. */
  private double pKspCalciumSoap = 10.0;
  /** Negative logarithm of the ARN formation constant at 298.15 K. */
  private double pKArn = 14.4;
  /** Dissolution enthalpy of the calcium soap (J/mol). */
  private double calciumSoapDissolutionEnthalpy = 0.0;
  /** Reaction enthalpy of the ARN calcium naphthenate formation (J/mol). */
  private double arnFormationEnthalpy = 0.0;
  /** Whether parameters were regressed to data. */
  private boolean calibrated = false;

  // ---- results ----
  /** Whether {@link #run()} has been called for the current input. */
  private boolean solved = false;
  /** Equilibrium water pH. */
  private double waterPH = Double.NaN;
  /** Water pH without salt precipitation. */
  private double waterPHBeforePrecipitation = Double.NaN;
  /** Saturation ratio of the calcium soap before precipitation. */
  private double calciumSoapSaturationRatio = Double.NaN;
  /** Saturation ratio of the ARN calcium naphthenate before precipitation. */
  private double arnSaturationRatio = Double.NaN;
  /** Precipitated calcium soap (mol CaA2). */
  private double precipitatedCalciumSoapMoles = 0.0;
  /** Precipitated ARN calcium naphthenate (mol Ca2A). */
  private double precipitatedArnMoles = 0.0;
  /** Dissociated acid concentration in the water at equilibrium (mol/L). */
  private double aqueousNaphthenateMolarity = 0.0;
  /** Undissociated acid concentration in the water at equilibrium (mol/L). */
  private double aqueousAcidMolarity = 0.0;
  /** Free calcium concentration in the water at equilibrium (mol/L). */
  private double freeCalciumMolarity = 0.0;
  /** Ionic strength used for the activity coefficients (mol/L). */
  private double ionicStrength = 0.0;
  /** Warnings from the last calculation. */
  private List<String> warnings = new ArrayList<String>();

  /**
   * Speciation at one pH.
   */
  private static final class Speciation {
    /** Precipitated calcium soap (mol). */
    private double soapMoles;
    /** Precipitated ARN salt (mol). */
    private double arnMoles;
    /** Dissociated acid in the water (mol/L). */
    private double naphthenate;
    /** Undissociated acid in the water (mol/L). */
    private double acidInWater;
    /** Free calcium (mol/L). */
    private double calcium;
    /** Saturation ratio of the calcium soap at this state. */
    private double soapSaturation;
    /** Saturation ratio of the ARN salt at this state. */
    private double arnSaturation;
  }

  /**
   * Creates a model with default screening parameters.
   */
  public NaphthenatePrecipitationModel() {
  }

  /**
   * Creates an independent copy of the model input and parameters. Results are not copied.
   *
   * @return copy of this model
   */
  public NaphthenatePrecipitationModel copy() {
    NaphthenatePrecipitationModel other = new NaphthenatePrecipitationModel();
    other.oilVolume = oilVolume;
    other.waterVolume = waterVolume;
    other.oilDensity = oilDensity;
    other.totalAcidNumber = totalAcidNumber;
    other.acidMolarMass = acidMolarMass;
    other.arnConcentration = arnConcentration;
    other.arnMolarMass = arnMolarMass;
    other.calciumConcentration = calciumConcentration;
    other.sodiumConcentration = sodiumConcentration;
    other.bicarbonateConcentration = bicarbonateConcentration;
    other.userIonicStrength = userIonicStrength;
    other.temperature = temperature;
    other.specifiedPH = specifiedPH;
    other.phFromCO2 = phFromCO2;
    other.co2PartialPressure = co2PartialPressure;
    other.pKa = pKa;
    other.logPartitionCoefficient = logPartitionCoefficient;
    other.pKspCalciumSoap = pKspCalciumSoap;
    other.pKArn = pKArn;
    other.calciumSoapDissolutionEnthalpy = calciumSoapDissolutionEnthalpy;
    other.arnFormationEnthalpy = arnFormationEnthalpy;
    other.calibrated = calibrated;
    return other;
  }

  // ------------------------------------------------------------------ correlations

  /**
   * Estimates the oil/water partition coefficient of an undissociated monoprotic naphthenic acid from its molar mass.
   *
   * <p>
   * log10 P = 4.0 + 0.010 (M - 300). This is an engineering default, not a published correlation: it places the pH of
   * half extraction of a 300 g/mol acid into an equal volume of water at about pH 9, in line with the general
   * observation that C15-C20 naphthenic acids transfer to water only at alkaline pH, and makes heavier acids more oil
   * soluble. Replace it by a regressed value when partition data exist.
   * </p>
   *
   * @param molarMass acid molar mass (g/mol)
   * @return base-10 logarithm of the oil/water partition coefficient
   */
  public static double estimateLogPartitionCoefficient(double molarMass) {
    return 4.0 + 0.010 * (molarMass - 300.0);
  }

  /**
   * Calculates the Henry constant of CO2 in water.
   *
   * @param temperatureK temperature (K)
   * @return base-10 logarithm of the Henry constant (mol/(kg atm))
   */
  public static double log10HenryConstantCO2(double temperatureK) {
    return 108.3865 + 0.01985076 * temperatureK - 6919.53 / temperatureK - 40.45154 * Math.log10(temperatureK)
        + 669365.0 / (temperatureK * temperatureK);
  }

  /**
   * Calculates the first dissociation constant of carbonic acid.
   *
   * @param temperatureK temperature (K)
   * @return base-10 logarithm of the first dissociation constant
   */
  public static double log10FirstDissociationConstantCO2(double temperatureK) {
    return -356.3094 - 0.06091964 * temperatureK + 21834.37 / temperatureK + 126.8339 * Math.log10(temperatureK)
        - 1684915.0 / (temperatureK * temperatureK);
  }

  /**
   * Calculates an ion activity coefficient with the Davies equation.
   *
   * @param charge ion charge number
   * @param ionicStrengthMolar ionic strength (mol/L)
   * @param temperatureK temperature (K)
   * @return activity coefficient
   */
  public static double daviesActivityCoefficient(int charge, double ionicStrengthMolar, double temperatureK) {
    double limited = Math.max(0.0, Math.min(ionicStrengthMolar, 1.0));
    double debyeHuckelA = 0.4883 + 8.074e-4 * (temperatureK - 273.15);
    double root = Math.sqrt(limited);
    return Math.pow(10.0, -debyeHuckelA * charge * charge * (root / (1.0 + root) - 0.3 * limited));
  }

  // ------------------------------------------------------------------ calculation

  /**
   * Calculates the equilibrium state.
   *
   * @throws IllegalStateException if the input is not physically meaningful
   */
  public void run() {
    validateInput();
    warnings = new ArrayList<String>();
    ionicStrength = calculateIonicStrength();
    if (ionicStrength > 0.5) {
      warnings.add("Ionic strength " + round(ionicStrength, 3)
          + " mol/L is above the validity of the Davies equation (0.5 mol/L); activity coefficients are approximate.");
    }
    if (!calibrated) {
      warnings.add("Default screening parameters in use; regress the parameters to bottle-test or field data with"
          + " NaphthenateModelFitting before quantitative use.");
    }

    Speciation initial;
    Speciation equilibrium;
    if (phFromCO2) {
      waterPHBeforePrecipitation = solveCO2BufferedPH(false);
      initial = calculateSpeciation(waterPHBeforePrecipitation, false);
      waterPH = solveCO2BufferedPH(true);
      equilibrium = calculateSpeciation(waterPH, true);
    } else {
      waterPHBeforePrecipitation = specifiedPH;
      waterPH = specifiedPH;
      initial = calculateSpeciation(specifiedPH, false);
      equilibrium = calculateSpeciation(specifiedPH, true);
    }
    calciumSoapSaturationRatio = initial.soapSaturation;
    arnSaturationRatio = initial.arnSaturation;
    precipitatedCalciumSoapMoles = equilibrium.soapMoles;
    precipitatedArnMoles = equilibrium.arnMoles;
    aqueousNaphthenateMolarity = equilibrium.naphthenate;
    aqueousAcidMolarity = equilibrium.acidInWater;
    freeCalciumMolarity = equilibrium.calcium;
    solved = true;
  }

  /**
   * Checks the input.
   *
   * @throws IllegalStateException if a value is outside its physical range
   */
  private void validateInput() {
    if (!(oilVolume > 0.0) || !(waterVolume > 0.0)) {
      throw new IllegalStateException("Oil and water volumes must be positive");
    }
    if (!(oilDensity > 0.0) || !(acidMolarMass > 0.0) || !(arnMolarMass > 0.0) || !(temperature > 0.0)) {
      throw new IllegalStateException("Oil density, molar masses and temperature must be positive");
    }
    if (totalAcidNumber < 0.0 || arnConcentration < 0.0 || calciumConcentration < 0.0 || sodiumConcentration < 0.0
        || bicarbonateConcentration < 0.0) {
      throw new IllegalStateException("Concentrations must not be negative");
    }
    if (phFromCO2 && (!(co2PartialPressure > 0.0) || !(bicarbonateConcentration > 0.0))) {
      throw new IllegalStateException(
          "CO2 partial pressure and bicarbonate alkalinity must be positive when the pH is calculated from CO2");
    }
  }

  /**
   * Gets the ionic strength, either given or estimated from sodium, calcium and bicarbonate with chloride closing the
   * charge balance.
   *
   * @return ionic strength (mol/L)
   */
  private double calculateIonicStrength() {
    if (!Double.isNaN(userIonicStrength)) {
      return userIonicStrength;
    }
    double sodium = sodiumConcentration / 1000.0 / SODIUM_MOLAR_MASS;
    double calcium = calciumConcentration / 1000.0 / CALCIUM_MOLAR_MASS;
    double bicarbonate = bicarbonateConcentration / 1000.0 / BICARBONATE_MOLAR_MASS;
    double chloride = Math.max(0.0, sodium + 2.0 * calcium - bicarbonate);
    return 0.5 * (sodium + 4.0 * calcium + bicarbonate + chloride);
  }

  /**
   * Gets the total amount of monoprotic acid.
   *
   * @return amount of acid (mol)
   */
  private double totalAcidMoles() {
    return totalAcidNumber / KOH_MG_PER_MOL * 1000.0 * oilDensity * oilVolume;
  }

  /**
   * Gets the total amount of ARN tetraacid.
   *
   * @return amount of ARN (mol)
   */
  private double totalArnMoles() {
    return arnConcentration * 1.0e-3 / arnMolarMass * oilDensity * oilVolume;
  }

  /**
   * Applies the van't Hoff temperature dependence to an equilibrium constant given at 298.15 K.
   *
   * @param log10AtReference base-10 logarithm of the constant at 298.15 K
   * @param enthalpy reaction enthalpy (J/mol)
   * @return base-10 logarithm of the constant at the model temperature
   */
  private double vantHoff(double log10AtReference, double enthalpy) {
    return log10AtReference - enthalpy / (R_GAS * Math.log(10.0)) * (1.0 / temperature - 1.0 / REFERENCE_TEMPERATURE);
  }

  /**
   * Calculates the distribution of the acids and the precipitated salts at a given pH.
   *
   * @param pH water pH
   * @param allowPrecipitation true to precipitate supersaturated salts, false for the metastable state
   * @return speciation at the pH
   */
  private Speciation calculateSpeciation(double pH, boolean allowPrecipitation) {
    final double gamma1 = daviesActivityCoefficient(1, ionicStrength, temperature);
    final double gamma2 = daviesActivityCoefficient(2, ionicStrength, temperature);
    final double hydrogenActivity = Math.pow(10.0, -pH);
    final double oilLitres = oilVolume * 1000.0;
    final double waterLitres = waterVolume * 1000.0;
    final double partition = Math.pow(10.0, getLogPartitionCoefficient());
    final double dissociation = Math.pow(10.0, -pKa) / (hydrogenActivity * gamma1);
    final double acidCapacity = oilLitres + waterLitres / partition * (1.0 + dissociation);
    final double soapSolubilityProduct = Math.pow(10.0, vantHoff(-pKspCalciumSoap, calciumSoapDissolutionEnthalpy));
    final double arnConstant = Math.pow(10.0, vantHoff(-pKArn, arnFormationEnthalpy));
    final double acidMoles = totalAcidMoles();
    final double arnMoles = totalArnMoles();
    final double calciumMoles = calciumConcentration / 1000.0 / CALCIUM_MOLAR_MASS * waterLitres;
    // a(A-)^2 per (mol acid in the system)^2
    final double naphthenatePerAcid = dissociation / partition / acidCapacity;
    final double arnDriving = gamma2 * gamma2 * arnConstant / Math.pow(hydrogenActivity, 4.0);

    Speciation state = new Speciation();
    state.soapSaturation = gamma2 * (calciumMoles / waterLitres)
        * Math.pow(gamma1 * naphthenatePerAcid * acidMoles, 2.0) / soapSolubilityProduct;
    state.arnSaturation = arnMoles / oilLitres * Math.pow(calciumMoles / waterLitres, 2.0) * arnDriving;

    double soap = 0.0;
    double arn = 0.0;
    if (allowPrecipitation) {
      for (int iteration = 0; iteration < 200; iteration++) {
        double previousSoap = soap;
        double previousArn = arn;
        arn = solveArnPrecipitation(arnMoles, calciumMoles - soap, oilLitres, waterLitres, arnDriving);
        soap = solveSoapPrecipitation(acidMoles, calciumMoles - 2.0 * arn, waterLitres,
            gamma2 * gamma1 * gamma1 * naphthenatePerAcid * naphthenatePerAcid / soapSolubilityProduct);
        if (Math.abs(soap - previousSoap) <= 1.0e-12 * Math.max(soap, 1.0e-30)
            && Math.abs(arn - previousArn) <= 1.0e-12 * Math.max(arn, 1.0e-30)) {
          break;
        }
      }
    }
    state.soapMoles = soap;
    state.arnMoles = arn;
    state.calcium = Math.max(0.0, calciumMoles - soap - 2.0 * arn) / waterLitres;
    state.naphthenate = naphthenatePerAcid * (acidMoles - 2.0 * soap);
    state.acidInWater = (acidMoles - 2.0 * soap) / acidCapacity / partition;
    return state;
  }

  /**
   * Solves the amount of ARN calcium naphthenate that brings the ARN salt to saturation.
   *
   * @param arnMoles total ARN (mol)
   * @param calciumMoles calcium available to ARN (mol)
   * @param oilLitres oil volume (L)
   * @param waterLitres water volume (L)
   * @param arnDriving activity and pH factor multiplying [H4A][Ca]^2 in the saturation ratio
   * @return precipitated ARN salt (mol Ca2A)
   */
  private static double solveArnPrecipitation(double arnMoles, double calciumMoles, double oilLitres,
      double waterLitres, double arnDriving) {
    double upper = Math.min(arnMoles, calciumMoles / 2.0);
    if (!(upper > 0.0)) {
      return 0.0;
    }
    double lower = 0.0;
    if (arnSaturationAt(lower, arnMoles, calciumMoles, oilLitres, waterLitres, arnDriving) <= 1.0) {
      return 0.0;
    }
    for (int i = 0; i < 200; i++) {
      double mid = 0.5 * (lower + upper);
      if (arnSaturationAt(mid, arnMoles, calciumMoles, oilLitres, waterLitres, arnDriving) > 1.0) {
        lower = mid;
      } else {
        upper = mid;
      }
      if (upper - lower <= 1.0e-14 * upper) {
        break;
      }
    }
    return 0.5 * (lower + upper);
  }

  /**
   * Calculates the ARN saturation ratio after a given amount has precipitated.
   *
   * @param precipitated precipitated ARN salt (mol)
   * @param arnMoles total ARN (mol)
   * @param calciumMoles calcium available to ARN (mol)
   * @param oilLitres oil volume (L)
   * @param waterLitres water volume (L)
   * @param arnDriving activity and pH factor
   * @return saturation ratio
   */
  private static double arnSaturationAt(double precipitated, double arnMoles, double calciumMoles, double oilLitres,
      double waterLitres, double arnDriving) {
    double calcium = (calciumMoles - 2.0 * precipitated) / waterLitres;
    return (arnMoles - precipitated) / oilLitres * calcium * calcium * arnDriving;
  }

  /**
   * Solves the amount of calcium soap that brings CaA2 to saturation.
   *
   * @param acidMoles total monoprotic acid (mol)
   * @param calciumMoles calcium available to the soap (mol)
   * @param waterLitres water volume (L)
   * @param soapDriving factor multiplying [Ca] (mol acid)^2 in the saturation ratio
   * @return precipitated calcium soap (mol CaA2)
   */
  private static double solveSoapPrecipitation(double acidMoles, double calciumMoles, double waterLitres,
      double soapDriving) {
    double upper = Math.min(acidMoles / 2.0, calciumMoles);
    if (!(upper > 0.0)) {
      return 0.0;
    }
    double lower = 0.0;
    if (soapSaturationAt(lower, acidMoles, calciumMoles, waterLitres, soapDriving) <= 1.0) {
      return 0.0;
    }
    for (int i = 0; i < 200; i++) {
      double mid = 0.5 * (lower + upper);
      if (soapSaturationAt(mid, acidMoles, calciumMoles, waterLitres, soapDriving) > 1.0) {
        lower = mid;
      } else {
        upper = mid;
      }
      if (upper - lower <= 1.0e-14 * upper) {
        break;
      }
    }
    return 0.5 * (lower + upper);
  }

  /**
   * Calculates the calcium soap saturation ratio after a given amount has precipitated.
   *
   * @param precipitated precipitated calcium soap (mol)
   * @param acidMoles total monoprotic acid (mol)
   * @param calciumMoles calcium available to the soap (mol)
   * @param waterLitres water volume (L)
   * @param soapDriving factor multiplying [Ca] (mol acid)^2
   * @return saturation ratio
   */
  private static double soapSaturationAt(double precipitated, double acidMoles, double calciumMoles, double waterLitres,
      double soapDriving) {
    double acid = acidMoles - 2.0 * precipitated;
    return (calciumMoles - precipitated) / waterLitres * acid * acid * soapDriving;
  }

  /**
   * Calculates the pH of water in equilibrium with CO2 for a given free bicarbonate concentration.
   *
   * @param bicarbonateMolarity free bicarbonate concentration (mol/L)
   * @return pH
   */
  private double carbonatePH(double bicarbonateMolarity) {
    double gamma1 = daviesActivityCoefficient(1, ionicStrength, temperature);
    return -(log10FirstDissociationConstantCO2(temperature) + log10HenryConstantCO2(temperature)
        + Math.log10(co2PartialPressure / 1.01325)) + Math.log10(gamma1 * bicarbonateMolarity);
  }

  /**
   * Solves the water pH at a fixed CO2 partial pressure, with the alkalinity reduced by the dissociated and
   * precipitated acids.
   *
   * @param allowPrecipitation true to include salt precipitation
   * @return water pH
   */
  private double solveCO2BufferedPH(boolean allowPrecipitation) {
    double alkalinity = bicarbonateConcentration / 1000.0 / BICARBONATE_MOLAR_MASS;
    double waterLitres = waterVolume * 1000.0;
    double low = 0.0;
    double high = carbonatePH(alkalinity);
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (low + high);
      Speciation state = calculateSpeciation(mid, allowPrecipitation);
      double consumed = state.naphthenate + (2.0 * state.soapMoles + 4.0 * state.arnMoles) / waterLitres;
      double freeBicarbonate = alkalinity - consumed;
      // the pH of the carbonate system falls when the trial pH rises, so the residual is monotonic
      if (freeBicarbonate <= 0.0 || mid > carbonatePH(freeBicarbonate)) {
        high = mid;
      } else {
        low = mid;
      }
      if (high - low < 1.0e-10) {
        break;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Calculates the water pH above which ARN calcium naphthenate is supersaturated, at the calcium and ARN
   * concentrations of the input.
   *
   * @return onset pH, or NaN when there is no ARN or no calcium
   */
  public double calculateArnOnsetPH() {
    validateInput();
    double arnMolarity = totalArnMoles() / (oilVolume * 1000.0);
    double calcium = calciumConcentration / 1000.0 / CALCIUM_MOLAR_MASS;
    if (!(arnMolarity > 0.0) || !(calcium > 0.0)) {
      return Double.NaN;
    }
    double gamma2 = daviesActivityCoefficient(2, calculateIonicStrength(), temperature);
    return 0.25
        * (-vantHoff(-pKArn, arnFormationEnthalpy) - Math.log10(arnMolarity) - 2.0 * Math.log10(gamma2 * calcium));
  }

  /**
   * Calculates the water pH above which the calcium soap of the monoprotic acids is supersaturated.
   *
   * @return onset pH, or NaN when the soap is undersaturated up to pH 14 or there is no acid or calcium
   */
  public double calculateCalciumSoapOnsetPH() {
    validateInput();
    if (!(totalAcidNumber > 0.0) || !(calciumConcentration > 0.0)) {
      return Double.NaN;
    }
    double savedIonicStrength = ionicStrength;
    ionicStrength = calculateIonicStrength();
    try {
      double low = 0.0;
      double high = 14.0;
      if (calculateSpeciation(high, false).soapSaturation < 1.0) {
        return Double.NaN;
      }
      if (calculateSpeciation(low, false).soapSaturation >= 1.0) {
        return low;
      }
      for (int i = 0; i < 60; i++) {
        double mid = 0.5 * (low + high);
        if (calculateSpeciation(mid, false).soapSaturation >= 1.0) {
          high = mid;
        } else {
          low = mid;
        }
      }
      return 0.5 * (low + high);
    } finally {
      ionicStrength = savedIonicStrength;
    }
  }

  /**
   * Classifies the naphthenate risk of the calculated state.
   *
   * @return risk level
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public NaphthenateRisk getRisk() {
    requireSolved();
    if (arnSaturationRatio > 1.0) {
      return NaphthenateRisk.ARN_DEPOSIT_RISK;
    }
    if (calciumSoapSaturationRatio > 1.0) {
      return NaphthenateRisk.SOAP_RISK;
    }
    double arnOnset = calculateArnOnsetPH();
    double soapOnset = calculateCalciumSoapOnsetPH();
    boolean nearArn = !Double.isNaN(arnOnset) && waterPHBeforePrecipitation > arnOnset - 0.5;
    boolean nearSoap = !Double.isNaN(soapOnset) && waterPHBeforePrecipitation > soapOnset - 0.5;
    return nearArn || nearSoap ? NaphthenateRisk.LOW_RISK : NaphthenateRisk.NO_RISK;
  }

  /**
   * Checks that results exist.
   *
   * @throws IllegalStateException if {@link #run()} has not been called since the last input change
   */
  private void requireSolved() {
    if (!solved) {
      throw new IllegalStateException("Call run() before reading results");
    }
  }

  /**
   * Rounds a value for messages.
   *
   * @param value value to round
   * @param decimals number of decimals
   * @return rounded value
   */
  private static double round(double value, int decimals) {
    double scale = Math.pow(10.0, decimals);
    return Math.round(value * scale) / scale;
  }

  // ------------------------------------------------------------------ results

  /**
   * Gets the equilibrium water pH.
   *
   * @return water pH
   */
  public double getWaterPH() {
    requireSolved();
    return waterPH;
  }

  /**
   * Gets the water pH before salt precipitation. Equal to the equilibrium pH when the pH is specified.
   *
   * @return water pH without precipitation
   */
  public double getWaterPHBeforePrecipitation() {
    requireSolved();
    return waterPHBeforePrecipitation;
  }

  /**
   * Gets the saturation ratio of the calcium soap CaA2 before precipitation.
   *
   * @return ion activity product divided by the solubility product
   */
  public double getCalciumSoapSaturationRatio() {
    requireSolved();
    return calciumSoapSaturationRatio;
  }

  /**
   * Gets the saturation ratio of the ARN calcium naphthenate before precipitation.
   *
   * @return saturation ratio, zero without ARN
   */
  public double getArnSaturationRatio() {
    requireSolved();
    return arnSaturationRatio;
  }

  /**
   * Gets the mass of precipitated calcium soap of the monoprotic acids.
   *
   * @return mass per volume of oil (kg/m3 oil)
   */
  public double getPrecipitatedCalciumSoap() {
    requireSolved();
    double molarMass = 2.0 * (acidMolarMass - HYDROGEN_MOLAR_MASS) + CALCIUM_MOLAR_MASS;
    return precipitatedCalciumSoapMoles * molarMass / 1000.0 / oilVolume;
  }

  /**
   * Gets the mass of precipitated ARN calcium naphthenate.
   *
   * @return mass per volume of oil (kg/m3 oil)
   */
  public double getPrecipitatedArnNaphthenate() {
    requireSolved();
    double molarMass = arnMolarMass - 4.0 * HYDROGEN_MOLAR_MASS + 2.0 * CALCIUM_MOLAR_MASS;
    return precipitatedArnMoles * molarMass / 1000.0 / oilVolume;
  }

  /**
   * Gets the fraction of the ARN tetraacid that has precipitated.
   *
   * @return precipitated fraction, zero without ARN
   */
  public double getPrecipitatedArnFraction() {
    requireSolved();
    double total = totalArnMoles();
    return total > 0.0 ? precipitatedArnMoles / total : 0.0;
  }

  /**
   * Gets the fraction of the monoprotic acid that has precipitated as calcium soap.
   *
   * @return precipitated fraction, zero without acid
   */
  public double getPrecipitatedAcidFraction() {
    requireSolved();
    double total = totalAcidMoles();
    return total > 0.0 ? 2.0 * precipitatedCalciumSoapMoles / total : 0.0;
  }

  /**
   * Gets the concentration of organic acid in the water, dissociated and undissociated, at equilibrium. This is the
   * dissolved acid that follows the produced water and stabilises sodium soap emulsions.
   *
   * @return concentration (mg/L water)
   */
  public double getAqueousOrganicAcidConcentration() {
    requireSolved();
    return (aqueousNaphthenateMolarity + aqueousAcidMolarity) * acidMolarMass * 1000.0;
  }

  /**
   * Gets the fraction of the monoprotic acid that is in the water at equilibrium.
   *
   * @return fraction of the acid in the water, zero without acid
   */
  public double getAcidFractionInWater() {
    requireSolved();
    double total = totalAcidMoles();
    return total > 0.0 ? (aqueousNaphthenateMolarity + aqueousAcidMolarity) * waterVolume * 1000.0 / total : 0.0;
  }

  /**
   * Gets the free calcium concentration in the water at equilibrium.
   *
   * @return calcium concentration (mg/L)
   */
  public double getFreeCalciumConcentration() {
    requireSolved();
    return freeCalciumMolarity * CALCIUM_MOLAR_MASS * 1000.0;
  }

  /**
   * Gets the ionic strength used for the activity coefficients.
   *
   * @return ionic strength (mol/L)
   */
  public double getIonicStrength() {
    requireSolved();
    return ionicStrength;
  }

  /**
   * Gets the warnings from the last calculation.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return new ArrayList<String>(warnings);
  }

  /**
   * Returns input, parameters and results as an ordered map.
   *
   * @return map of values
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public Map<String, Object> toMap() {
    requireSolved();
    Map<String, Object> input = new LinkedHashMap<String, Object>();
    input.put("oilVolume_m3", oilVolume);
    input.put("waterVolume_m3", waterVolume);
    input.put("oilDensity_kgm3", oilDensity);
    input.put("totalAcidNumber_mgKOHg", totalAcidNumber);
    input.put("acidMolarMass_gmol", acidMolarMass);
    input.put("arnConcentration_mgkg", arnConcentration);
    input.put("calcium_mgL", calciumConcentration);
    input.put("sodium_mgL", sodiumConcentration);
    input.put("bicarbonate_mgL", bicarbonateConcentration);
    input.put("temperature_K", temperature);
    input.put("pHFromCO2", phFromCO2);
    input.put("co2PartialPressure_bara", phFromCO2 ? co2PartialPressure : null);

    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    parameters.put("pKa", pKa);
    parameters.put("logPartitionCoefficient", getLogPartitionCoefficient());
    parameters.put("pKspCalciumSoap", pKspCalciumSoap);
    parameters.put("pKArn", pKArn);
    parameters.put("calciumSoapDissolutionEnthalpy_Jmol", calciumSoapDissolutionEnthalpy);
    parameters.put("arnFormationEnthalpy_Jmol", arnFormationEnthalpy);
    parameters.put("calibrated", calibrated);

    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("waterPH", waterPH);
    results.put("waterPHBeforePrecipitation", waterPHBeforePrecipitation);
    results.put("ionicStrength_molL", ionicStrength);
    results.put("calciumSoapSaturationRatio", calciumSoapSaturationRatio);
    results.put("arnSaturationRatio", arnSaturationRatio);
    results.put("calciumSoapOnsetPH", finiteOrNull(calculateCalciumSoapOnsetPH()));
    results.put("arnOnsetPH", finiteOrNull(calculateArnOnsetPH()));
    results.put("precipitatedCalciumSoap_kgm3oil", getPrecipitatedCalciumSoap());
    results.put("precipitatedArnNaphthenate_kgm3oil", getPrecipitatedArnNaphthenate());
    results.put("precipitatedArnFraction", getPrecipitatedArnFraction());
    results.put("precipitatedAcidFraction", getPrecipitatedAcidFraction());
    results.put("aqueousOrganicAcid_mgL", getAqueousOrganicAcidConcentration());
    results.put("freeCalcium_mgL", getFreeCalciumConcentration());
    NaphthenateRisk risk = getRisk();
    results.put("risk", risk.name());
    results.put("riskDescription", risk.getDescription());

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("model", "Naphthenate precipitation equilibrium");
    map.put("input", input);
    map.put("parameters", parameters);
    map.put("results", results);
    map.put("warnings", getWarnings());
    return map;
  }

  /**
   * Converts a non-finite number to null so that it can be written as JSON.
   *
   * @param value value to check
   * @return the value, or null when it is NaN or infinite
   */
  private static Double finiteOrNull(double value) {
    return Double.isNaN(value) || Double.isInfinite(value) ? null : Double.valueOf(value);
  }

  /**
   * Returns input, parameters and results as JSON.
   *
   * @return JSON string
   * @throws IllegalStateException if {@link #run()} has not been called
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }

  // ------------------------------------------------------------------ parameters

  /**
   * Gets the value of an adjustable parameter.
   *
   * @param parameter parameter to read
   * @return parameter value
   */
  public double getParameter(Parameter parameter) {
    switch (parameter) {
    case PKA:
      return pKa;
    case LOG_PARTITION_COEFFICIENT:
      return getLogPartitionCoefficient();
    case PKSP_CALCIUM_SOAP:
      return pKspCalciumSoap;
    case PK_ARN:
      return pKArn;
    case CALCIUM_SOAP_DISSOLUTION_ENTHALPY:
      return calciumSoapDissolutionEnthalpy;
    case ARN_FORMATION_ENTHALPY:
      return arnFormationEnthalpy;
    default:
      throw new IllegalArgumentException("Unknown parameter " + parameter);
    }
  }

  /**
   * Sets the value of an adjustable parameter.
   *
   * @param parameter parameter to set
   * @param value new value
   */
  public void setParameter(Parameter parameter, double value) {
    switch (parameter) {
    case PKA:
      pKa = value;
      break;
    case LOG_PARTITION_COEFFICIENT:
      logPartitionCoefficient = value;
      break;
    case PKSP_CALCIUM_SOAP:
      pKspCalciumSoap = value;
      break;
    case PK_ARN:
      pKArn = value;
      break;
    case CALCIUM_SOAP_DISSOLUTION_ENTHALPY:
      calciumSoapDissolutionEnthalpy = value;
      break;
    case ARN_FORMATION_ENTHALPY:
      arnFormationEnthalpy = value;
      break;
    default:
      throw new IllegalArgumentException("Unknown parameter " + parameter);
    }
    solved = false;
  }

  /**
   * Gets the oil/water partition coefficient of the undissociated monoprotic acid.
   *
   * @return base-10 logarithm of [HA]oil / [HA]water; from {@link #estimateLogPartitionCoefficient(double)} unless a
   * value has been set
   */
  public double getLogPartitionCoefficient() {
    return Double.isNaN(logPartitionCoefficient) ? estimateLogPartitionCoefficient(acidMolarMass)
        : logPartitionCoefficient;
  }

  /**
   * Checks whether the parameters have been regressed to data.
   *
   * @return true when marked as calibrated
   */
  public boolean isCalibrated() {
    return calibrated;
  }

  /**
   * Marks the parameters as regressed to data. Set by {@link NaphthenateModelFitting}; call it also after entering
   * parameters from an earlier regression.
   *
   * @param calibrated true when the parameters come from a regression to measurements
   */
  public void setCalibrated(boolean calibrated) {
    this.calibrated = calibrated;
  }

  // ------------------------------------------------------------------ input

  /**
   * Sets the oil and water volumes that are in contact.
   *
   * @param oilVolume oil volume (m3)
   * @param waterVolume water volume (m3)
   */
  public void setPhaseVolumes(double oilVolume, double waterVolume) {
    this.oilVolume = oilVolume;
    this.waterVolume = waterVolume;
    solved = false;
  }

  /**
   * Sets the oil and water volumes from the water cut, for one cubic metre of liquid.
   *
   * @param waterCut water volume fraction of the liquid, between 0 and 1 exclusive
   * @throws IllegalArgumentException if the water cut is not between 0 and 1
   */
  public void setWaterCut(double waterCut) {
    if (!(waterCut > 0.0) || !(waterCut < 1.0)) {
      throw new IllegalArgumentException("Water cut must be between 0 and 1");
    }
    setPhaseVolumes(1.0 - waterCut, waterCut);
  }

  /**
   * Sets the oil density.
   *
   * @param oilDensity oil density (kg/m3)
   */
  public void setOilDensity(double oilDensity) {
    this.oilDensity = oilDensity;
    solved = false;
  }

  /**
   * Sets the total acid number of the oil, which gives the amount of monoprotic acid.
   *
   * @param totalAcidNumber total acid number (mg KOH/g oil)
   */
  public void setTotalAcidNumber(double totalAcidNumber) {
    this.totalAcidNumber = totalAcidNumber;
    solved = false;
  }

  /**
   * Sets the mean molar mass of the monoprotic acids. Typical crude oil naphthenic acids are 200 to 500 g/mol.
   *
   * @param acidMolarMass molar mass (g/mol)
   */
  public void setAcidMolarMass(double acidMolarMass) {
    this.acidMolarMass = acidMolarMass;
    solved = false;
  }

  /**
   * Sets the ARN tetraacid content of the oil. Reported contents are of the order of 1 to 40 mg/kg.
   *
   * @param arnConcentration ARN concentration (mg/kg oil)
   */
  public void setArnConcentration(double arnConcentration) {
    this.arnConcentration = arnConcentration;
    solved = false;
  }

  /**
   * Sets the molar mass of the ARN tetraacid. The default 1230 g/mol is the C80 tetraacid.
   *
   * @param arnMolarMass molar mass (g/mol)
   */
  public void setArnMolarMass(double arnMolarMass) {
    this.arnMolarMass = arnMolarMass;
    solved = false;
  }

  /**
   * Sets the main ion concentrations of the water.
   *
   * @param calcium calcium concentration (mg/L)
   * @param sodium sodium concentration (mg/L)
   * @param bicarbonate bicarbonate alkalinity before contact with the oil acids (mg/L as HCO3-)
   */
  public void setWaterComposition(double calcium, double sodium, double bicarbonate) {
    this.calciumConcentration = calcium;
    this.sodiumConcentration = sodium;
    this.bicarbonateConcentration = bicarbonate;
    solved = false;
  }

  /**
   * Sets the ionic strength used for the activity coefficients. Without this call it is estimated from sodium, calcium
   * and bicarbonate with chloride closing the charge balance.
   *
   * @param ionicStrength ionic strength (mol/L), or NaN to estimate it
   */
  public void setIonicStrength(double ionicStrength) {
    this.userIonicStrength = ionicStrength;
    solved = false;
  }

  /**
   * Sets the temperature.
   *
   * @param temperature temperature (K)
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
    solved = false;
  }

  /**
   * Gets the temperature.
   *
   * @return temperature (K)
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Specifies the equilibrium water pH, for example a measured separator pH or the buffered pH of a bottle test.
   *
   * @param pH water pH
   */
  public void setWaterPH(double pH) {
    this.specifiedPH = pH;
    this.phFromCO2 = false;
    solved = false;
  }

  /**
   * Calculates the water pH from the CO2 partial pressure and the bicarbonate alkalinity given with
   * {@link #setWaterComposition(double, double, double)}. The protons released by the organic acids reduce the
   * alkalinity.
   *
   * @param co2PartialPressure CO2 partial pressure in the gas in contact with the water (bara)
   */
  public void setCO2PartialPressure(double co2PartialPressure) {
    this.co2PartialPressure = co2PartialPressure;
    this.phFromCO2 = true;
    solved = false;
  }
}
