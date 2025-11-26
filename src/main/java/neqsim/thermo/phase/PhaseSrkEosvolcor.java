/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentSrkvolcor;

/**
 * <p>
 * PhasePrEosvolcor class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseSrkEosvolcor extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  double loc_C = 0;
  private double CT;
  public double C;
  public double Ctot = 0;

  /**
   * Creates new PhaseSrkEos.
   */
  public PhaseSrkEosvolcor() {
    thermoPropertyModelName = "SRK-EoS-volcorr";
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    loc_C = calcC(this, temperature, pressure, numberOfComponents);
    CT = calcCT(this, temperature, pressure, numberOfComponents);
  }

  /**
   * <p>
   * getCT.
   * </p>
   *
   * @return a double
   */
  public double getCT() {
    return CT;
  }

  /**
   * <p>
   * getCTT.
   * </p>
   *
   * @return a double
   */
  public double getCTT() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcg() {
    return Math.log(1.0 - (getb() - getc()) / molarVolume);
  }

  /** {@inheritDoc} */
  @Override
  public double calcf() {
    return (1.0 / (R * getB() * (delta1 - delta2))
        * Math.log((1.0 + (delta1 * getb() + getc()) / molarVolume)
            / (1.0 + (delta2 * getb() + getc()) / (molarVolume))));
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    // return super.dFdV();
    return -numberOfMolesInPhase * gV() - getA() / temperature * fv();
  }

  // note that in future the next thre lines should be modified to handle various mixing rules for
  // the translation

  /**
   * <p>
   * getcij.
   * </p>
   *
   * @param compArray a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @param compArray2 a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @return a double
   */
  public double getcij(ComponentEosInterface compArray, ComponentEosInterface compArray2) {
    return ((((ComponentSrkvolcor) compArray).getc()) + (((ComponentSrkvolcor) compArray2).getc()))
        * 0.5;
  }

  /**
   * <p>
   * getcijT.
   * </p>
   *
   * @param compArray a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @param compArray2 a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @return a double
   */
  public double getcijT(ComponentEosInterface compArray, ComponentEosInterface compArray2) {
    return (((ComponentSrkvolcor) compArray).getcT() + ((ComponentSrkvolcor) compArray2).getcT())
        * 0.5;
  }

  /**
   * <p>
   * getcijTT.
   * </p>
   *
   * @param compi a {@link neqsim.thermo.component.ComponentSrkvolcor} object
   * @param compj a {@link neqsim.thermo.component.ComponentSrkvolcor} object
   * @return a double
   */
  public double getcijTT(ComponentSrkvolcor compi, ComponentSrkvolcor compj) {
    // return (compi.getcTT() + compj.getcTT()) * 0.5;
    return 0;
  }

  // @Override
  /**
   * <p>
   * calcCi.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcCi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    double Ci = 0.0;

    ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

    for (int j = 0; j < numbcomp; j++) {
      Ci += compArray[j].getNumberOfMolesInPhase() * getcij(compArray[compNumb], compArray[j]);
    }

    Ci = (2.0 * Ci - getC()) / phase.getNumberOfMolesInPhase();
    return Ci;
  }

  /**
   * <p>
   * calcCij.
   * </p>
   *
   * @param compNumb a int
   * @param compNumbj a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcCij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    double cij = 0.0;
    ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

    cij = getcij(compArray[compNumb], compArray[compNumbj]);
    return (2.0 * cij - ((ComponentSrkvolcor) compArray[compNumb]).getCi()
        - ((ComponentSrkvolcor) compArray[compNumbj]).getCi()) / phase.getNumberOfMolesInPhase();
  }

  /**
   * <p>
   * calcCiT.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcCiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    double CiT = 0.0;

    ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

    for (int j = 0; j < numbcomp; j++) {
      CiT += compArray[j].getNumberOfMolesInPhase() * getcijT(compArray[compNumb], compArray[j]);
    }

    CiT = (2.0 * CiT - getCT()) / phase.getNumberOfMolesInPhase();
    return CiT;
  }

  /**
   * <p>
   * calcCT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcCT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    double locCT = 0.0;
    ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

    for (int i = 0; i < numbcomp; i++) {
      for (int j = 0; j < numbcomp; j++) {
        locCT += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
            * getcijT(compArray[i], compArray[j]);
      }
    }

    return locCT / phase.getNumberOfMolesInPhase();
  }

  /**
   * <p>
   * calcC.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double calcC(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    C = 0.0;
    ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

    for (int i = 0; i < numbcomp; i++) {
      for (int j = 0; j < numbcomp; j++) {
        C += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
            * getcij(compArray[i], compArray[j]); // (compArray[i].getb()+compArray[j].getb())/2;
      }
    }
    C /= phase.getNumberOfMolesInPhase();
    Ctot = C;
    return C;
  }

  private double loc_C() {
    return calcC(this, temperature, pressure, numberOfComponents);
  }

  /**
   * <p>
   * getc.
   * </p>
   *
   * @return a double
   */
  public double getc() {
    return loc_C() / numberOfMolesInPhase;
  }

  /**
   * <p>
   * getC.
   * </p>
   *
   * @return a double
   */
  public double getC() {
    return loc_C();
  }

  /** {@inheritDoc} */
  @Override
  public double gV() {
    return (getb() - getc())
        / (molarVolume * (numberOfMolesInPhase * molarVolume + loc_C() - getB()));
    // molarvolume is m^3/mol/10^5
    // old is-->return getb() / (molarVolume * (numberOfMolesInPhase * molarVolume - loc_B));
    // aks Dr. Soolbra whats the difference between getb and loc_B and
    // why the molar volume in the bracket is multiplied by the numberofmolesinphase (is it because
    // of the units of molarvolume?)
  }

  /** {@inheritDoc} */
  @Override
  public double gVV() {
    double val1 = numberOfMolesInPhase * getMolarVolume();
    double val2 = val1 + getC() - getB();
    return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);

    // old is -->double val1 = numberOfMolesInPhase * getMolarVolume();
    // double val2 = val1 + getC - getB();
    // return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);
  }

  /** {@inheritDoc} */
  @Override
  public double gVVV() {
    double val1 = numberOfMolesInPhase * getMolarVolume();
    double val2 = val1 + getC() - getB();
    return 2.0 / (val2 * val2 * val2) - 2.0 / (val1 * val1 * val1);
  }

  /** {@inheritDoc} */
  @Override
  public double fv() {
    return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C())
        * (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C()));

    // OLD IS--> return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * loc_B)
    // * (numberOfMolesInPhase * molarVolume + delta2 * loc_B));
  }

  /** {@inheritDoc} */
  @Override
  public double fVV() {
    double val1 = (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C());
    double val2 = (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C());
    return 1.0 / (R * getB() * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));

    // old is-->double val1 = (numberOfMolesInPhase * molarVolume + delta1 * loc_B);
    // double val2 = (numberOfMolesInPhase * molarVolume + delta2 * loc_B);
    // return 1.0 / (R * loc_B * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));
  }

  /** {@inheritDoc} */
  @Override
  public double fVVV() {
    double val1 = numberOfMolesInPhase * molarVolume + getB() * delta1 + getC();
    double val2 = numberOfMolesInPhase * molarVolume + getB() * delta2 + getC();
    return 1.0 / (R * getB() * (delta1 - delta2))
        * (2.0 / (val1 * val1 * val1) - 2.0 / (val2 * val2 * val2));

    // old is -->double val1 = numberOfMolesInPhase * molarVolume + getB() * delta1;
    // double val2 = numberOfMolesInPhase * molarVolume + getB() * delta2;
    // return 1.0 / (R * getB() * (delta1 - delta2)) * (2.0 / (val1 * val1 * val1) - 2.0 / (val2 *
    // val2 * val2));
  }

  // derivative of small g with regards to b
  // problem with the loc_b in gb(),gc()-->it says that it is not visible and I think this is
  // because loc_B is marked as private
  // in PhaseEoS...so for now I switch it to getb and we will see

  /** {@inheritDoc} */
  @Override
  public double gb() {
    // return -1.0 / (numberOfMolesInPhase * molarVolume - loc_B + loc_C);
    return -1.0 / (numberOfMolesInPhase * molarVolume - getB() + getC());
  }

  // derivative of small g with regards to c
  /**
   * <p>
   * gc.
   * </p>
   *
   * @return a double
   */
  public double gc() {
    // return 1.0 / (numberOfMolesInPhase * molarVolume - loc_B + loc_C);
    return 1.0 / (numberOfMolesInPhase * molarVolume - getB() + getC());
  }

  // derivative of small f with regards to c-->equal to fv
  /**
   * <p>
   * fc.
   * </p>
   *
   * @return a double
   */
  public double fc() {
    return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C())
        * (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C()));
  }

  /** {@inheritDoc} */
  @Override
  public double fb() {
    return -(calcf() + (numberOfMolesInPhase * molarVolume + getC()) * fv()) / getB();
  }

  // second derivative of small f with regards to cc-->equal to fvv
  /**
   * <p>
   * fcc.
   * </p>
   *
   * @return a double
   */
  public double fcc() {
    return fVV();
  }

  // second derivative of small f with regards to bc-->equal to fvv
  /**
   * <p>
   * fbc.
   * </p>
   *
   * @return a double
   */
  public double fbc() {
    return fBV();
  }

  // second derivative of small f with regards to cv-->equal to fvv
  /**
   * <p>
   * fcv.
   * </p>
   *
   * @return a double
   */
  public double fcv() {
    return fVV();
  }

  // second derivative of small f with regards to bv-->
  /** {@inheritDoc} */
  @Override
  public double fBV() {
    return -(2.0 * fv() + (numberOfMolesInPhase * molarVolume + getC()) * fVV()) / getB();
  }

  // second derivative of small f with regards to bb-->
  /** {@inheritDoc} */
  @Override
  public double fBB() {
    return -(2.0 * fb() + (numberOfMolesInPhase * molarVolume + getC()) * fBV()) / getB();
  }

  // second derivative of small g with regards to bv-->
  /** {@inheritDoc} */
  @Override
  public double gBV() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return 1.0 / (val * val);
  }

  // second derivative of small g with regards to bb-->
  /** {@inheritDoc} */
  @Override
  public double gBB() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  // second derivative of small g with regards to bc-->
  /**
   * <p>
   * gBC.
   * </p>
   *
   * @return a double
   */
  public double gBC() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return 1.0 / (val * val);
  }

  // second derivative of small g with regards to cv-->
  /**
   * <p>
   * gCV.
   * </p>
   *
   * @return a double
   */
  public double gCV() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  // second derivative of small g with regards to cc-->
  /**
   * <p>
   * gCC.
   * </p>
   *
   * @return a double
   */
  public double gCC() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  // Below are the partial derivatives of F with regards to model parameters

  /** {@inheritDoc} */
  @Override
  public double F() {
    return super.F();
  }

  // derivative of big F with regards to C
  // @Override
  /**
   * <p>
   * FC.
   * </p>
   *
   * @return a double
   */
  public double FC() {
    return -numberOfMolesInPhase * gc() - getA() / temperature * fc();
  }

  /**
   * <p>
   * FnC.
   * </p>
   *
   * @return a double
   */
  public double FnC() {
    return -gc();
  }

  /**
   * <p>
   * FTC.
   * </p>
   *
   * @return a double
   */
  public double FTC() {
    return getA() * fc() / temperature / temperature;
  }

  /**
   * <p>
   * FBC.
   * </p>
   *
   * @return a double
   */
  public double FBC() {
    return -numberOfMolesInPhase * gBC() - getA() * fbc() / temperature;
  }

  /**
   * <p>
   * FCV.
   * </p>
   *
   * @return a double
   */
  public double FCV() {
    return -numberOfMolesInPhase * gCV() - getA() * fcv() / temperature;
  }

  /**
   * <p>
   * FCC.
   * </p>
   *
   * @return a double
   */
  public double FCC() {
    return -numberOfMolesInPhase * gCC() - getA() * fcc() / temperature;
  }

  /**
   * <p>
   * FCD.
   * </p>
   *
   * @return a double
   */
  public double FCD() {
    return -fc() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return -numberOfMolesInPhase * gVV() - getA() * fVV() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return -numberOfMolesInPhase * gVVV() - getA() * fVVV() / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return FTV() + FDV() * getAT() + FCV() * getCT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return FT() + FD() * getAT() + FC() * getCT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return FTT() + 2.0 * FDT() * getAT() + FD() * getATT() + 2 * FTC() * getCT()
        + FCC() * getCT() * getCT() + FC() * getCTT() + 2 * FCD() * getCT() * getAT();
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkEosvolcor clone() {
    PhaseSrkEosvolcor clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkEosvolcor) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSrkvolcor(name, moles, molesInPhase, compNumber);
  }
}
