package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermPr;

/**
 * Component class for the Burgoyne–Nielsen–Stanko PR correlation.
 */
public class ComponentBNS extends ComponentPR {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a BNS component with explicit pure component parameters.
   *
   * @param name component name
   * @param moles total moles of the component
   * @param molesInPhase moles of component in phase
   * @param compIndex index of component in phase
   * @param tc critical temperature [K]
   * @param pc critical pressure [bar]
   * @param mw molar mass [kg/mol]
   * @param acf acentric factor
   * @param omegaA PR constant OmegaA
   * @param omegaB PR constant OmegaB
   * @param vShift volume shift constant
   */
  public ComponentBNS(String name, double moles, double molesInPhase, int compIndex,
      double tc, double pc, double mw, double acf, double omegaA, double omegaB,
      double vShift) {
    super(compIndex, tc, pc, mw, acf, moles);
    this.componentName = name;
    this.index = compIndex;
    this.numberOfMolesInPhase = molesInPhase;
    a = omegaA * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    b = omegaB * R * criticalTemperature / criticalPressure;
    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
    setVolumeCorrectionConst(vShift);
    setAttractiveParameter(new AttractiveTermPr(this));
  }

  @Override
  public ComponentBNS clone() {
    return (ComponentBNS) super.clone();
  }
}
