package neqsim.fluidMechanics.util.parameterFitting.masstransfer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * MassTransferFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class MassTransferFunction extends
    neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction {
  static Logger logger = LogManager.getLogger(MassTransferFunction.class);

  /**
   * <p>
   * Constructor for MassTransferFunction.
   * </p>
   */
  public MassTransferFunction() {
    params = new double[1];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    system.setTemperature(dependentValues[0]);
    system.init(0);
    system.init(1);
    try {
      thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return Math.log(system.getPressure());
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
    system.getPhases()[0].getComponents()[i].setAcentricFactor(value);
    system.getPhases()[1].getComponents()[i].setAcentricFactor(value);
  }
}
