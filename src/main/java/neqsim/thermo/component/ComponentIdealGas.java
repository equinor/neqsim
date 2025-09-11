package neqsim.thermo.component;

import java.util.Arrays;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Component implementation for the ideal gas model.
 *
 * @author esol
 */
public class ComponentIdealGas extends Component {
  private static final long serialVersionUID = 1000L;

  /**
   * <p>
   * Constructor for ComponentIdealGas.
   * </p>
   */
  public ComponentIdealGas() {
    this("default", 0.0, 0.0, 0);
  }

  /**
   * <p>
   * Constructor for ComponentIdealGas.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compIndex a int
   */
  public ComponentIdealGas(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentIdealGas clone() {
    ComponentIdealGas cloned = null;
    try {
      cloned = (ComponentIdealGas) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return cloned;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    fugacityCoefficient = 1.0;
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdT(PhaseInterface phase) {
    dfugdt = 0.0;
    return dfugdt;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdP(PhaseInterface phase) {
    dfugdp = 0.0;
    return dfugdp;
  }

  /** {@inheritDoc} */
  @Override
  public double[] logfugcoefdN(PhaseInterface phase) {
    int n = phase.getNumberOfComponents();
    double[] deriv = new double[n];
    Arrays.fill(dfugdn, 0.0);
    Arrays.fill(dfugdx, 0.0);
    return deriv;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdNi(PhaseInterface phase, int k) {
    return 0.0;
  }
}

