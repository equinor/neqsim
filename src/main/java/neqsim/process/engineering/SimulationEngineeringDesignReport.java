package neqsim.process.engineering;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Machine-readable result of applying NeqSim simulation and design calculators to an engineering project.
 *
 * <p>
 * The report deliberately distinguishes calculated screening results from approved engineering data. Its JSON is a
 * design handoff: it records methods, assumptions, data gaps and review status without promoting an automated result to
 * a certified data-sheet or safety-requirements value.
 * </p>
 */
public final class SimulationEngineeringDesignReport {
  private final JsonObject document;
  private final int calculatedEquipmentCount;
  private final int reliefStudyCount;
  private final int blowdownFlareStudyCount;

  SimulationEngineeringDesignReport(JsonObject document, int calculatedEquipmentCount, int reliefStudyCount,
      int blowdownFlareStudyCount) {
    this.document = document.deepCopy();
    this.calculatedEquipmentCount = calculatedEquipmentCount;
    this.reliefStudyCount = reliefStudyCount;
    this.blowdownFlareStudyCount = blowdownFlareStudyCount;
  }

  /** @return number of equipment mechanical-design calculations completed */
  public int getCalculatedEquipmentCount() {
    return calculatedEquipmentCount;
  }

  /** @return number of explicit or automatically screened relief studies evaluated */
  public int getReliefStudyCount() {
    return reliefStudyCount;
  }

  /** @return number of readiness-gated blowdown/flare studies processed */
  public int getBlowdownFlareStudyCount() {
    return blowdownFlareStudyCount;
  }

  /** @return defensive copy of the JSON document */
  public JsonObject toJsonObject() {
    return document.deepCopy();
  }

  /** @return pretty-printed engineering calculation handoff */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().serializeSpecialFloatingPointValues().create()
        .toJson(document);
  }
}
