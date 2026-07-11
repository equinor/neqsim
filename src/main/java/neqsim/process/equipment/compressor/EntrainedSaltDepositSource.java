package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * {@link DepositSource} for salt precipitation from entrained produced water carried into a compressor.
 *
 * <p>
 * Dissolved salt (for example NaCl) is essentially non-volatile: when entrained brine droplets enter the hot compressed
 * gas and the water evaporates, the salt it carried precipitates as a solid that can foul the impeller. The deposition
 * rate is therefore a mass balance:
 * </p>
 *
 * <pre>
 * salt precipitation rate = entrainedWaterRate * salinity * evaporatedFraction
 * deposition rate         = salt precipitation rate * captureFraction
 * </pre>
 *
 * <p>
 * By default the whole entrained water load is assumed to evaporate ({@code evaporatedFraction =
 * 1.0}), which is the conservative worst case for salt fouling. Use
 * {@link #estimateWaterEvaporatedFraction(StreamInterface, double, double)} to compute the evaporated fraction from a
 * flash of the gas stream between the compressor inlet and discharge conditions and set it with
 * {@link #setEvaporatedFraction(double)}.
 * </p>
 *
 * <p>
 * This mass-balance approach avoids solid-salt electrolyte convergence issues while still tying the deposit amount to
 * the process (entrained water rate, salinity, and how much water flashes off).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EntrainedSaltDepositSource implements DepositSource {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(EntrainedSaltDepositSource.class);

  private double entrainedWaterRateKgHr;
  private double salinityMassFraction;
  private double evaporatedFraction = 1.0;
  private double captureFraction = 1.0;
  private final DepositMechanism mechanism;

  /**
   * Constructor (defaults to NaCl salt, full evaporation, full capture).
   *
   * @param entrainedWaterRateKgHr entrained liquid water carry-over rate in kg/hr
   * @param salinityMassFraction dissolved salt mass fraction of the brine (kg salt / kg water)
   */
  public EntrainedSaltDepositSource(double entrainedWaterRateKgHr, double salinityMassFraction) {
    this(entrainedWaterRateKgHr, salinityMassFraction, DepositMechanism.SALT_NACL);
  }

  /**
   * Constructor.
   *
   * @param entrainedWaterRateKgHr entrained liquid water carry-over rate in kg/hr
   * @param salinityMassFraction dissolved salt mass fraction of the brine (kg salt / kg water)
   * @param mechanism deposit mechanism (density) used by the deposit model
   */
  public EntrainedSaltDepositSource(double entrainedWaterRateKgHr, double salinityMassFraction,
      DepositMechanism mechanism) {
    this.entrainedWaterRateKgHr = Math.max(0.0, entrainedWaterRateKgHr);
    this.salinityMassFraction = Math.max(0.0, Math.min(1.0, salinityMassFraction));
    this.mechanism = mechanism;
  }

  /** {@inheritDoc} */
  @Override
  public DepositMechanism getMechanism() {
    return mechanism;
  }

  /** {@inheritDoc} */
  @Override
  public double getDepositRate(String flowUnit) {
    double kgHr = entrainedWaterRateKgHr * salinityMassFraction * evaporatedFraction * captureFraction;
    return convertFromKgHr(kgHr, flowUnit);
  }

  /**
   * Estimate the fraction of aqueous water that evaporates between the inlet and the compressor discharge conditions,
   * by flashing the stream at both states.
   *
   * @param inlet compressor inlet stream (must contain water)
   * @param dischargeTemperatureC compressor discharge temperature in Celsius
   * @param dischargePressureBara compressor discharge pressure in bara
   * @return evaporated fraction of aqueous water between 0 and 1
   */
  public static double estimateWaterEvaporatedFraction(StreamInterface inlet, double dischargeTemperatureC,
      double dischargePressureBara) {
    if (inlet == null || inlet.getThermoSystem() == null) {
      return 1.0;
    }
    try {
      double waterIn = aqueousWaterFlowKgHr(inlet.getThermoSystem(), inlet.getTemperature("C"),
          inlet.getPressure("bara"));
      if (waterIn <= 0.0) {
        return 1.0;
      }
      double waterOut = aqueousWaterFlowKgHr(inlet.getThermoSystem(), dischargeTemperatureC, dischargePressureBara);
      double frac = (waterIn - waterOut) / waterIn;
      return Math.max(0.0, Math.min(1.0, frac));
    } catch (Exception e) {
      logger.warn("Water evaporated-fraction estimate failed: {}", e.getMessage());
      return 1.0;
    }
  }

  /**
   * Aqueous liquid water flow at the given conditions.
   *
   * @param baseSystem system to clone
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @return aqueous water flow in kg/hr (0 if no aqueous phase)
   */
  private static double aqueousWaterFlowKgHr(SystemInterface baseSystem, double temperatureC, double pressureBara) {
    SystemInterface sys = baseSystem.clone();
    sys.setTemperature(temperatureC, "C");
    sys.setPressure(pressureBara, "bara");
    sys.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    if (!sys.hasPhaseType("aqueous")) {
      return 0.0;
    }
    return sys.getPhaseOfType("aqueous").getComponent("water") == null ? 0.0
        : sys.getPhaseOfType("aqueous").getFlowRate("kg/hr");
  }

  private static double convertFromKgHr(double kgHr, String flowUnit) {
    if (flowUnit == null) {
      return kgHr;
    }
    switch (flowUnit) {
    case "kg/hr":
      return kgHr;
    case "kg/day":
      return kgHr * 24.0;
    case "kg/sec":
      return kgHr / 3600.0;
    case "tonnes/day":
      return kgHr * 24.0 / 1000.0;
    default:
      return kgHr;
    }
  }

  /**
   * Get the entrained water carry-over rate.
   *
   * @return entrained water rate in kg/hr
   */
  public double getEntrainedWaterRateKgHr() {
    return entrainedWaterRateKgHr;
  }

  /**
   * Set the entrained water carry-over rate.
   *
   * @param entrainedWaterRateKgHr entrained water rate in kg/hr
   */
  public void setEntrainedWaterRateKgHr(double entrainedWaterRateKgHr) {
    this.entrainedWaterRateKgHr = Math.max(0.0, entrainedWaterRateKgHr);
  }

  /**
   * Get the brine salinity.
   *
   * @return dissolved salt mass fraction (kg salt / kg water)
   */
  public double getSalinityMassFraction() {
    return salinityMassFraction;
  }

  /**
   * Set the brine salinity.
   *
   * @param salinityMassFraction dissolved salt mass fraction (kg salt / kg water)
   */
  public void setSalinityMassFraction(double salinityMassFraction) {
    this.salinityMassFraction = Math.max(0.0, Math.min(1.0, salinityMassFraction));
  }

  /**
   * Get the fraction of entrained water assumed to evaporate.
   *
   * @return evaporated fraction (0-1)
   */
  public double getEvaporatedFraction() {
    return evaporatedFraction;
  }

  /**
   * Set the fraction of entrained water assumed to evaporate.
   *
   * @param evaporatedFraction evaporated fraction (0-1)
   */
  public void setEvaporatedFraction(double evaporatedFraction) {
    this.evaporatedFraction = Math.max(0.0, Math.min(1.0, evaporatedFraction));
  }

  /**
   * Get the capture fraction.
   *
   * @return fraction of precipitated salt that deposits on the machine (0-1)
   */
  public double getCaptureFraction() {
    return captureFraction;
  }

  /**
   * Set the capture fraction.
   *
   * @param captureFraction fraction of precipitated salt that deposits on the machine (0-1)
   */
  public void setCaptureFraction(double captureFraction) {
    this.captureFraction = Math.max(0.0, Math.min(1.0, captureFraction));
  }
}
