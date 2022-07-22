/*
 * ComponentEos.java
 *
 * Created on 14. mai 2000, 21:27
 */

package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.attractiveEosTerm.AtractiveTermMatCopPRUMRNew;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermCPAstatoil;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermGERG;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermInterface;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermMatCop;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermMatCopPR;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermMatCopPRUMR;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermMollerup;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermPr;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermPr1978;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermPrDanesh;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermPrDelft1998;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermPrGassem2001;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermRk;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermSchwartzentruber;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermSrk;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermTwu;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermTwuCoon;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermTwuCoonParam;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermTwuCoonStatoil;
import neqsim.thermo.component.attractiveEosTerm.AttractiveTermUMRPRU;
import neqsim.thermo.phase.PhaseInterface;

/**
 * @author Even Solbraa
 */
abstract class ComponentEos extends Component implements ComponentEosInterface {
  private static final long serialVersionUID = 1000;

  public double a = 1;

  public double b = 1;

  public double m = 0;

  public double alpha = 0;

  public double aT = 1;

  public double aDiffT = 0;

  public double Bi = 0;

  public double Ai = 0;

  public double AiT = 0;

  public double aDiffDiffT = 0;

  public double[] Aij = new double[MAX_NUMBER_OF_COMPONENTS];
  public double[] Bij = new double[MAX_NUMBER_OF_COMPONENTS];
  protected double delta1 = 0;

  protected double delta2 = 0;

  protected double aDern = 0;

  protected double aDerT = 0;

  protected double aDerTT = 0;

  protected double aDerTn = 0;

  protected double bDern = 0;

  protected double bDerTn = 0;

  protected double[] dAdndn = new double[MAX_NUMBER_OF_COMPONENTS];
  protected double[] dBdndn = new double[MAX_NUMBER_OF_COMPONENTS];
  private AttractiveTermInterface attractiveParameter;
  static Logger logger = LogManager.getLogger(ComponentEos.class);

