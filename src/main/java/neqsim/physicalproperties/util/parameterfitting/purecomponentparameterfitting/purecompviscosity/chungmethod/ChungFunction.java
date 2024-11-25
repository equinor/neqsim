package neqsim.physicalproperties.util.parameterfitting.purecomponentparameterfitting.purecompviscosity.chungmethod;

import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * <p>
 * ChungFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChungFunction extends LevenbergMarquardtFunction {
  /**
   * <p>
   * Constructor for ChungFunction.
   * </p>
   */
  public ChungFunction() {
    params = new double[1];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    system.setTemperature(dependentValues[0]);
    system.init(1);
    system.initPhysicalProperties();
    return system.getPhase(1).getPhysicalProperties().getViscosity();
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
    system.getPhase(0).getComponent(i).setViscosityAssociationFactor(value);
    system.getPhase(1).getComponent(i).setViscosityAssociationFactor(value);
  }
}
