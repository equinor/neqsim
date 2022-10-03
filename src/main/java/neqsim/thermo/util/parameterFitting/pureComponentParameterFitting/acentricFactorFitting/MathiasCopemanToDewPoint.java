package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>
 * MathiasCopemanToDewPoint class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class MathiasCopemanToDewPoint extends LevenbergMarquardtFunction {
  static Logger logger = LogManager.getLogger(MathiasCopemanToDewPoint.class);

  /**
   * <p>
   * Constructor for MathiasCopemanToDewPoint.
   * </p>
   */
  public MathiasCopemanToDewPoint() {
    params = new double[3];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    // System.out.println("dep " + dependentValues[0]);
    system.setPressure(dependentValues[0]);

    // System.out.println("antoine pres: " + system.getPressure());
    system.init(0);
    system.init(1);
    try {
      thermoOps.dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.toString());
    }
    // System.out.println("pres: " + system.getPressure());
    return system.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double calcTrueValue(double val) {
    return val;
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;

    if (system.getPhases()[0].getNumberOfComponents() == 1) {
      if (i < 3)
        ;
      else if (i < 6) {
        i -= 3;
      } else if (i < 9) {
        i -= 6;
      } else if (i < 12) {
        i -= 9;
      } else if (i < 15) {
        i -= 12;
      } else if (i < 18) {
        i -= 15;
      } else if (i < 21) {
        i -= 18;
      } else if (i < 24) {
        i -= 21;
      } else if (i < 27) {
        i -= 24;
      } else if (i < 30) {
        i -= 27;
      } else if (i < 33) {
        i -= 30;
      }

      system.getPhases()[1].getComponent(0).setMatiascopemanParams(i, value);
      system.getPhases()[0].getComponent(0).getAttractiveTerm().setParameters(i, value);
      system.getPhases()[1].getComponent(0).getAttractiveTerm().setParameters(i, value);
      return;
    }

    if (system.getPhase(0).hasComponent("methane") && i < 3) {
      system.getPhases()[0].getComponent("methane").setMatiascopemanParams(i, value);
      system.getPhases()[1].getComponent("methane").setMatiascopemanParams(i, value);
      system.getPhases()[0].getComponent("methane").getAttractiveTerm().setParameters(i, value);
      system.getPhases()[1].getComponent("methane").getAttractiveTerm().setParameters(i, value);
      return;
    }

    if (system.getPhase(0).hasComponent("ethane") && i < 6) {
      system.getPhases()[0].getComponent("ethane").setMatiascopemanParams(i - 3, value);
      system.getPhases()[1].getComponent("ethane").setMatiascopemanParams(i - 3, value);
      system.getPhases()[0].getComponent("ethane").getAttractiveTerm().setParameters(i - 3, value);
      system.getPhases()[1].getComponent("ethane").getAttractiveTerm().setParameters(i - 3, value);
      return;
    }

    if (system.getPhase(0).hasComponent("propane") && i < 9) {
      system.getPhases()[0].getComponent("propane").setMatiascopemanParams(i - 6, value);
      system.getPhases()[1].getComponent("propane").setMatiascopemanParams(i - 6, value);
      system.getPhases()[0].getComponent("propane").getAttractiveTerm().setParameters(i - 6, value);
      system.getPhases()[1].getComponent("propane").getAttractiveTerm().setParameters(i - 6, value);
      return;
    }

    if (system.getPhase(0).hasComponent("n-butane") && i < 12) {
      system.getPhases()[0].getComponent("n-butane").setMatiascopemanParams(i - 9, value);
      system.getPhases()[1].getComponent("n-butane").setMatiascopemanParams(i - 9, value);
      system.getPhases()[0].getComponent("n-butane").getAttractiveTerm().setParameters(i - 9,
          value);
      system.getPhases()[1].getComponent("n-butane").getAttractiveTerm().setParameters(i - 9,
          value);
      return;
    }

    if (system.getPhase(0).hasComponent("i-butane") && i < 15) {
      system.getPhases()[0].getComponent("i-butane").setMatiascopemanParams(i - 12, value);
      system.getPhases()[1].getComponent("i-butane").setMatiascopemanParams(i - 12, value);
      system.getPhases()[0].getComponent("i-butane").getAttractiveTerm().setParameters(i - 12,
          value);
      system.getPhases()[1].getComponent("i-butane").getAttractiveTerm().setParameters(i - 12,
          value);
      return;
    }

    if (system.getPhase(0).hasComponent("n-pentane") && i < 18) {
      system.getPhases()[0].getComponent("n-pentane").setMatiascopemanParams(i - 15, value);
      system.getPhases()[1].getComponent("n-pentane").setMatiascopemanParams(i - 15, value);
      system.getPhases()[0].getComponent("n-pentane").getAttractiveTerm().setParameters(i - 15,
          value);
      system.getPhases()[1].getComponent("n-pentane").getAttractiveTerm().setParameters(i - 15,
          value);
      return;
    }

    if (system.getPhase(0).hasComponent("c-hexane") && i < 21) {
      system.getPhases()[0].getComponent("c-hexane").setMatiascopemanParams(i - 18, value);
      system.getPhases()[1].getComponent("c-hexane").setMatiascopemanParams(i - 18, value);
      system.getPhases()[0].getComponent("c-hexane").getAttractiveTerm().setParameters(i - 18,
          value);
      system.getPhases()[1].getComponent("c-hexane").getAttractiveTerm().setParameters(i - 18,
          value);
      return;
    }

    if (system.getPhase(0).hasComponent("benzene") && i < 24) {
      system.getPhases()[0].getComponent("benzene").setMatiascopemanParams(i - 21, value);
      system.getPhases()[1].getComponent("benzene").setMatiascopemanParams(i - 21, value);
      system.getPhases()[0].getComponent("benzene").getAttractiveTerm().setParameters(i - 21,
          value);
      system.getPhases()[1].getComponent("benzene").getAttractiveTerm().setParameters(i - 21,
          value);
      return;
    }

    if (system.getPhase(0).hasComponent("n-heptane") && i < 27) {
      system.getPhases()[0].getComponent("n-heptane").setMatiascopemanParams(i - 24, value);
      system.getPhases()[1].getComponent("n-heptane").setMatiascopemanParams(i - 24, value);
      system.getPhases()[0].getComponent("n-heptane").getAttractiveTerm().setParameters(i - 24,
          value);
      system.getPhases()[1].getComponent("n-heptane").getAttractiveTerm().setParameters(i - 24,
          value);
    }
  }
}
