package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.steam.Iapws_if97;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.exception.IsNaNException;

/**
 * EOS screening of VPCR4, correlated RVP-equivalent values and bubble-point TVP.
 *
 * <p>
 * The default is raw {@code VPCR4}: vapor/liquid volume ratio 4:1, not 80% molar vapor. The D6377-labelled correlation
 * is {@code 0.834 * VPCR4}; D323-82 is {@code 0.752 * VPCR4 + 0.0607} in bara. {@code VPCR4_no_water} and the
 * historical D323-73/79 label both return raw dry VPCR4. These model labels do not certify laboratory equivalence or
 * compliance; {@link #isOnSpec()} always returns true.
 *
 * <p>
 * {@link #calculate()} mutates the supplied fluid state. A clone protects temperature, pressure and composition during
 * this workflow, but BIP arrays can be shared across clones: do not mutate model parameters through a clone. The
 * water-free variants remove water only when it is the last component and otherwise keep it as a 1e-15 trace, because
 * component removal does not remap BIPs when retained component indices shift. A finite positive {@link RvpResult}
 * alone does not certify convergence to the corrected volume-ratio target, except for
 * {@link RvpMethod#RVP_ASTM_D6377_ADD_WATER_SATURATED}, which checks it.
 *
 * <p>
 * For a declared water-contact storage basis, {@link RvpMethod#RVP_ASTM_D6377_ADD_WATER_SATURATED} returns
 * {@code RVPE_water_sat = 0.834 * (VPCR4_dry + psat_water(37.8 C))}, always at 37.8 C, not dry RVPE plus the full
 * water pressure. The EOS only sees the water-free hydrocarbon liquid; the water term is the IAPWS-IF97 Region 4
 * pure-water saturation pressure and is always added, whether or not the fluid contains water. This assumes water
 * activity near one and sufficient water for saturation; it neither models the ASTM apparatus nor repairs sampling
 * losses. The analogous TVP approximation is {@code TVP_dry(T) + psat_water(T)}. See the
 * <a href="https://equinor.github.io/neqsim/standards/astm_d6377_rvp.html">vapor-pressure water-basis guide</a>.
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
  private double RVP_ASTM_D6377_add_water_saturated = 0.0;

  /** Reference temperature at which RVP and RVPE are defined (100 F) [C]. */
  private static final double RVP_REFERENCE_TEMPERATURE_C = 37.8;

  /**
   * Water mole fraction used instead of removal when water is not the last component [-]. See
   * {@link #createWaterFreeFluid(SystemInterface)}.
   */
  private static final double TRACE_WATER_MOLE_FRACTION = 1.0e-15;

  /** True once {@link RvpMethod#RVP_ASTM_D6377_ADD_WATER_SATURATED} has been evaluated for the current state. */
  private boolean addWaterSaturatedCalculated = false;

  /** D6377-labelled correlation factor from VPCR4 to RVPE [-]. */
  private static final double RVPE_D6377_FACTOR = 0.834;

  /** Lower temperature limit of the IAPWS-IF97 Region 4 saturation equation [K]. */
  private static final double IF97_SATURATION_MIN_TEMPERATURE_K = 273.15;

  /** Upper temperature limit (critical point) of the IAPWS-IF97 Region 4 saturation equation [K]. */
  private static final double IF97_SATURATION_MAX_TEMPERATURE_K = 647.096;

  /**
   * Accepted absolute deviation of the corrected gas volume fraction from the 0.8 target (V/L = 4) before a dry VPCR4
   * is used for the water-saturated result [-].
   */
  private static final double VOLUME_FRACTION_TOLERANCE = 1.0e-4;

  /**
   * True once the water-free variants ({@code VPCR4_no_water} and {@code RVP_ASTM_D323_73_79}) have been evaluated for
   * the current {@link #calculate()} result. They require an extra fluid clone and two extra flashes, so they are
   * computed lazily on first request instead of on every {@link #calculate()}.
   */
  private boolean noWaterVariantsCalculated = false;

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
    /** D6377-labelled RVPE correlation: 0.834 times VPCR4, not laboratory certification. */
    RVP_ASTM_D6377("RVP_ASTM_D6377"),
    /** Historical D323-73/79 label: raw dry VPCR4 in this implementation. */
    RVP_ASTM_D323_73_79("RVP_ASTM_D323_73_79"),
    /** D323-82-labelled correlation: 0.752 times VPCR4 plus 0.0607 bara. */
    RVP_ASTM_D323_82("RVP_ASTM_D323_82"),
    /** Raw vapor pressure at vapor/liquid volume ratio 4:1 (default). */
    VPCR4("VPCR4"),
    /** Raw vapor pressure at vapor/liquid volume ratio 4:1 on the water-free fluid (water removed if last, else trace). */
    VPCR4_NO_WATER("VPCR4_no_water"),
    /**
     * D6377-labelled RVPE on an assumed water-saturated basis, always at 37.8 C: 0.834 times the sum of dry VPCR4 and
     * the IAPWS-IF97 pure-water saturation pressure. The water term is added whether or not the fluid contains water.
     * NaN (invalid) when the dry flash does not reach a gas/oil state with vapor/liquid volume ratio 4.
     */
    RVP_ASTM_D6377_ADD_WATER_SATURATED("RVP_ASTM_D6377_add_water_saturated");

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
     * @param value computed RVP value in bara
     * @param method method used to compute the value
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
     * fields
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
   * <li>VPCR4_no_water</li>
   * <li>RVP_ASTM_D6377_add_water_saturated</li>
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
   * <li>VPCR4_no_water</li>
   * <li>RVP_ASTM_D6377_add_water_saturated</li>
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
      calculateNoWaterVariants();
      return RVP_ASTM_D323_73_79;
    case RVP_ASTM_D323_82:
      return RVP_ASTM_D323_82;
    case VPCR4_NO_WATER:
      calculateNoWaterVariants();
      return VPCR4_no_water;
    case RVP_ASTM_D6377_ADD_WATER_SATURATED:
      calculateAddWaterSaturated();
      return RVP_ASTM_D6377_add_water_saturated;
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
   * Call {@link #calculate()} first. Direct correlations reuse that calculation; water-free variants run extra flashes
   * lazily on first access. Dry-basis selection is separate from correlation selection: multiply raw dry VPCR4 by 0.834
   * explicitly for dry D6377-labelled RVPE. Only {@link RvpMethod#RVP_ASTM_D6377_ADD_WATER_SATURATED} adds an
   * independent water-saturation term; it is always evaluated at 37.8 C and reports that temperature.
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
    double resultTemperatureC =
        method == RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED ? RVP_REFERENCE_TEMPERATURE_C : referenceTemperature;
    return new RvpResult(valueForMethod(method), method, resultTemperatureC);
  }

  /**
   * Constructor for Standard_ASTM_D6377.
   *
   * @param thermoSystem characterized fluid whose state will be mutated by calculation; pass a clone to preserve the
   * caller's state, without assuming BIP independence
   */
  public Standard_ASTM_D6377(SystemInterface thermoSystem) {
    super("Standard_ASTM_D6377", "Standard_ASTM_D6377", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    noWaterVariantsCalculated = false;
    VPCR4_no_water = 0.0;
    RVP_ASTM_D323_73_79 = 0.0;
    addWaterSaturatedCalculated = false;
    RVP_ASTM_D6377_add_water_saturated = 0.0;
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
  }

  /**
   * Evaluates the water-free vapor-pressure variants {@code VPCR4_no_water} and {@code RVP_ASTM_D323_73_79}.
   *
   * <p>
   * This requires a water-free clone of the fluid (see {@link #createWaterFreeFluid(SystemInterface)}) plus a
   * bubble-point flash and an 80% vapor-volume flash, which roughly doubles the cost of {@link #calculate()}. Because
   * most callers only read {@code VPCR4} or {@code RVP_ASTM_D6377}, the work is deferred until one of the water-free
   * values is actually requested and is then cached until the next {@link #calculate()} call.
   * </p>
   */
  private void calculateNoWaterVariants() {
    if (noWaterVariantsCalculated) {
      return;
    }
    noWaterVariantsCalculated = true;
    try {
      SystemInterface fluid1 = createWaterFreeFluid(this.thermoSystem);
      VPCR4_no_water = calculateVpcr4(fluid1, referenceTemperature);
      RVP_ASTM_D323_73_79 = VPCR4_no_water;
    } catch (Exception ex) {
      logger.debug("RVP calculation without water failed: {}", ex.getMessage());
      VPCR4_no_water = Double.NaN;
      RVP_ASTM_D323_73_79 = Double.NaN;
    }
  }

  /**
   * Evaluates {@link RvpMethod#RVP_ASTM_D6377_ADD_WATER_SATURATED} at the fixed RVP reference temperature.
   *
   * <p>
   * {@code RVPE = 0.834 * (VPCR4_dry(37.8 C) + psat_water,IF97(37.8 C))} in bara. The EOS only evaluates the water-free
   * hydrocarbon fluid; the water contribution is the IAPWS-IF97 pure-water saturation pressure, added whether or not
   * the fluid contains water (assumed water-saturated storage). The result is NaN when the dry flash fails, does not
   * end with exactly one gas and one oil phase, or misses the corrected gas volume fraction of 0.8 (V/L = 4) by more
   * than {@value #VOLUME_FRACTION_TOLERANCE}. The user-set reference temperature is ignored: RVP is defined at 37.8 C.
   * </p>
   */
  private void calculateAddWaterSaturated() {
    if (addWaterSaturatedCalculated) {
      return;
    }
    addWaterSaturatedCalculated = true;
    RVP_ASTM_D6377_add_water_saturated = Double.NaN;
    try {
      SystemInterface dryFluid = createWaterFreeFluid(this.thermoSystem);
      double vpcr4DryBara = calculateVpcr4(dryFluid, RVP_REFERENCE_TEMPERATURE_C);
      if (!isFiniteVolumeRatioFourResult(dryFluid, vpcr4DryBara)) {
        logger.warn("Dry VPCR4 flash did not reach a two-phase gas/oil state with gas volume fraction 0.8; "
            + "RVP_ASTM_D6377_add_water_saturated is set to NaN");
        return;
      }
      double waterPsatBara = getWaterSaturationPressure(RVP_REFERENCE_TEMPERATURE_C + 273.15);
      RVP_ASTM_D6377_add_water_saturated = RVPE_D6377_FACTOR * (vpcr4DryBara + waterPsatBara);
    } catch (Exception ex) {
      logger.warn("RVP_ASTM_D6377_add_water_saturated calculation failed: {}", ex.getMessage());
    }
  }

  /**
   * Returns a water-free working copy of a fluid for the dry VPCR4 flash.
   *
   * <p>
   * Removing a component compacts the component list without remapping the EOS binary interaction parameter (BIP)
   * matrix. Removing water is therefore only safe when it is the last component, because no retained index shifts.
   * When water sits elsewhere, removal would silently give hydrocarbon pairs the wrong BIPs, so water is kept as a
   * trace of mole fraction {@value #TRACE_WATER_MOLE_FRACTION} instead. At that level the water contribution to the
   * flash is negligible (VPCR4 agrees with the truly water-free fluid to about 13 significant digits) and every BIP
   * stays on its original pair. The working copy is reset to its unflashed state before water is touched. The source
   * fluid is not modified.
   * </p>
   *
   * @param source fluid that may contain water
   * @return a clone with water removed (water last) or reduced to a trace (water not last)
   */
  private static SystemInterface createWaterFreeFluid(SystemInterface source) {
    SystemInterface dryFluid = source.clone();
    if (!dryFluid.hasComponent("water")) {
      return dryFluid;
    }
    // Water removal is a change of the fluid definition, so start from the unflashed state. The source may carry phase
    // bookkeeping from an earlier (possibly unconverged) flash in which two phase slots point at the same phase object;
    // init(0) resets that bookkeeping while keeping components, composition and model parameters.
    dryFluid.init(0);
    int waterIndex = dryFluid.getPhase(0).getComponent("water").getComponentNumber();
    if (waterIndex == dryFluid.getNumberOfComponents() - 1) {
      dryFluid.removeComponent("water");
    } else {
      double[] moleFractions = dryFluid.getMolarComposition();
      moleFractions[waterIndex] = TRACE_WATER_MOLE_FRACTION;
      dryFluid.setMolarComposition(moleFractions);
    }
    return dryFluid;
  }

  /**
   * Calculates VPCR4 (vapor/liquid volume ratio 4:1) of a fluid with the same flash sequence as {@link #calculate()}.
   *
   * @param fluid fluid to flash; its state is modified
   * @param temperatureC flash temperature in degrees Celsius
   * @return VPCR4 in bara
   * @throws Exception if the bubble-point or volume-fraction flash fails
   */
  private static double calculateVpcr4(SystemInterface fluid, double temperatureC) throws Exception {
    fluid.setTemperature(temperatureC, "C");
    fluid.setPressure(ThermodynamicConstantsInterface.referencePressure);
    fluid.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.bubblePointPressureFlash(false);
    fluid.setPressure(fluid.getPressure() * 0.9);
    ops.TVfractionFlash(0.8);
    return fluid.getPressure();
  }

  /**
   * Checks that a VPCR4 flash ended in one gas and one oil phase at a corrected gas volume fraction of 0.8.
   *
   * @param fluid flashed fluid
   * @param vpcr4Bara VPCR4 returned by the flash in bara
   * @return true if the result is finite, positive and meets the V/L = 4 target within tolerance
   */
  private static boolean isFiniteVolumeRatioFourResult(SystemInterface fluid, double vpcr4Bara) {
    if (Double.isNaN(vpcr4Bara) || Double.isInfinite(vpcr4Bara) || vpcr4Bara <= 0.0) {
      return false;
    }
    if (fluid.getNumberOfPhases() != 2 || !fluid.hasPhaseType("gas") || !fluid.hasPhaseType("oil")) {
      return false;
    }
    fluid.initPhysicalProperties("density");
    double gasVolumeFraction = fluid.getPhase("gas").getCorrectedVolume() / fluid.getCorrectedVolume();
    return Math.abs(gasVolumeFraction - 0.8) <= VOLUME_FRACTION_TOLERANCE;
  }

  /**
   * Returns the saturation pressure of pure water from the IAPWS-IF97 Region 4 equation.
   *
   * @param temperatureK temperature in kelvin, between 273.15 K and 647.096 K
   * @return saturation pressure in bara
   * @throws IllegalArgumentException if the temperature is outside the IAPWS-IF97 Region 4 validity range
   */
  public static double getWaterSaturationPressure(double temperatureK) {
    if (!(temperatureK >= IF97_SATURATION_MIN_TEMPERATURE_K && temperatureK <= IF97_SATURATION_MAX_TEMPERATURE_K)) {
      throw new IllegalArgumentException("Temperature " + temperatureK
          + " K is outside the IAPWS-IF97 saturation range 273.15-647.096 K");
    }
    return Iapws_if97.psat_t(temperatureK) * 10.0;
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
        calculateNoWaterVariants();
        return RVP_ASTM_D323_73_79;
      case "VPCR4":
        return VPCR4;
      case "RVP_ASTM_D323_82":
        return RVP_ASTM_D323_82;
      case "VPCR4_no_water":
        calculateNoWaterVariants();
        return VPCR4_no_water;
      case "RVP_ASTM_D6377_add_water_saturated":
      case "RVP_ASTM_D6377_ADD_WATER_SATURATED":
        calculateAddWaterSaturated();
        return RVP_ASTM_D6377_add_water_saturated;
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
   * setReferenceTemperature.
   *
   * @param refTemp a double
   * @param refTempUnit a {@link java.lang.String} object
   */
  public void setReferenceTemperature(double refTemp, String refTempUnit) {
    neqsim.util.unit.TemperatureUnit tempConversion = new neqsim.util.unit.TemperatureUnit(refTemp, refTempUnit);
    referenceTemperature = tempConversion.getValue("C");
  }

  /**
   * main.
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
