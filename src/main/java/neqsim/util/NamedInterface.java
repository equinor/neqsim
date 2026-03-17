package neqsim.util;

/**
 * <p>
 * NamedInterface interface.
 * </p>
 *
 * @author ASMF
 * @version $Id: $Id
 */
public interface NamedInterface {
  /**
   * <p>
   * Getter for the field <code>name</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * <p>
   * Setter for the field <code>name</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setName(String name);

  /**
   * Sets the tag name for the process equipment.
   *
   * @param tagName a {@link java.lang.String} representing the tag name
   * @throws java.lang.IllegalArgumentException if the tag name is null
   */
  default public void setTagName(String tagName) {
    this.setTagNumber(tagName);
  }

  /**
   * Retrieves the tag name of the process equipment.
   *
   * @return the tag name as a String.
   */
  default public String getTagName() {
    return this.getTagNumber();
  }

  /**
   * Sets the tag number for the process equipment.
   *
   * @param tagNumber a {@link java.lang.String} representing the tag number
   * @throws java.lang.IllegalArgumentException if the tag number is null
   */
  public void setTagNumber(String tagNumber);

  /**
   * Retrieves the tag number of the process equipment.
   *
   * @return the tag number as a String.
   */
  public String getTagNumber();
}
