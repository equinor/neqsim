package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cargo mass, loading window, berth, tank preferences, and synthetic quality profile.
 */
public class CargoNomination implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String cargoId;
  private final double massKg;
  private final int earliestPeriod;
  private final int latestPeriod;
  private final String berth;
  private final double maximumLoadingRateKgS;
  private final NetworkQualityProfile qualityProfile;
  private final List<String> preferredTanks;

  /**
   * Create a cargo nomination.
   *
   * @param cargoId cargo identifier
   * @param massKg required mass
   * @param earliestPeriod earliest inclusive loading period
   * @param latestPeriod latest inclusive loading period
   * @param berth berth
   * @param maximumLoadingRateKgS maximum loading rate
   * @param qualityProfile synthetic/contractual profile
   * @param preferredTanks ordered tank preferences
   */
  public CargoNomination(String cargoId, double massKg, int earliestPeriod, int latestPeriod, String berth,
      double maximumLoadingRateKgS, NetworkQualityProfile qualityProfile, List<String> preferredTanks) {
    this.cargoId = cargoId;
    this.massKg = massKg;
    this.earliestPeriod = earliestPeriod;
    this.latestPeriod = latestPeriod;
    this.berth = berth;
    this.maximumLoadingRateKgS = maximumLoadingRateKgS;
    this.qualityProfile = qualityProfile;
    this.preferredTanks = new ArrayList<String>(preferredTanks);
  }

  /** @return cargo identifier */
  public String getCargoId() {
    return cargoId;
  }

  /** @return required mass */
  public double getMassKg() {
    return massKg;
  }

  /** @return earliest period */
  public int getEarliestPeriod() {
    return earliestPeriod;
  }

  /** @return latest period */
  public int getLatestPeriod() {
    return latestPeriod;
  }

  /** @return berth */
  public String getBerth() {
    return berth;
  }

  /** @return maximum loading rate */
  public double getMaximumLoadingRateKgS() {
    return maximumLoadingRateKgS;
  }

  /** @return quality profile */
  public NetworkQualityProfile getQualityProfile() {
    return qualityProfile;
  }

  /** @return ordered tank preferences */
  public List<String> getPreferredTanks() {
    return Collections.unmodifiableList(preferredTanks);
  }
}
