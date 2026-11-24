package neqsim.thermo.system;

import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionConcentrationBasis;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionDataSource;
import neqsim.thermo.phase.PhaseElectrolyteCPAstatoil;

/**
 * This class defines a thermodynamic system using the electrolyte CPA EoS Statoil model.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemElectrolyteCPAstatoil extends SystemFurstElectrolyteEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private boolean useMolalCarbonateChemistry;

  /**
   * Constructor for SystemElectrolyteCPAstatoil.
   */
  public SystemElectrolyteCPAstatoil() {
    this(298.15, 1.0);
  }

  /**
   * Constructor for SystemElectrolyteCPAstatoil.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemElectrolyteCPAstatoil(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-CPA-EOS-statoil";
    attractiveTermNumber = 15;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseElectrolyteCPAstatoil();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);
  }

  /** {@inheritDoc} */
  @Override
  public SystemElectrolyteCPAstatoil clone() {
    SystemElectrolyteCPAstatoil clonedSystem = null;
    try {
      clonedSystem = (SystemElectrolyteCPAstatoil) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }

  /**
   * Select the aqueous water and carbonate reaction set on a molality standard state.
   *
   * <p>
   * Select it before calling {@link #chemicalReactionInit()}. After a change, rebuild chemistry, the component database
   * and the mixing rule before reflashing. This option covers water autoprotolysis and the two carbon dioxide
   * dissociations. It is intended for water-solvent carbonate brines. Use the default reaction set for workflows
   * requiring other reactions. See docs/thermo/ElectrolyteCPAModel.md for the comparison envelope.
   * </p>
   *
   * @param enabled true to use the molality carbonate data, false to select the legacy reaction data
   */
  public void setUseMolalCarbonateChemistry(boolean enabled) {
    if (useMolalCarbonateChemistry == enabled) {
      return;
    }
    useMolalCarbonateChemistry = enabled;
    markChemicalReactionStateStale();
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseArray[i] instanceof PhaseElectrolyteCPAstatoil) {
        ((PhaseElectrolyteCPAstatoil) phaseArray[i]).setUseMolalCarbonateChemistry(enabled);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionConcentrationBasis getChemicalReactionConcentrationBasis() {
    return useMolalCarbonateChemistry ? ChemicalReactionConcentrationBasis.SOLUTE_MOLALITY
        : ChemicalReactionConcentrationBasis.MOLE_FRACTION;
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionDataSource getChemicalReactionDataSource() {
    return useMolalCarbonateChemistry ? ChemicalReactionDataSource.MOLAL_CARBONATE
        : ChemicalReactionDataSource.STANDARD;
  }
}
