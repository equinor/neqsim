package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import neqsim.util.NamedBaseClass;
import neqsim.util.database.NeqSimProcessDesignDataBase;

/**
 * Random-packing geometry loaded from the process-design table.
 *
 * <p>
 * Class-name aliases are retained for legacy callers. Defaults select a metal 50 mm Pall ring or a ceramic 25 mm
 * Raschig ring/Berl saddle. Missing rows, placeholder data and invalid geometry are errors. The geometry table is
 * independent of the hydraulic registry's built-in engineering estimates.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public abstract class Packing extends NamedBaseClass implements PackingInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Loaded void fraction. */
  double voidFractionPacking;
  /** Loaded nominal size in metres. */
  double size;
  /** Loaded surface area in square metres per cubic metre. */
  double surfaceAreaPrVolume;

  /**
   * Create a packing using the documented default material and size for its family.
   *
   * @param name packing family or legacy class-name alias
   * @throws IllegalArgumentException if the family has no documented default or geometry is unavailable
   * @throws IllegalStateException if the data table cannot be read
   */
  public Packing(String name) {
    this(name, defaultMaterial(name), "pallring".equals(tableName(name)) ? 50 : 25);
  }

  /**
   * Load one random-packing geometry without falling back to an unrelated row.
   *
   * @param name packing family or legacy class-name alias
   * @param material packing material, such as metal, plastic or ceramic
   * @param size nominal size in millimetres
   * @throws IllegalArgumentException if no matching qualified row exists or geometry is invalid
   * @throws IllegalStateException if the data table cannot be read
   */
  public Packing(String name, String material, int size) {
    super(name);
    if (material == null || material.trim().isEmpty() || size <= 0) {
      throw new IllegalArgumentException("Packing material and a positive nominal size in millimetres are required");
    }
    String query = "SELECT * FROM packing WHERE LOWER(name)='" + tableName(name).replace("'", "''") + "' AND size="
        + size + " AND LOWER(material)='" + material.trim().toLowerCase(Locale.ROOT).replace("'", "''")
        + "' AND LOWER(type)='random'";
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {
      if (!dataSet.next()) {
        throw new IllegalArgumentException(
            "No qualified random packing for " + name + ", material=" + material + ", size=" + size + " mm");
      }
      double loadedSize = 1.0e-3 * dataSet.getDouble("size");
      double loadedArea = dataSet.getDouble("surfaceAreaPrVolume");
      double loadedVoidFraction = dataSet.getDouble("voidFraction");
      if (!Double.isFinite(loadedSize) || loadedSize <= 0.0 || !Double.isFinite(loadedArea) || loadedArea <= 0.0
          || !Double.isFinite(loadedVoidFraction) || loadedVoidFraction <= 0.0 || loadedVoidFraction >= 1.0) {
        throw new IllegalArgumentException("Invalid geometry for packing " + name);
      }
      this.size = loadedSize;
      surfaceAreaPrVolume = loadedArea;
      voidFractionPacking = loadedVoidFraction;
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not read packing geometry for " + name, ex);
    }
  }

  /**
   * Resolve historical class names and common family spellings to the actual data key.
   *
   * @param name packing family or alias
   * @return lower-case table key
   * @throws IllegalArgumentException if the name is blank
   */
  private static String tableName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Packing family must be specified");
    }
    String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    if (normalized.equals("pallring") || normalized.equals("pallringpacking")) {
      return "pallring";
    }
    if (normalized.equals("raschigring") || normalized.equals("rashigring") || normalized.equals("rachigring")
        || normalized.equals("rachigringpacking")) {
      return "rashigring";
    }
    if (normalized.equals("berlsaddle") || normalized.equals("berlsaddlepacking")) {
      return "berlsaddle";
    }
    return name.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Resolve the documented material for a supported no-argument family constructor.
   *
   * @param name packing family or alias
   * @return default material
   * @throws IllegalArgumentException if the family has no documented default
   */
  private static String defaultMaterial(String name) {
    String family = tableName(name);
    if ("pallring".equals(family)) {
      return "metal";
    }
    if ("rashigring".equals(family) || "berlsaddle".equals(family)) {
      return "ceramic";
    }
    throw new IllegalArgumentException("No default geometry for packing " + name + "; specify material and size");
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceAreaPrVolume() {
    return surfaceAreaPrVolume;
  }

  /** {@inheritDoc} */
  @Override
  public double getVoidFractionPacking() {
    return voidFractionPacking;
  }

  /** {@inheritDoc} */
  @Override
  public void setVoidFractionPacking(double voidFractionPacking) {
    this.voidFractionPacking = voidFractionPacking;
  }

  /**
   * Set the nominal packing size.
   *
   * @param size nominal size in metres
   * @throws IllegalArgumentException if the size is not finite and positive
   */
  public void setSize(double size) {
    if (!Double.isFinite(size) || size <= 0.0) {
      throw new IllegalArgumentException("Packing size must be finite and positive in metres");
    }
    this.size = size;
  }

  /** {@inheritDoc} */
  @Override
  public double getSize() {
    return size;
  }
}
