package neqsim.process.equipment.stream;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.util.unit.PowerUnit;

/**
 * Multi-party energy connection that aggregates named power contributions.
 *
 * <p>
 * Unlike the point-to-point {@link EnergyStream}, an energy bus can connect several calculated and
 * specification ports. Positive and negative contribution signs represent injection and withdrawal
 * from the bus. The inherited duty is retained as an optional external or balancing contribution.
 *
 * @author NeqSim
 * @version 1.0
 */
public class EnergyBus extends EnergyStream {
  private static final long serialVersionUID = 1000L;

  private Map<String, Double> contributions = new LinkedHashMap<String, Double>();

  /** Creates an unnamed bus with an unspecified energy domain. */
  public EnergyBus() {
    super();
  }

  /**
   * Creates a named bus with an unspecified energy domain.
   *
   * @param name bus name
   */
  public EnergyBus(String name) {
    super(name);
  }

  /**
   * Creates a named, typed energy bus.
   *
   * @param name bus name
   * @param energyType physical energy domain
   */
  public EnergyBus(String name, EnergyType energyType) {
    super(name, energyType);
  }

  /** {@inheritDoc} */
  @Override
  public EnergyBus clone() {
    EnergyBus clonedBus = (EnergyBus) super.clone();
    clonedBus.contributions = new LinkedHashMap<String, Double>(contributions);
    return clonedBus;
  }

  /**
   * Sets a named power contribution in watts.
   *
   * @param participant unique participant or port name
   * @param power signed power contribution in W
   */
  public void setContribution(String participant, double power) {
    if (participant == null || participant.trim().isEmpty()) {
      throw new IllegalArgumentException("Energy bus participant cannot be null or empty");
    }
    if (!Double.isFinite(power)) {
      throw new IllegalArgumentException("Energy bus contribution must be finite");
    }
    contributions.put(participant, power);
  }

  /**
   * Sets a named power contribution in a specified unit.
   *
   * @param participant unique participant or port name
   * @param power signed power contribution
   * @param unit power unit
   */
  public void setContribution(String participant, double power, String unit) {
    setContribution(participant, new PowerUnit(power, unit).getValue("W"));
  }

  /**
   * Gets a participant contribution in watts.
   *
   * @param participant participant or port name
   * @return signed contribution in W, or zero when absent
   */
  public double getContribution(String participant) {
    Double contribution = contributions.get(participant);
    return contribution == null ? 0.0 : contribution.doubleValue();
  }

  /**
   * Gets a participant contribution in a requested unit.
   *
   * @param participant participant or port name
   * @param unit requested power unit
   * @return signed contribution in the requested unit
   */
  public double getContribution(String participant, String unit) {
    return new PowerUnit(getContribution(participant), "W").getValue(unit);
  }

  /**
   * Removes a participant contribution.
   *
   * @param participant participant or port name
   */
  public void removeContribution(String participant) {
    contributions.remove(participant);
  }

  /** Removes all named contributions while preserving the inherited balancing duty. */
  public void clearContributions() {
    contributions.clear();
  }

  /**
   * Gets an immutable view of named contributions in watts.
   *
   * @return contributions keyed by participant
   */
  public Map<String, Double> getContributions() {
    return Collections.unmodifiableMap(contributions);
  }

  /**
   * Gets the net bus duty in watts.
   *
   * @return inherited balancing duty plus all named contributions in W
   */
  @Override
  public double getDuty() {
    double netDuty = super.getDuty();
    for (Double contribution : contributions.values()) {
      netDuty += contribution.doubleValue();
    }
    return netDuty;
  }

  /**
   * Alias for {@link #getDuty()} emphasizing the bus balance.
   *
   * @return net bus power in W
   */
  public double getNetPower() {
    return getDuty();
  }

  /**
   * Gets net bus power in a requested unit.
   *
   * @param unit requested power unit
   * @return net bus power in the requested unit
   */
  public double getNetPower(String unit) {
    return getDuty(unit);
  }
}
