package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentElectrolyteCPAstatoil class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentElectrolyteCPAstatoil extends ComponentElectrolyteCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentElectrolyteCPAstatoil.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentElectrolyteCPAstatoil(String name, double moles, double molesInPhase,
      int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * Constructor for ComponentElectrolyteCPAstatoil.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentElectrolyteCPAstatoil(int number, double TC, double PC, double M, double a,
      double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentElectrolyteCPAstatoil clone() {
    ComponentElectrolyteCPAstatoil clonedComponent = null;
    try {
      clonedComponent = (ComponentElectrolyteCPAstatoil) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double calc_lngi(PhaseInterface phase) {
    // System.out.println("val "
    // +0.475/(1.0-0.475*phase.getB()/phase.getTotalVolume())*getBi()/phase.getTotalVolume());
    return 0.475 / (1.0 - 0.475 * phase.getB() / phase.getTotalVolume()) * getBi()
        / phase.getTotalVolume();
  }

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
    return 0.475 * getBij(j) * 0 / (phase.getTotalVolume() - 0.475 * phase.getB()) - 0.475 * getBi()
        * 1.0 / (temp * temp) * (-0.475 * ((ComponentEosInterface) phase.getComponent(j)).getBi());
  }
}
