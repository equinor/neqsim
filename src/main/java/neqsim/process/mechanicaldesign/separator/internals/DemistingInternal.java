package neqsim.process.mechanicaldesign.separator.internals;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base class for demisting internals used in separators and gas scrubbers.
 *
 * <p>
 * Models wire mesh, vane pack, and cyclone demisting devices. Provides methods to calculate allowable gas velocity
 * (Souders-Brown), pressure drop (Euler number), and liquid carry-over fraction.
 * </p>
 *
 * <p>
 * Typical Euler numbers (Eu):
 * </p>
 * <table>
 * <caption>Typical Euler numbers by demister type</caption>
 * <tr>
 * <th>Demister Type</th>
 * <th>Eu</th>
 * </tr>
 * <tr>
 * <td>Wire mesh (standard)</td>
 * <td>100–200</td>
 * </tr>
 * <tr>
 * <td>Wire mesh (high-capacity)</td>
 * <td>50–100</td>
 * </tr>
 * <tr>
 * <td>Vane pack (horizontal flow)</td>
 * <td>20–60</td>
 * </tr>
 * <tr>
 * <td>Multi-cyclone</td>
 * <td>40–80</td>
 * </tr>
 * </table>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DemistingInternal implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DemistingInternal.class);

  /** Name identifier for this internal. */
  private String name = "";

  /** Cross-sectional area available for gas flow [m2]. */
  private double area = 0.0;

  /** Euler number used for pressure drop calculation [-]. */
  private double euNumber = 150.0;

  /**
   * Souders-Brown K-factor for maximum allowable gas velocity [m/s]. Typical values: wire mesh 0.05-0.12, vane pack
   * 0.10-0.20, cyclone 0.15-0.30.
   */
  private double kFactor = 0.107;

  /** Void fraction of the demisting element [-]. */
  private double voidFraction = 0.97;

  /** Thickness of the demisting pad [m]. */
  private double thickness = 0.15;

  /** Wire diameter for wire mesh type [m]. */
  private double wireDiameter = 0.00028;

  /** Demister type: "wire_mesh", "vane_pack", or "cyclone". */
  private String type = "wire_mesh";

  /** Database sub-type description (e.g. "Standard Knitted", "High Efficiency"). */
  private String subType = "";

  /** Minimum recommended Souders-Brown K-factor [m/s] (low-load / turndown limit). */
  private double minKFactor = 0.02;

  /** Maximum recommended Souders-Brown K-factor [m/s] (flooding limit). */
  private double maxKFactor = 0.107;

  /** d50 cut diameter [um] of the grade-efficiency curve. */
  private double d50Um = 8.0;

  /** Sharpness parameter of the grade-efficiency curve [-]. */
  private double sharpness = 2.5;

  /** Maximum (rated) grade efficiency [0-1]. */
  private double maxEfficiency = 0.998;

  /** Literature reference for the internal performance data. */
  private String reference = "";

  /**
   * Constructs a DemistingInternal with default parameters.
   */
  public DemistingInternal() {
  }

  /**
   * Constructs a DemistingInternal with a name.
   *
   * @param name the name of this demisting internal
   */
  public DemistingInternal(String name) {
    this.name = name;
  }

  /**
   * Constructs a DemistingInternal with a name and type.
   *
   * @param name the name of this demisting internal
   * @param type the demister type ("wire_mesh", "vane_pack", or "cyclone")
   */
  public DemistingInternal(String name, String type) {
    this.name = name;
    this.type = type;
    applyTypeDefaults(type);
  }

  /**
   * Applies default parameters based on the demister type.
   *
   * @param demisterType the demister type string
   */
  private void applyTypeDefaults(String demisterType) {
    if ("wire_mesh".equalsIgnoreCase(demisterType)) {
      this.kFactor = 0.107;
      this.voidFraction = 0.97;
      this.thickness = 0.15;
      this.wireDiameter = 0.00028;
      this.euNumber = 150.0;
      this.minKFactor = 0.02;
      this.maxKFactor = 0.107;
      this.d50Um = 8.0;
      this.sharpness = 2.5;
      this.maxEfficiency = 0.998;
    } else if ("vane_pack".equalsIgnoreCase(demisterType)) {
      this.kFactor = 0.15;
      this.voidFraction = 0.90;
      this.thickness = 0.30;
      this.wireDiameter = 0.0;
      this.euNumber = 40.0;
      this.minKFactor = 0.02;
      this.maxKFactor = 0.15;
      this.d50Um = 10.0;
      this.sharpness = 2.3;
      this.maxEfficiency = 0.995;
    } else if ("cyclone".equalsIgnoreCase(demisterType)) {
      this.kFactor = 0.20;
      this.voidFraction = 0.85;
      this.thickness = 0.50;
      this.wireDiameter = 0.0;
      this.euNumber = 60.0;
      this.minKFactor = 0.05;
      this.maxKFactor = 0.30;
      this.d50Um = 3.0;
      this.sharpness = 4.0;
      this.maxEfficiency = 0.998;
    }
  }

  /**
   * Maps a mechanical-design demister type to the internals-database type code.
   *
   * @param demisterType demister type ("wire_mesh", "vane_pack", "cyclone")
   * @return database type code ("WIRE_MESH", "VANE_PACK", "AXIAL_CYCLONE")
   */
  public static String toDatabaseType(String demisterType) {
    if ("vane_pack".equalsIgnoreCase(demisterType)) {
      return "VANE_PACK";
    } else if ("cyclone".equalsIgnoreCase(demisterType) || "axial_cyclone".equalsIgnoreCase(demisterType)) {
      return "AXIAL_CYCLONE";
    }
    return "WIRE_MESH";
  }

  /**
   * Maps an internals-database type code to a mechanical-design demister type.
   *
   * @param databaseType database type code ("WIRE_MESH", "VANE_PACK", "AXIAL_CYCLONE")
   * @return demister type ("wire_mesh", "vane_pack", "cyclone")
   */
  public static String fromDatabaseType(String databaseType) {
    if ("VANE_PACK".equalsIgnoreCase(databaseType)) {
      return "vane_pack";
    } else if ("AXIAL_CYCLONE".equalsIgnoreCase(databaseType) || "CYCLONE".equalsIgnoreCase(databaseType)) {
      return "cyclone";
    }
    return "wire_mesh";
  }

  /**
   * Applies performance data from a {@code SeparatorInternalsDatabase} record. Sets the design K-factor to the maximum
   * allowable K-factor and populates the K-factor window, grade-efficiency parameters, and reference.
   *
   * @param record the internals database record to apply (ignored if null)
   */
  public void applyDatabaseRecord(
      neqsim.process.equipment.separator.entrainment.SeparatorInternalsDatabase.InternalsRecord record) {
    if (record == null) {
      return;
    }
    this.type = fromDatabaseType(record.internalsType);
    this.subType = record.subType;
    if (record.maxKFactor > 0.0) {
      this.kFactor = record.maxKFactor;
      this.maxKFactor = record.maxKFactor;
    }
    if (record.minKFactor > 0.0) {
      this.minKFactor = record.minKFactor;
    }
    if (record.d50_um > 0.0) {
      this.d50Um = record.d50_um;
    }
    if (record.sharpness > 0.0) {
      this.sharpness = record.sharpness;
    }
    if (record.maxEfficiency > 0.0) {
      this.maxEfficiency = record.maxEfficiency;
    }
    this.reference = record.reference;
  }

  /**
   * Builds a DemistingInternal configured from the internals database for the given type and sub-type. When the
   * sub-type is null or not found, the first record of the type is used; when the type has no records, type-based
   * defaults are applied.
   *
   * @param demisterType demister type ("wire_mesh", "vane_pack", "cyclone")
   * @param subType database sub-type description, or null for the first record of the type
   * @return a configured DemistingInternal
   */
  public static DemistingInternal fromDatabase(String demisterType, String subType) {
    DemistingInternal internal = new DemistingInternal(demisterType, demisterType);
    neqsim.process.equipment.separator.entrainment.SeparatorInternalsDatabase db = neqsim.process.equipment.separator.entrainment.SeparatorInternalsDatabase
        .getInstance();
    String dbType = toDatabaseType(demisterType);
    neqsim.process.equipment.separator.entrainment.SeparatorInternalsDatabase.InternalsRecord rec = null;
    if (subType != null && !subType.trim().isEmpty()) {
      rec = db.findByTypeAndSubType(dbType, subType);
    }
    if (rec == null) {
      java.util.List<neqsim.process.equipment.separator.entrainment.SeparatorInternalsDatabase.InternalsRecord> recs = db
          .findByType(dbType);
      if (!recs.isEmpty()) {
        rec = recs.get(0);
      }
    }
    internal.applyDatabaseRecord(rec);
    return internal;
  }

  /**
   * Builds an {@link InternalOperatingWindow} describing where the supplied operating K-factor sits within this
   * internal's recommended K-factor window.
   *
   * @param operatingKFactor operating Souders-Brown K-factor [m/s]
   * @return the operating window classification for this internal
   */
  public InternalOperatingWindow getOperatingWindow(double operatingKFactor) {
    return new InternalOperatingWindow(name, type, subType, minKFactor, maxKFactor, operatingKFactor, maxEfficiency,
        reference);
  }

  /**
   * Calculates the maximum allowable gas velocity through the demister using the Souders-Brown equation.
   *
   * <p>
   * $$ v_{gas,max} = K \sqrt{\frac{\rho_L - \rho_G}{\rho_G}} $$
   * </p>
   *
   * @param gasVelocitySuperficial superficial gas velocity [m/s] (not used in max calc, but retained for signature
   * compatibility)
   * @param gasDensity gas phase density [kg/m3]
   * @param liquidDensity liquid phase density [kg/m3]
   * @return maximum allowable gas velocity [m/s]
   */
  public double calcGasVelocity(double gasVelocitySuperficial, double gasDensity, double liquidDensity) {
    if (gasDensity <= 0 || liquidDensity <= gasDensity) {
      logger.warn("Invalid densities for gas velocity calculation: gasRho={}, liqRho={}", gasDensity, liquidDensity);
      return 0.0;
    }
    return kFactor * Math.sqrt((liquidDensity - gasDensity) / gasDensity);
  }

  /**
   * Calculates the pressure drop across the demister using the Euler number correlation.
   *
   * <p>
   * $$ \Delta P = Eu \cdot \frac{1}{2} \rho_G v_{gas}^2 $$
   * </p>
   *
   * @param gasVelocity gas velocity through the demister [m/s]
   * @param gasDensity gas phase density [kg/m3]
   * @return pressure drop [Pa]
   */
  public double calcPressureDrop(double gasVelocity, double gasDensity) {
    return euNumber * 0.5 * gasDensity * gasVelocity * gasVelocity;
  }

  /**
   * Calculates the liquid carry-over fraction past the demister. Uses a simple exponential decay model based on the
   * ratio of actual velocity to maximum velocity.
   *
   * <p>
   * For velocity ratios below 1.0, carry-over is near zero. Above 1.0, carry-over rises rapidly towards 1.0 (flooding).
   * </p>
   *
   * @param gasVelocity actual gas velocity [m/s]
   * @param maxGasVelocity maximum allowable gas velocity [m/s]
   * @return liquid carry-over fraction [0..1], where 0 = no carry-over, 1 = total flooding
   */
  public double calcLiquidCarryOver(double gasVelocity, double maxGasVelocity) {
    if (maxGasVelocity <= 0) {
      return 1.0;
    }
    double ratio = gasVelocity / maxGasVelocity;
    if (ratio < 0.8) {
      return 0.0;
    }
    // Exponential rise from 80% to 120% of max velocity
    double exponent = 5.0 * (ratio - 0.8);
    return Math.min(1.0, 1.0 - Math.exp(-exponent));
  }

  /**
   * Gets the name of this demisting internal.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of this demisting internal.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the cross-sectional area available for gas flow.
   *
   * @return area [m2]
   */
  public double getArea() {
    return area;
  }

  /**
   * Sets the cross-sectional area available for gas flow.
   *
   * @param area area [m2]
   */
  public void setArea(double area) {
    this.area = area;
  }

  /**
   * Gets the Euler number for pressure drop calculation.
   *
   * @return Euler number [-]
   */
  public double getEuNumber() {
    return euNumber;
  }

  /**
   * Sets the Euler number for pressure drop calculation.
   *
   * @param euNumber Euler number [-]
   */
  public void setEuNumber(double euNumber) {
    this.euNumber = euNumber;
  }

  /**
   * Gets the Souders-Brown K-factor.
   *
   * @return K-factor [m/s]
   */
  public double getKFactor() {
    return kFactor;
  }

  /**
   * Sets the Souders-Brown K-factor.
   *
   * @param kFactor K-factor [m/s]
   */
  public void setKFactor(double kFactor) {
    this.kFactor = kFactor;
  }

  /**
   * Gets the void fraction of the demisting element.
   *
   * @return void fraction [-]
   */
  public double getVoidFraction() {
    return voidFraction;
  }

  /**
   * Sets the void fraction of the demisting element.
   *
   * @param voidFraction void fraction [-]
   */
  public void setVoidFraction(double voidFraction) {
    this.voidFraction = voidFraction;
  }

  /**
   * Gets the thickness of the demisting pad.
   *
   * @return thickness [m]
   */
  public double getThickness() {
    return thickness;
  }

  /**
   * Sets the thickness of the demisting pad.
   *
   * @param thickness thickness [m]
   */
  public void setThickness(double thickness) {
    this.thickness = thickness;
  }

  /**
   * Gets the wire diameter for wire mesh type.
   *
   * @return wire diameter [m]
   */
  public double getWireDiameter() {
    return wireDiameter;
  }

  /**
   * Sets the wire diameter for wire mesh type.
   *
   * @param wireDiameter wire diameter [m]
   */
  public void setWireDiameter(double wireDiameter) {
    this.wireDiameter = wireDiameter;
  }

  /**
   * Gets the demister type.
   *
   * @return demister type string
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the demister type and applies default parameters for that type.
   *
   * @param type demister type ("wire_mesh", "vane_pack", or "cyclone")
   */
  public void setType(String type) {
    this.type = type;
    applyTypeDefaults(type);
  }

  /**
   * Gets the database sub-type description.
   *
   * @return the sub-type
   */
  public String getSubType() {
    return subType;
  }

  /**
   * Sets the database sub-type description.
   *
   * @param subType the sub-type to set
   */
  public void setSubType(String subType) {
    this.subType = subType;
  }

  /**
   * Gets the minimum recommended Souders-Brown K-factor (low-load / turndown limit).
   *
   * @return minimum K-factor [m/s]
   */
  public double getMinKFactor() {
    return minKFactor;
  }

  /**
   * Sets the minimum recommended Souders-Brown K-factor.
   *
   * @param minKFactor minimum K-factor [m/s]
   */
  public void setMinKFactor(double minKFactor) {
    this.minKFactor = minKFactor;
  }

  /**
   * Gets the maximum recommended Souders-Brown K-factor (flooding limit).
   *
   * @return maximum K-factor [m/s]
   */
  public double getMaxKFactor() {
    return maxKFactor;
  }

  /**
   * Sets the maximum recommended Souders-Brown K-factor.
   *
   * @param maxKFactor maximum K-factor [m/s]
   */
  public void setMaxKFactor(double maxKFactor) {
    this.maxKFactor = maxKFactor;
  }

  /**
   * Gets the d50 cut diameter of the grade-efficiency curve.
   *
   * @return d50 [um]
   */
  public double getD50Um() {
    return d50Um;
  }

  /**
   * Sets the d50 cut diameter of the grade-efficiency curve.
   *
   * @param d50Um d50 [um]
   */
  public void setD50Um(double d50Um) {
    this.d50Um = d50Um;
  }

  /**
   * Gets the sharpness parameter of the grade-efficiency curve.
   *
   * @return sharpness [-]
   */
  public double getSharpness() {
    return sharpness;
  }

  /**
   * Sets the sharpness parameter of the grade-efficiency curve.
   *
   * @param sharpness sharpness [-]
   */
  public void setSharpness(double sharpness) {
    this.sharpness = sharpness;
  }

  /**
   * Gets the maximum (rated) grade efficiency.
   *
   * @return maximum efficiency [0-1]
   */
  public double getMaxEfficiency() {
    return maxEfficiency;
  }

  /**
   * Sets the maximum (rated) grade efficiency.
   *
   * @param maxEfficiency maximum efficiency [0-1]
   */
  public void setMaxEfficiency(double maxEfficiency) {
    this.maxEfficiency = maxEfficiency;
  }

  /**
   * Gets the literature reference for the internal performance data.
   *
   * @return the reference
   */
  public String getReference() {
    return reference;
  }

  /**
   * Sets the literature reference for the internal performance data.
   *
   * @param reference the reference to set
   */
  public void setReference(String reference) {
    this.reference = reference;
  }
}
