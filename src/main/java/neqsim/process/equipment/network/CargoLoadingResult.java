package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Loaded cargo parcel, source tank, period, and quality result.
 */
public class CargoLoadingResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String cargoId;
  private final int periodIndex;
  private final String tankName;
  private final CrudeParcel parcel;
  private final NetworkQualityComplianceReport qualityReport;

  /**
   * Create a cargo result.
   *
   * @param cargoId cargo
   * @param periodIndex loading period
   * @param tankName source tank
   * @param parcel loaded parcel
   * @param qualityReport quality result
   */
  public CargoLoadingResult(String cargoId, int periodIndex, String tankName, CrudeParcel parcel,
      NetworkQualityComplianceReport qualityReport) {
    this.cargoId = cargoId;
    this.periodIndex = periodIndex;
    this.tankName = tankName;
    this.parcel = parcel;
    this.qualityReport = qualityReport;
  }

  /** @return cargo identifier */
  public String getCargoId() {
    return cargoId;
  }

  /** @return loading period */
  public int getPeriodIndex() {
    return periodIndex;
  }

  /** @return source tank */
  public String getTankName() {
    return tankName;
  }

  /** @return loaded parcel */
  public CrudeParcel getParcel() {
    return parcel;
  }

  /** @return quality result */
  public NetworkQualityComplianceReport getQualityReport() {
    return qualityReport;
  }
}
