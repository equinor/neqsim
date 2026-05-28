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
 * Autothermal reformer model for oxygen-blown hydrogen and syngas production.
 *
 * <p>
 * The model combines a rapid oxygen-blown burner zone with a downstream catalytic reforming
 * equilibrium zone. It adds oxygen-to-carbon and steam-to-carbon control targets, methane
 * conversion reporting, soot-risk screening, and burner-zone flame-temperature warnings. It is a
 * flowsheet-ready ATR template, not a replacement for detailed burner CFD or vendor reactor design.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class AutothermalReformer extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Target oxygen-to-carbon ratio. */
  private double oxygenToCarbonTarget = 0.60;

  /** Target steam-to-carbon ratio. */
  private double steamToCarbonTarget = 1.5;

  /** Whether oxygen and steam are adjusted to the configured targets. */
  private boolean ratioControlEnabled = true;

  /** Optional isothermal reforming-zone temperature in Kelvin. */
  private double reformingTemperatureK = 1223.15;

  /** Whether to hold the reforming zone at {@link #reformingTemperatureK}. */
  private boolean isothermalReformingZone = false;

  /** Methane conversion across the complete ATR. */
  private double methaneConversion = 0.0;

  /** Calculated oxygen-to-carbon ratio after controls. */
  private double oxygenToCarbonRatio = Double.NaN;

  /** Calculated steam-to-carbon ratio after controls. */
  private double steamToCarbonRatio = Double.NaN;

  /** Dry syngas LHV in MJ/Nm3. */
  private double drySyngasLhvMjPerNm3 = 0.0;

  /** Soot and coke risk index from zero to one. */
  private double sootRiskIndex = 0.0;

  /** Burner zone used by the last run. */
  private transient SyngasBurnerZone burnerZone;

  /** Reforming-zone Gibbs reactor used by the last run. */
  private transient GibbsReactor reformingReactor;

  /**
   * Creates an autothermal reformer.
   *
   * @param name equipment name
   */
  public AutothermalReformer(String name) {
    super(name);
  }

  /**
   * Creates an autothermal reformer with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream feed stream containing methane, steam, and oxygen or ratio-controlled basis
   */
  public AutothermalReformer(String name, StreamInterface inletStream) {
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
   * Sets the steam-to-carbon target and enables ratio control.
   *
   * @param steamToCarbonTarget target steam-to-carbon molar ratio
   */
  public void setSteamToCarbonTarget(double steamToCarbonTarget) {
    validateNonNegative(steamToCarbonTarget, "steamToCarbonTarget");
    this.steamToCarbonTarget = steamToCarbonTarget;
    this.ratioControlEnabled = true;
  }

  /**
   * Enables or disables automatic ratio control.
   *
   * @param enabled true to adjust oxygen and steam to targets
   */
  public void setRatioControlEnabled(boolean enabled) {
    this.ratioControlEnabled = enabled;
  }

  /**
   * Sets the reforming-zone temperature for isothermal operation.
   *
   * @param temperatureK reforming-zone temperature in Kelvin
   */
  public void setReformingTemperature(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be finite and greater than zero");
    }
    this.reformingTemperatureK = temperatureK;
  }

  /**
   * Sets whether the downstream reforming zone is held isothermal.
   *
   * @param isothermal true for isothermal Gibbs equilibrium, false for adiabatic
   */
  public void setIsothermalReformingZone(boolean isothermal) {
    this.isothermalReformingZone = isothermal;
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
   * Gets oxygen-to-carbon ratio.
   *
   * @return oxygen-to-carbon ratio
   */
  public double getOxygenToCarbonRatio() {
    return oxygenToCarbonRatio;
  }

  /**
   * Gets steam-to-carbon ratio.
   *
   * @return steam-to-carbon ratio
   */
  public double getSteamToCarbonRatio() {
    return steamToCarbonRatio;
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
   * Gets dry syngas LHV.
   *
   * @return dry syngas LHV in MJ/Nm3
   */
  public double getDrySyngasLhvMjPerNm3() {
    return drySyngasLhvMjPerNm3;
  }

  /**
   * Gets the burner zone used by the latest run.
   *
   * @return burner zone, or null before run
   */
  public SyngasBurnerZone getBurnerZone() {
    return burnerZone;
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
    burnerZone.setOxygenToCarbonEnvelope(0.45, 0.80);
    burnerZone.run(id);

    StreamInterface reformingFeed = burnerZone.getOutletStream();
    if (isothermalReformingZone) {
      SystemInterface reformingSystem = reformingFeed.getThermoSystem().clone();
      reformingSystem.setTemperature(reformingTemperatureK);
      reformingSystem.init(3);
      reformingFeed = new Stream(getName() + " reforming feed", reformingSystem);
      reformingFeed.run(id);
    }

    GibbsReactor.EnergyMode energyMode =
        isothermalReformingZone ? GibbsReactor.EnergyMode.ISOTHERMAL
            : GibbsReactor.EnergyMode.ADIABATIC;
    reformingReactor = HydrogenProductionUtils
        .createSyngasGibbsReactor(getName() + " catalytic equilibrium", reformingFeed, energyMode);
    reformingReactor.run(id);

    SystemInterface outletSystem = reformingReactor.getOutletStream().getThermoSystem().clone();
    outletSystem.init(3);
    methaneConversion =
        HydrogenProductionUtils.calculateMethaneConversion(controlledFeed, outletSystem);
    drySyngasLhvMjPerNm3 = HydrogenProductionUtils.estimateDrySyngasLhvMjPerNm3(outletSystem);
    sootRiskIndex = calculateSootRiskIndex();

    outStream.setThermoSystem(outletSystem);
    outStream.run(id);
    setCalculationIdentifier(id);
  }

  /**
   * Builds a map of ATR results.
   *
   * @return ordered result map
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("oxygenToCarbonRatio", oxygenToCarbonRatio);
    results.put("steamToCarbonRatio", steamToCarbonRatio);
    results.put("methaneConversion", methaneConversion);
    results.put("sootRiskIndex", sootRiskIndex);
    results.put("drySyngasLhvMjPerNm3", drySyngasLhvMjPerNm3);
    if (burnerZone != null) {
      results.put("burnerFlameTemperatureK", burnerZone.getFlameTemperature());
      results.put("burnerSafetyWarning", burnerZone.getSafetyWarning());
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
   * Applies O2/C and S/C targets by adjusting oxygen and water component amounts.
   *
   * @param system feed system to modify
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
   * Calculates a simple soot/coke risk index for ATR screening.
   *
   * @return risk index from zero to one
   */
  private double calculateSootRiskIndex() {
    double oxygenPenalty = oxygenToCarbonRatio < 0.50 ? (0.50 - oxygenToCarbonRatio) / 0.50 : 0.0;
    double steamPenalty = steamToCarbonRatio < 1.0 ? (1.0 - steamToCarbonRatio) : 0.0;
    double temperaturePenalty = burnerZone != null && burnerZone.getFlameTemperature() > 1800.0
        ? (burnerZone.getFlameTemperature() - 1800.0) / 600.0
        : 0.0;
    return HydrogenProductionUtils.clamp(oxygenPenalty + 0.5 * steamPenalty + temperaturePenalty,
        0.0, 1.0);
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
