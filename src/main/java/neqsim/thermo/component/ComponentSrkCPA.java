package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseSrkCPA;

/**
 * <p>
 * ComponentSrkCPA class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSrkCPA extends ComponentSrk implements ComponentCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int cpaon = 1;
  double[] xsite = new double[0];
  private double[][] xsitedni = new double[0][0];
  double[] xsiteOld = new double[0];
  double[] xsitedV = new double[0];
  double[] xsitedT = new double[0];
  double[] xsitedTdT = new double[0];

  /**
   * <p>
   * Constructor for ComponentSrkCPA.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentSrkCPA(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    xsite = new double[numberOfAssociationSites];
    xsitedni = new double[numberOfAssociationSites][100];
    xsitedV = new double[numberOfAssociationSites];
    xsitedT = new double[numberOfAssociationSites];
    xsitedTdT = new double[numberOfAssociationSites];
    xsiteOld = new double[numberOfAssociationSites];
    if (numberOfAssociationSites != 0 && cpaon == 1) {
      // System.out.println("ass sites: " + numberOfAssociationSites);
      // System.out.println("aSRK " + a + " aCPA " + aCPA);
      // System.out.println("bSRK " + b + " bCPA " + bCPA);
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
    // double[] surfTensInfluenceParamtemp = {-0.0286407191587279700,
    // -1.85760887578596, 0.520588, -0.1386439759, 1.1216308727071944};
    // this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
  }

  /**
   * <p>
   * Constructor for ComponentSrkCPA.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature
   * @param PC Critical pressure
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentSrkCPA(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
    xsite = new double[numberOfAssociationSites];
    xsitedni = new double[numberOfAssociationSites][100];
    xsitedV = new double[numberOfAssociationSites];
    xsitedT = new double[numberOfAssociationSites];
    xsitedTdT = new double[numberOfAssociationSites];
    xsiteOld = new double[numberOfAssociationSites];
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
    // double[] surfTensInfluenceParamtemp = {-0.0286407191587279700,
    // -1.85760887578596, 0.520588, -0.1386439759, 1.1216308727071944};
    // this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
  }

  /** {@inheritDoc} */
  @Override
  public ComponentSrkCPA clone() {
    ComponentSrkCPA clonedComponent = null;
    try {
      clonedComponent = (ComponentSrkCPA) super.clone();
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
    clonedComponent.xsitedTdT = xsitedTdT.clone();
    System.arraycopy(this.xsitedTdT, 0, clonedComponent.xsitedTdT, 0, xsitedTdT.length);
    clonedComponent.xsitedni = xsitedni.clone();
    System.arraycopy(this.xsitedni, 0, clonedComponent.xsitedni, 0, xsitedni.length);
    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    if ((getRacketZCPA() < 1.0e-10) && cpaon == 1) {
      return 0.0;
    } else {
      setVolumeCorrectionT(getVolumeCorrectionT_CPA());
      setRacketZ(getRacketZCPA());
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
    if (((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      Fcpa = dFCPAdN(phase, numberOfComponents, temperature, pressure);
    }
    // System.out.println("Fsup " + Fsup + " fcpa " + Fcpa);
    // System.out.println("i " + componentNumber + " dFCPAdn " + Fcpa);
    return Fsup + cpaon * Fcpa;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    if (((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      return super.dFdNdT(phase, numberOfComponents, temperature, pressure)
          + dFCPAdNdT(phase, numberOfComponents, temperature, pressure);
    } else {
      return super.dFdNdT(phase, numberOfComponents, temperature, pressure);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    // System.out.println("dQdndV " + dFCPAdNdV(phase, numberOfComponents,
    // temperature, pressure) + " dFdndV " + super.dFdNdV(phase, numberOfComponents,
    // temperature, pressure));
    if (((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      return super.dFdNdV(phase, numberOfComponents, temperature, pressure)
          + dFCPAdNdV(phase, numberOfComponents, temperature, pressure);
    } else {
      return super.dFdNdV(phase, numberOfComponents, temperature, pressure);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    // System.out.println("ij " + componentNumber + " " + j + " dQCPAdndn " +
    // dFCPAdNdN(j, phase, numberOfComponents, temperature, pressure)+ " dQsrkdndn "
    // + super.dFdNdN(j, phase, numberOfComponents, temperature, pressure));
    // System.out.println("dFdndn " +(super.dFdNdN(j, phase, numberOfComponents,
    // temperature, pressure) + dFCPAdNdN(j, phase, numberOfComponents, temperature,
    // pressure)));
    if (((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure)
          + dFCPAdNdN(j, phase, numberOfComponents, temperature, pressure);
    } else {
      return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure);
    }
  }

  /**
   * <p>
   * dFCPAdNdN.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFCPAdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double temp1 = 0;
    for (int i = 0; i < numberOfAssociationSites; i++) {
      temp1 += 1.0 / getXsite()[i] * getXsitedni(i, j);
    }
    double tot2 = 0.0;
    for (int i = 0; i < phase.getComponent(j).getNumberOfAssociationSites(); i++) {
      tot2 += calc_lngi(phase) * (1.0 - ((ComponentSrkCPA) phase.getComponent(j)).getXsite()[i]);
    }
    double tot1 = 1.0 / 2.0 * tot2;

    double tot4 = 0.0;
    tot4 = 0.5 * ((PhaseCPAInterface) phase).getHcpatot() * calc_lngij(j, phase);
    double tot10 = 0.0;
    for (int kk = 0; kk < phase.getNumberOfComponents(); kk++) {
      for (int k = 0; k < phase.getComponent(kk).getNumberOfAssociationSites(); k++) {
        tot10 += -phase.getComponent(kk).getNumberOfMolesInPhase()
            * ((ComponentSrkCPA) phase.getComponent(kk)).getXsitedni(k, j);
      }
    }
    double tot11 = tot10 / 2.0 * calc_lngi(phase);

    // System.out.println("dFCPAdndn " + (temp1 - tot1 - tot4 - tot11));
    return temp1 - tot1 - tot4 - tot11;
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
    // System.out.println("dFCPAdn " + ((xi - ((PhaseCPAInterface)
    // phase).getHcpatot() / 2.0 * calc_lngi(phase)))+ " " + componentNumber);
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
    double xi = 0.0;
    for (int i = 0; i < numberOfAssociationSites; i++) {
      xi += (1.0 / xsite[i]) * xsitedV[i];
    }

    double[] tempar = ((PhaseSrkCPA) phase).getdFdNtemp();
    // double temp = calc_lngi(phase);
    // double temp2 = calc_lngidV(phase);
    /*
     * double tot1 = 0.0, tot2 = 0.0, tot3 = 0.0, tot4 = 0.0, temp, temp2; for (int k = 0; k <
     * phase.getNumberOfComponents(); k++) { tot2 = 0.0; tot3 = 0.0; temp = calc_lngi(phase); temp2
     * = calc_lngidV(phase); for (int i = 0; i <
     * phase.getComponent(k).getNumberOfAssociationSites(); i++) { tot2 -= temp * ((ComponentSrkCPA)
     * phase.getComponent(k)).getXsitedV()[i]; tot3 += (1.0 - ((ComponentSrkCPA)
     * phase.getComponent(k)).getXsite()[i]) * temp2; } tot1 += 1.0 / 2.0 * tot2 *
     * phase.getComponent(k).getNumberOfMolesInPhase(); tot4 += 0.5 *
     * phase.getComponent(k).getNumberOfMolesInPhase() * tot3; }
     */
    // System.out.println("dFCPAdndV " + (xi - tot1 - tot4));
    return xi + tempar[0] * calc_lngi(phase) + tempar[1] * calc_lngidV(phase); // - tot1 - tot4;
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
      xi += 1.0 / xsite[i] * xsitedT[i];
    }

    double tot1 = 0.0;
    double tot2 = 0.0;
    for (int k = 0; k < phase.getNumberOfComponents(); k++) {
      tot2 = 0.0;
      for (int i = 0; i < phase.getComponent(k).getNumberOfAssociationSites(); i++) {
        tot2 -= ((ComponentSrkCPA) phase.getComponent(k)).getXsitedT()[i];
      }
      tot1 += tot2 * phase.getComponent(k).getNumberOfMolesInPhase();
    }
    // System.out.println("dFCPAdndT " + (xi - 1.0 / 2.0 * calc_lngi(phase) *
    // tot1));
    // System.out.println("dlngdni " + calc_lngi(phase));
    return xi - 1.0 / 2.0 * calc_lngi(phase) * tot1;
  }

  /**
   * <p>
   * calc_hCPAdn.
   * </p>
   *
   * @return a double
   */
  public double calc_hCPAdn() {
    double hdn = 0.0;
    for (int i = 0; i < getNumberOfAssociationSites(); i++) {
      hdn = 1.0 - getXsite()[i];
    }
    return hdn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFCPAdXi(int site, PhaseInterface phase) {
    return getNumberOfMolesInPhase() * (1.0 / xsite[site] - 1.0 / 2.0);
  }

  /**
   * <p>
   * dFCPAdXidni.
   * </p>
   *
   * @param site a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double dFCPAdXidni(int site, PhaseInterface phase) {
    return (1.0 / xsite[site] - 1.0 / 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double dFCPAdXidXj(int sitei, int sitej, int compj, PhaseInterface phase) {
    double fact = 0.0;
    if (sitei == sitej && compj == componentNumber) {
      fact = 1.0;
    }
    return -getNumberOfMolesInPhase() / Math.pow(xsite[sitei], 2.0) * fact
        - getNumberOfMolesInPhase() * phase.getComponent(compj).getNumberOfMolesInPhase()
            * ((PhaseCPAInterface) phase).getCpamix().calcDelta(sitei, sitej, componentNumber,
                compj, phase, phase.getTemperature(), phase.getPressure(),
                phase.getNumberOfComponents());
  }

  /** {@inheritDoc} */
  @Override
  public double dFCPAdVdXi(int site, PhaseInterface phase) {
    return -1.0 / (2.0 * phase.getTotalVolume())
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
    return 2.0 * getBi() * (10.0 * phase.getTotalVolume() - phase.getB())
        / ((8.0 * phase.getTotalVolume() - phase.getB())
            * (4.0 * phase.getTotalVolume() - phase.getB()));
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
            * (2.0 * 32.0 * phase.getTotalVolume() - 12.0 * phase.getB())
            / Math.pow(((8.0 * phase.getTotalVolume() - phase.getB())
                * (4.0 * phase.getTotalVolume() - phase.getB())), 2.0);
  }

  /**
   * <p>
   * calc_lngij.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double calc_lngij(int j, PhaseInterface phase) {
    // return 2.0 * getBij(j) * (10.0 * phase.getTotalVolume() - phase.getB())
    // / ((8.0 * phase.getTotalVolume() - phase.getB())
    // * (4.0 * phase.getTotalVolume() - phase.getB()));

    // akis
    double temp = (10.0 * phase.getTotalVolume() - phase.getB())
        / ((8.0 * phase.getTotalVolume() - phase.getB())
            * (4.0 * phase.getTotalVolume() - phase.getB()));
    double temp1 = (8.0 * phase.getTotalVolume() - phase.getB());
    double temp2 = (4.0 * phase.getTotalVolume() - phase.getB());
    double temp3 = (10.0 * phase.getTotalVolume() - phase.getB());
    double tempj = (-((ComponentEosInterface) phase.getComponent(j)).getBi() * temp1 * temp2
        + ((ComponentEosInterface) phase.getComponent(j)).getBi() * temp3 * (temp1 + temp2)) / temp1
        / temp1 / temp2 / temp2;
    return 2.0 * (getBij(j) * temp + getBi() * tempj);
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
  public double[] getXsitedT() {
    return this.xsitedT;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getXsitedTdT() {
    return this.xsitedTdT;
  }

  /** {@inheritDoc} */
  @Override
  public void setXsitedT(int i, double xsitedT) {
    this.xsitedT[i] = xsitedT;
  }

  /** {@inheritDoc} */
  @Override
  public void setXsitedTdT(int i, double xsitedTdT) {
    this.xsitedTdT[i] = xsitedTdT;
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
  public void setXsiteOld(int i, double xsiteOld) {
    this.xsiteOld[i] = xsiteOld;
  }

  /**
   * <p>
   * Getter for the field <code>xsitedni</code>.
   * </p>
   *
   * @return the xsitedni
   */
  public double[][] getXsitedni() {
    return xsitedni;
  }

  /**
   * <p>
   * Getter for the field <code>xsitedni</code>.
   * </p>
   *
   * @param xNumb a int
   * @param compNumbi a int
   * @return a double
   */
  public double getXsitedni(int xNumb, int compNumbi) {
    return xsitedni[xNumb][compNumbi];
  }

  /**
   * <p>
   * Setter for the field <code>xsitedni</code>.
   * </p>
   *
   * @param xsitedni the xsitedni to set
   */
  public void setXsitedni(double[][] xsitedni) {
    this.xsitedni = xsitedni;
  }

  /** {@inheritDoc} */
  @Override
  public void setXsitedni(int xnumb, int compnumb, double val) {
    xsitedni[xnumb][compnumb] = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceTenisionInfluenceParameter(double temperature) {
    double AA = 0;
    double BB = 0;
    if (componentName.equals("water")) {
      double TR = 1.0 - temperature / getTC();
      AA = -2.2367E-16;
      BB = 2.83732E-16;
      // return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB);

      double AAW1 = 2.2505E-16;
      double AAW2 = -1.3646E-16;

      return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AAW1 + AAW2 * TR + 0.5113e-16 * TR * TR);
    } else if (componentName.equals("water2")) { // THis is the old method from
      double TR = 1.0 - temperature / getTC();
      AA = -2.2367E-16;
      BB = 2.83732E-16;
      return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB);
    } else if (componentName.equals("MEG")) {
      return 1.04874809905393E-19;
    } else if (componentName.equals("TEG")) {
      return 7.46824658716429E-19;
    } else {
      return super.getSurfaceTenisionInfluenceParameter(temperature);
    }
  }
}
