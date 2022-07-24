package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * CPAFunctionDens class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CPAFunctionDens extends CPAFunction {
  static Logger logger = LogManager.getLogger(CPAFunctionDens.class);

  int phasetype = 1;

  /**
   * <p>
   * Constructor for CPAFunctionDens.
   * </p>
   */
  public CPAFunctionDens() {}

  /**
   * <p>
   * Constructor for CPAFunctionDens.
   * </p>
   *
   * @param phase a int
   */
  public CPAFunctionDens(int phase) {
    phasetype = phase;
  }

  /** {@inheritDoc} */
  @Override
  public double calcTrueValue(double val) {
    return val;
  }

  // public double calcValue(double[] dependentValues){
  // system.setTemperature(dependentValues[0]);
  // system.init(0);
  // system.init(1);
  // system.initPhysicalProperties();
  // return system.getPhase(phasetype).getPhysicalProperties().getDensity();
  // }
  /**
   * <p>
   * calcValue2.
   * </p>
   *
   * @param dependentValues an array of {@link double} objects
   * @return a double
   */
  public double calcValue2(double[] dependentValues) {
    system.setTemperature(dependentValues[0]);
    system.setPressure(1.0); // system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));
    try {
      thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.toString());
    }
    system.initPhysicalProperties();
    // System.out.println("pres: " + system.getPressure());
    return system.getPhase(phasetype).getPhysicalProperties().getDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    system.setTemperature(dependentValues[0]);
    // system.setPressure(system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));

    system.init(0);
    system.init(1);
    system.initPhysicalProperties();
    // System.out.println("pres: " + system.getPressure());
    return system.getPhase(phasetype).getPhysicalProperties().getDensity();
  }
}
