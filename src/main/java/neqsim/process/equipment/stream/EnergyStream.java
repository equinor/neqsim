package neqsim.process.equipment.stream;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * EnergyStream class.
 * </p>
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

  public EnergyStream() {}

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
      logger.error(ex.getMessage());;
    }
    return clonedStream;
  }

  /**
   * <p>
   * Getter for the field <code>duty</code>.
   * </p>
   *
   * @return a double
   */
  public double getDuty() {
    return duty;
  }

  /**
   * <p>
   * Setter for the field <code>duty</code>.
   * </p>
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
