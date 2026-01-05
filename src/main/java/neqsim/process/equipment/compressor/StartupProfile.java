package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a startup profile for compressor startup sequences.
 *
 * <p>
 * This class defines the sequence of speed/time points that define how a compressor should be
 * started. It includes safety checks and holds during the startup process.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class StartupProfile implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<ProfilePoint> profile = new ArrayList<>();
  private double minimumIdleSpeed = 1000.0; // RPM
  private double idleHoldTime = 30.0; // seconds to hold at idle
  private double warmupRampRate = 50.0; // RPM/s during warmup
  private double normalRampRate = 100.0; // RPM/s during normal ramp
  private double minimumOilPressure = 1.0; // bara
  private double minimumLubeOilTemperature = 300.0; // K
  private double maximumVibration = 50.0; // mm/s
  private boolean requireAntisurgeOpen = true;
  private double antisurgeOpeningDuration = 10.0; // seconds before start

  /**
   * Inner class representing a point in the startup profile.
   */
  public static class ProfilePoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double time;
    private final double targetSpeed;
    private final double holdDuration;
    private final String checkDescription;

    /**
     * Constructor for ProfilePoint.
     *
     * @param time elapsed time from start in seconds
     * @param targetSpeed target speed at this point in RPM
     * @param holdDuration time to hold at this speed in seconds
     * @param checkDescription description of checks to perform
     */
    public ProfilePoint(double time, double targetSpeed, double holdDuration,
        String checkDescription) {
      this.time = time;
      this.targetSpeed = targetSpeed;
      this.holdDuration = holdDuration;
      this.checkDescription = checkDescription;
    }

    /**
     * Get the elapsed time.
     *
     * @return time in seconds
     */
    public double getTime() {
      return time;
    }

    /**
     * Get the target speed.
     *
     * @return speed in RPM
     */
    public double getTargetSpeed() {
      return targetSpeed;
    }

    /**
     * Get the hold duration.
     *
     * @return duration in seconds
     */
    public double getHoldDuration() {
      return holdDuration;
    }

    /**
     * Get the check description.
     *
     * @return description string
     */
    public String getCheckDescription() {
      return checkDescription;
    }
  }

  /**
   * Default constructor creating a standard startup profile.
   */
  public StartupProfile() {
    createDefaultProfile();
  }

  /**
   * Constructor with minimum idle speed.
   *
   * @param minimumIdleSpeed minimum idle speed in RPM
   */
  public StartupProfile(double minimumIdleSpeed) {
    this.minimumIdleSpeed = minimumIdleSpeed;
    createDefaultProfile();
  }

  /**
   * Create a default startup profile.
   */
  private void createDefaultProfile() {
    profile.clear();
    // Standard startup sequence
    profile.add(new ProfilePoint(0.0, 0.0, antisurgeOpeningDuration,
        "Open antisurge valve, check oil pressure"));
    profile.add(new ProfilePoint(antisurgeOpeningDuration, minimumIdleSpeed * 0.5, 10.0,
        "Initial rotation, check for vibration"));
    profile.add(new ProfilePoint(antisurgeOpeningDuration + 10.0, minimumIdleSpeed, idleHoldTime,
        "Idle speed, check bearing temperatures"));
    profile.add(new ProfilePoint(antisurgeOpeningDuration + 10.0 + idleHoldTime, -1.0, 0.0,
        "Ramp to operating speed"));
  }

  /**
   * Add a custom profile point.
   *
   * @param time elapsed time in seconds
   * @param targetSpeed target speed in RPM (-1 means ramp to final)
   * @param holdDuration time to hold at this speed in seconds
   * @param checkDescription description of checks
   */
  public void addProfilePoint(double time, double targetSpeed, double holdDuration,
      String checkDescription) {
    profile.add(new ProfilePoint(time, targetSpeed, holdDuration, checkDescription));
  }

  /**
   * Clear the profile and add custom points.
   */
  public void clearProfile() {
    profile.clear();
  }

  /**
   * Get the target speed at a given elapsed time during startup.
   *
   * @param elapsedTime time since startup began in seconds
   * @param finalTargetSpeed the final target operating speed in RPM
   * @return target speed in RPM
   */
  public double getTargetSpeedAtTime(double elapsedTime, double finalTargetSpeed) {
    if (profile.isEmpty()) {
      return finalTargetSpeed;
    }

    ProfilePoint current = null;
    ProfilePoint next = null;

    for (int i = 0; i < profile.size(); i++) {
      ProfilePoint point = profile.get(i);
      if (point.getTime() <= elapsedTime) {
        current = point;
        if (i + 1 < profile.size()) {
          next = profile.get(i + 1);
        }
      }
    }

    if (current == null) {
      return 0.0;
    }

    double currentTarget = current.getTargetSpeed();
    if (currentTarget < 0) {
      // -1 indicates ramp to final target
      currentTarget = finalTargetSpeed;
    }

    // Check if we're in a hold period
    double holdEndTime = current.getTime() + current.getHoldDuration();
    if (elapsedTime < holdEndTime) {
      return currentTarget;
    }

    // If no next point, we're ramping to final
    if (next == null) {
      return currentTarget;
    }

    // Interpolate to next point
    double nextTarget = next.getTargetSpeed();
    if (nextTarget < 0) {
      nextTarget = finalTargetSpeed;
    }

    double rampStartTime = holdEndTime;
    double rampEndTime = next.getTime();
    if (rampEndTime <= rampStartTime) {
      return nextTarget;
    }

    double progress = (elapsedTime - rampStartTime) / (rampEndTime - rampStartTime);
    progress = Math.max(0.0, Math.min(1.0, progress));

    return currentTarget + progress * (nextTarget - currentTarget);
  }

  /**
   * Get the total expected startup duration.
   *
   * @param finalTargetSpeed final target speed in RPM
   * @return total duration in seconds
   */
  public double getTotalDuration(double finalTargetSpeed) {
    if (profile.isEmpty()) {
      return 0.0;
    }

    ProfilePoint last = profile.get(profile.size() - 1);
    double duration = last.getTime() + last.getHoldDuration();

    // Add time for final ramp if needed
    double lastTarget = last.getTargetSpeed();
    if (lastTarget < 0) {
      lastTarget = finalTargetSpeed;
    }
    if (lastTarget < finalTargetSpeed) {
      duration += (finalTargetSpeed - lastTarget) / normalRampRate;
    }

    return duration;
  }

  /**
   * Check if startup is complete at the given time.
   *
   * @param elapsedTime time since startup began in seconds
   * @param currentSpeed current actual speed in RPM
   * @param targetSpeed final target speed in RPM
   * @param tolerance speed tolerance in RPM
   * @return true if startup is complete
   */
  public boolean isStartupComplete(double elapsedTime, double currentSpeed, double targetSpeed,
      double tolerance) {
    double expectedSpeed = getTargetSpeedAtTime(elapsedTime, targetSpeed);
    return Math.abs(currentSpeed - targetSpeed) < tolerance && expectedSpeed >= targetSpeed * 0.99;
  }

  /**
   * Get the current phase description.
   *
   * @param elapsedTime time since startup began in seconds
   * @return phase description string
   */
  public String getCurrentPhase(double elapsedTime) {
    for (int i = profile.size() - 1; i >= 0; i--) {
      ProfilePoint point = profile.get(i);
      if (point.getTime() <= elapsedTime) {
        return point.getCheckDescription();
      }
    }
    return "Pre-start checks";
  }

  /**
   * Get the profile points.
   *
   * @return list of profile points
   */
  public List<ProfilePoint> getProfilePoints() {
    return new ArrayList<>(profile);
  }

  // Getters and setters for configuration

  /**
   * Get minimum idle speed.
   *
   * @return speed in RPM
   */
  public double getMinimumIdleSpeed() {
    return minimumIdleSpeed;
  }

  /**
   * Set minimum idle speed.
   *
   * @param speed speed in RPM
   */
  public void setMinimumIdleSpeed(double speed) {
    this.minimumIdleSpeed = speed;
    createDefaultProfile();
  }

  /**
   * Get idle hold time.
   *
   * @return time in seconds
   */
  public double getIdleHoldTime() {
    return idleHoldTime;
  }

  /**
   * Set idle hold time.
   *
   * @param time time in seconds
   */
  public void setIdleHoldTime(double time) {
    this.idleHoldTime = time;
    createDefaultProfile();
  }

  /**
   * Get warmup ramp rate.
   *
   * @return rate in RPM/s
   */
  public double getWarmupRampRate() {
    return warmupRampRate;
  }

  /**
   * Set warmup ramp rate.
   *
   * @param rate rate in RPM/s
   */
  public void setWarmupRampRate(double rate) {
    this.warmupRampRate = rate;
  }

  /**
   * Get normal ramp rate.
   *
   * @return rate in RPM/s
   */
  public double getNormalRampRate() {
    return normalRampRate;
  }

  /**
   * Set normal ramp rate.
   *
   * @param rate rate in RPM/s
   */
  public void setNormalRampRate(double rate) {
    this.normalRampRate = rate;
  }

  /**
   * Get minimum oil pressure requirement.
   *
   * @return pressure in bara
   */
  public double getMinimumOilPressure() {
    return minimumOilPressure;
  }

  /**
   * Set minimum oil pressure requirement.
   *
   * @param pressure pressure in bara
   */
  public void setMinimumOilPressure(double pressure) {
    this.minimumOilPressure = pressure;
  }

  /**
   * Get minimum lube oil temperature.
   *
   * @return temperature in K
   */
  public double getMinimumLubeOilTemperature() {
    return minimumLubeOilTemperature;
  }

  /**
   * Set minimum lube oil temperature.
   *
   * @param temperature temperature in K
   */
  public void setMinimumLubeOilTemperature(double temperature) {
    this.minimumLubeOilTemperature = temperature;
  }

  /**
   * Get maximum vibration limit.
   *
   * @return vibration in mm/s
   */
  public double getMaximumVibration() {
    return maximumVibration;
  }

  /**
   * Set maximum vibration limit.
   *
   * @param vibration vibration in mm/s
   */
  public void setMaximumVibration(double vibration) {
    this.maximumVibration = vibration;
  }

  /**
   * Check if antisurge must be open before start.
   *
   * @return true if required
   */
  public boolean isRequireAntisurgeOpen() {
    return requireAntisurgeOpen;
  }

  /**
   * Set whether antisurge must be open.
   *
   * @param required true if required
   */
  public void setRequireAntisurgeOpen(boolean required) {
    this.requireAntisurgeOpen = required;
  }

  /**
   * Get antisurge opening duration.
   *
   * @return duration in seconds
   */
  public double getAntisurgeOpeningDuration() {
    return antisurgeOpeningDuration;
  }

  /**
   * Set antisurge opening duration.
   *
   * @param duration duration in seconds
   */
  public void setAntisurgeOpeningDuration(double duration) {
    this.antisurgeOpeningDuration = duration;
    createDefaultProfile();
  }

  /**
   * Create a fast startup profile (for emergency restarts).
   *
   * @param finalSpeed target operating speed in RPM
   * @return a fast startup profile
   */
  public static StartupProfile createFastProfile(double finalSpeed) {
    StartupProfile profile = new StartupProfile();
    profile.clearProfile();
    profile.addProfilePoint(0.0, 0.0, 2.0, "Quick pre-start check");
    profile.addProfilePoint(2.0, finalSpeed * 0.5, 5.0, "Rapid acceleration");
    profile.addProfilePoint(7.0, finalSpeed, 0.0, "Operating speed");
    return profile;
  }

  /**
   * Create a slow startup profile (for cold starts).
   *
   * @param finalSpeed target operating speed in RPM
   * @param minimumIdle minimum idle speed in RPM
   * @return a slow startup profile
   */
  public static StartupProfile createSlowProfile(double finalSpeed, double minimumIdle) {
    StartupProfile profile = new StartupProfile(minimumIdle);
    profile.setIdleHoldTime(120.0); // Extended warmup
    profile.setWarmupRampRate(25.0); // Slower ramp
    return profile;
  }
}
