/*
 * GeometryDefinitionInterface.java
 *
 * Created on 10. desember 2000, 18:56
 */

package neqsim.fluidMechanics.geometryDefinitions;

import neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PackingInterface;
import neqsim.fluidMechanics.geometryDefinitions.surrounding.SurroundingEnvironment;

/**
 * <p>
 * GeometryDefinitionInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface GeometryDefinitionInterface extends Cloneable {
    /**
     * <p>
     * setDiameter.
     * </p>
     *
     * @param diameter a double
     */
    public void setDiameter(double diameter);

    /**
     * <p>
     * setNodeLength.
     * </p>
     *
     * @param nodeLength a double
     */
    public void setNodeLength(double nodeLength);

    /**
     * <p>
     * setInnerSurfaceRoughness.
     * </p>
     *
     * @param innerSurfaceRoughness a double
     */
    public void setInnerSurfaceRoughness(double innerSurfaceRoughness);

    /**
     * <p>
     * init.
     * </p>
     */
    public void init();

    /**
     * <p>
     * getDiameter.
     * </p>
     *
     * @return a double
     */
    public double getDiameter();

    /**
     * <p>
     * getArea.
     * </p>
     *
     * @return a double
     */
    public double getArea();

    /**
     * <p>
     * getRadius.
     * </p>
     *
     * @return a double
     */
    public double getRadius();

    /**
     * <p>
     * getInnerSurfaceRoughness.
     * </p>
     *
     * @return a double
     */
    public double getInnerSurfaceRoughness();

    /**
     * <p>
     * getCircumference.
     * </p>
     *
     * @return a double
     */
    public double getCircumference();

    /**
     * <p>
     * getRelativeRoughnes.
     * </p>
     *
     * @return a double
     */
    public double getRelativeRoughnes();

    /**
     * <p>
     * getNodeLength.
     * </p>
     *
     * @return a double
     */
    public double getNodeLength();

    /**
     * <p>
     * getRelativeRoughnes.
     * </p>
     *
     * @param diameter a double
     * @return a double
     */
    public double getRelativeRoughnes(double diameter);

    /**
     * <p>
     * getWallHeatTransferCoefficient.
     * </p>
     *
     * @return a double
     */
    public double getWallHeatTransferCoefficient();

    /**
     * <p>
     * setWallHeatTransferCoefficient.
     * </p>
     *
     * @param outerHeatTransferCoefficient a double
     */
    public void setWallHeatTransferCoefficient(double outerHeatTransferCoefficient);

    /**
     * <p>
     * setPackingType.
     * </p>
     *
     * @param i a int
     */
    public void setPackingType(int i);

    /**
     * <p>
     * setPackingType.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param material a {@link java.lang.String} object
     * @param size a int
     */
    public void setPackingType(String name, String material, int size);

    /**
     * <p>
     * getPacking.
     * </p>
     *
     * @return a
     *         {@link neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings.PackingInterface}
     *         object
     */
    public PackingInterface getPacking();

    /**
     * <p>
     * getGeometry.
     * </p>
     *
     * @return a {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface}
     *         object
     */
    public GeometryDefinitionInterface getGeometry();

    /**
     * <p>
     * clone.
     * </p>
     *
     * @return a {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface}
     *         object
     */
    public GeometryDefinitionInterface clone();

    /**
     * <p>
     * getInnerWallTemperature.
     * </p>
     *
     * @return a double
     */
    public double getInnerWallTemperature();

    /**
     * <p>
     * setInnerWallTemperature.
     * </p>
     *
     * @param temperature a double
     */
    public void setInnerWallTemperature(double temperature);

    /**
     * <p>
     * getSurroundingEnvironment.
     * </p>
     *
     * @return a
     *         {@link neqsim.fluidMechanics.geometryDefinitions.surrounding.SurroundingEnvironment}
     *         object
     */
    public SurroundingEnvironment getSurroundingEnvironment();

    /**
     * <p>
     * setSurroundingEnvironment.
     * </p>
     *
     * @param surroundingEnvironment a
     *        {@link neqsim.fluidMechanics.geometryDefinitions.surrounding.SurroundingEnvironment}
     *        object
     */
    public void setSurroundingEnvironment(SurroundingEnvironment surroundingEnvironment);
}
