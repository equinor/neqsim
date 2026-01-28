package neqsim.process.equipment.splitter;

import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.splitter.SplitterMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.util.monitor.SplitterResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
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
public class Splitter extends ProcessEquipmentBaseClass
    implements SplitterInterface, CapacityConstrainedEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Splitter.class);

  /** Mechanical design for the splitter. */
  private SplitterMechanicalDesign mechanicalDesign;

  /** Splitter capacity constraints map. */
  private java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> splitterCapacityConstraints =
      new java.util.LinkedHashMap<String, neqsim.process.equipment.capacity.CapacityConstraint>();

  /** Whether capacity analysis is enabled. */
  private boolean splitterCapacityAnalysisEnabled = false;

  /** Design pressure drop [bar]. */
  private double designPressureDrop = 0.05;

  /** Maximum design velocity [m/s]. */
  private double maxDesignVelocity = 30.0;

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
    initMechanicalDesign();
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

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new SplitterMechanicalDesign(this);
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
    // Check if inlet stream needs recalculation first
    if (inletStream.needRecalculation()) {
      return true;
    }
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

    // Store inlet stream values for needRecalculation check (not split stream values)
    lastFlowRate = inletStream.getFluid().getFlowRate("kg/hr");
    lastTemperature = inletStream.getFluid().getTemperature();
    lastPressure = inletStream.getFluid().getPressure();
    lastComposition = inletStream.getFluid().getMolarComposition();
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
      increaseTime(dt);
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
      double[] splits = new double[splitFactor.length];
      double totalFlow = 0.0;
      for (int i = 0; i < splitFactor.length; i++) {
        totalFlow += splits[i];
      }
      for (int i = 0; i < splitFactor.length; i++) {
        splits[i] = splitFactor[i] / totalFlow;
      }
      splitFactor = splits;

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

  /**
   * <p>
   * getSplitNumber.
   * </p>
   *
   * @return number of split outlets
   */
  public int getSplitNumber() {
    return splitNumber;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = getInletStream().getThermoSystem().getFlowRate(unit);
    double outletFlow = 0.0;
    for (int i = 0; i < splitStream.length; i++) {
      outletFlow += splitStream[i].getThermoSystem().getFlowRate(unit);
    }
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new SplitterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    SplitterResponse res = new SplitterResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates the splitter setup before execution. Checks that:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>Inlet stream is connected</li>
   * <li>Split factors are valid (sum to 1.0, all non-negative)</li>
   * <li>At least one split outlet is defined</li>
   * </ul>
   *
   * @return validation result with errors and warnings
   */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result =
        new neqsim.util.validation.ValidationResult(getName());

    // Check: Equipment has a valid name
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Splitter has no name",
          "Set splitter name in constructor: new Splitter(\"MySplitter\")");
    }

    // Check: Inlet stream is connected
    if (inletStream == null) {
      result.addError("stream", "No inlet stream connected",
          "Set inlet stream: splitter.setInletStream(stream) or use constructor");
    } else if (inletStream.getThermoSystem() == null) {
      result.addError("stream", "Inlet stream has no fluid system",
          "Ensure inlet stream has a valid thermodynamic system");
    }

    // Check: At least one split outlet
    if (splitNumber <= 0) {
      result.addError("split", "No split outlets defined (splitNumber=" + splitNumber + ")",
          "Set number of splits: splitter.setSplitNumber(2)");
    }

    // Check: Split factors are valid
    if (splitFactor != null && splitFactor.length > 0) {
      double sum = 0.0;
      boolean hasNegative = false;
      for (int i = 0; i < splitFactor.length; i++) {
        if (splitFactor[i] < 0) {
          hasNegative = true;
        }
        sum += splitFactor[i];
      }

      if (hasNegative) {
        result.addError("split", "Split factors contain negative values",
            "All split factors must be >= 0: splitter.setSplitFactors(new double[]{0.5, 0.5})");
      }

      if (Math.abs(sum - 1.0) > 1e-6 && flowRates == null) {
        result.addWarning("split", "Split factors sum to " + sum + " (expected 1.0)",
            "Split factors are normalized automatically, but verify intended distribution");
      }
    }

    // Check: Split streams are initialized
    if (splitStream == null || splitStream.length != splitNumber) {
      result.addWarning("stream", "Split streams not fully initialized",
          "Call setInletStream() to initialize split streams");
    }

    return result;
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /**
   * Initialize splitter capacity constraints.
   */
  private void initializeSplitterCapacityConstraints() {
    splitterCapacityConstraints.clear();

    // Pressure drop constraint
    if (designPressureDrop > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint dpConstraint =
          new neqsim.process.equipment.capacity.CapacityConstraint("pressureDrop", "bar",
              neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      dpConstraint.setDesignValue(designPressureDrop);
      dpConstraint.setDescription("Pressure drop across splitter");
      dpConstraint.setValueSupplier(() -> {
        // Splitter typically has minimal pressure drop
        // In ideal case, all outlets have same pressure as inlet
        return 0.0; // Ideal splitter
      });
      splitterCapacityConstraints.put("pressureDrop", dpConstraint);
    }

    // Velocity constraint (if mechanical design available)
    if (mechanicalDesign != null && maxDesignVelocity > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint velConstraint =
          new neqsim.process.equipment.capacity.CapacityConstraint("velocity", "m/s",
              neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      velConstraint.setDesignValue(maxDesignVelocity);
      velConstraint.setDescription("Velocity in distribution header");
      velConstraint.setValueSupplier(() -> {
        if (inletStream != null && mechanicalDesign.getHeaderDiameter() > 0) {
          double volumeFlow = inletStream.getFlowRate("m3/hr") / 3600.0;
          double area = Math.PI * Math.pow(mechanicalDesign.getHeaderDiameter() / 2.0, 2);
          return volumeFlow / area;
        }
        return 0.0;
      });
      splitterCapacityConstraints.put("velocity", velConstraint);
    }
  }

  /**
   * Sets the design pressure drop.
   *
   * @param pressureDrop design pressure drop in bar
   */
  public void setDesignPressureDrop(double pressureDrop) {
    this.designPressureDrop = pressureDrop;
    initializeSplitterCapacityConstraints();
  }

  /**
   * Gets the design pressure drop.
   *
   * @return design pressure drop in bar
   */
  public double getDesignPressureDrop() {
    return designPressureDrop;
  }

  /**
   * Sets the maximum design velocity.
   *
   * @param velocity max design velocity in m/s
   */
  public void setMaxDesignVelocity(double velocity) {
    this.maxDesignVelocity = velocity;
    initializeSplitterCapacityConstraints();
  }

  /**
   * Gets the maximum design velocity.
   *
   * @return max design velocity in m/s
   */
  public double getMaxDesignVelocity() {
    return maxDesignVelocity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityAnalysisEnabled() {
    return splitterCapacityAnalysisEnabled;
  }

  /** {@inheritDoc} */
  @Override
  public void setCapacityAnalysisEnabled(boolean enabled) {
    this.splitterCapacityAnalysisEnabled = enabled;
    if (enabled && splitterCapacityConstraints.isEmpty()) {
      initializeSplitterCapacityConstraints();
    }
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    return java.util.Collections.unmodifiableMap(splitterCapacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : splitterCapacityConstraints
        .values()) {
      if (constraint.isEnabled()) {
        double util = constraint.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
          bottleneck = constraint;
        }
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : splitterCapacityConstraints
        .values()) {
      if (constraint.isEnabled() && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : splitterCapacityConstraints
        .values()) {
      if (constraint.isEnabled()
          && constraint
              .getType() == neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD
          && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : splitterCapacityConstraints
        .values()) {
      if (constraint.isEnabled()) {
        maxUtil = Math.max(maxUtil, constraint.getUtilization());
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(
      neqsim.process.equipment.capacity.CapacityConstraint constraint) {
    splitterCapacityConstraints.put(constraint.getName(), constraint);
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return splitterCapacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    splitterCapacityConstraints.clear();
  }
}
