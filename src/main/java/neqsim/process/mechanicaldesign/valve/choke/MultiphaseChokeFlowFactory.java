package neqsim.process.mechanicaldesign.valve.choke;

/**
 * Factory class for creating multiphase choke flow models.
 *
 * <p>
 * This factory provides a convenient way to instantiate different choke flow correlations based on
 * the application requirements.
 * </p>
 *
 * <p>
 * <b>Model Selection Guidelines:</b>
 * </p>
 * <ul>
 * <li><b>Sachdeva:</b> Best for general two-phase flow, subcritical and critical, mechanistic
 * basis</li>
 * <li><b>Gilbert:</b> Quick estimates, field applications, critical flow only</li>
 * <li><b>Achong:</b> High GLR conditions (gas-dominated two-phase)</li>
 * <li><b>Baxendell:</b> General purpose empirical correlation</li>
 * <li><b>Ros:</b> Alternative empirical correlation</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public final class MultiphaseChokeFlowFactory {

  /**
   * Available choke flow model types.
   */
  public enum ModelType {
    /** Sachdeva et al. (1986) mechanistic model - recommended default. */
    SACHDEVA,
    /** Gilbert (1954) empirical correlation. */
    GILBERT,
    /** Baxendell (1958) empirical correlation. */
    BAXENDELL,
    /** Ros (1960) empirical correlation. */
    ROS,
    /** Achong (1961) empirical correlation for high GLR. */
    ACHONG
  }

  /** Private constructor to prevent instantiation. */
  private MultiphaseChokeFlowFactory() {}

  /**
   * Creates a choke flow model of the specified type.
   *
   * @param modelType the type of model to create
   * @return a new MultiphaseChokeFlow instance
   */
  public static MultiphaseChokeFlow createModel(ModelType modelType) {
    switch (modelType) {
      case SACHDEVA:
        return new SachdevaChokeFlow();
      case GILBERT:
        return new GilbertChokeFlow(GilbertChokeFlow.CorrelationType.GILBERT);
      case BAXENDELL:
        return new GilbertChokeFlow(GilbertChokeFlow.CorrelationType.BAXENDELL);
      case ROS:
        return new GilbertChokeFlow(GilbertChokeFlow.CorrelationType.ROS);
      case ACHONG:
        return new GilbertChokeFlow(GilbertChokeFlow.CorrelationType.ACHONG);
      default:
        return new SachdevaChokeFlow();
    }
  }

  /**
   * Creates a choke flow model of the specified type with choke diameter.
   *
   * @param modelType the type of model to create
   * @param chokeDiameter choke throat diameter in meters
   * @return a new MultiphaseChokeFlow instance
   */
  public static MultiphaseChokeFlow createModel(ModelType modelType, double chokeDiameter) {
    MultiphaseChokeFlow model = createModel(modelType);
    model.setChokeDiameter(chokeDiameter);
    return model;
  }

  /**
   * Creates a choke flow model from a string name.
   *
   * <p>
   * Accepted names (case-insensitive):
   * </p>
   * <ul>
   * <li>"sachdeva", "sachdeva1986" - Sachdeva model</li>
   * <li>"gilbert", "gilbert1954" - Gilbert correlation</li>
   * <li>"baxendell", "baxendell1958" - Baxendell correlation</li>
   * <li>"ros", "ros1960" - Ros correlation</li>
   * <li>"achong", "achong1961" - Achong correlation</li>
   * </ul>
   *
   * @param modelName name of the model
   * @return a new MultiphaseChokeFlow instance
   */
  public static MultiphaseChokeFlow createModel(String modelName) {
    String name = modelName.toLowerCase().trim();

    if (name.contains("sachdeva")) {
      return createModel(ModelType.SACHDEVA);
    } else if (name.contains("baxendell")) {
      return createModel(ModelType.BAXENDELL);
    } else if (name.contains("ros")) {
      return createModel(ModelType.ROS);
    } else if (name.contains("achong")) {
      return createModel(ModelType.ACHONG);
    } else if (name.contains("gilbert")) {
      return createModel(ModelType.GILBERT);
    } else {
      // Default to Sachdeva
      return createModel(ModelType.SACHDEVA);
    }
  }

  /**
   * Creates the default choke flow model (Sachdeva).
   *
   * <p>
   * Sachdeva is recommended as the default because:
   * </p>
   * <ul>
   * <li>Handles both critical and subcritical flow</li>
   * <li>Well-validated across wide operating range</li>
   * <li>Mechanistic basis provides better extrapolation</li>
   * <li>Industry standard for production operations</li>
   * </ul>
   *
   * @return a new SachdevaChokeFlow instance
   */
  public static MultiphaseChokeFlow createDefaultModel() {
    return new SachdevaChokeFlow();
  }

  /**
   * Selects the best model based on operating conditions.
   *
   * @param gasLiquidRatio gas-liquid ratio (Sm3/Sm3)
   * @param isCriticalFlow true if flow is expected to be critical
   * @return recommended model type
   */
  public static ModelType recommendModel(double gasLiquidRatio, boolean isCriticalFlow) {
    // For subcritical flow, always use Sachdeva (only mechanistic model that handles it)
    if (!isCriticalFlow) {
      return ModelType.SACHDEVA;
    }

    // For critical flow, select based on GLR
    if (gasLiquidRatio > 5000) {
      // Very high GLR - Achong was developed for high GLR conditions
      return ModelType.ACHONG;
    } else if (gasLiquidRatio < 100) {
      // Low GLR - Gilbert/Baxendell work well
      return ModelType.GILBERT;
    } else {
      // Moderate GLR - Sachdeva is most reliable
      return ModelType.SACHDEVA;
    }
  }
}
