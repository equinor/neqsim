package neqsim.fluidmechanics.flownode;

/**
 * Enumeration of two-phase flow patterns in pipes.
 *
 * <p>
 * Flow patterns describe the geometric distribution of gas and liquid phases in a pipe. The flow
 * pattern significantly affects mass transfer, heat transfer, and pressure drop calculations.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public enum FlowPattern {
  /**
   * Stratified flow - gas flows above liquid with a smooth or wavy interface. Occurs at low gas and
   * liquid velocities in horizontal or near-horizontal pipes.
   */
  STRATIFIED("stratified"),

  /**
   * Stratified wavy flow - stratified flow with interfacial waves. Transition between smooth
   * stratified and slug flow.
   */
  STRATIFIED_WAVY("stratified_wavy"),

  /**
   * Annular flow - liquid flows as a film on the pipe wall with gas in the core. Occurs at high gas
   * velocities.
   */
  ANNULAR("annular"),

  /**
   * Slug flow - alternating slugs of liquid and elongated gas bubbles (Taylor bubbles). Common
   * intermittent flow pattern.
   */
  SLUG("slug"),

  /**
   * Bubble flow - discrete gas bubbles dispersed in continuous liquid phase. Occurs at low gas
   * velocities and high liquid velocities.
   */
  BUBBLE("bubble"),

  /**
   * Droplet/mist flow - liquid droplets dispersed in continuous gas phase. Occurs at very high gas
   * velocities.
   */
  DROPLET("droplet"),

  /**
   * Churn flow - chaotic oscillating flow, transition between slug and annular. Occurs in vertical
   * or near-vertical pipes.
   */
  CHURN("churn"),

  /**
   * Dispersed bubble flow - small bubbles uniformly distributed in liquid. High liquid velocity,
   * low gas content.
   */
  DISPERSED_BUBBLE("dispersed_bubble");

  private final String name;

  FlowPattern(String name) {
    this.name = name;
  }

  /**
   * Gets the string name of the flow pattern.
   *
   * @return the flow pattern name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets a FlowPattern from its string name.
   *
   * @param name the flow pattern name (case insensitive)
   * @return the corresponding FlowPattern
   * @throws IllegalArgumentException if name doesn't match any flow pattern
   */
  public static FlowPattern fromString(String name) {
    if (name == null) {
      return STRATIFIED; // default
    }
    String lowerName = name.toLowerCase().trim();
    for (FlowPattern pattern : FlowPattern.values()) {
      if (pattern.name.equals(lowerName) || pattern.name().equalsIgnoreCase(lowerName)) {
        return pattern;
      }
    }
    // Handle aliases
    if (lowerName.equals("mist")) {
      return DROPLET;
    }
    if (lowerName.equals("wavy") || lowerName.equals("stratified-wavy")) {
      return STRATIFIED_WAVY;
    }
    throw new IllegalArgumentException("Unknown flow pattern: " + name);
  }

  @Override
  public String toString() {
    return name;
  }
}
