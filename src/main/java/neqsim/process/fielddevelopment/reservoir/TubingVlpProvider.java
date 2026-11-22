package neqsim.process.fielddevelopment.reservoir;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.TubingPerformance;
import neqsim.thermo.system.SystemInterface;

/**
 * Vertical lift performance of a (deviated) well from {@link TubingPerformance}.
 *
 * <p>
 * For every table coordinate the well stream is created by a {@link VfpFluidFactory}, set to the bottomhole
 * temperature, and the bottomhole pressure that gives the requested wellhead pressure is solved with
 * {@link TubingPerformance#calculateInletPressureForOutlet(SystemInterface, double, double)}. The tubing is described
 * by a survey (measured depth, true vertical depth and inner diameter per interval) so that inclination and diameter
 * change along the well without hand-written chains of instances.
 * </p>
 *
 * <p>
 * The Gray correlation is the default because Beggs-Brill over-predicts the pressure drop of lean gas wells. Each
 * evaluation costs one fluid flash per integration segment and iteration, so keep {@link #setNumberOfSegments(int)}
 * small (3 to 10 per well is usually enough for smooth gas wells) and verify the table against a finer direct run.
 * Instances are not thread-safe.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TubingVlpProvider implements VlpProvider {
  private static final Logger logger = LogManager.getLogger(TubingVlpProvider.class);
  private final VfpFluidFactory fluidFactory;
  private final TubingPerformance tubing;
  private double bottomholeTemperatureC = 90.0;
  private double toleranceBar = 0.02;
  private int evaluations = 0;
  private int failures = 0;

  /**
   * Creates a provider for a well described by a survey.
   *
   * @param fluidFactory creates the well stream for each coordinate
   * @param md measured depths of the survey stations in m, increasing from the wellhead
   * @param tvd true vertical depths of the stations in m
   * @param innerDiameter inner diameter in m for each interval between stations ({@code md.length - 1} values)
   * @throws IllegalArgumentException if the survey is inconsistent
   */
  public TubingVlpProvider(VfpFluidFactory fluidFactory, double[] md, double[] tvd, double[] innerDiameter) {
    this.fluidFactory = fluidFactory;
    this.tubing = new TubingPerformance("VFP tubing");
    this.tubing.setSurvey(md, tvd, innerDiameter);
    this.tubing.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    this.tubing.setTemperatureModel(TubingPerformance.TemperatureModel.LINEAR_GRADIENT);
    this.tubing.setNumberOfSegments(10);
    this.tubing.setSurfaceTemperature(30.0);
    this.tubing.setBottomholeTemperature(bottomholeTemperatureC);
  }

  /**
   * Creates a provider for a vertical well of constant diameter.
   *
   * @param fluidFactory creates the well stream for each coordinate
   * @param tvd true vertical depth in m
   * @param innerDiameter inner diameter in m
   */
  public TubingVlpProvider(VfpFluidFactory fluidFactory, double tvd, double innerDiameter) {
    this(fluidFactory, new double[] {0.0, tvd}, new double[] {0.0, tvd}, new double[] {innerDiameter});
  }

  /**
   * Selects the pressure-drop correlation.
   *
   * @param correlation correlation; GRAY for gas wells, HASAN_KABIR or BEGGS_BRILL for oil wells
   * @return this provider
   */
  public TubingVlpProvider setCorrelation(TubingPerformance.CorrelationType correlation) {
    tubing.setCorrelationType(correlation);
    return this;
  }

  /**
   * Sets the wall roughness.
   *
   * @param roughnessM absolute roughness in m
   * @return this provider
   */
  public TubingVlpProvider setRoughness(double roughnessM) {
    tubing.setRoughness(roughnessM);
    return this;
  }

  /**
   * Sets the total number of integration segments over the survey.
   *
   * @param segments number of segments (at least 1)
   * @return this provider
   */
  public TubingVlpProvider setNumberOfSegments(int segments) {
    tubing.setNumberOfSegments(Math.max(1, segments));
    return this;
  }

  /**
   * Sets the temperature profile between the bottomhole and the wellhead (linear in true vertical depth).
   *
   * @param bottomholeC bottomhole flowing temperature in C
   * @param wellheadC wellhead flowing temperature in C
   * @return this provider
   */
  public TubingVlpProvider setTemperatures(double bottomholeC, double wellheadC) {
    this.bottomholeTemperatureC = bottomholeC;
    tubing.setBottomholeTemperature(bottomholeC);
    tubing.setSurfaceTemperature(wellheadC);
    return this;
  }

  /**
   * Sets the pressure tolerance of the bottomhole pressure solve.
   *
   * @param bar absolute tolerance in bar
   * @return this provider
   */
  public TubingVlpProvider setTolerance(double bar) {
    this.toleranceBar = bar;
    return this;
  }

  /**
   * Gives the underlying tubing model for advanced settings such as the temperature model.
   *
   * @return tubing performance instance used for every evaluation
   */
  public TubingPerformance getTubing() {
    return tubing;
  }

  /**
   * Number of coordinates evaluated so far.
   *
   * @return evaluation count
   */
  public int getEvaluations() {
    return evaluations;
  }

  /**
   * Number of coordinates that did not converge or failed in the flash.
   *
   * @return failure count
   */
  public int getFailures() {
    return failures;
  }

  /** {@inheritDoc} */
  @Override
  public double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    evaluations++;
    try {
      SystemInterface fluid = fluidFactory.createWellStream(rate, waterRatio, gasRatio, alq);
      fluid.setTemperature(bottomholeTemperatureC, "C");
      double bhp = tubing.calculateInletPressureForOutlet(fluid, outletPressure, toleranceBar);
      if (Double.isNaN(bhp)) {
        failures++;
      }
      return bhp;
    } catch (RuntimeException ex) {
      failures++;
      logger.debug("VLP point rate {} outlet {} failed: {}", rate, outletPressure, ex.toString());
      return Double.NaN;
    }
  }
}
