package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import neqsim.process.fielddevelopment.concept.FieldConcept;

/**
 * Connects a screening-level {@link FieldConcept} to an executable lifecycle model and assumptions.
 *
 * @author ESOL
 * @version 1.0
 */
public final class FieldLifecycleConcept implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final FieldConcept fieldConcept;
  private final FieldLifecycleModel model;
  private final FieldLifecycleConfiguration configuration;

  /**
   * Creates an executable field concept.
   *
   * @param fieldConcept screening concept and design basis
   * @param model assembled reservoir-to-export NeqSim model
   * @param configuration lifecycle and economic assumptions
   */
  public FieldLifecycleConcept(FieldConcept fieldConcept, FieldLifecycleModel model,
      FieldLifecycleConfiguration configuration) {
    if (fieldConcept == null || model == null || configuration == null) {
      throw new IllegalArgumentException("fieldConcept, model and configuration are required");
    }
    this.fieldConcept = fieldConcept;
    this.model = model;
    this.configuration = configuration;
  }

  /** Returns the concept name from the screening design basis. */
  public String getName() {
    return fieldConcept.getName();
  }

  /** Returns the screening-level field concept and design basis. */
  public FieldConcept getFieldConcept() {
    return fieldConcept;
  }

  /** Returns the mutable executable reservoir-to-market model. */
  public FieldLifecycleModel getModel() {
    return model;
  }

  /** Returns the lifecycle and economic assumptions. */
  public FieldLifecycleConfiguration getConfiguration() {
    return configuration;
  }
}
