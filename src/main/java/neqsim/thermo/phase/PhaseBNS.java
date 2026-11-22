package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentBNS;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * Phase implementation using the Burgoyne–Nielsen–Stanko PR correlation.
 *
 * @author esol
 * @version 1.0
 */
public class PhaseBNS extends PhasePrEos {
  private static final long serialVersionUID = 1L;
  private static final int HYDROCARBON_INDEX = 4;
  private static final int HELIUM_INDEX = 5;

  private final double[] tcs;
  private final double[] pcs;
  private final double[] mws;
  private final double[] acfs;
  private final double[] omegaA;
  private final double[] omegaB;
  private final double[] vshift;

  /**
   * Constructor for PhaseBNS.
   *
   * @param tcs an array of double objects
   * @param pcs an array of double objects
   * @param mws an array of double objects
   * @param acfs an array of double objects
   * @param omegaA an array of double objects
   * @param omegaB an array of double objects
   * @param vshift an array of double objects
   */
  public PhaseBNS(double[] tcs, double[] pcs, double[] mws, double[] acfs, double[] omegaA, double[] omegaB,
      double[] vshift) {
    this.tcs = tcs;
    this.pcs = pcs;
    this.mws = mws;
    this.acfs = acfs;
    this.omegaA = omegaA;
    this.omegaB = omegaB;
    this.vshift = vshift;
  }

  /**
   * Converts a temperature from degrees Rankine to Kelvin.
   *
   * @param degR temperature in degrees Rankine
   * @return temperature in Kelvin
   */
  private static double degRToK(double degR) {
    return degR * 5.0 / 9.0;
  }

  /**
   * setBnsBips.
   *
   * @param temperature a double
   */
  public void setBnsBips(double temperature) {
    if (getNumberOfComponents() <= HYDROCARBON_INDEX || getMixingRule() == null
        || getMixingRuleType() == EosMixingRuleType.NO) {
      return;
    }
    double tpcHc = getComponent(HYDROCARBON_INDEX).getTC();
    int[][] pairs = {{4, 0}, {4, 1}, {4, 2}, {4, 3}, {0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}};
    double[] consts = {-0.145561, 0.16852, -0.108, -0.0620119, 0.248638, -0.25, -0.247153, -0.204414, 0.0, -0.166253};
    double[] slopes = {0.276572, -0.122378, 0.0605506, 0.0427873, -0.138185, 0.11602, 0.16377, 0.234417, 0.0,
        0.0788129};
    double[] tcsPair = {tpcHc, tpcHc, tpcHc, tpcHc, degRToK(547.416), degRToK(547.416), degRToK(547.416),
        degRToK(672.12), degRToK(672.12), degRToK(227.16)};
    EosMixingRulesInterface mix = getMixingRule();
    for (int k = 0; k < pairs.length; k++) {
      int i = pairs[k][0];
      int j = pairs[k][1];
      mix.setBinaryInteractionParameter(i, j, consts[k]);
      mix.setBinaryInteractionParameterT1(i, j, slopes[k] * tcsPair[k]);
    }
    if (getNumberOfComponents() > HELIUM_INDEX) {
      for (int i = 0; i < HELIUM_INDEX; i++) {
        mix.setBinaryInteractionParameter(i, HELIUM_INDEX, 0.0);
        mix.setBinaryInteractionParameterT1(i, HELIUM_INDEX, 0.0);
      }
    }
  }

  /**
   * Returns the constant BNS molar volume translation subtracted by the standard density models.
   *
   * @return mixture volume translation in NeqSim internal volume units (1e-5 m3/mol)
   */
  public double getMolarVolumeTranslation() {
    double translation = 0.0;
    if (useVolumeCorrection()) {
      for (int i = 0; i < getNumberOfComponents(); i++) {
        translation += getComponent(i).getx() * getComponent(i).getVolumeCorrection();
      }
    }
    return translation;
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    return super.getHresTP() - getPressure() * getNumberOfMolesInPhase() * getMolarVolumeTranslation();
  }

  /** {@inheritDoc} */
  @Override
  public double getGresTP() {
    return super.getGresTP() - getPressure() * getNumberOfMolesInPhase() * getMolarVolumeTranslation();
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    return getEnthalpy() - getPressure() * getNumberOfMolesInPhase() * (getMolarVolume() - getMolarVolumeTranslation());
  }

  /** {@inheritDoc} */
  @Override
  public double getHresdP() {
    return super.getHresdP() - getNumberOfMolesInPhase() * getMolarVolumeTranslation();
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return -getHresdP() / getCp();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    if (compNumber == HELIUM_INDEX) {
      // The fitted helium critical temperature (6.35 degR) and the other coefficients are a set.
      componentArray[compNumber] = new ComponentBNS(name, moles, molesInPhase, compNumber, degRToK(6.35),
          32.9236 * 0.06894757293168, 0.004003, -0.17984, 0.457236, 0.0777961, -0.078082);
    } else {
      componentArray[compNumber] = new ComponentBNS(name, moles, molesInPhase, compNumber, tcs[compNumber],
          pcs[compNumber], mws[compNumber], acfs[compNumber], omegaA[compNumber], omegaB[compNumber],
          vshift[compNumber]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public PhaseBNS clone() {
    PhaseBNS copy = (PhaseBNS) super.clone();
    if (copy.mixSelect == mixSelect && getMixingRuleType() != null) {
      // A composition edit can change fitted interactions or grow the matrix with helium.
      copy.mixSelect = mixSelect.clone();
      copy.mixRule = copy.mixSelect.resetMixingRule(getMixingRuleType().getValue(), copy);
    }
    return copy;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
    setBnsBips(getTemperature());
  }

  /** {@inheritDoc} */
  @Override
  public void resetMixingRule(MixingRuleTypeInterface mr) {
    super.resetMixingRule(mr);
    setBnsBips(getTemperature());
  }
}
