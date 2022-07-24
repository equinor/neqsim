package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.hydrate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.component.ComponentHydrate;

/**
 * <p>
 * HydrateFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class HydrateFunction extends LevenbergMarquardtFunction {
  static Logger logger = LogManager.getLogger(HydrateFunction.class);

  /**
   * <p>
   * Constructor for HydrateFunction.
   * </p>
   */
  public HydrateFunction() {
    // params = new double[3];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    try {
      thermoOps.hydrateFormationTemperature(1);
      // System.out.println("temperature " + system.getTemperature());
    } catch (Exception ex) {
      logger.error(ex.toString());
    }
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
    // int structure = 1;
    params[i] = value;
    // if(i==0) ((ComponentHydrate)
    // system.getPhase(4).getComponent("water")).setDGfHydrate(value, structure);
    // if(i==1) ((ComponentHydrate)
    // system.getPhase(4).getComponent("water")).setDHfHydrate(value, structure);
    // int k=0;

    // if(i==0) ((ComponentHydrate)
    // system.getPhase(4).getComponent("water")).setEmptyHydrateVapourPressureConstant(0,0,
    // value);
    // if(i==1) ((ComponentHydrate)
    // system.getPhase(4).getComponent("water")).setEmptyHydrateVapourPressureConstant(0,1,
    // value);

    // for(int k=0;k<system.getNumberOfPhases();k++){
    // if(i==0)
    // system.getPhase(k).getComponent(0).setLennardJonesEnergyParameter(value);
    // if(i==1)
    // system.getPhase(k).getComponent(0).setLennardJonesMolecularDiameter(value);
    // if(i==2) system.getPhase(k).getComponent(0).setSphericalCoreRadius(value);
    // }

    if (i == 0) {
      ((ComponentHydrate) system.getPhase(4).getComponent(0))
          .setLennardJonesEnergyParameterHydrate(value);
    }
    if (i == 1) {
      ((ComponentHydrate) system.getPhase(4).getComponent(0))
          .setLennardJonesMolecularDiameterHydrate(value);
    }
    if (i == 2) {
      ((ComponentHydrate) system.getPhase(4).getComponent(0)).setSphericalCoreRadiusHydrate(value);
    }
  }
}
