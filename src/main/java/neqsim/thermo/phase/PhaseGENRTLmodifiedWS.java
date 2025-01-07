package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGENRTLmodifiedWS;

/**
 * <p>
 * PhaseGENRTLmodifiedWS class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGENRTLmodifiedWS extends PhaseGENRTLmodifiedHV {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseGENRTLmodifiedWS.
   * </p>
   */
  public PhaseGENRTLmodifiedWS() {}

  /**
   * <p>
   * Constructor for PhaseGENRTLmodifiedWS.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGENRTLmodifiedWS(PhaseInterface phase, double[][] alpha, double[][] Dij,
      String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGENRTLmodifiedWS[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      numberOfComponents++;
      componentArray[i] = new ComponentGENRTLmodifiedWS(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
  }

  /**
   * <p>
   * Constructor for PhaseGENRTLmodifiedWS.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param DijT an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGENRTLmodifiedWS(PhaseInterface phase, double[][] alpha, double[][] Dij,
      double[][] DijT, String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, DijT, mixRule, intparam);
    componentArray = new ComponentGENRTLmodifiedWS[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGENRTLmodifiedWS(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    this.intparam = mixSelect.getWSintparam();
    this.alpha = mixSelect.getNRTLalpha();
    this.mixRule = mixSelect.getClassicOrHV();
    this.Dij = mixSelect.getNRTLDij();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] =
        new ComponentGENRTLmodifiedWS(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    // TODO: why is GE a local variable?
    double GE = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (type == 0) {
        GE += phase.getComponent(i).getx()
            * Math.log(((ComponentGEInterface) componentArray[i]).getGamma(phase,
                numberOfComponents, temperature, pressure, pt, alpha, Dij, intparam, mixRule));
      } else if (type == 1) {
        GE += phase.getComponent(i).getx() * Math
            .log(((ComponentGENRTLmodifiedWS) componentArray[i]).getGamma(phase, numberOfComponents,
                temperature, pressure, pt, alpha, Dij, DijT, intparam, mixRule));
      }
    }
    return R * temperature * numberOfMolesInPhase * GE;
  }
}
