/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall;

/**
 *
 * @author ESOL
 */
public interface WallInterface {

    public void addMaterialLayer(MaterialLayer layer);

    public MaterialLayer getWallMaterialLayer(int i);
}
