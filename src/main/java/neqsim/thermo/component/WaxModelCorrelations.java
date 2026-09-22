package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/** Shared correlations and domain checks for solid-solution wax models. */
final class WaxModelCorrelations {
  private WaxModelCorrelations() {
  }

  /**
   * Morgan-Kobayashi (1994) PERT2 vaporization enthalpy, in J/mol.
   *
   * <p>
   * Reference: Fluid Phase Equilibria 94, 51-87, DOI 10.1016/0378-3812(94)87051-9. Coefficients are cross-checked
   * against the independent Chemicals MK implementation. This correlation requires a positive subcritical temperature;
   * no critical property is fabricated.
   * </p>
   *
   * @param temperature temperature in K
   * @param criticalTemperature critical temperature in K
   * @param omega acentric factor
   * @return vaporization enthalpy in J/mol
   */
  static double vaporizationEnthalpy(double temperature, double criticalTemperature, double omega) {
    if (!Double.isFinite(temperature) || temperature <= 0.0 || !Double.isFinite(criticalTemperature)
        || temperature >= criticalTemperature || !Double.isFinite(omega)) {
      throw new IllegalArgumentException("Wax vaporization correlation requires 0 < T < Tc and finite omega: T="
          + temperature + ", Tc=" + criticalTemperature + ", omega=" + omega);
    }
    double tau = 1.0 - temperature / criticalTemperature;
    double tau13 = Math.pow(tau, 0.3333);
    double tau56 = Math.pow(tau, 0.8333);
    double tau29 = Math.pow(tau, 1.2083);
    double h0 = 5.2804 * tau13 + 12.865 * tau56 + 1.171 * tau29 + tau * (-13.116 + tau * (0.4858 - 1.088 * tau));
    double h1 = 0.080022 * tau13 + 273.23 * tau56 + 465.08 * tau29 + tau * (-638.51 + tau * (-145.12 + 74.049 * tau));
    double h2 = 7.2543 * tau13 - 346.45 * tau56 - 610.48 * tau29 + tau * (839.89 + tau * (160.05 - 50.711 * tau));
    double enthalpy = ThermodynamicConstantsInterface.R * criticalTemperature * (h0 + omega * (h1 + omega * h2));
    if (!Double.isFinite(enthalpy) || enthalpy <= 0.0) {
      throw new IllegalStateException("Invalid wax vaporization enthalpy at T=" + temperature + ", Tc="
          + criticalTemperature + ", omega=" + omega + ": " + enthalpy);
    }
    return enthalpy;
  }

  /**
   * Normalize the solid-solution composition over wax formers only.
   *
   * @param phase wax phase, including any excluded fluid components
   * @return normalized wax-former mole fractions (zero for other components)
   */
  static double[] composition(PhaseInterface phase) {
    double[] fractions = new double[phase.getNumberOfComponents()];
    double sum = 0.0;
    for (int i = 0; i < fractions.length; i++) {
      if (phase.getComponent(i).isWaxFormer()) {
        double value = phase.getComponent(i).getx();
        if (!Double.isFinite(value) || value < 0.0) {
          throw new IllegalStateException("Invalid wax composition for " + phase.getComponent(i).getComponentName());
        }
        fractions[i] = value;
        sum += value;
      }
    }
    if (!Double.isFinite(sum) || sum <= 0.0) {
      throw new IllegalStateException("Wax activity calculation requires a positive wax-former composition");
    }
    for (int i = 0; i < fractions.length; i++) {
      fractions[i] /= sum;
    }
    return fractions;
  }

  /**
   * Evaluate an exponential without silently passing invalid coefficients to a flash.
   *
   * @param logarithm logarithm of coefficient
   * @param component component being evaluated
   * @param phase current phase
   * @return finite positive coefficient
   */
  static double coefficient(double logarithm, ComponentInterface component, PhaseInterface phase) {
    double value = Math.exp(logarithm);
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalStateException("Invalid wax coefficient for " + component.getComponentName() + " ("
          + component.getClass().getSimpleName() + ") at T=" + phase.getTemperature() + " K, P=" + phase.getPressure()
          + " bara: ln(coefficient)=" + logarithm);
    }
    return value;
  }

  /**
   * Common solid reference with a model-specific logarithmic activity coefficient.
   *
   * @param component wax component
   * @param phase1 current wax phase
   * @param lnActivity logarithm of the solid activity coefficient
   * @return finite positive fugacity coefficient
   */
  static double solidFugacityCoefficient(ComponentSolid component, PhaseInterface phase1, double lnActivity) {
    if (!component.isWaxFormer()) {
      component.fugacityCoefficient = 1.0e50;
      return component.fugacityCoefficient;
    }
    double temperature = phase1.getTemperature();
    double pressure = phase1.getPressure();
    double meltingTemperature = component.getTriplePointTemperature();
    if (!Double.isFinite(temperature) || temperature <= 0.0 || !Double.isFinite(pressure) || pressure <= 0.0
        || !Double.isFinite(meltingTemperature) || meltingTemperature <= 0.0
        || !Double.isFinite(component.getHeatOfFusion()) || component.getHeatOfFusion() <= 0.0
        || component.refPhase == null) {
      throw new IllegalArgumentException("Invalid wax reference properties for " + component.getComponentName());
    }
    component.refPhase.setTemperature(temperature);
    component.refPhase.setPressure(pressure);
    component.refPhase.init(component.refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.LIQUID, 1.0);
    double liquidCoefficient = component.refPhase.getComponent(0).fugcoef(component.refPhase);
    if (!Double.isFinite(liquidCoefficient) || liquidCoefficient <= 0.0) {
      throw new IllegalStateException(
          "Invalid reference-liquid fugacity coefficient for " + component.getComponentName());
    }

    // NeqSim native EOS molar volume is m3*1e5/mol; convert once to SI.
    double liquidVolume = component.refPhase.getMolarVolume() * 1e-5;
    if (!Double.isFinite(liquidVolume) || liquidVolume <= 0.0) {
      throw new IllegalStateException("Invalid reference-liquid molar volume for " + component.getComponentName());
    }
    double solidVolume = 0.9 * liquidVolume;
    double pressureTerm = (solidVolume - liquidVolume) * (pressure - 1.0) * 1e5
        / (ThermodynamicConstantsInterface.R * temperature);
    double mw = component.getMolarMass() * 1000.0;
    double deltaCp = (0.3033 * mw - 4.635e-4 * mw * temperature) * 4.184;
    double ratio = meltingTemperature / temperature;
    double cpTerm = deltaCp / ThermodynamicConstantsInterface.R * (ratio - 1.0 - Math.log(ratio));
    double thermalTerm = -component.getHeatOfFusion() / (ThermodynamicConstantsInterface.R * temperature)
        * (1.0 - temperature / meltingTemperature);
    double lnCoefficient = Math.log(liquidCoefficient) + thermalTerm + cpTerm + pressureTerm + lnActivity;
    component.fugacityCoefficient = coefficient(lnCoefficient, component, phase1);
    // Evaluate the coefficient directly; x*phi/x is undefined at infinite dilution.
    component.SolidFug = component.getx() * pressure * component.fugacityCoefficient;
    return component.fugacityCoefficient;
  }
}
