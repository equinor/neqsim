package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * SoreideWhitsonMixingRule implements the Søreide-Whitson mixing rule for PR EoS.
 */
public class SoreideWhitsonMixingRule implements EosMixingRulesInterface {
  private static final long serialVersionUID = 1L;

  // Storage for T1 binary interaction parameters
  private double[][] binaryInteractionParameterT1 = new double[20][20]; // adjust size as needed

  private int bmixType = 0;

  @Override
  public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    double A = 0.0;
    for (int i = 0; i < numbcomp; i++) {
      for (int j = 0; j < numbcomp; j++) {
        double xi = phase.getComponent(i).getx();
        double xj = phase.getComponent(j).getx();
        double aij =
            Math.sqrt(((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(i)).geta()
                * ((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(j)).geta());
        A += xi * xj * aij;
      }
    }
    return A;
  }

  @Override
  public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    double B = 0.0;
    for (int i = 0; i < numbcomp; i++) {
      B += phase.getComponent(i).getx()
          * ((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(i)).getb();
    }
    return B;
  }

  @Override
  public double calcAi(int compnumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    return ((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(compnumb)).geta();
  }

  @Override
  public double calcBi(int compnumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    return ((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(compnumb)).getb();
  }

  @Override
  public double calcBij(int compnumb, int j, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    return Math
        .sqrt(((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(compnumb)).geta()
            * ((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(j)).geta());
  }

  @Override
  public double calcAij(int compnumb, int j, PhaseInterface phase, double temperature,
      double pressure, int numbcomp) {
    return Math
        .sqrt(((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(compnumb)).geta()
            * ((neqsim.thermo.component.ComponentEosInterface) phase.getComponent(j)).geta());
  }

  @Override
  public void setBinaryInteractionParameterT1(int i, int j, double value) {
    if (i >= binaryInteractionParameterT1.length || j >= binaryInteractionParameterT1[i].length) {
      // Optionally resize or throw exception
      return;
    }
    binaryInteractionParameterT1[i][j] = value;
  }

  @Override
  public double getBinaryInteractionParameterT1(int i, int j) {
    if (i >= binaryInteractionParameterT1.length || j >= binaryInteractionParameterT1[i].length) {
      return 0.0;
    }
    return binaryInteractionParameterT1[i][j];
  }

  @Override
  public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    // Placeholder: return 0 or implement as needed
    return 0.0;
  }

  @Override
  public double[][] getBinaryInteractionParameters() {
    // Return the T1 parameter array or a new array as needed
    return binaryInteractionParameterT1;
  }

  @Override
  public void setCalcEOSInteractionParameters(boolean CalcEOSInteractionParameters) {
    // No-op for now
  }

  @Override
  public PhaseInterface getGEPhase() {
    // No GE phase in this mixing rule
    return null;
  }

  @Override
  public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp) {
    // Placeholder: return 0 or implement as needed
    return 0.0;
  }

  @Override
  public void setBinaryInteractionParameterij(int i, int j, double value) {
    // No-op for now
  }

  @Override
  public void setMixingRuleGEModel(String GEmodel) {
    // No-op for now
  }

  @Override
  public void setnEOSkij(double n) {
    // No-op for now
  }

  @Override
  public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    // Placeholder: return 0 or implement as needed
    return 0.0;
  }

  @Override
  public void setBinaryInteractionParameter(int i, int j, double value) {
    // No-op for now
  }

  @Override
  public void setBinaryInteractionParameterji(int i, int j, double value) {
    // No-op for now
  }

  @Override
  public String getName() {
    return "SoreideWhitsonMixingRule";
  }

  @Override
  public double getBinaryInteractionParameter(int i, int j) {
    // Return 0 or implement as needed
    return 0.0;
  }

  @Override
  public void setBmixType(int bmixType2) {
    this.bmixType = bmixType2;
  }

  @Override
  public int getBmixType() {
    return bmixType;
  }

  // ...existing code for interface methods (can be left unimplemented if not
  // used)...
}
