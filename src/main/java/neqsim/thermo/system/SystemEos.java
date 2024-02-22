package neqsim.thermo.system;

/**
 *
 * @author Even Solbraa
 */
public abstract class SystemEos extends neqsim.thermo.system.SystemThermo {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemEos.
   * </p>
   */
  public SystemEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemEos(double T, double P) {
    this(T, P, false);
  }

  public SystemEos(double T, double P, boolean checkForSolids) {
    super(T, P);
    this.solidPhaseCheck = checkForSolids;
  }
}
