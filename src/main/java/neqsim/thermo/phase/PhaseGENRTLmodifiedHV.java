/*
 * PhaseGENRTLmodifiedHV.java
 *
 * Created on 18. juli 2000, 18:32
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGENRTLmodifiedHV;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * PhaseGENRTLmodifiedHV class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGENRTLmodifiedHV extends PhaseGENRTL {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] DijT;
  int type = 0;

  /**
   * Constructor for PhaseGENRTLmodifiedHV.
   */
  public PhaseGENRTLmodifiedHV() {
    mixRule = mixSelect.getMixingRule(1);
  }

  /**
   * Constructs the legacy activity-phase representation.
   *
   * <p>
   * Superclass initialization reloads database parameter tables. For an explicit complete HV configuration, call
   * {@link #setParams(PhaseInterface, double[][], double[][], double[][], String[][], double[][])} after construction.
   * This preserves the separate legacy constructor behavior used by the Wong-Sandler adapter.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGENRTLmodifiedHV(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
      double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGENRTLmodifiedHV[alpha[0].length];
    type = 0;
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGENRTLmodifiedHV(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
  }

  /**
   * Constructs the legacy activity-phase representation.
   *
   * <p>
   * Superclass initialization reloads database parameter tables. For an explicit complete HV configuration, call
   * {@link #setParams(PhaseInterface, double[][], double[][], double[][], String[][], double[][])} after construction.
   * This preserves the separate legacy constructor behavior used by the Wong-Sandler adapter.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param DijT an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGENRTLmodifiedHV(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT,
      String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGENRTLmodifiedHV[alpha[0].length];
    type = 1;
    this.DijT = DijT;
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGENRTLmodifiedHV(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
  }

  /**
   * Copies retained GE work state and binds the supplied detached HV parameters.
   *
   * <p>
   * The matrices belong to the copied HV handler. This method does not reload a database or evaluate activity
   * coefficients.
   * </p>
   *
   * @param owner copied EOS phase
   * @param alpha detached nonrandomness matrix
   * @param dij detached directed temperature coefficients in kelvin
   * @param dijT detached dimensionless temperature offsets
   * @param rules detached pair activation choices
   * @param kij detached classical binary interactions
   * @return independent GE state bound to the copied parameter handler
   */
  public PhaseGENRTLmodifiedHV copyForHuronVidal(PhaseInterface owner, double[][] alpha, double[][] dij,
      double[][] dijT, String[][] rules, double[][] kij) {
    PhaseGENRTLmodifiedHV copy = (PhaseGENRTLmodifiedHV) super.clone();
    copy.mixSelect = mixSelect.clone();
    copy.mixRule = copy.mixSelect.getMixingRule(mixingRuleType.getValue());
    copy.setParams(owner, alpha, dij, dijT, rules, kij);
    copy.type = type;
    copy.setProperties(owner);
    return copy;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGENRTLmodifiedHV(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
    this.DijT = mixSelect.getHVDijT();
    this.intparam = mixSelect.getSRKbinaryInteractionParameters();
    this.alpha = mixSelect.getHValpha();
    this.mixRuleString = mixSelect.getClassicOrHV();
    this.Dij = mixSelect.getHVDij();
  }

  /**
   * Binds one complete explicit HV configuration without reloading a database.
   *
   * <p>
   * All matrices must use the owning phase's component order and the same EOS-specific parameter convention. References
   * remain live, matching handler tuning and detached-clone binding; callers must not mutate them during evaluation.
   * Use this method after the legacy constructor when supplying source-qualified inputs directly.
   * </p>
   *
   * @param phase owning cubic phase
   * @param alpha directed dimensionless nonrandomness coefficients
   * @param Dij directed energy coefficients in kelvin
   * @param DijT directed dimensionless temperature offsets
   * @param mixRule explicit HV or classical selection for each pair
   * @param intparam ordinary classical coefficients for the owning EOS
   */
  @Override
  public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT, String[][] mixRule,
      double[][] intparam) {
    this.mixRuleString = mixRule;
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
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    GE = 0.0;
    boolean standard = type == 1;
    for (int i = 0; standard && i < numberOfComponents; i++) {
      standard = componentArray[i].getClass() == ComponentGENRTLmodifiedHV.class;
    }
    if (standard) {
      GE = ComponentGENRTLmodifiedHV.excessGibbsSum(phase, componentArray, numberOfComponents, temperature, pressure,
          pt, alpha, Dij, DijT, intparam, mixRuleString);
      return R * phase.getTemperature() * phase.getNumberOfMolesInPhase() * GE;
    }
    for (int i = 0; i < numberOfComponents; i++) {
      if (type == 0) {
        GE += phase.getComponent(i).getx() * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase,
            numberOfComponents, temperature, pressure, pt, alpha, Dij, intparam, mixRuleString));
      } else if (type == 1) {
        GE += phase.getComponent(i).getx() * Math.log(((ComponentGENRTLmodifiedHV) componentArray[i]).getGamma(phase,
            numberOfComponents, temperature, pressure, pt, alpha, Dij, DijT, intparam, mixRuleString));
      }
    }
    return R * phase.getTemperature() * phase.getNumberOfMolesInPhase() * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    double val = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      val += getComponent(i).getNumberOfMolesInPhase() * (getComponent(i).getLogFugacityCoefficient());
      // +Math.log(getComponent(i).getx()*getComponent(i).getAntoineVaporPressure(temperature)));
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
