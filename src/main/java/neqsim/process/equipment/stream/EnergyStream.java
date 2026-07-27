package neqsim.process.equipment.stream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.ProcessElementInterface;
import neqsim.util.unit.PowerUnit;

/**
 * A named energy-flow connection used to couple process equipment.
 *
 * <p>
 * The canonical stored value is duty in watts. Positive and negative values remain supported for compatibility with
 * existing models; new equipment should use {@link EnergyPort} metadata to express physical direction instead of
 * encoding direction only in the duty sign.
 * </p>
 *
 * <p>
 * <b>Identity equality.</b> An energy stream is a mutable connection, not a value: {@code duty} is rewritten on every
 * flowsheet run. It therefore inherits {@link Object#equals(Object)} and {@link Object#hashCode()} and is equal only to
 * itself, consistent with {@code ProcessSystem} and the process-equipment classes. Compare duties explicitly with
 * {@link #getDuty()} when a value comparison is intended.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class EnergyStream implements ProcessElementInterface, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(EnergyStream.class);

  private String name = "";
  private String tagNumber = "";
  protected double duty = 0.0;
  private EnergyType energyType = EnergyType.UNSPECIFIED;
  private EnergyQuality quality = new EnergyQuality();

  /** Creates an unnamed, unspecified energy stream with zero duty. */
  public EnergyStream() {
  }

  /**
   * Creates an unspecified energy stream with zero duty.
   *
   * @param name stream name
   */
  public EnergyStream(String name) {
    this.name = name;
  }

  /**
   * Creates a typed energy stream with zero duty.
   *
   * @param name stream name
   * @param energyType physical energy domain
   */
  public EnergyStream(String name, EnergyType energyType) {
    this.name = name;
    this.energyType = Objects.requireNonNull(energyType, "energyType cannot be null");
  }

  /**
   * Restores defaults introduced after the original serialized energy-stream format.
   *
   * @param input serialized object input
   * @throws IOException if the stream cannot be read
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (energyType == null) {
      energyType = EnergyType.UNSPECIFIED;
    }
    if (quality == null) {
      quality = new EnergyQuality();
    }
  }

  /** {@inheritDoc} */
  @Override
  public EnergyStream clone() {
    EnergyStream clonedStream = null;
    try {
      clonedStream = (EnergyStream) super.clone();
      clonedStream.quality = quality.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    return clonedStream;
  }

  /**
   * Gets the duty in watts.
   *
   * @return duty in W
   */
  public double getDuty() {
    return duty;
  }

  /**
   * Gets the duty in a requested power unit.
   *
   * @param unit requested power unit
   * @return duty in the requested unit
   */
  public double getDuty(String unit) {
    return new PowerUnit(getDuty(), "W").getValue(unit);
  }

  /**
   * Sets the duty in watts.
   *
   * <p>
   * Legacy point-to-point streams retain support for non-finite intermediate values used by existing equipment fallback
   * calculations. Multi-party {@link EnergyBus} connections reject non-finite duties because allocation requires finite
   * inputs.
   * </p>
   *
   * @param duty duty in W
   */
  public void setDuty(double duty) {
    this.duty = duty;
  }

  /**
   * Sets the duty in a specified power unit.
   *
   * @param duty duty value
   * @param unit power unit
   */
  public void setDuty(double duty, String unit) {
    setDuty(new PowerUnit(duty, unit).getValue("W"));
  }

  /**
   * Alias for {@link #getDuty()} using power terminology.
   *
   * @return power in W
   */
  public double getPower() {
    return getDuty();
  }

  /**
   * Alias for {@link #getDuty(String)} using power terminology.
   *
   * @param unit requested power unit
   * @return power in the requested unit
   */
  public double getPower(String unit) {
    return getDuty(unit);
  }

  /**
   * Alias for {@link #setDuty(double)} using power terminology.
   *
   * @param power power in W
   */
  public void setPower(double power) {
    setDuty(power);
  }

  /**
   * Alias for {@link #setDuty(double, String)} using power terminology.
   *
   * @param power power value
   * @param unit power unit
   */
  public void setPower(double power, String unit) {
    setDuty(power, unit);
  }

  /**
   * Alias for {@link #getDuty()} using energy-flow terminology.
   *
   * @return energy flow in W
   */
  public double getEnergyFlow() {
    return getDuty();
  }

  /**
   * Alias for {@link #getDuty(String)} using energy-flow terminology.
   *
   * @param unit requested power unit
   * @return energy flow in the requested unit
   */
  public double getEnergyFlow(String unit) {
    return getDuty(unit);
  }

  /**
   * Alias for {@link #setDuty(double)} using energy-flow terminology.
   *
   * @param energyFlow energy flow in W
   */
  public void setEnergyFlow(double energyFlow) {
    setDuty(energyFlow);
  }

  /**
   * Alias for {@link #setDuty(double, String)} using energy-flow terminology.
   *
   * @param energyFlow energy-flow value
   * @param unit power unit
   */
  public void setEnergyFlow(double energyFlow, String unit) {
    setDuty(energyFlow, unit);
  }

  /**
   * Gets the physical energy domain.
   *
   * @return energy type
   */
  public EnergyType getEnergyType() {
    return energyType;
  }

  /**
   * Sets the physical energy domain.
   *
   * @param energyType physical energy domain
   */
  public void setEnergyType(EnergyType energyType) {
    this.energyType = Objects.requireNonNull(energyType, "energyType cannot be null");
  }

  /**
   * Gets mutable energy-quality metadata.
   *
   * @return quality metadata
   */
  public EnergyQuality getQuality() {
    return quality;
  }

  /**
   * Replaces energy-quality metadata.
   *
   * @param quality quality metadata
   */
  public void setQuality(EnergyQuality quality) {
    this.quality = Objects.requireNonNull(quality, "quality cannot be null");
  }

  /** {@inheritDoc} */
  @Override
  public String getTagNumber() {
    return tagNumber;
  }

  /** {@inheritDoc} */
  @Override
  public void setTagNumber(String tagNumber) {
    this.tagNumber = Objects.requireNonNull(tagNumber, "tagNumber cannot be null");
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return name;
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    this.name = name;
  }
}
