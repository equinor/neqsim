package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall;

import java.util.ArrayList;

/**
 * <p>
 * Wall class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Wall implements WallInterface {
    /**
     * <p>
     * Getter for the field <code>heatTransferCoefficient</code>.
     * </p>
     *
     * @return the heatTransferCOefficient
     */
    public double getHeatTransferCoefficient() {
        return heatTransferCoefficient;
    }

    /**
     * <p>
     * Setter for the field <code>heatTransferCoefficient</code>.
     * </p>
     *
     * @param heatTransferCOefficient the heatTransferCOefficient to set
     */
    public void setHeatTransferCoefficient(double heatTransferCOefficient) {
        this.heatTransferCoefficient = heatTransferCOefficient;
    }

    private ArrayList<MaterialLayer> wallMaterialLayers = new ArrayList<MaterialLayer>();
    private double heatTransferCoefficient = 10.0;

    /** {@inheritDoc} */
    @Override
    public void addMaterialLayer(MaterialLayer layer) {
        wallMaterialLayers.add(layer);
        heatTransferCoefficient = calcHeatTransferCoefficient();
    }

    /** {@inheritDoc} */
    @Override
    public MaterialLayer getWallMaterialLayer(int i) {
        return wallMaterialLayers.get(i);
    }

    /**
     * <p>
     * calcHeatTransferCoefficient.
     * </p>
     *
     * @return a double
     */
    public double calcHeatTransferCoefficient() {
        double invheatTransCoef = 0.0;
        for (MaterialLayer mat : wallMaterialLayers) {
            invheatTransCoef += 1.0 / (mat.getConductivity() / mat.getThickness());
        }
        return 1.0 / invheatTransCoef;
    }
}
