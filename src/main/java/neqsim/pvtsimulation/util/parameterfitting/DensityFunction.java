package neqsim.pvtsimulation.util.parameterfitting;

import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * DensityFunction class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class DensityFunction extends LevenbergMarquardtFunction {
  double molarMass = 0.0;

  /**
   * Constructor for DensityFunction.
   */
  public DensityFunction() {
    params = new double[1];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    system.setTemperature(dependentValues[0]);
    thermoOps.TPflash();
    system.initPhysicalProperties();

    // system.display();
    return system.getPhase(0).getPhysicalProperties().getDensity();
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
  }
}
