/*
 * PhaseGENRTLmodifiedHV.java
 *
 * Created on 18. juli 2000, 18:32
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGENRTLmodifiedHV;

/**
 * <p>
 * PhaseGENRTLmodifiedHV class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGENRTLmodifiedHV extends PhaseGENRTL {
  private static final long serialVersionUID = 1000;

  double[][] DijT;
  int type = 0;

  /**
   * <p>
   * Constructor for PhaseGENRTLmodifiedHV.
   * </p>
   */
  public PhaseGENRTLmodifiedHV() {
    super();
    mixRuleEos = mixSelect.getMixingRule(1);
  }

  /**
   * <p>
   * Constructor for PhaseGENRTLmodifiedHV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of {@link double} objects
   * @param Dij an array of {@link double} objects
   * @param mixRule an array of {@link String} objects
   * @param intparam an array of {@link double} objects
   */
  public PhaseGENRTLmodifiedHV(PhaseInterface phase, double[][] alpha, double[][] Dij,
      String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGENRTLmodifiedHV[alpha[0].length];
    type = 0;
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGENRTLmodifiedHV(phase.getComponents()[i].getName(),
          phase.getComponents()[i].getNumberOfmoles(),
          phase.getComponents()[i].getNumberOfMolesInPhase(),
          phase.getComponents()[i].getComponentNumber());
    }
  }

  /**
   * <p>
   * Constructor for PhaseGENRTLmodifiedHV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of {@link double} objects
   * @param Dij an array of {@link double} objects
   * @param DijT an array of {@link double} objects
   * @param mixRule an array of {@link String} objects
   * @param intparam an array of {@link double} objects
   */
  public PhaseGENRTLmodifiedHV(PhaseInterface phase, double[][] alpha, double[][] Dij,
      double[][] DijT, String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGENRTLmodifiedHV[alpha[0].length];
    type = 1;
    this.DijT = DijT;
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGENRTLmodifiedHV(phase.getComponents()[i].getName(),
          phase.getComponents()[i].getNumberOfmoles(),
          phase.getComponents()[i].getNumberOfMolesInPhase(),
          phase.getComponents()[i].getComponentNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase);
    componentArray[compNumber] =
        new ComponentGENRTLmodifiedHV(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    this.DijT = mixSelect.getHVDijT();
    this.intparam = mixSelect.getSRKbinaryInteractionParameters();
    this.alpha = mixSelect.getHValpha();
    this.mixRule = mixSelect.getClassicOrHV();
    this.Dij = mixSelect.getHVDij();
  }

  /** {@inheritDoc} */
  @Override
  public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT,
      String[][] mixRule, double[][] intparam) {
    this.mixRule = mixRule;
    this.alpha = alpha;
    this.Dij = Dij;
    type = 1;
    this.DijT = DijT;
    this.intparam = intparam;
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    for (int i = 0; i < DijT.length; i++) {
      System.arraycopy(DijT[i], 0, this.DijT[i], 0, DijT[0].length);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (type == 0) {
        GE += phase.getComponents()[i].getx()
            * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase,
                numberOfComponents, temperature, pressure, pt, alpha, Dij, intparam, mixRule));
      } else if (type == 1) {
        GE += phase.getComponents()[i].getx() * Math
            .log(((ComponentGENRTLmodifiedHV) componentArray[i]).getGamma(phase, numberOfComponents,
                temperature, pressure, pt, alpha, Dij, DijT, intparam, mixRule));
      }
    }
    return R * phase.getTemperature() * phase.getNumberOfMolesInPhase() * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    double val = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      val +=
          getComponent(i).getNumberOfMolesInPhase() * (getComponent(i).getLogFugacityCoefficient()); // +Math.log(getComponent(i).getx()*getComponent(i).getAntoineVaporPressure(temperature)));
    }
    return R * temperature * numberOfMolesInPhase * (val + Math.log(pressure));
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    double val = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      val -= getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getdfugdt();
    }
    return R * temperature * temperature * val;
  }
}
