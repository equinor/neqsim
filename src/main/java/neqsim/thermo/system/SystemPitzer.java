package neqsim.thermo.system;

import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionConcentrationBasis;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionDataSource;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PitzerParameterDatasets;
import neqsim.thermo.phase.PitzerParameterQualification;
import neqsim.thermo.phase.PitzerParameterQualification.ValidationTarget;
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

  /**
   * Selects legacy Pitzer parameter loading for compatibility with historical calculations.
   *
   * <p>
   * The default is to use the bundled PHREEQC catalog whenever it contains every interaction required by the active
   * aqueous topology. Call this method before the first activity or property evaluation only when reproducing a legacy
   * result is required.
   * </p>
   */
  public void useLegacyPitzerParameters() {
    ((PhasePitzer) phaseArray[1]).setUsePhreeqcCatalogByDefault(false);
  }

  /**
   * Reports whether automatic parameter loading prefers the bundled PHREEQC catalog.
   *
   * @return {@code true} for the default catalog-first policy
   */
  public boolean isUsingPhreeqcPitzerParametersByDefault() {
    return ((PhasePitzer) phaseArray[1]).isUsePhreeqcCatalogByDefault();
  }

  /**
   * Returns scientific qualification metadata for the selected Pitzer parameter dataset.
   *
   * <p>
   * Calling this method completes lazy dataset selection and the active ionic-topology coverage audit. It does not run
   * a flash or enter ordinary property kernels.
   * </p>
   *
   * @return immutable qualification metadata for the selected dataset identity
   */
  public PitzerParameterQualification getPitzerParameterQualification() {
    PhasePitzer aqueousPhase = (PhasePitzer) phaseArray[1];
    aqueousPhase.getPitzerParameterCoverage();
    return PitzerParameterDatasets.getQualification(aqueousPhase.getParameterDatasetId());
  }

  /**
   * Requires complete interaction coverage and complete scientific qualification of the named Pitzer dataset.
   *
   * <p>
   * This is an explicit publication gate. A broad dataset with only partially validated subsystems is rejected even
   * when it covers the active topology. A successful result still requires the caller to check the appropriate
   * subsystem-specific temperature and molality range helper. This legacy gate does not select an observable; use
   * {@link #requirePitzerDatasetValidationFor(ValidationTarget)} before publishing a property-specific calculation.
   * </p>
   *
   * @return immutable qualification metadata for the accepted dataset
   * @throws IllegalStateException when interaction coverage is incomplete or the complete dataset is not validated
   */
  public PitzerParameterQualification requireCompletePitzerDatasetQualification() {
    PhasePitzer aqueousPhase = (PhasePitzer) phaseArray[1];
    aqueousPhase.requireCompletePitzerParameterCoverage();
    PitzerParameterQualification qualification = PitzerParameterDatasets
        .getQualification(aqueousPhase.getParameterDatasetId());
    qualification.requireCompleteDatasetQualification();
    return qualification;
  }

  /**
   * Requires complete interaction coverage and independent qualification for one scientific target.
   *
   * <p>
   * This explicit publication gate does not run a flash and does not check whether the current temperature, pressure,
   * or composition lies inside the evidence envelope. Callers must also use the applicable dataset-specific range
   * helper in {@link PitzerParameterDatasets}.
   * </p>
   *
   * @param target requested property or equilibrium target
   * @return immutable qualification metadata for the accepted dataset and target
   * @throws IllegalArgumentException when {@code target} is null
   * @throws IllegalStateException when coverage is incomplete or the target lacks independent qualification
   */
  public PitzerParameterQualification requirePitzerDatasetValidationFor(ValidationTarget target) {
    if (target == null) {
      throw new IllegalArgumentException("Pitzer validation target must not be null");
    }
    PhasePitzer aqueousPhase = (PhasePitzer) phaseArray[1];
    aqueousPhase.requireCompletePitzerParameterCoverage();
    PitzerParameterQualification qualification = PitzerParameterDatasets
        .getQualification(aqueousPhase.getParameterDatasetId());
    qualification.requireValidationFor(target);
    return qualification;
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

  /**
   * Applies the complete explicit PHREEQC Pitzer subset required by this system's active aqueous species.
   *
   * <p>
   * The bundled source catalog is broad, but activation remains fail-closed: every required binary, same-sign, ternary,
   * and neutral interaction for the active aqueous topology must exist explicitly. Gas and oil remain on their EOS role
   * phases and do not invoke the catalog.
   * </p>
   */
  public void applyCompletePhreeqcPitzerCatalogParameters() {
    PitzerParameterDatasets.applyCompletePhreeqcPitzerCatalog((PhasePitzer) phaseArray[1]);
  }

  /**
   * Applies the complete qualified PHREEQC Ca-Mg-Cl-SO4 family to this system's Pitzer aqueous role.
   */
  public void applyPhreeqcCalciumMagnesiumChlorideSulfateParameters() {
    PitzerParameterDatasets.applyPhreeqcCalciumMagnesiumChlorideSulfate((PhasePitzer) phaseArray[1]);
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
