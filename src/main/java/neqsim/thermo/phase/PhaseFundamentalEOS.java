package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentFundamentalEOSInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.mixingrule.MixingRulesInterface;

/**
 * Abstract base class for Helmholtz-energy-based equations of state.
 * Assumes subclasses use reduced Helmholtz energy (a/RT) and compute properties from it.
 *
 * @author victorigi
 * @since 27.03.2025
 */
public abstract class PhaseFundamentalEOS extends Phase implements PhaseFundamentalEOSInterface {
  private static final long serialVersionUID = 1L;

  doubleW[][] ar;
  doubleW[] a0;
  double density;
  double T;
  double RT;
  double dPdD;
  double dPdT;
  double d2PdTD;
  double A;
  double G;
  double U;
  double H;
  double S;
  double Cp;
  double Cv;
  double JT;
  double W;
  double Kappa;
  double d2PdD2;


  public PhaseFundamentalEOS() {
    super();
    setType(PhaseType.GAS); // Default; subclasses can override after density calc
  }

  /**
   * Compute molar density [mol/m3] at specified T and P.
   */
  public abstract double solveDensity(double temperature, double pressure);

  /**
   * Compute reduced residual Helmholtz energy and its derivatives.
   *   /**
   * Get reduced residual helmholtz free energy and its derivatives.
   * The returned array has the following structure:
   * <ul>
   * <li>ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)</li>
   * <li>ar(0,1) - delta*partial (ar)/partial(delta)</li>
   * <li>ar(0,2) - delta^2*partial^2(ar)/partial(delta)^2</li>
   * <li>ar(0,3) - delta^3*partial^3(ar)/partial(delta)^3</li>
   * <li>ar(1,0) - tau*partial (ar)/partial(tau)</li>
   * <li>ar(1,1) - tau*delta*partial^2(ar)/partial(tau)/partial(delta)</li>
   * <li>ar(2,0) - tau^2*partial^2(ar)/partial(tau)^2</li>
   * </ul>
   */
  public abstract doubleW[][] getAlpharesMatrix();


  public abstract doubleW[] getAlpha0Matrix();

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int nComponents, int initType, PhaseType pt, double beta) {
    super.init(totalNumberOfMoles, nComponents, initType, pt, beta);
    T = getTemperature();
    this.pressure = getPressure();




    if (initType >= 1) {
      density = solveDensity(temperature, pressure); //mol/m3
      ar = getAlpharesMatrix();
      a0 = getAlpha0Matrix();
      RT = R * temperature;
      Z = 1 + ar[0][1].val;
      dPdD = RT * (1 + 2 * ar[0][1].val + ar[0][2].val);
      dPdT = density * R * (1 + ar[0][1].val - ar[1][1].val);
  
      d2PdTD = R * (1 + 2 * ar[0][1].val + ar[0][2].val - 2 * ar[1][1].val - ar[1][2].val);
      A = RT * (a0[0].val + ar[0][0].val); // Helmholtz energy
      G = RT * (1 + ar[0][1].val + a0[0].val + ar[0][0].val);
      U = RT * (a0[1].val + ar[1][0].val);
      H = RT * (1 + ar[0][1].val + a0[1].val + ar[1][0].val);
      S = R * (a0[1].val + ar[1][0].val - a0[0].val - ar[0][0].val);
      Cv = -R * (a0[2].val + ar[2][0].val);
      if (density > 1e-15) {
        Cp = Cv + T * (dPdT / density) * (dPdT / density) / dPdD;
      d2PdD2 = RT * (2 * ar[0][1].val + 4 * ar[0][2].val + ar[0][3].val) / density;
      JT = (T / density * dPdT / dPdD - 1) / Cp / density; 
      }  else {
        Cp = Cv + R;
        d2PdD2 = 0;
        JT = 1E+20;
      }
      W = 1000 * Cp / Cv * dPdD / getMolarMass();
      if (W < 0) {
        W = 0;
      }
      W = Math.sqrt(W);
      Kappa = Math.pow(W, 2) * getMolarMass() / (RT * 1000 * Z);
    }
  }



  // --- Thermodynamic framework methods ---


  /** {@inheritDoc} */
  @Override
  public double getZ() {
    return Z;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return density;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return Z * R * temperature * density;
  }

  /**
  * <p>
  * Get the derivative of pressure with respect to density.
  * </p>
  *
  * @return double
  */
  public double getDPdD() {
    return dPdD;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return G * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return JT * 1e3; // [K/bar]
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return H * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    return S * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    return U * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    return Cp * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return Cv * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
    public double getSoundSpeed() {
    return W;
  }

  /** {@inheritDoc} */
  @Override
  public double getKappa() {
    return Kappa;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    return 1 / density;
  }



  
  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    return density * R * (1 + ar[0][1].val - ar[1][1].val);
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdVTn() {
    return -density * density * R * T * (1 + 2 * ar[0][1].val + ar[0][2].val) / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdrho() {
    return R * T * (1 + 2 * ar[0][1].val + ar[0][2].val); 
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodP() {
    return 1.0 / getdPdrho();
  }

  /** {@inheritDoc} */
  @Override
  public double getdrhodT() {
    return -getdPdTVn() / getdPdrho();
  }

  /**
   * <p>
   * getF.
   * </p>
   *
   * @return a double
   */
  public double getF() {
    return numberOfMolesInPhase * ar[0][0].val;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return numberOfMolesInPhase * ar[1][1].val / (temperature * getVolume()); 
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(int i) {
    return ((ComponentFundamentalEOSInterface) getComponent(i)).dFdN(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, int j) {
    return ((ComponentFundamentalEOSInterface) getComponent(i)).dFdNdN(j, this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(int i) {
    return ((ComponentFundamentalEOSInterface) getComponent(i)).dFdNdV(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(int i) {
    return ((ComponentFundamentalEOSInterface) getComponent(i)).dFdNdT(this, this.getNumberOfComponents(),
        temperature, pressure);
  }



  // --- Provide defaults to avoid forcing subclass overrides if not needed ---

  @Override
  public double getA() {
    throw new UnsupportedOperationException("getA() not supported for fundamental EOS");
  }

  @Override
  public double getB() {
    throw new UnsupportedOperationException("getB() not supported for fundamental EOS");
  }

  @Override
public MixingRulesInterface getMixingRule() {
    throw new UnsupportedOperationException("Mixing rule not implemented for PhaseFundamentalEos");
}

  @Override
  public MixingRuleTypeInterface getMixingRuleType() {
    throw new UnsupportedOperationException("Mixing rule not implemented for PhaseFundamentalEos");
  }

  @Override
  public void resetMixingRule(MixingRuleTypeInterface mixingRule) {
    throw new UnsupportedOperationException("Mixing rule not implemented for PhaseFundamentalEos");
  }

  @Override
  public void setMixingRule(MixingRuleTypeInterface mixingRule) {
    throw new UnsupportedOperationException("Mixing rule not implemented for PhaseFundamentalEos");
  }

  @Override
  public void setMixingRuleGEModel(String model) {
    throw new UnsupportedOperationException("Mixing rule GE Model not implemented for PhaseFundamentalEos");
  }


}
