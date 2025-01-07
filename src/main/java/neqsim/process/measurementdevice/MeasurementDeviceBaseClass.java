package neqsim.process.measurementdevice;

import neqsim.process.measurementdevice.online.OnlineSignal;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * Abstract MeasurementDeviceBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class MeasurementDeviceBaseClass extends NamedBaseClass
    implements MeasurementDeviceInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected String unit;

  private double maximumValue = 1.0;
  private double minimumValue = 0.0;

  private boolean logging = false;
  private OnlineSignal onlineSignal = null;
  private boolean isOnlineSignal = false;
  private double onlineMeasurementValue = 0.0;
  private String onlineMeasurementValueUnit = "";

  private boolean conditionAnalysis = true;
  private String conditionAnalysisMessage = "";
  private double conditionAnalysisMaxDeviation = 0.0;

  /**
   * Constructor for MeasurementDeviceBaseClass.
   *
   * @param name Name of the object
   * @param unit Engineering unit of the measurement
   */
  public MeasurementDeviceBaseClass(String name, String unit) {
    super(name);
    setUnit(unit);
  }

  /** {@inheritDoc} */
  @Override
  public OnlineSignal getOnlineSignal() {
    return onlineSignal;
  }

  /**
   * <p>
   * Setter for the field <code>onlineSignal</code>.
   * </p>
   *
   * @param onlineSignal the onlineSignal to set
   */
  public void setOnlineSignal(OnlineSignal onlineSignal) {
    this.onlineSignal = onlineSignal;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnlineSignal() {
    return isOnlineSignal;
  }

  /**
   * <p>
   * Setter for the field <code>isOnlineSignal</code>.
   * </p>
   *
   * @param isOnlineSignal the isOnlineSignal to set
   * @param plantName a {@link java.lang.String} object
   * @param transmitterame a {@link java.lang.String} object
   */
  public void setIsOnlineSignal(boolean isOnlineSignal, String plantName, String transmitterame) {
    this.isOnlineSignal = isOnlineSignal;
    onlineSignal = new OnlineSignal(plantName, transmitterame);
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit() {
    return unit;
  }

  /** {@inheritDoc} */
  @Override
  public void setUnit(String unit) {
    this.unit = unit;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaximumValue() {
    return maximumValue;
  }

  /** {@inheritDoc} */
  @Override
  public void setMaximumValue(double maximumValue) {
    this.maximumValue = maximumValue;
  }

  /** {@inheritDoc} */
  @Override
  public double getMinimumValue() {
    return minimumValue;
  }

  /** {@inheritDoc} */
  @Override
  public void setMinimumValue(double minimumValue) {
    this.minimumValue = minimumValue;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredPercentValue() {
    return (getMeasuredValue() - minimumValue) / (maximumValue - minimumValue) * 100;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isLogging() {
    return logging;
  }

  /** {@inheritDoc} */
  @Override
  public void setLogging(boolean logging) {
    this.logging = logging;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured value: " + getMeasuredValue());
  }

  /**
   * <p>
   * Setter for the field <code>onlineMeasurementValue</code>.
   * </p>
   *
   * @param value a double
   * @param unit a {@link java.lang.String} object
   */
  public void setOnlineMeasurementValue(double value, String unit) {
    onlineMeasurementValue = value;
    onlineMeasurementValueUnit = unit;
  }

  /**
   * <p>
   * Getter for the field <code>onlineMeasurementValue</code>.
   * </p>
   *
   * @return a double
   */
  public double getOnlineMeasurementValue() {
    return onlineMeasurementValue;
  }

  /**
   * <p>
   * doConditionAnalysis.
   * </p>
   *
   * @return a boolean
   */
  public boolean doConditionAnalysis() {
    return conditionAnalysis;
  }

  /**
   * <p>
   * Setter for the field <code>conditionAnalysis</code>.
   * </p>
   *
   * @param conditionMonitor a boolean
   */
  public void setConditionAnalysis(boolean conditionMonitor) {
    this.conditionAnalysis = conditionMonitor;
  }

  /**
   * <p>
   * runConditionAnalysis.
   * </p>
   */
  public void runConditionAnalysis() {
    if (Math.abs(getMeasuredValue(onlineMeasurementValueUnit)
        - onlineMeasurementValue) < getConditionAnalysisMaxDeviation()) {
      conditionAnalysisMessage = "ok";
    } else {
      conditionAnalysisMessage = "fail";
    }
  }

  /**
   * <p>
   * Getter for the field <code>conditionAnalysisMessage</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getConditionAnalysisMessage() {
    return conditionAnalysisMessage;
  }

  /**
   * <p>
   * setQualityCheckMessage.
   * </p>
   *
   * @param conditionAnalysisMessage a {@link java.lang.String} object
   */
  public void setQualityCheckMessage(String conditionAnalysisMessage) {
    this.conditionAnalysisMessage = conditionAnalysisMessage;
  }

  /**
   * <p>
   * Getter for the field <code>conditionAnalysisMaxDeviation</code>.
   * </p>
   *
   * @return a double
   */
  public double getConditionAnalysisMaxDeviation() {
    return conditionAnalysisMaxDeviation;
  }

  /**
   * <p>
   * Setter for the field <code>conditionAnalysisMaxDeviation</code>.
   * </p>
   *
   * @param conditionAnalysisMaxDeviation a double
   */
  public void setConditionAnalysisMaxDeviation(double conditionAnalysisMaxDeviation) {
    this.conditionAnalysisMaxDeviation = conditionAnalysisMaxDeviation;
  }
}
