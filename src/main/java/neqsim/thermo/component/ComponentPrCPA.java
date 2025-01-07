package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Abstract class ComponentPrCPA.
 *
 * @author Even Solbraa
 */
public abstract class ComponentPrCPA extends ComponentPR implements ComponentCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int cpaon = 1;
  double[] xsite;

  /**
   * <p>
   * Constructor for ComponentPrCPA.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentPrCPA(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    xsite = new double[numberOfAssociationSites];
    if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
      // System.out.println("ass sites: " + numberOfAssociationSites);
      // System.out.println("aSRK " + a + " aCPA " + aCPA);
      // System.out.println("bSRK " + b + " bCPA " + bCPA);
      for (int j = 0; j < getNumberOfAssociationSites(); j++) {
        setXsite(j, 0.0);
      }
      a = aCPA;
      b = bCPA;
      setAttractiveTerm(1);
    }
  }

  /**
   * <p>
   * Constructor for ComponentPrCPA.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature
   * @param PC Critical pressure
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentPrCPA(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
    xsite = new double[numberOfAssociationSites];
    for (int j = 0; j < getNumberOfAssociationSites(); j++) {
      setXsite(j, 1.0);
    }
    if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
      a = aCPA;
      b = bCPA;
    }
    setAttractiveTerm(1);
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    if ((aCPA > 1.0e-10) && cpaon == 1) {
      return 0.0;
    } else {
      return super.getVolumeCorrection();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ComponentPrCPA clone() {
    ComponentPrCPA clonedComponent = null;
    try {
      clonedComponent = (ComponentPrCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
      return aCPA;
    }
    return a;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    if ((numberOfAssociationSites != 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
      return bCPA;
    }
    return b;
  }

  /** {@inheritDoc} */
  @Override
  public void setAttractiveTerm(int i) {
    super.setAttractiveTerm(i);
    if ((getNumberOfAssociationSites() > 0 || Math.abs(aCPA) > 1e-6) && cpaon == 1) {
      getAttractiveTerm().setm(mCPA);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double Fsup = super.dFdN(phase, numberOfComponents, temperature, pressure);
    double Fcpa = 0.0;
    // if(phase.getPhaseType()==1) cpaon=0;
    Fcpa = dFCPAdN(phase, numberOfComponents, temperature, pressure);
    // System.out.println("Fsup " + Fsup + " fcpa " + Fcpa);
    return Fsup + cpaon * Fcpa;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdT(phase, numberOfComponents, temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdV(phase, numberOfComponents, temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure);
  }

  /**
   * <p>
   * dFCPAdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFCPAdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double xi = 0.0;
    for (int i = 0; i < numberOfAssociationSites; i++) {
      xi += Math.log(xsite[i]);
    }
    return (xi - ((PhaseCPAInterface) phase).getHcpatot() / 2.0 * calc_lngi(phase));
  }

  /**
   * <p>
   * calc_lngi.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double calc_lngi(PhaseInterface phase) {
    return 0.475 / (1.0 - 0.475 * phase.getB() / phase.getTotalVolume()) * getBi()
        / phase.getTotalVolume();
  }

  /**
   * <p>
   * calc_lngi2.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double calc_lngi2(PhaseInterface phase) {
    return 2.0 * getBi() * (10.0 * phase.getTotalVolume() - phase.getB())
        / ((8.0 * phase.getTotalVolume() - phase.getB())
            * (4.0 * phase.getTotalVolume() - phase.getB()));
  }

  /** {@inheritDoc} */
  @Override
  public double[] getXsite() {
    return this.xsite;
  }

  /**
   * Setter for property xsite.
   *
   * @param xsite New value of property xsite.
   */
  public void setXsite(double[] xsite) {
    this.xsite = xsite;
  }

  /** {@inheritDoc} */
  @Override
  public void setXsite(int i, double xsite) {
    this.xsite[i] = xsite;
  }
}
