package neqsim.process.equipment;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * Abstract class defining ProcessEquipment with one inlet and one outlet.
 *
 * @author ASMF
 * @version $Id: $Id
 */
public abstract class TwoPortEquipment extends ProcessEquipmentBaseClass
    implements TwoPortInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected StreamInterface inStream;
  protected StreamInterface outStream;

  /**
   * Constructor for TwoPortEquipment.
   *
   * @param name Name of TwoPortEquipment
   */
  public TwoPortEquipment(String name) {
    super(name);
  }

  /**
   * Constructor for TwoPortEquipment.
   *
   * @param name Name of TwoPortEquipment
   * @param stream Stream to set as inlet Stream. A clone of stream is set as outlet stream.
   */
  public TwoPortEquipment(String name, StreamInterface stream) {
    this(name);
    this.setInletStream(stream);
  }

  /** {@inheritDoc} */
  @Override
  public double getInletPressure() {
    return getInletStream().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getInletStream() {
    return inStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getInletTemperature() {
    return getInletStream().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletPressure() {
    return getOutletStream().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutletStream() {
    return outStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletTemperature() {
    return getOutletStream().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public void setInletPressure(double pressure) {
    this.inStream.setPressure(pressure);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    this.inStream = stream;
    this.outStream = inStream.clone(this.getName() + " out stream");
  }

  /** {@inheritDoc} */
  @Override
  public void setInletTemperature(double temperature) {
    this.inStream.setTemperature(temperature, "unit");
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.outStream.setPressure(pressure);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletStream(StreamInterface stream) {
    this.outStream = stream;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletTemperature(double temperature) {
    this.outStream.setTemperature(temperature, "unit");
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    return outStream.getThermoSystem().getFlowRate(unit)
        - inStream.getThermoSystem().getFlowRate(unit);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    return toJson();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates the two-port equipment setup before execution. Checks that:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>Inlet stream is connected</li>
   * <li>Outlet stream is initialized</li>
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
      result.addError("equipment", "Equipment has no name", "Set equipment name in constructor");
    }

    // Check: Inlet stream is connected
    if (inStream == null) {
      result.addError("stream", "No inlet stream connected",
          "Set inlet stream: equipment.setInletStream(stream)");
    } else if (inStream.getThermoSystem() == null) {
      result.addError("stream", "Inlet stream has no fluid system",
          "Ensure inlet stream has a valid thermodynamic system");
    }

    // Check: Outlet stream is initialized
    if (outStream == null) {
      result.addWarning("stream", "Outlet stream not initialized",
          "Outlet stream is typically created when inlet stream is set");
    }

    return result;
  }
}
