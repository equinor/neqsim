package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.freezingFit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermodynamicOperations.flashOps.saturationOps.SolidComplexTemperatureCalc;

/**
 * <p>
 * SolidComplexFunction class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SolidComplexFunction extends LevenbergMarquardtFunction {
  static Logger logger = LogManager.getLogger(SolidComplexFunction.class);

  /**
   * <p>
   * Constructor for SolidComplexFunction.
   * </p>
   */
  public SolidComplexFunction() {}

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    try {
      thermoOps.calcSolidComlexTemperature("TEG", "water");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
    return system.getTemperature(); // for lucia data
    // return system.getPhases()[0].getComponents()[1].getx(); // for MEG
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
    if (i == 1) {
      SolidComplexTemperatureCalc.HrefComplex = value;
    }
    if (i == 0) {
      SolidComplexTemperatureCalc.Kcomplex = value;
    }
    if (i == 2) {
      SolidComplexTemperatureCalc.TrefComplex = value * 100.0;
    }
  }
}
