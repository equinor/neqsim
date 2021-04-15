/*
 * GeometryDefinition.java
 *
 * Created on 10. desember 2000, 18:47
 */

package neqsim.fluidMechanics.geometryDefinitions;

import neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PackingInterface;
import neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall.Wall;
import neqsim.fluidMechanics.geometryDefinitions.surrounding.SurroundingEnvironment;
import neqsim.fluidMechanics.geometryDefinitions.surrounding.SurroundingEnvironmentBaseClass;

/**
 *
 * @author Even Solbraa
 * @version
 */
public abstract class GeometryDefinition extends Object implements GeometryDefinitionInterface, Cloneable,
        java.io.Serializable, neqsim.thermo.ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;

    /**
     * @return the surroundingEnvironment
     */
    public SurroundingEnvironment getSurroundingEnvironment() {
        return surroundingEnvironment;
    }

    /**
     * @param surroundingEnvironment the surroundingEnvironment to set
     */
    public void setSurroundingEnvironment(SurroundingEnvironment surroundingEnvironment) {
        this.surroundingEnvironment = surroundingEnvironment;
    }

    /**
     * @return the wall
     */
    public Wall getWall() {
        return wall;
    }

    /**
     * @param wall the wall to set
     */
    public void setWall(Wall wall) {
        this.wall = wall;
    }

    double wallHeatTransferCoefficient = 20.0;
    private double innerWallTemperature = 276.5;
    protected PackingInterface packing = null;
    /** Creates new GeometryDefinition */

    public double diameter = 0, radius = 0, innerSurfaceRoughness = 0.000005, nodeLength = 0, area = 0,
            relativeRoughnes = 0;
    public double[] layerConductivity, layerThickness;

    public Wall wall = new Wall();

    private SurroundingEnvironment surroundingEnvironment = new SurroundingEnvironmentBaseClass();

    public GeometryDefinition() {
    }

    public GeometryDefinition(double diameter) {
        this.diameter = diameter;
        this.radius = diameter / 2.0;
        this.nodeLength = nodeLength;
        this.area = pi * Math.pow(radius, 2);
    }

    public GeometryDefinition(double diameter, double roughness) {
        this(diameter);
        this.relativeRoughnes = roughness / diameter;
        this.innerSurfaceRoughness = roughness;
    }

    public Object clone() {
        GeometryDefinitionInterface clonedGeometry = null;
        try {
            clonedGeometry = (GeometryDefinition) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedGeometry;
    }

    public void setNodeLength(double nodeLength) {
        this.nodeLength = nodeLength;
    }

    public void setInnerSurfaceRoughness(double innerSurfaceRoughness) {
        this.innerSurfaceRoughness = innerSurfaceRoughness;
    }

    public void init() {
        this.radius = diameter / 2.0;
        this.area = pi * Math.pow(radius, 2.0);
        this.relativeRoughnes = innerSurfaceRoughness / diameter;
    }

    public void setDiameter(double diameter) {
        this.diameter = diameter;
        this.radius = diameter / 2.0;
        this.area = pi * Math.pow(radius, 2);
    }

    public double getWallHeatTransferCoefficient() {
        return wall.getHeatTransferCoefficient();
    }

    public double getDiameter() {
        return diameter;
    }

    public double getArea() {
        return area;
    }

    public double getRadius() {
        return radius;
    }

    public double getInnerSurfaceRoughness() {
        return innerSurfaceRoughness;
    }

    public double getRelativeRoughnes() {
        return relativeRoughnes;
    }

    public double getRelativeRoughnes(double diameter) {
        return innerSurfaceRoughness / diameter;
    }

    public double getCircumference() {
        return 2 * pi * radius;
    }

    public double getNodeLength() {
        return nodeLength;
    }

    public GeometryDefinitionInterface getGeometry() {
        return this;
    }

    public void setPackingType(int i) {
    }

    public void setPackingType(String name, String material, int size) {
        System.out.println("error - packing set in Geometry definition class");
    }

    public PackingInterface getPacking() {
        return packing;
    }

    public double getInnerWallTemperature() {
        return innerWallTemperature;
    }

    public void setInnerWallTemperature(double temperature) {
        this.innerWallTemperature = temperature;
    }

    /**
     * @param wallHeatTransferCoefficient the wallHeatTransferCoefficient to set
     */
    public void setWallHeatTransferCoefficient(double wallHeatTransferCoefficient) {
        this.wallHeatTransferCoefficient = wallHeatTransferCoefficient;
    }
}
