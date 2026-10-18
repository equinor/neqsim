package neqsim.process.mechanicaldesign.designstandards;

import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * JointEfficiencyPlateStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class JointEfficiencyPlateStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for JointEfficiencyPlateStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public JointEfficiencyPlateStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    readJointEfficiencyStandard(typeName, radiagraphType);
  }

  /**
   * getJEFactor.
   *
   * @return the JEFactor
   */
  public double getJEFactor() {
    if (!(JEFactor > 0.0) || JEFactor > 1.0) {
      throw new IllegalStateException("No valid plate joint efficiency has been selected");
    }
    return JEFactor;
  }

  /**
   * setJEFactor.
   *
   * @param JEFactor the JEFactor to set
   */
  public void setJEFactor(double JEFactor) {
    this.JEFactor = JEFactor;
  }

  String typeName = "Double-Welded Butt Joints";
  String radiagraphType = "Fully radiographed";
  private double JEFactor = 1.0;

  /**
   * Loads the joint efficiency matching the joint type and radiographic inspection.
   *
   * @param typeName a {@link java.lang.String} object
   * @param radiagraphType a {@link java.lang.String} object
   * @throws IllegalArgumentException if the joint and inspection combination is unknown
   * @throws IllegalStateException if the database cannot supply a valid factor
   */
  public void readJointEfficiencyStandard(String typeName, String radiagraphType) {
    JEFactor = Double.NaN;
    String joint = normalizeJointKey(typeName);
    String inspection = normalizeJointKey(radiagraphType);
    try (
        neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase();
        java.sql.ResultSet row = database.getResultSet("SELECT * FROM TechnicalRequirements_Mechanical "
            + "WHERE Type='material plate joint efficiency factor'")) {
      while (row.next()) {
        if (joint.equals(normalizeJointKey(row.getString("CodeSpecification1")))
            && inspection.equals(normalizeJointKey(row.getString("CodeSpecification2")))) {
          double factor = row.getDouble("CodeSpecification3");
          if (!(factor > 0.0) || factor > 1.0) {
            throw new IllegalStateException("Invalid plate joint efficiency in mechanical requirements table");
          }
          this.typeName = typeName;
          this.radiagraphType = radiagraphType;
          JEFactor = factor;
          return;
        }
      }
    } catch (java.sql.SQLException ex) {
      throw new IllegalStateException("Unable to load plate joint efficiency", ex);
    }
    throw new IllegalArgumentException("No plate joint efficiency for " + typeName + " / " + radiagraphType);
  }

  /**
   * Normalizes legacy punctuation and documented radiography spelling variants.
   *
   * @param key joint or inspection label
   * @return normalized lookup key
   * @throws IllegalArgumentException if the label is null or empty
   */
  private static String normalizeJointKey(String key) {
    if (key == null || key.trim().isEmpty()) {
      throw new IllegalArgumentException("Joint and radiography labels must not be empty");
    }
    return key.toLowerCase(java.util.Locale.ROOT).replace("radiagraphed", "radiographed")
        .replace("rediographed", "radiographed").replaceAll("[^a-z0-9]", "");
  }
}
