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
 * <p>
 * Abstract GeometryDefinition class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class GeometryDefinition
        implements GeometryDefinitionInterface, neqsim.thermo.ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;

    /** {@inheritDoc} */
    @Override
    public SurroundingEnvironment getSurroundingEnvironment() {
        return surroundingEnvironment;
    }

    /** {@inheritDoc} */
    @Override
    public void setSurroundingEnvironment(SurroundingEnvironment surroundingEnvironment) {
        this.surroundingEnvironment = surroundingEnvironment;
    }

    /**
     * <p>
     * Getter for the field <code>wall</code>.
     * </p>
     *
     * @return the wall
     */
    public Wall getWall() {
        return wall;
    }

    /**
     * <p>
     * Setter for the field <code>wall</code>.
     * </p>
     *
     * @param wall the wall to set
     */
    public void setWall(Wall wall) {
        this.wall = wall;
    }

    double wallHeatTransferCoefficient = 20.0;
    private double innerWallTemperature = 276.5;
    protected PackingInterface packing = null;

    public double diameter = 0, radius = 0, innerSurfaceRoughness = 0.000005, nodeLength = 0,
            area = 0, relativeRoughnes = 0;
    public double[] layerConductivity, layerThickness;

    public Wall wall = new Wall();

    private SurroundingEnvironment surroundingEnvironment = new SurroundingEnvironmentBaseClass();

    /**
     * *
     * <p>
     * Constructor for GeometryDefinition.
     * </p>
     */
    public GeometryDefinition() {}

    /**
     * <p>
     * Constructor for GeometryDefinition.
     * </p>
     *
     * @param diameter a double
     */
    public GeometryDefinition(double diameter) {
        this.diameter = diameter;
        this.radius = diameter / 2.0;
        this.area = pi * Math.pow(radius, 2);
    }

    /**
     * <p>
     * Constructor for GeometryDefinition.
     * </p>
     *
     * @param diameter a double
     * @param roughness a double
     */
    public GeometryDefinition(double diameter, double roughness) {
        this(diameter);
        this.relativeRoughnes = roughness / diameter;
        this.innerSurfaceRoughness = roughness;
    }

    /** {@inheritDoc} */
    @Override
    public GeometryDefinitionInterface clone() {
        GeometryDefinitionInterface clonedGeometry = null;
        try {
            clonedGeometry = (GeometryDefinition) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedGeometry;
    }

    /** {@inheritDoc} */
    @Override
    public void setNodeLength(double nodeLength) {
        this.nodeLength = nodeLength;
    }

    /** {@inheritDoc} */
    @Override
    public void setInnerSurfaceRoughness(double innerSurfaceRoughness) {
        this.innerSurfaceRoughness = innerSurfaceRoughness;
    }

    /** {@inheritDoc} */
    @Override
    public void init() {
        this.radius = diameter / 2.0;
        this.area = pi * Math.pow(radius, 2.0);
        this.relativeRoughnes = innerSurfaceRoughness / diameter;
    }

    /** {@inheritDoc} */
    @Override
    public void setDiameter(double diameter) {
        this.diameter = diameter;
        this.radius = diameter / 2.0;
        this.area = pi * Math.pow(radius, 2);
    }

    /** {@inheritDoc} */
    @Override
    public double getWallHeatTransferCoefficient() {
        return wall.getHeatTransferCoefficient();
    }

    /** {@inheritDoc} */
    @Override
    public double getDiameter() {
        return diameter;
    }

    /** {@inheritDoc} */
    @Override
    public double getArea() {
        return area;
    }

    /** {@inheritDoc} */
    @Override
    public double getRadius() {
        return radius;
    }

    /** {@inheritDoc} */
    @Override
    public double getInnerSurfaceRoughness() {
        return innerSurfaceRoughness;
    }

    /** {@inheritDoc} */
    @Override
    public double getRelativeRoughnes() {
        return relativeRoughnes;
    }

    /** {@inheritDoc} */
    @Override
    public double getRelativeRoughnes(double diameter) {
        return innerSurfaceRoughness / diameter;
    }

    /** {@inheritDoc} */
    @Override
    public double getCircumference() {
        return 2 * pi * radius;
    }

    /** {@inheritDoc} */
    @Override
    public double getNodeLength() {
        return nodeLength;
    }

    /** {@inheritDoc} */
    @Override
    public GeometryDefinitionInterface getGeometry() {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public void setPackingType(int i) {}

    /** {@inheritDoc} */
    @Override
    public void setPackingType(String name, String material, int size) {
        System.out.println("error - packing set in Geometry definition class");
    }

    /** {@inheritDoc} */
    @Override
    public PackingInterface getPacking() {
        return packing;
    }

    /** {@inheritDoc} */
    @Override
    public double getInnerWallTemperature() {
        return innerWallTemperature;
    }

    /** {@inheritDoc} */
    @Override
    public void setInnerWallTemperature(double temperature) {
        this.innerWallTemperature = temperature;
    }

    /** {@inheritDoc} */
    @Override
    public void setWallHeatTransferCoefficient(double wallHeatTransferCoefficient) {
        this.wallHeatTransferCoefficient = wallHeatTransferCoefficient;
    }
}
