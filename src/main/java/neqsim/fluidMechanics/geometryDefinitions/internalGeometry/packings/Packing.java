/*
 * Packing.java
 *
 * Created on 25. august 2001, 23:34
 */

package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * Packing class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Packing extends NamedBaseClass implements PackingInterface {
  private static final long serialVersionUID = 1L;
  static Logger logger = LogManager.getLogger(Packing.class);
  double voidFractionPacking = 0.951;
  double size = 0;
  double surfaceAreaPrVolume = 112.6;

  /**
   * <p>
   * Constructor for Packing.
   * </p>
   */
  @Deprecated
  public Packing() {
    super("Packing");
  }

  /**
   * <p>
   * Constructor for Packing.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Packing(String name) {
    super(name);
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      System.out.println("init packing");
      java.sql.ResultSet dataSet =
          database.getResultSet(("SELECT * FROM packing WHERE name='" + name + "'"));
      dataSet.next();
      size = 1e-3 * Double.parseDouble(dataSet.getString("size")); // C
      surfaceAreaPrVolume = Double.parseDouble(dataSet.getString("surfaceAreaPrVolume"));
      voidFractionPacking = Double.parseDouble(dataSet.getString("voidFraction"));
      System.out.println("packing ok");
    } catch (Exception ex) {
      String err = ex.toString();
      System.out.println(err);
    }
  }

  /**
   * <p>
   * Constructor for Packing.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param material a {@link java.lang.String} object
   * @param size a int
   */
  public Packing(String name, String material, int size) {
    super(name);
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      System.out.println("init packing");
      java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM packing WHERE name='"
          + name + "' AND size=" + size + " AND material='" + material + "'"));
      dataSet.next();
      this.size = 1e-3 * Double.parseDouble(dataSet.getString("size")); // C
      surfaceAreaPrVolume = Double.parseDouble(dataSet.getString("surfaceAreaPrVolume"));
      voidFractionPacking = Double.parseDouble(dataSet.getString("voidFraction"));
      System.out.println("packing ok");
    } catch (Exception ex) {
      String err = ex.toString();
      System.out.println(err);
    }
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
   * Setter for property size.
   *
   * @param size New value of property size.
   */
  public void setSize(double size) {
    this.size = size;
  }

  /** {@inheritDoc} */
  @Override
  public double getSize() {
    return size;
  }
}
