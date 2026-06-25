package neqsim.process.equipment.compressor;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Steady-state anti-surge recycle calculator for a charted {@link Compressor}.
 *
 * <p>
 * Given a compressor that already carries a performance map with an active surge curve and a natural (offtake) suction
 * stream, this helper computes the cooled recycle flow required to hold the compressor inlet on the anti-surge control
 * line in a converged steady-state flowsheet. It mirrors the field recycle topology (a {@link Mixer} that combines the
 * natural suction with a cooled-gas recycle stream feeding the machine) and converges the recycle flow with a damped
 * fixed-point iteration so that the machine inlet sits at the configured fractional distance to surge.
 * </p>
 *
 * <p>
 * This is the steady-state companion to {@link neqsim.process.controllerdevice.AntiSurgeController}, which performs
 * transient proportional-integral control of a recycle valve during dynamic runs. This class adds no new compressor
 * physics; it only reads {@link Compressor#getSurgeFlowRate()} and {@link Compressor#getDistanceToSurge()} and re-runs
 * the supplied compressor.
 * </p>
 *
 * <p>
 * Typical usage:
 * </p>
 *
 * <pre>
 * Compressor comp = new Compressor("export", suction);
 * comp.setUsePolytropicCalc(true);
 * // ... attach chart + surge curve, set outlet pressure / solve speed ...
 * comp.run();
 *
 * AntiSurgeRecycleCalculator calc = new AntiSurgeRecycleCalculator(comp, suction);
 * calc.setSurgeControlMargin(0.05); // hold inlet 5% above surge flow
 * calc.setRecycleCoolerTemperature(40.0, "C");
 * AntiSurgeRecycleCalculator.Result r = calc.solve();
 * if (r.isRecycleActive()) {
 *   double recycle = r.getRecycleStream().getFlowRate("MSm3/day");
 *   // comp.getPower(...) now reflects the recycled machine
 * }
 * </pre>
 *
 * <p>
 * The recycle stream composition is cloned from the suction fluid. Because an ideal compressor does not change
 * composition, this is an accurate representation of a cooled-discharge recycle while avoiding re-entrant coupling to
 * the outlet stream during iteration.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AntiSurgeRecycleCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(AntiSurgeRecycleCalculator.class);

  /** The compressor whose surge margin is being protected. */
  private final Compressor compressor;

  /** The natural (offtake) suction stream feeding the compressor before any recycle. */
  private final StreamInterface suctionStream;

  /**
   * Surge-control margin as a fraction of the surge flow. The control line is {@code surgeFlow * (1 + margin)}. Default
   * 0.05 = 5%.
   */
  private double surgeControlMargin = 0.05;

  /** Recycle cooler outlet temperature value. */
  private double recycleCoolerTemperature = 40.0;

  /** Recycle cooler outlet temperature unit (passed to the recycle stream). */
  private String recycleCoolerTemperatureUnit = "C";

  /** Maximum number of fixed-point iterations. */
  private int maxIterations = 20;

  /** Convergence tolerance as a fraction of the control flow. Default 0.005 = 0.5%. */
  private double tolerance = 0.005;

  /** Fixed-point relaxation (damping) factor in the range (0, 1]. */
  private double damping = 0.8;

  /** Result of the last {@link #solve()} call. */
  private Result lastResult = null;

  /**
   * Constructs an anti-surge recycle calculator.
   *
   * @param compressor the charted compressor whose inlet is to be held on the surge control line
   * @param suctionStream the natural (offtake) suction stream feeding the compressor before recycle
   */
  public AntiSurgeRecycleCalculator(Compressor compressor, StreamInterface suctionStream) {
    this.compressor = compressor;
    this.suctionStream = suctionStream;
  }

  /**
   * Solve for the steady-state cooled recycle flow that holds the compressor inlet on the anti-surge control line.
   *
   * <p>
   * The supplied compressor is run first to establish the natural operating point. If the natural inlet volumetric flow
   * already meets or exceeds the control flow, no recycle is required and the compressor is left untouched. Otherwise a
   * {@link Mixer} of the natural suction and a cooled recycle {@link Stream} is wired to the compressor inlet and the
   * recycle flow is converged with a damped fixed-point iteration. On return, the compressor inlet reflects the
   * converged recycled state so that {@link Compressor#getPower(String)} and related getters describe the recycled
   * machine.
   * </p>
   *
   * @return the converged {@link Result}; never {@code null}
   */
  public Result solve() {
    compressor.run();

    double surgeFlow = compressor.getSurgeFlowRate();
    double inletVol = compressor.getInletStream().getFlowRate("m3/hr");
    double controlFlow = surgeFlow * (1.0 + surgeControlMargin);
    boolean active = inletVol < controlFlow;

    if (!active) {
      lastResult = new Result(false, 0.0, null, compressor.getDistanceToSurge(), inletVol, surgeFlow, controlFlow, 0,
	  true);
      return lastResult;
    }

    double feedMass = suctionStream.getFlowRate("kg/hr");
    double suctionPressureBara = suctionStream.getPressure("bara");
    double recycleMass = Math.max((controlFlow - inletVol) / Math.max(inletVol, 1.0e-6) * feedMass, 0.0);

    Stream recycleStream = null;
    int iterations = 0;
    boolean converged = false;
    for (int i = 0; i < maxIterations; i++) {
      iterations = i + 1;
      SystemInterface recycleFluid = suctionStream.getFluid().clone();
      recycleStream = new Stream("anti-surge recycle", recycleFluid);
      recycleStream.setPressure(suctionPressureBara, "bara");
      recycleStream.setTemperature(recycleCoolerTemperature, recycleCoolerTemperatureUnit);
      recycleStream.setFlowRate(recycleMass, "kg/hr");
      recycleStream.run();

      Mixer mixer = new Mixer("anti-surge mixer");
      mixer.addStream(suctionStream);
      mixer.addStream(recycleStream);
      mixer.run();

      compressor.setInletStream(mixer.getOutletStream());
      compressor.run();

      inletVol = compressor.getInletStream().getFlowRate("m3/hr");
      surgeFlow = compressor.getSurgeFlowRate();
      controlFlow = surgeFlow * (1.0 + surgeControlMargin);
      double error = controlFlow - inletVol;
      if (Math.abs(error) < tolerance * controlFlow) {
	converged = true;
	break;
      }
      double totalMass = mixer.getOutletStream().getFlowRate("kg/hr");
      double volumePerMass = inletVol / Math.max(totalMass, 1.0e-6);
      recycleMass = Math.max(recycleMass + damping * error / Math.max(volumePerMass, 1.0e-9), 0.0);
    }

    if (!converged) {
      logger.warn("AntiSurgeRecycleCalculator did not converge in {} iterations (residual on control flow)",
	  maxIterations);
    }

    lastResult = new Result(true, recycleMass, recycleStream, compressor.getDistanceToSurge(), inletVol, surgeFlow,
	controlFlow, iterations, converged);
    return lastResult;
  }

  /**
   * Get the surge-control margin.
   *
   * @return the margin as a fraction of the surge flow
   */
  public double getSurgeControlMargin() {
    return surgeControlMargin;
  }

  /**
   * Set the surge-control margin. The inlet is held at {@code surgeFlow * (1 + margin)}.
   *
   * @param surgeControlMargin the margin as a fraction of the surge flow (e.g. 0.05 for 5%)
   */
  public void setSurgeControlMargin(double surgeControlMargin) {
    this.surgeControlMargin = surgeControlMargin;
  }

  /**
   * Set the recycle cooler outlet temperature.
   *
   * @param value the temperature value
   * @param unit the temperature unit accepted by the stream (e.g. "C", "K")
   */
  public void setRecycleCoolerTemperature(double value, String unit) {
    this.recycleCoolerTemperature = value;
    this.recycleCoolerTemperatureUnit = unit;
  }

  /**
   * Get the recycle cooler outlet temperature value.
   *
   * @return the temperature value in the configured unit
   */
  public double getRecycleCoolerTemperature() {
    return recycleCoolerTemperature;
  }

  /**
   * Get the recycle cooler outlet temperature unit.
   *
   * @return the temperature unit
   */
  public String getRecycleCoolerTemperatureUnit() {
    return recycleCoolerTemperatureUnit;
  }

  /**
   * Get the maximum number of fixed-point iterations.
   *
   * @return the maximum iteration count
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set the maximum number of fixed-point iterations.
   *
   * @param maxIterations the maximum iteration count
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Get the convergence tolerance.
   *
   * @return the tolerance as a fraction of the control flow
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Set the convergence tolerance.
   *
   * @param tolerance the tolerance as a fraction of the control flow (e.g. 0.005 for 0.5%)
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * Get the fixed-point relaxation (damping) factor.
   *
   * @return the damping factor
   */
  public double getDamping() {
    return damping;
  }

  /**
   * Set the fixed-point relaxation (damping) factor.
   *
   * @param damping the damping factor in the range (0, 1]
   */
  public void setDamping(double damping) {
    this.damping = damping;
  }

  /**
   * Get the result of the last {@link #solve()} call.
   *
   * @return the last result, or {@code null} if {@link #solve()} has not been called
   */
  public Result getLastResult() {
    return lastResult;
  }

  /**
   * Immutable result of an anti-surge recycle calculation.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Whether a recycle was required to stay off the surge control line. */
    private final boolean recycleActive;

    /** Converged recycle mass flow in kg/hr. */
    private final double recycleMassFlow;

    /** The converged recycle stream, or {@code null} when no recycle was required. */
    private final transient StreamInterface recycleStream;

    /** Compressor distance to surge (dimensionless) at the converged state. */
    private final double distanceToSurge;

    /** Compressor inlet actual volumetric flow in m3/hr at the converged state. */
    private final double inletVolumeFlow;

    /** Compressor surge flow in m3/hr at the running head. */
    private final double surgeFlow;

    /** Anti-surge control flow in m3/hr (surge flow times the margin). */
    private final double controlFlow;

    /** Number of fixed-point iterations performed. */
    private final int iterations;

    /** Whether the fixed-point iteration converged within the tolerance. */
    private final boolean converged;

    /**
     * Constructs an anti-surge recycle result.
     *
     * @param recycleActive whether a recycle was required
     * @param recycleMassFlow the converged recycle mass flow in kg/hr
     * @param recycleStream the converged recycle stream, or {@code null} when inactive
     * @param distanceToSurge the compressor distance to surge at the converged state
     * @param inletVolumeFlow the compressor inlet actual volumetric flow in m3/hr
     * @param surgeFlow the compressor surge flow in m3/hr at the running head
     * @param controlFlow the anti-surge control flow in m3/hr
     * @param iterations the number of fixed-point iterations performed
     * @param converged whether the iteration converged within the tolerance
     */
    public Result(boolean recycleActive, double recycleMassFlow, StreamInterface recycleStream, double distanceToSurge,
	double inletVolumeFlow, double surgeFlow, double controlFlow, int iterations, boolean converged) {
      this.recycleActive = recycleActive;
      this.recycleMassFlow = recycleMassFlow;
      this.recycleStream = recycleStream;
      this.distanceToSurge = distanceToSurge;
      this.inletVolumeFlow = inletVolumeFlow;
      this.surgeFlow = surgeFlow;
      this.controlFlow = controlFlow;
      this.iterations = iterations;
      this.converged = converged;
    }

    /**
     * Whether a recycle was required to keep the machine off the surge control line.
     *
     * @return true if a recycle was opened
     */
    public boolean isRecycleActive() {
      return recycleActive;
    }

    /**
     * Get the converged recycle mass flow.
     *
     * @return the recycle mass flow in kg/hr
     */
    public double getRecycleMassFlow() {
      return recycleMassFlow;
    }

    /**
     * Get the converged recycle stream.
     *
     * @return the recycle stream, or {@code null} when no recycle was required
     */
    public StreamInterface getRecycleStream() {
      return recycleStream;
    }

    /**
     * Get the compressor distance to surge at the converged state.
     *
     * @return the dimensionless distance to surge
     */
    public double getDistanceToSurge() {
      return distanceToSurge;
    }

    /**
     * Get the compressor inlet actual volumetric flow at the converged state.
     *
     * @return the inlet volumetric flow in m3/hr
     */
    public double getInletVolumeFlow() {
      return inletVolumeFlow;
    }

    /**
     * Get the compressor surge flow at the running head.
     *
     * @return the surge flow in m3/hr
     */
    public double getSurgeFlow() {
      return surgeFlow;
    }

    /**
     * Get the anti-surge control flow.
     *
     * @return the control flow in m3/hr
     */
    public double getControlFlow() {
      return controlFlow;
    }

    /**
     * Get the number of fixed-point iterations performed.
     *
     * @return the iteration count
     */
    public int getIterations() {
      return iterations;
    }

    /**
     * Whether the fixed-point iteration converged within the tolerance.
     *
     * @return true if converged
     */
    public boolean isConverged() {
      return converged;
    }
  }
}
