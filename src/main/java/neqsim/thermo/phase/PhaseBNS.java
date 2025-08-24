package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentBNS;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * Phase implementation using the Burgoyne–Nielsen–Stanko PR correlation.
 */
public class PhaseBNS extends PhasePrEos {
  private static final long serialVersionUID = 1L;

  private final double[] tcs;
  private final double[] pcs;
  private final double[] mws;
  private final double[] acfs;
  private final double[] omegaA;
  private final double[] omegaB;
  private final double[] vshift;

  public PhaseBNS(double[] tcs, double[] pcs, double[] mws, double[] acfs, double[] omegaA,
      double[] omegaB, double[] vshift) {
    super();
    this.tcs = tcs;
    this.pcs = pcs;
    this.mws = mws;
    this.acfs = acfs;
    this.omegaA = omegaA;
    this.omegaB = omegaB;
    this.vshift = vshift;
  }

  private static double degRToK(double degR) {
    return degR * 5.0 / 9.0;
  }

  public void setBnsBips(double temperature) {
    double tpcHc = tcs[4];
    int[][] pairs =
        {{4, 0}, {4, 1}, {4, 2}, {4, 3}, {0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}};
    double[] consts = {-0.145561, 0.16852, -0.108, -0.0620119, 0.248638, -0.25, -0.247153,
        -0.204414, 0.0, -0.166253};
    double[] slopes = {0.276572, -0.122378, 0.0605506, 0.0427873, -0.138185, 0.11602, 0.16377,
        0.234417, 0.0, 0.0788129};
    double[] tcsPair = {tpcHc, tpcHc, tpcHc, tpcHc, degRToK(547.416), degRToK(547.416),
        degRToK(547.416), degRToK(672.12), degRToK(672.12), degRToK(227.16)};
    EosMixingRulesInterface mix = getMixingRule();
    for (int k = 0; k < pairs.length; k++) {
      int i = pairs[k][0];
      int j = pairs[k][1];
      double kij = consts[k] + slopes[k] * tcsPair[k] / temperature;
      double dkijdT = -slopes[k] * tcsPair[k] / (temperature * temperature);
      mix.setBinaryInteractionParameter(i, j, consts[k]);
      mix.setBinaryInteractionParameterT1(i, j, slopes[k] * tcsPair[k]);
    }
  }

  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentBNS(name, moles, molesInPhase, compNumber,
        tcs[compNumber], pcs[compNumber], mws[compNumber], acfs[compNumber], omegaA[compNumber],
        omegaB[compNumber], vshift[compNumber]);
  }

  @Override
  public PhaseBNS clone() {
    return (PhaseBNS) super.clone();
  }

  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
    setBnsBips(getTemperature());
  }
}
