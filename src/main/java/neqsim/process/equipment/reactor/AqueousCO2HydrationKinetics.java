package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Published reversible hydration kinetics for aqueous carbon dioxide in 0.65 molal NaCl.
 *
 * <p>
 * Soli and Byrne measured the reversible reaction {@code CO2(aq) + H2O <=> H2CO3} from 15 to 32.5 degrees Celsius in
 * 0.65 molal NaCl. Their fitted first-order rate constants are {@code ln(kH / s-1) = 22.66 - 7799 / T} and
 * {@code ln(kD / s-1) = 30.15 - 8018 / T}, with temperature in Kelvin.
 * </p>
 *
 * <p>
 * The source did not qualify pressure effects or dense-phase CO2. This class therefore provides the aqueous kinetic
 * bridge and its published temperature/composition boundary, but it does not claim that the correlation is valid at CO2
 * pipeline pressure. Pressure qualification and phase selection remain caller responsibilities.
 * </p>
 *
 * @see <a href="https://doi.org/10.1016/S0304-4203(02)00010-5">Soli and Byrne (2002), Marine Chemistry 78, 65-73</a>
 * @author NeqSim Team
 * @version 1.0
 */
public final class AqueousCO2HydrationKinetics implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Primary-source DOI. */
  public static final String SOURCE_IDENTIFIER = "doi:10.1016/S0304-4203(02)00010-5";

  /** Human-readable primary-source citation. */
  public static final String SOURCE_CITATION = "A. L. Soli and R. H. Byrne, Marine Chemistry 78 (2002) 65-73";

  /** Public-access and redistribution note for the implemented source material. */
  public static final String SOURCE_ACCESS_STATUS = "Public DOI and institutional abstract; equations implemented without redistributing tabulated data";

  /** NaCl molality used in the published measurements [mol/kg solvent]. */
  public static final double PUBLISHED_NACL_MOLALITY = 0.65;

  /** Minimum published correlation temperature [K]. */
  public static final double MINIMUM_TEMPERATURE_K = 288.15;

  /** Maximum published correlation temperature [K]. */
  public static final double MAXIMUM_TEMPERATURE_K = 305.65;

  /** Temperature of the separately reported equilibrium-ratio observation [K]. */
  public static final double REPORTED_RATIO_TEMPERATURE_K = 298.15;

  /** Separately reported {@code [CO2(aq)] / [H2CO3]} ratio at 25 degrees Celsius. */
  public static final double REPORTED_CO2_TO_H2CO3_RATIO_AT_25_C = 848.0;

  /** Gas constant used by {@link KineticReaction} [J/(mol K)]. */
  private static final double KINETIC_REACTION_GAS_CONSTANT_J_PER_MOL_K = 8.31446;
  private static final double HYDRATION_LOG_PRE_EXPONENTIAL = 22.66;
  private static final double HYDRATION_INVERSE_TEMPERATURE_K = 7799.0;
  private static final double DEHYDRATION_LOG_PRE_EXPONENTIAL = 30.15;
  private static final double DEHYDRATION_INVERSE_TEMPERATURE_K = 8018.0;

  private AqueousCO2HydrationKinetics() {
  }

  /**
   * Calculate the published first-order CO2 hydration rate constant.
   *
   * @param temperatureK aqueous-phase temperature [K]
   * @return hydration rate constant [1/s]
   * @throws IllegalArgumentException when temperature is outside the published range
   */
  public static double hydrationRateConstant(double temperatureK) {
    requirePublishedTemperature(temperatureK);
    return Math.exp(HYDRATION_LOG_PRE_EXPONENTIAL - HYDRATION_INVERSE_TEMPERATURE_K / temperatureK);
  }

  /**
   * Calculate the published first-order H2CO3 dehydration rate constant.
   *
   * @param temperatureK aqueous-phase temperature [K]
   * @return dehydration rate constant [1/s]
   * @throws IllegalArgumentException when temperature is outside the published range
   */
  public static double dehydrationRateConstant(double temperatureK) {
    requirePublishedTemperature(temperatureK);
    return Math.exp(DEHYDRATION_LOG_PRE_EXPONENTIAL - DEHYDRATION_INVERSE_TEMPERATURE_K / temperatureK);
  }

  /**
   * Calculate the kinetic equilibrium ratio {@code [H2CO3] / [CO2(aq)]}.
   *
   * @param temperatureK aqueous-phase temperature [K]
   * @return dimensionless concentration ratio kH/kD
   */
  public static double carbonicAcidToCO2EquilibriumRatio(double temperatureK) {
    return hydrationRateConstant(temperatureK) / dehydrationRateConstant(temperatureK);
  }

  /**
   * Partition a lumped molecular-CO2 concentration into explicit aqueous CO2 and carbonic acid.
   *
   * <p>
   * NeqSim's existing electrolyte reaction sets use a single molecular {@code CO2} inventory for carbonate equilibrium,
   * while this kinetic model distinguishes {@code CO2(aq)} from {@code H2CO3}. This method provides the
   * source-consistent equilibrium partition needed for reporting or for initializing the explicit kinetic pair. It does
   * not add an {@code H2CO3} component to a thermodynamic system or replace the model-specific {@code CO2water}
   * equilibrium constant.
   * </p>
   *
   * @param totalMolecularCO2Concentration lumped {@code CO2(aq) + H2CO3} concentration [mol/m3]
   * @param temperatureK aqueous-phase temperature [K]
   * @return immutable explicit-pair partition
   * @throws IllegalArgumentException when the concentration is negative or non-finite, or temperature is outside the
   * published range
   */
  public static SpeciationBridgeResult partitionLumpedMolecularCO2(double totalMolecularCO2Concentration,
      double temperatureK) {
    requireFiniteNonNegative(totalMolecularCO2Concentration, "total molecular CO2 concentration");
    double carbonicAcidToCO2Ratio = carbonicAcidToCO2EquilibriumRatio(temperatureK);
    double carbonicAcidConcentration = totalMolecularCO2Concentration * carbonicAcidToCO2Ratio
        / (1.0 + carbonicAcidToCO2Ratio);
    double co2Concentration = totalMolecularCO2Concentration - carbonicAcidConcentration;
    return new SpeciationBridgeResult(totalMolecularCO2Concentration, co2Concentration, carbonicAcidConcentration,
        carbonicAcidToCO2Ratio, totalMolecularCO2Concentration - co2Concentration - carbonicAcidConcentration);
  }

  /**
   * Collapse an explicit aqueous CO2/carbonic-acid pair to the lumped molecular-CO2 inventory.
   *
   * <p>
   * Use this conservation-only handoff before invoking an electrolyte equilibrium model whose reaction set already
   * represents carbonate speciation from one molecular {@code CO2} component. The method does not run hydration,
   * dissociation, charge balance, or phase equilibrium.
   * </p>
   *
   * @param co2Concentration aqueous CO2 concentration [mol/m3]
   * @param carbonicAcidConcentration carbonic-acid concentration [mol/m3]
   * @return lumped {@code CO2(aq) + H2CO3} concentration [mol/m3]
   * @throws IllegalArgumentException when either input or their sum is negative or non-finite
   */
  public static double collapseExplicitPairToLumpedCO2(double co2Concentration, double carbonicAcidConcentration) {
    requireFiniteNonNegative(co2Concentration, "CO2 concentration");
    requireFiniteNonNegative(carbonicAcidConcentration, "H2CO3 concentration");
    double totalMolecularCO2Concentration = co2Concentration + carbonicAcidConcentration;
    if (!Double.isFinite(totalMolecularCO2Concentration)) {
      throw new IllegalArgumentException("total molecular CO2 concentration must be finite");
    }
    return totalMolecularCO2Concentration;
  }

  /**
   * Create an equivalent generic {@link KineticReaction} definition.
   *
   * <p>
   * Water is retained in the stoichiometry for elemental conservation but has kinetic order zero because it is the
   * solvent in the published pseudo-first-order model. The returned reaction is not automatically phase- or
   * pressure-qualified. A compatible thermodynamic system must expose distinct {@code CO2}, {@code water}, and
   * {@code H2CO3} components before evaluating its rate.
   * </p>
   *
   * @return reversible volume-basis reaction matching the published correlations
   */
  public static KineticReaction createReaction() {
    KineticReaction reaction = new KineticReaction("aqueous CO2 hydration (Soli and Byrne 2002)");
    reaction.addReactant("CO2", 1.0, 1.0);
    reaction.addReactant("water", 1.0, 0.0);
    reaction.addProduct("H2CO3", 1.0, 1.0);
    reaction.setRateBasis(KineticReaction.RateBasis.VOLUME);
    reaction.setPreExponentialFactor(Math.exp(HYDRATION_LOG_PRE_EXPONENTIAL));
    reaction.setActivationEnergy(HYDRATION_INVERSE_TEMPERATURE_K * KINETIC_REACTION_GAS_CONSTANT_J_PER_MOL_K);
    reaction.setEquilibriumConstantCorrelation(HYDRATION_LOG_PRE_EXPONENTIAL - DEHYDRATION_LOG_PRE_EXPONENTIAL,
        DEHYDRATION_INVERSE_TEMPERATURE_K - HYDRATION_INVERSE_TEMPERATURE_K, 0.0, 0.0);
    return reaction;
  }

  /**
   * Advance a closed, isothermal aqueous CO2/H2CO3 pair analytically.
   *
   * <p>
   * The exact solution of the two first-order rates is used, so the update is deterministic, non-negative, and
   * conserves the supplied dissolved inorganic carbon without a timestep-size error. It does not perform acid
   * dissociation, charge balance, phase equilibrium, or energy coupling.
   * </p>
   *
   * @param co2Concentration initial aqueous CO2 concentration [mol/m3]
   * @param carbonicAcidConcentration initial H2CO3 concentration [mol/m3]
   * @param elapsedTimeSeconds elapsed reaction time [s]
   * @param temperatureK aqueous-phase temperature [K]
   * @return immutable analytical update result
   */
  public static Result advance(double co2Concentration, double carbonicAcidConcentration, double elapsedTimeSeconds,
      double temperatureK) {
    requireFiniteNonNegative(co2Concentration, "CO2 concentration");
    requireFiniteNonNegative(carbonicAcidConcentration, "H2CO3 concentration");
    requireFiniteNonNegative(elapsedTimeSeconds, "elapsed time");

    double hydrationRate = hydrationRateConstant(temperatureK);
    double dehydrationRate = dehydrationRateConstant(temperatureK);
    double totalConcentration = co2Concentration + carbonicAcidConcentration;
    if (!Double.isFinite(totalConcentration)) {
      throw new IllegalArgumentException("total carbon concentration must be finite");
    }

    double equilibriumCarbonicAcid = totalConcentration * hydrationRate / (hydrationRate + dehydrationRate);
    double relaxation = Math.exp(-(hydrationRate + dehydrationRate) * elapsedTimeSeconds);
    double updatedCarbonicAcid = equilibriumCarbonicAcid
        + (carbonicAcidConcentration - equilibriumCarbonicAcid) * relaxation;
    updatedCarbonicAcid = Math.max(0.0, Math.min(totalConcentration, updatedCarbonicAcid));
    double updatedCO2 = totalConcentration - updatedCarbonicAcid;

    return new Result(updatedCO2, updatedCarbonicAcid, hydrationRate, dehydrationRate,
        totalConcentration - updatedCO2 - updatedCarbonicAcid);
  }

  private static void requirePublishedTemperature(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK < MINIMUM_TEMPERATURE_K
        || temperatureK > MAXIMUM_TEMPERATURE_K) {
      throw new IllegalArgumentException("temperature must be within the published Soli-Byrne range of "
          + MINIMUM_TEMPERATURE_K + " to " + MAXIMUM_TEMPERATURE_K + " K");
    }
  }

  private static void requireFiniteNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  /** Result of an analytical aqueous CO2 hydration/dehydration update. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double co2Concentration;
    private final double carbonicAcidConcentration;
    private final double hydrationRateConstant;
    private final double dehydrationRateConstant;
    private final double carbonBalanceResidual;

    private Result(double co2Concentration, double carbonicAcidConcentration, double hydrationRateConstant,
        double dehydrationRateConstant, double carbonBalanceResidual) {
      this.co2Concentration = co2Concentration;
      this.carbonicAcidConcentration = carbonicAcidConcentration;
      this.hydrationRateConstant = hydrationRateConstant;
      this.dehydrationRateConstant = dehydrationRateConstant;
      this.carbonBalanceResidual = carbonBalanceResidual;
    }

    /** @return aqueous CO2 concentration [mol/m3]. */
    public double getCO2Concentration() {
      return co2Concentration;
    }

    /** @return carbonic-acid concentration [mol/m3]. */
    public double getCarbonicAcidConcentration() {
      return carbonicAcidConcentration;
    }

    /** @return hydration rate constant [1/s]. */
    public double getHydrationRateConstant() {
      return hydrationRateConstant;
    }

    /** @return dehydration rate constant [1/s]. */
    public double getDehydrationRateConstant() {
      return dehydrationRateConstant;
    }

    /** @return carbon concentration closure residual [mol/m3]. */
    public double getCarbonBalanceResidual() {
      return carbonBalanceResidual;
    }
  }

  /** Result of translating a lumped molecular-CO2 inventory to the explicit kinetic pair. */
  public static final class SpeciationBridgeResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double totalMolecularCO2Concentration;
    private final double co2Concentration;
    private final double carbonicAcidConcentration;
    private final double carbonicAcidToCO2EquilibriumRatio;
    private final double carbonBalanceResidual;

    private SpeciationBridgeResult(double totalMolecularCO2Concentration, double co2Concentration,
        double carbonicAcidConcentration, double carbonicAcidToCO2EquilibriumRatio, double carbonBalanceResidual) {
      this.totalMolecularCO2Concentration = totalMolecularCO2Concentration;
      this.co2Concentration = co2Concentration;
      this.carbonicAcidConcentration = carbonicAcidConcentration;
      this.carbonicAcidToCO2EquilibriumRatio = carbonicAcidToCO2EquilibriumRatio;
      this.carbonBalanceResidual = carbonBalanceResidual;
    }

    /** @return lumped {@code CO2(aq) + H2CO3} concentration [mol/m3]. */
    public double getTotalMolecularCO2Concentration() {
      return totalMolecularCO2Concentration;
    }

    /** @return aqueous CO2 concentration [mol/m3]. */
    public double getCO2Concentration() {
      return co2Concentration;
    }

    /** @return carbonic-acid concentration [mol/m3]. */
    public double getCarbonicAcidConcentration() {
      return carbonicAcidConcentration;
    }

    /** @return correlation-derived {@code [H2CO3] / [CO2(aq)]} ratio. */
    public double getCarbonicAcidToCO2EquilibriumRatio() {
      return carbonicAcidToCO2EquilibriumRatio;
    }

    /** @return correlation-derived {@code [CO2(aq)] / [H2CO3]} ratio. */
    public double getCO2ToCarbonicAcidEquilibriumRatio() {
      return 1.0 / carbonicAcidToCO2EquilibriumRatio;
    }

    /** @return carbon concentration closure residual [mol/m3]. */
    public double getCarbonBalanceResidual() {
      return carbonBalanceResidual;
    }
  }
}
