package neqsim.pvtsimulation.util.parameterfitting;

import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * <p>
 * FunctionJohanSverderup class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class FunctionJohanSverderup extends LevenbergMarquardtFunction {
  double molarMass = 0.0;

  /**
   * <p>
   * Constructor for FunctionJohanSverderup.
   * </p>
   */
  public FunctionJohanSverderup() {
    params = new double[1];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    system.addComponent("methane", -system.getPhase(0).getComponent("methane").getNumberOfmoles());
    system.addComponent("methane", params[0]);
    system.init_x_y();
    system.init(1);
    system.setPressure(system.getPressure() - 25.0);
    SaturationPressure satCalc = new SaturationPressure(system);
    double satPres = satCalc.calcSaturationPressure();

    return satPres;
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
  }
}
