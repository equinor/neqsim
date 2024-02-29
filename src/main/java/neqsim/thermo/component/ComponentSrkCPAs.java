package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentSrkCPAs class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSrkCPAs extends ComponentSrkCPA {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentSrkCPAs.
   * </p>
   *
   * @param component_name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compnumber Index number of component in phase object component array.
   */
  public ComponentSrkCPAs(String component_name, double moles, double molesInPhase,
      int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);
  }

  /**
   * <p>
   * Constructor for ComponentSrkCPAs.
   * </p>
   *
   * @param number a int
   * @param TC a double
   * @param PC a double
   * @param M a double
   * @param a a double
   * @param moles a double
   */
  public ComponentSrkCPAs(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentSrkCPAs clone() {
    ComponentSrkCPAs clonedComponent = null;
    try {
      clonedComponent = (ComponentSrkCPAs) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }
  /*
   * public double calc_lngi2(PhaseInterface phase) { return 0.475 / (1.0 - 0.475 * phase.getB() /
   * phase.getTotalVolume()) * getBi() / phase.getTotalVolume(); }
   */

  /** {@inheritDoc} */
  @Override
  public double calc_lngi(PhaseInterface phase) {
    return 0.475 * getBi() / (phase.getTotalVolume() - 0.475 * phase.getB());
  }
  /*
   * public double calc_lngi(PhaseInterface phase) { double nbet = phase.getB() / 4.0 /
   * phase.getVolume(); double dlngdb = 1.9 / (1.0 - 1.9 * nbet); double nbeti = nbet / phase.getB()
   * * getBi(); return dlngdb * nbeti; }
   */

  /** {@inheritDoc} */
  @Override
  public double calc_lngidV(PhaseInterface phase) {
    double temp = phase.getTotalVolume() - 0.475 * phase.getB();
    return -0.475 * getBi() / (temp * temp);
  }

  /** {@inheritDoc} */
  @Override
  public double calc_lngij(int j, PhaseInterface phase) {
    double temp = phase.getTotalVolume() - 0.475 * phase.getB();
    // System.out.println("B " + phase.getB() + " Bi " + getBi() + " bij " +
    // getBij(j));
    // return 0.475 * getBij(j) * 0 / (phase.getTotalVolume() - 0.475 * phase.getB())
    // - 0.475 * getBi() * 1.0 / (temp * temp)
    // * (-0.475 * ((ComponentEosInterface) phase.getComponent(j)).getBi())
    // akis
    return (0.475 * getBij(j) * temp
        + 0.475 * ((ComponentEosInterface) phase.getComponent(j)).getBi() * 0.475 * getBi())
        / (temp * temp);
  }
}
