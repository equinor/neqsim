package neqsim.physicalproperties.util.parameterfitting.purecomponentparameterfitting.purecompviscosity.chungmethod;

import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * ChungFunction class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChungFunction extends LevenbergMarquardtFunction {
  /**
   * Constructor for ChungFunction.
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
    return system.getPhases()[1].getPhysicalProperties().getViscosity();
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
    system.getPhases()[0].getComponent(i).setViscosityAssociationFactor(value);
    system.getPhases()[1].getComponent(i).setViscosityAssociationFactor(value);
  }
}
