package neqsim.process.mechanicaldesign.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * NORSOK P-002 screening validator for process line sizing.
 *
 * <p>
 * The validator checks a simulated pipeline against configurable screening limits for velocity,
 * pressure gradient, and erosional velocity. It intentionally uses {@link PipeLineInterface}, so it
 * works with the empirical and mechanistic pipe models already available in NeqSim.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class NorsokP002LineSizingValidator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Default maximum gas velocity in m/s. */
  private double maximumGasVelocityMPerS = 35.0;
  /** Default minimum liquid velocity in m/s. */
  private double minimumLiquidVelocityMPerS = 0.7;
  /** Default maximum liquid velocity in m/s. */
  private double maximumLiquidVelocityMPerS = 5.0;
  /** Default maximum pressure gradient in Pa/m. */
  private double maximumPressureGradientPaPerM = 500.0;
  /** Erosional velocity constant in SI units. */
  private double erosionalVelocityConstant = 122.0;
  /** Maximum allowed fraction of erosional velocity. */
  private double maximumErosionalVelocityFraction = 0.80;

  /** Flow service categories for line-sizing checks. */
  public enum ServiceType {
    /** Gas-dominated line. */
    GAS,
    /** Liquid-dominated line. */
    LIQUID,
    /** Mixed gas-liquid line. */
    TWO_PHASE,
    /** Service could not be determined from the fluid state. */
    UNKNOWN
  }

  /**
   * Sets the maximum gas velocity limit.
   *
   * @param velocityMPerS maximum gas velocity in m/s
   * @return this validator for chaining
   */
  public NorsokP002LineSizingValidator setMaximumGasVelocityMPerS(double velocityMPerS) {
    this.maximumGasVelocityMPerS = velocityMPerS;
    return this;
  }

  /**
   * Sets the minimum liquid velocity limit.
   *
   * @param velocityMPerS minimum liquid velocity in m/s
   * @return this validator for chaining
   */
  public NorsokP002LineSizingValidator setMinimumLiquidVelocityMPerS(double velocityMPerS) {
    this.minimumLiquidVelocityMPerS = velocityMPerS;
    return this;
  }

  /**
   * Sets the maximum liquid velocity limit.
   *
   * @param velocityMPerS maximum liquid velocity in m/s
   * @return this validator for chaining
   */
  public NorsokP002LineSizingValidator setMaximumLiquidVelocityMPerS(double velocityMPerS) {
    this.maximumLiquidVelocityMPerS = velocityMPerS;
    return this;
  }

  /**
   * Sets the maximum pressure gradient limit.
   *
   * @param pressureGradientPaPerM maximum pressure gradient in Pa/m
   * @return this validator for chaining
   */
  public NorsokP002LineSizingValidator setMaximumPressureGradientPaPerM(
      double pressureGradientPaPerM) {
    this.maximumPressureGradientPaPerM = pressureGradientPaPerM;
    return this;
  }

  /**
   * Sets the SI erosional velocity constant used for screening.
   *
   * @param constant erosional velocity constant in SI units
   * @return this validator for chaining
   */
  public NorsokP002LineSizingValidator setErosionalVelocityConstant(double constant) {
    this.erosionalVelocityConstant = constant;
    return this;
  }

  /**
   * Sets the maximum fraction of erosional velocity allowed.
   *
   * @param fraction maximum fraction of erosional velocity; 0.80 means 80 percent
   * @return this validator for chaining
   */
  public NorsokP002LineSizingValidator setMaximumErosionalVelocityFraction(double fraction) {
    this.maximumErosionalVelocityFraction = fraction;
    return this;
  }

  /**
   * Validates a pipeline against NORSOK P-002 screening limits.
   *
   * @param pipe pipeline model that has been run or configured with inlet stream and geometry
   * @return line-sizing result
   * @throws IllegalArgumentException if {@code pipe} is null
   */
  public LineSizingResult validate(PipeLineInterface pipe) {
    if (pipe == null) {
      throw new IllegalArgumentException("pipe must not be null");
    }
    SystemInterface fluid = pipe.getInletStream() == null ? null : pipe.getInletStream().getFluid();
    ServiceType serviceType = detectService(fluid);
    double velocityMPerS = getVelocity(pipe, fluid);
    double densityKgPerM3 = getMixtureDensity(fluid);
    double pressureGradientPaPerM = getPressureGradientPaPerM(pipe);
    double erosionalVelocityMPerS = densityKgPerM3 > 0.0
        ? erosionalVelocityConstant / Math.sqrt(densityKgPerM3) : Double.NaN;
    double allowedErosionalVelocityMPerS = erosionalVelocityMPerS * maximumErosionalVelocityFraction;

    double lowerVelocityLimit = getLowerVelocityLimit(serviceType);
    double upperVelocityLimit = getUpperVelocityLimit(serviceType, allowedErosionalVelocityMPerS);
    boolean velocityAboveMinimum = !Double.isFinite(lowerVelocityLimit)
        || velocityMPerS >= lowerVelocityLimit;
    boolean velocityBelowMaximum = !Double.isFinite(upperVelocityLimit)
        || velocityMPerS <= upperVelocityLimit;
    boolean pressureGradientOk = !Double.isFinite(pressureGradientPaPerM)
        || pressureGradientPaPerM <= maximumPressureGradientPaPerM;
    boolean erosionalVelocityOk = !Double.isFinite(allowedErosionalVelocityMPerS)
        || velocityMPerS <= allowedErosionalVelocityMPerS;

    return new LineSizingResult(serviceType, velocityMPerS, lowerVelocityLimit,
        upperVelocityLimit, pressureGradientPaPerM, maximumPressureGradientPaPerM,
        densityKgPerM3, erosionalVelocityMPerS, allowedErosionalVelocityMPerS,
        velocityAboveMinimum, velocityBelowMaximum, pressureGradientOk, erosionalVelocityOk);
  }

  /**
   * Detects the line service from the fluid phase state.
   *
   * @param fluid thermodynamic fluid
   * @return detected service type
   */
  private ServiceType detectService(SystemInterface fluid) {
    if (fluid == null) {
      return ServiceType.UNKNOWN;
    }
    try {
      boolean hasGas = fluid.hasPhaseType("gas");
      boolean hasOil = fluid.hasPhaseType("oil");
      boolean hasAqueous = fluid.hasPhaseType("aqueous");
      boolean hasLiquid = hasOil || hasAqueous;
      if (hasGas && hasLiquid) {
        return ServiceType.TWO_PHASE;
      }
      if (hasGas) {
        return ServiceType.GAS;
      }
      if (hasLiquid) {
        return ServiceType.LIQUID;
      }
    } catch (RuntimeException ex) {
      return ServiceType.UNKNOWN;
    }
    return ServiceType.UNKNOWN;
  }

  /**
   * Gets the representative pipe velocity.
   *
   * @param pipe pipeline model
   * @param fluid inlet fluid
   * @return velocity in m/s, or NaN when unavailable
   */
  private double getVelocity(PipeLineInterface pipe, SystemInterface fluid) {
    double velocity = pipe.getVelocity();
    if (Double.isFinite(velocity) && velocity > 0.0) {
      return velocity;
    }
    if (fluid == null || pipe.getDiameter() <= 0.0) {
      return Double.NaN;
    }
    double areaM2 = Math.PI * Math.pow(pipe.getDiameter() / 2.0, 2.0);
    double volumetricFlowM3PerS = getMixtureVolumetricFlowRate(fluid);
    return areaM2 > 0.0 ? volumetricFlowM3PerS / areaM2 : Double.NaN;
  }

  /**
   * Gets the pressure gradient across the pipe.
   *
   * @param pipe pipeline model
   * @return pressure gradient in Pa/m, or NaN when unavailable
   */
  private double getPressureGradientPaPerM(PipeLineInterface pipe) {
    if (pipe.getLength() <= 0.0) {
      return Double.NaN;
    }
    return Math.abs(pipe.getPressureDrop()) * 1.0e5 / pipe.getLength();
  }

  /**
   * Gets mixture density from the fluid state.
   *
   * @param fluid thermodynamic fluid
   * @return density in kg/m3, or NaN when unavailable
   */
  private double getMixtureDensity(SystemInterface fluid) {
    if (fluid == null) {
      return Double.NaN;
    }
    try {
      double massFlowKgPerS = fluid.getFlowRate("kg/sec");
      double volumetricFlowM3PerS = getMixtureVolumetricFlowRate(fluid);
      if (volumetricFlowM3PerS > 0.0) {
        return massFlowKgPerS / volumetricFlowM3PerS;
      }
      return fluid.getDensity("kg/m3");
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Gets the summed volumetric flow rate for all phases.
   *
   * @param fluid thermodynamic fluid
   * @return volumetric flow rate in m3/s
   */
  private double getMixtureVolumetricFlowRate(SystemInterface fluid) {
    try {
      double volumetricFlowM3PerS = 0.0;
      for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
        volumetricFlowM3PerS += fluid.getPhase(phaseIndex).getFlowRate("m3/sec");
      }
      if (volumetricFlowM3PerS > 0.0) {
        return volumetricFlowM3PerS;
      }
      return fluid.getFlowRate("m3/sec");
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Gets the lower velocity limit for the service.
   *
   * @param serviceType detected service type
   * @return lower velocity limit in m/s, or NaN when not applicable
   */
  private double getLowerVelocityLimit(ServiceType serviceType) {
    if (serviceType == ServiceType.LIQUID) {
      return minimumLiquidVelocityMPerS;
    }
    return Double.NaN;
  }

  /**
   * Gets the upper velocity limit for the service.
   *
   * @param serviceType detected service type
   * @param allowedErosionalVelocityMPerS allowed erosional velocity in m/s
   * @return upper velocity limit in m/s, or NaN when not applicable
   */
  private double getUpperVelocityLimit(ServiceType serviceType, double allowedErosionalVelocityMPerS) {
    if (serviceType == ServiceType.GAS) {
      return maximumGasVelocityMPerS;
    }
    if (serviceType == ServiceType.LIQUID) {
      return maximumLiquidVelocityMPerS;
    }
    if (serviceType == ServiceType.TWO_PHASE) {
      return allowedErosionalVelocityMPerS;
    }
    return Double.NaN;
  }

  /** Result from validating one line against NORSOK P-002 screening limits. */
  public static class LineSizingResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final ServiceType serviceType;
    private final double velocityMPerS;
    private final double lowerVelocityLimitMPerS;
    private final double upperVelocityLimitMPerS;
    private final double pressureGradientPaPerM;
    private final double pressureGradientLimitPaPerM;
    private final double densityKgPerM3;
    private final double erosionalVelocityMPerS;
    private final double allowedErosionalVelocityMPerS;
    private final boolean velocityAboveMinimum;
    private final boolean velocityBelowMaximum;
    private final boolean pressureGradientOk;
    private final boolean erosionalVelocityOk;

    /**
     * Creates a line-sizing result.
     *
     * @param serviceType detected service type
     * @param velocityMPerS velocity in m/s
     * @param lowerVelocityLimitMPerS lower velocity limit in m/s
     * @param upperVelocityLimitMPerS upper velocity limit in m/s
     * @param pressureGradientPaPerM pressure gradient in Pa/m
     * @param pressureGradientLimitPaPerM pressure gradient limit in Pa/m
     * @param densityKgPerM3 mixture density in kg/m3
     * @param erosionalVelocityMPerS erosional velocity in m/s
     * @param allowedErosionalVelocityMPerS allowed erosional velocity in m/s
     * @param velocityAboveMinimum true when velocity is above the minimum limit
     * @param velocityBelowMaximum true when velocity is below the maximum limit
     * @param pressureGradientOk true when pressure gradient is acceptable
     * @param erosionalVelocityOk true when erosional velocity limit is acceptable
     */
    public LineSizingResult(ServiceType serviceType, double velocityMPerS,
        double lowerVelocityLimitMPerS, double upperVelocityLimitMPerS,
        double pressureGradientPaPerM, double pressureGradientLimitPaPerM, double densityKgPerM3,
        double erosionalVelocityMPerS, double allowedErosionalVelocityMPerS,
        boolean velocityAboveMinimum, boolean velocityBelowMaximum, boolean pressureGradientOk,
        boolean erosionalVelocityOk) {
      this.serviceType = serviceType;
      this.velocityMPerS = velocityMPerS;
      this.lowerVelocityLimitMPerS = lowerVelocityLimitMPerS;
      this.upperVelocityLimitMPerS = upperVelocityLimitMPerS;
      this.pressureGradientPaPerM = pressureGradientPaPerM;
      this.pressureGradientLimitPaPerM = pressureGradientLimitPaPerM;
      this.densityKgPerM3 = densityKgPerM3;
      this.erosionalVelocityMPerS = erosionalVelocityMPerS;
      this.allowedErosionalVelocityMPerS = allowedErosionalVelocityMPerS;
      this.velocityAboveMinimum = velocityAboveMinimum;
      this.velocityBelowMaximum = velocityBelowMaximum;
      this.pressureGradientOk = pressureGradientOk;
      this.erosionalVelocityOk = erosionalVelocityOk;
    }

    /**
     * Gets detected service type.
     *
     * @return detected service type
     */
    public ServiceType getServiceType() {
      return serviceType;
    }

    /**
     * Gets representative velocity.
     *
     * @return velocity in m/s
     */
    public double getVelocityMPerS() {
      return velocityMPerS;
    }

    /**
     * Gets pressure gradient.
     *
     * @return pressure gradient in Pa/m
     */
    public double getPressureGradientPaPerM() {
      return pressureGradientPaPerM;
    }

    /**
     * Gets allowed erosional velocity.
     *
     * @return allowed erosional velocity in m/s
     */
    public double getAllowedErosionalVelocityMPerS() {
      return allowedErosionalVelocityMPerS;
    }

    /**
     * Checks whether all configured limits are met.
     *
     * @return true if the line passes the screening checks
     */
    public boolean isAcceptable() {
      return velocityAboveMinimum && velocityBelowMaximum && pressureGradientOk
          && erosionalVelocityOk;
    }

    /**
     * Converts the result to an ordered map.
     *
     * @return ordered map for reports and JSON serialization
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("standard", "NORSOK P-002");
      map.put("serviceType", serviceType.name());
      map.put("velocityMPerS", velocityMPerS);
      map.put("lowerVelocityLimitMPerS", lowerVelocityLimitMPerS);
      map.put("upperVelocityLimitMPerS", upperVelocityLimitMPerS);
      map.put("pressureGradientPaPerM", pressureGradientPaPerM);
      map.put("pressureGradientLimitPaPerM", pressureGradientLimitPaPerM);
      map.put("densityKgPerM3", densityKgPerM3);
      map.put("erosionalVelocityMPerS", erosionalVelocityMPerS);
      map.put("allowedErosionalVelocityMPerS", allowedErosionalVelocityMPerS);
      map.put("velocityAboveMinimum", velocityAboveMinimum);
      map.put("velocityBelowMaximum", velocityBelowMaximum);
      map.put("pressureGradientOk", pressureGradientOk);
      map.put("erosionalVelocityOk", erosionalVelocityOk);
      map.put("acceptable", isAcceptable());
      return map;
    }

    /**
     * Converts the result to pretty-printed JSON.
     *
     * @return JSON representation of the result
     */
    public String toJson() {
      Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting()
          .create();
      return gson.toJson(toMap());
    }
  }
}