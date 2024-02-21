/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */

package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEosvolcor;

/**
 * <p>
 * ComponentPRvolcor class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentPRvolcor extends ComponentPR {
  private static final long serialVersionUID = 1000;
  private double c;
  // private double calcc;
  public double[] Cij = new double[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  public double Ci = 0;

  /**
   * <p>
   * Constructor for ComponentPRvolcor.
   * </p>
   *
   * @param number a int
   * @param TC a double
   * @param PC a double
   * @param M a double
   * @param a a double
   * @param moles a double
   */
  public ComponentPRvolcor(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /// ** {@inheritDoc} */
  // @Override
  /**
   * <p>
   * calcc.
   * </p>
   *
   * @return a double
   */
  public double calcc() {
    return (0.1154 - 0.4406 * (0.29056 - 0.08775 * getAcentricFactor())) * R * criticalTemperature
        / criticalPressure;
  }

  // derivative of translation with regards to temperature
  /**
   * <p>
   * calccT.
   * </p>
   *
   * @return a double
   */
  public double calccT() {
    return 0.;
  }

  // second derivative of translation with regards to temperature*temperature
  /**
   * <p>
   * calccTT.
   * </p>
   *
   * @return a double
   */
  public double calccTT() {
    return 0.;
  }

  /**
   * <p>
   * Constructor for ComponentPRvolcor.
   * </p>
   *
   * @param component_name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compnumber a int
   */
  public ComponentPRvolcor(String component_name, double moles, double molesInPhase,
      int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);
    c = (0.1154 - 0.4406 * (0.29056 - 0.08775 * getAcentricFactor())) * R * criticalTemperature
        / criticalPressure;

  }

  /** {@inheritDoc} */
  @Override
  public void init(double temp, double pres, double totMoles, double beta, int type) {
    super.init(temp, pres, totMoles, beta, type);
    c = calcc();
  }

  /** {@inheritDoc} */
  public double getc() {
    return c;
  }

  /**
   * <p>
   * getcT.
   * </p>
   *
   * @return a double
   */
  public double getcT() {
    return 0;
  }

  // derivative of C with regards to mole fraction
  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta,
      int numberOfComponents, int type) {
    super.Finit(phase, temp, pres, totMoles, beta, numberOfComponents, type);
    Ci = ((PhasePrEosvolcor) phase).calcCi(componentNumber, phase, temp, pres, numberOfComponents);
    if (type >= 2) {
      ((PhasePrEosvolcor) phase).calcCiT(componentNumber, phase, temp, pres, numberOfComponents);
    }

    if (type >= 3) {
      for (int j = 0; j < numberOfComponents; j++) {
        Cij[j] = ((PhasePrEosvolcor) phase).calcCij(componentNumber, j, phase, temp, pres,
            numberOfComponents);
      }
    }
  }

  /**
   * <p>
   * getCi.
   * </p>
   *
   * @return a double
   */
  public double getCi() {
    return Ci;
  }

  /**
   * <p>
   * getCij.
   * </p>
   *
   * @param j a int
   * @return a double
   */
  public double getCij(int j) {
    return Cij[j];
  }

  // depending on the type of the volume translation (T-dependent or not) these sould be properly
  // modified

  // second derivative of C with regards to mole fraction and temperature
  /**
   * <p>
   * getCiT.
   * </p>
   *
   * @return a double
   */
  public double getCiT() {
    return 0;
  }

  /**
   * <p>
   * getcTT.
   * </p>
   *
   * @return a double
   */
  public double getcTT() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return phase.Fn() + phase.FB() * getBi() + phase.FD() * getAi()
        + ((PhasePrEosvolcor) phase).FC() * getCi();
  }

  /**
   * <p>
   * getFC.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getFC(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return ((PhasePrEosvolcor) phase).FC();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double loc_CT = ((PhasePrEosvolcor) phase).getCT();
    double loc_FnC = ((PhasePrEosvolcor) phase).FnC();
    double loc_FBC = ((PhasePrEosvolcor) phase).FBC();
    double loc_FCD = ((PhasePrEosvolcor) phase).FCD();
    double loc_FC = ((PhasePrEosvolcor) phase).FC();
    double loc_FCT = ((PhasePrEosvolcor) phase).FTC();
    double loc_FCC = ((PhasePrEosvolcor) phase).FCC();

    return loc_FnC * loc_CT
        + (phase.FBT() + phase.FBD() * phase.getAT() + loc_FBC * loc_CT) * getBi()
        + (loc_FCD * loc_CT + phase.FDT()) * getAi()
        + (loc_FCT + loc_FCC * loc_CT + loc_FCD * phase.getAT()) * getCi() + phase.FD() * getAiT()
        + loc_FC * getCiT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double loc_FnC = ((PhasePrEosvolcor) phase).FnC();
    double loc_FBC = ((PhasePrEosvolcor) phase).FBC();
    double loc_FCD = ((PhasePrEosvolcor) phase).FCD();
    double loc_FC = ((PhasePrEosvolcor) phase).FC();
    double loc_FCC = ((PhasePrEosvolcor) phase).FCC();
    ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
    // return phase.FnB() * (getBi() + comp_Array[j].getBi())
    // + phase.FBD() * (getBi() * comp_Array[j].getAi() + comp_Array[j].getBi() * getAi())
    // + phase.FB() * getBij(j) + phase.FBB() * getBi() * comp_Array[j].getBi()
    // + phase.FD() * getAij(j);
    double loc_Cj = ((ComponentPRvolcor) comp_Array[j]).getCi();
    // double loc_Ci= ((ComponentPRvolcor))component.getCi();
    return phase.FnB() * comp_Array[j].getBi() + loc_FnC * loc_Cj
        + (phase.FnB() + phase.FBB() * comp_Array[j].getBi() + loc_FBC * loc_Cj
            + phase.FBD() * comp_Array[j].getAi()) * getBi()
        + phase.FB() * getBij(j)
        + (loc_FnC + loc_FCC * loc_Cj + loc_FBC * comp_Array[j].getBi()
            + loc_FCD * comp_Array[j].getAi()) * getCi()
        + loc_FC * getCij(j) + (loc_FCD * loc_Cj + phase.FBD() * comp_Array[j].getBi()) * getAi()
        + phase.FD() * getAij(j);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double loc_FCV = ((PhasePrEosvolcor) phase).FCV();
    return phase.FnV() + phase.FBV() * getBi() + phase.FDV() * getAi() + loc_FCV * getCi();
  }

  // Remember this trick above that professor showed to you...you can call whichever new F
  // expression
  // you need by first specifying the type "PhasePrEosvolcor"
}
