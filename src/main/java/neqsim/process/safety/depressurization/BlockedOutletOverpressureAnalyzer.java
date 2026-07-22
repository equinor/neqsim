package neqsim.process.safety.depressurization;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import neqsim.thermo.system.SystemInterface;

/**
 * Screening analyser for the MORE PRESSURE / blocked-outlet overpressure HAZOP deviation.
 *
 * <p>
 * When a vessel keeps receiving feed while its outlet is inadvertently blocked (a closed valve, a tripped downstream
 * unit, or a control failure), the trapped gas inventory grows and the pressure rises until a pressure-relief device
 * lifts. This analyser quantifies how quickly the vessel reaches its relief set pressure by reusing the validated
 * coupled mass-and-energy transient in {@link VesselFillingSimulator} (the same charging physics used for cylinder
 * filling), then reports the time-to-set-pressure and the relief demand for a HAZOP team.
 * </p>
 *
 * <p>
 * This is a screening calculation: it assumes the inflow continues unabated with no outflow, which is the conservative
 * basis for a blocked-outlet contingency in API 521. It does not size the relief device — pair it with
 * {@link neqsim.process.util.fire.ReliefValveSizing} for the required orifice area once the relieving rate is known.
 * </p>
 *
 * <p>
 * <b>References:</b> API Standard 521 (Pressure-relieving and Depressuring Systems), §4.4 (blocked outlet /
 * closed-outlet contingency).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class BlockedOutletOverpressureAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  private final SystemInterface fluid;
  private final double vesselVolume;
  private double inletTemperatureK = 288.15;
  private double supplyPressureBara = Double.NaN;
  private double inletMassFlowKgPerS = Double.NaN;
  private double reliefSetPressureBara = Double.NaN;
  private double timeStep = 1.0;
  private double maxTime = 3600.0;

  /**
   * Construct a blocked-outlet overpressure analyser.
   *
   * @param fluid the residual gas inventory in the vessel at the moment the outlet blocks; must already have its mixing
   * rule set
   * @param vesselVolume internal vessel volume in m³; must be positive
   * @throws IllegalArgumentException if {@code fluid} is null or {@code vesselVolume} is not positive
   */
  public BlockedOutletOverpressureAnalyzer(SystemInterface fluid, double vesselVolume) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (vesselVolume <= 0.0) {
      throw new IllegalArgumentException("vesselVolume must be positive");
    }
    this.fluid = fluid;
    this.vesselVolume = vesselVolume;
  }

  /**
   * Set the continued inlet feed conditions that drive the pressure build-up while the outlet is blocked.
   *
   * @param inletTemperatureK feed gas stagnation temperature in K; must be positive
   * @param supplyPressureBara feed supply pressure in bara used to evaluate the inlet enthalpy; must be positive
   * @param massFlowKgPerS continued feed mass flow into the blocked vessel in kg/s; must be positive
   * @return this analyser for chaining
   * @throws IllegalArgumentException if any argument is not positive
   */
  public BlockedOutletOverpressureAnalyzer setInletConditions(double inletTemperatureK, double supplyPressureBara,
      double massFlowKgPerS) {
    if (inletTemperatureK <= 0.0 || supplyPressureBara <= 0.0 || massFlowKgPerS <= 0.0) {
      throw new IllegalArgumentException("inletTemperatureK, supplyPressureBara and massFlowKgPerS must be positive");
    }
    this.inletTemperatureK = inletTemperatureK;
    this.supplyPressureBara = supplyPressureBara;
    this.inletMassFlowKgPerS = massFlowKgPerS;
    return this;
  }

  /**
   * Set the pressure-relief device set pressure used as the overpressure threshold.
   *
   * @param reliefSetPressureBara relief set pressure in bara; must be positive
   * @return this analyser for chaining
   * @throws IllegalArgumentException if {@code reliefSetPressureBara} is not positive
   */
  public BlockedOutletOverpressureAnalyzer setReliefSetPressure(double reliefSetPressureBara) {
    if (reliefSetPressureBara <= 0.0) {
      throw new IllegalArgumentException("reliefSetPressureBara must be positive");
    }
    this.reliefSetPressureBara = reliefSetPressureBara;
    return this;
  }

  /**
   * Set the integration time step.
   *
   * @param timeStepSeconds time step in seconds; must be positive
   * @return this analyser for chaining
   * @throws IllegalArgumentException if {@code timeStepSeconds} is not positive
   */
  public BlockedOutletOverpressureAnalyzer setTimeStep(double timeStepSeconds) {
    if (timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("timeStepSeconds must be positive");
    }
    this.timeStep = timeStepSeconds;
    return this;
  }

  /**
   * Set the maximum simulated time horizon.
   *
   * @param maxTimeSeconds maximum simulated time in seconds; must be positive
   * @return this analyser for chaining
   * @throws IllegalArgumentException if {@code maxTimeSeconds} is not positive
   */
  public BlockedOutletOverpressureAnalyzer setMaxTime(double maxTimeSeconds) {
    if (maxTimeSeconds <= 0.0) {
      throw new IllegalArgumentException("maxTimeSeconds must be positive");
    }
    this.maxTime = maxTimeSeconds;
    return this;
  }

  /**
   * Run the blocked-outlet pressure build-up screening and return the result.
   *
   * @return the blocked-outlet overpressure result
   * @throws IllegalStateException if the inlet conditions or relief set pressure have not been configured
   */
  public BlockedOutletResult run() {
    if (Double.isNaN(inletMassFlowKgPerS) || Double.isNaN(supplyPressureBara)) {
      throw new IllegalStateException("inlet conditions must be configured via setInletConditions(...)");
    }
    if (Double.isNaN(reliefSetPressureBara)) {
      throw new IllegalStateException("relief set pressure must be configured via setReliefSetPressure(...)");
    }
    VesselFillingSimulator simulator = new VesselFillingSimulator(fluid, vesselVolume);
    simulator.setInletConditions(inletTemperatureK, supplyPressureBara, inletMassFlowKgPerS);
    simulator.setTargetPressure(reliefSetPressureBara);
    simulator.setTimeStep(timeStep);
    simulator.setMaxTime(maxTime);
    VesselFillingSimulator.VesselFillingResult filling = simulator.run();

    BlockedOutletResult result = new BlockedOutletResult();
    result.reliefSetPressureBara = reliefSetPressureBara;
    result.maxPressureBara = filling.maxPressureBara;
    result.finalPressureBara = filling.finalPressureBara;
    result.finalMassKg = filling.finalMassKg;
    result.inletMassFlowKgPerS = inletMassFlowKgPerS;
    result.simulatedHorizonSeconds = maxTime;

    double timeToSet = Double.NaN;
    int n = filling.pressureBara.size();
    for (int i = 0; i < n; i++) {
      if (filling.pressureBara.get(i).doubleValue() >= reliefSetPressureBara) {
        timeToSet = filling.time.get(i).doubleValue();
        break;
      }
    }
    result.timeToReliefSetSeconds = timeToSet;
    result.reliefDemand = !Double.isNaN(timeToSet);

    if (result.reliefDemand) {
      result.message = String.format(java.util.Locale.ROOT,
          "Blocked outlet reaches relief set pressure %.1f bara in %.0f s; pressure-relief device demand confirmed.",
          reliefSetPressureBara, timeToSet);
    } else {
      result.message = String.format(java.util.Locale.ROOT,
          "Blocked outlet did not reach relief set pressure %.1f bara within %.0f s (max %.1f bara reached).",
          reliefSetPressureBara, maxTime, filling.maxPressureBara);
    }
    return result;
  }

  /**
   * Result of a blocked-outlet overpressure screening run.
   */
  public static final class BlockedOutletResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Relief device set pressure used as the threshold (bara). */
    public double reliefSetPressureBara;
    /**
     * Time at which the vessel first reaches the relief set pressure (s); NaN if not reached within the horizon.
     */
    public double timeToReliefSetSeconds = Double.NaN;
    /** True if the relief set pressure is reached within the simulated horizon. */
    public boolean reliefDemand;
    /** Maximum absolute pressure reached during the run (bara). */
    public double maxPressureBara;
    /** Final absolute pressure at the end of the run (bara). */
    public double finalPressureBara;
    /** Final trapped inventory mass (kg). */
    public double finalMassKg;
    /** Continued feed mass flow into the blocked vessel (kg/s). */
    public double inletMassFlowKgPerS;
    /** Simulated time horizon (s). */
    public double simulatedHorizonSeconds;
    /** Human-readable summary of the relief demand. */
    public String message;

    /**
     * Serialise this result to a pretty-printed JSON object.
     *
     * @return JSON representation of the result
     */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }

    /**
     * Render the result as a single human-readable line.
     *
     * @return a one-line summary string
     */
    @Override
    public String toString() {
      return message;
    }
  }
}
