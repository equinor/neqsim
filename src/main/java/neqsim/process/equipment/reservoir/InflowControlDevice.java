package neqsim.process.equipment.reservoir;

import java.io.Serializable;

/**
 * Pressure-drop model of an inflow control device installed in a well completion.
 *
 * <p>
 * Four device families are covered. All of them return a pressure drop that increases monotonically with the flow rate,
 * so the inverse problem (flow for a given pressure drop) has one solution.
 * </p>
 * <ul>
 * <li><b>ICD</b> - passive nozzle or orifice: {@code dP = rho_mix v^2 / (2 Cd^2)}; independent of viscosity.</li>
 * <li><b>AICD</b> - autonomous (fluidic) device: {@code dP = a rho_mix^2 / rho_cal (mu_cal / mu_mix)^y q^x} with the
 * constants calibrated on a reference fluid. Low-viscosity water or gas gives a larger pressure drop than the design
 * oil.</li>
 * <li><b>AICV</b> - autonomous inflow control valve: a nozzle whose open area falls when the mixture viscosity falls
 * below the design oil viscosity.</li>
 * <li><b>DAR</b> - density-activated restrictor: a nozzle whose open area falls when the mixture density leaves a
 * window around the design oil density. The window width and residual opening are caller inputs because the
 * characteristic is vendor specific.</li>
 * </ul>
 *
 * <p>
 * Screening model: steady state, one in-situ mixture that is homogeneous at the device, no slip, no hysteresis and no
 * erosion. Constants must be taken from vendor flow-loop tests before the result is used for more than ranking.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class InflowControlDevice implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Pascal per bar. */
  private static final double PA_PER_BAR = 1.0e5;
  /** Seconds per day. */
  private static final double SECONDS_PER_DAY = 86400.0;
  /** Hours per day. */
  private static final double HOURS_PER_DAY = 24.0;

  /** Device family. */
  public enum DeviceType {
    /** Passive nozzle inflow control device. */
    ICD,
    /** Autonomous (fluidic) inflow control device. */
    AICD,
    /** Autonomous inflow control valve closing on low viscosity. */
    AICV,
    /** Density-activated restrictor closing outside a density window. */
    DAR
  }

  /**
   * In-situ fluid mixture entering a device: phase properties and volume fractions.
   *
   * <p>
   * Density is the volume-weighted mean. Viscosity is the volume-weighted mean of the natural logarithm of the phase
   * viscosities, which keeps a small water or gas fraction from being swamped by the oil value.
   * </p>
   */
  public static final class Mixture implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Oil density [kg/m3]. */
    private final double oilDensity;
    /** Water density [kg/m3]. */
    private final double waterDensity;
    /** Gas density [kg/m3]. */
    private final double gasDensity;
    /** Oil viscosity [Pa s]. */
    private final double oilViscosity;
    /** Water viscosity [Pa s]. */
    private final double waterViscosity;
    /** Gas viscosity [Pa s]. */
    private final double gasViscosity;
    /** Water volume fraction of the in-situ flow [-]. */
    private final double waterFraction;
    /** Gas volume fraction of the in-situ flow [-]. */
    private final double gasFraction;

    /**
     * Creates a mixture.
     *
     * @param oilDensity oil density [kg/m3]
     * @param waterDensity water density [kg/m3]
     * @param gasDensity gas density at downhole conditions [kg/m3]
     * @param oilViscosity oil viscosity [Pa s]
     * @param waterViscosity water viscosity [Pa s]
     * @param gasViscosity gas viscosity [Pa s]
     * @param waterFraction water volume fraction of the in-situ flow [-]
     * @param gasFraction gas volume fraction of the in-situ flow [-]
     * @throws IllegalArgumentException if a density or viscosity is not positive, a fraction is negative, or the
     * fractions sum to more than one
     */
    public Mixture(double oilDensity, double waterDensity, double gasDensity, double oilViscosity,
        double waterViscosity, double gasViscosity, double waterFraction, double gasFraction) {
      if (oilDensity <= 0.0 || waterDensity <= 0.0 || gasDensity <= 0.0 || oilViscosity <= 0.0 || waterViscosity <= 0.0
          || gasViscosity <= 0.0) {
        throw new IllegalArgumentException("densities and viscosities must be positive");
      }
      if (waterFraction < 0.0 || gasFraction < 0.0 || waterFraction + gasFraction > 1.0 + 1.0e-12) {
        throw new IllegalArgumentException("fractions must be non-negative and sum to at most one");
      }
      this.oilDensity = oilDensity;
      this.waterDensity = waterDensity;
      this.gasDensity = gasDensity;
      this.oilViscosity = oilViscosity;
      this.waterViscosity = waterViscosity;
      this.gasViscosity = gasViscosity;
      this.waterFraction = waterFraction;
      this.gasFraction = gasFraction;
    }

    /**
     * Returns a copy of this mixture with other phase fractions.
     *
     * @param newWaterFraction water volume fraction [-]
     * @param newGasFraction gas volume fraction [-]
     * @return new mixture with the same phase properties
     */
    public Mixture withFractions(double newWaterFraction, double newGasFraction) {
      return new Mixture(oilDensity, waterDensity, gasDensity, oilViscosity, waterViscosity, gasViscosity,
          newWaterFraction, newGasFraction);
    }

    /**
     * Oil volume fraction.
     *
     * @return oil volume fraction [-]
     */
    public double getOilFraction() {
      return Math.max(0.0, 1.0 - waterFraction - gasFraction);
    }

    /**
     * Water volume fraction.
     *
     * @return water volume fraction [-]
     */
    public double getWaterFraction() {
      return waterFraction;
    }

    /**
     * Gas volume fraction.
     *
     * @return gas volume fraction [-]
     */
    public double getGasFraction() {
      return gasFraction;
    }

    /**
     * Oil density.
     *
     * @return oil density [kg/m3]
     */
    public double getOilDensity() {
      return oilDensity;
    }

    /**
     * Oil viscosity.
     *
     * @return oil viscosity [Pa s]
     */
    public double getOilViscosity() {
      return oilViscosity;
    }

    /**
     * Volume-weighted mixture density.
     *
     * @return mixture density [kg/m3]
     */
    public double getDensity() {
      return getOilFraction() * oilDensity + waterFraction * waterDensity + gasFraction * gasDensity;
    }

    /**
     * Log-mean mixture viscosity.
     *
     * @return mixture viscosity [Pa s]
     */
    public double getViscosity() {
      double ln = getOilFraction() * Math.log(oilViscosity) + waterFraction * Math.log(waterViscosity)
          + gasFraction * Math.log(gasViscosity);
      return Math.exp(ln);
    }
  }

  /** Device name. */
  private final String name;
  /** Device family. */
  private final DeviceType type;
  /** Number of devices in parallel (joints or valves). */
  private final int numberOfDevices;

  /** Nozzle diameter [m] (ICD, AICV, DAR). */
  private double nozzleDiameterM = 0.004;
  /** Nozzles per device (ICD, AICV, DAR). */
  private int nozzlesPerDevice = 1;
  /** Discharge coefficient [-] (ICD, AICV, DAR). */
  private double dischargeCoefficient = 0.8;

  /** AICD constant [bar / (kg/m3 * (m3/h)^x)]. */
  private double aicdConstant = 0.0;
  /** AICD flow exponent [-]. */
  private double aicdFlowExponent = 2.0;
  /** AICD viscosity exponent [-]. */
  private double aicdViscosityExponent = 0.3;
  /** AICD calibration density [kg/m3]. */
  private double calibrationDensity = 850.0;
  /** AICD calibration viscosity [Pa s]. */
  private double calibrationViscosity = 0.005;

  /** Open fraction of a closed AICV or DAR [-]. */
  private double minOpenFraction = 0.05;
  /** AICV viscosity ratio (mixture over design oil) at half-open [-]. */
  private double switchViscosityRatio = 0.4;
  /** AICV switching steepness [-]. */
  private double switchSteepness = 4.0;
  /** DAR density window half-width as a fraction of the design density [-]. */
  private double densityWindow = 0.1;
  /** Design oil density for AICV and DAR [kg/m3]. */
  private double designOilDensity = 850.0;
  /** Design oil viscosity for AICV and DAR [Pa s]. */
  private double designOilViscosity = 0.005;

  /**
   * Creates a device of a given family with default constants.
   *
   * @param name device name
   * @param type device family
   * @param numberOfDevices number of identical devices in parallel
   * @throws IllegalArgumentException if the number of devices is not positive
   */
  public InflowControlDevice(String name, DeviceType type, int numberOfDevices) {
    if (numberOfDevices <= 0) {
      throw new IllegalArgumentException("number of devices must be positive");
    }
    this.name = name;
    this.type = type;
    this.numberOfDevices = numberOfDevices;
  }

  /**
   * Creates a passive nozzle ICD.
   *
   * @param name device name
   * @param numberOfDevices number of devices in parallel
   * @param nozzleDiameterMm nozzle diameter [mm]
   * @param nozzlesPerDevice nozzles per device
   * @param dischargeCoefficient discharge coefficient [-]
   * @return configured device
   */
  public static InflowControlDevice icd(String name, int numberOfDevices, double nozzleDiameterMm, int nozzlesPerDevice,
      double dischargeCoefficient) {
    InflowControlDevice d = new InflowControlDevice(name, DeviceType.ICD, numberOfDevices);
    d.setNozzle(nozzleDiameterMm, nozzlesPerDevice, dischargeCoefficient);
    return d;
  }

  /**
   * Creates an AICD calibrated on a reference fluid and flow rate.
   *
   * @param name device name
   * @param numberOfDevices number of devices in parallel
   * @param referenceFlowM3hPerDevice reference flow of one device [m3/h]
   * @param referenceDpBar pressure drop at the reference flow with the reference fluid [bar]
   * @param flowExponent flow exponent x [-]
   * @param viscosityExponent viscosity exponent y [-]
   * @param calibrationDensity reference fluid density [kg/m3]
   * @param calibrationViscosity reference fluid viscosity [Pa s]
   * @return configured device
   */
  public static InflowControlDevice aicd(String name, int numberOfDevices, double referenceFlowM3hPerDevice,
      double referenceDpBar, double flowExponent, double viscosityExponent, double calibrationDensity,
      double calibrationViscosity) {
    InflowControlDevice d = new InflowControlDevice(name, DeviceType.AICD, numberOfDevices);
    d.calibrateAicd(referenceFlowM3hPerDevice, referenceDpBar, flowExponent, viscosityExponent, calibrationDensity,
        calibrationViscosity);
    return d;
  }

  /**
   * Creates an AICV that closes when the mixture viscosity falls below the design oil viscosity.
   *
   * @param name device name
   * @param numberOfDevices number of devices in parallel
   * @param nozzleDiameterMm open nozzle diameter [mm]
   * @param dischargeCoefficient discharge coefficient [-]
   * @param designOilViscosity design oil viscosity [Pa s]
   * @param switchViscosityRatio mixture over design viscosity at half-open [-]
   * @param minOpenFraction open fraction of the closed valve [-]
   * @return configured device
   */
  public static InflowControlDevice aicv(String name, int numberOfDevices, double nozzleDiameterMm,
      double dischargeCoefficient, double designOilViscosity, double switchViscosityRatio, double minOpenFraction) {
    InflowControlDevice d = new InflowControlDevice(name, DeviceType.AICV, numberOfDevices);
    d.setNozzle(nozzleDiameterMm, 1, dischargeCoefficient);
    d.setDesignOilViscosity(designOilViscosity);
    d.setSwitchViscosityRatio(switchViscosityRatio);
    d.setMinOpenFraction(minOpenFraction);
    return d;
  }

  /**
   * Creates a density-activated restrictor.
   *
   * @param name device name
   * @param numberOfDevices number of devices in parallel
   * @param nozzleDiameterMm open nozzle diameter [mm]
   * @param dischargeCoefficient discharge coefficient [-]
   * @param designOilDensity design oil density [kg/m3]
   * @param densityWindow half-width of the open density window as a fraction of the design density [-]
   * @param minOpenFraction open fraction outside the window [-]
   * @return configured device
   */
  public static InflowControlDevice dar(String name, int numberOfDevices, double nozzleDiameterMm,
      double dischargeCoefficient, double designOilDensity, double densityWindow, double minOpenFraction) {
    InflowControlDevice d = new InflowControlDevice(name, DeviceType.DAR, numberOfDevices);
    d.setNozzle(nozzleDiameterMm, 1, dischargeCoefficient);
    d.setDesignOilDensity(designOilDensity);
    d.setDensityWindow(densityWindow);
    d.setMinOpenFraction(minOpenFraction);
    return d;
  }

  /**
   * Sets the nozzle geometry used by ICD, AICV and DAR.
   *
   * @param nozzleDiameterMm nozzle diameter [mm]
   * @param nozzlesPerDevice nozzles per device
   * @param dischargeCoefficient discharge coefficient [-]
   * @throws IllegalArgumentException if a value is not positive
   */
  public void setNozzle(double nozzleDiameterMm, int nozzlesPerDevice, double dischargeCoefficient) {
    if (nozzleDiameterMm <= 0.0 || nozzlesPerDevice <= 0 || dischargeCoefficient <= 0.0) {
      throw new IllegalArgumentException("nozzle diameter, count and discharge coefficient must be positive");
    }
    this.nozzleDiameterM = nozzleDiameterMm / 1000.0;
    this.nozzlesPerDevice = nozzlesPerDevice;
    this.dischargeCoefficient = dischargeCoefficient;
  }

  /**
   * Calibrates the AICD constants so that the reference fluid gives the reference pressure drop at the reference flow.
   *
   * @param referenceFlowM3hPerDevice reference flow of one device [m3/h]
   * @param referenceDpBar pressure drop at the reference flow with the reference fluid [bar]
   * @param flowExponent flow exponent x [-]
   * @param viscosityExponent viscosity exponent y [-]
   * @param calibrationDensity reference fluid density [kg/m3]
   * @param calibrationViscosity reference fluid viscosity [Pa s]
   * @throws IllegalArgumentException if a value is not positive
   */
  public void calibrateAicd(double referenceFlowM3hPerDevice, double referenceDpBar, double flowExponent,
      double viscosityExponent, double calibrationDensity, double calibrationViscosity) {
    if (referenceFlowM3hPerDevice <= 0.0 || referenceDpBar <= 0.0 || flowExponent <= 0.0 || calibrationDensity <= 0.0
        || calibrationViscosity <= 0.0 || viscosityExponent < 0.0) {
      throw new IllegalArgumentException("calibration values must be positive");
    }
    this.aicdFlowExponent = flowExponent;
    this.aicdViscosityExponent = viscosityExponent;
    this.calibrationDensity = calibrationDensity;
    this.calibrationViscosity = calibrationViscosity;
    this.aicdConstant = referenceDpBar / (calibrationDensity * Math.pow(referenceFlowM3hPerDevice, flowExponent));
  }

  /**
   * Sets the design oil viscosity used by an AICV.
   *
   * @param viscosity design oil viscosity [Pa s]
   */
  public void setDesignOilViscosity(double viscosity) {
    this.designOilViscosity = viscosity;
  }

  /**
   * Sets the design oil density used by a DAR.
   *
   * @param density design oil density [kg/m3]
   */
  public void setDesignOilDensity(double density) {
    this.designOilDensity = density;
  }

  /**
   * Sets the AICV viscosity ratio at which the valve is half open.
   *
   * @param ratio mixture over design viscosity at half-open [-]
   */
  public void setSwitchViscosityRatio(double ratio) {
    this.switchViscosityRatio = ratio;
  }

  /**
   * Sets the AICV switching steepness.
   *
   * @param steepness exponent of the closing curve [-]; larger values switch more sharply
   */
  public void setSwitchSteepness(double steepness) {
    this.switchSteepness = steepness;
  }

  /**
   * Sets the DAR density window.
   *
   * @param window half-width of the open window as a fraction of the design density [-]
   */
  public void setDensityWindow(double window) {
    this.densityWindow = window;
  }

  /**
   * Sets the open fraction of a closed AICV or DAR.
   *
   * @param fraction residual open fraction [-], between 0 (exclusive) and 1
   * @throws IllegalArgumentException if the fraction is outside (0, 1]
   */
  public void setMinOpenFraction(double fraction) {
    if (fraction <= 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("minimum open fraction must be in (0, 1]");
    }
    this.minOpenFraction = fraction;
  }

  /**
   * Device name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Device family.
   *
   * @return device type
   */
  public DeviceType getType() {
    return type;
  }

  /**
   * Number of devices in parallel.
   *
   * @return number of devices
   */
  public int getNumberOfDevices() {
    return numberOfDevices;
  }

  /**
   * Open fraction of the flow area for the given mixture.
   *
   * <p>
   * ICD and AICD are always fully open (1). An AICV returns a logistic function of the viscosity ratio; a DAR returns a
   * Gaussian window in the density ratio. Both are bounded below by the minimum open fraction.
   * </p>
   *
   * @param mixture in-situ mixture
   * @return open fraction between the minimum open fraction and 1
   */
  public double openFraction(Mixture mixture) {
    if (type == DeviceType.AICV) {
      double ratio = Math.max(1.0e-12, mixture.getViscosity() / designOilViscosity);
      double act = 1.0 / (1.0 + Math.pow(switchViscosityRatio / ratio, switchSteepness));
      return minOpenFraction + (1.0 - minOpenFraction) * act;
    }
    if (type == DeviceType.DAR) {
      double dev = (mixture.getDensity() / designOilDensity - 1.0) / densityWindow;
      return minOpenFraction + (1.0 - minOpenFraction) * Math.exp(-dev * dev);
    }
    return 1.0;
  }

  /**
   * Pressure drop over the device group for a total in-situ flow.
   *
   * @param flowM3PerDay total in-situ flow through all devices [m3/d]
   * @param mixture in-situ mixture
   * @return pressure drop [bar], zero for non-positive flow
   */
  public double pressureDropBar(double flowM3PerDay, Mixture mixture) {
    if (flowM3PerDay <= 0.0) {
      return 0.0;
    }
    double perDeviceM3h = flowM3PerDay / HOURS_PER_DAY / numberOfDevices;
    if (type == DeviceType.AICD) {
      double rho = mixture.getDensity();
      double mu = mixture.getViscosity();
      return aicdConstant * rho * rho / calibrationDensity * Math.pow(calibrationViscosity / mu, aicdViscosityExponent)
          * Math.pow(perDeviceM3h, aicdFlowExponent);
    }
    double area = nozzlesPerDevice * Math.PI * nozzleDiameterM * nozzleDiameterM / 4.0 * openFraction(mixture);
    double velocity = flowM3PerDay / SECONDS_PER_DAY / numberOfDevices / area;
    return mixture.getDensity() * velocity * velocity / (2.0 * dischargeCoefficient * dischargeCoefficient)
        / PA_PER_BAR;
  }

  /**
   * Total in-situ flow through the device group at a given pressure drop.
   *
   * @param deltaPBar pressure drop over the devices [bar]
   * @param mixture in-situ mixture
   * @return total in-situ flow [m3/d], zero for non-positive pressure drop
   */
  public double flowRateM3PerDay(double deltaPBar, Mixture mixture) {
    if (deltaPBar <= 0.0) {
      return 0.0;
    }
    double hi = 1.0;
    int guard = 0;
    while (pressureDropBar(hi, mixture) < deltaPBar && guard < 200) {
      hi *= 2.0;
      guard++;
    }
    double lo = 0.0;
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (lo + hi);
      if (pressureDropBar(mid, mixture) < deltaPBar) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }
}
