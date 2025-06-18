package neqsim.thermo.system;

/**
 * Base class for system with EOS.
 *
 * @author Even Solbraa
 */
public abstract class SystemEos extends neqsim.thermo.system.SystemThermo {
  /** Serialization version UID. */
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
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    if (!super.equals(obj)) {
      return false;
    }
    // No additional fields to compare in System
    return true;
  }
}
