package neqsim.thermo.util.mineral;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Phase-specific standard-state thermochemistry for a pure species in a heterogeneous reaction.
 *
 * <p>
 * Thermochemical functions are referenced to the standard pressure of 1 bar. The apparent Gibbs energy follows the
 * Benson-Helgeson convention, {@code G(T) = dfH298 + [H(T) - H298] - T S(T)}, where element contributions cancel for
 * any atom-balanced reaction. Heat capacity can be described by NIST-JANAF Shomate segments (which carry transition
 * enthalpies in their segment constants) or by the Robie and Hemingway (1995) polynomial
 * {@code Cp = A1 + A2 T + A3/T^2 + A4/T^0.5 +
 * A5 T^2}. Outside the tabulated data range the heat capacity is held at its boundary value and the state is reported
 * as extrapolated by {@link #isWithinDataRange(double)}.
 * </p>
 *
 * <p>
 * Pressure dependence of a condensed phase is represented by the integral {@code I(T,P)}, the integral of the molar
 * volume from 1 bar to P. Solids use a constant molar volume; liquid water uses the IAPWS-IF97 Region 1 Gibbs function.
 * Ideal-gas species have no pressure term: their non-ideality belongs in the fugacity calculated by an equation of
 * state.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class StandardStateSpecies implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Reference temperature [K]. */
  public static final double REFERENCE_TEMPERATURE = 298.15;

  /** Standard pressure [bara]. */
  public static final double STANDARD_PRESSURE = 1.0;

  /** Molar mass of water used for the IAPWS-IF97 pressure integral [kg/mol]. */
  private static final double WATER_MOLAR_MASS = 0.018015268;

  /** Reference-state phase of the species. */
  public enum Phase {
    /** Pure crystalline solid. */
    SOLID,
    /** Pure liquid. */
    LIQUID,
    /** Ideal gas at the standard pressure. */
    IDEAL_GAS
  }

  /** Model used for the pressure integral of the molar volume. */
  public enum PressureModel {
    /** No pressure term (ideal-gas standard state). */
    IDEAL_GAS,
    /** Constant molar volume (incompressible, no thermal expansion). */
    CONSTANT_VOLUME,
    /** Liquid water from the IAPWS-IF97 Region 1 Gibbs function. */
    IAPWS_IF97_LIQUID_WATER
  }

  /** Heat-capacity representation. */
  private enum CpForm {
    /** NIST-JANAF Shomate segments. */
    SHOMATE,
    /** Robie and Hemingway (1995) polynomial anchored at 298.15 K. */
    ROBIE_HEMINGWAY
  }

  /** Species name. */
  private final String name;
  /** Reference phase. */
  private final Phase phase;
  /** Element composition. */
  private final Map<String, Double> elements;
  /** Enthalpy of formation at 298.15 K [J/mol]. */
  private final double formationEnthalpy298;
  /** Third-law entropy at 298.15 K [J/(mol K)]. */
  private final double entropy298;
  /** Heat-capacity representation. */
  private final CpForm cpForm;
  /** Heat-capacity coefficients, one row per segment. */
  private final double[][] coefficients;
  /** Lower temperature limit of each segment [K]. */
  private final double[] segmentMinTemperature;
  /** Upper temperature limit of each segment [K]. */
  private final double[] segmentMaxTemperature;
  /** Pressure-integral model. */
  private final PressureModel pressureModel;
  /** Molar volume for the constant-volume model [m3/mol]. */
  private final double molarVolume;
  /** Enthalpy-of-formation uncertainty [J/mol]. */
  private final double formationEnthalpyUncertainty;
  /** Entropy uncertainty [J/(mol K)]. */
  private final double entropyUncertainty;
  /** Literature source. */
  private final String source;

  /**
   * Creates a species. Use the static factory methods for documented data sets.
   *
   * @param name species name
   * @param phase reference phase
   * @param elements element composition (element symbol to atoms per formula unit)
   * @param formationEnthalpy298 standard enthalpy of formation at 298.15 K [J/mol]
   * @param entropy298 third-law entropy at 298.15 K [J/(mol K)], or NaN to evaluate from Shomate
   * @param cpForm heat-capacity representation
   * @param coefficients coefficient rows, one row per temperature segment
   * @param segmentMinTemperature lower temperature limit of each segment [K]
   * @param segmentMaxTemperature upper temperature limit of each segment [K]
   * @param pressureModel pressure-integral model
   * @param molarVolume molar volume for {@link PressureModel#CONSTANT_VOLUME} [m3/mol]
   * @param formationEnthalpyUncertainty enthalpy-of-formation uncertainty [J/mol], NaN if unknown
   * @param entropyUncertainty entropy uncertainty [J/(mol K)], NaN if unknown
   * @param source literature source of the data
   * @throws IllegalArgumentException if the segment arrays or the volume are inconsistent
   */
  private StandardStateSpecies(String name, Phase phase, Map<String, Double> elements, double formationEnthalpy298,
      double entropy298, CpForm cpForm, double[][] coefficients, double[] segmentMinTemperature,
      double[] segmentMaxTemperature, PressureModel pressureModel, double molarVolume,
      double formationEnthalpyUncertainty, double entropyUncertainty, String source) {
    if (coefficients.length == 0 || coefficients.length != segmentMinTemperature.length
        || coefficients.length != segmentMaxTemperature.length) {
      throw new IllegalArgumentException(
          "Species " + name + ": one coefficient row and one temperature range per segment needed");
    }
    for (int i = 0; i < coefficients.length; i++) {
      if (segmentMaxTemperature[i] <= segmentMinTemperature[i]) {
        throw new IllegalArgumentException(
            "Species " + name + ": segment " + i + " has a non-positive temperature interval");
      }
    }
    if (pressureModel == PressureModel.CONSTANT_VOLUME && !(molarVolume > 0.0)) {
      throw new IllegalArgumentException(
          "Species " + name + ": constant-volume pressure model needs a positive molar volume");
    }
    this.name = name;
    this.phase = phase;
    this.elements = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(elements));
    this.formationEnthalpy298 = formationEnthalpy298;
    this.cpForm = cpForm;
    this.coefficients = copy(coefficients);
    this.segmentMinTemperature = segmentMinTemperature.clone();
    this.segmentMaxTemperature = segmentMaxTemperature.clone();
    this.pressureModel = pressureModel;
    this.molarVolume = molarVolume;
    this.formationEnthalpyUncertainty = formationEnthalpyUncertainty;
    this.entropyUncertainty = entropyUncertainty;
    this.source = source;
    this.entropy298 = Double.isNaN(entropy298) ? shomateEntropy(REFERENCE_TEMPERATURE) : entropy298;
  }

  /**
   * Creates a species from NIST-JANAF Shomate segments.
   *
   * <p>
   * Each row holds A, B, C, D, E, F, G, H with {@code t = T/1000}: {@code Cp = A + B t + C t^2 +
   * D t^3 + E/t^2} [J/(mol K)], {@code H - H298 = A t + B t^2/2 + C t^3/3 + D t^4/4 - E/t + F - H} [kJ/mol] and
   * {@code S = A ln t + B t + C t^2/2 + D t^3/3 - E/(2 t^2) + G} [J/(mol K)]. Coefficient H is the enthalpy of
   * formation at 298.15 K in kJ/mol.
   * </p>
   *
   * @param name species name
   * @param phase reference phase
   * @param elements element composition
   * @param coefficients Shomate rows A to H, one per segment
   * @param segmentMinTemperature lower limit of each segment [K]
   * @param segmentMaxTemperature upper limit of each segment [K]
   * @param pressureModel pressure-integral model
   * @param molarVolume molar volume [m3/mol]; ignored unless the model is constant volume
   * @param formationEnthalpyUncertainty enthalpy-of-formation uncertainty [J/mol], NaN if unknown
   * @param entropyUncertainty entropy uncertainty [J/(mol K)], NaN if unknown
   * @param source literature source
   * @return the species
   * @throws IllegalArgumentException if a row does not have eight coefficients
   */
  public static StandardStateSpecies shomate(String name, Phase phase, Map<String, Double> elements,
      double[][] coefficients, double[] segmentMinTemperature, double[] segmentMaxTemperature,
      PressureModel pressureModel, double molarVolume, double formationEnthalpyUncertainty, double entropyUncertainty,
      String source) {
    for (double[] row : coefficients) {
      if (row.length != 8) {
        throw new IllegalArgumentException("Species " + name + ": Shomate rows need A to H");
      }
    }
    return new StandardStateSpecies(name, phase, elements, coefficients[0][7] * 1000.0, Double.NaN, CpForm.SHOMATE,
        coefficients, segmentMinTemperature, segmentMaxTemperature, pressureModel, molarVolume,
        formationEnthalpyUncertainty, entropyUncertainty, source);
  }

  /**
   * Creates a species from the Robie and Hemingway (1995) heat-capacity polynomial.
   *
   * @param name species name
   * @param phase reference phase
   * @param elements element composition
   * @param formationEnthalpy298 standard enthalpy of formation at 298.15 K [J/mol]
   * @param entropy298 third-law entropy at 298.15 K [J/(mol K)]
   * @param cpCoefficients A1 to A5 of {@code Cp = A1 + A2 T + A3/T^2 + A4/T^0.5 + A5 T^2}
   * @param minTemperature lower limit of the fit [K]
   * @param maxTemperature upper limit of the fit [K]
   * @param pressureModel pressure-integral model
   * @param molarVolume molar volume [m3/mol]; ignored unless the model is constant volume
   * @param formationEnthalpyUncertainty enthalpy-of-formation uncertainty [J/mol], NaN if unknown
   * @param entropyUncertainty entropy uncertainty [J/(mol K)], NaN if unknown
   * @param source literature source
   * @return the species
   * @throws IllegalArgumentException if five coefficients are not given
   */
  public static StandardStateSpecies robieHemingway(String name, Phase phase, Map<String, Double> elements,
      double formationEnthalpy298, double entropy298, double[] cpCoefficients, double minTemperature,
      double maxTemperature, PressureModel pressureModel, double molarVolume, double formationEnthalpyUncertainty,
      double entropyUncertainty, String source) {
    if (cpCoefficients.length != 5) {
      throw new IllegalArgumentException("Species " + name + ": A1 to A5 required");
    }
    return new StandardStateSpecies(name, phase, elements, formationEnthalpy298, entropy298, CpForm.ROBIE_HEMINGWAY,
        new double[][] {cpCoefficients.clone()}, new double[] {minTemperature}, new double[] {maxTemperature},
        pressureModel, molarVolume, formationEnthalpyUncertainty, entropyUncertainty, source);
  }

  /**
   * Troilite, hexagonal FeS, from NIST-JANAF (Chase, 1998), 298-1463 K in three segments that carry the 411 K and 598 K
   * transitions; molar volume 18.20 cm3/mol (Robie and Hemingway, 1995).
   *
   * @return troilite species
   */
  public static StandardStateSpecies troilite() {
    double[][] c = {{9240.570, -55016.80, 121502.0, -93187.10, -99.35930, -1634.010, 22510.20, -101.6710},
        {72.36830, -0.060653, 0.120490, -0.079265, -0.000018, -122.1360, 149.9740, -101.6710},
        {95.82780, -85.56150, 48.72030, -0.000101, 0.000071, -123.9460, 205.1350, -101.6710}};
    return shomate("troilite", Phase.SOLID, formula("Fe", 1.0, "S", 1.0), c, new double[] {298.0, 411.0, 598.0},
        new double[] {411.0, 598.0, 1463.0}, PressureModel.CONSTANT_VOLUME, 18.20e-6, Double.NaN, Double.NaN,
        "NIST-JANAF, Chase (1998), troilite phase; V from Robie and Hemingway (1995)");
  }

  /**
   * Siderite, rhombohedral FeCO3, from Robie and Hemingway (1995) USGS Bulletin 2131: dfH298 of -755.9 +/- 5.5 kJ/mol,
   * S298 of 95.47 +/- 0.2 J/(mol K), Cp fit 298-600 K and molar volume of 29.38 cm3/mol.
   *
   * @return siderite species
   */
  public static StandardStateSpecies siderite() {
    return robieHemingway("siderite", Phase.SOLID, formula("Fe", 1.0, "C", 1.0, "O", 3.0), -755.9e3, 95.47,
        new double[] {257.4, -0.04620, 1.523e6, -3.082e3, 0.0}, 298.0, 600.0, PressureModel.CONSTANT_VOLUME, 29.38e-6,
        5.5e3, 0.2, "Robie and Hemingway (1995), USGS Bulletin 2131, siderite");
  }

  /**
   * Liquid water from NIST-JANAF (Chase, 1998), 298-500 K at 1 bar (metastable above the normal boiling point), with
   * the pressure integral from IAPWS-IF97 Region 1.
   *
   * @return liquid water species
   */
  public static StandardStateSpecies liquidWater() {
    return shomate("water", Phase.LIQUID, formula("H", 2.0, "O", 1.0),
        new double[][] {{-203.6060, 1523.290, -3196.413, 2474.455, 3.855326, -256.5478, -488.7163, -285.8304}},
        new double[] {298.0}, new double[] {500.0}, PressureModel.IAPWS_IF97_LIQUID_WATER, 0.0, Double.NaN, Double.NaN,
        "NIST-JANAF, Chase (1998), liquid; pressure from IAPWS-IF97");
  }

  /**
   * Carbon dioxide ideal gas from NIST-JANAF (Chase, 1998), 298-1200 K.
   *
   * @return CO2 ideal-gas species
   */
  public static StandardStateSpecies carbonDioxideGas() {
    return shomate("CO2", Phase.IDEAL_GAS, formula("C", 1.0, "O", 2.0),
        new double[][] {{24.99735, 55.18696, -33.69137, 7.948387, -0.136638, -403.6075, 228.2431, -393.5224}},
        new double[] {298.0}, new double[] {1200.0}, PressureModel.IDEAL_GAS, 0.0, Double.NaN, Double.NaN,
        "NIST-JANAF, Chase (1998), ideal gas");
  }

  /**
   * Hydrogen sulfide ideal gas from NIST-JANAF (Chase, 1998), 298-1400 K.
   *
   * @return H2S ideal-gas species
   */
  public static StandardStateSpecies hydrogenSulfideGas() {
    return shomate("H2S", Phase.IDEAL_GAS, formula("H", 2.0, "S", 1.0),
        new double[][] {{26.88412, 18.67809, 3.434203, -3.378702, 0.135882, -28.91211, 233.3747, -20.50202}},
        new double[] {298.0}, new double[] {1400.0}, PressureModel.IDEAL_GAS, 0.0, Double.NaN, Double.NaN,
        "NIST-JANAF, Chase (1998), ideal gas");
  }

  /**
   * Pyrite, cubic FeS2, from Robie and Hemingway (1995) USGS Bulletin 2131: dfH298 of -171.5 +/- 1.7 kJ/mol, S298 of
   * 52.9 +/- 0.1 J/(mol K), Cp fit 298-1015 K and molar volume of 23.94 cm3/mol.
   *
   * @return pyrite species
   */
  public static StandardStateSpecies pyrite() {
    return robieHemingway("pyrite", Phase.SOLID, formula("Fe", 1.0, "S", 2.0), -171.5e3, 52.9,
        new double[] {-20.32, 0.0503, -3.200e6, 1787.0, 0.0}, 298.15, 1015.0, PressureModel.CONSTANT_VOLUME, 23.94e-6,
        1.7e3, 0.1, "Robie and Hemingway (1995), USGS Bulletin 2131, pyrite");
  }

  /**
   * Magnetite, Fe3O4, from Robie and Hemingway (1995) USGS Bulletin 2131: dfH298 of -1115.7 +/- 2.1 kJ/mol, S298 of
   * 146.1 +/- 0.4 J/(mol K), Cp fit 298-800 K (first segment) and molar volume of 44.52 cm3/mol.
   *
   * @return magnetite species
   */
  public static StandardStateSpecies magnetite() {
    return robieHemingway("magnetite", Phase.SOLID, formula("Fe", 3.0, "O", 4.0), -1115.7e3, 146.1,
        new double[] {2659.11, -2.5215, 2.0734e7, -36455.0, 0.0013677}, 298.15, 800.0, PressureModel.CONSTANT_VOLUME,
        44.52e-6, 2.1e3, 0.4, "Robie and Hemingway (1995), USGS Bulletin 2131, magnetite");
  }

  /**
   * Calcite, CaCO3, from Robie and Hemingway (1995) USGS Bulletin 2131: dfH298 of -1207.4 +/- 1.3 kJ/mol, S298 of 91.7
   * +/- 0.2 J/(mol K), Cp fit 298-1200 K and molar volume of 36.93 cm3/mol.
   *
   * @return calcite species
   */
  public static StandardStateSpecies calcite() {
    return robieHemingway("calcite", Phase.SOLID, formula("Ca", 1.0, "C", 1.0, "O", 3.0), -1207.4e3, 91.7,
        new double[] {99.72, 0.02692, -2.158e6, 0.0, 0.0}, 298.15, 1200.0, PressureModel.CONSTANT_VOLUME, 36.93e-6,
        1.3e3, 0.2, "Robie and Hemingway (1995), USGS Bulletin 2131, calcite");
  }

  /**
   * Anhydrite, CaSO4, from Robie and Hemingway (1995) USGS Bulletin 2131: dfH298 of -1434.4 +/- 4.2 kJ/mol, S298 of
   * 107.4 +/- 0.2 J/(mol K), Cp fit 298-1000 K and molar volume of 46.01 cm3/mol.
   *
   * @return anhydrite species
   */
  public static StandardStateSpecies anhydrite() {
    return robieHemingway("anhydrite", Phase.SOLID, formula("Ca", 1.0, "S", 1.0, "O", 4.0), -1434.4e3, 107.4,
        new double[] {372.8, -0.1574, 1.695e6, -4330.8, 7.99e-5}, 298.15, 1000.0, PressureModel.CONSTANT_VOLUME,
        46.01e-6, 4.2e3, 0.2, "Robie and Hemingway (1995), USGS Bulletin 2131, anhydrite");
  }

  /**
   * Methane ideal gas from NIST-JANAF (Chase, 1998), 298-1300 K. Named "methane" to match the NeqSim component.
   *
   * @return methane ideal-gas species
   */
  public static StandardStateSpecies methaneGas() {
    return shomate("methane", Phase.IDEAL_GAS, formula("C", 1.0, "H", 4.0),
        new double[][] {{-0.703029, 108.4773, -42.52157, 5.862788, 0.678565, -76.84376, 158.7163, -74.87310}},
        new double[] {298.0}, new double[] {1300.0}, PressureModel.IDEAL_GAS, 0.0, Double.NaN, Double.NaN,
        "NIST-JANAF, Chase (1998), ideal gas");
  }

  /**
   * Hydrogen ideal gas from NIST-JANAF (Chase, 1998), 298-1000 K. Named "hydrogen" to match the NeqSim component.
   *
   * @return hydrogen ideal-gas species
   */
  public static StandardStateSpecies hydrogenGas() {
    return shomate("hydrogen", Phase.IDEAL_GAS, formula("H", 2.0),
        new double[][] {{33.066178, -11.363417, 11.432816, -2.772874, -0.158558, -9.980797, 172.707974, 0.0}},
        new double[] {298.0}, new double[] {1000.0}, PressureModel.IDEAL_GAS, 0.0, Double.NaN, Double.NaN,
        "NIST-JANAF, Chase (1998), ideal gas");
  }

  /**
   * Builds an element map from alternating symbol and count arguments.
   *
   * @param symbolsAndCounts alternating element symbol (String) and atom count (Double)
   * @return ordered element map
   */
  private static Map<String, Double> formula(Object... symbolsAndCounts) {
    Map<String, Double> map = new LinkedHashMap<String, Double>();
    for (int i = 0; i < symbolsAndCounts.length; i += 2) {
      map.put((String) symbolsAndCounts[i], (Double) symbolsAndCounts[i + 1]);
    }
    return map;
  }

  /**
   * Deep-copies a coefficient matrix.
   *
   * @param source matrix to copy
   * @return copy of the matrix
   */
  private static double[][] copy(double[][] source) {
    double[][] result = new double[source.length][];
    for (int i = 0; i < source.length; i++) {
      result[i] = source[i].clone();
    }
    return result;
  }

  /**
   * Gets the species name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the reference phase.
   *
   * @return phase
   */
  public Phase getPhase() {
    return phase;
  }

  /**
   * Gets the element composition.
   *
   * @return unmodifiable element map
   */
  public Map<String, Double> getElements() {
    return elements;
  }

  /**
   * Gets the literature source.
   *
   * @return source description
   */
  public String getSource() {
    return source;
  }

  /**
   * Gets the standard enthalpy of formation at 298.15 K.
   *
   * @return enthalpy of formation [J/mol]
   */
  public double getFormationEnthalpy298() {
    return formationEnthalpy298;
  }

  /**
   * Gets the third-law entropy at 298.15 K.
   *
   * @return entropy [J/(mol K)]
   */
  public double getEntropy298() {
    return entropy298;
  }

  /**
   * Gets the enthalpy-of-formation uncertainty.
   *
   * @return uncertainty [J/mol], NaN if not quantified
   */
  public double getFormationEnthalpyUncertainty() {
    return formationEnthalpyUncertainty;
  }

  /**
   * Gets the entropy uncertainty.
   *
   * @return uncertainty [J/(mol K)], NaN if not quantified
   */
  public double getEntropyUncertainty() {
    return entropyUncertainty;
  }

  /**
   * Gets the pressure-integral model.
   *
   * @return pressure model
   */
  public PressureModel getPressureModel() {
    return pressureModel;
  }

  /**
   * Gets the lowest temperature covered by the heat-capacity data.
   *
   * @return temperature [K]
   */
  public double getMinimumTemperature() {
    return segmentMinTemperature[0];
  }

  /**
   * Gets the highest temperature covered by the heat-capacity data.
   *
   * @return temperature [K]
   */
  public double getMaximumTemperature() {
    return segmentMaxTemperature[segmentMaxTemperature.length - 1];
  }

  /**
   * Checks whether the temperature lies inside the tabulated heat-capacity range.
   *
   * @param temperature temperature [K]
   * @return true if no extrapolation is needed
   */
  public boolean isWithinDataRange(double temperature) {
    return temperature >= getMinimumTemperature() - 0.5 && temperature <= getMaximumTemperature() + 0.5;
  }

  /**
   * Calculates the standard heat capacity; constant outside the data range.
   *
   * @param temperature temperature [K]
   * @return heat capacity [J/(mol K)]
   */
  public double getHeatCapacity(double temperature) {
    return rawHeatCapacity(clampTemperature(temperature));
  }

  /**
   * Calculates the enthalpy change from 298.15 K.
   *
   * @param temperature temperature [K]
   * @return {@code H(T) - H(298.15)} [J/mol]
   */
  public double getEnthalpyChange(double temperature) {
    double tc = clampTemperature(temperature);
    return rawEnthalpyChange(tc) + rawHeatCapacity(tc) * (temperature - tc);
  }

  /**
   * Calculates the standard entropy.
   *
   * @param temperature temperature [K]
   * @return entropy [J/(mol K)]
   */
  public double getEntropy(double temperature) {
    double tc = clampTemperature(temperature);
    return rawEntropy(tc) + rawHeatCapacity(tc) * Math.log(temperature / tc);
  }

  /**
   * Calculates the apparent standard Gibbs energy, {@code dfH298 + [H(T) - H298] - T S(T)}.
   *
   * @param temperature temperature [K]
   * @return apparent Gibbs energy [J/mol]
   */
  public double getApparentGibbsEnergy(double temperature) {
    return formationEnthalpy298 + getEnthalpyChange(temperature) - temperature * getEntropy(temperature);
  }

  /**
   * Calculates the pressure integral of the molar volume from 1 bar to the given pressure.
   *
   * @param temperature temperature [K]
   * @param pressure pressure [bara]
   * @return integral of V dp [J/mol]; zero for ideal-gas species
   */
  public double getPressureIntegral(double temperature, double pressure) {
    switch (pressureModel) {
    case CONSTANT_VOLUME:
      return molarVolume * (pressure - STANDARD_PRESSURE) * 1.0e5;
    case IAPWS_IF97_LIQUID_WATER:
      return WATER_MOLAR_MASS * (Iapws_if97.liquidGibbsDerivative(pressure / 10.0, temperature, 0, 0)
          - Iapws_if97.liquidGibbsDerivative(STANDARD_PRESSURE / 10.0, temperature, 0, 0));
    default:
      return 0.0;
    }
  }

  /**
   * Checks whether the pressure integral uses a metastable liquid-water reference, which happens when the 1-bar
   * reference lies above the normal boiling point.
   *
   * @param temperature temperature [K]
   * @return true if the liquid-water reference at 1 bar is metastable
   */
  public boolean usesMetastableLiquidReference(double temperature) {
    return pressureModel == PressureModel.IAPWS_IF97_LIQUID_WATER
        && temperature > Iapws_if97.tsat_p(STANDARD_PRESSURE / 10.0);
  }

  /**
   * Limits a temperature to the tabulated data range, starting at 298.15 K.
   *
   * @param temperature temperature [K]
   * @return clamped temperature [K]
   */
  private double clampTemperature(double temperature) {
    double lower = Math.max(getMinimumTemperature(), REFERENCE_TEMPERATURE);
    return Math.min(Math.max(temperature, lower), getMaximumTemperature());
  }

  /**
   * Finds the segment that contains a temperature.
   *
   * @param temperature temperature inside the data range [K]
   * @return segment index
   */
  private int segmentIndex(double temperature) {
    for (int i = 0; i < segmentMaxTemperature.length; i++) {
      if (temperature <= segmentMaxTemperature[i]) {
        return i;
      }
    }
    return segmentMaxTemperature.length - 1;
  }

  /**
   * Evaluates the heat capacity inside the data range.
   *
   * @param temperature temperature [K]
   * @return heat capacity [J/(mol K)]
   */
  private double rawHeatCapacity(double temperature) {
    if (cpForm == CpForm.ROBIE_HEMINGWAY) {
      double[] a = coefficients[0];
      return a[0] + a[1] * temperature + a[2] / (temperature * temperature) + a[3] / Math.sqrt(temperature)
          + a[4] * temperature * temperature;
    }
    double[] c = coefficients[segmentIndex(temperature)];
    double t = temperature / 1000.0;
    return c[0] + c[1] * t + c[2] * t * t + c[3] * t * t * t + c[4] / (t * t);
  }

  /**
   * Evaluates {@code H(T) - H(298.15)} inside the data range.
   *
   * @param temperature temperature [K]
   * @return enthalpy change [J/mol]
   */
  private double rawEnthalpyChange(double temperature) {
    if (cpForm == CpForm.ROBIE_HEMINGWAY) {
      double[] a = coefficients[0];
      double t0 = REFERENCE_TEMPERATURE;
      double t = temperature;
      return a[0] * (t - t0) + a[1] / 2.0 * (t * t - t0 * t0) - a[2] * (1.0 / t - 1.0 / t0)
          + 2.0 * a[3] * (Math.sqrt(t) - Math.sqrt(t0)) + a[4] / 3.0 * (t * t * t - t0 * t0 * t0);
    }
    return shomateEnthalpyChange(temperature) - shomateEnthalpyChange(REFERENCE_TEMPERATURE);
  }

  /**
   * Evaluates the entropy inside the data range.
   *
   * @param temperature temperature [K]
   * @return entropy [J/(mol K)]
   */
  private double rawEntropy(double temperature) {
    if (cpForm == CpForm.ROBIE_HEMINGWAY) {
      double[] a = coefficients[0];
      double t0 = REFERENCE_TEMPERATURE;
      double t = temperature;
      return entropy298 + a[0] * Math.log(t / t0) + a[1] * (t - t0) - a[2] / 2.0 * (1.0 / (t * t) - 1.0 / (t0 * t0))
          - 2.0 * a[3] * (1.0 / Math.sqrt(t) - 1.0 / Math.sqrt(t0)) + a[4] / 2.0 * (t * t - t0 * t0);
    }
    return shomateEntropy(temperature);
  }

  /**
   * Evaluates the tabulated Shomate enthalpy {@code H - H298}.
   *
   * @param temperature temperature [K]
   * @return enthalpy change from the segment constants [J/mol]
   */
  private double shomateEnthalpyChange(double temperature) {
    double[] c = coefficients[segmentIndex(temperature)];
    double t = temperature / 1000.0;
    return 1000.0 * (c[0] * t + c[1] * t * t / 2.0 + c[2] * t * t * t / 3.0 + c[3] * t * t * t * t / 4.0 - c[4] / t
        + c[5] - c[7]);
  }

  /**
   * Evaluates the Shomate entropy.
   *
   * @param temperature temperature [K]
   * @return entropy [J/(mol K)]
   */
  private double shomateEntropy(double temperature) {
    double[] c = coefficients[segmentIndex(temperature)];
    double t = temperature / 1000.0;
    return c[0] * Math.log(t) + c[1] * t + c[2] * t * t / 2.0 + c[3] * t * t * t / 3.0 - c[4] / (2.0 * t * t) + c[6];
  }
}
