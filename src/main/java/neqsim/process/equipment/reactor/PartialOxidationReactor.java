package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * Partial oxidation reactor model for high-temperature syngas production.
 *
 * <p>
 * The model applies oxygen-to-carbon control, adiabatic high-temperature equilibrium in a
 * {@link SyngasBurnerZone}, optional fast quench, refractory-temperature screening, and soot/coke
 * risk metrics. It is suitable for route comparison and process integration studies before a
 * detailed burner, refractory, or soot-kinetics model is available.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class PartialOxidationReactor extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Target oxygen-to-carbon ratio. */
  private double oxygenToCarbonTarget = 0.55;

  /** Optional steam-to-carbon ratio used for soot suppression. */
  private double steamToCarbonTarget = 0.0;

  /** Whether oxygen and steam are adjusted to the configured targets. */
  private boolean ratioControlEnabled = true;

  /** Whether fast quench is active. */
  private boolean quenchEnabled = true;

  /** Quench target temperature in Kelvin. */
  private double quenchTemperatureK = 1173.15;

  /** Fractional heat loss to refractory before quench. */
  private double refractoryHeatLossFraction = 0.03;

  /** Refractory screening temperature limit in Kelvin. */
  private double refractoryTemperatureLimitK = 1873.15;

  /** Calculated oxygen-to-carbon ratio. */
  private double oxygenToCarbonRatio = Double.NaN;

  /** Calculated steam-to-carbon ratio. */
  private double steamToCarbonRatio = Double.NaN;

  /** Methane conversion fraction. */
  private double methaneConversion = 0.0;

  /** H2/CO molar ratio in product syngas. */
  private double hydrogenToCarbonMonoxideRatio = Double.NaN;

  /** Soot risk index between zero and one. */
  private double sootRiskIndex = 0.0;

  /** Refractory hot-face temperature estimate in Kelvin. */
  private double refractoryTemperatureK = Double.NaN;

  /** Refractory warning from latest run. */
  private String refractoryWarning = "not run";

  /** Dry syngas LHV in MJ/Nm3. */
  private double drySyngasLhvMjPerNm3 = 0.0;

  /** Burner-zone model from latest run. */
  private transient SyngasBurnerZone burnerZone;

  /** Quench section from latest run. */
  private transient QuenchSection quenchSection;

  /**
   * Creates a partial oxidation reactor.
   *
   * @param name equipment name
   */
  public PartialOxidationReactor(String name) {
    super(name);
  }

  /**
   * Creates a partial oxidation reactor with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream feed stream containing methane and oxygen or ratio-controlled basis
   */
  public PartialOxidationReactor(String name,
      neqsim.process.equipment.stream.StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the oxygen-to-carbon target and enables ratio control.
   *
   * @param oxygenToCarbonTarget target oxygen-to-carbon molar ratio
   */
  public void setOxygenToCarbonTarget(double oxygenToCarbonTarget) {
    validateNonNegative(oxygenToCarbonTarget, "oxygenToCarbonTarget");
    this.oxygenToCarbonTarget = oxygenToCarbonTarget;
    this.ratioControlEnabled = true;
  }

  /**
   * Sets optional steam-to-carbon ratio and enables ratio control.
   *
   * @param steamToCarbonTarget target steam-to-carbon molar ratio
   */
  public void setSteamToCarbonTarget(double steamToCarbonTarget) {
    validateNonNegative(steamToCarbonTarget, "steamToCarbonTarget");
    this.steamToCarbonTarget = steamToCarbonTarget;
    this.ratioControlEnabled = true;
  }

  /**
   * Enables or disables ratio control.
   *
   * @param enabled true to adjust oxygen and steam to target ratios
   */
  public void setRatioControlEnabled(boolean enabled) {
    this.ratioControlEnabled = enabled;
  }

  /**
   * Enables or disables the fast quench section.
   *
   * @param enabled true to apply quench
   */
  public void setQuenchEnabled(boolean enabled) {
    this.quenchEnabled = enabled;
  }

  /**
   * Sets quench target temperature.
   *
   * @param temperatureK target quench outlet temperature in Kelvin
   */
  public void setQuenchTemperature(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be finite and greater than zero");
    }
    this.quenchTemperatureK = temperatureK;
  }

  /**
   * Sets refractory heat-loss fraction.
   *
   * @param fraction fractional heat loss, between zero and one
   */
  public void setRefractoryHeatLossFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("fraction must be finite and between zero and one");
    }
    this.refractoryHeatLossFraction = fraction;
  }

  /**
   * Sets refractory temperature limit.
   *
   * @param temperatureK refractory limit in Kelvin
   */
  public void setRefractoryTemperatureLimit(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be finite and greater than zero");
    }
    this.refractoryTemperatureLimitK = temperatureK;
  }

  /**
   * Gets oxygen-to-carbon ratio.
   *
   * @return oxygen-to-carbon molar ratio
   */
  public double getOxygenToCarbonRatio() {
    return oxygenToCarbonRatio;
  }

  /**
   * Gets methane conversion.
   *
   * @return methane conversion fraction
   */
  public double getMethaneConversion() {
    return methaneConversion;
  }

  /**
   * Gets H2/CO ratio.
   *
   * @return product H2/CO molar ratio
   */
  public double getHydrogenToCarbonMonoxideRatio() {
    return hydrogenToCarbonMonoxideRatio;
  }

  /**
   * Gets soot risk index.
   *
   * @return soot risk index between zero and one
   */
  public double getSootRiskIndex() {
    return sootRiskIndex;
  }

  /**
   * Gets refractory hot-face temperature.
   *
   * @return refractory temperature in Kelvin
   */
  public double getRefractoryTemperature() {
    return refractoryTemperatureK;
  }

  /**
   * Gets refractory warning.
   *
   * @return refractory warning text
   */
  public String getRefractoryWarning() {
    return refractoryWarning;
  }

  /**
   * Gets dry syngas LHV.
   *
   * @return dry syngas LHV in MJ/Nm3
   */
  public double getDrySyngasLhvMjPerNm3() {
    return drySyngasLhvMjPerNm3;
  }

  /**
   * Gets the quench section from the latest run.
   *
   * @return quench section, or null when disabled or not run
   */
  public QuenchSection getQuenchSection() {
    return quenchSection;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface controlledFeed = getInletStream().getThermoSystem().clone();
    HydrogenProductionUtils.ensureSyngasComponents(controlledFeed);
    if (ratioControlEnabled) {
      applyRatioControls(controlledFeed);
    }
    controlledFeed.init(3);
    oxygenToCarbonRatio = HydrogenProductionUtils.calculateOxygenToCarbonRatio(controlledFeed);
    steamToCarbonRatio = HydrogenProductionUtils.calculateSteamToCarbonRatio(controlledFeed);

    Stream burnerFeed = new Stream(getName() + " burner feed", controlledFeed);
    burnerFeed.run(id);
    burnerZone = new SyngasBurnerZone(getName() + " burner zone", burnerFeed);
    burnerZone.setOxygenToCarbonEnvelope(0.40, 0.75);
    burnerZone.setMaximumFlameTemperature(refractoryTemperatureLimitK);
    burnerZone.run(id);

    refractoryTemperatureK =
        burnerZone.getFlameTemperature() * (1.0 - 0.5 * refractoryHeatLossFraction);
    refractoryWarning = refractoryTemperatureK > refractoryTemperatureLimitK
        ? "refractory hot-face temperature above screening limit"
        : "ok";

    SystemInterface productSystem = burnerZone.getOutletStream().getThermoSystem().clone();
    if (quenchEnabled) {
      quenchSection = new QuenchSection(getName() + " quench", burnerZone.getOutletStream());
      quenchSection.setTargetTemperature(quenchTemperatureK);
      quenchSection.run(id);
      productSystem = quenchSection.getOutletStream().getThermoSystem().clone();
    } else {
      quenchSection = null;
    }
    productSystem.init(3);

    methaneConversion =
        HydrogenProductionUtils.calculateMethaneConversion(controlledFeed, productSystem);
    hydrogenToCarbonMonoxideRatio = calculateH2ToCoRatio(productSystem);
    drySyngasLhvMjPerNm3 = HydrogenProductionUtils.estimateDrySyngasLhvMjPerNm3(productSystem);
    sootRiskIndex = calculateSootRiskIndex();

    outStream.setThermoSystem(productSystem);
    outStream.run(id);
    setCalculationIdentifier(id);
  }

  /**
   * Builds a map of POX results.
   *
   * @return ordered result map
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("oxygenToCarbonRatio", oxygenToCarbonRatio);
    results.put("steamToCarbonRatio", steamToCarbonRatio);
    results.put("methaneConversion", methaneConversion);
    results.put("hydrogenToCarbonMonoxideRatio", hydrogenToCarbonMonoxideRatio);
    results.put("sootRiskIndex", sootRiskIndex);
    results.put("refractoryTemperatureK", refractoryTemperatureK);
    results.put("refractoryWarning", refractoryWarning);
    results.put("drySyngasLhvMjPerNm3", drySyngasLhvMjPerNm3);
    if (quenchSection != null) {
      results.put("quenchHeatRemovedKW", quenchSection.getHeatRemoved("kW"));
      results.put("quenchSeverityK", quenchSection.getQuenchSeverity());
    }
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
   * Applies feed ratio controls by adjusting oxygen and optional steam amounts.
   *
   * @param system thermodynamic system to modify
   */
  private void applyRatioControls(SystemInterface system) {
    double methane = HydrogenProductionUtils.getComponentMoles(system, "methane");
    if (methane <= 0.0) {
      return;
    }
    double nitrogen = HydrogenProductionUtils.getComponentMoles(system, "nitrogen");
    system.setEmptyFluid();
    system.addComponent("methane", methane, "mole/sec");
    system.addComponent("oxygen", methane * oxygenToCarbonTarget, "mole/sec");
    system.addComponent("water", methane * steamToCarbonTarget, "mole/sec");
    if (nitrogen > 0.0) {
      system.addComponent("nitrogen", nitrogen, "mole/sec");
    }
    HydrogenProductionUtils.ensureSyngasComponents(system);
  }

  /**
   * Calculates product H2/CO ratio.
   *
   * @param system product system
   * @return H2/CO ratio, or positive infinity when CO is absent
   */
  private double calculateH2ToCoRatio(SystemInterface system) {
    double h2 = HydrogenProductionUtils.getComponentMoles(system, "hydrogen");
    double co = HydrogenProductionUtils.getComponentMoles(system, "CO");
    if (co <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return h2 / co;
  }

  /**
   * Calculates a simple soot risk index for POX route screening.
   *
   * @return soot risk index from zero to one
   */
  private double calculateSootRiskIndex() {
    double oxygenPenalty = oxygenToCarbonRatio < 0.48 ? (0.48 - oxygenToCarbonRatio) / 0.48 : 0.0;
    double steamCredit = Math.min(0.25, 0.25 * steamToCarbonRatio);
    double quenchPenalty =
        quenchEnabled && quenchSection != null && quenchSection.getQuenchSeverity() > 700.0 ? 0.0
            : 0.25;
    return HydrogenProductionUtils.clamp(oxygenPenalty + quenchPenalty - steamCredit, 0.0, 1.0);
  }

  /**
   * Validates a non-negative finite input.
   *
   * @param value value to validate
   * @param name parameter name used in exception text
   */
  private void validateNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }
}
