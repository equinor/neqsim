package neqsim.processSimulation.processEquipment.util;

import java.util.ArrayList;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.mixer.MixerInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Recycle class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Recycle extends ProcessEquipmentBaseClass implements MixerInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Recycle.class);

  protected ArrayList<StreamInterface> streams = new ArrayList<StreamInterface>(0);
  private ArrayList<String> downstreamProperty = new ArrayList<String>(0);
  protected int numberOfInputStreams = 0;
  protected StreamInterface mixedStream;
  StreamInterface lastIterationStream = null;
  private StreamInterface outletStream = null;
  private double tolerance = 1e-2;
  private int priority = 100;
  private double error = 1e10;
  private double errorFlow = 1e10;
  boolean firstTime = true;
  int iterations = 0;
  int maxIterations = 10;

  double compositionAccuracy = 1.0;
  double temperatureAccuracy = 1.0;
  double flowAccuracy = 1.0e-2;

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
   * Setter for the field <code>compositionAccuracy</code>.
   * </p>
   *
   * @param compositionAccuracy a double
   */
  public void setCompositionAccuracy(double compositionAccuracy) {
    this.compositionAccuracy = compositionAccuracy;
  }

  /**
   * <p>
   * Setter for the field <code>temperatureAccuracy</code>.
   * </p>
   *
   * @param temperatureAccuracy a double
   */
  public void setTemperatureAccuracy(double temperatureAccuracy) {
    this.temperatureAccuracy = temperatureAccuracy;
  }

  /**
   * <p>
   * Setter for the field <code>flowAccuracy</code>.
   * </p>
   *
   * @param flowAccuracy a double
   */
  public void setFlowAccuracy(double flowAccuracy) {
    this.flowAccuracy = flowAccuracy;
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
   * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
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
   * @param outstream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
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
    if (numberOfInputStreams == 1 && thermoSystem2.getFlowRate("kg/hr") < 1e-100) {
      setError(0);
      setErrorFlow(0);
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
    setError(massBalanceCheck());
    setErrorFlow(massBalanceCheck2());
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
  public double massBalanceCheck2() {
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
   * massBalanceCheck.
   * </p>
   *
   * @return a double
   */
  public double massBalanceCheck() {
    // logger.info("flow rate new " +
    // mixedStream.getThermoSystem().getFlowRate("kg/hr"));
    // logger.info("temperature " +
    // mixedStream.getThermoSystem().getTemperature("C"));
    // logger.info("pressure " +
    // mixedStream.getThermoSystem().getPressure("bara"));
    if (lastIterationStream.getFluid().getNumberOfComponents() != mixedStream.getFluid()
        .getNumberOfComponents()) {
      return 10.0;
    }

    double abs_sum_error = 0.0;
    for (int i = 0; i < mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents(); i++) {
      // logger.info("x last " +
      // lastIterationStream.getThermoSystem().getPhase(0).getComponent(i).getx());
      // logger.info("x new " +
      // mixedStream.getThermoSystem().getPhase(0).getComponent(i).getx());

      abs_sum_error += Math.abs(mixedStream.getThermoSystem().getPhase(0).getComponent(i).getx()
          - lastIterationStream.getThermoSystem().getPhase(0).getComponent(i).getx());
    }

    return abs_sum_error;
  }

  /** {@inheritDoc} */
  @Override
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
   * Getter for the field <code>tolerance</code>.
   * </p>
   *
   * @return the tolerance
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * <p>
   * Setter for the field <code>tolerance</code>.
   * </p>
   *
   * @param tolerance the tolerance to set
   */
  public void setTolerance(double tolerance) {
    this.tolerance = tolerance;
  }

  /**
   * <p>
   * Getter for the field <code>error</code>.
   * </p>
   *
   * @return the error
   */
  public double getError() {
    return error;
  }

  /**
   * <p>
   * Setter for the field <code>error</code>.
   * </p>
   *
   * @param error the error to set
   */
  public void setError(double error) {
    this.error = error;
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
    if (Math.abs(this.error) < tolerance && Math.abs(this.errorFlow) < flowAccuracy
        && iterations > 1) {
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
   * @param outletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
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
}
