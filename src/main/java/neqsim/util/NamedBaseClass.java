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
  private String tagName = "";

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
  public void setTagName(String tagName) {
    if (tagName == null) {
      throw new IllegalArgumentException("Tag name cannot be null.");
    }
    this.tagName = tagName;
  }

  /** {@inheritDoc} */
  @Override
  public String getTagName() {
    return tagName;
  }
}
