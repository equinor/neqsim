/*
 * PhaseGEUniquac.java
 *
 * Created on 11. juli 2000, 21:01
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGEUniquac;

/**
 * <p>
 * PhaseGEUniquac class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEUniquac extends PhaseGE {
  private static final long serialVersionUID = 1000;

  double[][] alpha;
  String[][] mixRule;
  double[][] intparam;
  double[][] Dij;
  double GE = 0.0;

  /**
   * <p>
   * Constructor for PhaseGEUniquac.
   * </p>
   */
  public PhaseGEUniquac() {
    super();
    componentArray = new ComponentGEInterface[MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * <p>
   * Constructor for PhaseGEUniquac.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of {@link double} objects
   * @param Dij an array of {@link double} objects
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of {@link double} objects
   */
  public PhaseGEUniquac(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
      double[][] intparam) {
    super();
    componentArray = new ComponentGEUniquac[alpha[0].length];
    this.mixRule = mixRule;
    this.alpha = alpha;
    this.Dij = Dij;
    this.intparam = intparam;
    for (int i = 0; i < alpha[0].length; i++) {
      numberOfComponents++;
      componentArray[i] = new ComponentGEUniquac(phase.getComponents()[i].getName(),
          phase.getComponents()[i].getNumberOfmoles(),
          phase.getComponents()[i].getNumberOfMolesInPhase(),
          phase.getComponents()[i].getComponentNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase);
    componentArray[compNumber] = new ComponentGEUniquac(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int type, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, type, pt, beta);
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy() {
    // GE = getExcessGibbsEnergy(this, numberOfComponents, temperature, pressure,
    // phaseType);
    return GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, int phasetype) {
    GE = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponents()[i].getx()
          * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase, numberOfComponents,
              temperature, pressure, phasetype, alpha, Dij, intparam, mixRule));
    }

    return R * temperature * numberOfMolesInPhase * GE;
  }
}
