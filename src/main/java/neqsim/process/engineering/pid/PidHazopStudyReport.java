package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/** HAZOP preparation report whose nodes and safeguards are derived from one governed P&amp;ID model. */
public final class PidHazopStudyReport implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final List<Map<String, Object>> nodes;
  private final List<PidCompletenessFinding> findings;

  PidHazopStudyReport(String projectId, List<Map<String, Object>> nodes,
      List<PidCompletenessFinding> findings) {
    this.projectId = projectId;
    this.nodes = new ArrayList<Map<String, Object>>(nodes);
    this.findings = new ArrayList<PidCompletenessFinding>(findings);
  }

  public boolean isReadyForHazopWorkshop() {
    for (PidCompletenessFinding finding : findings) {
      if (finding.getSeverity() == PidCompletenessFinding.Severity.ERROR) {
        return false;
      }
    }
    return !nodes.isEmpty();
  }

  public List<Map<String, Object>> getNodes() {
    return Collections.unmodifiableList(nodes);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> serializedFindings = new ArrayList<Map<String, Object>>();
    for (PidCompletenessFinding finding : findings) {
      serializedFindings.add(finding.toMap());
    }
    value.put("schemaVersion", "neqsim_pid_hazop_study.v1");
    value.put("projectId", projectId);
    value.put("studyStatus", "PREPARATION_FOR_MULTIDISCIPLINE_WORKSHOP");
    value.put("readyForHazopWorkshop", Boolean.valueOf(isReadyForHazopWorkshop()));
    value.put("nodes", nodes);
    value.put("findings", serializedFindings);
    value.put("standards", java.util.Arrays.asList("IEC 61882", "NORSOK Z-013", "IEC 61511"));
    value.put("fitnessForConstruction", Boolean.FALSE);
    return value;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }
}
