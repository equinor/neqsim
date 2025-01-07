package neqsim.process.equipment.compressor;

import java.util.Objects;

/**
 * <p>
 * AntiSurge class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class AntiSurge implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private boolean isActive = false;
  private boolean isSurge = false;
  private double surgeControlFactor = 1.05;
  private double currentSurgeFraction = 0.0;

  /**
   * <p>
   * isActive.
   * </p>
   *
   * @return a boolean
   */
  public boolean isActive() {
    return isActive;
  }

  /**
   * <p>
   * setActive.
   * </p>
   *
   * @param isActive a boolean
   */
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  /**
   * <p>
   * isSurge.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSurge() {
    return isSurge;
  }

  /**
   * <p>
   * setSurge.
   * </p>
   *
   * @param isSurge a boolean
   */
  public void setSurge(boolean isSurge) {
    this.isSurge = isSurge;
  }

  /**
   * <p>
   * Getter for the field <code>surgeControlFactor</code>.
   * </p>
   *
   * @return a double
   */
  public double getSurgeControlFactor() {
    return surgeControlFactor;
  }

  /**
   * <p>
   * Setter for the field <code>surgeControlFactor</code>.
   * </p>
   *
   * @param antiSurgeSafetyFactor a double
   */
  public void setSurgeControlFactor(double antiSurgeSafetyFactor) {
    this.surgeControlFactor = antiSurgeSafetyFactor;
  }

  /**
   * <p>
   * Getter for the field <code>currentSurgeFraction</code>.
   * </p>
   *
   * @return a double
   */
  public double getCurrentSurgeFraction() {
    return currentSurgeFraction;
  }

  /**
   * <p>
   * Setter for the field <code>currentSurgeFraction</code>.
   * </p>
   *
   * @param currentSurgeFraction a double
   */
  public void setCurrentSurgeFraction(double currentSurgeFraction) {
    this.currentSurgeFraction = currentSurgeFraction;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(currentSurgeFraction, isActive, isSurge, surgeControlFactor);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AntiSurge other = (AntiSurge) obj;
    return Double.doubleToLongBits(currentSurgeFraction) == Double
        .doubleToLongBits(other.currentSurgeFraction) && isActive == other.isActive
        && isSurge == other.isSurge && Double.doubleToLongBits(surgeControlFactor) == Double
            .doubleToLongBits(other.surgeControlFactor);
  }
}
