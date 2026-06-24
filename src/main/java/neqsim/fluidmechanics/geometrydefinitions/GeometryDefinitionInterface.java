/*
 * GeometryDefinitionInterface.java
 *
 * Created on 10. desember 2000, 18:56
 */

package neqsim.fluidmechanics.geometrydefinitions;

import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PackingInterface;
import neqsim.fluidmechanics.geometrydefinitions.surrounding.SurroundingEnvironment;

/**
 * GeometryDefinitionInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface GeometryDefinitionInterface extends Cloneable {
  /**
   * setDiameter.
   *
   * @param diameter a double
   */
  public void setDiameter(double diameter);

  /**
   * setNodeLength.
   *
   * @param nodeLength a double
   */
  public void setNodeLength(double nodeLength);

  /**
   * setInnerSurfaceRoughness.
   *
   * @param innerSurfaceRoughness a double
   */
  public void setInnerSurfaceRoughness(double innerSurfaceRoughness);

  /**
   * init.
   */
  public void init();

  /**
   * getDiameter.
   *
   * @return a double
   */
  public double getDiameter();

  /**
   * getArea.
   *
   * @return a double
   */
  public double getArea();

  /**
   * getRadius.
   *
   * @return a double
   */
  public double getRadius();

  /**
   * getInnerSurfaceRoughness.
   *
   * @return a double
   */
  public double getInnerSurfaceRoughness();

  /**
   * getCircumference.
   *
   * @return a double
   */
  public double getCircumference();

  /**
   * getRelativeRoughnes.
   *
   * @return a double
   */
  public double getRelativeRoughnes();

  /**
   * getRelativeRoughnes.
   *
   * @param diameter a double
   * @return a double
   */
  public double getRelativeRoughnes(double diameter);

  /**
   * getNodeLength.
   *
   * @return a double
   */
  public double getNodeLength();

  /**
   * getWallHeatTransferCoefficient.
   *
   * @return a double
   */
  public double getWallHeatTransferCoefficient();

  /**
   * setWallHeatTransferCoefficient.
   *
   * @param outerHeatTransferCoefficient a double
   */
  public void setWallHeatTransferCoefficient(double outerHeatTransferCoefficient);

  /**
   * setPackingType.
   *
   * @param i a int
   */
  public void setPackingType(int i);

  /**
   * setPackingType.
   *
   * @param name a {@link java.lang.String} object
   * @param material a {@link java.lang.String} object
   * @param size a int
   */
  public void setPackingType(String name, String material, int size);

  /**
   * getPacking.
   *
   * @return a {@link neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PackingInterface} object
   */
  public PackingInterface getPacking();

  /**
   * getGeometry.
   *
   * @return a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface} object
   */
  public GeometryDefinitionInterface getGeometry();

  /**
   * clone.
   *
   * @return a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface} object
   */
  public GeometryDefinitionInterface clone();

  /**
   * getInnerWallTemperature.
   *
   * @return a double
   */
  public double getInnerWallTemperature();

  /**
   * setInnerWallTemperature.
   *
   * @param temperature a double
   */
  public void setInnerWallTemperature(double temperature);

  /**
   * getSurroundingEnvironment.
   *
   * @return a {@link neqsim.fluidmechanics.geometrydefinitions.surrounding.SurroundingEnvironment} object
   */
  public SurroundingEnvironment getSurroundingEnvironment();

  /**
   * setSurroundingEnvironment.
   *
   * @param surroundingEnvironment a
   * {@link neqsim.fluidmechanics.geometrydefinitions.surrounding.SurroundingEnvironment} object
   */
  public void setSurroundingEnvironment(SurroundingEnvironment surroundingEnvironment);
}
