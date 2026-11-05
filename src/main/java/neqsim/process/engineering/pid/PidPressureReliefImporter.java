package neqsim.process.engineering.pid;

import java.util.Map;
import com.google.gson.Gson;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.valve.SafetyReliefValve;

/**
 * Imports an actual passive pressure-relief valve into the shared canonical P&amp;ID proposal.
 *
 * <p>
 * The returned graph preserves all existing identities. Mechanical relief creates no PLC, safety function or artificial
 * instrument signal. The declared capacity basis remains review evidence; a Cv-based simulation does not certify
 * installed relieving capacity or adequacy.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidPressureReliefImporter {
  /** Prevents utility construction. */
  private PidPressureReliefImporter() {
  }

  /**
   * Adds or exactly reuses one actual native relief valve without mutating the supplied graph.
   *
   * @param project governed source project
   * @param sourceModel current canonical proposal graph
   * @param bindings current bindings before import
   * @param process configured isolated copy with actual equipment and stream references
   * @param reliefTag actual native relief-valve name and canonical tag
   * @param protectedEquipmentTag actual protected separator name
   * @param teeTag actual one-inlet two-outlet splitter name
   * @param capacityBasisEvidenceId registered capacity/Cv assumption evidence identifier
   * @return copied graph containing the exact passive relief proposal
   * @throws IllegalArgumentException if source, route, capacity basis or existing proposal conflicts
   */
  public static PidDesignModel fromNativeValve(EngineeringProject project, PidDesignModel sourceModel,
      PidSimulationBindings bindings, ProcessSystem process, String reliefTag, String protectedEquipmentTag,
      String teeTag, String capacityBasisEvidenceId) {
    if (bindings == null || process == null) {
      throw new IllegalArgumentException("Current bindings and configured native process are required");
    }
    bindings.assertCurrent(project, sourceModel);
    SafetyReliefValve valve = PidPressureReliefValidator.actualValve(process, reliefTag);
    Map<String, Object> route = PidPressureReliefValidator.route(process, valve, protectedEquipmentTag, teeTag);
    PidPressureReliefValidator.capacityBasis(project, capacityBasisEvidenceId, reliefTag);
    Map<String, Object> configuration = PidPressureReliefValidator.nativeConfiguration(valve);
    PidDesignModel model = PidDesignModel.fromJson(new Gson().toJson(sourceModel.toMap()));
    PidElement element = null;
    for (PidElement candidate : model.getElements()) {
      if (candidate.getTag().equals(reliefTag)) {
        element = candidate;
        if (candidate.getType() != PidElementType.SAFETY_RELIEF_VALVE
            || bindings.resolveEquipment(reliefTag, process) != valve) {
          throw new IllegalArgumentException("Existing relief proposal lacks its exact native equipment binding");
        }
      }
    }
    if (element == null) {
      element = new PidElement("native-relief:" + reliefTag, reliefTag, PidElementType.SAFETY_RELIEF_VALVE)
          .equipment(protectedEquipmentTag).line(String.valueOf(route.get("inletStreamTag")))
          .status(PidProposalStatus.REVIEW_REQUIRED)
          .provenance("NATIVE-PASSIVE-RELIEF-IMPORT-v1",
              "Actual native passive relief branch; controlled relief design and standards review required")
          .attribute("proposalOrigin", "ACTUAL_NATIVE_PASSIVE_PRESSURE_RELIEF")
          .attribute("qualificationStatus", "CAPACITY_AND_CONTROLLED_ENGINEERING_REVIEW_REQUIRED")
          .attribute("automaticSafetyApproval", Boolean.FALSE);
      model.add(element);
    }
    if (!element.getEquipmentTag().equals(protectedEquipmentTag)
        || !element.getLineTag().equals(route.get("inletStreamTag")) || !element.getConnectedElementIds().isEmpty()) {
      throw new IllegalArgumentException("Passive relief requires the exact physical branch and no PLC signals");
    }
    for (PidElement candidate : model.getElements()) {
      if (candidate.getConnectedElementIds().contains(element.getId())) {
        throw new IllegalArgumentException("Passive relief cannot be imported with an incoming PLC signal");
      }
    }
    governed(element, "simulationPressureReliefConfiguration", configuration);
    governed(element, "simulationPressureReliefRoute", route);
    governed(element, "capacityBasisEvidenceId", capacityBasisEvidenceId);
    governed(element, "nativeSourceFingerprint", bindings.toMap().get("sourceFingerprint"));
    return model;
  }

  /**
   * Retains existing governed values and rejects conflicting import instead of silently replacing them.
   *
   * @param element copied canonical proposal
   * @param key required attribute name
   * @param value actual native value
   * @throws IllegalArgumentException if the existing value conflicts
   */
  private static void governed(PidElement element, String key, Object value) {
    Object old = element.getAttributes().get(key);
    if (old != null && !new Gson().toJsonTree(old).equals(new Gson().toJsonTree(value))) {
      throw new IllegalArgumentException("Conflicting governed relief setting: " + key);
    }
    element.attribute(key, value);
  }
}
