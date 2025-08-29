package neqsim.process.util.monitor;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * <p>
 * BaseResponse class.
 * </p>
 *
 * @author esol
 */
public class BaseResponse {
  public String tagName = "";
  public String name = "";

  /**
   * <p>
   * Constructor for BaseResponse.
   * </p>
   */
  public BaseResponse() {
    // Default constructor
  }

  /**
   * <p>
   * Constructor for BaseResponse.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public BaseResponse(ProcessEquipmentInterface equipment) {
    tagName = equipment.getTagName();
    name = equipment.getName();
  }

  /**
   * <p>
   * Constructor for BaseResponse.
   * </p>
   *
   * @param equipment a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface} object
   */
  public BaseResponse(MeasurementDeviceInterface equipment) {
    tagName = equipment.getTagName();
    name = equipment.getName();
  }

  /**
   * Apply reporting configuration - default does nothing.
   *
   * @param cfg a {@link neqsim.process.util.report.ReportConfig} object
   */
  public void applyConfig(ReportConfig cfg) {}

  /**
   * Determine detail level for this response based on config.
   *
   * @param cfg a {@link neqsim.process.util.report.ReportConfig} object
   * @return a {@link neqsim.process.util.report.ReportConfig.DetailLevel} object
   */
  protected DetailLevel getDetailLevel(ReportConfig cfg) {
    return cfg == null ? DetailLevel.FULL : cfg.getDetailLevel(name);
  }
}
