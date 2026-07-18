package neqsim.process.engineering.impact;

import java.io.Serializable;
import neqsim.process.engineering.model.EngineeringEdge;

/** Propagation direction and required response for one engineering relationship kind. */
public final class ImpactAnalysisRule implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Direction {
    SOURCE_TO_TARGET, TARGET_TO_SOURCE, BIDIRECTIONAL
  }

  private final EngineeringEdge.Kind relationship;
  private final Direction direction;
  private final ImpactAction action;

  public ImpactAnalysisRule(EngineeringEdge.Kind relationship, Direction direction, ImpactAction action) {
    if (relationship == null || direction == null || action == null) {
      throw new IllegalArgumentException("relationship, direction and action must not be null");
    }
    this.relationship = relationship;
    this.direction = direction;
    this.action = action;
  }

  public EngineeringEdge.Kind getRelationship() {
    return relationship;
  }

  public Direction getDirection() {
    return direction;
  }

  public ImpactAction getAction() {
    return action;
  }
}
