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
  /* Descriptive name of process object */
  public String name;
  /* Tag number for identifying the object in a process system. */
  private String tagNumber = "";

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

  /** {@inheritDoc} */
  @Override
  public void setTagNumber(String tagNumber) {
    if (tagNumber == null) {
      throw new IllegalArgumentException("Tag number cannot be null.");
    }
    this.tagNumber = tagNumber;
  }

  /** {@inheritDoc} */
  @Override
  public String getTagNumber() {
    return this.tagNumber;
  }
}
