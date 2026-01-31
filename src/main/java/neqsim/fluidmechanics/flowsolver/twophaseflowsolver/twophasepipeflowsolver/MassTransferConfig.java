package neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver;

/**
 * Configuration class for mass transfer calculations in two-phase pipe flow.
 *
 * <p>
 * Provides user-configurable parameters for transfer limiting, convergence, stability, and model
 * options. Default values are based on literature recommendations and numerical stability analysis.
 * </p>
 *
 * <h3>Literature References:</h3>
 * <ul>
 * <li>Krishna, R., Standart, G.L. (1976). Mass and energy transfer in multicomponent systems.
 * Chemical Engineering Communications, 3(4-5), 201-275.</li>
 * <li>Solbraa, E. (2002). Measurement and modelling of absorption of carbon dioxide into
 * methyldiethanolamine solutions at high pressures. PhD thesis, NTNU.</li>
 * <li>Taylor, R., Krishna, R. (1993). Multicomponent Mass Transfer. Wiley.</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class MassTransferConfig {

  // ==================== TRANSFER LIMITS ====================

  /**
   * Maximum fraction of available moles that can transfer in BIDIRECTIONAL mode per node. Default
   * 0.9 (90%) provides good stability for most cases.
   *
   * <p>
   * Literature: Krishna &amp; Standart (1976) recommend limiting single-node transfer to prevent
   * numerical oscillations.
   * </p>
   */
  private double maxTransferFractionBidirectional = 0.9;

  /**
   * Maximum fraction of available moles that can transfer in directional modes per node. Default
   * 0.5 (50%) is more conservative for single-direction scenarios.
   */
  private double maxTransferFractionDirectional = 0.5;

  /**
   * Whether to use adaptive limiting based on local Courant-like condition. When true, the transfer
   * limit adapts to local conditions (flow rate, interfacial area, etc.).
   */
  private boolean useAdaptiveLimiting = true;

  // ==================== CONVERGENCE ====================

  /**
   * Convergence tolerance for mass transfer calculations. Default 1e-4 provides good balance
   * between accuracy and speed.
   */
  private double convergenceTolerance = 1e-4;

  /**
   * Maximum number of iterations for mass transfer solver.
   */
  private int maxIterations = 100;

  /**
   * Minimum iterations to perform before checking convergence.
   */
  private int minIterations = 5;

  // ==================== STABILITY ====================

  /**
   * Minimum relative mole fraction below which a component is considered depleted. This is relative
   * to total system moles.
   */
  private double minMolesFraction = 1e-15;

  /**
   * Absolute minimum moles for numerical stability.
   */
  private double absoluteMinMoles = 1e-20;

  /**
   * Maximum temperature change allowed per node (K). Prevents numerical instabilities from large
   * enthalpy changes.
   */
  private double maxTemperatureChangePerNode = 50.0;

  /**
   * Maximum fraction of a phase that can disappear in a single node. For complete evaporation or
   * dissolution scenarios.
   */
  private double maxPhaseDepletionPerNode = 0.95;

  /**
   * Whether to allow complete phase disappearance.
   */
  private boolean allowPhaseDisappearance = true;

  // ==================== MODEL OPTIONS ====================

  /**
   * Whether to include Marangoni effect correction for surface-active components. Reduces mass
   * transfer coefficient for species with high surface activity.
   *
   * <p>
   * Reference: Springer, T.G., Pigford, R.L. (1970). Influence of surface turbulence and
   * surfactants on gas transport through liquid interfaces. Ind. Eng. Chem. Fundam., 9(3), 458-465.
   * </p>
   */
  private boolean includeMarangoniEffect = false;

  /**
   * Whether to include droplet entrainment in annular flow interfacial area calculation.
   *
   * <p>
   * Reference: Ishii, M., Mishima, K. (1989). Droplet entrainment correlation in annular two-phase
   * flow. Int. J. Heat Mass Transfer, 32(10), 1835-1846.
   * </p>
   */
  private boolean includeEntrainment = true;

  /**
   * Whether to include wave enhancement for stratified wavy flow interfacial area.
   *
   * <p>
   * Reference: Tzotzi, C., Andritsos, N. (2013). Interfacial shear stress in wavy stratified
   * gas-liquid flow. Chem. Eng. Sci., 86, 49-57.
   * </p>
   */
  private boolean includeWaveEnhancement = true;

  /**
   * Whether to include turbulence effects in mass transfer coefficient correlations.
   */
  private boolean includeTurbulenceEffects = true;

  /**
   * Whether to couple heat and mass transfer iteratively. When true, the evaporative cooling /
   * heating effect is properly accounted for.
   */
  private boolean coupledHeatMassTransfer = true;

  /**
   * Number of outer iterations for coupled heat-mass transfer.
   */
  private int coupledIterations = 10;

  // ==================== THREE-PHASE OPTIONS ====================

  /**
   * Whether to enable three-phase mass transfer (gas-oil-water).
   */
  private boolean enableThreePhase = false;

  /**
   * Index of the aqueous phase in three-phase systems.
   */
  private int aqueousPhaseIndex = 2;

  /**
   * Index of the oil/organic phase in three-phase systems.
   */
  private int organicPhaseIndex = 1;

  // ==================== DIAGNOSTICS ====================

  /**
   * Whether to enable convergence diagnostics and logging.
   */
  private boolean enableDiagnostics = false;

  /**
   * Whether to detect and report convergence stalls.
   */
  private boolean detectStalls = true;

  /**
   * Number of iterations to look back for stall detection.
   */
  private int stallDetectionWindow = 5;

  // ==================== CONSTRUCTORS ====================

  /**
   * Creates a MassTransferConfig with default values.
   */
  public MassTransferConfig() {}

  /**
   * Creates a MassTransferConfig optimized for evaporation scenarios.
   *
   * @return configuration optimized for evaporation
   */
  public static MassTransferConfig forEvaporation() {
    MassTransferConfig config = new MassTransferConfig();
    config.setMaxTransferFractionBidirectional(0.8);
    config.setMaxPhaseDepletionPerNode(0.98);
    config.setAllowPhaseDisappearance(true);
    config.setIncludeWaveEnhancement(true);
    config.setCoupledHeatMassTransfer(true);
    return config;
  }

  /**
   * Creates a MassTransferConfig optimized for dissolution scenarios.
   *
   * @return configuration optimized for dissolution
   */
  public static MassTransferConfig forDissolution() {
    MassTransferConfig config = new MassTransferConfig();
    config.setMaxTransferFractionBidirectional(0.85);
    config.setMaxPhaseDepletionPerNode(0.98);
    config.setAllowPhaseDisappearance(true);
    config.setIncludeEntrainment(true);
    return config;
  }

  /**
   * Creates a MassTransferConfig for three-phase systems (gas-oil-water).
   *
   * @return configuration for three-phase systems
   */
  public static MassTransferConfig forThreePhase() {
    MassTransferConfig config = new MassTransferConfig();
    config.setEnableThreePhase(true);
    config.setMaxTransferFractionBidirectional(0.7);
    config.setConvergenceTolerance(1e-5);
    config.setCoupledIterations(15);
    return config;
  }

  /**
   * Creates a MassTransferConfig for high-accuracy calculations.
   *
   * @return configuration for high accuracy
   */
  public static MassTransferConfig forHighAccuracy() {
    MassTransferConfig config = new MassTransferConfig();
    config.setConvergenceTolerance(1e-6);
    config.setMaxIterations(200);
    config.setCoupledIterations(20);
    config.setIncludeMarangoniEffect(true);
    config.setIncludeEntrainment(true);
    config.setIncludeWaveEnhancement(true);
    config.setIncludeTurbulenceEffects(true);
    return config;
  }

  // ==================== GETTERS AND SETTERS ====================

  /**
   * Gets the maximum transfer fraction for bidirectional mode.
   *
   * @return the maximum transfer fraction (0-1)
   */
  public double getMaxTransferFractionBidirectional() {
    return maxTransferFractionBidirectional;
  }

  /**
   * Sets the maximum transfer fraction for bidirectional mode.
   *
   * @param fraction the maximum transfer fraction (0-1)
   */
  public void setMaxTransferFractionBidirectional(double fraction) {
    this.maxTransferFractionBidirectional = Math.max(0.1, Math.min(0.99, fraction));
  }

  /**
   * Gets the maximum transfer fraction for directional modes.
   *
   * @return the maximum transfer fraction (0-1)
   */
  public double getMaxTransferFractionDirectional() {
    return maxTransferFractionDirectional;
  }

  /**
   * Sets the maximum transfer fraction for directional modes.
   *
   * @param fraction the maximum transfer fraction (0-1)
   */
  public void setMaxTransferFractionDirectional(double fraction) {
    this.maxTransferFractionDirectional = Math.max(0.1, Math.min(0.99, fraction));
  }

  /**
   * Gets whether adaptive limiting is enabled.
   *
   * @return true if adaptive limiting is enabled
   */
  public boolean isUseAdaptiveLimiting() {
    return useAdaptiveLimiting;
  }

  /**
   * Sets whether to use adaptive limiting.
   *
   * @param useAdaptiveLimiting true to enable adaptive limiting
   */
  public void setUseAdaptiveLimiting(boolean useAdaptiveLimiting) {
    this.useAdaptiveLimiting = useAdaptiveLimiting;
  }

  /**
   * Gets the convergence tolerance.
   *
   * @return the convergence tolerance
   */
  public double getConvergenceTolerance() {
    return convergenceTolerance;
  }

  /**
   * Sets the convergence tolerance.
   *
   * @param tolerance the convergence tolerance
   */
  public void setConvergenceTolerance(double tolerance) {
    this.convergenceTolerance = Math.max(1e-10, tolerance);
  }

  /**
   * Gets the maximum number of iterations.
   *
   * @return the maximum iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Sets the maximum number of iterations.
   *
   * @param maxIterations the maximum iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = Math.max(10, maxIterations);
  }

  /**
   * Gets the minimum number of iterations.
   *
   * @return the minimum iterations
   */
  public int getMinIterations() {
    return minIterations;
  }

  /**
   * Sets the minimum number of iterations.
   *
   * @param minIterations the minimum iterations
   */
  public void setMinIterations(int minIterations) {
    this.minIterations = Math.max(1, minIterations);
  }

  /**
   * Gets the minimum moles fraction.
   *
   * @return the minimum moles fraction
   */
  public double getMinMolesFraction() {
    return minMolesFraction;
  }

  /**
   * Sets the minimum moles fraction.
   *
   * @param fraction the minimum moles fraction
   */
  public void setMinMolesFraction(double fraction) {
    this.minMolesFraction = Math.max(1e-20, fraction);
  }

  /**
   * Gets the absolute minimum moles.
   *
   * @return the absolute minimum moles
   */
  public double getAbsoluteMinMoles() {
    return absoluteMinMoles;
  }

  /**
   * Sets the absolute minimum moles.
   *
   * @param moles the absolute minimum moles
   */
  public void setAbsoluteMinMoles(double moles) {
    this.absoluteMinMoles = Math.max(1e-30, moles);
  }

  /**
   * Gets the maximum temperature change per node.
   *
   * @return the maximum temperature change (K)
   */
  public double getMaxTemperatureChangePerNode() {
    return maxTemperatureChangePerNode;
  }

  /**
   * Sets the maximum temperature change per node.
   *
   * @param maxChange the maximum temperature change (K)
   */
  public void setMaxTemperatureChangePerNode(double maxChange) {
    this.maxTemperatureChangePerNode = Math.max(1.0, maxChange);
  }

  /**
   * Gets the maximum phase depletion per node.
   *
   * @return the maximum phase depletion fraction
   */
  public double getMaxPhaseDepletionPerNode() {
    return maxPhaseDepletionPerNode;
  }

  /**
   * Sets the maximum phase depletion per node.
   *
   * @param fraction the maximum phase depletion fraction
   */
  public void setMaxPhaseDepletionPerNode(double fraction) {
    this.maxPhaseDepletionPerNode = Math.max(0.5, Math.min(0.99, fraction));
  }

  /**
   * Gets whether phase disappearance is allowed.
   *
   * @return true if phase disappearance is allowed
   */
  public boolean isAllowPhaseDisappearance() {
    return allowPhaseDisappearance;
  }

  /**
   * Sets whether phase disappearance is allowed.
   *
   * @param allow true to allow phase disappearance
   */
  public void setAllowPhaseDisappearance(boolean allow) {
    this.allowPhaseDisappearance = allow;
  }

  /**
   * Gets whether Marangoni effect is included.
   *
   * @return true if Marangoni effect is included
   */
  public boolean isIncludeMarangoniEffect() {
    return includeMarangoniEffect;
  }

  /**
   * Sets whether to include Marangoni effect.
   *
   * @param include true to include Marangoni effect
   */
  public void setIncludeMarangoniEffect(boolean include) {
    this.includeMarangoniEffect = include;
  }

  /**
   * Gets whether entrainment is included.
   *
   * @return true if entrainment is included
   */
  public boolean isIncludeEntrainment() {
    return includeEntrainment;
  }

  /**
   * Sets whether to include entrainment.
   *
   * @param include true to include entrainment
   */
  public void setIncludeEntrainment(boolean include) {
    this.includeEntrainment = include;
  }

  /**
   * Gets whether wave enhancement is included.
   *
   * @return true if wave enhancement is included
   */
  public boolean isIncludeWaveEnhancement() {
    return includeWaveEnhancement;
  }

  /**
   * Sets whether to include wave enhancement.
   *
   * @param include true to include wave enhancement
   */
  public void setIncludeWaveEnhancement(boolean include) {
    this.includeWaveEnhancement = include;
  }

  /**
   * Gets whether turbulence effects are included.
   *
   * @return true if turbulence effects are included
   */
  public boolean isIncludeTurbulenceEffects() {
    return includeTurbulenceEffects;
  }

  /**
   * Sets whether to include turbulence effects.
   *
   * @param include true to include turbulence effects
   */
  public void setIncludeTurbulenceEffects(boolean include) {
    this.includeTurbulenceEffects = include;
  }

  /**
   * Gets whether coupled heat-mass transfer is enabled.
   *
   * @return true if coupled heat-mass transfer is enabled
   */
  public boolean isCoupledHeatMassTransfer() {
    return coupledHeatMassTransfer;
  }

  /**
   * Sets whether to couple heat and mass transfer.
   *
   * @param coupled true to enable coupling
   */
  public void setCoupledHeatMassTransfer(boolean coupled) {
    this.coupledHeatMassTransfer = coupled;
  }

  /**
   * Gets the number of coupled iterations.
   *
   * @return the number of coupled iterations
   */
  public int getCoupledIterations() {
    return coupledIterations;
  }

  /**
   * Sets the number of coupled iterations.
   *
   * @param iterations the number of coupled iterations
   */
  public void setCoupledIterations(int iterations) {
    this.coupledIterations = Math.max(1, iterations);
  }

  /**
   * Gets whether three-phase is enabled.
   *
   * @return true if three-phase is enabled
   */
  public boolean isEnableThreePhase() {
    return enableThreePhase;
  }

  /**
   * Sets whether to enable three-phase.
   *
   * @param enable true to enable three-phase
   */
  public void setEnableThreePhase(boolean enable) {
    this.enableThreePhase = enable;
  }

  /**
   * Gets the aqueous phase index.
   *
   * @return the aqueous phase index
   */
  public int getAqueousPhaseIndex() {
    return aqueousPhaseIndex;
  }

  /**
   * Sets the aqueous phase index.
   *
   * @param index the aqueous phase index
   */
  public void setAqueousPhaseIndex(int index) {
    this.aqueousPhaseIndex = index;
  }

  /**
   * Gets the organic phase index.
   *
   * @return the organic phase index
   */
  public int getOrganicPhaseIndex() {
    return organicPhaseIndex;
  }

  /**
   * Sets the organic phase index.
   *
   * @param index the organic phase index
   */
  public void setOrganicPhaseIndex(int index) {
    this.organicPhaseIndex = index;
  }

  /**
   * Gets whether diagnostics are enabled.
   *
   * @return true if diagnostics are enabled
   */
  public boolean isEnableDiagnostics() {
    return enableDiagnostics;
  }

  /**
   * Sets whether to enable diagnostics.
   *
   * @param enable true to enable diagnostics
   */
  public void setEnableDiagnostics(boolean enable) {
    this.enableDiagnostics = enable;
  }

  /**
   * Gets whether stall detection is enabled.
   *
   * @return true if stall detection is enabled
   */
  public boolean isDetectStalls() {
    return detectStalls;
  }

  /**
   * Sets whether to detect stalls.
   *
   * @param detect true to detect stalls
   */
  public void setDetectStalls(boolean detect) {
    this.detectStalls = detect;
  }

  /**
   * Gets the stall detection window.
   *
   * @return the stall detection window
   */
  public int getStallDetectionWindow() {
    return stallDetectionWindow;
  }

  /**
   * Sets the stall detection window.
   *
   * @param window the stall detection window
   */
  public void setStallDetectionWindow(int window) {
    this.stallDetectionWindow = Math.max(2, window);
  }

  /**
   * Returns a string representation of the configuration.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("MassTransferConfig {\n");
    sb.append("  Transfer Limits:\n");
    sb.append("    maxTransferFractionBidirectional = ").append(maxTransferFractionBidirectional)
        .append("\n");
    sb.append("    maxTransferFractionDirectional = ").append(maxTransferFractionDirectional)
        .append("\n");
    sb.append("    useAdaptiveLimiting = ").append(useAdaptiveLimiting).append("\n");
    sb.append("  Convergence:\n");
    sb.append("    convergenceTolerance = ").append(convergenceTolerance).append("\n");
    sb.append("    maxIterations = ").append(maxIterations).append("\n");
    sb.append("  Model Options:\n");
    sb.append("    includeMarangoniEffect = ").append(includeMarangoniEffect).append("\n");
    sb.append("    includeEntrainment = ").append(includeEntrainment).append("\n");
    sb.append("    includeWaveEnhancement = ").append(includeWaveEnhancement).append("\n");
    sb.append("    coupledHeatMassTransfer = ").append(coupledHeatMassTransfer).append("\n");
    sb.append("    enableThreePhase = ").append(enableThreePhase).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
