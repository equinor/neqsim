/*
 * PhaseUMRCPAvolcor.java
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentUMRCPAvolcor;

/**
 * <p>
 * PhaseUMRCPAvolcor class.
 * </p>
 *
 * <p>
 * Volume-translated UMR-CPA phase. It keeps the full UMR-CPA solve pipeline (PR physical term with
 * UMR/UNIFAC mixing, Mathias-Copeman alpha and the Wertheim association term) inherited from
 * {@link PhaseUMRCPA} and adds a consistent per-component Peneloux volume translation by overriding
 * the reduced residual Helmholtz energy primitives in the same way as {@link PhasePrEosvolcor}.
 * </p>
 *
 * <p>
 * The translation parameter C (extensive) is introduced into the cubic g- and f-functions and into
 * every volume/temperature/mole derivative used by the solver and property routines. Because
 * {@link PhaseEos#getF()} reads the cached g and f values that are populated from the overridden
 * {@link #calcg()} and {@link #calcf()} during {@code init}, the translation propagates
 * automatically into the residual Helmholtz energy. The temperature derivatives, however, are
 * augmented explicitly with the C-temperature cross terms (FC, FTC, FCC, FCD) and the CPA
 * contribution, so density, fugacity coefficients and density-derived caloric/acoustic properties
 * are all consistent with the translated volume.
 * </p>
 *
 * <p>
 * The translation acts only on the cubic part (Option A): the association term is evaluated on the
 * physical co-volume b and on the (translated) physical molar volume returned by the solver. The
 * per-component translation c equals the inherited UMR-CPA Peneloux shift (PR shift for
 * non-associating compounds, zero for associating compounds until a UMR-CPA Rackett Z is
 * regressed).
 * </p>
 *
 * <p>
 * Note: the cubic translation machinery is duplicated from {@link PhasePrEosvolcor} because Java
 * single inheritance prevents reusing both the UMR-CPA solver and the PRvolcor cubic phase. Keep
 * the two implementations in sync if either is changed.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseUMRCPAvolcor extends PhaseUMRCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Extensive volume translation parameter C [m^3]. */
  double loc_C = 0;
  /** Temperature derivative of the extensive translation parameter dC/dT. */
  private double CT;
  /** Extensive volume translation parameter (mixing-rule result). */
  public double C;
  /** Total extensive volume translation parameter. */
  public double Ctot = 0;
  private double[] cachedCi;
  private double[] cachedCiT;
  private double[][] cachedCij;

  /**
   * Creates new PhaseUMRCPAvolcor.
   */
  public PhaseUMRCPAvolcor() {
    thermoPropertyModelName = "UMR-CPA-volcorr";
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    cachedCi = null;
    cachedCiT = null;
    cachedCij = null;
    loc_C = calcC(this, temperature, pressure, numberOfComponents);
    CT = calcCT(this, temperature, pressure, numberOfComponents);
    if (initType >= 1) {
      ensureCiCache(numberOfComponents);
    }
    if (initType >= 2) {
      ensureCiTCache(numberOfComponents);
    }
    if (initType >= 3) {
      ensureCijCache(numberOfComponents);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentUMRCPAvolcor(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseUMRCPAvolcor clone() {
    PhaseUMRCPAvolcor clonedPhase = null;
    try {
      clonedPhase = (PhaseUMRCPAvolcor) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /**
   * <p>
   * getCT.
   * </p>
   *
   * @return dC/dT
   */
  public double getCT() {
    return CT;
  }

  /**
   * <p>
   * getCTT.
   * </p>
   *
   * @return d2C/dT2 (zero for a linear, temperature-independent translation mixing rule)
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

  /**
   * <p>
   * getcij - linear translation mixing rule c_ij = (c_i + c_j)/2.
   * </p>
   *
   * @param compArray a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @param compArray2 a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @return a double
   */
  public double getcij(ComponentEosInterface compArray, ComponentEosInterface compArray2) {
    return ((((ComponentUMRCPAvolcor) compArray).getc())
        + (((ComponentUMRCPAvolcor) compArray2).getc())) * 0.5;
  }

  /**
   * <p>
   * getcijT - temperature derivative of the translation mixing rule.
   * </p>
   *
   * @param compArray a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @param compArray2 a {@link neqsim.thermo.component.ComponentEosInterface} object
   * @return a double
   */
  public double getcijT(ComponentEosInterface compArray, ComponentEosInterface compArray2) {
    return (((ComponentUMRCPAvolcor) compArray).getcT()
        + ((ComponentUMRCPAvolcor) compArray2).getcT()) * 0.5;
  }

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
    ensureCiCache(numbcomp);
    return cachedCi[compNumb];
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
    ensureCijCache(numbcomp);
    return cachedCij[compNumb][compNumbj];
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
    ensureCiTCache(numbcomp);
    return cachedCiT[compNumb];
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

  private void ensureCiCache(int numbcomp) {
    if (cachedCi != null && cachedCi.length >= numbcomp) {
      return;
    }
    cachedCi = new double[numbcomp];
    ComponentEosInterface[] compArray = (ComponentEosInterface[]) this.getcomponentArray();
    double totalMolesInPhase = getNumberOfMolesInPhase();
    for (int i = 0; i < numbcomp; i++) {
      double CiVal = 0.0;
      for (int j = 0; j < numbcomp; j++) {
        CiVal += compArray[j].getNumberOfMolesInPhase() * getcij(compArray[i], compArray[j]);
      }
      cachedCi[i] = (2.0 * CiVal - getC()) / totalMolesInPhase;
    }
  }

  private void ensureCiTCache(int numbcomp) {
    if (cachedCiT != null && cachedCiT.length >= numbcomp) {
      return;
    }
    cachedCiT = new double[numbcomp];
    ComponentEosInterface[] compArray = (ComponentEosInterface[]) this.getcomponentArray();
    double totalMolesInPhase = getNumberOfMolesInPhase();
    for (int i = 0; i < numbcomp; i++) {
      double CiTVal = 0.0;
      for (int j = 0; j < numbcomp; j++) {
        CiTVal += compArray[j].getNumberOfMolesInPhase() * getcijT(compArray[i], compArray[j]);
      }
      cachedCiT[i] = (2.0 * CiTVal - getCT()) / totalMolesInPhase;
    }
  }

  private void ensureCijCache(int numbcomp) {
    if (cachedCij != null && cachedCij.length >= numbcomp) {
      return;
    }
    cachedCij = new double[numbcomp][numbcomp];
    ComponentEosInterface[] compArray = (ComponentEosInterface[]) this.getcomponentArray();
    double totalMolesInPhase = getNumberOfMolesInPhase();
    for (int i = 0; i < numbcomp; i++) {
      for (int j = 0; j < numbcomp; j++) {
        double cij = getcij(compArray[i], compArray[j]);
        cachedCij[i][j] = (2.0 * cij - ((ComponentUMRCPAvolcor) compArray[i]).getCi()
            - ((ComponentUMRCPAvolcor) compArray[j]).getCi()) / totalMolesInPhase;
      }
    }
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
            * getcij(compArray[i], compArray[j]);
      }
    }
    C /= phase.getNumberOfMolesInPhase();
    Ctot = C;
    loc_C = C;
    return C;
  }

  private double loc_C() {
    return loc_C;
  }

  /**
   * <p>
   * getc - intensive (molar) volume translation parameter.
   * </p>
   *
   * @return a double
   */
  public double getc() {
    return loc_C() / numberOfMolesInPhase;
  }

  /**
   * <p>
   * getC - extensive volume translation parameter.
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
  }

  /** {@inheritDoc} */
  @Override
  public double gVV() {
    double val1 = numberOfMolesInPhase * getMolarVolume();
    double val2 = val1 + getC() - getB();
    return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);
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
  }

  /** {@inheritDoc} */
  @Override
  public double fVV() {
    double val1 = (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C());
    double val2 = (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C());
    return 1.0 / (R * getB() * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));
  }

  /** {@inheritDoc} */
  @Override
  public double fVVV() {
    double val1 = numberOfMolesInPhase * molarVolume + getB() * delta1 + getC();
    double val2 = numberOfMolesInPhase * molarVolume + getB() * delta2 + getC();
    return 1.0 / (R * getB() * (delta1 - delta2))
        * (2.0 / (val1 * val1 * val1) - 2.0 / (val2 * val2 * val2));
  }

  /** {@inheritDoc} */
  @Override
  public double gb() {
    return -1.0 / (numberOfMolesInPhase * molarVolume - getB() + getC());
  }

  /**
   * <p>
   * gc - derivative of small g with respect to the translation parameter c.
   * </p>
   *
   * @return a double
   */
  public double gc() {
    return 1.0 / (numberOfMolesInPhase * molarVolume - getB() + getC());
  }

  /**
   * <p>
   * fc - derivative of small f with respect to the translation parameter c.
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

  /**
   * <p>
   * fcc - second derivative of small f with respect to c.
   * </p>
   *
   * @return a double
   */
  public double fcc() {
    return fVV();
  }

  /**
   * <p>
   * fbc - second cross derivative of small f with respect to b and c.
   * </p>
   *
   * @return a double
   */
  public double fbc() {
    return fBV();
  }

  /**
   * <p>
   * fcv - second cross derivative of small f with respect to c and V.
   * </p>
   *
   * @return a double
   */
  public double fcv() {
    return fVV();
  }

  /** {@inheritDoc} */
  @Override
  public double fBV() {
    return -(2.0 * fv() + (numberOfMolesInPhase * molarVolume + getC()) * fVV()) / getB();
  }

  /** {@inheritDoc} */
  @Override
  public double fBB() {
    return -(2.0 * fb() + (numberOfMolesInPhase * molarVolume + getC()) * fBV()) / getB();
  }

  /** {@inheritDoc} */
  @Override
  public double gBV() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return 1.0 / (val * val);
  }

  /** {@inheritDoc} */
  @Override
  public double gBB() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  /**
   * <p>
   * gBC - second cross derivative of small g with respect to b and c.
   * </p>
   *
   * @return a double
   */
  public double gBC() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return 1.0 / (val * val);
  }

  /**
   * <p>
   * gCV - second cross derivative of small g with respect to c and V.
   * </p>
   *
   * @return a double
   */
  public double gCV() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  /**
   * <p>
   * gCC - second derivative of small g with respect to c.
   * </p>
   *
   * @return a double
   */
  public double gCC() {
    double val = numberOfMolesInPhase * getMolarVolume() - getB() + getC();
    return -1.0 / (val * val);
  }

  /**
   * <p>
   * FC - derivative of the reduced residual Helmholtz energy F with respect to C.
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
  public double dFdV() {
    return (-numberOfMolesInPhase * gV() - getA() / temperature * fv()) + cpaon * dFCPAdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return (-numberOfMolesInPhase * gVV() - getA() * fVV() / temperature) + cpaon * dFCPAdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return (-numberOfMolesInPhase * gVVV() - getA() * fVVV() / temperature) + cpaon * dFCPAdVdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return (FT() + FD() * getAT() + FC() * getCT()) + cpaon * dFCPAdT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return (FTV() + FDV() * getAT() + FCV() * getCT()) + cpaon * dFCPAdTdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return (FTT() + 2.0 * FDT() * getAT() + FD() * getATT() + 2.0 * FTC() * getCT()
        + FCC() * getCT() * getCT() + FC() * getCTT() + 2.0 * FCD() * getCT() * getAT())
        + cpaon * dFCPAdTdT();
  }
}
