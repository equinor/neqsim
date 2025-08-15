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
  public void setTagName(String tagName);

  /**
   * Retrieves the tag name of the process equipment.
   *
   * @return the tag name as a String.
   */
  public String getTagName();
}
