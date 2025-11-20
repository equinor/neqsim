package neqsim.process.equipment.splitter;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.ComponentSplitterResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
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
public class ComponentSplitter extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentSplitter.class);

  SystemInterface thermoSystem;
  StreamInterface inletStream;
  StreamInterface[] splitStream;
  protected int splitNumber = 1;
  double[] splitFactor = new double[1];

  /**
   * Constructor for Splitter.
   *
   * @param name name of splitter
   */
  public ComponentSplitter(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Splitter.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public ComponentSplitter(String name, StreamInterface inletStream) {
    this(name);
    this.setInletStream(inletStream);
  }

  /**
   * <p>
   * Getter for the field <code>inletStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * <p>
   * setSplitFactors.
   * </p>
   *
   * @param factors an array of type double
   */
  public void setSplitFactors(double[] factors) {
    splitFactor = factors;
  }

  /**
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   *
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    splitStream = new Stream[2];
    try {
      for (int i = 0; i < splitStream.length; i++) {
        // todo: why not inletStream.clone("Split Stream_" + i)
        splitStream[i] = new Stream("Split Stream_" + i, inletStream.getThermoSystem().clone());
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * Getter for the field <code>splitStream</code>.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getSplitStream(int i) {
    return splitStream[i];
  }

  /**
   * <p>
   * Getter for the field <code>splitNumber</code>.
   * </p>
   *
   * @return number of split outlets
   */
  public int getSplitNumber() {
    return splitStream != null ? splitStream.length : splitNumber;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    for (int i = 0; i < 2; i++) {
      thermoSystem = inletStream.getThermoSystem().clone();
      thermoSystem.setEmptyFluid();
      double totalMoles = 0.0;
      if (i == 0) {
        for (int k = 0; k < thermoSystem.getNumberOfComponents(); k++) {
          double moles =
              inletStream.getThermoSystem().getComponent(k).getNumberOfmoles() * splitFactor[k];
          thermoSystem.addComponent(k, moles);
          totalMoles += moles;
        }
      } else {
        for (int k = 0; k < thermoSystem.getNumberOfComponents(); k++) {
          double moles = inletStream.getThermoSystem().getComponent(k).getNumberOfmoles()
              * (1.0 - splitFactor[k]);
          thermoSystem.addComponent(k, moles);
          totalMoles += moles;
        }
      }

      if (totalMoles > 0.0) {
        thermoSystem.init(0);
        splitStream[i].setThermoSystem(thermoSystem);
        ThermodynamicOperations thermoOps =
            new ThermodynamicOperations(splitStream[i].getThermoSystem());
        thermoOps.TPflash();
      } else {
        splitStream[i].setThermoSystem(thermoSystem);
      }
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = inletStream.getThermoSystem().getFlowRate(unit);
    double outletFlow = 0.0;
    for (int i = 0; i < splitStream.length; i++) {
      outletFlow += splitStream[i].getThermoSystem().getFlowRate(unit);
    }
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new ComponentSplitterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    ComponentSplitterResponse res = new ComponentSplitterResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}
}
