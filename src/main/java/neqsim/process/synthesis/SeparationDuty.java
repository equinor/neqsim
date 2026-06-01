package neqsim.process.synthesis;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Declarative description of a separation problem handed to
 * {@link FlowsheetSynthesisEngine#proposeAndBuild(SeparationDuty)}.
 *
 * <p>
 * A duty consists of:
 * </p>
 * <ul>
 * <li>a fully-specified feed {@link StreamInterface} (composition, flow, T, P),</li>
 * <li>a set of <em>top-product</em> purity requirements (component name → minimum mole fraction
 * required in the lightest product stream), and</li>
 * <li>a set of <em>bottom-product</em> purity requirements (component name → minimum mole fraction
 * required in the heaviest product stream).</li>
 * </ul>
 *
 * <p>
 * Either map may be empty. When both are empty the synthesis engine returns the trivial
 * single-stage flash as a sanity check.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SeparationDuty implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final StreamInterface feed;
  private final Map<String, Double> topProductSpecs;
  private final Map<String, Double> bottomProductSpecs;
  private final double operatingPressureBara;
  private final String name;

  /**
   * Creates a separation duty.
   *
   * @param name short identifier used in the generated flowsheet
   * @param feed the feed stream; must have been {@code run()} so that flow and composition are
   *        valid
   * @param topProductSpecs map from component name to required mole fraction in the top product
   *        (gas/overhead); may be null or empty
   * @param bottomProductSpecs map from component name to required mole fraction in the bottom
   *        product (liquid/bottoms); may be null or empty
   * @param operatingPressureBara operating pressure for the separation stage in bara; pass
   *        {@code Double.NaN} to use the feed pressure
   */
  public SeparationDuty(String name, StreamInterface feed, Map<String, Double> topProductSpecs,
      Map<String, Double> bottomProductSpecs, double operatingPressureBara) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (feed == null) {
      throw new IllegalArgumentException("feed must not be null");
    }
    this.name = name;
    this.feed = feed;
    this.topProductSpecs = topProductSpecs == null ? Collections.<String, Double>emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<String, Double>(topProductSpecs));
    this.bottomProductSpecs = bottomProductSpecs == null ? Collections.<String, Double>emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<String, Double>(bottomProductSpecs));
    this.operatingPressureBara = operatingPressureBara;
  }

  /**
   * Returns the duty identifier.
   *
   * @return non-null name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the feed stream.
   *
   * @return the feed
   */
  public StreamInterface getFeed() {
    return feed;
  }

  /**
   * Returns the top-product purity requirements.
   *
   * @return an unmodifiable map; never null
   */
  public Map<String, Double> getTopProductSpecs() {
    return topProductSpecs;
  }

  /**
   * Returns the bottom-product purity requirements.
   *
   * @return an unmodifiable map; never null
   */
  public Map<String, Double> getBottomProductSpecs() {
    return bottomProductSpecs;
  }

  /**
   * Returns the operating pressure (bara), or {@link Double#NaN} when the synthesis engine should
   * use the feed pressure.
   *
   * @return pressure in bara or NaN
   */
  public double getOperatingPressureBara() {
    return operatingPressureBara;
  }
}
