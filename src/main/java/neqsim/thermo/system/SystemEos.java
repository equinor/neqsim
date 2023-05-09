package neqsim.thermo.system;

/**
 *
 * @author Even Solbraa
 */
abstract class SystemEos extends neqsim.thermo.system.SystemThermo {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemEos.
   * </p>
   */
  public SystemEos() {
    super();
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
    super(T, P);
  }
}
