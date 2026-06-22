package neqsim.process.allocation;

import java.io.Serializable;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * A tagged production source (well, template, satellite feed, commingled inlet, ...) injected into the allocation proxy
 * network.
 *
 * <p>
 * Each source corresponds to a feed stream entering the process. Its own per-component molar flow is used as the
 * injection vector {@code x_k} when the linear network is solved, so the rigorous base-case throughput is partitioned
 * among sources by superposition through the frozen split factors.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class AllocationSource implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** User-facing name of the source. */
  private final String name;

  /** Feed stream that carries the source composition and flow into the process. */
  private final StreamInterface feedStream;

  /**
   * Creates a production source.
   *
   * @param name the source name; must be non-null and unique within an allocation
   * @param feedStream the feed stream entering the process for this source; must be non-null
   */
  public AllocationSource(String name, StreamInterface feedStream) {
    this.name = name;
    this.feedStream = feedStream;
  }

  /**
   * Gets the source name.
   *
   * @return the source name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the feed stream that injects this source into the process.
   *
   * @return the feed stream
   */
  public StreamInterface getFeedStream() {
    return feedStream;
  }
}
