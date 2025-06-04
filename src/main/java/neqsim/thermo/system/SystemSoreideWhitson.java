package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseSoreideWhitson;

/**
 * This class defines a thermodynamic system using the Søreide-Whitson
 * Peng-Robinson EoS (modified alpha and mixing rule).
 */
public class SystemSoreideWhitson extends SystemPrEos1978 {
  private static final long serialVersionUID = 1000L;

  /**
   * Default constructor: 298.15 K, 1.0 bara, no solid check.
   */
  public SystemSoreideWhitson() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor with temperature and pressure.
   * 
   * @param T temperature in Kelvin
   * @param P pressure in bara
   */
  public SystemSoreideWhitson(double T, double P) {
    this(T, P, false);
  }

  /**
   * Full constructor.
   * 
   * @param T              temperature in Kelvin
   * @param P              pressure in bara
   * @param checkForSolids check for solids
   */
  public SystemSoreideWhitson(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Soreide-Whitson-PR-EoS";
    attractiveTermNumber = 20; // Use the code for Soreide-Whitson alpha term
    phaseArray[0] = new PhaseSoreideWhitson();
    phaseArray[1] = new PhaseSoreideWhitson();
  }

  @Override
  public SystemSoreideWhitson clone() {
    SystemSoreideWhitson clonedSystem = null;
    try {
      clonedSystem = (SystemSoreideWhitson) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
