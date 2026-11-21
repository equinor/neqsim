package neqsim.process.engineering.pid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Opt-in completion of standard auxiliary connections so relief, blowdown, vent and drain proposals appear on the
 * P&amp;ID instead of in the HOLD register.
 *
 * <p>
 * {@link PidPhysicalProjection} never invents a nozzle or a destination: an auxiliary proposal stays HOLD until its
 * source equipment, a new design nozzle, a port role, a design-evidence reference and a destination are supplied. This
 * class supplies those inputs explicitly, using the arrangement normally shown on process P&amp;IDs:
 * </p>
 * <ul>
 * <li>pressure-safety valves, rupture disks and blowdown valves on a dedicated process nozzle of the protected equipment,
 * discharging to the flare header;</li>
 * <li>vents on a dedicated process nozzle, routed to the flare header;</li>
 * <li>drains on a dedicated process nozzle, routed to the closed drain header.</li>
 * </ul>
 *
 * <p>
 * Each completed proposal records {@code designEvidenceReference} starting with {@link #EVIDENCE_PREFIX} and keeps its
 * review-required status, so the drawing and registers show it as a standard connection proposal and never as a
 * reviewed design. Explicit project inputs are never overwritten, and utility-side proposals (cooling-medium valves and
 * meters) are left untouched because their utility system is project data.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PidStandardConnectionProposals {
  /** Prefix of the design-evidence reference written on every completed proposal. */
  public static final String EVIDENCE_PREFIX = "NEQSIM-STANDARD-CONNECTION-PROPOSAL";
  /** Destination used for relief, blowdown and vent connections. */
  public static final String FLARE_HEADER = "FLARE-HEADER";
  /** Destination used for drain connections. */
  public static final String CLOSED_DRAIN_HEADER = "CLOSED-DRAIN-HEADER";

  private PidStandardConnectionProposals() {}

  /**
   * Completes the standard auxiliary connections of a P&amp;ID proposal model in place.
   *
   * <p>
   * Call this before creating {@link PidSimulationBindings} for the model, because bindings record the model state.
   * </p>
   *
   * @param model P&amp;ID proposal model to complete
   * @param process the engineering process the model was synthesized from
   * @return the tags of the completed proposals, in model order
   */
  public static List<String> complete(PidDesignModel model, ProcessSystem process) {
    if (model == null || process == null) {
      throw new IllegalArgumentException("model and process are required");
    }
    Map<String, String> flareConnectors = new LinkedHashMap<String, String>();
    for (PidElement element : model.getElements()) {
      if (element.getType() == PidElementType.OFF_PAGE_CONNECTOR
          && element.getTag().toUpperCase(java.util.Locale.ROOT).contains("FLARE")) {
        flareConnectors.put(element.getEquipmentTag(), element.getTag());
      }
    }
    List<String> completed = new ArrayList<String>();
    for (PidElement element : model.getElements()) {
      String basis = basis(element.getType());
      if (basis == null || !text(element.getAttributes().get("physicalSourceEquipmentTag")).isEmpty()
          || !text(element.getAttributes().get("destinationTag")).isEmpty()) {
        continue;
      }
      ProcessEquipmentInterface owner = process.getUnit(element.getEquipmentTag());
      if (owner == null || owner instanceof StreamInterface || owner instanceof ThrottlingValve
          || owner instanceof neqsim.process.equipment.splitter.Splitter
          || owner instanceof neqsim.process.equipment.mixer.Mixer
          || owner instanceof neqsim.process.equipment.util.Recycle) {
        continue;
      }
      String destination = element.getType() == PidElementType.DRAIN ? CLOSED_DRAIN_HEADER
          : flareConnectors.containsKey(element.getEquipmentTag()) ? flareConnectors.get(element.getEquipmentTag())
              : FLARE_HEADER;
      element.attribute("physicalSourceEquipmentTag", owner.getName())
          .attribute("physicalSourceNozzleTag", "N-" + element.getTag())
          .attribute("physicalSourcePortRole", "PROCESS")
          .attribute("designEvidenceReference", EVIDENCE_PREFIX + ": " + basis)
          .attribute("destinationTag", destination);
      completed.add(element.getTag());
    }
    return Collections.unmodifiableList(completed);
  }

  /**
   * Returns the standard-arrangement basis for an auxiliary proposal type.
   *
   * @param type proposal type
   * @return basis text, or null when the type is not completed by this class
   */
  private static String basis(PidElementType type) {
    switch (type) {
      case SAFETY_RELIEF_VALVE:
      case RUPTURE_DISK:
        return "relief device on a dedicated equipment nozzle discharging to the flare header (API 521 / ISO 23251)";
      case BLOWDOWN_VALVE:
        return "blowdown valve on a dedicated equipment nozzle routed to the flare header (API 521 / ISO 23251)";
      case VENT:
        return "hydrocarbon vent on a dedicated equipment nozzle routed to the flare header";
      case DRAIN:
        return "drain on a dedicated equipment nozzle routed to the closed drain header";
      default:
        return null;
    }
  }

  /**
   * Returns trimmed text for an optional attribute value.
   *
   * @param value attribute value, possibly null
   * @return trimmed text, empty when absent
   */
  private static String text(Object value) {
    return value == null ? "" : value.toString().trim();
  }
}
