package neqsim.process.equipment.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.monitor.RecycleResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Recycle class for handling tear streams in process simulations.
 *
 * <p>
 * This class implements convergence acceleration methods for recycle calculations, including direct substitution,
 * Wegstein acceleration, and Broyden's method.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Recycle extends ProcessEquipmentBaseClass
    implements MixerInterface, TransientStateParticipant<Recycle.TransientState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Stable identity used for transaction provenance and foreign-snapshot rejection. */
  private String transientStateIdentity = UUID.randomUUID().toString();
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Recycle.class);

  protected ArrayList<StreamInterface> streams = new ArrayList<StreamInterface>(0);
  private ArrayList<String> downstreamProperty = new ArrayList<String>(0);
  protected int numberOfInputStreams = 0;
  protected StreamInterface mixedStream;
  StreamInterface lastIterationStream = null;
  private StreamInterface outletStream = null;
  private int priority = 100;
  boolean firstTime = true;
  int iterations = 0;
  int maxIterations = 10;
  private double errorComposition = 1e10;
  private double errorFlow = 1e10;
  private double errorTemperature = 1e10;
  private double errorPressure = 1e10;
  private double flowTolerance = 1e-2;
  private double compositionTolerance = 1e-2;
  private double temperatureTolerance = 1e-2;
  private double pressureTolerance = 1e-2;

  /**
   * Absolute change in loop mass flow (kg/hr) between the last two iterations. Unlike {@link #getErrorFlow()} this is
   * always a mass flow, so it can be compared against a physically meaningful tolerance.
   */
  private double absoluteFlowChange = Double.NaN;

  /**
   * Absolute flow tolerance in kg/hr. A value of 0.0 (the default) disables the criterion and restores pure
   * {@link #getErrorFlow()} checking.
   */
  private double absoluteFlowTolerance = 0.0;

  /** True once the caller has set an absolute flow tolerance, which then survives auto-tuning. */
  private boolean absoluteFlowToleranceExplicit = false;

  private double minimumFlow = 1e-20;

  // Acceleration method settings
  private AccelerationMethod accelerationMethod = AccelerationMethod.DIRECT_SUBSTITUTION;

  /** True once the caller has chosen an acceleration method; the adaptive upgrade then stands down. */
  private boolean accelerationMethodExplicit = false;
  /** Whether a stalling direct-substitution loop may upgrade itself to Wegstein. */
  private boolean adaptiveAcceleration = false;
  /** True once the caller has explicitly enabled or disabled adaptive acceleration. */
  private boolean adaptiveAccelerationExplicit = false;
  /** True when automatic convergence tuning, rather than the caller, enabled adaptive acceleration. */
  private boolean adaptiveAccelerationAutoManaged = false;
  /** True when the adaptive logic (not the caller) selected the current acceleration method. */
  private boolean accelerationAutoUpgraded = false;
  /** Flow error of the previous pass, used to detect a stalling loop. */
  private double previousErrorFlow = Double.NaN;
  /** Consecutive passes whose flow error failed to shrink materially. */
  private int stallingPasses = 0;
  /** A pass counts as stalling when the flow error is still above this fraction of the previous one. */
  private static final double ADAPTIVE_STALL_RATIO = 0.7;
  /** Consecutive stalling passes required before switching to Wegstein. */
  private static final int ADAPTIVE_STALL_PASSES = 3;
  /** q-factor ceiling applied on an adaptive upgrade so an oscillating loop can damp rather than only accelerate. */
  private static final double ADAPTIVE_WEGSTEIN_Q_MAX = 0.9;
  /** True once the caller has chosen a Wegstein q-factor ceiling. */
  private boolean wegsteinQMaxExplicit = false;

  // Wegstein acceleration fields
  /** Minimum bound for Wegstein q-factor to prevent divergence. */
  private double wegsteinQMin = -5.0;
  /** Maximum bound for Wegstein q-factor to prevent divergence. */
  private double wegsteinQMax = 0.0;
  /** Delay iterations before applying Wegstein (allows system to stabilize). */
  private int wegsteinDelayIterations = 2;
  /** Previous iteration input values for Wegstein slope calculation. */
  private double[] previousInputValues = null;
  /** Previous iteration output values for Wegstein slope calculation. */
  private double[] previousOutputValues = null;
  /** Current Wegstein q-factor values per variable. */
  private double[] wegsteinQFactors = null;

  // Broyden acceleration
  /** Broyden accelerator instance for multi-variable acceleration. */
  private BroydenAccelerator broydenAccelerator = null;

  /**
   * Constructor for Recycle.
   *
   * @param name a {@link java.lang.String} object
   */
  public Recycle(String name) {
    super(name);
  }

  /**
   * Setter for the field <code>compositionTolerance</code>.
   *
   * @param compositionTolerance a double
   */
  public void setCompositionTolerance(double compositionTolerance) {
    this.compositionTolerance = compositionTolerance;
  }

  /**
   * Getter for the field <code>compositionTolerance</code>.
   *
   * @return a double
   */
  public double getCompositionTolerance() {
    return this.compositionTolerance;
  }

  /**
   * Setter for the field <code>temperatureTolerance</code>.
   *
   * @param temperatureTolerance a double in % error
   */
  public void setTemperatureTolerance(double temperatureTolerance) {
    this.temperatureTolerance = temperatureTolerance;
  }

  /**
   * Getter for the field <code>temperatureTolerance</code>.
   *
   * @return a double
   */
  public double getTemperatureTolerance() {
    return this.temperatureTolerance;
  }

  /**
   * Setter for the field <code>flowTolerance</code>.
   *
   * @param flowTolerance a double
   */
  public void setFlowTolerance(double flowTolerance) {
    this.flowTolerance = flowTolerance;
  }

  /**
   * Getter for the field <code>flowTolerance</code>.
   *
   * @return a double
   */
  public double getFlowTolerance() {
    return this.flowTolerance;
  }

  /**
   * Setter for the pressure convergence tolerance.
   *
   * @param pressureTolerance relative pressure tolerance used to decide recycle convergence
   */
  public void setPressureTolerance(double pressureTolerance) {
    this.pressureTolerance = pressureTolerance;
  }

  /**
   * Getter for the pressure convergence tolerance.
   *
   * @return relative pressure tolerance used to decide recycle convergence
   */
  public double getPressureTolerance() {
    return this.pressureTolerance;
  }

  /**
   * Setter for the maximum number of recycle iterations.
   *
   * @param maxIterations maximum number of recycle iterations before validation flags the recycle
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Getter for the maximum number of recycle iterations.
   *
   * @return maximum number of recycle iterations before validation flags the recycle
   */
  public int getMaxIterations() {
    return this.maxIterations;
  }

  /**
   * resetIterations.
   *
   * <p>
   * Called at the start of every process pass. The adaptive-acceleration bookkeeping deliberately survives it: a tear
   * stream that closes across process areas is updated only once per outer pass, so a stall counter that reset here
   * could never reach its threshold and the loop would stay on direct substitution forever. Use
   * {@link #resetAdaptiveAcceleration()} for a genuine fresh start.
   * </p>
   */
  public void resetIterations() {
    iterations = 0;
    resetAccelerationState();
  }

  /** Clears the adaptive-acceleration bookkeeping and returns the loop to direct substitution. */
  public void resetAdaptiveAcceleration() {
    previousErrorFlow = Double.NaN;
    stallingPasses = 0;
    if (accelerationAutoUpgraded) {
      accelerationMethod = AccelerationMethod.DIRECT_SUBSTITUTION;
      accelerationAutoUpgraded = false;
    }
    resetAccelerationState();
  }

  /**
   * Gets the current iteration count.
   *
   * @return number of iterations performed since last reset
   */
  public int getIterations() {
    return iterations;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return mixedStream.getThermoSystem();
  }

  /**
   * Setter for the field <code>downstreamProperty</code>.
   *
   * @param property a {@link java.util.ArrayList} object
   */
  public void setDownstreamProperty(ArrayList<String> property) {
    this.downstreamProperty = property;
  }

  /**
   * Setter for the field <code>downstreamProperty</code>.
   *
   * @param property a {@link java.lang.String} object
   */
  public void setDownstreamProperty(String property) {
    downstreamProperty.add(property);
  }

  /** {@inheritDoc} */
  @Override
  public void replaceStream(int i, StreamInterface newStream) {
    streams.set(i, newStream);
  }

  /** {@inheritDoc} */
  @Override
  public void addStream(StreamInterface newStream) {
    streams.add(newStream);

    if (numberOfInputStreams == 0) {
      mixedStream = streams.get(0).clone(this.getName() + " mixed stream");
      // mixedStream.getThermoSystem().setNumberOfPhases(2);
      // mixedStream.getThermoSystem().init(0);
      // mixedStream.getThermoSystem().init(3);
    }
    mixedStream.setEmptyThermoSystem(streams.get(0).getThermoSystem());
    numberOfInputStreams++;
    lastIterationStream = mixedStream.clone(this.getName() + " last iteration stream");
  }

  /**
   * getStream.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getStream(int i) {
    return streams.get(i);
  }

  /**
   * mixStream.
   */
  public void mixStream() {
    int index = 0;
    // String compName = new String();

    for (int k = 1; k < streams.size(); k++) {
      for (int i = 0; i < streams.get(k).getThermoSystem().getPhase(0).getNumberOfComponents(); i++) {
        boolean gotComponent = false;
        String componentName = streams.get(k).getThermoSystem().getPhase(0).getComponent(i).getName();
        // logger.info("adding: " + componentName);
        // int numberOfPhases = streams.get(k).getThermoSystem().getNumberOfPhases();

        double moles = streams.get(k).getThermoSystem().getPhase(0).getComponent(i).getNumberOfmoles();
        // logger.info("moles: " + moles + " " +
        // mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents());
        for (int p = 0; p < mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents(); p++) {
          if (mixedStream.getThermoSystem().getPhase(0).getComponent(p).getName().equals(componentName)) {
            gotComponent = true;
            index = mixedStream.getThermoSystem().getPhase(0).getComponent(p).getComponentNumber();
            break;
          }
        }

        if (gotComponent) {
          mixedStream.getThermoSystem().addComponent(index, moles);
        } else {
          mixedStream.getThermoSystem().addComponent(componentName, moles);
        }
      }
    }
    // mixedStream.getThermoSystem().init_x_y();
    // mixedStream.getThermoSystem().initBeta();
    // mixedStream.getThermoSystem().init(2);
  }

  /**
   * guessTemperature.
   *
   * @return a double
   */
  public double guessTemperature() {
    double gtemp = 0;
    for (int k = 0; k < streams.size(); k++) {
      gtemp += streams.get(k).getThermoSystem().getTemperature() * streams.get(k).getThermoSystem().getNumberOfMoles()
          / mixedStream.getThermoSystem().getNumberOfMoles();
    }
    return gtemp;
  }

  /**
   * calcMixStreamEnthalpy.
   *
   * @return a double
   */
  public double calcMixStreamEnthalpy() {
    double enthalpy = 0;
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      // logger.info("total enthalpy k : " + ( ((Stream)
      // streams.get(k)).getThermoSystem()).getEnthalpy());
    }
    // logger.info("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  @Deprecated
  public StreamInterface getOutStream() {
    return mixedStream;
  }

  /**
   * initiateDownstreamProperties.
   *
   * @param outstream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void initiateDownstreamProperties(StreamInterface outstream) {
    lastIterationStream = outstream.clone();
  }

  /**
   * setDownstreamProperties.
   */
  public void setDownstreamProperties() {
    if (downstreamProperty.size() > 0) {
      for (int i = 0; i < downstreamProperty.size(); i++) {
        if (downstreamProperty.get(i).equals("flow rate")) {
          mixedStream.setFlowRate(outletStream.getFlowRate("kg/hr"), "kg/hr");
        }
      }
    }
  }

  /**
   * Deactivates this recycle because its loop flow has collapsed below the configured {@link #getMinimumFlow()} cutoff.
   *
   * <p>
   * A recycle below the cutoff carries no physically meaningful inventory, so it is marked inactive and its four
   * residuals are reported as exactly zero. The previous-iteration snapshot is refreshed at the same time; without that
   * refresh the flow, temperature and pressure balance checks would keep comparing the (negligible) current stream
   * against a stale pre-collapse snapshot, so the recycle would report {@code solved() == false} forever. That in turn
   * makes the owning {@link neqsim.process.processmodel.ProcessSystem} report NOT SOLVED and spend its whole iteration
   * budget on a dead leg.
   * </p>
   *
   * @param inletSystem clone of the (negligible) inlet thermodynamic system to publish on the outlet
   * @param id current calculation identifier; {@code null} leaves existing recycle and outlet identifiers unchanged
   */
  private void deactivateOnLowFlow(SystemInterface inletSystem, UUID id) {
    isActive(false);
    mixedStream.setThermoSystem(inletSystem);
    setErrorCompositon(0.0);
    setErrorFlow(0.0);
    setErrorTemperature(0.0);
    setErrorPressure(0.0);
    absoluteFlowChange = 0.0;
    lastIterationStream = mixedStream.clone();
    outletStream.setThermoSystem(mixedStream.getThermoSystem());
    if (id != null) {
      outletStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    iterations++;
    isActive(true);
    /*
     * if(firstTime || iterations>maxIterations) { firstTime=false; return; }
     */
    double enthalpy = 0.0;
    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    if (numberOfInputStreams == 1 && thermoSystem2.getFlowRate("kg/hr") < minimumFlow) {
      deactivateOnLowFlow(thermoSystem2, id);
      return;
    }
    mixedStream.setThermoSystem(thermoSystem2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
    if (streams.size() > 1) {
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().reInitPhaseType();
      mixedStream.getThermoSystem().init(0);

      mixStream();

      if (mixedStream.getFlowRate("kg/hr") < minimumFlow) {
        deactivateOnLowFlow(thermoSystem2, id);
        return;
      }

      setDownstreamProperties();
      try {
        enthalpy = calcMixStreamEnthalpy();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        return;
      }
      // logger.info("temp guess " + guessTemperature());
      mixedStream.getThermoSystem().setTemperature(guessTemperature());
      testOps.PHflash(enthalpy, 0);
      // logger.info("filan temp " + mixedStream.getTemperature());
    } else {
      setDownstreamProperties();
      testOps.TPflash();
    }
    mixedStream.setCalculationIdentifier(id);

    // Apply convergence acceleration if enabled and past delay period
    if (accelerationMethod == AccelerationMethod.WEGSTEIN && iterations > wegsteinDelayIterations
        && lastIterationStream != null) {
      applyWegsteinToStream();
    } else if (accelerationMethod == AccelerationMethod.BROYDEN && lastIterationStream != null) {
      applyBroydenToStream();
    }

    setErrorCompositon(compositionBalanceCheck());
    setErrorFlow(flowBalanceCheck());
    setErrorTemperature(temperatureBalanceCheck());
    setErrorPressure(pressureBalanceCheck());
    updateAdaptiveAcceleration();
    lastIterationStream = mixedStream.clone();
    outletStream.setThermoSystem(mixedStream.getThermoSystem());
    outletStream.setCalculationIdentifier(id);

    // logger.info("enthalpy: " +
    // mixedStream.getThermoSystem().getEnthalpy());
    // logger.info("enthalpy: " + enthalpy);
    // logger.info("temperature: " +
    // mixedStream.getThermoSystem().getTemperature());

    // logger.info("beta " + mixedStream.getThermoSystem().getBeta());
    // outStream.setThermoSystem(mixedStream.getThermoSystem());
    setCalculationIdentifier(id);
  }

  /**
   * Advances this recycle by one transient process evaluation.
   *
   * <p>
   * The same recycle object is used in both simulation modes. A steady-state {@link ProcessSystem} may call
   * {@link #run(UUID)} repeatedly until the tear stream converges. A transient process calls this method once in
   * flowsheet order for each evaluation of an accepted physical timestep, so the previously accepted outlet state is
   * consumed upstream before the current inlet state is published for the following evaluation. This breaks the
   * algebraic loop without requiring the flowsheet to replace its steady-state recycle with a separate dynamic unit.
   * </p>
   *
   * <p>
   * Steady-state Wegstein or Broyden acceleration is temporarily disabled during the transient evaluation. Applying
   * convergence acceleration between physical timesteps would introduce a non-physical state correction. The configured
   * acceleration method is restored unchanged for the next steady-state solve. This method deliberately works
   * regardless of {@link #getCalculateSteadyState()}, because a recycle has no independent differential inventory and
   * its transient role is an ordered algebraic transport evaluation.
   * </p>
   *
   * @param dt timestep in seconds
   * @param id calculation identifier shared by the physical timestep
   */
  @Override
  public void runTransient(double dt, UUID id) {
    boolean alreadyEvaluatedForStep = id != null && id.equals(getCalculationIdentifier());
    AccelerationMethod configuredAccelerationMethod = accelerationMethod;
    boolean configuredAdaptiveAcceleration = adaptiveAcceleration;
    try {
      accelerationMethod = AccelerationMethod.DIRECT_SUBSTITUTION;
      adaptiveAcceleration = false;
      run(id);
    } finally {
      accelerationMethod = configuredAccelerationMethod;
      adaptiveAcceleration = configuredAdaptiveAcceleration;
    }
    if (!alreadyEvaluatedForStep) {
      increaseTime(dt);
    }
  }

  /**
   * Flow residual between this iteration and the previous one.
   *
   * <p>
   * <b>The returned value is not dimensionally uniform.</b> Below 1 kg/sec it is the ABSOLUTE change in kg/sec; at or
   * above 1 kg/sec it is the RELATIVE change in PERCENT. A {@link #setFlowTolerance(double)} of 0.1 therefore means
   * "0.1 kg/sec" on a small loop but "0.1 %" on a large one, and the meaning flips discontinuously across the 1 kg/sec
   * threshold. A returned value of exactly 100 means the loop carried no flow on the previous iteration (a recycle that
   * collapsed and re-opened), not "100 kg/sec".
   * </p>
   *
   * <p>
   * This behaviour is retained for backward compatibility. Use {@link #getAbsoluteFlowChange()} for a properly
   * dimensioned residual (kg/hr) and {@link #setAbsoluteFlowTolerance(double)} for a scale-independent convergence
   * criterion.
   * </p>
   *
   * @return the flow residual, in kg/sec below 1 kg/sec and in percent at or above 1 kg/sec
   */
  public double flowBalanceCheck() {
    absoluteFlowChange = Math.abs(mixedStream.getFlowRate("kg/hr") - lastIterationStream.getFlowRate("kg/hr"));
    double abs_sum_errorFlow = 0.0;
    if (mixedStream.getFlowRate("kg/sec") < 1.0) {
      abs_sum_errorFlow += Math.abs(mixedStream.getFlowRate("kg/sec") - lastIterationStream.getFlowRate("kg/sec"));
    } else {
      abs_sum_errorFlow += Math.abs(mixedStream.getFlowRate("kg/sec") - lastIterationStream.getFlowRate("kg/sec"))
          / mixedStream.getFlowRate("kg/sec") * 100.0;
    }
    return abs_sum_errorFlow;
  }

  /**
   * compositionBalanceCheck.
   *
   * @return a double
   */
  public double compositionBalanceCheck() {
    if (lastIterationStream.getFluid().getNumberOfComponents() != mixedStream.getFluid().getNumberOfComponents()) {
      return 10.0;
    }

    double abs_sum_error = 0.0;
    for (int i = 0; i < mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents(); i++) {
      abs_sum_error += Math.abs(mixedStream.getThermoSystem().getPhase(0).getComponent(i).getx()
          - lastIterationStream.getThermoSystem().getPhase(0).getComponent(i).getx());
    }

    return abs_sum_error;
  }

  /**
   * temperatureBalanceCheck.
   *
   * @return a double
   */
  public double temperatureBalanceCheck() {
    double error = 0.0;
    for (int i = 0; i < mixedStream.getThermoSystem().getNumberOfPhases(); i++) {
      error += Math.abs((mixedStream.getThermoSystem().getPhase(i).getTemperature()
          - lastIterationStream.getThermoSystem().getPhase(i).getTemperature())
          / lastIterationStream.getThermoSystem().getPhase(i).getTemperature()) * 100.0;
    }
    return error;
  }

  /**
   * pressureBalanceCheck.
   *
   * @return a double
   */
  public double pressureBalanceCheck() {
    double error = 0.0;
    for (int i = 0; i < mixedStream.getThermoSystem().getNumberOfPhases(); i++) {
      error += Math.abs((mixedStream.getThermoSystem().getPhase(i).getPressure()
          - lastIterationStream.getThermoSystem().getPhase(i).getPressure())
          / lastIterationStream.getThermoSystem().getPhase(i).getPressure()) * 100.0;
    }
    return error;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pres) {
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().setPressure(pres);
    }
    mixedStream.getThermoSystem().setPressure(pres);
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temp) {
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().setTemperature(temp);
    }
    mixedStream.getThermoSystem().setTemperature(temp);
  }

  /**
   * Setter for the tolerance fields. Set tolerances to tolerance input.
   *
   * @param tolerance the tolerance to set
   */
  public void setTolerance(double tolerance) {
    this.flowTolerance = tolerance;
    this.temperatureTolerance = tolerance;
    this.compositionTolerance = tolerance;
    this.pressureTolerance = tolerance;
  }

  /**
   * Gets the current acceleration method used for convergence.
   *
   * @return the acceleration method
   */
  public AccelerationMethod getAccelerationMethod() {
    return accelerationMethod;
  }

  /**
   * Sets the acceleration method for convergence.
   *
   * @param method the acceleration method to use
   */
  public void setAccelerationMethod(AccelerationMethod method) {
    this.accelerationMethod = method;
    this.accelerationMethodExplicit = true;
    this.accelerationAutoUpgraded = false;
    // Reset acceleration state when method changes
    resetAccelerationState();
  }

  /**
   * Whether a direct-substitution loop that stops converging may switch itself to Wegstein acceleration.
   *
   * @return true if adaptive acceleration is enabled
   */
  public boolean isAdaptiveAcceleration() {
    return adaptiveAcceleration;
  }

  /**
   * Enables or disables adaptive acceleration explicitly.
   *
   * <p>
   * Direct substitution is robust but converges slowly, and a low-flow loop can oscillate instead of settling, forcing
   * the surrounding process to iterate to its budget. When enabled, a loop whose flow error stops shrinking for
   * {@value #ADAPTIVE_STALL_PASSES} consecutive passes switches itself to {@link AccelerationMethod#WEGSTEIN}, which
   * damps the oscillation and converges to the same solution. Calling
   * {@link #setAccelerationMethod(AccelerationMethod)} pins the method and disables the upgrade.
   * </p>
   *
   * <p>
   * Adaptive acceleration is disabled for an ordinary legacy {@code ProcessSystem.run()}. Automatic convergence tuning
   * may enable it for {@code runUntilConverged(...)} unless the caller has made an explicit choice here.
   * </p>
   *
   * @param adaptiveAcceleration true to let a stalling loop accelerate itself
   */
  public void setAdaptiveAcceleration(boolean adaptiveAcceleration) {
    this.adaptiveAcceleration = adaptiveAcceleration;
    adaptiveAccelerationExplicit = true;
    adaptiveAccelerationAutoManaged = false;
    if (!adaptiveAcceleration && accelerationAutoUpgraded) {
      resetAdaptiveAcceleration();
    }
  }

  /**
   * Enables adaptive acceleration on behalf of automatic convergence tuning.
   *
   * @return true when the tuner owns adaptive acceleration for this recycle
   */
  public boolean applyAutoAdaptiveAcceleration() {
    if (adaptiveAccelerationExplicit) {
      return false;
    }
    adaptiveAcceleration = true;
    adaptiveAccelerationAutoManaged = true;
    return true;
  }

  /**
   * Clears an automatically enabled adaptive-acceleration setting without touching a caller-owned choice.
   *
   * @return true when an automatic setting was cleared
   */
  public boolean resetAutoAdaptiveAcceleration() {
    if (!adaptiveAccelerationAutoManaged) {
      return false;
    }
    adaptiveAcceleration = false;
    adaptiveAccelerationAutoManaged = false;
    resetAdaptiveAcceleration();
    return true;
  }

  /**
   * Whether automatic convergence tuning owns the adaptive-acceleration setting.
   *
   * @return true when the setting is auto-managed
   */
  public boolean isAdaptiveAccelerationAutoManaged() {
    return adaptiveAccelerationAutoManaged;
  }

  /**
   * Whether the current acceleration method was selected by the adaptive logic rather than by the caller.
   *
   * @return true if this loop upgraded itself to an accelerated method
   */
  public boolean isAccelerationAutoUpgraded() {
    return accelerationAutoUpgraded;
  }

  /**
   * Upgrades a stalling direct-substitution loop to Wegstein acceleration.
   *
   * <p>
   * Called once per pass after the errors have been evaluated. A pass counts as stalling when the flow error is still
   * above {@value #ADAPTIVE_STALL_RATIO} of the previous one - i.e. the loop is oscillating or crawling rather than
   * contracting.
   * </p>
   */
  private void updateAdaptiveAcceleration() {
    if (!adaptiveAcceleration || accelerationMethodExplicit
        || accelerationMethod != AccelerationMethod.DIRECT_SUBSTITUTION) {
      return;
    }
    double error = Math.abs(getErrorFlow());
    if (Double.isNaN(error) || Double.isInfinite(error)) {
      return;
    }
    if (!Double.isNaN(previousErrorFlow) && error > ADAPTIVE_STALL_RATIO * previousErrorFlow) {
      stallingPasses++;
    } else {
      stallingPasses = 0;
    }
    previousErrorFlow = error;
    if (stallingPasses >= ADAPTIVE_STALL_PASSES) {
      accelerationMethod = AccelerationMethod.WEGSTEIN;
      accelerationAutoUpgraded = true;
      stallingPasses = 0;
      // The default ceiling of 0.0 only permits extrapolation; an oscillating loop needs a positive q to damp.
      if (!wegsteinQMaxExplicit) {
        wegsteinQMax = ADAPTIVE_WEGSTEIN_Q_MAX;
      }
      resetAccelerationState();
      logger.debug("Recycle {} stalled on direct substitution - switching to Wegstein acceleration", getName());
    }
  }

  /**
   * Gets the minimum bound for Wegstein q-factor.
   *
   * @return the minimum q-factor
   */
  public double getWegsteinQMin() {
    return wegsteinQMin;
  }

  /**
   * Sets the minimum bound for Wegstein q-factor. Default is -5.0. More negative values allow stronger acceleration but
   * risk instability.
   *
   * @param qMin the minimum q-factor
   */
  public void setWegsteinQMin(double qMin) {
    this.wegsteinQMin = qMin;
  }

  /**
   * Gets the maximum bound for Wegstein q-factor.
   *
   * @return the maximum q-factor
   */
  public double getWegsteinQMax() {
    return wegsteinQMax;
  }

  /**
   * Sets the maximum bound for Wegstein q-factor. Default is 0.0 (no acceleration beyond direct substitution). Positive
   * values can help with oscillating systems.
   *
   * @param qMax the maximum q-factor
   */
  public void setWegsteinQMax(double qMax) {
    this.wegsteinQMax = qMax;
    this.wegsteinQMaxExplicit = true;
  }

  /**
   * Gets the number of delay iterations before Wegstein acceleration is applied.
   *
   * @return the delay iterations
   */
  public int getWegsteinDelayIterations() {
    return wegsteinDelayIterations;
  }

  /**
   * Sets the number of delay iterations before Wegstein acceleration is applied. This allows the system to stabilize
   * before acceleration. Default is 2.
   *
   * @param delayIterations number of iterations to delay
   */
  public void setWegsteinDelayIterations(int delayIterations) {
    this.wegsteinDelayIterations = delayIterations;
  }

  /**
   * Gets the current Wegstein q-factors for each variable.
   *
   * @return array of q-factors, or null if not yet calculated
   */
  public double[] getWegsteinQFactors() {
    return wegsteinQFactors != null ? wegsteinQFactors.clone() : null;
  }

  /**
   * Resets the acceleration state for a new convergence cycle.
   */
  public void resetAccelerationState() {
    previousInputValues = null;
    previousOutputValues = null;
    wegsteinQFactors = null;
    if (broydenAccelerator != null) {
      broydenAccelerator.reset();
    }
  }

  /**
   * Gets the Broyden accelerator instance, creating one if needed.
   *
   * @return the Broyden accelerator
   */
  public BroydenAccelerator getBroydenAccelerator() {
    if (broydenAccelerator == null) {
      broydenAccelerator = new BroydenAccelerator();
    }
    return broydenAccelerator;
  }

  /**
   * Extracts the current tear stream values as an array. The array contains: [temperature, pressure, total_flow,
   * mole_fractions...]
   *
   * @param stream the stream to extract values from
   * @return array of stream property values
   */
  private double[] extractStreamValues(StreamInterface stream) {
    SystemInterface fluid = stream.getThermoSystem();
    int numComponents = fluid.getPhase(0).getNumberOfComponents();
    double[] values = new double[3 + numComponents]; // T, P, flow, + compositions

    values[0] = fluid.getTemperature();
    values[1] = fluid.getPressure();
    values[2] = fluid.getFlowRate("mole/sec");

    for (int i = 0; i < numComponents; i++) {
      values[3 + i] = fluid.getPhase(0).getComponent(i).getx();
    }
    return values;
  }

  /**
   * Applies Wegstein acceleration to calculate accelerated values.
   *
   * <p>
   * The Wegstein method uses the formula: x_{n+1} = q * g(x_n) + (1-q) * x_n where q = s / (s - 1) and s is the slope
   * estimate.
   *
   * <p>
   * The q-factor is bounded to prevent divergence: - q between qMin and qMax (typically -5 to 0) - q = 0 corresponds to
   * direct substitution - Negative q provides acceleration for monotonic convergence
   *
   * @param currentInput the input values for current iteration (x_n)
   * @param currentOutput the output values from current iteration (g(x_n))
   * @return accelerated values for next iteration input
   */
  private double[] applyWegsteinAcceleration(double[] currentInput, double[] currentOutput) {
    int n = currentInput.length;
    double[] acceleratedValues = new double[n];

    // Initialize q-factors array if needed
    if (wegsteinQFactors == null) {
      wegsteinQFactors = new double[n];
    }

    // Check if we have previous values for slope calculation
    if (previousInputValues == null || previousOutputValues == null || previousInputValues.length != n) {
      // First iteration with Wegstein - use direct substitution
      wegsteinQFactors = new double[n]; // all zeros = direct substitution
      return currentOutput.clone();
    }

    // Calculate Wegstein acceleration for each variable
    for (int i = 0; i < n; i++) {
      double deltaInput = currentInput[i] - previousInputValues[i];
      double deltaOutput = currentOutput[i] - previousOutputValues[i];

      // Calculate slope s = (g(x_n) - g(x_{n-1})) / (x_n - x_{n-1})
      double slope;
      if (Math.abs(deltaInput) > 1e-15) {
        slope = deltaOutput / deltaInput;
      } else {
        slope = 0.0; // No change, use direct substitution
      }

      // Calculate q-factor: q = s / (s - 1)
      double q;
      if (Math.abs(slope - 1.0) > 1e-10) {
        q = slope / (slope - 1.0);
      } else {
        // slope ≈ 1 means diverging, use minimum q for maximum damping
        q = wegsteinQMin;
      }

      // Bound the q-factor to prevent divergence
      q = Math.max(wegsteinQMin, Math.min(wegsteinQMax, q));
      wegsteinQFactors[i] = q;

      // Apply Wegstein formula: x_{n+1} = q * g(x_n) + (1-q) * x_n
      acceleratedValues[i] = q * currentOutput[i] + (1.0 - q) * currentInput[i];
    }

    return acceleratedValues;
  }

  /**
   * Applies accelerated values to the mixed stream.
   *
   * @param values array containing [temperature, pressure, flow, mole_fractions...]
   */
  private void applyStreamValues(double[] values) {
    SystemInterface fluid = mixedStream.getThermoSystem();
    int numComponents = fluid.getPhase(0).getNumberOfComponents();

    // Only apply composition changes - T, P, and flow are handled elsewhere
    // This is because the recycle primarily needs to converge on composition
    if (values.length >= 3 + numComponents) {
      double[] newFractions = new double[numComponents];
      double sum = 0.0;
      for (int i = 0; i < numComponents; i++) {
        newFractions[i] = Math.max(0.0, values[3 + i]); // Ensure non-negative
        sum += newFractions[i];
      }

      // Normalize to ensure sum = 1
      if (sum > 1e-15) {
        for (int i = 0; i < numComponents; i++) {
          fluid.getPhase(0).getComponent(i).setx(newFractions[i] / sum);
          fluid.getPhase(1).getComponent(i).setx(newFractions[i] / sum);
        }
      }
    }
  }

  /**
   * Applies Wegstein acceleration to the mixed stream using previous iteration data. This method is called during run()
   * when Wegstein acceleration is enabled.
   */
  private void applyWegsteinToStream() {
    // Extract current input (from lastIterationStream) and output (from mixedStream)
    double[] currentInput = extractStreamValues(lastIterationStream);
    double[] currentOutput = extractStreamValues(mixedStream);

    // Apply Wegstein acceleration
    double[] accelerated = applyWegsteinAcceleration(currentInput, currentOutput);

    // Apply accelerated values to stream
    applyStreamValues(accelerated);

    // Store current values for next iteration
    previousInputValues = currentInput;
    previousOutputValues = currentOutput;
  }

  /**
   * Applies Broyden's quasi-Newton acceleration to the mixed stream. This method is called during run() when Broyden
   * acceleration is enabled.
   */
  private void applyBroydenToStream() {
    // Extract current input (from lastIterationStream) and output (from mixedStream)
    double[] currentInput = extractStreamValues(lastIterationStream);
    double[] currentOutput = extractStreamValues(mixedStream);

    // Get or create Broyden accelerator
    BroydenAccelerator accelerator = getBroydenAccelerator();

    // Apply Broyden acceleration
    double[] accelerated = accelerator.accelerate(currentInput, currentOutput);

    // Apply accelerated values to stream
    applyStreamValues(accelerated);
  }

  /**
   * Setter for the field <code>errorTemperature</code>.
   *
   * @param errorTemperature the errorTemperature to set
   */
  public void setErrorTemperature(double errorTemperature) {
    this.errorTemperature = errorTemperature;
  }

  /**
   * Setter for the field <code>errorPressure</code>.
   *
   * @param errorPressure the errorPressure to set
   */
  public void setErrorPressure(double errorPressure) {
    this.errorPressure = errorPressure;
  }

  /**
   * Setter for the field <code>errorFlow</code>.
   *
   * @param errorFlow the error to set
   */
  public void setErrorFlow(double errorFlow) {
    this.errorFlow = errorFlow;
  }

  /**
   * Getter for the field <code>errorFlow</code>.
   *
   * @return a double
   */
  public double getErrorFlow() {
    return errorFlow;
  }

  /**
   * Getter for the field <code>errorTemperature</code>.
   *
   * @return a double
   */
  public double getErrorTemperature() {
    return errorTemperature;
  }

  /**
   * Getter for the field <code>errorPressure</code>.
   *
   * @return a double
   */
  public double getErrorPressure() {
    return errorPressure;
  }

  /**
   * Setter for the field <code>errorComposition</code>.
   *
   * @param errorComposition the error to set
   */
  public void setErrorCompositon(double errorComposition) {
    this.errorComposition = errorComposition;
  }

  /**
   * Getter for the field <code>errorComposition</code>.
   *
   * @return a double
   */
  public double getErrorComposition() {
    return errorComposition;
  }

  /**
   * Getter for the field <code>priority</code>.
   *
   * @return a int
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Setter for the field <code>priority</code>.
   *
   * @param priority a int
   */
  public void setPriority(int priority) {
    this.priority = priority;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * A recycle that has been deactivated by the low-flow cutoff (see {@link #setMinimumFlow(double)}) is reported as
   * solved: it carries no meaningful inventory, so there is nothing left to converge and holding the flowsheet open for
   * it would only burn the iteration budget on a dead leg. A recycle that the user locked inactive is likewise treated
   * as solved, because it never executes.
   * </p>
   *
   * <p>
   * The flow criterion is satisfied when EITHER the {@link #getErrorFlow()} residual is below
   * {@link #getFlowTolerance()} OR the absolute loop-flow change is below {@link #getAbsoluteFlowTolerance()} (kg/hr).
   * The absolute term is disabled by default; enabling it gives a scale-independent criterion, which matters because
   * {@link #flowBalanceCheck()} is an absolute kg/sec residual on small loops and a percentage on large ones.
   * </p>
   */
  @Override
  public boolean solved() {
    if (isLockedInactive() || (!isActive() && iterations > 0)) {
      return true;
    }

    double zeroFlowFloor = Math.max(minimumFlow, 1e-20);
    if (getOutletStream().getFlowRate("kg/hr") < zeroFlowFloor
        && lastIterationStream.getFlowRate("kg/hr") < zeroFlowFloor && iterations > 1) {
      return true;
    }

    boolean flowConverged = Math.abs(this.errorFlow) < flowTolerance || (absoluteFlowTolerance > 0.0
        && Double.isFinite(absoluteFlowChange) && absoluteFlowChange < absoluteFlowTolerance);

    if (Math.abs(this.errorComposition) < compositionTolerance && flowConverged
        && Math.abs(this.errorTemperature) < temperatureTolerance && Math.abs(this.errorPressure) < pressureTolerance
        && iterations > 1) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Getter for the field <code>downstreamProperty</code>.
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<String> getDownstreamProperty() {
    return downstreamProperty;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the tear (recycle) streams fed into this recycle unit via {@link #addStream(StreamInterface)}. Exposing
   * them through the standard inlet accessor lets topology walkers (DOT/Graphviz export, JSON DTO export, DEXPI,
   * auto-instrumentation) trace the recycle loop instead of rendering the recycle unit as an isolated node.
   * </p>
   */
  @Override
  public List<StreamInterface> getInletStreams() {
    return Collections.unmodifiableList(streams);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the converged recycle outlet stream so the recycle loop closes back to its downstream consumer in topology
   * graphs. Falls back to the internal mixed stream when no explicit outlet stream has been assigned via
   * {@link #setOutletStream(StreamInterface)}.
   * </p>
   */
  @Override
  public List<StreamInterface> getOutletStreams() {
    if (outletStream != null) {
      return Collections.singletonList(outletStream);
    }
    if (mixedStream != null) {
      return Collections.singletonList(mixedStream);
    }
    return Collections.emptyList();
  }

  /**
   * Setter for the field <code>outletStream</code>.
   *
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setOutletStream(StreamInterface outletStream) {
    this.outletStream = outletStream;
    lastIterationStream = this.outletStream.clone();
  }

  /**
   * Absolute change in loop mass flow between the last two iterations.
   *
   * <p>
   * Unlike {@link #getErrorFlow()}, which is an absolute kg/sec residual on loops below 1 kg/sec and a percentage above
   * it, this is always a mass flow and can therefore be compared against a physically meaningful limit.
   * </p>
   *
   * @return the absolute loop-flow change in kg/hr, or NaN before the first balance check
   */
  public double getAbsoluteFlowChange() {
    return absoluteFlowChange;
  }

  /**
   * Absolute flow tolerance used by the recycle convergence check.
   *
   * @return the absolute flow tolerance in kg/hr (0.0 means the criterion is disabled)
   */
  public double getAbsoluteFlowTolerance() {
    return absoluteFlowTolerance;
  }

  /**
   * Sets an absolute flow tolerance for the recycle convergence check.
   *
   * <p>
   * The recycle counts as flow-converged when EITHER {@link #getErrorFlow()} is below {@link #getFlowTolerance()} OR
   * the absolute loop-flow change is below this value. This is the standard industrial form of the criterion and is the
   * recommended way to get a scale-independent tolerance, because {@link #flowBalanceCheck()} switches between an
   * absolute kg/sec residual and a percentage at 1 kg/sec. It mirrors
   * {@link neqsim.process.processmodel.ProcessModel#setAbsoluteFlowTolerance(double)} at the recycle level.
   * </p>
   *
   * @param absoluteFlowTolerance the absolute flow tolerance in kg/hr; must be finite and non-negative. Use 0.0 to
   * disable the criterion (the default)
   * @throws IllegalArgumentException if the value is negative or not finite
   */
  public void setAbsoluteFlowTolerance(double absoluteFlowTolerance) {
    if (!Double.isFinite(absoluteFlowTolerance) || absoluteFlowTolerance < 0.0) {
      throw new IllegalArgumentException(
          "absoluteFlowTolerance must be a finite non-negative number, was " + absoluteFlowTolerance);
    }
    this.absoluteFlowTolerance = absoluteFlowTolerance;
    this.absoluteFlowToleranceExplicit = true;
  }

  /**
   * Applies an automatically derived absolute flow tolerance, unless the caller already set one.
   *
   * <p>
   * Used by the process-level auto-tuner so a tear stream is judged on the same flow noise floor as the plant-wide
   * convergence gate. Without it a recycle keeps iterating on a residual the surrounding model already accepts, or -
   * worse - reports itself solved on a looser criterion than the plant demands.
   * </p>
   *
   * @param absoluteFlowTolerance the absolute flow tolerance in kg/hr; must be finite and non-negative
   * @return true if the value was applied, false if an explicit tolerance is already in force
   */
  public boolean applyAutoAbsoluteFlowTolerance(double absoluteFlowTolerance) {
    if (absoluteFlowToleranceExplicit || !Double.isFinite(absoluteFlowTolerance) || absoluteFlowTolerance < 0.0) {
      return false;
    }
    this.absoluteFlowTolerance = absoluteFlowTolerance;
    return true;
  }

  /**
   * Clears an automatically assigned absolute flow tolerance before a fresh process scenario.
   *
   * @return true when an automatic value was cleared, false when the caller owns the tolerance
   */
  public boolean resetAutoAbsoluteFlowTolerance() {
    if (absoluteFlowToleranceExplicit) {
      return false;
    }
    boolean changed = absoluteFlowTolerance != 0.0;
    absoluteFlowTolerance = 0.0;
    return changed;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletFlow += streams.get(i).getThermoSystem().getFlowRate(unit);
    }
    return getOutletStream().getThermoSystem().getFlowRate(unit) - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void removeInputStream(int i) {
    streams.remove(i);
  }

  /** {@inheritDoc} */
  @Override
  public double getMinimumFlow() {
    return minimumFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void setMinimumFlow(double minimumFlow) {
    this.minimumFlow = minimumFlow;
    super.setMinimumFlow(minimumFlow);
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateIdentity == null || transientStateIdentity.trim().isEmpty()) {
      transientStateIdentity = UUID.randomUUID().toString();
    }
    return "equipment:recycle:" + transientStateIdentity;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != Recycle.class) {
      return "recycle subclass " + getClass().getName() + " must extend the snapshot for subclass-owned mutable state";
    }
    String baseIssue = getBaseTransientStateCoverageIssue();
    if (baseIssue != null) {
      return baseIssue;
    }
    if (numberOfInputStreams != streams.size()) {
      return "input-stream count does not match the registered stream identities";
    }
    if (streams.isEmpty() || mixedStream == null || lastIterationStream == null || outletStream == null) {
      return "recycle streams must be fully connected before transient state can be captured";
    }
    for (StreamInterface stream : streams) {
      if (stream == null || stream.getThermoSystem() == null) {
        return "recycle contains a null stream or thermodynamic system";
      }
    }
    if (mixedStream.getThermoSystem() == null || lastIterationStream.getThermoSystem() == null
        || outletStream.getThermoSystem() == null) {
      return "recycle-owned stream thermodynamic state is incomplete";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public TransientState captureTransientState() {
    String coverageIssue = getTransientStateCoverageIssue();
    if (coverageIssue != null) {
      throw new IllegalStateException("Cannot capture recycle '" + getName() + "': " + coverageIssue);
    }
    return new TransientState(this);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(TransientState snapshot) {
    Objects.requireNonNull(snapshot, "recycle transient snapshot cannot be null");
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException("Transient snapshot belongs to another recycle");
    }

    restoreBaseTransientState(snapshot.baseState);
    streams.clear();
    streams.addAll(snapshot.streams);
    downstreamProperty = new ArrayList<String>(snapshot.downstreamProperty);
    numberOfInputStreams = snapshot.numberOfInputStreams;
    mixedStream = snapshot.mixedStream;
    lastIterationStream = snapshot.lastIterationStream;
    outletStream = snapshot.outletStream;
    for (StreamTransientState streamState : snapshot.streamStates) {
      streamState.restore();
    }

    priority = snapshot.priority;
    firstTime = snapshot.firstTime;
    iterations = snapshot.iterations;
    maxIterations = snapshot.maxIterations;
    errorComposition = snapshot.errorComposition;
    errorFlow = snapshot.errorFlow;
    errorTemperature = snapshot.errorTemperature;
    errorPressure = snapshot.errorPressure;
    flowTolerance = snapshot.flowTolerance;
    compositionTolerance = snapshot.compositionTolerance;
    temperatureTolerance = snapshot.temperatureTolerance;
    pressureTolerance = snapshot.pressureTolerance;
    absoluteFlowChange = snapshot.absoluteFlowChange;
    absoluteFlowTolerance = snapshot.absoluteFlowTolerance;
    absoluteFlowToleranceExplicit = snapshot.absoluteFlowToleranceExplicit;
    minimumFlow = snapshot.minimumFlow;
    accelerationMethod = snapshot.accelerationMethod;
    accelerationMethodExplicit = snapshot.accelerationMethodExplicit;
    adaptiveAcceleration = snapshot.adaptiveAcceleration;
    adaptiveAccelerationExplicit = snapshot.adaptiveAccelerationExplicit;
    adaptiveAccelerationAutoManaged = snapshot.adaptiveAccelerationAutoManaged;
    accelerationAutoUpgraded = snapshot.accelerationAutoUpgraded;
    previousErrorFlow = snapshot.previousErrorFlow;
    stallingPasses = snapshot.stallingPasses;
    wegsteinQMaxExplicit = snapshot.wegsteinQMaxExplicit;
    wegsteinQMin = snapshot.wegsteinQMin;
    wegsteinQMax = snapshot.wegsteinQMax;
    wegsteinDelayIterations = snapshot.wegsteinDelayIterations;
    previousInputValues = copyTransientArray(snapshot.previousInputValues);
    previousOutputValues = copyTransientArray(snapshot.previousOutputValues);
    wegsteinQFactors = copyTransientArray(snapshot.wegsteinQFactors);
    if (snapshot.broydenState == null) {
      broydenAccelerator = null;
    } else {
      if (broydenAccelerator == null) {
        broydenAccelerator = new BroydenAccelerator();
      }
      broydenAccelerator.restoreState(snapshot.broydenState);
    }
  }

  private static double[] copyTransientArray(double[] source) {
    return source == null ? null : source.clone();
  }

  private static ArrayList<StreamTransientState> captureStreamStates(Recycle source) {
    ArrayList<StreamTransientState> states = new ArrayList<StreamTransientState>();
    Map<StreamInterface, Boolean> captured = new IdentityHashMap<StreamInterface, Boolean>();
    for (StreamInterface stream : source.streams) {
      captureStreamState(stream, captured, states);
    }
    captureStreamState(source.mixedStream, captured, states);
    captureStreamState(source.lastIterationStream, captured, states);
    captureStreamState(source.outletStream, captured, states);
    return states;
  }

  private static void captureStreamState(StreamInterface stream, Map<StreamInterface, Boolean> captured,
      ArrayList<StreamTransientState> states) {
    if (stream != null && captured.put(stream, Boolean.TRUE) == null) {
      states.add(new StreamTransientState(stream));
    }
  }

  /** Immutable serializable state for one configured recycle. */
  public static final class TransientState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final ProcessEquipmentTransientState baseState;
    private final ArrayList<StreamInterface> streams;
    private final ArrayList<String> downstreamProperty;
    private final int numberOfInputStreams;
    private final StreamInterface mixedStream;
    private final StreamInterface lastIterationStream;
    private final StreamInterface outletStream;
    private final ArrayList<StreamTransientState> streamStates;
    private final int priority;
    private final boolean firstTime;
    private final int iterations;
    private final int maxIterations;
    private final double errorComposition;
    private final double errorFlow;
    private final double errorTemperature;
    private final double errorPressure;
    private final double flowTolerance;
    private final double compositionTolerance;
    private final double temperatureTolerance;
    private final double pressureTolerance;
    private final double absoluteFlowChange;
    private final double absoluteFlowTolerance;
    private final boolean absoluteFlowToleranceExplicit;
    private final double minimumFlow;
    private final AccelerationMethod accelerationMethod;
    private final boolean accelerationMethodExplicit;
    private final boolean adaptiveAcceleration;
    private final boolean adaptiveAccelerationExplicit;
    private final boolean adaptiveAccelerationAutoManaged;
    private final boolean accelerationAutoUpgraded;
    private final double previousErrorFlow;
    private final int stallingPasses;
    private final boolean wegsteinQMaxExplicit;
    private final double wegsteinQMin;
    private final double wegsteinQMax;
    private final int wegsteinDelayIterations;
    private final double[] previousInputValues;
    private final double[] previousOutputValues;
    private final double[] wegsteinQFactors;
    private final BroydenAccelerator.Snapshot broydenState;

    private TransientState(Recycle source) {
      stateIdentity = source.getTransientStateIdentity();
      baseState = source.captureBaseTransientState();
      streams = new ArrayList<StreamInterface>(source.streams);
      downstreamProperty = new ArrayList<String>(source.downstreamProperty);
      numberOfInputStreams = source.numberOfInputStreams;
      mixedStream = source.mixedStream;
      lastIterationStream = source.lastIterationStream;
      outletStream = source.outletStream;
      streamStates = captureStreamStates(source);
      priority = source.priority;
      firstTime = source.firstTime;
      iterations = source.iterations;
      maxIterations = source.maxIterations;
      errorComposition = source.errorComposition;
      errorFlow = source.errorFlow;
      errorTemperature = source.errorTemperature;
      errorPressure = source.errorPressure;
      flowTolerance = source.flowTolerance;
      compositionTolerance = source.compositionTolerance;
      temperatureTolerance = source.temperatureTolerance;
      pressureTolerance = source.pressureTolerance;
      absoluteFlowChange = source.absoluteFlowChange;
      absoluteFlowTolerance = source.absoluteFlowTolerance;
      absoluteFlowToleranceExplicit = source.absoluteFlowToleranceExplicit;
      minimumFlow = source.minimumFlow;
      accelerationMethod = source.accelerationMethod;
      accelerationMethodExplicit = source.accelerationMethodExplicit;
      adaptiveAcceleration = source.adaptiveAcceleration;
      adaptiveAccelerationExplicit = source.adaptiveAccelerationExplicit;
      adaptiveAccelerationAutoManaged = source.adaptiveAccelerationAutoManaged;
      accelerationAutoUpgraded = source.accelerationAutoUpgraded;
      previousErrorFlow = source.previousErrorFlow;
      stallingPasses = source.stallingPasses;
      wegsteinQMaxExplicit = source.wegsteinQMaxExplicit;
      wegsteinQMin = source.wegsteinQMin;
      wegsteinQMax = source.wegsteinQMax;
      wegsteinDelayIterations = source.wegsteinDelayIterations;
      previousInputValues = copyTransientArray(source.previousInputValues);
      previousOutputValues = copyTransientArray(source.previousOutputValues);
      wegsteinQFactors = copyTransientArray(source.wegsteinQFactors);
      broydenState = source.broydenAccelerator == null ? null : source.broydenAccelerator.captureState();
    }
  }

  /** Identity-preserving checkpoint for one stream owned or referenced by the recycle. */
  private static final class StreamTransientState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StreamInterface stream;
    private final String name;
    private final SystemInterface thermoSystem;
    private final UUID calculationIdentifier;
    private final boolean calculateSteadyState;
    private final double time;
    private final boolean runInSteps;
    private final boolean active;
    private final boolean lockedInactive;

    private StreamTransientState(StreamInterface stream) {
      this.stream = stream;
      name = stream.getName();
      thermoSystem = stream.getThermoSystem().clone();
      calculationIdentifier = stream.getCalculationIdentifier();
      calculateSteadyState = stream.getCalculateSteadyState();
      time = stream.getTime();
      runInSteps = stream.isRunInSteps();
      active = stream.isActive();
      lockedInactive = stream.isLockedInactive();
    }

    private void restore() {
      stream.setName(name);
      stream.setThermoSystem(thermoSystem.clone());
      stream.setCalculationIdentifier(calculationIdentifier);
      stream.setCalculateSteadyState(calculateSteadyState);
      stream.setTime(time);
      stream.setRunInSteps(runInSteps);
      stream.setLockedInactive(lockedInactive);
      stream.isActive(active);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(new RecycleResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    RecycleResponse res = new RecycleResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates the recycle setup before execution. Checks that:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>At least one input stream is connected</li>
   * <li>Outlet stream is set</li>
   * <li>Tolerance values are positive</li>
   * </ul>
   *
   * @return validation result with errors and warnings
   */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result = new neqsim.util.validation.ValidationResult(getName());

    // Check: Equipment has a valid name
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Recycle has no name",
          "Set recycle name in constructor: new Recycle(\"MyRecycle\")");
    }

    // Check: At least one input stream is connected
    if (numberOfInputStreams == 0 || streams.isEmpty()) {
      result.addError("stream", "No input streams connected", "Add input stream: recycle.addStream(stream)");
    }

    // Check: Outlet stream is set
    if (outletStream == null) {
      result.addWarning("stream", "Outlet stream not set", "Set outlet stream: recycle.setOutletStream(stream)");
    }

    // Check: Mixed stream is initialized
    if (mixedStream == null) {
      result.addWarning("stream", "Mixed stream not initialized", "Ensure streams are added before running");
    }

    // Check: Tolerance values are positive
    if (flowTolerance <= 0) {
      result.addError("tolerance", "Flow tolerance must be positive: " + flowTolerance,
          "Set positive tolerance: recycle.setFlowTolerance(1e-2)");
    }

    if (compositionTolerance <= 0) {
      result.addError("tolerance", "Composition tolerance must be positive: " + compositionTolerance,
          "Set positive tolerance: recycle.setCompositionTolerance(1e-2)");
    }

    if (temperatureTolerance <= 0) {
      result.addError("tolerance", "Temperature tolerance must be positive: " + temperatureTolerance,
          "Set positive tolerance: recycle.setTemperatureTolerance(1e-2)");
    }

    if (pressureTolerance <= 0) {
      result.addError("tolerance", "Pressure tolerance must be positive: " + pressureTolerance,
          "Set positive tolerance: recycle.setPressureTolerance(1e-2)");
    }

    // Check: Max iterations is reasonable
    if (maxIterations <= 0) {
      result.addError("iterations", "Max iterations must be positive: " + maxIterations,
          "Set positive max iterations: recycle.setMaxIterations(10)");
    }

    return result;
  }
}
