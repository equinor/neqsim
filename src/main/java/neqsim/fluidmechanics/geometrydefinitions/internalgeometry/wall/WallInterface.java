package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall;

/**
 * WallInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface WallInterface {
  /**
   * addMaterialLayer.
   *
   * @param layer a {@link neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.MaterialLayer} object
   */
  public void addMaterialLayer(MaterialLayer layer);

  /**
   * getWallMaterialLayer.
   *
   * @param i a int
   * @return a {@link neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.MaterialLayer} object
   */
  public MaterialLayer getWallMaterialLayer(int i);
}
