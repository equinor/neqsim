package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermPr;

/**
 * Component class for the Burgoyne–Nielsen–Stanko PR correlation.
 *
 * @author esol
 */
public class ComponentBNS extends ComponentPR {
  private static final long serialVersionUID = 1L;
  double omegaA = 0.45724;
  double omegaB = 0.07780;

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
  public ComponentBNS(String name, double moles, double molesInPhase, int compIndex, double tc,
      double pc, double mw, double acf, double omegaA, double omegaB, double vShift) {
    super(compIndex, tc, pc, mw, acf, moles);
    this.componentName = name;
    this.index = compIndex;
    this.numberOfMolesInPhase = molesInPhase;
    this.omegaA = omegaA;
    this.omegaB = omegaB;
    a = omegaA * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    b = omegaB * R * criticalTemperature / criticalPressure;
    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
    setVolumeCorrectionConst(vShift);
    setAttractiveParameter(new AttractiveTermPr(this));
  }

  /** {@inheritDoc} */
  @Override
  public ComponentBNS clone() {
    return (ComponentBNS) super.clone();
  }

  @Override
  public double calca() {
    a = omegaA * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    return a;
  }

  @Override
  public double calcb() {
    b = omegaB * R * criticalTemperature / criticalPressure;
    return b;

  }
}
