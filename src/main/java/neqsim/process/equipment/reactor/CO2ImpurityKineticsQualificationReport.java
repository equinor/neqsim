package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of evaluating the evidence registered for a CO2 impurity reactor.
 *
 * <p>
 * The report explains whether every material-selected R1-R8 parameterization is qualified at one
 * temperature and pressure. It contains no kinetic parameters and does not execute chemistry.
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public final class CO2ImpurityKineticsQualificationReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Qualification result for one required reaction parameterization. */
  public enum QualificationState {
    /** No evidence metadata is registered. */
    MISSING,
    /** Evidence metadata exists but is not independently validated. */
    NOT_VALIDATED,
    /** Validated evidence exists but the requested state is outside its declared range. */
    OUT_OF_RANGE,
    /** Validated evidence covers the requested state. */
    QUALIFIED
  }

  /** Immutable qualification result for one required reaction identifier. */
  public static final class Entry implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String reactionId;
    private final QualificationState state;

    Entry(String reactionId, QualificationState state) {
      if (reactionId == null || reactionId.trim().isEmpty()) {
        throw new IllegalArgumentException("reaction identifier cannot be empty");
      }
      if (state == null) {
        throw new IllegalArgumentException("qualification state cannot be null");
      }
      this.reactionId = reactionId;
      this.state = state;
    }

    /** @return normalized reaction parameter identifier. */
    public String getReactionId() {
      return reactionId;
    }

    /** @return evidence qualification state at the evaluated temperature and pressure. */
    public QualificationState getState() {
      return state;
    }

    /** @return true only when validated evidence covers the evaluated state. */
    public boolean isQualified() {
      return state == QualificationState.QUALIFIED;
    }
  }

  private final double temperatureK;
  private final double pressureBara;
  private final String material;
  private final List<Entry> entries;

  CO2ImpurityKineticsQualificationReport(double temperatureK, double pressureBara, String material,
      List<Entry> entries) {
    this.temperatureK = temperatureK;
    this.pressureBara = pressureBara;
    this.material = material;
    this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
  }

  /** @return evaluated temperature [K]. */
  public double getTemperatureK() {
    return temperatureK;
  }

  /** @return evaluated absolute pressure [bara]. */
  public double getPressureBara() {
    return pressureBara;
  }

  /** @return reactor material selection used to choose the R8 family. */
  public String getMaterial() {
    return material;
  }

  /** @return immutable source-ordered qualification entries. */
  public List<Entry> getEntries() {
    return entries;
  }

  /** @return true only when every required parameterization is qualified. */
  public boolean isQualified() {
    for (Entry entry : entries) {
      if (!entry.isQualified()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Return identifiers that block qualified execution.
   *
   * @return new source-ordered array of missing, unvalidated, or out-of-range identifiers
   */
  public String[] getBlockedReactionIds() {
    List<String> blocked = new ArrayList<>();
    for (Entry entry : entries) {
      if (!entry.isQualified()) {
        blocked.add(entry.getReactionId());
      }
    }
    return blocked.toArray(new String[blocked.size()]);
  }
}
