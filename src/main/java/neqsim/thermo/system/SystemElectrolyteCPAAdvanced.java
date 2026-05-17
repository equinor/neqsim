package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPAAdvanced;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/**
 * Thermodynamic system using the e-CPA-Advanced electrolyte equation of state.
 *
 * <p>
 * The e-CPA-Advanced model extends the electrolyte CPA framework with:
 * </p>
 * <ul>
 * <li>Ion-specific short-range interaction parameters (W) instead of universal correlations</li>
 * <li>Temperature-dependent Born radii for accurate solvation energetics</li>
 * <li>Quadratic temperature dependence in ion-solvent interactions</li>
 * <li>Ion-pair formation for 2:2 electrolytes (MgSO4, CaSO4, etc.)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * SystemInterface brine = new SystemElectrolyteCPAAdvanced(298.15, 10.0);
 * brine.addComponent("water", 1.0);
 * brine.addComponent("Na+", 0.01);
 * brine.addComponent("Cl-", 0.01);
 * brine.setMixingRule(10);
 *
 * ThermodynamicOperations ops = new ThermodynamicOperations(brine);
 * ops.TPflash();
 * brine.initProperties();
 * </pre>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Maribo-Mogensen et al., Ind. Eng. Chem. Res. 2012, 51, 5353-5363</li>
 * <li>Furst and Renon, AIChE J. 1993, 39(2), 335-343</li>
 * <li>Robinson and Stokes, Electrolyte Solutions, 2nd Ed., 2002</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemElectrolyteCPAAdvanced extends SystemFurstElectrolyteEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for SystemElectrolyteCPAAdvanced with default conditions (298.15 K, 1 bara).
   */
  public SystemElectrolyteCPAAdvanced() {
    this(298.15, 1.0);
  }

  /**
   * Constructor for SystemElectrolyteCPAAdvanced.
   *
   * @param T the temperature in Kelvin
   * @param P the pressure in bara (absolute pressure)
   */
  public SystemElectrolyteCPAAdvanced(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-CPA-Advanced";
    attractiveTermNumber = 15;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseElectrolyteCPAAdvanced();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
    this.useVolumeCorrection(true);
  }

  /** {@inheritDoc} */
  @Override
  public SystemElectrolyteCPAAdvanced clone() {
    SystemElectrolyteCPAAdvanced clonedSystem = null;
    try {
      clonedSystem = (SystemElectrolyteCPAAdvanced) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
