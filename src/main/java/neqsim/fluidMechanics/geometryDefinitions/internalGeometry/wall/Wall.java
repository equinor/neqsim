package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall;

import java.util.ArrayList;

/**
 *
 * @author ESOL
 */
public class Wall implements WallInterface {
    private static final long serialVersionUID = 1000;

    /**
     * @return the heatTransferCOefficient
     */
    public double getHeatTransferCoefficient() {
        return heatTransferCoefficient;
    }

    /**
     * @param heatTransferCOefficient the heatTransferCOefficient to set
     */
    public void setHeatTransferCoefficient(double heatTransferCOefficient) {
        this.heatTransferCoefficient = heatTransferCOefficient;
    }

    private ArrayList<MaterialLayer> wallMaterialLayers = new ArrayList<MaterialLayer>();
    private double heatTransferCoefficient = 10.0;

    @Override
    public void addMaterialLayer(MaterialLayer layer) {
        wallMaterialLayers.add(layer);
        heatTransferCoefficient = calcHeatTransferCoefficient();
    }

    @Override
    public MaterialLayer getWallMaterialLayer(int i) {
        return wallMaterialLayers.get(i);
    }

    public double calcHeatTransferCoefficient() {
        double invheatTransCoef = 0.0;
        for (MaterialLayer mat : wallMaterialLayers) {
            invheatTransCoef += 1.0 / (mat.getConductivity() / mat.getThickness());
        }
        return 1.0 / invheatTransCoef;
    }
}
