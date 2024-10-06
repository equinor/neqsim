package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

/**
 * <p>
 * WallInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface WallInterface {
  /**
   * <p>
   * addMaterialLayer.
   * </p>
   *
   * @param layer a
   *        {@link neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.MaterialLayer}
   *        object
   */
  public void addMaterialLayer(MaterialLayer layer);

  /**
   * <p>
   * getWallMaterialLayer.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.MaterialLayer}
   *         object
   */
  public MaterialLayer getWallMaterialLayer(int i);
}
