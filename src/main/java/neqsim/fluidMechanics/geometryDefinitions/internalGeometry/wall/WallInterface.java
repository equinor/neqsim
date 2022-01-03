package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall;

/**
 * <p>WallInterface interface.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface WallInterface {

    /**
     * <p>addMaterialLayer.</p>
     *
     * @param layer a {@link neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall.MaterialLayer} object
     */
    public void addMaterialLayer(MaterialLayer layer);

    /**
     * <p>getWallMaterialLayer.</p>
     *
     * @param i a int
     * @return a {@link neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall.MaterialLayer} object
     */
    public MaterialLayer getWallMaterialLayer(int i);
}
