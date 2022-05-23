/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentPRvolcor;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhasePrEosvolcor extends PhasePrEos {

  private static final long serialVersionUID = 1000;

  /** Creates new PhaseSrkEos */
  public PhasePrEosvolcor() {
    super();
    thermoPropertyModelName = "PR-EoS-volcorr";
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);

  }

  public double calcg() {
    return Math.log(1.0 - (getb() - getc()) / molarVolume);
  }

  public double calcf() {
    return (1.0 / (R * getB() * (delta1 - delta2))
        * Math.log((1.0 + (delta1 * getb() + getc()) / molarVolume)
            / (1.0 + (delta2 * getb() + getc()) / (molarVolume))));
  }

  @Override
  public double dFdV() {
    // return super.dFdV();
    return -numberOfMolesInPhase * gV() - getA() / temperature * fv();

  }



  public double getcij(ComponentPRvolcor compi, ComponentPRvolcor compj) {
    return (compi.getc() + compj.getc()) * 0.5;
  }



  // @Override
  public double calcCi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    double Ci = 0.0;

    ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

    for (int j = 0; j < numbcomp; j++) {
      // Ci += compArray[j].getNumberOfMolesInPhase() * getcij(compArray[compNumb], compArray[j]);
    }

    Ci = (2.0 * Ci - getC()) / phase.getNumberOfMolesInPhase();
    return Ci;
  }

  public double loc_C() {
    return 0.0;
  }

  public double getc() {
    return loc_C() / numberOfMolesInPhase;
  }

  public double getC() {
    return loc_C();
  }



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

  @Override
  public double gVV() {
    double val1 = numberOfMolesInPhase * getMolarVolume();
    double val2 = val1 + getC() - getB();
    return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);

    // old is -->double val1 = numberOfMolesInPhase * getMolarVolume();
    // double val2 = val1 + getC - getB();
    // return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);
  }

  public double gVVV() {
    double val1 = numberOfMolesInPhase * getMolarVolume();
    double val2 = val1 + getC() - getB();
    return 2.0 / (val2 * val2 * val2) - 2.0 / (val1 * val1 * val1);
  }

  @Override
  public double fv() {
    return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C())
        * (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C()));

    // OLD IS--> return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * loc_B)
    // * (numberOfMolesInPhase * molarVolume + delta2 * loc_B));
  }

  @Override
  public double fVV() {
    double val1 = (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C());
    double val2 = (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C());
    return 1.0 / (R * getB() * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));

    // old is-->double val1 = (numberOfMolesInPhase * molarVolume + delta1 * loc_B);
    // double val2 = (numberOfMolesInPhase * molarVolume + delta2 * loc_B);
    // return 1.0 / (R * loc_B * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));
  }

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

  @Override
  public double gb() {
    // return -1.0 / (numberOfMolesInPhase * molarVolume - loc_B + loc_C);
    return -1.0 / (numberOfMolesInPhase * molarVolume - getB() + getC());
  }

  //// derivative of small g with regards to c
  public double gc() {
    // return 1.0 / (numberOfMolesInPhase * molarVolume - loc_B + loc_C);
    return 1.0 / (numberOfMolesInPhase * molarVolume - getB() + getC());
  }

  //// derivative of small f with regards to c-->equal to fv
  public double fc() {
    return fv();
  }

  //// second derivative of small f with regards to cc-->equal to fvv
  public double fcc() {
    return fVV();
  }

  //// second derivative of small f with regards to bc-->equal to fvv
  public double fbc() {
    return fBV();
  }

  //// second derivative of small f with regards to cv-->equal to fvv
  public double fcv() {
    return fVV();
  }

  //// second derivative of small f with regards to bv-->
  @Override
  public double fBV() {
    return -(2.0 * fv() + (numberOfMolesInPhase * molarVolume + getC()) * fVV()) / getB();
  }

  //// second derivative of small f with regards to bb-->
  @Override
  public double fBB() {
    return -(2.0 * fb() + (numberOfMolesInPhase * molarVolume + getC()) * fBV()) / getB();
  }

  //// second derivative of small g with regards to bv-->
  @Override
  public double gBV() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return 1.0 / (val * val);
  }

  //// second derivative of small g with regards to bb-->
  @Override
  public double gBB() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  //// second derivative of small g with regards to bc-->
  public double gBC() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return 1.0 / (val * val);
  }

  //// second derivative of small g with regards to cv-->
  public double gCV() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  //// second derivative of small g with regards to cc-->
  public double gCC() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  @Override
  public double dFdVdV() {
    return -numberOfMolesInPhase * gVV() - getA() * fVV() / temperature;
  }

  @Override
  public double dFdVdVdV() {
    return -numberOfMolesInPhase * gVVV() - getA() * fVVV() / temperature;
  }

  @Override
  public double dFdTdV() {
    return super.dFdTdV();
  }

  @Override
  public double F() {
    return super.F();
  }

  // derivative of big F with regards to C
  // @Override
  public double FC() {
    return -numberOfMolesInPhase * gc() - getA() / temperature * fc();
  }


  @Override
  public double dFdTdT() {
    return super.dFdTdT();
  }

  @Override
  public double dFdT() {
    return super.dFdT();
  }


  @Override
  public PhasePrEosvolcor clone() {
    PhasePrEosvolcor clonedPhase = null;
    try {
      clonedPhase = (PhasePrEosvolcor) super.clone();
    } catch (Exception e) {
      logger.error("Cloning failed.", e);
    }

    return clonedPhase;
  }

  @Override
  public void addcomponent(String componentName, double moles, double molesInPhase,
      int compNumber) {
    super.addcomponent(molesInPhase);
    componentArray[compNumber] =
        new ComponentPRvolcor(componentName, moles, molesInPhase, compNumber);
  }

}
