package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * Component class for the Span-Wagner reference equation for CO2. The model is limited to pure CO2
 * and therefore many mixture related methods return simplified values.
 *
 * @author esol
 */
public class ComponentSpanWagnerEos extends ComponentEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentSpanWagnerEos.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compIndex a int
   */
  public ComponentSpanWagnerEos(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * Constructor for ComponentSpanWagnerEos.
   * </p>
   *
   * @param number a int
   * @param TC a double
   * @param PC a double
   * @param M a double
   * @param a a double
   * @param moles a double
   */
  public ComponentSpanWagnerEos(int number, double TC, double PC, double M, double a,
      double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentSpanWagnerEos clone() {
    return (ComponentSpanWagnerEos) super.clone();
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double T, double p, double totalNumberOfMoles,
      double beta, int numberOfComponents, int initType) {
    super.Finit(phase, T, p, totalNumberOfMoles, beta, numberOfComponents, initType);
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    return getFugacityCoefficient();
  }
}
