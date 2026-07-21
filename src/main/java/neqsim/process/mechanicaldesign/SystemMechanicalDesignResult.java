package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable structured result from a whole-process mechanical design calculation.
 *
 * <p>
 * Aggregate getters on {@link SystemMechanicalDesign} contain only successfully calculated equipment. Call
 * {@link #isComplete()} before treating those aggregates as whole-system values.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class SystemMechanicalDesignResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final long revision;
  private final SystemDesignExecutionMode executionMode;
  private final List<EquipmentDesignOutcome> equipmentOutcomes;
  private final int calculatedCount;
  private final int failedCount;
  private final int skippedCount;

  /**
   * Create an immutable system result.
   *
   * @param revision persistent calculation-attempt revision
   * @param executionMode requested failure handling mode
   * @param equipmentOutcomes outcomes in process order
   */
  public SystemMechanicalDesignResult(long revision, SystemDesignExecutionMode executionMode,
      List<EquipmentDesignOutcome> equipmentOutcomes) {
    this.revision = revision;
    this.executionMode = executionMode;
    this.equipmentOutcomes = Collections.unmodifiableList(new ArrayList<EquipmentDesignOutcome>(equipmentOutcomes));

    int successful = 0;
    int failed = 0;
    int skipped = 0;
    for (EquipmentDesignOutcome outcome : equipmentOutcomes) {
      if (outcome.getStatus() == EquipmentDesignOutcome.Status.CALCULATED) {
        successful++;
      } else if (outcome.getStatus() == EquipmentDesignOutcome.Status.FAILED) {
        failed++;
      } else if (outcome.getStatus() == EquipmentDesignOutcome.Status.SKIPPED) {
        skipped++;
      }
    }
    this.calculatedCount = successful;
    this.failedCount = failed;
    this.skippedCount = skipped;
  }

  /** @return persistent calculation-attempt revision */
  public long getRevision() {
    return revision;
  }

  /** @return requested execution mode */
  public SystemDesignExecutionMode getExecutionMode() {
    return executionMode;
  }

  /** @return immutable outcomes in process order */
  public List<EquipmentDesignOutcome> getEquipmentOutcomes() {
    return equipmentOutcomes;
  }

  /** @return number of successfully calculated equipment items */
  public int getCalculatedCount() {
    return calculatedCount;
  }

  /** @return number of failed equipment items */
  public int getFailedCount() {
    return failedCount;
  }

  /** @return number of equipment items skipped by fail-fast execution */
  public int getSkippedCount() {
    return skippedCount;
  }

  /**
   * Check whether every process item was successfully calculated.
   *
   * @return {@code true} when there are no failures or skipped items
   */
  public boolean isComplete() {
    return failedCount == 0 && skippedCount == 0;
  }

  /** @return {@code true} when at least one equipment calculation failed */
  public boolean hasFailures() {
    return failedCount > 0;
  }
}
