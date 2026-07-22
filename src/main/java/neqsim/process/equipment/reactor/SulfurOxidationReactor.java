package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Stoichiometric reactor for partial oxidation of hydrogen sulfide to elemental sulfur.
 *
 * <p>
 * The model applies the screening reaction {@code 2 H2S + O2 -> 2 H2O + 0.25 S8} to sour gas, treating methane and
 * other hydrocarbons as inert. It is intended as a process-unit link between oxygen ingress or controlled oxidation and
 * downstream sulfur filtration, where the generated {@code S8} can precipitate as solid sulfur and be captured by a
 * sulfur filter.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class SulfurOxidationReactor extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Molar mass of S8 in kg/mol. */
  private static final double S8_MOLAR_MASS_KG_PER_MOL = 0.25648;

  /** Fraction of inlet H2S allowed to react, before oxygen limitation is applied. */
  private double h2sConversionTarget = 1.0;

  /** Pressure drop across the reactor in bar. */
  private double pressureDropBar = 0.0;

  /** Whether to run a solid sulfur flash after adding S8. */
  private boolean solidFlashEnabled = true;

  /** H2S moles consumed on the current stream basis. */
  private double h2sConsumedMoles = 0.0;

  /** Oxygen moles consumed on the current stream basis. */
  private double oxygenConsumedMoles = 0.0;

  /** Water moles produced on the current stream basis. */
  private double waterProducedMoles = 0.0;

  /** S8 moles produced on the current stream basis. */
  private double s8ProducedMoles = 0.0;

  /** Fraction of inlet H2S consumed in the last run. */
  private double h2sConversion = 0.0;

  /** Fraction of inlet oxygen consumed in the last run. */
  private double oxygenConversion = 0.0;

  /** Name of the limiting reactant in the last run. */
  private String limitingReactant = "none";

  /** Whether solid sulfur was present after the outlet flash. */
  private boolean solidSulfurPresent = false;

  /** Mass of S8 formed on the current stream basis in kg. */
  private double s8ProducedMassKg = 0.0;

  /**
   * Creates a sulfur oxidation reactor without an inlet stream.
   *
   * @param name equipment name
   */
  public SulfurOxidationReactor(String name) {
    super(name);
  }

  /**
   * Creates a sulfur oxidation reactor with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream sour gas stream containing H2S and oxygen
   */
  public SulfurOxidationReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the target H2S conversion before oxygen limitation is applied.
   *
   * @param conversion target conversion fraction from 0.0 to 1.0
   */
  public void setH2SConversionTarget(double conversion) {
    if (!Double.isFinite(conversion) || conversion < 0.0 || conversion > 1.0) {
      throw new IllegalArgumentException("conversion must be finite and between zero and one");
    }
    this.h2sConversionTarget = conversion;
  }

  /**
   * Gets the target H2S conversion.
   *
   * @return target H2S conversion fraction
   */
  public double getH2SConversionTarget() {
    return h2sConversionTarget;
  }

  /**
   * Sets the reactor pressure drop.
   *
   * @param pressureDropBar pressure drop in bar, greater than or equal to zero
   */
  public void setPressureDrop(double pressureDropBar) {
    if (!Double.isFinite(pressureDropBar) || pressureDropBar < 0.0) {
      throw new IllegalArgumentException("pressureDropBar must be finite and non-negative");
    }
    this.pressureDropBar = pressureDropBar;
  }

  /**
   * Gets the reactor pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getPressureDrop() {
    return pressureDropBar;
  }

  /**
   * Enables or disables solid sulfur flashing at the reactor outlet.
   *
   * @param enabled true to run a solid sulfur flash after reaction
   */
  public void setSolidFlashEnabled(boolean enabled) {
    this.solidFlashEnabled = enabled;
  }

  /**
   * Returns whether solid sulfur flashing is enabled.
   *
   * @return true if a solid sulfur flash is run at the outlet
   */
  public boolean isSolidFlashEnabled() {
    return solidFlashEnabled;
  }

  /**
   * Gets H2S consumed in the last run.
   *
   * @return consumed H2S moles on the current stream basis
   */
  public double getH2SConsumedMoles() {
    return h2sConsumedMoles;
  }

  /**
   * Gets oxygen consumed in the last run.
   *
   * @return consumed oxygen moles on the current stream basis
   */
  public double getOxygenConsumedMoles() {
    return oxygenConsumedMoles;
  }

  /**
   * Gets water produced in the last run.
   *
   * @return produced water moles on the current stream basis
   */
  public double getWaterProducedMoles() {
    return waterProducedMoles;
  }

  /**
   * Gets S8 produced in the last run.
   *
   * @return produced S8 moles on the current stream basis
   */
  public double getS8ProducedMoles() {
    return s8ProducedMoles;
  }

  /**
   * Gets the S8 mass produced in the last run.
   *
   * @return produced S8 mass in kg on the current stream basis
   */
  public double getS8ProducedMassKg() {
    return s8ProducedMassKg;
  }

  /**
   * Gets actual H2S conversion from the last run.
   *
   * @return H2S conversion fraction
   */
  public double getH2SConversion() {
    return h2sConversion;
  }

  /**
   * Gets actual oxygen conversion from the last run.
   *
   * @return oxygen conversion fraction
   */
  public double getOxygenConversion() {
    return oxygenConversion;
  }

  /**
   * Gets the limiting reactant from the last run.
   *
   * @return limiting reactant name, or {@code none} if no reaction occurred
   */
  public String getLimitingReactant() {
    return limitingReactant;
  }

  /**
   * Returns whether solid sulfur was present after the outlet flash.
   *
   * @return true if a solid phase was present after reaction and flash
   */
  public boolean isSolidSulfurPresent() {
    return solidSulfurPresent;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (getInletStream() == null) {
      throw new IllegalStateException("SulfurOxidationReactor requires an inlet stream");
    }

    resetRunResults();
    SystemInterface outletSystem = getInletStream().getThermoSystem().clone();
    ensureSulfurComponents(outletSystem);
    applyPressureDrop(outletSystem);

    double h2sIn = HydrogenProductionUtils.getComponentMoles(outletSystem, "H2S");
    double oxygenIn = HydrogenProductionUtils.getComponentMoles(outletSystem, "oxygen");
    double targetH2S = h2sIn * h2sConversionTarget;
    double oxygenLimitedH2S = oxygenIn * 2.0;
    h2sConsumedMoles = Math.max(0.0, Math.min(targetH2S, oxygenLimitedH2S));

    if (h2sConsumedMoles > 0.0) {
      oxygenConsumedMoles = 0.5 * h2sConsumedMoles;
      waterProducedMoles = h2sConsumedMoles;
      s8ProducedMoles = h2sConsumedMoles / 8.0;
      s8ProducedMassKg = s8ProducedMoles * S8_MOLAR_MASS_KG_PER_MOL;
      limitingReactant = oxygenLimitedH2S + 1.0e-12 < targetH2S ? "oxygen" : "H2S";

      outletSystem.addComponent("H2S", -h2sConsumedMoles);
      outletSystem.addComponent("oxygen", -oxygenConsumedMoles);
      outletSystem.addComponent("water", waterProducedMoles);
      outletSystem.addComponent("S8", s8ProducedMoles);
    }

    flashOutlet(outletSystem);
    h2sConversion = h2sIn > 0.0 ? h2sConsumedMoles / h2sIn : 0.0;
    oxygenConversion = oxygenIn > 0.0 ? oxygenConsumedMoles / oxygenIn : 0.0;
    getOutletStream().setThermoSystem(outletSystem);
    getOutletStream().run(id);
    setCalculationIdentifier(id);
  }

  /**
   * Gets a compact result map for reporting and agent workflows.
   *
   * @return ordered map with sulfur oxidation metrics
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("h2sConversionTarget", h2sConversionTarget);
    results.put("h2sConversion", h2sConversion);
    results.put("oxygenConversion", oxygenConversion);
    results.put("limitingReactant", limitingReactant);
    results.put("h2sConsumedMoles", h2sConsumedMoles);
    results.put("oxygenConsumedMoles", oxygenConsumedMoles);
    results.put("waterProducedMoles", waterProducedMoles);
    results.put("s8ProducedMoles", s8ProducedMoles);
    results.put("s8ProducedMassKg", s8ProducedMassKg);
    results.put("solidSulfurPresent", solidSulfurPresent);
    results.put("pressureDropBar", pressureDropBar);
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(getResults());
  }

  /**
   * Resets calculated results before a new run.
   */
  private void resetRunResults() {
    h2sConsumedMoles = 0.0;
    oxygenConsumedMoles = 0.0;
    waterProducedMoles = 0.0;
    s8ProducedMoles = 0.0;
    s8ProducedMassKg = 0.0;
    h2sConversion = 0.0;
    oxygenConversion = 0.0;
    limitingReactant = "none";
    solidSulfurPresent = false;
  }

  /**
   * Ensures components needed by the sulfur oxidation stoichiometry exist.
   *
   * @param system thermodynamic system to update
   */
  private void ensureSulfurComponents(SystemInterface system) {
    HydrogenProductionUtils.ensureComponent(system, "H2S");
    HydrogenProductionUtils.ensureComponent(system, "oxygen");
    HydrogenProductionUtils.ensureComponent(system, "water");
    HydrogenProductionUtils.ensureComponent(system, "S8");
    system.createDatabase(true);
    system.init(0);
  }

  /**
   * Applies the configured pressure drop to the outlet system.
   *
   * @param system thermodynamic system to update
   */
  private void applyPressureDrop(SystemInterface system) {
    if (pressureDropBar > 0.0) {
      system.setPressure(Math.max(0.1, system.getPressure("bara") - pressureDropBar), "bara");
    }
  }

  /**
   * Flashes the outlet system after reaction and records whether solid sulfur exists.
   *
   * @param system thermodynamic system to flash
   */
  private void flashOutlet(SystemInterface system) {
    system.setMultiPhaseCheck(true);
    system.setSolidPhaseCheck("S8");
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    if (solidFlashEnabled) {
      operations.TPSolidflash();
    } else {
      operations.TPflash();
    }
    system.initProperties();
    solidSulfurPresent = system.hasPhaseType("solid");
  }
}
