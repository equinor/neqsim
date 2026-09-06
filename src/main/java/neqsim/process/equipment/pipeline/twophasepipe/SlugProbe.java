package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Observes accepted slug-interface movements across one fixed axial position.
 *
 * <p>
 * Events describe changes in occupancy of the union of the liquid bodies during an accepted step. Interior endpoints of
 * overlapping bodies are suppressed. Events use the tracker's piecewise linear motion before merging and removal, and
 * do not interpret instantaneous marker creation or geometry changes as motion. This probe does not change flow, marker
 * geometry, or physical inventories. Histories are bounded; callers can drain events after each output interval and
 * must inspect the dropped-event count before treating a history as complete.
 * </p>
 */
public final class SlugProbe implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final int DEFAULT_CAPACITY = 10000;

  /** Geometric interface crossing the probe. */
  public enum InterfaceType {
    /** Downstream endpoint of the tracked liquid body. */
    FRONT,
    /** Upstream endpoint of the tracked liquid body. */
    TAIL
  }

  /** Immutable observation of a single interface crossing. */
  public static final class Crossing implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int slugId;
    private final double time;
    private final InterfaceType interfaceType;
    private final int direction;
    private final double velocity;
    private final double slugLength;

    private Crossing(int slugId, double time, InterfaceType interfaceType, double velocity, double slugLength) {
      this.slugId = slugId;
      this.time = time;
      this.interfaceType = interfaceType;
      this.direction = velocity > 0.0 ? 1 : -1;
      this.velocity = velocity;
      this.slugLength = slugLength;
    }

    /** @return marker identity before any subsequent merge or removal */
    public int getSlugId() {
      return slugId;
    }

    /** @return linearly interpolated crossing time on the tracker clock, in seconds */
    public double getTime() {
      return time;
    }

    /** @return front or tail of the liquid body */
    public InterfaceType getInterfaceType() {
      return interfaceType;
    }

    /** @return +1 for increasing axial position and -1 for decreasing axial position */
    public int getDirection() {
      return direction;
    }

    /** @return signed interface displacement divided by accepted step duration, in m/s */
    public double getVelocity() {
      return velocity;
    }

    /** @return absolute interface speed in m/s */
    public double getSpeed() {
      return Math.abs(velocity);
    }

    /** @return connected liquid-union length interpolated at this crossing, in metres */
    public double getSlugLength() {
      return slugLength;
    }
  }

  private final double position;
  private final int capacity;
  private final Deque<Crossing> events = new ArrayDeque<>();
  private long droppedEventCount;
  private transient List<Motion> stepMotions;

  /**
   * Create a probe retaining at most 10000 events between drains.
   *
   * @param position fixed axial position in metres
   */
  public SlugProbe(double position) {
    this(position, DEFAULT_CAPACITY);
  }

  /**
   * Create a probe with a bounded retained history.
   *
   * @param position fixed axial position in metres
   * @param capacity maximum number of retained events, strictly positive
   */
  public SlugProbe(double position, int capacity) {
    if (!Double.isFinite(position) || capacity <= 0) {
      throw new IllegalArgumentException("Probe position must be finite and event capacity must be positive");
    }
    this.position = position;
    this.capacity = capacity;
  }

  /** @return fixed axial position in metres */
  public double getPosition() {
    return position;
  }

  /** @return maximum number of retained events */
  public int getCapacity() {
    return capacity;
  }

  /** @return count of overwritten events since construction or the last clear */
  public long getDroppedEventCount() {
    return droppedEventCount;
  }

  /** @return immutable, chronologically sorted snapshot of the retained events */
  public List<Crossing> getEvents() {
    List<Crossing> snapshot = new ArrayList<>(events);
    sortCrossings(snapshot);
    return Collections.unmodifiableList(snapshot);
  }

  private static void sortCrossings(List<Crossing> snapshot) {
    Collections.sort(snapshot, new Comparator<Crossing>() {
      @Override
      public int compare(Crossing first, Crossing second) {
        int timeOrder = Double.compare(first.time, second.time);
        if (timeOrder != 0) {
          return timeOrder;
        }
        int idOrder = Integer.compare(first.slugId, second.slugId);
        return idOrder != 0 ? idOrder : first.interfaceType.compareTo(second.interfaceType);
      }
    });
  }

  /**
   * Return and remove retained observations without resetting the dropped-event diagnostic.
   *
   * @return immutable events in chronological order
   */
  public List<Crossing> drainEvents() {
    List<Crossing> snapshot = getEvents();
    events.clear();
    return snapshot;
  }

  /** Clear retained events and reset the dropped-event counter. */
  public void clearEvents() {
    events.clear();
    droppedEventCount = 0;
  }

  /** @return independent probe with the same position/capacity and an empty history */
  public SlugProbe copyConfiguration() {
    return new SlugProbe(position, capacity);
  }

  /** Collect all individual trajectories so overlapping bodies can be observed as their liquid union. */
  void beginStep() {
    stepMotions = new ArrayList<>();
  }

  /** Retain only physical union occupancy changes before the tracker merges or removes markers. */
  void endStep() {
    if (stepMotions != null) {
      List<Motion> completed = stepMotions;
      stepMotions = null;
      observeMotions(completed);
    }
  }

  /** Record only continuous movement; tracker creation and merge paths never call this method. */
  void recordMotion(int slugId, double startTime, double dt, double previousFront, double previousTail,
      double nextFront, double nextTail) {
    if (!Double.isFinite(startTime) || !Double.isFinite(dt) || dt <= 0.0 || !Double.isFinite(previousFront)
        || !Double.isFinite(previousTail) || !Double.isFinite(nextFront) || !Double.isFinite(nextTail)) {
      return;
    }
    Motion motion = new Motion(slugId, startTime, dt, previousFront, previousTail, nextFront, nextTail);
    if (stepMotions != null) {
      stepMotions.add(motion);
    } else {
      observeMotions(Collections.singletonList(motion));
    }
  }

  private void candidateCrossing(List<Crossing> candidates, int slugId, InterfaceType type, double startTime, double dt,
      double previous, double next, double previousFront, double previousTail, double nextFront, double nextTail) {
    // Exclude the beginning and include the end: reaching a probe exactly is recorded once. A newly born endpoint
    // already on/beyond the probe does not invent an arrival, and departing an endpoint already on it is not repeated.
    if (!(previous < position && next >= position || previous > position && next <= position)) {
      return;
    }
    double fraction = (position - previous) / (next - previous);
    double front = previousFront + fraction * (nextFront - previousFront);
    double tail = previousTail + fraction * (nextTail - previousTail);
    candidates.add(
        new Crossing(slugId, startTime + fraction * dt, type, (next - previous) / dt, Math.max(0.0, front - tail)));
  }

  private void observeMotions(List<Motion> motions) {
    List<Crossing> candidates = new ArrayList<>();
    for (Motion motion : motions) {
      candidateCrossing(candidates, motion.id, InterfaceType.FRONT, motion.start, motion.dt, motion.front0,
          motion.front1, motion.front0, motion.tail0, motion.front1, motion.tail1);
      candidateCrossing(candidates, motion.id, InterfaceType.TAIL, motion.start, motion.dt, motion.tail0, motion.tail1,
          motion.front0, motion.tail0, motion.front1, motion.tail1);
    }
    sortCrossings(candidates);
    for (int index = 0; index < candidates.size();) {
      Crossing first = candidates.get(index);
      int end = index + 1;
      while (end < candidates.size() && Math.abs(candidates.get(end).time - first.time) <= roundoff(first.time)) {
        end++;
      }
      boolean before = false;
      boolean after = false;
      for (Motion motion : motions) {
        before |= coversProbe(motion, first.time, false);
        after |= coversProbe(motion, first.time, true);
      }
      if (before != after) {
        for (int candidate = index; candidate < end; candidate++) {
          Crossing crossing = candidates.get(candidate);
          boolean entering = crossing.interfaceType == InterfaceType.FRONT ? crossing.direction > 0
              : crossing.direction < 0;
          if (entering == after) {
            retain(new Crossing(crossing.slugId, crossing.time, crossing.interfaceType, crossing.velocity,
                unionLength(motions, crossing.time)));
            break;
          }
        }
      }
      index = end;
    }
  }

  private boolean coversProbe(Motion motion, double time, boolean after) {
    double fraction = (time - motion.start) / motion.dt;
    double front = motion.front0 + fraction * (motion.front1 - motion.front0);
    double tail = motion.tail0 + fraction * (motion.tail1 - motion.tail0);
    return side(front, motion.front1 - motion.front0, after) > 0 && side(tail, motion.tail1 - motion.tail0, after) < 0;
  }

  /** Resolve a coordinate at a crossing by its one-sided motion, without a time-sized numerical perturbation. */
  private int side(double coordinate, double displacement, boolean after) {
    double difference = coordinate - position;
    if (Math.abs(difference) > Math.max(roundoff(coordinate), roundoff(position))) {
      return difference > 0.0 ? 1 : -1;
    }
    return displacement == 0.0 ? 0 : Double.compare(after ? displacement : -displacement, 0.0);
  }

  private static double roundoff(double value) {
    return 8.0 * Math.ulp(Math.abs(value));
  }

  /** Length of the connected liquid union containing the observed interface. */
  private double unionLength(List<Motion> motions, double time) {
    List<double[]> intervals = new ArrayList<>();
    for (Motion motion : motions) {
      double fraction = (time - motion.start) / motion.dt;
      double front = motion.front0 + fraction * (motion.front1 - motion.front0);
      double tail = motion.tail0 + fraction * (motion.tail1 - motion.tail0);
      if (front >= tail) {
        intervals.add(new double[] { tail, front });
      }
    }
    Collections.sort(intervals, new Comparator<double[]>() {
      @Override
      public int compare(double[] first, double[] second) {
        return Double.compare(first[0], second[0]);
      }
    });
    double left = Double.NaN;
    double right = Double.NaN;
    for (double[] interval : intervals) {
      if (Double.isNaN(left)) {
        left = interval[0];
        right = interval[1];
      } else if (interval[0] <= right + roundoff(right)) {
        right = Math.max(right, interval[1]);
      } else {
        if (position >= left - roundoff(left) && position <= right + roundoff(right)) {
          return right - left;
        }
        left = interval[0];
        right = interval[1];
      }
    }
    return !Double.isNaN(left) && position >= left - roundoff(left) && position <= right + roundoff(right)
        ? right - left
        : 0.0;
  }

  private void retain(Crossing crossing) {
    if (events.size() == capacity) {
      events.removeFirst();
      droppedEventCount++;
    }
    events.addLast(crossing);
  }

  private static final class Motion {
    private final int id;
    private final double start;
    private final double dt;
    private final double front0;
    private final double tail0;
    private final double front1;
    private final double tail1;

    private Motion(int id, double start, double dt, double front0, double tail0, double front1, double tail1) {
      this.id = id;
      this.start = start;
      this.dt = dt;
      this.front0 = front0;
      this.tail0 = tail0;
      this.front1 = front1;
      this.tail1 = tail1;
    }
  }
}
