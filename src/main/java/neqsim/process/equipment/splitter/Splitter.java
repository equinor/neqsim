package neqsim.process.equipment.splitter;

import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Splitter class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Splitter extends ProcessEquipmentBaseClass implements SplitterInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Splitter.class);

  SystemInterface thermoSystem;
  SystemInterface gasSystem;
  SystemInterface waterSystem;
  SystemInterface liquidSystem;
  SystemInterface thermoSystemCloned;
  StreamInterface inletStream;
  StreamInterface[] splitStream;
  protected int splitNumber = 1;
  double[] splitFactor = new double[1];
  double[] flowRates;
  String flowUnit = "mole/sec";
  protected double[] oldSplitFactor = null;
  protected double lastTemperature = 0.0;
  protected double lastPressure = 0.0;
  protected double lastFlowRate = 0.0;
  protected double[] lastComposition = null;

  /**
   * Constructor for Splitter.
   *
   * @param name name of splitter
   */
  public Splitter(String name) {
    super(name);
  }

  /**
   * Constructor for Splitter.
   *
   * @param name name of splitter
   * @param inStream input stream
   */
  public Splitter(String name, StreamInterface inStream) {
    this(name);
    this.setInletStream(inStream);
  }

  /**
   * <p>
   * Constructor for Splitter.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param number_of_splits an int
   */
  public Splitter(String name, StreamInterface inletStream, int number_of_splits) {
    this(name);
    setSplitNumber(number_of_splits);
    this.setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setSplitNumber(int number_of_splits) {
    splitNumber = number_of_splits;
    splitFactor = new double[splitNumber];
    splitFactor[0] = 1.0;
    if (inletStream != null) {
      setInletStream(inletStream);
    }
  }

  /**
   * <p>
   * setSplitFactors.
   * </p>
   *
   * @param splitFact an array of type double
   */
  public void setSplitFactors(double[] splitFact) {
    double sum = 0.0;
    for (int i = 0; i < splitFact.length; i++) {
      if (splitFact[i] < 0.0) {
        splitFact[i] = 0.0;
      }
      sum += splitFact[i];
    }
    splitFactor = new double[splitFact.length];
    for (int i = 0; i < splitFact.length; i++) {
      splitFactor[i] = splitFact[i] / sum;
    }
    splitNumber = splitFact.length;
    flowRates = null;
    setInletStream(inletStream);
  }

  /**
   * <p>
   * setFlowRates.
   * </p>
   *
   * @param flowRates an array of type double
   * @param flowUnit a {@link java.lang.String} object
   */
  public void setFlowRates(double[] flowRates, String flowUnit) {
    if (flowRates.length != splitNumber) {
      setInletStream(inletStream);
    }
    this.flowRates = flowRates;
    this.flowUnit = flowUnit;

    splitNumber = flowRates.length;
    splitFactor = new double[flowRates.length];
    splitFactor[0] = 1.0;
    setInletStream(inletStream);
  }

  /**
   * <p>
   * calcSplitFactors.
   * </p>
   */
  public void calcSplitFactors() {
    double sum = 0.0;
    for (int i = 0; i < flowRates.length; i++) {
      if (flowRates[i] > 0.0) {
        sum += flowRates[i];
      }
    }

    double missingFlowRate = 0.0;
    for (int i = 0; i < flowRates.length; i++) {
      if (flowRates[i] < -0.1) {
        missingFlowRate = inletStream.getFlowRate(flowUnit) - sum;
        sum += missingFlowRate;
      }
    }

    splitFactor = new double[flowRates.length];
    for (int i = 0; i < flowRates.length; i++) {
      splitFactor[i] = flowRates[i] / sum;
      if (flowRates[i] < -0.1) {
        splitFactor[i] = missingFlowRate / sum;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    if (splitStream == null || splitStream.length != splitNumber) {
      splitStream = new Stream[splitNumber];
      try {
        for (int i = 0; i < splitNumber; i++) {
          splitStream[i] = new Stream("Split Stream_" + i, inletStream.getThermoSystem().clone());
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getSplitStream(int i) {
    return splitStream[i];
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    if (inletStream.getFluid().getTemperature() == lastTemperature
        && inletStream.getFluid().getPressure() == lastPressure
        && Math.abs(inletStream.getFluid().getFlowRate("kg/hr") - lastFlowRate)
            / inletStream.getFluid().getFlowRate("kg/hr") < 1e-6
        && Arrays.equals(splitFactor, oldSplitFactor)) {
      return false;
    } else {
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double totSplit = 0.0;

    if (flowRates != null) {
      calcSplitFactors();
    }

    for (int i = 0; i < splitNumber; i++) {
      if (splitFactor[i] < 0) {
        logger.debug("split factor negative = " + splitFactor[i]);
        splitFactor[i] = 0.0;
      }
      totSplit += splitFactor[i];
    }
    if (Math.abs(totSplit - 1.0) > 1e-10) {
      logger.debug("total split factor different from 0 in splitter - totsplit = " + totSplit);
      logger.debug("setting first split to = " + (1.0 - totSplit));
      splitFactor[0] = 1.0 - totSplit;
    }

    for (int i = 0; i < splitNumber; i++) {
      thermoSystem = inletStream.getThermoSystem().clone();
      thermoSystem.init(0);
      splitStream[i].setThermoSystem(thermoSystem);
      for (int j = 0; j < inletStream.getThermoSystem().getPhase(0).getNumberOfComponents(); j++) {
        int index = inletStream.getThermoSystem().getPhase(0).getComponent(j).getComponentNumber();
        double moles = inletStream.getThermoSystem().getPhase(0).getComponent(j).getNumberOfmoles();
        double change = (moles * splitFactor[i] - moles > 0) ? 0.0 : moles * splitFactor[i] - moles;
        splitStream[i].getThermoSystem().addComponent(index, change);
      }
      ThermodynamicOperations thermoOps =
          new ThermodynamicOperations(splitStream[i].getThermoSystem());
      thermoOps.TPflash();
    }

    lastFlowRate = thermoSystem.getFlowRate("kg/hr");
    lastTemperature = thermoSystem.getTemperature();
    lastPressure = thermoSystem.getPressure();
    lastComposition = thermoSystem.getMolarComposition();
    oldSplitFactor = Arrays.copyOf(splitFactor, splitFactor.length);

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
    } else {
      Mixer mixer = new Mixer("tmpMixer");
      for (int i = 0; i < splitStream.length; i++) {
        splitStream[i].setPressure(inletStream.getPressure());
        splitStream[i].setTemperature(inletStream.getTemperature("C"), "C");
        splitStream[i].run();
        mixer.addStream(splitStream[i]);
      }
      mixer.run();

      inletStream.setThermoSystem(mixer.getThermoSystem());
      inletStream.run();

      lastFlowRate = thermoSystem.getFlowRate("kg/hr");
      lastTemperature = thermoSystem.getTemperature();
      lastPressure = thermoSystem.getPressure();
      lastComposition = thermoSystem.getMolarComposition();
      oldSplitFactor = Arrays.copyOf(splitFactor, splitFactor.length);
      setCalculationIdentifier(id);
    }
  }

  /**
   * <p>
   * Getter for the field <code>splitFactor</code>.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getSplitFactor(int i) {
    return splitFactor[i];
  }

  /**
   * <p>
   * getSplitFactors.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getSplitFactors() {
    return splitFactor;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}
}
