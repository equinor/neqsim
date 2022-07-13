package neqsim.util;

/**
 * Abstract class for named objects.
 * 
 */
public abstract class NamedBaseClass implements NamedInterface, java.io.Serializable {
  private static final long serialVersionUID = 1L;
  public String name;

  /**
   * Constructor for NamedBaseClass
   * 
   * @param name the name of the class
   */
  public NamedBaseClass(String name) {
    this.name = name;
  }

  /**
   * Getter for property name
   * 
   * @return Name property
   */
  @Override
  public String getName() {
    return this.name;
  }

  /**
   * Setter for property name
   * 
   * @param name Name to set.
   */
  @Override
  public void setName(String name) {
    this.name = name;
  }
}
