package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentElectrolyteCPAOld class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentElectrolyteCPAOld extends ComponentModifiedFurstElectrolyteEos
    implements ComponentCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentElectrolyteCPAOld.class);

  int cpaon = 1;

  private double[][] xsitedni = new double[0][0];
  double[] xsite = new double[0];
  double[] xsiteOld = new double[0];
  double[] xsitedV = new double[0];
  double[] xsitedT = new double[0];

  /**
   * <p>
   * Constructor for ComponentElectrolyteCPAOld.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentElectrolyteCPAOld(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    xsite = new double[numberOfAssociationSites];
    xsitedV = new double[numberOfAssociationSites];
    xsiteOld = new double[numberOfAssociationSites];
    xsitedT = new double[numberOfAssociationSites];
    if (numberOfAssociationSites != 0 && cpaon == 1) {
      logger.info("ass sites: " + numberOfAssociationSites);
      logger.info("aSRK " + a + " aCPA " + aCPA);
      logger.info("bSRK " + b + " bCPA " + bCPA);
      for (int j = 0; j < getNumberOfAssociationSites(); j++) {
        setXsite(j, 1.0);
        setXsiteOld(j, 1.0);
        setXsitedV(j, 0.0);
        setXsitedT(j, 0.0);
      }
      if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
        a = aCPA;
        b = bCPA;
      }
      setAttractiveTerm(0);
    }
  }

  /**
   * <p>
   * Constructor for ComponentElectrolyteCPAOld.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature
   * @param PC Critical pressure
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentElectrolyteCPAOld(int number, double TC, double PC, double M, double a,
      double moles) {
    super(number, TC, PC, M, a, moles);
    xsite = new double[numberOfAssociationSites];
    xsitedV = new double[numberOfAssociationSites];
    xsiteOld = new double[numberOfAssociationSites];
    xsitedT = new double[numberOfAssociationSites];
    if (numberOfAssociationSites != 0 && cpaon == 1) {
      for (int j = 0; j < getNumberOfAssociationSites(); j++) {
        setXsite(j, 1.0);
        setXsiteOld(j, 1.0);
        setXsitedV(j, 0.0);
        setXsitedT(j, 0.0);
      }
      if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
        a = aCPA;
        b = bCPA;
      }
      setAttractiveTerm(0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ComponentElectrolyteCPAOld clone() {
    ComponentElectrolyteCPAOld clonedComponent = null;
    try {
      clonedComponent = (ComponentElectrolyteCPAOld) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    clonedComponent.xsite = xsite.clone();
    System.arraycopy(this.xsite, 0, clonedComponent.xsite, 0, xsite.length);
    clonedComponent.xsiteOld = xsiteOld.clone();
    System.arraycopy(this.xsiteOld, 0, clonedComponent.xsiteOld, 0, xsiteOld.length);
    clonedComponent.xsitedV = xsitedV.clone();
    System.arraycopy(this.xsitedV, 0, clonedComponent.xsitedV, 0, xsitedV.length);
    clonedComponent.xsitedT = xsitedT.clone();
    System.arraycopy(this.xsitedT, 0, clonedComponent.xsitedT, 0, xsitedT.length);
    return clonedComponent;
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
  public void setAttractiveTerm(int i) {
    super.setAttractiveTerm(i);
    if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
      getAttractiveTerm().setm(mCPA);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void seta(double a) {
    aCPA = a;
  }

  /** {@inheritDoc} */
  @Override
  public void setb(double a) {
    bCPA = a;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
      return aCPA;
    } else {
      return super.calca();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    if (Math.abs(aCPA) > 1e-6 && cpaon == 1) {
      return bCPA;
    } else {
      return super.calcb();
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
    return super.dFdNdT(phase, numberOfComponents, temperature, pressure)
        + dFCPAdNdT(phase, numberOfComponents, temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdV(phase, numberOfComponents, temperature, pressure)
        + dFCPAdNdV(phase, numberOfComponents, temperature, pressure);
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
   * dFCPAdNdV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFCPAdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double xi = dFCPAdNdXidXdV(phase);
    double xi2 = -((PhaseCPAInterface) phase).getHcpatot() / 2.0 * calc_lngidV(phase);
    return xi + xi2;
  }

  /**
   * <p>
   * dFCPAdNdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFCPAdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double xi = 0.0;
    for (int i = 0; i < numberOfAssociationSites; i++) {
      xi += (1.0 / xsite[i] - 1.0 / 2.0) * xsitedT[i];
    }
    return xi;
  }

  /**
   * <p>
   * calc_lngidV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double calc_lngidV(PhaseInterface phase) {
    return 2.0 * getBi() * (10.0)
        / ((8.0 * phase.getTotalVolume() - phase.getB())
            * (4.0 * phase.getTotalVolume() - phase.getB()))
        - 2.0 * getBi() * (10.0 * phase.getTotalVolume() - phase.getB())
            * (32 * Math.pow(phase.getTotalVolume(), 2.0)
                - 12.0 * phase.getTotalVolume() * phase.getB() + Math.pow(phase.getB(), 2.0))
            / Math.pow(((8.0 * phase.getTotalVolume() - phase.getB())
                * (4.0 * phase.getTotalVolume() - phase.getB())), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double dFCPAdVdXi(int site, PhaseInterface phase) {
    return 1.0 / (2.0 * phase.getTotalVolume())
        * (1.0 - phase.getTotalVolume() * ((PhaseCPAInterface) phase).getGcpav())
        * getNumberOfMolesInPhase();
  }

  /** {@inheritDoc} */
  @Override
  public double dFCPAdNdXi(int site, PhaseInterface phase) {
    double xi = 1.0 / xsite[site];

    // return xi - tempp;
    return xi + getNumberOfMolesInPhase() / 2.0 * calc_lngi(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double dFCPAdXidXj(int sitei, int sitej, int compj, PhaseInterface phase) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFCPAdXi(int site, PhaseInterface phase) {
    return 0.0;
  }

  /**
   * <p>
   * dFCPAdNdXidXdV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double dFCPAdNdXidXdV(PhaseInterface phase) {
    double temp = 0.0;
    for (int i = 0; i < numberOfAssociationSites; i++) {
      temp += dFCPAdNdXi(i, phase) * getXsitedV()[i];
    }
    return temp;
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

  /** {@inheritDoc} */
  @Override
  public double[] getXsitedV() {
    return this.xsitedV;
  }

  /** {@inheritDoc} */
  @Override
  public void setXsitedV(int i, double xsitedV) {
    this.xsitedV[i] = xsitedV;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getXsiteOld() {
    return this.xsiteOld;
  }

  /**
   * Setter for property xsite.
   *
   * @param xsiteOld an array of type double
   */
  public void setXsiteOld(double[] xsiteOld) {
    this.xsiteOld = xsiteOld;
  }

  /** {@inheritDoc} */
  @Override
  public void setXsiteOld(int i, double xsiteOld) {}

  /** {@inheritDoc} */
  @Override
  public double[] getXsitedT() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getXsitedTdT() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setXsitedT(int i, double xsitedT) {}

  /** {@inheritDoc} */
  @Override
  public void setXsitedTdT(int i, double xsitedT) {}

  /** {@inheritDoc} */
  @Override
  public void setXsitedni(int xnumb, int compnumb, double val) {
    xsitedni[xnumb][compnumb] = val;
  }
}
