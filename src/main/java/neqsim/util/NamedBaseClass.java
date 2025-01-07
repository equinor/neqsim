package neqsim.util;

/**
 * Abstract class for named objects.
 *
 * @author ASMF
 * @version $Id: $Id
 */
public abstract class NamedBaseClass implements NamedInterface, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  public String name;

  /**
   * Constructor for NamedBaseClass
   *
   * @param name the name of the object
   */
  public NamedBaseClass(String name) {
    this.name = name;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return this.name;
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    this.name = name;
  }
}
