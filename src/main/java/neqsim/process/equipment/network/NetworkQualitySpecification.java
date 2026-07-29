package neqsim.process.equipment.network;

/**
 * Compatibility name for a named network quality profile.
 */
public class NetworkQualitySpecification extends NetworkQualityProfile {
  private static final long serialVersionUID = 1000L;

  /**
   * Create a named quality specification.
   *
   * @param name specification name
   */
  public NetworkQualitySpecification(String name) {
    super(name);
  }
}
