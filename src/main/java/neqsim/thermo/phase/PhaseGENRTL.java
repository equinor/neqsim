/*
 * PhaseGENRTL.java
 *
 * Created on 17. juli 2000, 20:51
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGeNRTL;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * <p>
 * PhaseGENRTL class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGENRTL extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] alpha;
  String[][] mixRule;
  double[][] intparam;
  double[][] Dij;
  double GE = 0.0;

  /**
   * <p>
   * Constructor for PhaseGENRTL.
   * </p>
   */
  public PhaseGENRTL() {}

  /**
   * <p>
   * Constructor for PhaseGENRTL.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGENRTL(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
      double[][] intparam) {
    componentArray = new ComponentGeNRTL[alpha[0].length];
    this.mixRule = mixRule;
    this.alpha = alpha;
    this.Dij = Dij;
    this.intparam = intparam;
    for (int i = 0; i < alpha[0].length; i++) {
      numberOfComponents++;
      componentArray[i] = new ComponentGeNRTL(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
    setMixingRule(2);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGeNRTL(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    this.intparam = mixSelect.getSRKbinaryInteractionParameters();
    this.alpha = mixSelect.getNRTLalpha();
    this.mixRule = mixSelect.getClassicOrHV();
    this.Dij = mixSelect.getNRTLDij();
  }

  /** {@inheritDoc} */
  @Override
  public void setAlpha(double[][] alpha) {
    for (int i = 0; i < alpha.length; i++) {
      System.arraycopy(alpha[i], 0, this.alpha[i], 0, alpha[0].length);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setDij(double[][] Dij) {
    for (int i = 0; i < Dij.length; i++) {
      System.arraycopy(Dij[i], 0, this.Dij[i], 0, Dij[0].length);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    throw new UnsupportedOperationException("Unimplemented method 'setDijT'");
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy() {
    // double GE = getExcessGibbsEnergy(this, numberOfComponents, temperature,
    // pressure, pt);
    return GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    GE = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx()
          * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase, numberOfComponents,
              temperature, pressure, pt, alpha, Dij, intparam, mixRule));
    }

    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    throw new UnsupportedOperationException("Unimplemented method 'molarVolume'");
  }
}
