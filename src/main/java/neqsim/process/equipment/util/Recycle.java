package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.stream.StreamInterface;
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
 * This class implements convergence acceleration methods for recycle calculations, including direct
 * substitution, Wegstein acceleration, and Broyden's method.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Recycle extends ProcessEquipmentBaseClass implements MixerInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
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

  private double minimumFlow = 1e-20;

  // Acceleration method settings
  private AccelerationMethod accelerationMethod = AccelerationMethod.DIRECT_SUBSTITUTION;

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
  private transient BroydenAccelerator broydenAccelerator = null;

  /**
   * <p>
   * Constructor for Recycle.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Recycle(String name) {
    super(name);
  }

  /**
   * <p>
   * Setter for the field <code>compositionTolerance</code>.
   * </p>
   *
   * @param compositionTolerance a double
   */
  public void setCompositionTolerance(double compositionTolerance) {
    this.compositionTolerance = compositionTolerance;
  }

  /**
   * <p>
   * Getter for the field <code>compositionTolerance</code>.
   * </p>
   *
   * @return a double
   */
  public double getCompositionTolerance() {
    return this.compositionTolerance;
  }

  /**
   * <p>
   * Setter for the field <code>temperatureTolerance</code>.
   * </p>
   *
   * @param temperatureTolerance a double in % error
   */
  public void setTemperatureTolerance(double temperatureTolerance) {
    this.temperatureTolerance = temperatureTolerance;
  }

  /**
   * <p>
   * Getter for the field <code>temperatureTolerance</code>.
   * </p>
   *
   * @return a double
   */
  public double getTemperatureTolerance() {
    return this.temperatureTolerance;
  }

  /**
   * <p>
   * Setter for the field <code>flowTolerance</code>.
   * </p>
   *
   * @param flowTolerance a double
   */
  public void setFlowTolerance(double flowTolerance) {
    this.flowTolerance = flowTolerance;
  }

  /**
   * <p>
   * Getter for the field <code>flowTolerance</code>.
   * </p>
   *
   * @return a double
   */
  public double getFlowTolerance() {
    return this.flowTolerance;
  }

  /**
   * <p>
   * resetIterations.
   * </p>
   */
  public void resetIterations() {
    iterations = 0;
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
   * <p>
   * Setter for the field <code>downstreamProperty</code>.
   * </p>
   *
   * @param property a {@link java.util.ArrayList} object
   */
  public void setDownstreamProperty(ArrayList<String> property) {
    this.downstreamProperty = property;
  }

  /**
   * <p>
   * Setter for the field <code>downstreamProperty</code>.
   * </p>
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
   * <p>
   * getStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getStream(int i) {
    return streams.get(i);
  }

  /**
   * <p>
   * mixStream.
   * </p>
   */
  public void mixStream() {
    int index = 0;
    // String compName = new String();

    for (int k = 1; k < streams.size(); k++) {
      for (int i = 0; i < streams.get(k).getThermoSystem().getPhase(0)
          .getNumberOfComponents(); i++) {
        boolean gotComponent = false;
        String componentName =
            streams.get(k).getThermoSystem().getPhase(0).getComponent(i).getName();
        // logger.info("adding: " + componentName);
        // int numberOfPhases = streams.get(k).getThermoSystem().getNumberOfPhases();

        double moles =
            streams.get(k).getThermoSystem().getPhase(0).getComponent(i).getNumberOfmoles();
        // logger.info("moles: " + moles + " " +
        // mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents());
        for (int p = 0; p < mixedStream.getThermoSystem().getPhase(0)
            .getNumberOfComponents(); p++) {
          if (mixedStream.getThermoSystem().getPhase(0).getComponent(p).getName()
              .equals(componentName)) {
            gotComponent = true;
            index =
                streams.get(0).getThermoSystem().getPhase(0).getComponent(p).getComponentNumber();
            // compName = streams.get(0).getThermoSystem().getPhase(0).getComponent(p)
            // .getComponentName();
          }
        }

        if (gotComponent) {
          // logger.info("adding moles starting....");
          mixedStream.getThermoSystem().addComponent(index, moles);
          // mixedStream.getThermoSystem().init_x_y();
          // logger.info("adding moles finished");
        } else {
          logger.warn("ikke gaa hit");
          mixedStream.getThermoSystem().addComponent(index, moles);
        }
      }
    }
    // mixedStream.getThermoSystem().init_x_y();
    // mixedStream.getThermoSystem().initBeta();
    // mixedStream.getThermoSystem().init(2);
  }

  /**
   * <p>
   * guessTemperature.
   * </p>
   *
   * @return a double
   */
  public double guessTemperature() {
    double gtemp = 0;
    for (int k = 0; k < streams.size(); k++) {
      gtemp += streams.get(k).getThermoSystem().getTemperature()
          * streams.get(k).getThermoSystem().getNumberOfMoles()
          / mixedStream.getThermoSystem().getNumberOfMoles();
    }
    return gtemp;
  }

  /**
   * <p>
   * calcMixStreamEnthalpy.
   * </p>
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
   * <p>
   * initiateDownstreamProperties.
   * </p>
   *
   * @param outstream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void initiateDownstreamProperties(StreamInterface outstream) {
    lastIterationStream = outstream.clone();
  }

  /**
   * <p>
   * setDownstreamProperties.
   * </p>
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
      isActive(false);
      mixedStream.setThermoSystem(thermoSystem2);
      setErrorCompositon(0.0);
      setErrorFlow(flowBalanceCheck());
      setErrorTemperature(temperatureBalanceCheck());
      setErrorPressure(pressureBalanceCheck());
      outletStream.setThermoSystem(mixedStream.getThermoSystem());
      outletStream.setCalculationIdentifier(id);
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
        isActive(false);
        mixedStream.setThermoSystem(thermoSystem2);
        setErrorCompositon(0.0);
        setErrorFlow(flowBalanceCheck());
        setErrorTemperature(temperatureBalanceCheck());
        setErrorPressure(pressureBalanceCheck());
        outletStream.setThermoSystem(mixedStream.getThermoSystem());
        outletStream.setCalculationIdentifier(id);
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
   * <p>
   * massBalanceCheck.
   * </p>
   *
   * @return a double
   */
  public double flowBalanceCheck() {
    double abs_sum_errorFlow = 0.0;
    if (mixedStream.getFlowRate("kg/sec") < 1.0) {
      abs_sum_errorFlow +=
          Math.abs(mixedStream.getFlowRate("kg/sec") - lastIterationStream.getFlowRate("kg/sec"));
    } else {
      abs_sum_errorFlow +=
          Math.abs(mixedStream.getFlowRate("kg/sec") - lastIterationStream.getFlowRate("kg/sec"))
              / mixedStream.getFlowRate("kg/sec") * 100.0;
    }
    return abs_sum_errorFlow;
  }

  /**
   * <p>
   * compositionBalanceCheck.
   * </p>
   *
   * @return a double
   */
  public double compositionBalanceCheck() {
    if (lastIterationStream.getFluid().getNumberOfComponents() != mixedStream.getFluid()
        .getNumberOfComponents()) {
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
   * <p>
   * temperatureBalanceCheck.
   * </p>
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
   * <p>
   * pressureBalanceCheck.
   * </p>
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
  public void displayResult() {}

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
   * <p>
   * Setter for the tolerance fields.
   * </p>
   * Set tolerances to tolerance input.
   *
   * @param tolerance the tolerance to set
   */
  public void setTolerance(double tolerance) {
    this.flowTolerance = tolerance;
    this.temperatureTolerance = tolerance;
    this.compositionTolerance = tolerance;
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
    // Reset acceleration state when method changes
    resetAccelerationState();
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
   * Sets the minimum bound for Wegstein q-factor. Default is -5.0. More negative values allow
   * stronger acceleration but risk instability.
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
   * Sets the maximum bound for Wegstein q-factor. Default is 0.0 (no acceleration beyond direct
   * substitution). Positive values can help with oscillating systems.
   *
   * @param qMax the maximum q-factor
   */
  public void setWegsteinQMax(double qMax) {
    this.wegsteinQMax = qMax;
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
   * Sets the number of delay iterations before Wegstein acceleration is applied. This allows the
   * system to stabilize before acceleration. Default is 2.
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
   * Extracts the current tear stream values as an array. The array contains: [temperature,
   * pressure, total_flow, mole_fractions...]
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
   * The Wegstein method uses the formula: x_{n+1} = q * g(x_n) + (1-q) * x_n where q = s / (s - 1)
   * and s is the slope estimate.
   *
   * <p>
   * The q-factor is bounded to prevent divergence: - q between qMin and qMax (typically -5 to 0) -
   * q = 0 corresponds to direct substitution - Negative q provides acceleration for monotonic
   * convergence
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
    if (previousInputValues == null || previousOutputValues == null
        || previousInputValues.length != n) {
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
   * Applies Wegstein acceleration to the mixed stream using previous iteration data. This method is
   * called during run() when Wegstein acceleration is enabled.
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
   * Applies Broyden's quasi-Newton acceleration to the mixed stream. This method is called during
   * run() when Broyden acceleration is enabled.
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
   * <p>
   * Setter for the field <code>errorTemperature</code>.
   * </p>
   *
   * @param errorTemperature the errorTemperature to set
   */
  public void setErrorTemperature(double errorTemperature) {
    this.errorTemperature = errorTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>errorPressure</code>.
   * </p>
   *
   * @param errorPressure the errorPressure to set
   */
  public void setErrorPressure(double errorPressure) {
    this.errorPressure = errorPressure;
  }

  /**
   * <p>
   * Setter for the field <code>errorFlow</code>.
   * </p>
   *
   * @param errorFlow the error to set
   */
  public void setErrorFlow(double errorFlow) {
    this.errorFlow = errorFlow;
  }

  /**
   * <p>
   * Getter for the field <code>errorFlow</code>.
   * </p>
   *
   * @return a double
   */
  public double getErrorFlow() {
    return errorFlow;
  }

  /**
   * <p>
   * Getter for the field <code>errorTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getErrorTemperature() {
    return errorTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>errorPressure</code>.
   * </p>
   *
   * @return a double
   */
  public double getErrorPressure() {
    return errorPressure;
  }

  /**
   * <p>
   * Setter for the field <code>errorComposition</code>.
   * </p>
   *
   * @param errorComposition the error to set
   */
  public void setErrorCompositon(double errorComposition) {
    this.errorComposition = errorComposition;
  }

  /**
   * <p>
   * Getter for the field <code>errorComposition</code>.
   * </p>
   *
   * @return a double
   */
  public double getErrorComposition() {
    return errorComposition;
  }

  /**
   * <p>
   * Getter for the field <code>priority</code>.
   * </p>
   *
   * @return a int
   */
  public int getPriority() {
    return priority;
  }

  /**
   * <p>
   * Setter for the field <code>priority</code>.
   * </p>
   *
   * @param priority a int
   */
  public void setPriority(int priority) {
    this.priority = priority;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    if (getOutletStream().getFlowRate("kg/hr") < 1e-20
        && lastIterationStream.getFlowRate("kg/hr") < 1e-20 && iterations > 1) {
      return true;
    }

    if (Math.abs(this.errorComposition) < compositionTolerance
        && Math.abs(this.errorFlow) < flowTolerance
        && Math.abs(this.errorTemperature) < temperatureTolerance
        && Math.abs(this.errorPressure) < pressureTolerance && iterations > 1) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * <p>
   * Getter for the field <code>downstreamProperty</code>.
   * </p>
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
   * <p>
   * Setter for the field <code>outletStream</code>.
   * </p>
   *
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setOutletStream(StreamInterface outletStream) {
    this.outletStream = outletStream;
    lastIterationStream = this.outletStream.clone();
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
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new RecycleResponse(this));
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
}
