package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall;

/**
 *
 * @author ESOL
 */
public interface WallInterface {
    public void addMaterialLayer(MaterialLayer layer);

    public MaterialLayer getWallMaterialLayer(int i);
}
