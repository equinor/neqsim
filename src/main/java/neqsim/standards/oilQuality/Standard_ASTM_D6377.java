package neqsim.standards.oilQuality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Standard_ASTM_D6377 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Standard_ASTM_D6377 extends neqsim.standards.Standard {
  private static final long serialVersionUID = 1L;
  static Logger logger = LogManager.getLogger(Standard_ASTM_D6377.class);

  String unit = "bara";
  double RVP = 1.0;
  double TVP = 1.0;
  double referenceTemperature = 37.8;
  String referenceTemperatureUnit = "C";

  /**
   * <p>
   * Constructor for Standard_ASTM_D6377.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ASTM_D6377(SystemInterface thermoSystem) {
    super("Standard_ASTM_D6377", "Standard_ASTM_D6377", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    this.thermoSystem.setTemperature(referenceTemperature, "C");
    this.thermoSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
    this.thermoOps = new ThermodynamicOperations(thermoSystem);
    try {
      this.thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    TVP = this.thermoSystem.getPressure();

    this.thermoSystem.setPressure(TVP * 0.9);
    try {
      // ASTM D323 -08 method is used for this property calculation. It is defined at the pressure
      // at 100°F (37.8°C) at which 80% of the stream by volume is vapor at 100°F. In
      this.thermoOps.TVfractionFlash(0.8);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    RVP = this.thermoSystem.getPressure();
  }

  // old method for RVP
  public void calculate2() {
    this.thermoSystem.setTemperature(referenceTemperature, "C");
    this.thermoSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
    this.thermoOps = new ThermodynamicOperations(thermoSystem);
    try {
      this.thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    TVP = this.thermoSystem.getPressure();
    double liquidVolume = thermoSystem.getVolume();

    this.thermoSystem.setPressure(TVP * 0.9);
    try {
      this.thermoOps.TVflash(liquidVolume * 4.0);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    RVP = (0.752 * (100.0 * this.thermoSystem.getPressure()) + 6.07) / 100.0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return unit;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, java.lang.String returnUnit) {
    if (returnParameter == "RVP") {
      neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(RVP, "bara");
      return presConversion.getValue(returnUnit);
    }
    if (returnParameter == "TVP") {
      neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(TVP, "bara");
      return presConversion.getValue(returnUnit);
    } else {
      return RVP;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    return RVP;
  }

  /**
   * <p>
   * setReferenceTemperature.
   * </p>
   *
   * @param refTemp a double
   * @param refTempUnit a {@link java.lang.String} object
   */
  public void setReferenceTemperature(double refTemp, String refTempUnit) {
    neqsim.util.unit.TemperatureUnit tempConversion =
        new neqsim.util.unit.TemperatureUnit(refTemp, refTempUnit);
    referenceTemperature = tempConversion.getValue(refTemp, refTempUnit, "C");
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.0006538);
    testSystem.addComponent("ethane", 0.006538);
    testSystem.addComponent("propane", 0.006538);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.calculate();
    System.out.println("RVP " + standard.getValue("RVP", "bara"));
  }
}
