package neqsim.process.equipment.stream;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * EnergyStream class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class EnergyStream implements java.io.Serializable, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(EnergyStream.class);
  private String name = "";

  private double duty = 0.0;

  /**
   * Constructor for EnergyStream.
   */
  public EnergyStream() {
  }

  /**
   * Constructor for EnergyStream.
   *
   * @param name a {@link java.lang.String} object
   */
  public EnergyStream(String name) {
    this.name = name;
  }

  /** {@inheritDoc} */
  @Override
  public EnergyStream clone() {
    EnergyStream clonedStream = null;
    try {
      clonedStream = (EnergyStream) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    return clonedStream;
  }

  /**
   * Getter for the field <code>duty</code>.
   *
   * @return a double
   */
  public double getDuty() {
    return duty;
  }

  /**
   * Setter for the field <code>duty</code>.
   *
   * @param duty a double
   */
  public void setDuty(double duty) {
    this.duty = duty;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hashCode(duty);
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
    EnergyStream other = (EnergyStream) obj;
    return Double.doubleToLongBits(duty) == Double.doubleToLongBits(other.duty);
  }

  /**
   * Getter for the field <code>name</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public String getName() {
    return name;
  }

  /**
   * Setter for the field <code>name</code>.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setName(String name) {
    this.name = name;
  }
}
