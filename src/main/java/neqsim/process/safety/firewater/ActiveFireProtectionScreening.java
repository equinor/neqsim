package neqsim.process.safety.firewater;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Screening of active versus passive fire protection for a process area or item of equipment.
 *
 * <p>
 * Encodes the protection hierarchy that governs offshore fire-protection design, so that a proposal to substitute one
 * measure for another can be tested rather than argued:
 * </p>
 * <ul>
 * <li><b>Inventory limitation and blowdown come first.</b> NORSOK S-001 makes depressurisation, as fast as practicable,
 * the primary means of protecting a pressurised inventory; passive fire protection is a supplement to blowdown, not a
 * replacement for it.</li>
 * <li><b>Fire water is not a substitute for passive fire protection.</b> The cooling effect of fire-fighting equipment
 * may not be taken into account when designing passive fire protection for load-bearing structure, and fire water may
 * not be credited when establishing design accidental loads. Credit for cooling of vessels, equipment and piping has to
 * be justified.</li>
 * <li><b>Passive fire protection is likewise not a substitute for the area fire-water requirement. </b> The
 * area-coverage requirement is written against the area, not against a single item, so protecting one item passively
 * does not discharge it.</li>
 * <li><b>Deluge does not extinguish a pressurised gas jet fire.</b> Its creditable function against a jet fire is
 * cooling of exposed surfaces and control of escalation, and it is far less effective where the jet impinges directly.
 * Against a liquid pool fire, foam-water deluge can control and extinguish.</li>
 * </ul>
 *
 * <p>
 * The screening returns a ranked measure list and an explicit statement of which substitutions the requirement
 * framework does and does not allow, each with the reason attached, so the output can be carried into a deviation
 * application or a modification basis.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ActiveFireProtectionScreening implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Dominant fire type for the scenario under screening. */
  public enum FireScenario {
    /** Pressurised gas release, momentum-dominated flame. */
    GAS_JET_FIRE,
    /** Flashing or spraying liquid release. */
    TWO_PHASE_JET_FIRE,
    /** Confined or unconfined liquid pool. */
    LIQUID_POOL_FIRE,
    /** Slow-developing fire in ordinary combustibles. */
    ORDINARY_COMBUSTIBLE_FIRE
  }

  private final FireScenario scenario;
  private double incidentHeatFluxKWPerM2 = 250.0;
  private double isolatableInventoryKg = Double.NaN;
  private double blowdownTimeToTargetS = Double.NaN;
  private double bareSteelTimeToCriticalS = Double.NaN;
  private boolean loadBearingStructureExposed = false;
  private boolean passiveFireProtectionPresent = false;
  private boolean nozzlesCanBePlacedAboveEquipment = true;
  private boolean liquidPoolCredible = false;
  private double dedicatedObjectDemandLpm = Double.NaN;
  private double generalAreaDemandLpm = Double.NaN;

  /**
   * Create a screening for a fire scenario.
   *
   * @param scenario dominant fire type, must not be null
   */
  public ActiveFireProtectionScreening(FireScenario scenario) {
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    this.scenario = scenario;
  }

  /**
   * Set the incident heat flux on the exposed target.
   *
   * @param fluxKWPerM2 incident heat flux in kW/m2, must be greater than zero
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setIncidentHeatFluxKWPerM2(double fluxKWPerM2) {
    if (fluxKWPerM2 <= 0.0) {
      throw new IllegalArgumentException("heat flux must be > 0, got " + fluxKWPerM2);
    }
    this.incidentHeatFluxKWPerM2 = fluxKWPerM2;
    return this;
  }

  /**
   * Set the isolatable hydrocarbon inventory feeding the fire.
   *
   * @param inventoryKg inventory in kg, must be zero or greater
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setIsolatableInventoryKg(double inventoryKg) {
    if (inventoryKg < 0.0) {
      throw new IllegalArgumentException("inventory must be >= 0, got " + inventoryKg);
    }
    this.isolatableInventoryKg = inventoryKg;
    return this;
  }

  /**
   * Set the blowdown time to the target pressure.
   *
   * @param timeS time in seconds, must be greater than zero; leave unset when the segment is not depressurised
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setBlowdownTimeToTargetS(double timeS) {
    if (timeS <= 0.0) {
      throw new IllegalArgumentException("blowdown time must be > 0, got " + timeS);
    }
    this.blowdownTimeToTargetS = timeS;
    return this;
  }

  /**
   * Set the time for unprotected steel to reach its critical temperature under the fire load.
   *
   * <p>
   * Typically obtained from {@code neqsim.process.safety.fire.PfpDemandCalculator}.
   * </p>
   *
   * @param timeS time in seconds, must be greater than zero
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setBareSteelTimeToCriticalS(double timeS) {
    if (timeS <= 0.0) {
      throw new IllegalArgumentException("time to critical must be > 0, got " + timeS);
    }
    this.bareSteelTimeToCriticalS = timeS;
    return this;
  }

  /**
   * Declare whether load-bearing structure is exposed to the fire.
   *
   * @param exposed true when primary structure is within the fire envelope
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setLoadBearingStructureExposed(boolean exposed) {
    this.loadBearingStructureExposed = exposed;
    return this;
  }

  /**
   * Declare whether passive fire protection is already applied to the exposed target.
   *
   * @param present true when the target carries passive fire protection
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setPassiveFireProtectionPresent(boolean present) {
    this.passiveFireProtectionPresent = present;
    return this;
  }

  /**
   * Declare whether nozzles may physically be placed above the equipment.
   *
   * <p>
   * Maintenance access, lifting routes and tube-bundle withdrawal frequently prevent overhead nozzles. When they do,
   * side-mounted nozzles or dedicated object protection are the routes that remain; abandoning coverage altogether is
   * not one of them.
   * </p>
   *
   * @param canBePlaced true when overhead nozzles are physically acceptable
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setNozzlesCanBePlacedAboveEquipment(boolean canBePlaced) {
    this.nozzlesCanBePlacedAboveEquipment = canBePlaced;
    return this;
  }

  /**
   * Declare whether a liquid pool fire is credible in the area.
   *
   * @param credible true when a spill can form a burning pool
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setLiquidPoolCredible(boolean credible) {
    this.liquidPoolCredible = credible;
    return this;
  }

  /**
   * Set the two competing fire-water demands so the screening can report the saving of selective protection.
   *
   * @param dedicatedObjectDemandLpm demand for dedicated protection of the critical items, l/min
   * @param generalAreaDemandLpm demand for blanket general area protection, l/min
   * @return this screening, for chaining
   */
  public ActiveFireProtectionScreening setDemands(double dedicatedObjectDemandLpm, double generalAreaDemandLpm) {
    this.dedicatedObjectDemandLpm = dedicatedObjectDemandLpm;
    this.generalAreaDemandLpm = generalAreaDemandLpm;
    return this;
  }

  /**
   * Whether water application can extinguish the flame itself.
   *
   * @return true only for scenarios where water or foam-water can achieve extinguishment
   */
  public boolean isWaterExtinguishing() {
    return scenario == FireScenario.LIQUID_POOL_FIRE || scenario == FireScenario.ORDINARY_COMBUSTIBLE_FIRE;
  }

  /**
   * Whether fire water is creditable for controlling escalation on this scenario.
   *
   * @return true when cooling of exposed surfaces is a meaningful contribution
   */
  public boolean isWaterCreditableForEscalationControl() {
    return true;
  }

  /**
   * Whether passive fire protection may be offered in place of the fire-water requirement.
   *
   * @return always false; the requirement framework does not permit this substitution
   */
  public boolean isPassiveSubstitutionPermitted() {
    return false;
  }

  /**
   * Reason attached to the passive-substitution answer.
   *
   * @return explanatory text
   */
  public String passiveSubstitutionReason() {
    return "Passive fire protection and fire water are complementary, not interchangeable. The "
        + "cooling effect of fire-fighting equipment may not be credited when designing passive fire "
        + "protection or when establishing design accidental loads, and the area fire-water "
        + "requirement is written against the area rather than against a single protected item. "
        + "Passive fire protection can reduce the CONSEQUENCE of not having water on a specific "
        + "item, and is the right measure where water cannot reach, but it does not discharge the "
        + "area requirement. Removing the area requirement needs an accepted deviation or a "
        + "performance-based demonstration, not a substitution argument.";
  }

  /**
   * Whether blowdown is fast enough to empty the inventory before unprotected steel fails.
   *
   * @return true when both times are known and blowdown completes first, false when it does not, and false when either
   * time is unknown
   */
  public boolean isBlowdownFasterThanFailure() {
    if (Double.isNaN(blowdownTimeToTargetS) || Double.isNaN(bareSteelTimeToCriticalS)) {
      return false;
    }
    return blowdownTimeToTargetS < bareSteelTimeToCriticalS;
  }

  /**
   * Margin between the time to failure of unprotected steel and the blowdown time.
   *
   * @return margin in seconds, positive when blowdown wins; {@link Double#NaN} when either input is unknown
   */
  public double blowdownMarginS() {
    if (Double.isNaN(blowdownTimeToTargetS) || Double.isNaN(bareSteelTimeToCriticalS)) {
      return Double.NaN;
    }
    return bareSteelTimeToCriticalS - blowdownTimeToTargetS;
  }

  /**
   * Fire-water saving obtained by protecting the critical objects instead of the whole area.
   *
   * @return saving as a fraction of the general area demand, or {@link Double#NaN} when the demands were not supplied
   */
  public double selectiveProtectionSavingFraction() {
    if (Double.isNaN(dedicatedObjectDemandLpm) || Double.isNaN(generalAreaDemandLpm) || generalAreaDemandLpm <= 0.0) {
      return Double.NaN;
    }
    return 1.0 - dedicatedObjectDemandLpm / generalAreaDemandLpm;
  }

  /**
   * Ranked list of measures for this scenario, strongest first.
   *
   * @return ordered measure descriptions
   */
  public List<String> rankedMeasures() {
    List<String> m = new ArrayList<String>();
    m.add("1. Limit and remove the inventory: segment isolation and emergency depressurisation, "
        + "released on confirmed fire or gas detection in the fire area.");
    if (scenario == FireScenario.GAS_JET_FIRE || scenario == FireScenario.TWO_PHASE_JET_FIRE) {
      m.add("2. Passive fire protection on the items whose failure would escalate the event, sized "
          + "for the jet-fire load and for the time the inventory takes to blow down.");
      m.add("3. Fire water for cooling of exposed surfaces and control of escalation beyond the "
          + "impinged item. Water will not extinguish a pressurised jet flame.");
    } else {
      m.add("2. Foam-water application for control and extinguishment of the pool, plus drainage so "
          + "the pool cannot persist or spread.");
      m.add("3. Passive fire protection on items that a sustained pool fire would reach.");
    }
    if (!nozzlesCanBePlacedAboveEquipment) {
      m.add("4. Where overhead nozzles are excluded by maintenance access, use side-mounted nozzles "
          + "directed at the exposed surfaces, or dedicated object spray rings, rather than leaving "
          + "the item uncovered.");
    }
    if (liquidPoolCredible) {
      m.add("5. Confirm that lubricating and seal-oil inventories in the area are included in the "
          + "pool-fire scenario and in the foam demand; they are a common omission when an area is "
          + "labelled a gas area.");
    }
    return m;
  }

  /**
   * Overall screening statement.
   *
   * @return verdict text
   */
  public String verdict() {
    StringBuilder sb = new StringBuilder();
    sb.append(scenario.name()).append(" at ").append(String.format("%.0f", incidentHeatFluxKWPerM2)).append(" kW/m2. ");
    sb.append(isWaterExtinguishing() ? "Water application can extinguish this fire type. "
        : "Water application cannot extinguish this fire type; the creditable function is cooling "
            + "and escalation control. ");
    if (!Double.isNaN(blowdownMarginS())) {
      sb.append(isBlowdownFasterThanFailure()
          ? "Blowdown empties the segment " + String.format("%.0f", blowdownMarginS())
              + " s before unprotected steel reaches its critical temperature, so the inventory is "
              + "the governing control. "
          : "Unprotected steel reaches its critical temperature before blowdown completes; passive "
              + "protection or faster blowdown is required. ");
    }
    if (loadBearingStructureExposed) {
      sb.append(
          "Load-bearing structure is exposed: no fire-water cooling credit is permitted for " + "its protection. ");
    }
    if (passiveFireProtectionPresent) {
      sb.append("Passive fire protection is present on the target. ");
    }
    sb.append("Passive protection may not be offered in place of the area fire-water requirement.");
    return sb.toString();
  }

  /**
   * Serialise the screening to JSON.
   *
   * @return pretty-printed JSON document
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("scenario", scenario.name());
    root.addProperty("incidentHeatFluxKWPerM2", incidentHeatFluxKWPerM2);
    root.addProperty("isolatableInventoryKg", isolatableInventoryKg);
    root.addProperty("blowdownTimeToTargetS", blowdownTimeToTargetS);
    root.addProperty("bareSteelTimeToCriticalS", bareSteelTimeToCriticalS);
    root.addProperty("blowdownMarginS", blowdownMarginS());
    root.addProperty("blowdownFasterThanFailure", isBlowdownFasterThanFailure());
    root.addProperty("loadBearingStructureExposed", loadBearingStructureExposed);
    root.addProperty("passiveFireProtectionPresent", passiveFireProtectionPresent);
    root.addProperty("nozzlesCanBePlacedAboveEquipment", nozzlesCanBePlacedAboveEquipment);
    root.addProperty("liquidPoolCredible", liquidPoolCredible);
    root.addProperty("waterExtinguishing", isWaterExtinguishing());
    root.addProperty("waterCreditableForEscalationControl", isWaterCreditableForEscalationControl());
    root.addProperty("passiveSubstitutionPermitted", isPassiveSubstitutionPermitted());
    root.addProperty("passiveSubstitutionReason", passiveSubstitutionReason());
    root.addProperty("selectiveProtectionSavingFraction", selectiveProtectionSavingFraction());
    JsonArray measures = new JsonArray();
    List<String> ranked = rankedMeasures();
    for (int i = 0; i < ranked.size(); i++) {
      measures.add(ranked.get(i));
    }
    root.add("rankedMeasures", measures);
    root.addProperty("verdict", verdict());
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }
}
