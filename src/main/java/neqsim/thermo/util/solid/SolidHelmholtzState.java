package neqsim.thermo.util.solid;

import java.io.Serializable;

/**
 * Immutable molar property state returned by a solid Helmholtz equation of state.
 *
 * @author esol
 * @version 1.0
 */
public final class SolidHelmholtzState implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double molarVolume;
  private final double helmholtzEnergy;
  private final double internalEnergy;
  private final double entropy;
  private final double enthalpy;
  private final double gibbsEnergy;
  private final double heatCapacityCp;
  private final double heatCapacityCv;
  private final double logFugacityCoefficient;

  /**
   * Construct a solid thermodynamic state.
   *
   * @param molarVolume molar volume in m3/mol; must be positive
   * @param helmholtzEnergy molar Helmholtz energy in J/mol
   * @param internalEnergy molar internal energy in J/mol
   * @param entropy molar entropy in J/(mol K)
   * @param enthalpy molar enthalpy in J/mol
   * @param gibbsEnergy molar Gibbs energy or chemical potential in J/mol
   * @param heatCapacityCp molar constant-pressure heat capacity in J/(mol K)
   * @param heatCapacityCv molar constant-volume heat capacity in J/(mol K)
   * @param logFugacityCoefficient natural logarithm of the fugacity coefficient
   */
  public SolidHelmholtzState(double molarVolume, double helmholtzEnergy, double internalEnergy, double entropy,
      double enthalpy, double gibbsEnergy, double heatCapacityCp, double heatCapacityCv,
      double logFugacityCoefficient) {
    if (!(molarVolume > 0.0) || !Double.isFinite(molarVolume)) {
      throw new IllegalArgumentException("Molar volume must be finite and positive.");
    }
    this.molarVolume = molarVolume;
    this.helmholtzEnergy = helmholtzEnergy;
    this.internalEnergy = internalEnergy;
    this.entropy = entropy;
    this.enthalpy = enthalpy;
    this.gibbsEnergy = gibbsEnergy;
    this.heatCapacityCp = heatCapacityCp;
    this.heatCapacityCv = heatCapacityCv;
    this.logFugacityCoefficient = logFugacityCoefficient;
  }

  /** @return molar volume in m3/mol */
  public double getMolarVolume() {
    return molarVolume;
  }

  /** @return molar Helmholtz energy in J/mol */
  public double getHelmholtzEnergy() {
    return helmholtzEnergy;
  }

  /** @return molar internal energy in J/mol */
  public double getInternalEnergy() {
    return internalEnergy;
  }

  /** @return molar entropy in J/(mol K) */
  public double getEntropy() {
    return entropy;
  }

  /** @return molar enthalpy in J/mol */
  public double getEnthalpy() {
    return enthalpy;
  }

  /** @return molar Gibbs energy in J/mol */
  public double getGibbsEnergy() {
    return gibbsEnergy;
  }

  /** @return molar constant-pressure heat capacity in J/(mol K) */
  public double getHeatCapacityCp() {
    return heatCapacityCp;
  }

  /** @return molar constant-volume heat capacity in J/(mol K) */
  public double getHeatCapacityCv() {
    return heatCapacityCv;
  }

  /** @return natural logarithm of the fugacity coefficient */
  public double getLogFugacityCoefficient() {
    return logFugacityCoefficient;
  }
}