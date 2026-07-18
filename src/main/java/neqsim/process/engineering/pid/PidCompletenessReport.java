package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/** Machine-readable structural and governance assessment of a P&amp;ID design model. */
public final class PidCompletenessReport implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final int elementCount;
  private final List<PidCompletenessFinding> findings;

  PidCompletenessReport(String projectId, int elementCount, List<PidCompletenessFinding> findings) {
    this.projectId = projectId;
    this.elementCount = elementCount;
    this.findings = new ArrayList<PidCompletenessFinding>(findings);
  }

  public boolean isStructurallyComplete() {
    return !hasSeverity(PidCompletenessFinding.Severity.ERROR);
  }

  public boolean isReadyForApproval() {
    return isStructurallyComplete() && !hasSeverity(PidCompletenessFinding.Severity.REVIEW);
  }

  private boolean hasSeverity(PidCompletenessFinding.Severity severity) {
    for (PidCompletenessFinding finding : findings) {
      if (finding.getSeverity() == severity) {
        return true;
      }
    }
    return false;
  }

  public List<PidCompletenessFinding> getFindings() {
    return Collections.unmodifiableList(findings);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> serialized = new ArrayList<Map<String, Object>>();
    for (PidCompletenessFinding finding : findings) {
      serialized.add(finding.toMap());
    }
    value.put("schemaVersion", "neqsim_pid_completeness.v1");
    value.put("projectId", projectId);
    value.put("elementCount", Integer.valueOf(elementCount));
    value.put("structurallyComplete", Boolean.valueOf(isStructurallyComplete()));
    value.put("readyForApproval", Boolean.valueOf(isReadyForApproval()));
    value.put("fitnessForConstruction", Boolean.FALSE);
    value.put("findings", serialized);
    return value;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }
}
