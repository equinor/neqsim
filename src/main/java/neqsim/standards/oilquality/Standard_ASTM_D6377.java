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
   * Enumeration of the supported Reid Vapor Pressure (RVP) / vapor-pressure correlation methods.
   *
   * <p>
   * Each constant carries the legacy string label used by the string-based
   * {@link Standard_ASTM_D6377#setMethodRVP(String)} API so the two APIs stay interchangeable.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public enum RvpMethod {
    /** ASTM D6377 RVPE (RVP equivalent), derived from VPCR4. */
    RVP_ASTM_D6377("RVP_ASTM_D6377"),
    /** ASTM D323-73/79 dry method (water removed before flash). */
    RVP_ASTM_D323_73_79("RVP_ASTM_D323_73_79"),
    /** ASTM D323-82 correlation. */
    RVP_ASTM_D323_82("RVP_ASTM_D323_82"),
    /** Vapor pressure at vapor/liquid ratio 4:1 (default). */
    VPCR4("VPCR4"),
    /** Vapor pressure at vapor/liquid ratio 4:1 with water removed. */
    VPCR4_NO_WATER("VPCR4_no_water");

    /** Legacy string label associated with this method. */
    private final String label;

    /**
     * Creates a method constant with its legacy string label.
     *
     * @param label the legacy string label used by the string-based API
     */
    RvpMethod(String label) {
      this.label = label;
    }

    /**
     * Gets the legacy string label for this method.
     *
     * @return the legacy string label
     */
    public String getLabel() {
      return label;
    }

    /**
     * Resolves an {@link RvpMethod} from its legacy string label or enum name.
     *
     * @param label the legacy string label (for example {@code "VPCR4"}) or the enum constant name
     * @return the matching {@link RvpMethod}
     * @throws IllegalArgumentException if no method matches the supplied label
     */
    public static RvpMethod fromLabel(String label) {
      if (label != null) {
	for (RvpMethod method : values()) {
	  if (method.label.equals(label) || method.name().equals(label)) {
	    return method;
	  }
	}
      }
      throw new IllegalArgumentException("Unknown RVP method: " + label);
    }
  }

  /**
   * Immutable structured result of an RVP calculation.
   *
   * <p>
   * Bundles the computed value with the method, reference temperature and a validity flag so callers (especially
   * agentic workflows) can detect a failed calculation instead of silently receiving a zero or {@link Double#NaN}. A
   * result is considered valid when its value is a finite positive number.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class RvpResult {
    /** Computed RVP value in bara. */
    private final double value;

    /** Method used to compute the value. */
    private final RvpMethod method;

    /** Reference temperature in degrees Celsius. */
    private final double referenceTemperatureC;

    /** Whether the value is a finite positive number. */
    private final boolean valid;

    /**
     * Creates an RVP result.
     *
     * @param value                 computed RVP value in bara
     * @param method                method used to compute the value
     * @param referenceTemperatureC reference temperature in degrees Celsius
     */
    public RvpResult(double value, RvpMethod method, double referenceTemperatureC) {
      this.value = value;
      this.method = method;
      this.referenceTemperatureC = referenceTemperatureC;
      this.valid = !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
    }

    /**
     * Gets the computed RVP value.
     *
     * @return the RVP value in bara
     */
    public double getValue() {
      return value;
    }

    /**
     * Gets the method used to compute the value.
     *
     * @return the RVP method
     */
    public RvpMethod getMethod() {
      return method;
    }

    /**
     * Gets the reference temperature.
     *
     * @return the reference temperature in degrees Celsius
     */
    public double getReferenceTemperatureC() {
      return referenceTemperatureC;
    }

    /**
     * Indicates whether the result represents a successful calculation.
     *
     * @return true if the value is a finite positive number, false otherwise
     */
    public boolean isValid() {
      return valid;
    }

    /**
     * Serializes this result to a compact JSON string.
     *
     * @return a JSON representation with {@code value}, {@code method}, {@code referenceTemperatureC} and {@code valid}
     *         fields
     */
    public String toJson() {
      com.google.gson.JsonObject root = new com.google.gson.JsonObject();
      root.addProperty("value", value);
      root.addProperty("unit", "bara");
      root.addProperty("method", method.getLabel());
      root.addProperty("referenceTemperatureC", referenceTemperatureC);
      root.addProperty("valid", valid);
      return root.toString();
    }
  }

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
   * Sets the method used for measuring Reid Vapor Pressure (RVP) using the type-safe enum.
   *
   * @param method the {@link RvpMethod} to use; must not be null
   * @throws IllegalArgumentException if {@code method} is null
   */
  public void setMethodRVP(RvpMethod method) {
    if (method == null) {
      throw new IllegalArgumentException("RVP method must not be null");
    }
    this.methodRVP = method.getLabel();
  }

  /**
   * Returns the RVP value (in bara) computed for the supplied method during the last {@link #calculate()} call.
   *
   * @param method the method whose value should be returned
   * @return the RVP value in bara for the requested method
   */
  private double valueForMethod(RvpMethod method) {
    switch (method) {
    case RVP_ASTM_D6377:
      return RVP_ASTM_D6377;
    case RVP_ASTM_D323_73_79:
      return RVP_ASTM_D323_73_79;
    case RVP_ASTM_D323_82:
      return RVP_ASTM_D323_82;
    case VPCR4_NO_WATER:
      return VPCR4_no_water;
    case VPCR4:
    default:
      return VPCR4;
    }
  }

  /**
   * Returns a structured RVP result for the currently configured method.
   *
   * <p>
   * Call {@link #calculate()} first. The returned {@link RvpResult} carries the value, the method, the reference
   * temperature and a validity flag so a failed calculation (which historically left the value at zero) is detectable
   * via {@link RvpResult#isValid()}.
   * </p>
   *
   * @return the structured RVP result for the configured method
   */
  public RvpResult getRvpResult() {
    return getRvpResult(RvpMethod.fromLabel(methodRVP));
  }

  /**
   * Returns a structured RVP result for a specific method.
   *
   * <p>
   * Call {@link #calculate()} first. All method values are populated by a single {@link #calculate()} call, so this
   * does not trigger a recalculation.
   * </p>
   *
   * @param method the method whose result should be returned; must not be null
   * @return the structured RVP result for the requested method
   * @throws IllegalArgumentException if {@code method} is null
   */
  public RvpResult getRvpResult(RvpMethod method) {
    if (method == null) {
      throw new IllegalArgumentException("RVP method must not be null");
    }
    return new RvpResult(valueForMethod(method), method, referenceTemperature);
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

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    this.thermoSystem.setTemperature(referenceTemperature, "C");
    this.thermoSystem.setPressure(ThermodynamicConstantsInterface.referencePressure);
    this.thermoOps = new ThermodynamicOperations(thermoSystem);
    try {
      this.thermoOps.bubblePointPressureFlash(false);
    } catch (IsNaNException ex) {
      logger.error(ex.getMessage(), ex);
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
      logger.debug("not able to find RVP: {}", ex.getMessage());
    }

    VPCR4 = this.thermoSystem.getPressure();
    RVP_ASTM_D6377 = 0.834 * VPCR4;
    RVP_ASTM_D323_82 = (0.752 * (100.0 * this.thermoSystem.getPressure()) + 6.07) / 100.0;

    SystemInterface fluid1 = this.thermoSystem.clone();
    if (fluid1.hasComponent("water")) {
      fluid1.removeComponent("water");
    }
    fluid1.setTemperature(referenceTemperature, "C");
    fluid1.setPressure(ThermodynamicConstantsInterface.referencePressure);
    fluid1.init(0);
    ThermodynamicOperations opsNoWater = new ThermodynamicOperations(fluid1);
    try {
      opsNoWater.bubblePointPressureFlash(false);
      double tvpNoWater = fluid1.getPressure();
      fluid1.setPressure(tvpNoWater * 0.9);
      // ASTM D323 -08 method is used for this property calculation. It is defined at
      // the pressure
      // at 100°F (37.8°C) at which 80% of the stream by volume is vapor at 100°F. In
      opsNoWater.TVfractionFlash(0.8);
      VPCR4_no_water = fluid1.getPressure();
      RVP_ASTM_D323_73_79 = VPCR4_no_water;
    } catch (Exception ex) {
      logger.debug("RVP calculation without water failed: {}", ex.getMessage());
      VPCR4_no_water = Double.NaN;
      RVP_ASTM_D323_73_79 = Double.NaN;
    }
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
      neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(RVPlocal, "bara");
      return presConversion.getValue(returnUnit);
    }
    if ("TVP".equals(returnParameter)) {
      neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(getValue("TVP"), "bara");
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
   * @param refTemp     a double
   * @param refTempUnit a {@link java.lang.String} object
   */
  public void setReferenceTemperature(double refTemp, String refTempUnit) {
    neqsim.util.unit.TemperatureUnit tempConversion = new neqsim.util.unit.TemperatureUnit(refTemp, refTempUnit);
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
