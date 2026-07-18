package neqsim.process.engineering.pid.rules;

import java.util.List;
import java.util.Locale;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.pid.PidDesignContext;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/** Shared deterministic construction and requirement-trace helpers for standard P&amp;ID rules. */
final class PidRuleSupport {
  private PidRuleSupport() {
  }

  static PidElement element(PidDesignContext context, ProcessEquipmentInterface equipment,
      String functionCode, String purpose, PidElementType type, String ruleId, String description,
      String rationale) {
    String stableKey = equipment.getName() + "-" + purpose;
    String tag = context.getTagAllocator().allocate(functionCode, stableKey);
    return new PidElement("pid:" + normalize(tag), tag, type).equipment(equipment.getName())
        .description(description).provenance(ruleId, rationale).standard("ANSI/ISA-5.1:2024")
        .attribute("proposalPurpose", purpose).attribute("approvalStatus", "REVIEW_REQUIRED");
  }

  static void trace(PidElement element, PidDesignContext context,
      ProcessEquipmentInterface equipment, String... tokens) {
    List<EngineeringRequirement> requirements =
        context.requirements(equipment.getName(), null);
    for (EngineeringRequirement requirement : requirements) {
      if (matches(requirement, tokens)) {
        element.requirement(requirement.getId());
        for (String standard : requirement.getStandardReferences()) {
          element.standard(standard);
        }
      }
    }
  }

  static PidElement onInlet(PidElement element, ProcessEquipmentInterface equipment) {
    StreamInterface stream = first(equipment.getInletStreams());
    return stream == null ? element : element.line(stream.getName());
  }

  static PidElement onOutlet(PidElement element, ProcessEquipmentInterface equipment) {
    StreamInterface stream = first(equipment.getOutletStreams());
    return stream == null ? element : element.line(stream.getName());
  }

  private static StreamInterface first(List<StreamInterface> streams) {
    return streams == null || streams.isEmpty() ? null : streams.get(0);
  }

  private static boolean matches(EngineeringRequirement requirement, String... tokens) {
    if (tokens == null || tokens.length == 0) {
      return true;
    }
    String searchable = (requirement.getId() + " " + requirement.getTitle()).toUpperCase(Locale.ROOT);
    for (String token : tokens) {
      if (token != null && searchable.contains(token.toUpperCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
  }
}
