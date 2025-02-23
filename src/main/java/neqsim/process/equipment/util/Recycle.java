package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Recycle class.
 * </p>
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
    /*
     * if(firstTime || iterations>maxIterations) { firstTime=false; return; }
     */
    double enthalpy = 0.0;
    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    if (numberOfInputStreams == 1 && thermoSystem2.getFlowRate("kg/hr") < minimumFlow) {
      setErrorCompositon(0.0);
      setErrorFlow(0.0);
      setErrorTemperature(0.0);
      setErrorPressure(0.0);
      return;
    }
    mixedStream.setThermoSystem(thermoSystem2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
    if (streams.size() > 1) {
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().reInitPhaseType();
      mixedStream.getThermoSystem().init(0);

      mixStream();

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

  /**
   * <p>
   * setTemperature.
   * </p>
   *
   * @param temp a double
   */
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
    if (getOutletStream().getFlowRate("kg/hr") < 1e-20 && iterations > 1)
      return true;

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
  public void removeInputStream(int i) {
    streams.remove(i);
  }

  /**
   * Gets the minimum flow rate for the pump.
   *
   * @return the minimum flow rate
   */
  public double getMinimumFlow() {
    return minimumFlow;
  }

  /**
   * Sets the minimum flow rate for the pump.
   *
   * @param minimumFlow the minimum flow rate to be set, in appropriate units.
   */
  public void setMinimumFlow(double minimumFlow) {
    this.minimumFlow = minimumFlow;
  }
}
