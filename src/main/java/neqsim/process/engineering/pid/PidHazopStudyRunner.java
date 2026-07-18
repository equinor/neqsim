package neqsim.process.engineering.pid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.safety.hazid.HAZOPTemplate;
import neqsim.process.safety.hazid.HazopConsequenceAutoPopulator;

/** Runs a traceable HAZOP preparation pass directly from a governed P&amp;ID proposal. */
public final class PidHazopStudyRunner {
  private PidHazopStudyRunner() {
  }

  public static PidHazopStudyReport run(EngineeringProject project, PidDesignModel model) {
    if (project == null || model == null) {
      throw new IllegalArgumentException("project and model must not be null");
    }
    if (!project.getProjectId().equals(model.getProjectId())) {
      throw new IllegalArgumentException("project and P&ID model identities do not match");
    }
    List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
    List<PidCompletenessFinding> findings = new ArrayList<PidCompletenessFinding>();
    Set<String> ids = new LinkedHashSet<String>();
    for (PidElement element : model.getElements()) {
      ids.add(element.getId());
    }
    for (ProcessEquipmentInterface equipment : project.getProcessSystem().getUnitOperations()) {
      if (equipment == null || equipment instanceof Stream) {
        continue;
      }
      List<PidElement> safeguards = model.getElementsForEquipment(equipment.getName());
      if (safeguards.isEmpty()) {
        findings.add(error(equipment.getName(), "No P&ID elements protect or control this HAZOP node."));
        continue;
      }
      nodes.add(node(equipment, safeguards));
    }
    for (PidElement element : model.getElements()) {
      for (String connection : element.getConnectedElementIds()) {
        if (!ids.contains(connection)) {
          findings.add(error(element.getTag(), "Referenced P&ID connection " + connection + " is unresolved."));
        }
      }
    }
    findings.add(new PidCompletenessFinding("HAZOP-WORKSHOP-REQUIRED",
        PidCompletenessFinding.Severity.REVIEW, project.getProjectId(),
        "Auto-populated deviations require a multidisciplinary IEC 61882 workshop and recorded decisions."));
    return new PidHazopStudyReport(project.getProjectId(), nodes, findings);
  }

  private static Map<String, Object> node(ProcessEquipmentInterface equipment,
      List<PidElement> safeguards) {
    HAZOPTemplate seed = new HAZOPTemplate("HAZOP-" + equipment.getName(),
        "Operate " + equipment.getName()
            + " within the approved pressure, temperature, flow, level and composition envelope")
            .generateGrid(HAZOPTemplate.Parameter.FLOW, HAZOPTemplate.Parameter.PRESSURE,
                HAZOPTemplate.Parameter.TEMPERATURE, HAZOPTemplate.Parameter.LEVEL,
                HAZOPTemplate.Parameter.COMPOSITION);
    HAZOPTemplate populated = new HazopConsequenceAutoPopulator().populate(seed);
    List<Map<String, Object>> deviations = new ArrayList<Map<String, Object>>();
    for (HAZOPTemplate.HAZOPDeviation deviation : populated.getDeviations()) {
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("guideWord", deviation.guideWord.name());
      row.put("parameter", deviation.parameter.name());
      row.put("cause", deviation.cause);
      row.put("consequence", deviation.consequence);
      row.put("catalogueSafeguard", deviation.safeguard);
      row.put("recommendation", deviation.recommendation);
      row.put("creditedSafeguardTags", matchingSafeguards(safeguards, deviation.parameter));
      row.put("workshopDecision", "OPEN");
      deviations.add(row);
    }
    Map<String, Object> node = new LinkedHashMap<String, Object>();
    node.put("nodeId", populated.getNodeId());
    node.put("equipmentTag", equipment.getName());
    node.put("equipmentType", equipment.getClass().getSimpleName());
    node.put("designIntent", populated.getDesignIntent());
    node.put("pidElementTags", tags(safeguards));
    node.put("deviations", deviations);
    return node;
  }

  private static List<String> matchingSafeguards(List<PidElement> elements,
      HAZOPTemplate.Parameter parameter) {
    List<String> tags = new ArrayList<String>();
    String variable = parameter.name();
    for (PidElement element : elements) {
      Object measured = element.getAttributes().get("measuredVariable");
      Object purpose = element.getAttributes().get("proposalPurpose");
      if ((measured != null && variable.equals(String.valueOf(measured)))
          || (purpose != null && String.valueOf(purpose).contains(variable))) {
        tags.add(element.getTag());
      }
    }
    return tags;
  }

  private static List<String> tags(List<PidElement> elements) {
    List<String> tags = new ArrayList<String>();
    for (PidElement element : elements) {
      tags.add(element.getTag());
    }
    return tags;
  }

  private static PidCompletenessFinding error(String subject, String message) {
    return new PidCompletenessFinding("HAZOP-PID-NODE-INCOMPLETE",
        PidCompletenessFinding.Severity.ERROR, subject, message);
  }
}
