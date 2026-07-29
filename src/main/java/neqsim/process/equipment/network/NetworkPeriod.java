package neqsim.process.equipment.network;

import java.io.Serializable;
import java.time.Instant;

/**
 * Explicit half-open planning period [start, end).
 */
public class NetworkPeriod implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final int index;
  private final String start;
  private final String end;
  private final double durationSeconds;

  /**
   * Create a period.
   *
   * @param index zero-based index
   * @param startIso ISO-8601 start instant
   * @param durationSeconds duration in seconds
   */
  public NetworkPeriod(int index, String startIso, double durationSeconds) {
    if (!(durationSeconds > 0.0)) {
      throw new IllegalArgumentException("Period duration must be positive");
    }
    Instant startInstant = Instant.parse(startIso);
    this.index = index;
    this.start = startInstant.toString();
    this.end = startInstant.plusMillis(Math.round(durationSeconds * 1000.0)).toString();
    this.durationSeconds = durationSeconds;
  }

  /** @return zero-based period index */
  public int getIndex() {
    return index;
  }

  /** @return inclusive ISO-8601 start */
  public String getStart() {
    return start;
  }

  /** @return exclusive ISO-8601 end */
  public String getEnd() {
    return end;
  }

  /** @return period duration in seconds */
  public double getDurationSeconds() {
    return durationSeconds;
  }
}
