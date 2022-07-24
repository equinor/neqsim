package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * ClassicAcentricDens class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ClassicAcentricDens extends ClassicAcentricFunction {
  static Logger logger = LogManager.getLogger(ClassicAcentricDens.class);

  int phasetype = 1;

  /**
   * <p>
   * Constructor for ClassicAcentricDens.
   * </p>
   */
  public ClassicAcentricDens() {}

  /**
   * <p>
   * Constructor for ClassicAcentricDens.
   * </p>
   *
   * @param phase a int
   */
  public ClassicAcentricDens(int phase) {
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

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    system.setTemperature(dependentValues[0]);
    system.setPressure(
        system.getPhases()[0].getComponents()[0].getAntoineVaporPressure(dependentValues[0]));
    // System.out.println("pres from antoine: " + system.getPressure());
    system.init(0);
    system.init(1);
    try {
      thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.toString());
    }
    // System.out.println("pres: " + system.getPressure());
    system.initPhysicalProperties();
    return system.getPhase(phasetype).getPhysicalProperties().getDensity();
  }
}
