package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * High-temperature oxygen-blown burner zone for syngas generation models.
 *
 * <p>
 * The burner zone represents the rapid mixing and partial oxidation section used upstream of ATR
 * and POX equilibrium zones. It uses adiabatic Gibbs equilibrium and adds practical screening
 * metrics for oxygen-to-carbon ratio, flame temperature, and oxygen-safety envelope checks.
 * Detailed burner aerodynamics and flame CFD are outside the scope of this screening model.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class SyngasBurnerZone extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Minimum recommended oxygen-to-carbon ratio for stable partial oxidation. */
  private double minimumOxygenToCarbonRatio = 0.45;

  /** Maximum recommended oxygen-to-carbon ratio before full-combustion risk dominates. */
  private double maximumOxygenToCarbonRatio = 0.75;

  /** Maximum screening flame temperature in Kelvin before refractory warning. */
  private double maximumFlameTemperatureK = 1773.15;

  /** Calculated oxygen-to-carbon ratio. */
  private double oxygenToCarbonRatio = Double.NaN;

  /** Calculated flame temperature in Kelvin. */
  private double flameTemperatureK = Double.NaN;

  /** Equilibrium heat release in kW from the internal Gibbs reactor. */
  private double heatReleaseKW = 0.0;

  /** Safety warning text from the most recent run. */
  private String safetyWarning = "not run";

  /** Internal Gibbs reactor reused between runs. */
  private transient GibbsReactor equilibriumReactor;

  /**
   * Creates a syngas burner zone.
   *
   * @param name equipment name
   */
  public SyngasBurnerZone(String name) {
    super(name);
  }

  /**
   * Creates a syngas burner zone with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream premixed methane, oxygen, and optional steam feed
   */
  public SyngasBurnerZone(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the recommended oxygen-to-carbon operating envelope.
   *
   * @param minimumRatio minimum oxygen-to-carbon ratio
   * @param maximumRatio maximum oxygen-to-carbon ratio
   */
  public void setOxygenToCarbonEnvelope(double minimumRatio, double maximumRatio) {
    if (!Double.isFinite(minimumRatio) || !Double.isFinite(maximumRatio) || minimumRatio < 0.0
        || maximumRatio <= minimumRatio) {
      throw new IllegalArgumentException("oxygen-to-carbon envelope must be finite and ordered");
    }
    this.minimumOxygenToCarbonRatio = minimumRatio;
    this.maximumOxygenToCarbonRatio = maximumRatio;
  }

  /**
   * Sets the maximum screening flame temperature.
   *
   * @param temperatureK maximum flame temperature in Kelvin
   */
  public void setMaximumFlameTemperature(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be finite and greater than zero");
    }
    this.maximumFlameTemperatureK = temperatureK;
  }

  /**
   * Gets the oxygen-to-carbon ratio.
   *
   * @return oxygen-to-carbon molar ratio
   */
  public double getOxygenToCarbonRatio() {
    return oxygenToCarbonRatio;
  }

  /**
   * Gets the flame temperature.
   *
   * @return flame temperature in Kelvin
   */
  public double getFlameTemperature() {
    return flameTemperatureK;
  }

  /**
   * Gets the heat release from the internal equilibrium calculation.
   *
   * @return heat release in kW
   */
  public double getHeatReleaseKW() {
    return heatReleaseKW;
  }

  /**
   * Gets the safety warning from the latest run.
   *
   * @return safety warning text
   */
  public String getSafetyWarning() {
    return safetyWarning;
  }

  /**
   * Checks whether the latest run is inside the configured screening envelope.
   *
   * @return true when O2/C and flame temperature are inside limits
   */
  public boolean isWithinOperatingEnvelope() {
    return "ok".equals(safetyWarning);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface feedSystem = getInletStream().getThermoSystem().clone();
    HydrogenProductionUtils.ensureSyngasComponents(feedSystem);
    oxygenToCarbonRatio = HydrogenProductionUtils.calculateOxygenToCarbonRatio(feedSystem);

    Stream burnerFeed = new Stream(getName() + " feed", feedSystem);
    burnerFeed.run(id);

    equilibriumReactor = HydrogenProductionUtils.createSyngasGibbsReactor(
        getName() + " Gibbs equilibrium", burnerFeed, GibbsReactor.EnergyMode.ADIABATIC);
    equilibriumReactor.run(id);

    SystemInterface outletSystem = equilibriumReactor.getOutletStream().getThermoSystem().clone();
    outletSystem.init(3);
    flameTemperatureK = outletSystem.getTemperature();
    heatReleaseKW = Math.abs(equilibriumReactor.getEnthalpyOfReactions());
    safetyWarning = evaluateOperatingEnvelope();

    outStream.setThermoSystem(outletSystem);
    outStream.run(id);
    setCalculationIdentifier(id);
  }

  /**
   * Builds a map of burner-zone results.
   *
   * @return ordered result map
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("oxygenToCarbonRatio", oxygenToCarbonRatio);
    results.put("flameTemperatureK", flameTemperatureK);
    results.put("heatReleaseKW", heatReleaseKW);
    results.put("safetyWarning", safetyWarning);
    if (outStream != null && outStream.getThermoSystem() != null) {
      results.put("syngasComposition_molFrac",
          HydrogenProductionUtils.extractSyngasComposition(outStream.getThermoSystem()));
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /**
   * Evaluates the burner-zone screening envelope.
   *
   * @return warning text, or ok when inside limits
   */
  private String evaluateOperatingEnvelope() {
    if (Double.isNaN(oxygenToCarbonRatio)) {
      return "no methane basis for oxygen-to-carbon screening";
    }
    if (oxygenToCarbonRatio < minimumOxygenToCarbonRatio) {
      return "oxygen-to-carbon ratio below partial-oxidation stability envelope";
    }
    if (oxygenToCarbonRatio > maximumOxygenToCarbonRatio) {
      return "oxygen-to-carbon ratio above screening envelope; full-combustion risk";
    }
    if (flameTemperatureK > maximumFlameTemperatureK) {
      return "flame temperature above refractory screening limit";
    }
    return "ok";
  }
}
