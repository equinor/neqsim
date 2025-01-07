package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEWilson;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * <p>
 * PhaseGEWilson class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEWilson extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double GE = 0;

  /**
   * <p>
   * Constructor for PhaseGEWilson.
   * </p>
   */
  public PhaseGEWilson() {
    componentArray = new ComponentGEWilson[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * <p>
   * Constructor for PhaseGEWilson.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGEWilson(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
      double[][] intparam) {
    componentArray = new ComponentGEWilson[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      numberOfComponents++;
      componentArray[i] = new ComponentGEWilson(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setAlpha(double[][] alpha) {
    throw new UnsupportedOperationException("Unimplemented method 'setAlpha'");
  }

  /** {@inheritDoc} */
  @Override
  public void setDij(double[][] Dij) {
    throw new UnsupportedOperationException("Unimplemented method 'setDij'");
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    throw new UnsupportedOperationException("Unimplemented method 'setDijT'");
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGEWilson(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
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
    // pt);
    return GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    GE = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx()
          * Math.log(((ComponentGEWilson) componentArray[i]).getWilsonActivityCoefficient(phase));
    }

    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    throw new UnsupportedOperationException("Unimplemented method 'molarVolume'");
  }
}
