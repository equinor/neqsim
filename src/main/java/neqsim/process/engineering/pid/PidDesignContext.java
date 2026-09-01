package neqsim.process.engineering.pid;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;

/** Shared project, policy and tag-allocation context supplied to P&amp;ID rules. */
public final class PidDesignContext {
  private final EngineeringProject project;
  private final PidDesignBasis basis;
  private final PidTagAllocator tagAllocator;

  public PidDesignContext(EngineeringProject project, PidDesignBasis basis) {
    if (project == null || basis == null) {
      throw new IllegalArgumentException("project and basis must not be null");
    }
    this.project = project;
    this.basis = basis;
    this.tagAllocator = new PidTagAllocator(basis);
  }

  public List<EngineeringRequirement> requirements(String equipmentTag, EngineeringRequirement.Type type) {
    List<EngineeringRequirement> matches = new ArrayList<EngineeringRequirement>();
    for (EngineeringRequirement requirement : project.getRequirementsForEquipment(equipmentTag)) {
      if (type == null || requirement.getType() == type) {
        matches.add(requirement);
      }
    }
    return matches;
  }

  public EngineeringProject getProject() {
    return project;
  }

  public PidDesignBasis getBasis() {
    return basis;
  }

  public PidTagAllocator getTagAllocator() {
    return tagAllocator;
  }
}
