package neqsim.thermo.mixingrule;

import neqsim.thermo.ThermodynamicConstantsInterface;


public abstract class MixingRuleHandler implements ThermodynamicConstantsInterface {

  protected String mixingRuleName;

  public String getName() {
    return mixingRuleName;
  }
}
