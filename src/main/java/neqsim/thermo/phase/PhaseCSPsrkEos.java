package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCSPsrk;
import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * PhaseCSPsrkEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseCSPsrkEos extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double f_scale_mix = 0;
  double h_scale_mix = 0;
  PhaseSrkEos refBWRSPhase = null;
  double brefBWRSPhase = 0;
  double acrefBWRSPhase = 0;
  double mrefBWRSPhase = 0;

  /**
   * <p>
   * Constructor for PhaseCSPsrkEos.
   * </p>
   */
  public PhaseCSPsrkEos() {
    refBWRSPhase = new PhaseBWRSEos();
    // refBWRSPhase = new PhaseSrkEos();
    refBWRSPhase.addComponent("methane", 1.0, 1.0, 0);
    refBWRSPhase.calcMolarVolume(false);
    brefBWRSPhase = (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R
        * refBWRSPhase.getComponent(0).getTC() / refBWRSPhase.getComponent(0).getPC();
    mrefBWRSPhase = (0.48 + 1.574 * refBWRSPhase.getComponent(0).getAcentricFactor()
        - 0.175 * refBWRSPhase.getComponent(0).getAcentricFactor()
            * refBWRSPhase.getComponent(0).getAcentricFactor());
    acrefBWRSPhase = 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R
        * refBWRSPhase.getComponent(0).getTC() * refBWRSPhase.getComponent(0).getTC()
        / refBWRSPhase.getComponent(0).getPC();
  }

  /** {@inheritDoc} */
  @Override
  public PhaseCSPsrkEos clone() {
    PhaseCSPsrkEos clonedPhase = null;
    try {
      clonedPhase = (PhaseCSPsrkEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentCSPsrk(name, moles, molesInPhase, compNumber);
    ((ComponentCSPsrk) componentArray[compNumber]).setRefPhaseBWRS(this);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    double oldtemp = getTemperature();
    if (initType == 0) {
      refBWRSPhase.init(1.0, 1, 0, pt, 1.0);
    }
    refBWRSPhase.init(1.0, 1, 3, pt, 1.0);

    do {
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
      oldtemp = refBWRSPhase.getTemperature();
      h_scale_mix = getNumberOfMolesInPhase() * getb() / brefBWRSPhase;
      double term1 = getA() / ((ComponentEosInterface) refBWRSPhase.getComponent(0)).getaT();
      f_scale_mix = term1 / h_scale_mix;
      refBWRSPhase.setTemperature(temperature * numberOfMolesInPhase / f_scale_mix);
      refBWRSPhase.setMolarVolume(getTotalVolume() / h_scale_mix);
      // refBWRSPhase.setPressure(refBWRSPhase.calcPressure());
      refBWRSPhase.setPressure(pressure * h_scale_mix / f_scale_mix);
      refBWRSPhase.init(1.0, 1, initType, pt, 1.0);
    } while (Math.abs((oldtemp - refBWRSPhase.getTemperature()) / oldtemp) > 1e-8);
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    return f_scale_mix * refBWRSPhase.getF() / refBWRSPhase.getNumberOfMolesInPhase()
        * refBWRSPhase.getTemperature() / getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    return (f_scale_mix * refBWRSPhase.dFdV() / refBWRSPhase.getNumberOfMolesInPhase()
        / h_scale_mix) * refBWRSPhase.getTemperature() / getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return (f_scale_mix * refBWRSPhase.dFdVdV() / refBWRSPhase.getNumberOfMolesInPhase()
        / h_scale_mix / h_scale_mix) * refBWRSPhase.getTemperature() / getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return (f_scale_mix * refBWRSPhase.dFdVdVdV() / refBWRSPhase.getNumberOfMolesInPhase()
        / h_scale_mix / h_scale_mix / h_scale_mix) * refBWRSPhase.getTemperature()
        / getTemperature();
  }

  /**
   * Getter for property f_scale_mix.
   *
   * @return Value of property f_scale_mix.
   */
  public double getF_scale_mix() {
    return f_scale_mix;
  }

  // 6-8 feb.

  /**
   * Setter for property f_scale_mix.
   *
   * @param f_scale_mix New value of property f_scale_mix.
   */
  public void setF_scale_mix(double f_scale_mix) {
    this.f_scale_mix = f_scale_mix;
  }

  /**
   * Getter for property h_scale_mix.
   *
   * @return Value of property h_scale_mix.
   */
  public double getH_scale_mix() {
    return h_scale_mix;
  }

  /**
   * Setter for property h_scale_mix.
   *
   * @param h_scale_mix New value of property h_scale_mix.
   */
  public void setH_scale_mix(double h_scale_mix) {
    this.h_scale_mix = h_scale_mix;
  }

  /**
   * Getter for property brefBWRSPhase.
   *
   * @return Value of property brefBWRSPhase.
   */
  public double getBrefBWRSPhase() {
    return brefBWRSPhase;
  }

  /**
   * Setter for property brefBWRSPhase.
   *
   * @param brefBWRSPhase New value of property brefBWRSPhase.
   */
  public void setBrefBWRSPhase(double brefBWRSPhase) {
    this.brefBWRSPhase = brefBWRSPhase;
  }

  /**
   * Getter for property acrefBWRSPhase.
   *
   * @return Value of property acrefBWRSPhase.
   */
  public double getAcrefBWRSPhase() {
    return acrefBWRSPhase;
  }

  /**
   * Setter for property acrefBWRSPhase.
   *
   * @param acrefBWRSPhase New value of property acrefBWRSPhase.
   */
  public void setAcrefBWRSPhase(double acrefBWRSPhase) {
    this.acrefBWRSPhase = acrefBWRSPhase;
  }

  /**
   * Getter for property refBWRSPhase.
   *
   * @return Value of property refBWRSPhase.
   */
  public neqsim.thermo.phase.PhaseSrkEos getRefBWRSPhase() {
    return refBWRSPhase;
  }

  /**
   * Setter for property refBWRSPhase.
   *
   * @param refBWRSPhase New value of property refBWRSPhase.
   */
  public void setRefBWRSPhase(neqsim.thermo.phase.PhaseBWRSEos refBWRSPhase) {
    this.refBWRSPhase = refBWRSPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV = pt == PhaseType.GAS ? pressure * getB() / (numberOfMolesInPhase * temperature * R)
        : 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    BonV = Math.max(1.0e-4, Math.min(1.0 - 1.0e-4, BonV));

    double Btemp = getB();
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);

    double Dtemp = getA();
    double BonVold = 0;
    int iterations = 0;
    int maxIterations = 1000;
    do {
      iterations++;
      BonVold = BonV;
      double h = BonV + Btemp * gV() + Btemp * Dtemp / (numberOfMolesInPhase * temperature) * fv()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      double dh = 1.0 - Btemp / (BonV * BonV)
          * (Btemp * gVV() + Btemp * Dtemp * fVV() / (numberOfMolesInPhase * temperature));
      double fvvv = 1.0 / (R * Btemp * (delta1 - delta2))
          * (2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume() + Btemp * delta1, 3.0)
              - 2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume() + Btemp * delta2, 3.0));
      double gvvv = 2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume() - Btemp, 3.0)
          - 2.0 / Math.pow(numberOfMolesInPhase * getMolarVolume(), 3.0);
      double dhh = 2.0 * Btemp / Math.pow(BonV, 3.0)
          * (Btemp * gVV() + Btemp * Dtemp / (numberOfMolesInPhase * temperature) * fVV())
          + Btemp * Btemp / Math.pow(BonV, 4.0)
              * (Btemp * gvvv + Btemp * Dtemp / (numberOfMolesInPhase * temperature) * fvvv);

      double d1 = -h / dh;
      double d2 = -dh / dhh;

      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += d1 * (1.0 + 0.5 * -1.0);
      } else if (d1 / d2 > 1) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV += 0;
        }
      }

      if (BonV > 1) {
        BonV = 1.0 - 1.0e-16;
        BonVold = 10;
      }
      if (BonV < 0) {
        BonV = 1.0e-16;
        BonVold = 10;
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while (Math.abs(BonV - BonVold) > 1.0e-10 && iterations < maxIterations);
    // molarVolume = 1.0/BonV*Btemp/numberOfMolesInPhase;
    // Z = pressure*molarVolume/(R*temperature);
    // System.out.println("BonV: " + BonV + " " + h + " " +dh + " B " + Btemp + " D
    // " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
    // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
    // + fVV());
    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume",
          maxIterations);
    }
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
    }
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
    // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
    // + fVV());

    return getMolarVolume();
  }
}
