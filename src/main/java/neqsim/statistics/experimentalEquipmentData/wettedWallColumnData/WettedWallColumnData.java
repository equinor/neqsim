/*
 * WettedWallColumnData.java
 *
 * Created on 9. februar 2001, 10:01
 */
package neqsim.statistics.experimentalEquipmentData.wettedWallColumnData;

import neqsim.statistics.experimentalEquipmentData.ExperimentalEquipmentData;

/**
 * <p>
 * WettedWallColumnData class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class WettedWallColumnData extends ExperimentalEquipmentData {
    /**
     * <p>
     * Constructor for WettedWallColumnData.
     * </p>
     */
    public WettedWallColumnData() {}

    /**
     * <p>
     * Constructor for WettedWallColumnData.
     * </p>
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
     * <p>
     * setDiameter.
     * </p>
     *
     * @param diameter a double
     */
    public void setDiameter(double diameter) {
        this.diameter = diameter;
    }

    /**
     * <p>
     * getDiameter.
     * </p>
     *
     * @return a double
     */
    public double getDiameter() {
        return this.diameter;
    }

    /**
     * <p>
     * setLength.
     * </p>
     *
     * @param length a double
     */
    public void setLength(double length) {
        this.length = length;
    }

    /**
     * <p>
     * getLength.
     * </p>
     *
     * @return a double
     */
    public double getLength() {
        return this.length;
    }

    /**
     * <p>
     * setVolume.
     * </p>
     *
     * @param volume a double
     */
    public void setVolume(double volume) {
        this.volume = volume;
    }

    /**
     * <p>
     * getVolume.
     * </p>
     *
     * @return a double
     */
    public double getVolume() {
        return this.volume;
    }
}
