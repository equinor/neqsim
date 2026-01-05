package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a shutdown profile for compressor shutdown sequences.
 *
 * <p>
 * This class defines the sequence of speed/time points that define how a compressor should be
 * stopped. It includes safety considerations for normal and emergency shutdowns.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ShutdownProfile implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Enum for shutdown types.
   */
  public enum ShutdownType {
    /** Normal controlled shutdown. */
    NORMAL,
    /** Rapid shutdown due to process upset. */
    RAPID,
    /** Emergency shutdown due to safety issue. */
    EMERGENCY,
    /** Coastdown with no braking. */
    COASTDOWN
  }

  private final List<ProfilePoint> profile = new ArrayList<>();
  private ShutdownType shutdownType = ShutdownType.NORMAL;
  private double normalRampRate = 100.0; // RPM/s for normal shutdown
  private double rapidRampRate = 300.0; // RPM/s for rapid shutdown
  private double emergencyRampRate = 500.0; // RPM/s for emergency
  private double coastdownTime = 120.0; // seconds for natural coastdown
  private double minimumIdleSpeed = 1000.0; // RPM
  private double idleRundownTime = 30.0; // seconds at idle before stop
  private boolean openAntisurgeOnShutdown = true;
  private double antisurgeOpenDelay = 2.0; // seconds after shutdown starts
  private double depressurizationTime = 300.0; // seconds for settle pressure
  private double maximumDepressurizationRate = 10.0; // bar/min

  /**
   * Inner class representing a point in the shutdown profile.
   */
  public static class ProfilePoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double time;
    private final double targetSpeed;
    private final String action;

    /**
     * Constructor for ProfilePoint.
     *
     * @param time elapsed time from shutdown start in seconds
     * @param targetSpeed target speed at this point in RPM
     * @param action action or check description
     */
    public ProfilePoint(double time, double targetSpeed, String action) {
      this.time = time;
      this.targetSpeed = targetSpeed;
      this.action = action;
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
     * Get the action description.
     *
     * @return action string
     */
    public String getAction() {
      return action;
    }
  }

  /**
   * Default constructor creating a normal shutdown profile.
   */
  public ShutdownProfile() {
    createProfile(ShutdownType.NORMAL, 5000.0);
  }

  /**
   * Constructor with shutdown type.
   *
   * @param type the shutdown type
   * @param currentSpeed current operating speed in RPM
   */
  public ShutdownProfile(ShutdownType type, double currentSpeed) {
    this.shutdownType = type;
    createProfile(type, currentSpeed);
  }

  /**
   * Create profile based on shutdown type.
   *
   * @param type shutdown type
   * @param currentSpeed current speed in RPM
   */
  private void createProfile(ShutdownType type, double currentSpeed) {
    profile.clear();

    switch (type) {
      case NORMAL:
        createNormalProfile(currentSpeed);
        break;
      case RAPID:
        createRapidProfile(currentSpeed);
        break;
      case EMERGENCY:
        createEmergencyProfile(currentSpeed);
        break;
      case COASTDOWN:
        createCoastdownProfile(currentSpeed);
        break;
      default:
        createNormalProfile(currentSpeed);
        break;
    }
  }

  /**
   * Create normal shutdown profile.
   *
   * @param currentSpeed current speed in RPM
   */
  private void createNormalProfile(double currentSpeed) {
    double rampDownTime = (currentSpeed - minimumIdleSpeed) / normalRampRate;

    profile.add(new ProfilePoint(0.0, currentSpeed, "Initiate shutdown, open antisurge valve"));
    profile.add(new ProfilePoint(antisurgeOpenDelay, currentSpeed * 0.9, "Begin ramp down"));
    profile.add(new ProfilePoint(antisurgeOpenDelay + rampDownTime * 0.5, minimumIdleSpeed * 1.5,
        "Intermediate speed check"));
    profile.add(new ProfilePoint(antisurgeOpenDelay + rampDownTime, minimumIdleSpeed,
        "Idle speed reached, begin rundown"));
    profile.add(new ProfilePoint(antisurgeOpenDelay + rampDownTime + idleRundownTime, 0.0,
        "Stop complete"));
  }

  /**
   * Create rapid shutdown profile.
   *
   * @param currentSpeed current speed in RPM
   */
  private void createRapidProfile(double currentSpeed) {
    double rampDownTime = currentSpeed / rapidRampRate;

    profile.add(new ProfilePoint(0.0, currentSpeed, "Rapid shutdown initiated, open antisurge"));
    profile.add(new ProfilePoint(rampDownTime * 0.5, currentSpeed * 0.3, "Rapid deceleration"));
    profile.add(new ProfilePoint(rampDownTime, 0.0, "Stop complete"));
  }

  /**
   * Create emergency shutdown profile.
   *
   * @param currentSpeed current speed in RPM
   */
  private void createEmergencyProfile(double currentSpeed) {
    double rampDownTime = currentSpeed / emergencyRampRate;

    profile
        .add(new ProfilePoint(0.0, currentSpeed, "EMERGENCY SHUTDOWN - Trip all, open antisurge"));
    profile.add(new ProfilePoint(rampDownTime, 0.0, "Emergency stop complete"));
  }

  /**
   * Create coastdown profile (natural deceleration).
   *
   * @param currentSpeed current speed in RPM
   */
  private void createCoastdownProfile(double currentSpeed) {
    // Exponential decay model for coastdown
    profile.add(new ProfilePoint(0.0, currentSpeed, "Power removed, coastdown started"));
    profile.add(new ProfilePoint(coastdownTime * 0.2, currentSpeed * 0.6, "Coastdown in progress"));
    profile.add(new ProfilePoint(coastdownTime * 0.5, currentSpeed * 0.2, "Coastdown continuing"));
    profile.add(new ProfilePoint(coastdownTime, 0.0, "Coastdown complete"));
  }

  /**
   * Get the target speed at a given elapsed time during shutdown.
   *
   * @param elapsedTime time since shutdown began in seconds
   * @param initialSpeed the speed when shutdown started in RPM
   * @return target speed in RPM
   */
  public double getTargetSpeedAtTime(double elapsedTime, double initialSpeed) {
    if (profile.isEmpty()) {
      return 0.0;
    }

    // Find current and next profile points
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
      return initialSpeed;
    }

    if (next == null) {
      return current.getTargetSpeed();
    }

    // Interpolate between points
    double timeDiff = next.getTime() - current.getTime();
    if (timeDiff <= 0) {
      return next.getTargetSpeed();
    }

    double progress = (elapsedTime - current.getTime()) / timeDiff;
    progress = Math.max(0.0, Math.min(1.0, progress));

    // For coastdown, use exponential decay
    if (shutdownType == ShutdownType.COASTDOWN) {
      double tau = coastdownTime / 3.0; // Time constant
      return initialSpeed * Math.exp(-elapsedTime / tau);
    }

    return current.getTargetSpeed() + progress * (next.getTargetSpeed() - current.getTargetSpeed());
  }

  /**
   * Get the total expected shutdown duration.
   *
   * @return duration in seconds
   */
  public double getTotalDuration() {
    if (profile.isEmpty()) {
      return 0.0;
    }
    return profile.get(profile.size() - 1).getTime();
  }

  /**
   * Check if shutdown is complete.
   *
   * @param elapsedTime time since shutdown began in seconds
   * @param currentSpeed current actual speed in RPM
   * @return true if shutdown is complete
   */
  public boolean isShutdownComplete(double elapsedTime, double currentSpeed) {
    return currentSpeed < 10.0 && elapsedTime >= getTotalDuration() * 0.9;
  }

  /**
   * Get the current phase description.
   *
   * @param elapsedTime time since shutdown began in seconds
   * @return phase description string
   */
  public String getCurrentPhase(double elapsedTime) {
    for (int i = profile.size() - 1; i >= 0; i--) {
      ProfilePoint point = profile.get(i);
      if (point.getTime() <= elapsedTime) {
        return point.getAction();
      }
    }
    return "Pre-shutdown";
  }

  /**
   * Check if antisurge valve should be opened.
   *
   * @param elapsedTime time since shutdown began in seconds
   * @return true if antisurge should be open
   */
  public boolean shouldOpenAntisurge(double elapsedTime) {
    return openAntisurgeOnShutdown && elapsedTime >= antisurgeOpenDelay;
  }

  /**
   * Get the profile points.
   *
   * @return list of profile points
   */
  public List<ProfilePoint> getProfilePoints() {
    return new ArrayList<>(profile);
  }

  // Getters and setters

  /**
   * Get the shutdown type.
   *
   * @return shutdown type
   */
  public ShutdownType getShutdownType() {
    return shutdownType;
  }

  /**
   * Set shutdown type and rebuild profile.
   *
   * @param type shutdown type
   * @param currentSpeed current operating speed in RPM
   */
  public void setShutdownType(ShutdownType type, double currentSpeed) {
    this.shutdownType = type;
    createProfile(type, currentSpeed);
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
   * Get rapid ramp rate.
   *
   * @return rate in RPM/s
   */
  public double getRapidRampRate() {
    return rapidRampRate;
  }

  /**
   * Set rapid ramp rate.
   *
   * @param rate rate in RPM/s
   */
  public void setRapidRampRate(double rate) {
    this.rapidRampRate = rate;
  }

  /**
   * Get emergency ramp rate.
   *
   * @return rate in RPM/s
   */
  public double getEmergencyRampRate() {
    return emergencyRampRate;
  }

  /**
   * Set emergency ramp rate.
   *
   * @param rate rate in RPM/s
   */
  public void setEmergencyRampRate(double rate) {
    this.emergencyRampRate = rate;
  }

  /**
   * Get coastdown time.
   *
   * @return time in seconds
   */
  public double getCoastdownTime() {
    return coastdownTime;
  }

  /**
   * Set coastdown time.
   *
   * @param time time in seconds
   */
  public void setCoastdownTime(double time) {
    this.coastdownTime = time;
  }

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
  }

  /**
   * Get idle rundown time.
   *
   * @return time in seconds
   */
  public double getIdleRundownTime() {
    return idleRundownTime;
  }

  /**
   * Set idle rundown time.
   *
   * @param time time in seconds
   */
  public void setIdleRundownTime(double time) {
    this.idleRundownTime = time;
  }

  /**
   * Check if antisurge opens on shutdown.
   *
   * @return true if enabled
   */
  public boolean isOpenAntisurgeOnShutdown() {
    return openAntisurgeOnShutdown;
  }

  /**
   * Set whether antisurge opens on shutdown.
   *
   * @param open true to open on shutdown
   */
  public void setOpenAntisurgeOnShutdown(boolean open) {
    this.openAntisurgeOnShutdown = open;
  }

  /**
   * Get antisurge open delay.
   *
   * @return delay in seconds
   */
  public double getAntisurgeOpenDelay() {
    return antisurgeOpenDelay;
  }

  /**
   * Set antisurge open delay.
   *
   * @param delay delay in seconds
   */
  public void setAntisurgeOpenDelay(double delay) {
    this.antisurgeOpenDelay = delay;
  }

  /**
   * Get depressurization time.
   *
   * @return time in seconds
   */
  public double getDepressurizationTime() {
    return depressurizationTime;
  }

  /**
   * Set depressurization time.
   *
   * @param time time in seconds
   */
  public void setDepressurizationTime(double time) {
    this.depressurizationTime = time;
  }

  /**
   * Get maximum depressurization rate.
   *
   * @return rate in bar/min
   */
  public double getMaximumDepressurizationRate() {
    return maximumDepressurizationRate;
  }

  /**
   * Set maximum depressurization rate.
   *
   * @param rate rate in bar/min
   */
  public void setMaximumDepressurizationRate(double rate) {
    this.maximumDepressurizationRate = rate;
  }
}
