package neqsim.thermo.system;

import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionConcentrationBasis;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionDataSource;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PitzerParameterDatasets;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * Thermodynamic system using the Pitzer GE model for the aqueous phase and SRK EOS for gas and optional oil phases.
 *
 * <p>
 * Supports vapor-liquid-liquid equilibrium (VLLE) with creation-order roles {@code phaseArray[0]} = SRK gas,
 * {@code phaseArray[1]} = Pitzer aqueous and {@code phaseArray[2]} = SRK oil. Enable the dedicated hybrid strategy by
 * calling {@code setMultiPhaseCheck(true)} before running the flash. Phase disappearance only changes the active
 * mapping; repeated flashes, cloning and serialization retain the role objects. Systems initialized through
 * {@code chemicalReactionInit()} alternate fixed-role phase equilibrium with chemical equilibrium in the Pitzer aqueous
 * phase, enabling reactive gas-aqueous and gas-oil-aqueous scale-potential calculations.
 * </p>
 *
 * <p>
 * The hybrid strategy currently supports fluid phases only. Solid and wax checks are rejected explicitly when the
 * strategy is active.
 * </p>
 *
 * @author esol
 */
public class SystemPitzer extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Default constructor.
   */
  public SystemPitzer() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemPitzer.
   *
   * @param T temperature in K
   * @param P pressure in bara
   */
  public SystemPitzer(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemPitzer.
   *
   * @param T temperature in K
   * @param P pressure in bara
   * @param checkForSolids include solid phase
   */
  public SystemPitzer(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Pitzer-GE-model";
    attractiveTermNumber = 0;

    configureHybridEosGePhases(T, P, new PhaseSrkEos(), new PhasePitzer(), new PhaseSrkEos());
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(String typename) {
    super.setMixingRule(neqsim.thermo.mixingrule.EosMixingRuleType.byName(typename.replace("-", "_")));
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i].initRefPhases(false);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionConcentrationBasis getChemicalReactionConcentrationBasis() {
    return ChemicalReactionConcentrationBasis.SOLUTE_MOLALITY;
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionDataSource getChemicalReactionDataSource() {
    return ChemicalReactionDataSource.PITZER;
  }

  /**
   * Applies the qualified public-domain PHREEQC CO2-Na2SO4 parameter subset to this system's Pitzer aqueous role.
   *
   * <p>
   * The dataset is intentionally fail-closed: additional active species require explicit companion interactions. See
   * {@link PitzerParameterDatasets#applyPhreeqcCo2SodiumSulfate(PhasePitzer)} for source identity and validation scope.
   * </p>
   */
  public void applyPhreeqcCo2SodiumSulfateParameters() {
    PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate((PhasePitzer) phaseArray[1]);
  }

  /**
   * Applies the qualified public-domain PHREEQC Na-K-Cl parameter subset to this system's Pitzer aqueous role.
   *
   * <p>
   * The dataset contains both binary families and their same-sign and ternary mixed-ion companions. It is intentionally
   * fail-closed if another active ionic or neutral species lacks a qualified interaction. See
   * {@link PitzerParameterDatasets#applyPhreeqcSodiumPotassiumChloride(PhasePitzer)} for source identity and validation
   * scope.
   * </p>
   */
  public void applyPhreeqcSodiumPotassiumChlorideParameters() {
    PitzerParameterDatasets.applyPhreeqcSodiumPotassiumChloride((PhasePitzer) phaseArray[1]);
  }

  /** {@inheritDoc} */
  @Override
  public SystemPitzer clone() {
    SystemPitzer clonedSystem = null;
    try {
      clonedSystem = (SystemPitzer) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
