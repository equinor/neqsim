package neqsim.thermo.system;

import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionDataSource;
import neqsim.thermo.phase.PhaseKentEisenberg;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * Thermodynamic system using SRK for gas and oil and Kent-Eisenberg for the aqueous electrolyte phase.
 *
 * <p>
 * Calling {@code setMultiPhaseCheck(true)} selects the fixed-role hybrid gas-oil-aqueous flash. If
 * {@code chemicalReactionInit()} has loaded reactions, aqueous chemical equilibrium is coupled to phase equilibrium.
 * Scale-potential results use the Kent-Eisenberg phase activity coefficients and are limited by that screening model's
 * component and parameter coverage.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemKentEisenberg extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemKentEisenberg.
   */
  public SystemKentEisenberg() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemKentEisenberg.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemKentEisenberg(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemKentEisenberg.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemKentEisenberg(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Kent Eisenberg-model";
    attractiveTermNumber = 0;

    configureHybridEosGePhases(T, P, new PhaseSrkEos(), new PhaseKentEisenberg(), new PhaseSrkEos());
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionDataSource getChemicalReactionDataSource() {
    return ChemicalReactionDataSource.KENT_EISENBERG;
  }

  /** {@inheritDoc} */
  @Override
  public SystemKentEisenberg clone() {
    SystemKentEisenberg clonedSystem = null;
    try {
      clonedSystem = (SystemKentEisenberg) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
