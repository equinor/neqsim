package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Draft_ISO18453 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Draft_ISO18453 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Draft_ISO18453.class);
  String dewPointTemperatureUnit = "C";
  String pressureUnit = "bar";
  double dewPointTemperature = 273.0;
  double dewPointTemperatureSpec = -12.0;
  double initTemperature = 273.15;
  SystemInterface thermoSystem;
  ThermodynamicOperations thermoOps;

  /**
   * <p>
   * Constructor for Draft_ISO18453.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Draft_ISO18453(SystemInterface thermoSystem) {
    super("Draft_ISO18453", "water dew point calculation method");

    if (thermoSystem.getModelName().equals("GERGwater")) {
      this.thermoSystem = thermoSystem;
    } else {
      // System.out.println("setting model GERG water...");
      this.thermoSystem = new SystemGERGwaterEos(initTemperature, getReferencePressure());
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        this.thermoSystem.addComponent(thermoSystem.getPhase(0).getComponentName(i),
            thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles());
      }
    }

    this.thermoSystem.setTemperature(273.15);
    this.thermoSystem.setPressure(1.0);
    this.thermoSystem.setMixingRule(8);
    thermoSystem.init(0);
    thermoSystem.init(1);

    this.thermoOps = new ThermodynamicOperations(this.thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    this.thermoSystem.setTemperature(initTemperature);
    this.thermoSystem.setPressure(getReferencePressure());

    try {
      this.thermoOps.waterDewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    dewPointTemperature = this.thermoSystem.getTemperature() - 273.15;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if (returnParameter.equals("dewPointTemperature")) {
      return dewPointTemperature;
    } else {
      return dewPointTemperature;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if (returnParameter.equals("dewPointTemperature")) {
      return dewPointTemperature;
    }
    if (returnParameter.equals("pressure")) {
      return this.thermoSystem.getPressure();
    } else {
      return dewPointTemperature;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if (returnParameter.equals("dewPointTemperature")) {
      return dewPointTemperatureUnit;
    }
    if (returnParameter.equals("pressureUnit")) {
      return this.pressureUnit;
    } else {
      return dewPointTemperatureUnit;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return dewPointTemperature < getSalesContract().getWaterDewPointTemperature();
  }
}
