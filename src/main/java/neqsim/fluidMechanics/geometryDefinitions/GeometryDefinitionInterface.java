/*
 * GeometryDefinitionInterface.java
 *
 * Created on 10. desember 2000, 18:56
 */
package neqsim.fluidMechanics.geometryDefinitions;

import neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PackingInterface;
import neqsim.fluidMechanics.geometryDefinitions.surrounding.SurroundingEnvironment;

/**
 * @author Even Solbraa
 * @version
 */
public interface GeometryDefinitionInterface extends Cloneable {
    public void setDiameter(double diameter);

    public void setNodeLength(double nodeLength);

    public void setInnerSurfaceRoughness(double innerSurfaceRoughness);

    public void init();

    public double getDiameter();

    public double getArea();

    public double getRadius();

    public double getInnerSurfaceRoughness();

    public double getCircumference();

    public double getRelativeRoughnes();

    public double getNodeLength();

    public double getRelativeRoughnes(double diameter);

    public double getWallHeatTransferCoefficient();

    public void setWallHeatTransferCoefficient(double outerHeatTransferCoefficient);

    public void setPackingType(int i);

    public void setPackingType(String name, String material, int size);

    public PackingInterface getPacking();

    public GeometryDefinitionInterface getGeometry();

    public Object clone();

    public double getInnerWallTemperature();

    public void setInnerWallTemperature(double temperature);

    public SurroundingEnvironment getSurroundingEnvironment();

    public void setSurroundingEnvironment(SurroundingEnvironment surroundingEnvironment);
}
