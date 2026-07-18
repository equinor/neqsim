package neqsim.process.engineering.pid;

import java.util.List;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;

/** Applies an ordered rule catalog to a governed engineering project. */
public final class PidDesignSynthesizer {
  private PidDesignSynthesizer() {
  }

  public static PidDesignModel synthesize(EngineeringProject project, PidDesignBasis basis,
      PidRuleCatalog catalog) {
    if (project == null || basis == null || catalog == null) {
      throw new IllegalArgumentException("project, basis and catalog must not be null");
    }
    PidDesignContext context = new PidDesignContext(project, basis);
    PidDesignModel model = new PidDesignModel(project.getProjectId(), basis.getProfileId());
    for (ProcessEquipmentInterface equipment : project.getProcessSystem().getUnitOperations()) {
      if (equipment == null || equipment instanceof Stream) {
        continue;
      }
      for (PidDesignRule rule : catalog.getRules()) {
        if (rule.supports(equipment)) {
          List<PidElement> proposals = rule.propose(context, equipment);
          if (proposals != null) {
            for (PidElement proposal : proposals) {
              model.add(proposal);
            }
          }
        }
      }
    }
    return model;
  }
}
