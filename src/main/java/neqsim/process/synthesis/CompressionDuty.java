package neqsim.process.synthesis;

import java.io.Serializable;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Declarative description of a compression problem handed to
 * {@link FlowsheetSynthesisEngine#proposeAndBuildCompression(CompressionDuty)}.
 *
 * <p>
 * A compression duty specifies a feed stream and a target discharge pressure. The synthesis engine decides on the
 * number of compression stages from the overall pressure ratio (limited per-stage by {@link #getMaxStageRatio()}),
 * inserts inter-stage coolers to control discharge temperature, and returns a fully wired
 * {@link neqsim.process.processmodel.ProcessSystem} with all units named.
 * </p>
 *
 * <p>
 * Defaults follow industry rules of thumb (NORSOK P-100, GPSA Engineering Data Book):
 * </p>
 * <ul>
 * <li>Maximum per-stage pressure ratio: 3.5 (centrifugal compressors, gas service)</li>
 * <li>Inter-stage cooler outlet temperature: 35 °C (typical ambient + 10 °C approach)</li>
 * <li>Polytropic efficiency: 0.78</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CompressionDuty implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final StreamInterface feed;
  private final double dischargePressureBara;
  private double maxStageRatio = 3.5;
  private double interstageCoolerTemperatureC = 35.0;
  private double polytropicEfficiency = 0.78;
  private boolean afterCooler = true;
  private double finalCoolerTemperatureC = 35.0;

  /**
   * Creates a compression duty.
   *
   * @param name short identifier used in the generated flowsheet
   * @param feed the feed stream; must have been {@code run()} so that flow, T and P are valid
   * @param dischargePressureBara target discharge pressure of the train, in bara; must be strictly greater than the
   * feed pressure
   */
  public CompressionDuty(String name, StreamInterface feed, double dischargePressureBara) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (feed == null) {
      throw new IllegalArgumentException("feed must not be null");
    }
    if (!(dischargePressureBara > 0.0)) {
      throw new IllegalArgumentException("dischargePressureBara must be > 0");
    }
    if (!(dischargePressureBara > feed.getPressure("bara"))) {
      throw new IllegalArgumentException("dischargePressureBara (" + dischargePressureBara
          + ") must be > feed pressure (" + feed.getPressure("bara") + ")");
    }
    this.name = name;
    this.feed = feed;
    this.dischargePressureBara = dischargePressureBara;
  }

  /**
   * Sets the maximum per-stage pressure ratio. Above this, the engine adds another stage.
   *
   * @param ratio per-stage ratio (must be {@code > 1}); default 3.5
   * @return this duty for chaining
   */
  public CompressionDuty setMaxStageRatio(double ratio) {
    if (!(ratio > 1.0)) {
      throw new IllegalArgumentException("maxStageRatio must be > 1, got " + ratio);
    }
    this.maxStageRatio = ratio;
    return this;
  }

  /**
   * Sets the inter-stage cooler outlet temperature in degrees Celsius.
   *
   * @param tC outlet temperature in °C; default 35 °C
   * @return this duty for chaining
   */
  public CompressionDuty setInterstageCoolerTemperatureC(double tC) {
    this.interstageCoolerTemperatureC = tC;
    return this;
  }

  /**
   * Sets the polytropic efficiency of each compression stage.
   *
   * @param eta polytropic efficiency in the open interval (0, 1); default 0.78
   * @return this duty for chaining
   */
  public CompressionDuty setPolytropicEfficiency(double eta) {
    if (!(eta > 0.0 && eta < 1.0)) {
      throw new IllegalArgumentException("polytropicEfficiency must be in (0, 1), got " + eta);
    }
    this.polytropicEfficiency = eta;
    return this;
  }

  /**
   * Configures whether a final after-cooler is added downstream of the last stage and at what temperature.
   *
   * @param enabled true to add an after-cooler (default), false to leave the last stage discharge uncooled
   * @param tC after-cooler outlet temperature in °C; ignored when {@code enabled} is false
   * @return this duty for chaining
   */
  public CompressionDuty setAfterCooler(boolean enabled, double tC) {
    this.afterCooler = enabled;
    this.finalCoolerTemperatureC = tC;
    return this;
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
   * Returns the target discharge pressure in bara.
   *
   * @return discharge pressure
   */
  public double getDischargePressureBara() {
    return dischargePressureBara;
  }

  /**
   * Returns the maximum per-stage pressure ratio.
   *
   * @return max per-stage ratio
   */
  public double getMaxStageRatio() {
    return maxStageRatio;
  }

  /**
   * Returns the inter-stage cooler outlet temperature in °C.
   *
   * @return inter-stage cooler temperature
   */
  public double getInterstageCoolerTemperatureC() {
    return interstageCoolerTemperatureC;
  }

  /**
   * Returns the polytropic efficiency for each stage.
   *
   * @return polytropic efficiency
   */
  public double getPolytropicEfficiency() {
    return polytropicEfficiency;
  }

  /**
   * Returns whether an after-cooler is included downstream of the last stage.
   *
   * @return true when an after-cooler is configured
   */
  public boolean hasAfterCooler() {
    return afterCooler;
  }

  /**
   * Returns the after-cooler outlet temperature in °C (ignored when {@link #hasAfterCooler()} is false).
   *
   * @return after-cooler outlet temperature
   */
  public double getFinalCoolerTemperatureC() {
    return finalCoolerTemperatureC;
  }
}
