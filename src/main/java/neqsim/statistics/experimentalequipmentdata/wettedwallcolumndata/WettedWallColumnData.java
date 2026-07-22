/*
 * WettedWallColumnData.java
 *
 * Created on 9. februar 2001, 10:01
 */

package neqsim.statistics.experimentalequipmentdata.wettedwallcolumndata;

import neqsim.statistics.experimentalequipmentdata.ExperimentalEquipmentData;

/**
 * WettedWallColumnData class.
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class WettedWallColumnData extends ExperimentalEquipmentData {
  /**
   * Constructor for WettedWallColumnData.
   */
  public WettedWallColumnData() {
  }

  /**
   * Constructor for WettedWallColumnData.
   *
   * @param diameter a double
   * @param length a double
   * @param volume a double
   */
  public WettedWallColumnData(double diameter, double length, double volume) {
    this.diameter = diameter;
    this.length = length;
    this.volume = volume;
  }

  /**
   * setDiameter.
   *
   * @param diameter a double
   */
  public void setDiameter(double diameter) {
    this.diameter = diameter;
  }

  /**
   * getDiameter.
   *
   * @return a double
   */
  public double getDiameter() {
    return this.diameter;
  }

  /**
   * setLength.
   *
   * @param length a double
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * getLength.
   *
   * @return a double
   */
  public double getLength() {
    return this.length;
  }

  /**
   * setVolume.
   *
   * @param volume a double
   */
  public void setVolume(double volume) {
    this.volume = volume;
  }

  /**
   * getVolume.
   *
   * @return a double
   */
  public double getVolume() {
    return this.volume;
  }
}