  /**
   * <p>
   * Constructor for ComponentEos.
   * </p>
   *
   * @param component_name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compnumber a int
   */
  public ComponentEos(String component_name, double moles, double molesInPhase, int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);
  }

  /**
   * <p>
   * Constructor for ComponentEos.
   * </p>
   *
   * @param number a int
   * @param TC a double
   * @param PC a double
   * @param M a double
   * @param a a double
   * @param moles a double
   */
  public ComponentEos(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentEos clone() {
    ComponentEos clonedComponent = null;
    try {
      clonedComponent = (ComponentEos) super.clone();
    } catch (Exception e) {
      logger.error("Cloning failed.", e);
    }

    clonedComponent.setAttractiveParameter(this.getAttractiveParameter().clone());

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double temp, double pres, double totMoles, double beta, int type) {
    super.init(temp, pres, totMoles, beta, type);
    a = calca();
    b = calcb();
    reducedTemperature = reducedTemperature(temp);
    reducedPressure = reducedPressure(pres);
    aT = a * alpha(temp);
    if (type >= 2) {
      aDiffT = diffaT(temp);
      aDiffDiffT = diffdiffaT(temp);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta,
      int numberOfComponents, int type) {
    Bi = phase.calcBi(componentNumber, phase, temp, pres, numberOfComponents);
    Ai = phase.calcAi(componentNumber, phase, temp, pres, numberOfComponents);
    if (type >= 2) {
      AiT = phase.calcAiT(componentNumber, phase, temp, pres, numberOfComponents);
    }
    double totVol = phase.getMolarVolume() * phase.getNumberOfMolesInPhase();
    voli = -(-R * temp * dFdNdV(phase, numberOfComponents, temp, pres)
        + R * temp / (phase.getMolarVolume() * phase.getNumberOfMolesInPhase()))
        / (-R * temp * phase.dFdVdV()
            - phase.getNumberOfMolesInPhase() * R * temp / (totVol * totVol));

    if (type >= 3) {
      for (int j = 0; j < numberOfComponents; j++) {
        Aij[j] = phase.calcAij(componentNumber, j, phase, temp, pres, numberOfComponents);
        Bij[j] = phase.calcBij(componentNumber, j, phase, temp, pres, numberOfComponents);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setAttractiveTerm(int i) {
    attractiveTermNumber = i;
    if (i == 0) {
      setAttractiveParameter(new AttractiveTermSrk(this));
    } else if (i == 1) {
      setAttractiveParameter(new AttractiveTermPr(this));
    } else if (i == 2) {
      setAttractiveParameter(new AttractiveTermSchwartzentruber(this, getSchwartzentruberParams()));
    } else if (i == 3) {
      setAttractiveParameter(new AttractiveTermMollerup(this, getSchwartzentruberParams()));
    } else if (i == 4) {
      setAttractiveParameter(new AttractiveTermMatCop(this, getMatiascopemanParams()));
    } else if (i == 5) {
      setAttractiveParameter(new AttractiveTermRk(this));
    } else if (i == 6) {
      setAttractiveParameter(new AttractiveTermPr1978(this));
    } else if (i == 7) {
      setAttractiveParameter(new AttractiveTermPrDelft1998(this));
    } else if (i == 8) {
      setAttractiveParameter(new AttractiveTermPrGassem2001(this));
    } else if (i == 9) {
      setAttractiveParameter(new AttractiveTermPrDanesh(this));
    } else if (i == 10) {
      setAttractiveParameter(new AttractiveTermGERG(this));
    } else if (i == 11) {
      setAttractiveParameter(new AttractiveTermTwuCoon(this));
    } else if (i == 12) {
      setAttractiveParameter(new AttractiveTermTwuCoonParam(this, getTwuCoonParams()));
    } else if (i == 13) {
      setAttractiveParameter(new AttractiveTermMatCopPR(this, getMatiascopemanParamsPR()));
    } else if (i == 14) {
      setAttractiveParameter(new AttractiveTermTwu(this));
    } else if (i == 15) {
      setAttractiveParameter(new AttractiveTermCPAstatoil(this));
    } else if (i == 16) {
      setAttractiveParameter(new AttractiveTermUMRPRU(this));
    } else if (i == 17) {
      setAttractiveParameter(new AttractiveTermMatCopPRUMR(this));
    } else if (i == 18) {
      if (componentName.equals("mercury")) {
        setAttractiveParameter(new AttractiveTermTwuCoonStatoil(this, getTwuCoonParams()));
      } else {
        setAttractiveParameter(new AttractiveTermSrk(this));
      }
    } else if (i == 19) {
      setAttractiveParameter(new AtractiveTermMatCopPRUMRNew(this, getMatiascopemanParamsUMRPRU()));
    } else {
      logger.error("error selecting an alpha formultaion term");
      logger.info("ok setting alpha function");
    }
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermInterface getAttractiveTerm() {
    return this.getAttractiveParameter();
  }

  /**
   * @param temperature temperature of fluid
   * @return double reduced temperature T/TC
   */
  double reducedTemperature(double temperature) {
    return temperature / criticalTemperature;
  }

  /**
   * @param pressure pressure in unit bara
   * @return double
   */
  double reducedPressure(double pressure) {
    return pressure / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double geta() {
    return a;
  }

  /** {@inheritDoc} */
  @Override
  public double getb() {
    return b;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return phase.Fn() + phase.FB() * getBi() + phase.FD() * getAi();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return (phase.FBT() + phase.FBD() * phase.getAT()) * getBi() + phase.FDT() * getAi()
        + phase.FD() * getAiT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return phase.FnV() + phase.FBV() * getBi() + phase.FDV() * getAi();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
    return phase.FnB() * (getBi() + comp_Array[j].getBi())
        + phase.FBD() * (getBi() * comp_Array[j].getAi() + comp_Array[j].getBi() * getAi())
        + phase.FB() * getBij(j) + phase.FBB() * getBi() * comp_Array[j].getBi()
        + phase.FD() * getAij(j);
  }

  /** {@inheritDoc} */
  @Override
  public double getAi() {
    return Ai;
  }

  /** {@inheritDoc} */
  @Override
  public double getAiT() {
    return AiT;
  }

  /** {@inheritDoc} */
  @Override
  public double getBi() {
    return Bi;
  }

  /** {@inheritDoc} */
  @Override
  public double getBij(int j) {
    return Bij[j];
  }

  /** {@inheritDoc} */
  @Override
  public double getAij(int j) {
    return Aij[j];
  }

  /** {@inheritDoc} */
  @Override
  public double getaDiffT() {
    return aDiffT;
  }

  /** {@inheritDoc} */
  @Override
  public double getaDiffDiffT() {
    return aDiffDiffT;
  }

  /** {@inheritDoc} */
  @Override
  public double getaT() {
    return aT;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    logFugacityCoefficient = dFdN(phase, phase.getNumberOfComponents(), temperature, pressure)
        - Math.log(pressure * phase.getMolarVolume() / (R * temperature));
    fugacityCoefficient = Math.exp(logFugacityCoefficient);
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdP(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    dfugdp = getVoli() / R / temperature - 1.0 / pressure;
    return dfugdp;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdT(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    int numberOfComponents = phase.getNumberOfComponents();
    dfugdt = (this.dFdNdT(phase, numberOfComponents, temperature, pressure) + 1.0 / temperature
        - getVoli() / R / temperature
            * (-R * temperature * phase.dFdTdV() + pressure / temperature));
    return dfugdt;
  }

  /** {@inheritDoc} */
  @Override
  public double[] logfugcoefdN(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    int numberOfComponents = phase.getNumberOfComponents();
    ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getComponents();

    for (int i = 0; i < numberOfComponents; i++) {
      // System.out.println("dfdndn " + dFdNdN(i, phase, numberOfComponents,
      // temperature, pressure) + " n " + phase.getNumberOfMolesInPhase() + " voli " +
      // getVoli()/R/temperature + " dFdNdV " + comp_Array[i].dFdNdV(phase,
      // numberOfComponents, temperature, pressure));
      dfugdn[i] = (this.dFdNdN(i, phase, numberOfComponents, temperature, pressure)
          + 1.0 / phase.getNumberOfMolesInPhase() - getVoli() / R / temperature
              * (-R * temperature
                  * comp_Array[i].dFdNdV(phase, numberOfComponents, temperature, pressure)
                  + R * temperature / phase.getTotalVolume()));
      // System.out.println("dfugdn " + dfugdn[i]);
      // System.out.println("dFdndn " + dFdNdN(i, phase, numberOfComponents,
      // temperature, pressure) + " voli " + voli + " dFdvdn " +
      // comp_Array[i].dFdNdV(phase, numberOfComponents, temperature, pressure) + "
      // dfugdn " + dfugdn[i]);
      dfugdx[i] = dfugdn[i] * phase.getNumberOfMolesInPhase();
    }
    // System.out.println("diffN: " + 1 + dfugdn[0]);
    return dfugdn;
  }

  // Method added by Neeraj
  /*
   * public double getdfugdn(int i){ double[] dfugdnv = this.logfugcoefdN(phase); //return 0.0001;
   * return dfugdnv[i]; }
   */
  // Added By Neeraj
  /** {@inheritDoc} */
  @Override
  public double logfugcoefdNi(PhaseInterface phase, int k) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    int numberOfComponents = phase.getNumberOfComponents();
    double vol;
    double voli;
    ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
    vol = phase.getMolarVolume();
    voli = getVoli();

    dfugdn[k] = (this.dFdNdN(k, phase, numberOfComponents, temperature, pressure)
        + 1.0 / phase.getNumberOfMolesInPhase()
        - voli / R / temperature
            * (-R * temperature
                * comp_Array[k].dFdNdV(phase, numberOfComponents, temperature, pressure)
                + R * temperature / (vol * phase.getNumberOfMolesInPhase())));
    dfugdx[k] = dfugdn[k] * (phase.getNumberOfMolesInPhase());
    // System.out.println("Main dfugdn "+dfugdn[k]);
    return dfugdn[k];
  }

  /** {@inheritDoc} */
  @Override
  public double getAder() {
    return aDern;
  }

  /** {@inheritDoc} */
  @Override
  public void setAder(double val) {
    aDern = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getdAdndn(int j) {
    return dAdndn[j];
  }

  /** {@inheritDoc} */
  @Override
  public void setdAdndn(int jComp, double val) {
    dAdndn[jComp] = val;
  }

  /** {@inheritDoc} */
  @Override
  public void setdAdT(double val) {
    aDerT = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getdAdT() {
    return aDerT;
  }

  /** {@inheritDoc} */
  @Override
  public void setdAdTdn(double val) {
    aDerTn = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getdAdTdn() {
    return aDerTn;
  }

  /**
   * <p>
   * getdAdTdT.
   * </p>
   *
   * @return a double
   */
  public double getdAdTdT() {
    return aDerT;
  }

  /** {@inheritDoc} */
  @Override
  public void setdAdTdT(double val) {
    aDerTT = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getBder() {
    return bDern;
  }

  /** {@inheritDoc} */
  @Override
  public void setBder(double val) {
    bDern = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getdBdndn(int j) {
    return dBdndn[j];
  }

  /** {@inheritDoc} */
  @Override
  public void setdBdndn(int jComp, double val) {
    dBdndn[jComp] = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getdBdT() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public void setdBdTdT(double val) {}

  /** {@inheritDoc} */
  @Override
  public double getdBdndT() {
    return bDerTn;
  }

  /** {@inheritDoc} */
  @Override
  public void setdBdndT(double val) {
    bDerTn = val;
  }

  /**
   * <p>
   * alpha.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double alpha(double temperature) {
    return getAttractiveParameter().alpha(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double aT(double temperature) {
    return getAttractiveParameter().aT(temperature);
  }

  /**
   * <p>
   * diffalphaT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffalphaT(double temperature) {
    return getAttractiveParameter().diffalphaT(temperature);
  }

  /**
   * <p>
   * diffdiffalphaT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double diffdiffalphaT(double temperature) {
    return getAttractiveParameter().diffdiffalphaT(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return getAttractiveParameter().diffaT(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return getAttractiveParameter().diffdiffaT(temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double[] getDeltaEosParameters() {
    double[] param = {delta1, delta2};
    return param;
  }

  /** {@inheritDoc} */
  @Override
  public void seta(double a) {
    this.a = a;
  }

  /** {@inheritDoc} */
  @Override
  public void setb(double b) {
    this.b = b;
  }

  /** {@inheritDoc} */
  @Override
  public abstract double calca();

  /** {@inheritDoc} */
  @Override
  public abstract double calcb();

  /** {@inheritDoc} */
  @Override
  public double getSurfaceTenisionInfluenceParameter(double temperature) {
    /*
     * double a_inf = -3.471 + 4.927 * getCriticalCompressibilityFactor() + 13.085 *
     * Math.pow(getCriticalCompressibilityFactor(), 2.0) - 2.067 * getAcentricFactor() + 1.891 *
     * Math.pow(getAcentricFactor(), 2.0); double b_inf = -1.690 + 2.311 *
     * getCriticalCompressibilityFactor() + 5.644 * Math.pow(getCriticalCompressibilityFactor(),
     * 2.0) - 1.027 * getAcentricFactor() + 1.424 * Math.pow(getAcentricFactor(), 2.0); double c_inf
     * = -0.318 + 0.299 * getCriticalCompressibilityFactor() + 1.710 *
     * Math.pow(getCriticalCompressibilityFactor(), 2.0) - 0.174 * getAcentricFactor() + 0.157 *
     * Math.pow(getAcentricFactor(), 2.0);
     */
    double TR = 1.0 - temperature / getTC();
    if (TR < 1) {
      TR = 0.5;
    }

    // double scale1 = aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * Math.exp(a_inf + b_inf *
    // Math.log(TR) + c_inf * (Math.pow(Math.log(TR), 2.0))) /
    // Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 8.0 / 3.0);

    // System.out.println("scale1 " + scale1);
    // return scale1;
    // getAttractiveTerm().alpha(temperature)*1e-5 * Math.pow(b*1e-5, 2.0 / 3.0) *
    // Math.exp(a_inf + b_inf * Math.log(TR) + c_inf * (Math.pow(Math.log(TR),
    // 2.0)))/ Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 8.0 / 3.0);

    double AA = -1.0e-16 / (1.2326 + 1.3757 * getAcentricFactor());
    double BB = 1.0e-16 / (0.9051 + 1.541 * getAcentricFactor());

    // double scale2 = getAttractiveTerm().alpha(temperature) * 1e-5 * Math.pow(b * 1e-5, 2.0 /
    // 3.0) * (AA * TR + BB);
    // System.out.println("scale2 " + scale2);
    return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB); /// Math.pow(ThermodynamicConstantsInterface.avagadroNumber,
                                                                       /// 2.0 / 3.0);
  }

  /**
   * <p>
   * getAresnTV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getAresnTV(PhaseInterface phase) {
    return R * phase.getTemperature()
        * dFdN(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
  }

  /** {@inheritDoc} */
  @Override
  public double getChemicalPotential(PhaseInterface phase) {
    double entalp = getHID(phase.getTemperature()) * numberOfMolesInPhase;
    double entrop = numberOfMolesInPhase * getIdEntropy(phase.getTemperature());
    // double chempot = ((entalp - phase.getTemperature() * entrop) + numberOfMolesInPhase * R *
    // phase.getTemperature() * Math.log(numberOfMolesInPhase * R * phase.getTemperature() /
    // phase.getVolume() / referencePressure) + getAresnTV(phase) * numberOfMolesInPhase) /
    // numberOfMolesInPhase;

    // double chempot2 = super.getChemicalPotential(phase);
    // System.out.println("d " + chempot + " " + chempot2);
    return ((entalp - phase.getTemperature() * entrop) + numberOfMolesInPhase * R
        * phase.getTemperature() * Math.log(numberOfMolesInPhase * R * phase.getTemperature()
            / phase.getVolume() / referencePressure)
        + getAresnTV(phase) * numberOfMolesInPhase) / numberOfMolesInPhase;
    // return dF;
  }

  /**
   * <p>
   * getdUdnSV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getdUdnSV(PhaseInterface phase) {
    return getChemicalPotential(phase);
  }

  /**
   * <p>
   * getdUdSdnV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getdUdSdnV(PhaseInterface phase) {
    return -1.0 / phase.FTT()
        * dFdNdT(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
  }

  /**
   * <p>
   * getdUdVdnS.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getdUdVdnS(PhaseInterface phase) {
    return 1.0 / phase.FTT()
        * dFdNdV(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
  }

  /**
   * <p>
   * getdUdndnSV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param compNumb1 a int
   * @param compNumb2 a int
   * @return a double
   */
  public double getdUdndnSV(PhaseInterface phase, int compNumb1, int compNumb2) {
    return dFdNdN(compNumb2, phase, phase.getNumberOfComponents(), phase.getTemperature(),
        phase.getPressure())
        - dFdNdT(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure())
            * 1.0 / phase.FTT(); // *
    // phase.getComponent(compNumb2).getF;
  }

  /**
   * <p>
   * Getter for the field <code>attractiveParameter</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.component.attractiveEosTerm.AttractiveTermInterface} object
   */
  public AttractiveTermInterface getAttractiveParameter() {
    return attractiveParameter;
  }

  /**
   * <p>
   * Setter for the field <code>attractiveParameter</code>.
   * </p>
   *
   * @param attractiveParameter a
   *        {@link neqsim.thermo.component.attractiveEosTerm.AttractiveTermInterface} object
   */
  public void setAttractiveParameter(AttractiveTermInterface attractiveParameter) {
    this.attractiveParameter = attractiveParameter;
  }
}
