package neqsim.thermo.util.sulfur;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thermodynamic correlations for elemental sulfur used by sulfur-recovery equipment.
 *
 * <p>
 * Gas-phase sulfur is treated as an ideal reacting mixture of {@code S2} through {@code S8}. The
 * equilibrium calculation minimizes the ideal-gas Gibbs energy subject to a single sulfur-atom
 * balance. Reference enthalpies and entropies are from the NIST-JANAF Thermochemical Tables,
 * fourth edition (Chase, 1998). Heat capacities for {@code S2-S7} are constant reference values;
 * {@code S8} uses the NIST Shomate correlation. This is a considerably better representation than
 * treating all sulfur vapour as {@code S8}, while keeping the calculation independent of cubic-EOS
 * pseudo-component parameters.
 * </p>
 *
 * <p>
 * The vapour-pressure correlation interpolates the reviewed sulfur vapour-pressure table reported
 * by Meyer (1976), based on Jensen, Baker, and Rau. Interpolation is linear in {@code ln(P)} versus
 * {@code 1/T}, which is locally consistent with the Clausius-Clapeyron equation. No extrapolation is
 * performed beyond 312.15-1308.15 K.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class SulfurThermodynamics {
  /** Universal gas constant [J/(mol K)]. */
  private static final double R = 8.31446261815324;

  /** Reference temperature [K]. */
  private static final double REFERENCE_TEMPERATURE_K = 298.15;

  /** Standard pressure [bar]. */
  private static final double STANDARD_PRESSURE_BAR = 1.0;

  /** Lower validity limit of the vapour-pressure table [K]. */
  private static final double MIN_VAPOUR_PRESSURE_TEMPERATURE_K = 312.15;

  /** Upper validity limit of the vapour-pressure table [K]. */
  private static final double MAX_VAPOUR_PRESSURE_TEMPERATURE_K = 1308.15;

  /** Sulfur vapour-pressure anchor temperatures [K]. */
  private static final double[] VAPOUR_PRESSURE_TEMPERATURE_K = {312.15, 331.95, 354.25,
      380.05, 414.15, 459.15, 518.05, 601.15, 717.76, 768.15, 847.15, 917.15,
      994.15, 1073.15, 1106.15, 1209.15, 1308.15};

  /** Sulfur vapour-pressure anchors [bar]. */
  private static final double[] VAPOUR_PRESSURE_BAR = {1.33322e-8, 1.33322e-7,
      1.33322e-6, 1.33322e-5, 1.33322e-4, 1.33322e-3, 1.33322e-2, 1.33322e-1,
      1.01325, 2.02650, 5.06625, 10.1325, 20.2650, 40.5300, 50.6625, 101.325,
      202.650};

  /** NIST-JANAF reference data for S2-S8. */
  private static final SpeciesData[] SPECIES = {
      new SpeciesData(2, 128600.0, 228.165, 32.1),
      new SpeciesData(3, 141500.0, 269.5, 57.8),
      new SpeciesData(4, 145800.0, 310.6, 70.0),
      new SpeciesData(5, 109400.0, 308.6, 89.5),
      new SpeciesData(6, 101900.0, 354.1, 111.6),
      new SpeciesData(7, 113700.0, 407.7, 133.5),
      new SpeciesData(8, 100420.0, 430.31, 155.0)};

  /** Utility class; do not instantiate. */
  private SulfurThermodynamics() {}

  /**
   * Calculate equilibrium mole fractions of sulfur vapour allotropes.
   *
   * <p>
   * The supplied pressure is the total partial pressure of elemental sulfur vapour, not the total
   * process pressure. The returned fractions therefore sum to one over {@code S2-S8}.
   * </p>
   *
   * @param temperatureK temperature [K]
   * @param sulfurPartialPressureBar total elemental-sulfur vapour partial pressure [bar]
   * @return unmodifiable map keyed by {@code S2} through {@code S8}
   */
  public static Map<String, Double> calculateAllotropeMoleFractions(double temperatureK,
      double sulfurPartialPressureBar) {
    requirePositiveFinite(temperatureK, "temperatureK");
    requirePositiveFinite(sulfurPartialPressureBar, "sulfurPartialPressureBar");

    double targetLogPressure = Math.log(sulfurPartialPressureBar / STANDARD_PRESSURE_BAR);
    double lowerChemicalPotential = -1.0e6;
    double upperChemicalPotential = 1.0e6;

    for (int iteration = 0; iteration < 200; iteration++) {
      double chemicalPotential =
          0.5 * (lowerChemicalPotential + upperChemicalPotential);
      double logPressure = logSumOfSpeciesActivities(temperatureK, chemicalPotential);
      if (logPressure > targetLogPressure) {
        upperChemicalPotential = chemicalPotential;
      } else {
        lowerChemicalPotential = chemicalPotential;
      }
    }

    double chemicalPotential = 0.5 * (lowerChemicalPotential + upperChemicalPotential);
    double normalizationLog = logSumOfSpeciesActivities(temperatureK, chemicalPotential);
    Map<String, Double> fractions = new LinkedHashMap<String, Double>();
    for (SpeciesData species : SPECIES) {
      double exponent =
          (species.sulfurAtoms * chemicalPotential - species.gibbsEnergyJPerMol(temperatureK))
              / (R * temperatureK);
      fractions.put(species.getName(), Math.exp(exponent - normalizationLog));
    }
    return Collections.unmodifiableMap(fractions);
  }

  /**
   * Calculate the mean number of sulfur atoms per vapour molecule.
   *
   * @param temperatureK temperature [K]
   * @param sulfurPartialPressureBar elemental-sulfur partial pressure [bar]
   * @return average sulfur atoms per molecule
   */
  public static double calculateMeanSulfurAtomsPerMolecule(double temperatureK,
      double sulfurPartialPressureBar) {
    Map<String, Double> fractions =
        calculateAllotropeMoleFractions(temperatureK, sulfurPartialPressureBar);
    double meanAtoms = 0.0;
    for (SpeciesData species : SPECIES) {
      meanAtoms += species.sulfurAtoms * fractions.get(species.getName());
    }
    return meanAtoms;
  }

  /**
   * Calculate equilibrium vapour pressure of elemental sulfur.
   *
   * @param temperatureK temperature [K], valid from 312.15 to 1308.15 K
   * @return vapour pressure [bar]
   */
  public static double calculateVapourPressureBar(double temperatureK) {
    if (!Double.isFinite(temperatureK)
        || temperatureK < MIN_VAPOUR_PRESSURE_TEMPERATURE_K
        || temperatureK > MAX_VAPOUR_PRESSURE_TEMPERATURE_K) {
      throw new IllegalArgumentException("temperatureK must be within "
          + MIN_VAPOUR_PRESSURE_TEMPERATURE_K + "-"
          + MAX_VAPOUR_PRESSURE_TEMPERATURE_K + " K");
    }

    for (int i = 0; i < VAPOUR_PRESSURE_TEMPERATURE_K.length - 1; i++) {
      double lowerTemperature = VAPOUR_PRESSURE_TEMPERATURE_K[i];
      double upperTemperature = VAPOUR_PRESSURE_TEMPERATURE_K[i + 1];
      if (temperatureK <= upperTemperature) {
        double inverseTemperature = 1.0 / temperatureK;
        double inverseLower = 1.0 / lowerTemperature;
        double inverseUpper = 1.0 / upperTemperature;
        double interpolationFraction =
            (inverseTemperature - inverseLower) / (inverseUpper - inverseLower);
        double logPressure = Math.log(VAPOUR_PRESSURE_BAR[i])
            + interpolationFraction
                * (Math.log(VAPOUR_PRESSURE_BAR[i + 1])
                    - Math.log(VAPOUR_PRESSURE_BAR[i]));
        return Math.exp(logPressure);
      }
    }
    return VAPOUR_PRESSURE_BAR[VAPOUR_PRESSURE_BAR.length - 1];
  }

  /**
   * Calculate sulfur dew-point temperature from elemental-sulfur partial pressure.
   *
   * @param sulfurPartialPressureBar elemental-sulfur partial pressure [bar]
   * @return dew-point temperature [K]
   */
  public static double calculateDewPointTemperatureK(double sulfurPartialPressureBar) {
    requirePositiveFinite(sulfurPartialPressureBar, "sulfurPartialPressureBar");
    double minimumPressure = VAPOUR_PRESSURE_BAR[0];
    double maximumPressure = VAPOUR_PRESSURE_BAR[VAPOUR_PRESSURE_BAR.length - 1];
    if (sulfurPartialPressureBar < minimumPressure || sulfurPartialPressureBar > maximumPressure) {
      throw new IllegalArgumentException("sulfurPartialPressureBar must be within "
          + minimumPressure + "-" + maximumPressure + " bar");
    }

    double lowerTemperature = MIN_VAPOUR_PRESSURE_TEMPERATURE_K;
    double upperTemperature = MAX_VAPOUR_PRESSURE_TEMPERATURE_K;
    for (int iteration = 0; iteration < 100; iteration++) {
      double trialTemperature = 0.5 * (lowerTemperature + upperTemperature);
      if (calculateVapourPressureBar(trialTemperature) > sulfurPartialPressureBar) {
        upperTemperature = trialTemperature;
      } else {
        lowerTemperature = trialTemperature;
      }
    }
    return 0.5 * (lowerTemperature + upperTemperature);
  }

  /**
   * Calculate the NIST Shomate ideal-gas heat capacity of {@code S8}.
   *
   * @param temperatureK temperature [K], valid from 298 to 6000 K
   * @return heat capacity [J/(mol K)] on an S8 molecular basis
   */
  public static double calculateS8IdealGasHeatCapacityJPerMolK(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK < 298.0 || temperatureK > 6000.0) {
      throw new IllegalArgumentException("temperatureK must be within 298-6000 K");
    }
    double reducedTemperature = temperatureK / 1000.0;
    return 180.6697 + 1.918988 * reducedTemperature
        - 0.520130 * reducedTemperature * reducedTemperature
        + 0.044394 * reducedTemperature * reducedTemperature * reducedTemperature
        - 2.270155 / (reducedTemperature * reducedTemperature);
  }

  /**
   * Return immutable reference data for an allotrope.
   *
   * @param sulfurAtoms atom count, 2 through 8
   * @return species data
   */
  public static SpeciesData getSpeciesData(int sulfurAtoms) {
    for (SpeciesData species : SPECIES) {
      if (species.sulfurAtoms == sulfurAtoms) {
        return species;
      }
    }
    throw new IllegalArgumentException("sulfurAtoms must be in the range 2-8");
  }

  /**
   * Stable log-sum-exp evaluation of the sulfur species activities.
   *
   * @param temperatureK temperature [K]
   * @param atomicChemicalPotential sulfur atomic chemical potential [J/mol atom]
   * @return logarithm of the summed dimensionless species activities
   */
  private static double logSumOfSpeciesActivities(double temperatureK,
      double atomicChemicalPotential) {
    double maximumExponent = -Double.MAX_VALUE;
    double[] exponents = new double[SPECIES.length];
    for (int i = 0; i < SPECIES.length; i++) {
      SpeciesData species = SPECIES[i];
      exponents[i] =
          (species.sulfurAtoms * atomicChemicalPotential
              - species.gibbsEnergyJPerMol(temperatureK)) / (R * temperatureK);
      maximumExponent = Math.max(maximumExponent, exponents[i]);
    }
    double scaledSum = 0.0;
    for (double exponent : exponents) {
      scaledSum += Math.exp(exponent - maximumExponent);
    }
    return maximumExponent + Math.log(scaledSum);
  }

  /** Validate a positive finite input. */
  private static void requirePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /** Immutable reference thermochemistry for one sulfur vapour allotrope. */
  public static final class SpeciesData implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Number of sulfur atoms in the molecule. */
    private final int sulfurAtoms;

    /** Standard enthalpy relative to orthorhombic sulfur at 298.15 K [J/mol]. */
    private final double enthalpyJPerMolAt298K;

    /** Absolute ideal-gas entropy at 298.15 K and 1 bar [J/(mol K)]. */
    private final double entropyJPerMolKAt298K;

    /** Reference ideal-gas heat capacity [J/(mol K)]. */
    private final double heatCapacityJPerMolK;

    /** Create a species-data record. */
    private SpeciesData(int sulfurAtoms, double enthalpyJPerMolAt298K,
        double entropyJPerMolKAt298K, double heatCapacityJPerMolK) {
      this.sulfurAtoms = sulfurAtoms;
      this.enthalpyJPerMolAt298K = enthalpyJPerMolAt298K;
      this.entropyJPerMolKAt298K = entropyJPerMolKAt298K;
      this.heatCapacityJPerMolK = heatCapacityJPerMolK;
    }

    /** @return component name, for example {@code S8} */
    public String getName() {
      return "S" + sulfurAtoms;
    }

    /** @return number of sulfur atoms */
    public int getSulfurAtoms() {
      return sulfurAtoms;
    }

    /** @return enthalpy at 298.15 K [J/mol] */
    public double getEnthalpyJPerMolAt298K() {
      return enthalpyJPerMolAt298K;
    }

    /** @return absolute entropy at 298.15 K [J/(mol K)] */
    public double getEntropyJPerMolKAt298K() {
      return entropyJPerMolKAt298K;
    }

    /** @return reference heat capacity [J/(mol K)] */
    public double getHeatCapacityJPerMolK() {
      return heatCapacityJPerMolK;
    }

    /** Calculate ideal-gas Gibbs energy on the common JANAF reference basis. */
    private double gibbsEnergyJPerMol(double temperatureK) {
      double enthalpy;
      double entropy;
      if (sulfurAtoms == 8) {
        double boundedTemperature = Math.max(298.0, Math.min(6000.0, temperatureK));
        enthalpy = enthalpyJPerMolAt298K
            + calculateS8EnthalpyIncrementJPerMol(boundedTemperature);
        entropy = entropyJPerMolKAt298K
            + calculateS8EntropyIncrementJPerMolK(boundedTemperature);
      } else {
        enthalpy = enthalpyJPerMolAt298K
            + heatCapacityJPerMolK * (temperatureK - REFERENCE_TEMPERATURE_K);
        entropy = entropyJPerMolKAt298K
            + heatCapacityJPerMolK * Math.log(temperatureK / REFERENCE_TEMPERATURE_K);
      }
      return enthalpy - temperatureK * entropy;
    }

    /** Integrate the S8 Shomate heat capacity from 298.15 K to the requested temperature. */
    private double calculateS8EnthalpyIncrementJPerMol(double temperatureK) {
      double temperature = temperatureK / 1000.0;
      double reference = REFERENCE_TEMPERATURE_K / 1000.0;
      return 1000.0 * (180.6697 * (temperature - reference)
          + 1.918988 / 2.0 * (temperature * temperature - reference * reference)
          - 0.520130 / 3.0
              * (Math.pow(temperature, 3.0) - Math.pow(reference, 3.0))
          + 0.044394 / 4.0
              * (Math.pow(temperature, 4.0) - Math.pow(reference, 4.0))
          - 2.270155 * (1.0 / reference - 1.0 / temperature));
    }

    /** Integrate Cp/T for S8 from 298.15 K to the requested temperature. */
    private double calculateS8EntropyIncrementJPerMolK(double temperatureK) {
      double temperature = temperatureK / 1000.0;
      double reference = REFERENCE_TEMPERATURE_K / 1000.0;
      return 180.6697 * Math.log(temperature / reference)
          + 1.918988 * (temperature - reference)
          - 0.520130 / 2.0 * (temperature * temperature - reference * reference)
          + 0.044394 / 3.0
              * (Math.pow(temperature, 3.0) - Math.pow(reference, 3.0))
          + 2.270155 / 2.0
              * (1.0 / (temperature * temperature) - 1.0 / (reference * reference));
    }
  }
}
