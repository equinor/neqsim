package neqsim.process.engineering.pid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Validates references, minimum coverage and approval gates for generated P&amp;ID proposals. */
public final class PidCompletenessValidator {
  private PidCompletenessValidator() {
  }

  public static PidCompletenessReport validate(PidDesignModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    List<PidCompletenessFinding> findings = new ArrayList<PidCompletenessFinding>();
    Set<String> ids = new LinkedHashSet<String>();
    for (PidElement element : model.getElements()) {
      ids.add(element.getId());
    }
    for (PidElement element : model.getElements()) {
      for (String connectedId : element.getConnectedElementIds()) {
        if (!ids.contains(connectedId)) {
          findings.add(new PidCompletenessFinding("PID-REFERENCE-MISSING", PidCompletenessFinding.Severity.ERROR,
              element.getTag(), "Connected proposal " + connectedId + " does not exist in the design model."));
        }
      }
    }
    requireType(model, PidElementType.MEASUREMENT, findings);
    requireType(model, PidElementType.CONTROLLER, findings);
    requireType(model, PidElementType.CONTROL_VALVE, findings);
    requireType(model, PidElementType.TRIP, findings);
    requireType(model, PidElementType.SHUTDOWN_VALVE, findings);
    if (!model.getElementsByType(PidElementType.SAFETY_RELIEF_VALVE).isEmpty()) {
      for (PidElement relief : model.getElementsByType(PidElementType.SAFETY_RELIEF_VALVE)) {
        if (!relief.getAttributes().containsKey("scenarioCount")) {
          findings.add(new PidCompletenessFinding("PID-RELIEF-EVIDENCE-MISSING", PidCompletenessFinding.Severity.ERROR,
              relief.getTag(), "Relief proposal has no linked overpressure scenario evidence."));
        }
      }
    }
    findings.add(new PidCompletenessFinding("PID-DISCIPLINE-APPROVAL-REQUIRED", PidCompletenessFinding.Severity.REVIEW,
        model.getProjectId(),
        "Generated proposals require process, control, technical-safety and operations approval."));
    findings.add(new PidCompletenessFinding("PID-HAZOP-LOPA-REQUIRED", PidCompletenessFinding.Severity.REVIEW,
        model.getProjectId(),
        "Trip set points, voting, SIL targets and valve failure positions require HAZOP/LOPA review."));
    return new PidCompletenessReport(model.getProjectId(), model.getElements().size(), findings);
  }

  private static void requireType(PidDesignModel model, PidElementType type, List<PidCompletenessFinding> findings) {
    if (model.getElementsByType(type).isEmpty()) {
      findings.add(new PidCompletenessFinding("PID-COVERAGE-" + type.name(), PidCompletenessFinding.Severity.ERROR,
          model.getProjectId(), "Complete proposal profile contains no " + type.name() + " element."));
    }
  }
}
