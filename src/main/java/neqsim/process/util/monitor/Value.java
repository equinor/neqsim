package neqsim.process.util.monitor;

/**
 * Unit class nested within Units.
 *
 * @author Even Solbraa
 */
public class Value {
  public String value;
  public String unit;

  /**
   * <p>
   * Constructor for Value.
   * </p>
   *
   * @param value a {@link java.lang.String} object
   * @param unit a {@link java.lang.String} object
   */
  public Value(String value, String unit) {
    this.value = value;
    this.unit = unit;
  }
}
