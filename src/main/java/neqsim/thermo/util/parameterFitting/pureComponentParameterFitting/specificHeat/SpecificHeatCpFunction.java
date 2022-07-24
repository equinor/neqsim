package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.specificHeat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>
 * SpecificHeatCpFunction class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SpecificHeatCpFunction extends LevenbergMarquardtFunction {
  static Logger logger = LogManager.getLogger(SpecificHeatCpFunction.class);

  /**
   * <p>
   * Constructor for SpecificHeatCpFunction.
   * </p>
   */
  public SpecificHeatCpFunction() {
    params = new double[4];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    system.init(0);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.toString());
    }

    system.init(3);
    return system.getPhase(0).getCp("kJ/kgK");
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
    if (i == 1) {
      system.getPhases()[0].getComponents()[0].setCpB(value);
      system.getPhases()[1].getComponents()[0].setCpB(value);
    }
    if (i == 0) {
      system.getPhases()[0].getComponents()[0].setCpA(value);
      system.getPhases()[1].getComponents()[0].setCpA(value);
    }
    if (i == 2) {
      system.getPhases()[0].getComponents()[0].setCpC(value);
      system.getPhases()[1].getComponents()[0].setCpC(value);
    }
    if (i == 3) {
      system.getPhases()[0].getComponents()[0].setCpD(value);
      system.getPhases()[1].getComponents()[0].setCpD(value);
    }
  }
}
