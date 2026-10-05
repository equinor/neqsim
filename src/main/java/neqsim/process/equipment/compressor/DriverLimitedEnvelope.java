package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Compressor capacity at the speed and power a (possibly derated) driver can still deliver.
 *
 * <p>
 * Built by {@link Compressor#getDriverLimitedEnvelope()}. The driver speed fraction (driver maximum speed divided by
 * driver rated speed) is mapped onto the compressor chart speed scale, so a driver derating such as a bypassed VFD
 * power cell or an ambient derate shows up directly as a reduced head, flow and power capacity of the compressor.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DriverLimitedEnvelope implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Driver maximum speed divided by driver rated speed (-). */
  private final double driverSpeedFraction;

  /** Chart speed corresponding to the driver rated speed (RPM). */
  private final double referenceChartSpeed;

  /** Maximum chart-scale speed the driver can deliver (RPM). */
  private final double limitedChartSpeed;

  /** Maximum power the driver can deliver at its limited speed (kW). */
  private final double maxDriverPower;

  /** Surge flow at the limited speed (m3/h), NaN when the chart has no surge data. */
  private final double surgeFlow;

  /** Stonewall flow at the limited speed (m3/h), NaN when the chart has no data. */
  private final double stonewallFlow;

  /** Polytropic head at surge flow and the limited speed (kJ/kg), NaN when unavailable. */
  private final double maxHead;

  /** Polytropic head at surge flow and the reference chart speed (kJ/kg), NaN when unavailable. */
  private final double referenceMaxHead;

  /**
   * Creates an envelope result.
   *
   * @param driverSpeedFraction driver maximum speed divided by rated speed (-)
   * @param referenceChartSpeed chart speed at driver rated speed in RPM
   * @param limitedChartSpeed chart-scale speed the driver can deliver in RPM
   * @param maxDriverPower maximum driver power at the limited speed in kW
   * @param surgeFlow surge flow at the limited speed in m3/h
   * @param stonewallFlow stonewall flow at the limited speed in m3/h
   * @param maxHead polytropic head at surge flow and limited speed in kJ/kg
   * @param referenceMaxHead polytropic head at surge flow and reference speed in kJ/kg
   */
  public DriverLimitedEnvelope(double driverSpeedFraction, double referenceChartSpeed, double limitedChartSpeed,
      double maxDriverPower, double surgeFlow, double stonewallFlow, double maxHead, double referenceMaxHead) {
    this.driverSpeedFraction = driverSpeedFraction;
    this.referenceChartSpeed = referenceChartSpeed;
    this.limitedChartSpeed = limitedChartSpeed;
    this.maxDriverPower = maxDriverPower;
    this.surgeFlow = surgeFlow;
    this.stonewallFlow = stonewallFlow;
    this.maxHead = maxHead;
    this.referenceMaxHead = referenceMaxHead;
  }

  /**
   * Gets the driver speed fraction.
   *
   * @return driver maximum speed divided by driver rated speed (-)
   */
  public double getDriverSpeedFraction() {
    return driverSpeedFraction;
  }

  /**
   * Gets the chart speed that corresponds to the driver rated speed.
   *
   * @return reference chart speed in RPM
   */
  public double getReferenceChartSpeed() {
    return referenceChartSpeed;
  }

  /**
   * Gets the maximum chart-scale speed the driver can deliver.
   *
   * @return limited chart speed in RPM
   */
  public double getLimitedChartSpeed() {
    return limitedChartSpeed;
  }

  /**
   * Gets the maximum driver power at the limited speed.
   *
   * @return power in kW
   */
  public double getMaxDriverPower() {
    return maxDriverPower;
  }

  /**
   * Gets the surge flow at the limited speed.
   *
   * @return surge flow in m3/h, NaN when the chart has no surge data
   */
  public double getSurgeFlow() {
    return surgeFlow;
  }

  /**
   * Gets the stonewall flow at the limited speed.
   *
   * @return stonewall flow in m3/h, NaN when unavailable
   */
  public double getStonewallFlow() {
    return stonewallFlow;
  }

  /**
   * Gets the maximum polytropic head at the limited speed.
   *
   * @return head in kJ/kg, NaN when unavailable
   */
  public double getMaxHead() {
    return maxHead;
  }

  /**
   * Gets the fraction of the reference-speed maximum head that remains at the limited speed.
   *
   * @return head fraction (-), NaN when either head is unavailable
   */
  public double getHeadFraction() {
    if (Double.isNaN(maxHead) || Double.isNaN(referenceMaxHead) || referenceMaxHead <= 0.0) {
      return Double.NaN;
    }
    return maxHead / referenceMaxHead;
  }

  /**
   * Checks whether the limited speed lies below the reference chart speed.
   *
   * @return true when the driver cannot reach the reference chart speed
   */
  public boolean isDerated() {
    return limitedChartSpeed < referenceChartSpeed * (1.0 - 1.0e-9);
  }
}
