/*
 * ComponentModifiedFurstElectrolyteEosMod2004.java
 *
 * Created on 26. februar 2001, 17:59
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEosMod2004;

/**
 * <p>
 * ComponentModifiedFurstElectrolyteEosMod2004 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentModifiedFurstElectrolyteEosMod2004 extends ComponentSrk {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Wi = 0;

  double WiT = 0.0;

  double epsi = 0;

  double epsiV = 0;

  double epsIonici = 0;

  double epsIoniciV = 0;

  double dEpsdNi = 0;

  double ionicCoVolume = 0;

  double solventdiElectricdn = 0.0;

  double solventdiElectricdndT = 0;

  double diElectricdn = 0;

  double diElectricdndV = 0;

  double diElectricdndT = 0;

  double bornterm = 0;

  double alphai = 0.0;

  double alphaiT = 0.0;

  double alphaiV = 0.0;

  double XLRi = 0.0;

  double XBorni = 0.0;

  double sr2On = 1.0;

  double lrOn = 1.0;

  double bornOn = 1.0;

  /**
   * <p>
   * Constructor for ComponentModifiedFurstElectrolyteEosMod2004.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentModifiedFurstElectrolyteEosMod2004(String name, double moles, double molesInPhase,
      int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    ionicCoVolume = this.getIonicDiameter();
    if (ionicCharge != 0) {
      setIsIon(true);
    }
    b = ionicCharge != 0
        ? (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[0]
            * Math.pow(getIonicDiameter(), 3.0)
            + neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[1]) * 1e5
        : b;
    a = ionicCharge != 0 ? 1.0e-35 : a;
    setAttractiveParameter(
        new neqsim.thermo.component.attractiveeosterm.AttractiveTermSchwartzentruber(this));
    lennardJonesMolecularDiameter =
        ionicCharge != 0 ? Math.pow((6.0 * b / 1.0e5) / (pi * avagadroNumber), 1.0 / 3.0) * 1e10
            : lennardJonesMolecularDiameter;

    // if(ionicCharge>0) stokesCationicDiameter = stokesCationicDiameter/3.0;
  }

  /**
   * <p>
   * Constructor for ComponentModifiedFurstElectrolyteEosMod2004.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentModifiedFurstElectrolyteEosMod2004(int number, double TC, double PC, double M,
      double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /**
   * <p>
   * initFurstParam.
   * </p>
   */
  public void initFurstParam() {
    b = ionicCharge != 0
        ? (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[0]
            * Math.pow(getIonicDiameter(), 3.0)
            + neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[1]) * 1e5
        : b;
    lennardJonesMolecularDiameter =
        ionicCharge != 0 ? Math.pow((6.0 * b / 1.0e5) / (pi * avagadroNumber), 1.0 / 3.0) * 1e10
            : lennardJonesMolecularDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public ComponentModifiedFurstElectrolyteEosMod2004 clone() {
    ComponentModifiedFurstElectrolyteEosMod2004 clonedComponent = null;
    try {
      clonedComponent = (ComponentModifiedFurstElectrolyteEosMod2004) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return a;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return b;
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta,
      int numberOfComponents, int initType) {
    Wi = ((PhaseModifiedFurstElectrolyteEosMod2004) phase).calcWi(componentNumber, phase, temp,
        pres, numberOfComponents);
    WiT = ((PhaseModifiedFurstElectrolyteEosMod2004) phase).calcWiT(componentNumber, phase, temp,
        pres, numberOfComponents);
    epsi = dEpsdNi(phase, numberOfComponents, temp, pres);
    epsiV = dEpsdNidV(phase, numberOfComponents, temp, pres);
    epsIonici = dEpsIonicdNi(phase, numberOfComponents, temp, pres);
    epsIoniciV = dEpsIonicdNidV(phase, numberOfComponents, temp, pres);
    solventdiElectricdn = calcSolventdiElectricdn(phase, numberOfComponents, temp, pres);
    solventdiElectricdndT = calcSolventdiElectricdndT(phase, numberOfComponents, temp, pres);
    diElectricdn = calcdiElectricdn(phase, numberOfComponents, temp, pres);
    diElectricdndV = calcdiElectricdndV(phase, numberOfComponents, temp, pres);
    diElectricdndT = calcdiElectricdndT(phase, numberOfComponents, temp, pres);
    alphai = -(electronCharge * electronCharge * avagadroNumber) / (vacumPermittivity
        * Math.pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 2.0)
        * R * temp) * diElectricdn;
    alphaiT = -electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * Math
            .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 2.0) * R
            * temp)
        * diElectricdndT
        + electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity
                * Math.pow(
                    ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 2.0)
                * R * temp * temp)
            * diElectricdn
        + 2.0 * electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity
                * Math.pow(
                    ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 3.0)
                * R * temp)
            * diElectricdn
            * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstantdT();
    alphaiV = -electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * Math
            .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 2.0) * R
            * temp)
        * diElectricdndV
        + 2.0 * electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity
                * Math.pow(
                    ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 3.0)
                * R * temp)
            * diElectricdn
            * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstantdV();
    XLRi = calcXLRdN(phase, numberOfComponents, temp, pres);
    XBorni = ionicCharge * ionicCharge / (getLennardJonesMolecularDiameter() * 1e-10);
    super.Finit(phase, temp, pres, totMoles, beta, numberOfComponents, initType);
  }

  /**
   * <p>
   * dAlphaLRdndn.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dAlphaLRdndn(int j, PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    double temp = 2.0 * electronCharge * electronCharge * avagadroNumber
        / (vacumPermittivity * Math
            .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 3.0) * R
            * temperature)
        * diElectricdn
        * ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
            .getComponent(j)).getDiElectricConstantdn()
        - electronCharge * electronCharge * avagadroNumber
            / (vacumPermittivity
                * Math.pow(
                    ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getDiElectricConstant(), 2.0)
                * R * temperature)
            * calcdiElectricdndn(j, phase, numberOfComponents, temperature, pressure);
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double Fsup = 0;
    double FSR2 = 0;
    double FLR = 0;
    double FBorn = 0;
    Fsup = super.dFdN(phase, numberOfComponents, temperature, pressure);
    FSR2 = dFSR2dN(phase, numberOfComponents, temperature, pressure);
    FLR = dFLRdN(phase, numberOfComponents, temperature, pressure);
    FBorn = dFBorndN(phase, numberOfComponents, temperature, pressure);
    // System.out.println("phase " + phase.getType());
    // System.out.println("name " + componentName);
    // System.out.println("Fsup: " + super.dFdN(phase,
    // numberOfComponents,temperature, pressure));
    // if(componentName.equals("Na+")){
    // System.out.println("FnSR: " + dFSR2dN(phase, numberOfComponents, temperature,
    // pressure));
    // System.out.println("FLRn: " + dFLRdN(phase, numberOfComponents,temperature,
    // pressure));
    // }
    return Fsup + sr2On * FSR2 + lrOn * FLR + bornOn * FBorn;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdT(phase, numberOfComponents, temperature, pressure)
        + sr2On * dFSR2dNdT(phase, numberOfComponents, temperature, pressure)
        + lrOn * dFLRdNdT(phase, numberOfComponents, temperature, pressure)
        + bornOn * dFBorndNdT(phase, numberOfComponents, temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdV(phase, numberOfComponents, temperature, pressure)
        + sr2On * dFSR2dNdV(phase, numberOfComponents, temperature, pressure)
        + lrOn * dFLRdNdV(phase, numberOfComponents, temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure)
        + sr2On * dFSR2dNdN(j, phase, numberOfComponents, temperature, pressure)
        + lrOn * dFLRdNdN(j, phase, numberOfComponents, temperature, pressure)
        + bornOn * dFBorndNdN(j, phase, numberOfComponents, temperature, pressure);
  }

  // Long Range term equations and derivatives
  /**
   * <p>
   * dFLRdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFLRdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FLRXLR() * XLRi
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLR() * alphai;
    // + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FLRGammaLR()*gammaLRdn;
  }

  /**
   * <p>
   * dFLRdNdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFLRdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLRdX() * XLRi
        * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getAlphaLRT()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLR() * alphaiT;
  }

  /**
   * <p>
   * dFLRdNdV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFLRdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 1e-5 * (((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLRdX() * XLRi
        * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getAlphaLRV()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLR() * alphaiV);
  }

  /**
   * <p>
   * dFLRdNdN.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFLRdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLRdX() * XLRi
        * ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
            .getComponent(j)).getAlphai()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLR()
            * dAlphaLRdndn(j, phase, numberOfComponents, temperature, pressure)
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).dFdAlphaLRdX() * alphai
            * ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
                .getComponent(j)).getXLRi();
  }

  /**
   * <p>
   * calcXLRdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcXLRdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return Math.pow(getIonicCharge(), 2.0)
        * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getShieldingParameter()
        / (1.0 + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getShieldingParameter()
            * getLennardJonesMolecularDiameter() * 1e-10);
  }

  /**
   * <p>
   * FLRN.
   * </p>
   *
   * @return a double
   */
  public double FLRN() {
    return 0.0;
  }

  /**
   * <p>
   * calcSolventdiElectricdn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcSolventdiElectricdn(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    if (getIonicCharge() != 0) {
    }
    /*
     * double ans2 = 0.0; for (int i = 0; i < numberOfComponents; i++) { if
     * (phase.getComponent(i).getIonicCharge() == 0) { ans2 +=
     * phase.getComponent(i).getNumberOfMolesInPhase(); } }
     */
    return 0.0;
    // return getDiElectricConstant(temperature)/ans2 -
    // ((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getSolventDiElectricConstant()/ans2;
  }

  /**
   * <p>
   * calcSolventdiElectricdndn.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcSolventdiElectricdndn(int j, PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    if (getIonicCharge() != 0 || ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getComponent(j)
        .getIonicCharge() != 0) {
    }
    /*
     * double ans2 = 0.0; for (int i = 0; i < numberOfComponents; i++) { if
     * (phase.getComponent(i).getIonicCharge() == 0) { ans2 +=
     * phase.getComponent(i).getNumberOfMolesInPhase(); } }
     */
    return 0.0;
    // return -getDiElectricConstant(temperature)/(ans2*ans2) -
    // ((ComponentModifiedFurstElectrolyteEosMod2004)((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getComponent(j)).getDiElectricConstant(temperature)/(ans2*ans2) +
    // 2.0*((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getSolventDiElectricConstant()/(ans2*ans2);
  }

  /**
   * <p>
   * calcSolventdiElectricdndT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcSolventdiElectricdndT(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    if (getIonicCharge() != 0) {
    }
    /*
     * double ans2 = 0.0; for (int i = 0; i < numberOfComponents; i++) { if
     * (phase.getComponent(i).getIonicCharge() == 0) { ans2 +=
     * phase.getComponent(i).getNumberOfMolesInPhase(); } }
     */
    return 0.0;
    // return getDiElectricConstantdT(temperature)/ans2 -
    // ((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getSolventDiElectricConstantdT()/ans2;
  }

  /**
   * <p>
   * calcdiElectricdn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcdiElectricdn(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double X = (1.0 - ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic())
        / (1.0 + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0);
    double Y =
        ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getSolventDiElectricConstant() - 1.0;
    double dXdf = getEpsIonici() * -3.0 / 2.0 / Math
        .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0, 2.0);
    double dYdf = getSolventDiElectricConstantdn();
    return dYdf * X + Y * dXdf;
  }

  /**
   * <p>
   * calcdiElectricdndV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcdiElectricdndV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double dXdf =
        ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonicdV() * -3.0 / 2.0 / Math
            .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0, 2.0);
    double dYdf = getSolventDiElectricConstantdn();
    double d1 = ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getSolventDiElectricConstant();
    double d2 = epsIoniciV * -3.0 / 2.0 / Math
        .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0, 2.0);
    double d3 = ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonicdV() * epsIonici * 3.0
        / 2.0 / Math
            .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0, 3.0);
    return dYdf * dXdf + d1 * d2 + d1 * d3;
  }

  /**
   * <p>
   * calcdiElectricdndn.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcdiElectricdndn(int j, PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    double dYdf =
        ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
            .getComponent(j)).getSolventDiElectricConstantdn();
    double dXdfdfj = getEpsIonici() * -3.0 / 2.0 / Math
        .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0, 2.0);

    double dXdf =
        ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
            .getComponent(j)).getEpsIonici() * getEpsIonici() * 3.0 / 2.0
            / Math.pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0,
                3.0);
    double d1 = ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getSolventDiElectricConstant();

    double d2 =
        ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
            .getComponent(j)).getEpsIonici() * -3.0 / 2.0
            / Math.pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0,
                2.0);
    double d5 = getSolventDiElectricConstantdn();

    double d3 = calcSolventdiElectricdndn(j, phase, numberOfComponents, temperature, pressure);
    double d4 = (1.0 - ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic())
        / (1.0 + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0);

    return dYdf * dXdfdfj + dXdf * d1 + d2 * d5 + d3 * d4;
  }

  /**
   * <p>
   * calcdiElectricdndT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcdiElectricdndT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double X = (1.0 - ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic())
        / (1.0 + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0);
    double Y = ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getSolventDiElectricConstantdT();
    double dXdf = getEpsIonici() * -3.0 / 2.0 / Math
        .pow(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsIonic() / 2.0 + 1.0, 2.0);
    double dYdf = solventdiElectricdndT;
    return dYdf * X + Y * dXdf;
  }

  // a little simplified
  /**
   * <p>
   * calcGammaLRdn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double calcGammaLRdn(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
    // if(((PhaseModifiedFurstElectrolyteEosMod2004) phase).getPhaseType()==1)
    // return 0.0;
    // double temp =
    // Math.pow(getIonicCharge()/(1.0+((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getShieldingParameter()*getLennardJonesMolecularDiameter()*1e-10),2.0);
    // return 1.0/(8.0*((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getShieldingParameter() + 4.0 *
    // Math.pow(((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).calcShieldingParameter2(),2.0) * 2.0 *
    // getLennardJonesMolecularDiameter()*1e-10) *
    // ((temp * ((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getAlphaLR2()/(((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getMolarVolume()*1e-5*((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getNumberOfMolesInPhase()) * avagadroNumber)
    // + 4.0 * Math.pow(((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getShieldingParameter(),2.0) /
    // ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getAlphaLR2() * alphai );
    // Math.pow(getIonicCharge()/(1.0+((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getShieldingParameter()*getLennardJonesMolecularDiameter()*1e-10),2.0)
    // + alphai*temp) /(8.0*((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).getShieldingParameter());
    // + 2.0*getLennardJonesMolecularDiameter()*1e-10 * 4.0 *
    // Math.pow(((PhaseModifiedFurstElectrolyteEosMod2004)
    // phase).calcShieldingParameter2(),2.0))*
  }

  // Short Range term equations and derivatives
  /**
   * <p>
   * dFSR2dN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFSR2dN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2eps() * epsi
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2W() * Wi;
  }

  /**
   * <p>
   * dFSR2dNdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFSR2dNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2W() * WiT
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2epsW() * epsi
            * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getWT();
  }

  /**
   * <p>
   * dFSR2dNdV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFSR2dNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 1.0e-5 * (+((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2epseps() * epsi
        * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsdV()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2eps() * epsiV
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2epsW() * Wi
            * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).getEpsdV()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2epsV() * epsi
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2VW() * Wi);
  }

  /**
   * <p>
   * dFSR2dNdN.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFSR2dNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2epseps() * epsi
        * ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
            .getComponent(j)).getEpsi()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2epsW() * Wi
            * ((ComponentModifiedFurstElectrolyteEosMod2004) ((PhaseModifiedFurstElectrolyteEosMod2004) phase)
                .getComponent(j)).getEpsi()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2W()
            * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).calcWij(componentNumber, j, phase,
                temperature, pressure, numberOfComponents)
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FSR2epsW() * epsi
            * ((PhaseModifiedFurstElectrolyteEosMod2004) phase).calcWi(j, phase, temperature,
                pressure, numberOfComponents);
  }

  /**
   * <p>
   * dEpsdNi.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dEpsdNi(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return avagadroNumber * pi / 6.0 * Math.pow(lennardJonesMolecularDiameter * 1.0e-10, 3.0)
        * (1.0 / (phase.getMolarVolume() * 1.0e-5 * phase.getNumberOfMolesInPhase()));
  }

  /**
   * <p>
   * dEpsdNidV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dEpsdNidV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return (-avagadroNumber * pi / 6.0 * Math.pow(lennardJonesMolecularDiameter * 1.0e-10, 3.0)
        * (1.0
            / (Math.pow(phase.getMolarVolume() * 1.0e-5 * phase.getNumberOfMolesInPhase(), 2.0))));
  }

  /**
   * <p>
   * dEpsIonicdNi.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dEpsIonicdNi(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    if (ionicCharge == 0) {
      return 0.0;
    } else {
      return pi / 6.0 * (avagadroNumber * Math.pow(lennardJonesMolecularDiameter * 1.0e-10, 3.0))
          * (1.0 / (phase.getMolarVolume() * 1.0e-5 * phase.getNumberOfMolesInPhase()));
    }
  }

  /**
   * <p>
   * dEpsIonicdNidV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dEpsIonicdNidV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    if (ionicCharge == 0) {
      return 0.0;
    }
    return (-avagadroNumber * pi / 6.0 * Math.pow(lennardJonesMolecularDiameter * 1e-10, 3.0)
        / (Math.pow(phase.getMolarVolume() * 1e-5 * phase.getNumberOfMolesInPhase(), 2.0)));
  }

  // Born term equations and derivatives
  /**
   * <p>
   * dFBorndN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFBorndN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FBornX() * getXBorni()
        + ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FBornD();
  }

  /**
   * <p>
   * dFBorndNdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFBorndNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FBornTX() * XBorni;
  }

  /**
   * <p>
   * dFBorndNdN.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFBorndNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /**
   * <p>
   * Getter for the field <code>ionicCoVolume</code>.
   * </p>
   *
   * @return a double
   */
  public double getIonicCoVolume() {
    return ionicCoVolume;
  }

  /**
   * <p>
   * getDiElectricConstantdn.
   * </p>
   *
   * @return a double
   */
  public double getDiElectricConstantdn() {
    return diElectricdn;
  }

  /**
   * <p>
   * getSolventDiElectricConstantdn.
   * </p>
   *
   * @return a double
   */
  public double getSolventDiElectricConstantdn() {
    return solventdiElectricdn;
  }

  /**
   * <p>
   * getBornVal.
   * </p>
   *
   * @return a double
   */
  public double getBornVal() {
    return bornterm;
  }

  /**
   * <p>
   * Getter for the field <code>epsi</code>.
   * </p>
   *
   * @return a double
   */
  public double getEpsi() {
    return epsi;
  }

  /**
   * <p>
   * Getter for the field <code>epsIonici</code>.
   * </p>
   *
   * @return a double
   */
  public double getEpsIonici() {
    return epsIonici;
  }

  /**
   * <p>
   * Getter for the field <code>alphai</code>.
   * </p>
   *
   * @return a double
   */
  public double getAlphai() {
    return alphai;
  }

  /**
   * <p>
   * getXLRi.
   * </p>
   *
   * @return a double
   */
  public double getXLRi() {
    return XLRi;
  }

  /**
   * <p>
   * getXBorni.
   * </p>
   *
   * @return a double
   */
  public double getXBorni() {
    return XBorni;
  }
}
