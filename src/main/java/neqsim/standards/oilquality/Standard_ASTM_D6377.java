package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.exception.IsNaNException;

/**
 * <p>
 * Standard_ASTM_D6377 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Standard_ASTM_D6377 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Standard_ASTM_D6377.class);

  String unit = "bara";
  double RVP = 1.0;
  double TVP = 1.0;
  double referenceTemperature = 37.8;
  String referenceTemperatureUnit = "C";
  String methodRVP = "VPCR4"; // RVP_ASTM_D6377 // RVP_ASTM_D323_73_79
                              // RVP_ASTM_D323_82 // VPCR4_no_water // VPCR4

  private double VPCR4_no_water = 0.0;
  private double VPCR4 = 0.0;
  private double RVP_ASTM_D6377 = 0.0;
  private double RVP_ASTM_D323_73_79 = 0.0;
  private double RVP_ASTM_D323_82 = 0.0;

  /**
   * Gets the method used for measuring Reid Vapor Pressure (RVP).
   *
   * <p>
   * The method can be one of the following:
   * </p>
   * <ul>
   * <li>RVP_ASTM_D6377</li>
   * <li>RVP_ASTM_D323_73_79</li>
   * <li>RVP_ASTM_D323_82</li>
   * <li>VPCR4</li>
   * </ul>
   *
   * @return the method used for RVP measurement.
   */
  public String getMethodRVP() {
    return methodRVP;
  }

  /**
   * Sets the method used for measuring Reid Vapor Pressure (RVP).
   *
   * <p>
   * The method should be one of the following:
   * </p>
   * <ul>
   * <li>RVP_ASTM_D6377</li>
   * <li>RVP_ASTM_D323_73_79</li>
   * <li>RVP_ASTM_D323_82</li>
   * <li>VPCR4</li>
   * </ul>
   *
   * @param methodRVP the method to set for RVP measurement.
   */
  public void setMethodRVP(String methodRVP) {
    this.methodRVP = methodRVP;
  }

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

  /**
   * Checks if the fluid composition is suitable for RVP (Reid Vapor Pressure) calculation.
   *
   * <p>
   * RVP is only meaningful for fluids that can form a liquid phase at reference conditions (37.8°C,
   * ~1 bara). Light gas mixtures dominated by methane/ethane will not have a bubble point and
   * cannot have RVP calculated.
   * </p>
   *
   * @return true if the fluid is suitable for RVP calculation, false otherwise
   */
  public boolean isSuitableForRVP() {
    if (thermoSystem == null || thermoSystem.getNumberOfComponents() == 0) {
      return false;
    }

    // Check molar mass - fluids lighter than ~44 g/mol (propane) are unlikely to form liquid
    // at RVP conditions. Use a threshold slightly below propane to allow for some margin.
    double molarMass = thermoSystem.getMolarMass("kg/mol") * 1000.0; // convert to g/mol
    if (molarMass < 35.0) {
      logger.debug("Fluid too light for RVP calculation (molar mass: {} g/mol)", molarMass);
      return false;
    }

    // Check for presence of components heavier than ethane (C3+)
    // If fluid is >90% methane+ethane, RVP is not meaningful
    double lightFraction = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      String compName = thermoSystem.getComponent(i).getComponentName().toLowerCase();
      if (compName.equals("methane") || compName.equals("ethane") || compName.equals("nitrogen")
          || compName.equals("co2") || compName.equals("hydrogen")) {
        lightFraction += thermoSystem.getComponent(i).getz();
      }
    }
    if (lightFraction > 0.95) {
      logger.debug("Fluid composition too light for RVP (light gas fraction: {})", lightFraction);
      return false;
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    // Skip calculation if fluid is not suitable for RVP
    if (!isSuitableForRVP()) {
      return;
    }

    this.thermoSystem.setTemperature(referenceTemperature, "C");
    this.thermoSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
    this.thermoOps = new ThermodynamicOperations(thermoSystem);
    try {
      this.thermoOps.bubblePointPressureFlash(false);
    } catch (IsNaNException ex) {
      logger.debug("RVP calculation failed for this fluid composition: {}", ex.getMessage());
      return;
    }

    TVP = this.thermoSystem.getPressure();

    this.thermoSystem.setPressure(TVP * 0.9);
    try {
      // ASTM D323 -08 method is used for this property calculation. It is defined at
      // the pressure
      // at 100°F (37.8°C) at which 80% of the stream by volume is vapor at 100°F. In
      this.thermoOps.TVfractionFlash(0.8);
    } catch (Exception ex) {
      logger.debug("Not able to find RVP for this fluid composition: {}", ex.getMessage());
    }

    VPCR4 = this.thermoSystem.getPressure();
    RVP_ASTM_D6377 = 0.834 * VPCR4;
    RVP_ASTM_D323_82 = (0.752 * (100.0 * this.thermoSystem.getPressure()) + 6.07) / 100.0;

    SystemInterface fluid1 = this.thermoSystem.clone();
    this.thermoSystem.setPressure(TVP * 0.9);
    if (fluid1.hasComponent("water")) {
      fluid1.removeComponent("water");
      fluid1.init(0);
    }
    try {
      // ASTM D323 -08 method is used for this property calculation. It is defined at
      // the pressure
      // at 100°F (37.8°C) at which 80% of the stream by volume is vapor at 100°F. In
      this.thermoOps.TVfractionFlash(0.8);
    } catch (Exception ex) {
      logger.debug("RVP calculation without water failed: {}", ex.getMessage());
    }
    VPCR4_no_water = this.thermoSystem.getPressure();
    RVP_ASTM_D323_73_79 = VPCR4_no_water;
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
  public double getValue(String returnParameter, String returnUnit) {
    if ("RVP".equals(returnParameter)) {
      double RVPlocal = getValue("RVP");
      neqsim.util.unit.PressureUnit presConversion =
          new neqsim.util.unit.PressureUnit(RVPlocal, "bara");
      return presConversion.getValue(returnUnit);
    }
    if ("TVP".equals(returnParameter)) {
      neqsim.util.unit.PressureUnit presConversion =
          new neqsim.util.unit.PressureUnit(getValue("TVP"), "bara");
      return presConversion.getValue(returnUnit);
    } else {
      return RVP;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if (returnParameter.equals("RVP")) {
      switch (methodRVP) {
        case "RVP_ASTM_D6377":
          return RVP_ASTM_D6377;
        case "RVP_ASTM_D323_73_79":
          return RVP_ASTM_D323_73_79;
        case "VPCR4":
          return VPCR4;
        case "RVP_ASTM_D323_82":
          return RVP_ASTM_D323_82;
        case "VPCR4_no_water":
          return VPCR4_no_water;
        default:
          return VPCR4;
      }
    } else if (returnParameter.equals("TVP")) {
      return TVP;
    } else {
      logger.error("returnParameter not supported.. " + returnParameter);
      return 0.0;
    }
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
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.0006538);
    testSystem.addComponent("ethane", 0.006538);
    testSystem.addComponent("propane", 0.006538);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.addComponent("water", 0.00545);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.calculate();
    System.out.println("RVP " + standard.getValue("RVP", "bara"));
    standard.setMethodRVP("RVP_ASTM_D323_73_79");
    standard.calculate();
    System.out.println("RVP_ASTM_D323_73_79 " + standard.getValue("RVP", "bara"));
  }
}
