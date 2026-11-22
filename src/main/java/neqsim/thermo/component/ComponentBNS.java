package neqsim.thermo.component;

import java.io.IOException;
import java.io.ObjectInputStream;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermPr;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Component class for the Burgoyne–Nielsen–Stanko PR correlation.
 *
 * @author esol
 * @version 1.0
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
  public ComponentBNS(String name, double moles, double molesInPhase, int compIndex, double tc, double pc, double mw,
      double acf, double omegaA, double omegaB, double vShift) {
    super(compIndex, tc, pc, mw, acf, moles);
    this.componentName = name;
    this.index = compIndex;
    this.componentNumber = compIndex;
    setBnsComponentType();
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

  /**
   * Restores the mixing-rule index when reading a BNS component saved before it was initialized correctly.
   *
   * @param input serialized component data
   * @throws IOException if the component cannot be read
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    componentNumber = index;
    setBnsComponentType();
  }

  /** Classifies the supported species so dense BNS phases are treated as nonaqueous. */
  private void setBnsComponentType() {
    super.setComponentType("HC".equals(componentName) || "methane".equals(componentName) ? "HC" : "inert");
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    fugacityCoefficient = super.fugcoef(phase);
    if (phase.useVolumeCorrection()) {
      // The same component offset applies in each phase and cancels from the equilibrium ratio.
      fugacityCoefficient *= Math.exp(-phase.getPressure() * getVolumeCorrection() / (R * phase.getTemperature()));
    }
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdP(PhaseInterface phase) {
    super.logfugcoefdP(phase);
    if (phase.useVolumeCorrection()) {
      dfugdp -= getVolumeCorrection() / (R * phase.getTemperature());
    }
    return dfugdp;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdT(PhaseInterface phase) {
    super.logfugcoefdT(phase);
    if (phase.useVolumeCorrection()) {
      double temperature = phase.getTemperature();
      dfugdt += phase.getPressure() * getVolumeCorrection() / (R * temperature * temperature);
    }
    return dfugdt;
  }

  /** {@inheritDoc} */
  @Override
  public ComponentBNS clone() {
    return (ComponentBNS) super.clone();
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    a = omegaA * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    return a;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    b = getOmegaB() * R * criticalTemperature / criticalPressure;
    return b;
  }

  /** {@inheritDoc} */
  @Override
  public double getOmegaB() {
    return hasOmegaBOverride() ? getOmegaBOverride() : omegaB;
  }
}
