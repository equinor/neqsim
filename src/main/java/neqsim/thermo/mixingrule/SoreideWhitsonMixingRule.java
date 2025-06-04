package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * SoreideWhitsonMixingRule implements the Søreide-Whitson mixing rule for PR
 * EoS.
 */
public class SoreideWhitsonMixingRule implements EosMixingRulesInterface {
  private static final long serialVersionUID = 1L;

  @Override
  public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    // TODO: Implement Søreide-Whitson mixing rule for A (composition, T, salinity
    // dependent)
    // Placeholder: classic quadratic mixing rule
    double A = 0.0;
    for (int i = 0; i < numbcomp; i++) {
      for (int j = 0; j < numbcomp; j++) {
        double xi = phase.getComponent(i).getx();
        double xj = phase.getComponent(j).getx();
        double aij = Math.sqrt(phase.getComponent(i).geta() * phase.getComponent(j).geta());
        // TODO: Add Søreide-Whitson specific terms (salinity, T, etc.)
        A += xi * xj * aij;
      }
    }
    return A;
  }

  @Override
  public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    double B = 0.0;
    for (int i = 0; i < numbcomp; i++) {
      B += phase.getComponent(i).getx() * phase.getComponent(i).getb();
    }
    return B;
  }

  @Override
  public double calcAi(int compnumb, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    return phase.getComponent(compnumb).geta();
  }

  @Override
  public double calcBi(int compnumb, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    return phase.getComponent(compnumb).getb();
  }

  @Override
  public double calcBij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    return Math.sqrt(phase.getComponent(compnumb).geta() * phase.getComponent(j).geta());
  }

  @Override
  public double calcAij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
    return Math.sqrt(phase.getComponent(compnumb).geta() * phase.getComponent(j).geta());
  }

  // ...existing code for interface methods (can be left unimplemented if not
  // used)...
}
