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

  /**
   * Sets the tag name for the process equipment.
   *
   * @param tagName a {@link java.lang.String} representing the tag name
   * @throws IllegalArgumentException if the tag name is null
   */
  public void setTagName(String tagName) {
    if (tagName == null) {
      throw new IllegalArgumentException("Tag name cannot be null.");
    }
    this.tagName = tagName;
  }

  /**
   * Retrieves the tag name of the process equipment.
   *
   * @return the tag name as a String.
   */
  public String getTagName() {
    return tagName;
  }
}
