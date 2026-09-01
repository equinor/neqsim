package neqsim.process.mechanicaldesign.designstandards;

/**
 * Immutable description of NeqSim's implementation support for one {@link StandardType}.
 */
public final class StandardSupport {
  private final StandardType standardType;
  private final StandardSupportLevel supportLevel;
  private final boolean registryConnected;
  private final String registryImplementation;
  private final String calculationImplementation;
  private final String limitation;

  /**
   * Create a standards-support description.
   *
   * @param standardType catalogued standard
   * @param supportLevel implementation evidence level
   * @param registryConnected whether the stated calculation is selected by the registry
   * @param registryImplementation class selected by {@link StandardRegistry}
   * @param calculationImplementation calculation path that provides the stated support
   * @param limitation concise implementation boundary
   */
  StandardSupport(StandardType standardType, StandardSupportLevel supportLevel, boolean registryConnected,
      String registryImplementation, String calculationImplementation, String limitation) {
    this.standardType = standardType;
    this.supportLevel = supportLevel;
    this.registryConnected = registryConnected;
    this.registryImplementation = registryImplementation;
    this.calculationImplementation = calculationImplementation;
    this.limitation = limitation;
  }

  /**
   * Get the catalogued standard.
   *
   * @return standard type
   */
  public StandardType getStandardType() {
    return standardType;
  }

  /**
   * Get the implementation evidence level.
   *
   * @return support level
   */
  public StandardSupportLevel getSupportLevel() {
    return supportLevel;
  }

  /**
   * Check whether the calculation path is selected by {@link StandardRegistry}.
   *
   * @return {@code true} when strict registry selection can reach the calculation
   */
  public boolean isRegistryConnected() {
    return registryConnected;
  }

  /**
   * Get the class selected by the generic standards factory.
   *
   * @return simple class name
   */
  public String getRegistryImplementation() {
    return registryImplementation;
  }

  /**
   * Get the calculation path that provides the stated support.
   *
   * @return implementation name, or {@code None} when only metadata is available
   */
  public String getCalculationImplementation() {
    return calculationImplementation;
  }

  /**
   * Get the concise implementation boundary.
   *
   * @return limitation text
   */
  public String getLimitation() {
    return limitation;
  }
}
