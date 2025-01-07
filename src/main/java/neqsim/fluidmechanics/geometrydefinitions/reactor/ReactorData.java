package neqsim.fluidmechanics.geometrydefinitions.reactor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinition;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;

/**
 * <p>
 * ReactorData class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ReactorData extends GeometryDefinition {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ReactorData.class);

  /**
   * <p>
   * Constructor for ReactorData.
   * </p>
   */
  public ReactorData() {}

  /**
   * <p>
   * Constructor for ReactorData.
   * </p>
   *
   * @param diameter a double
   */
  public ReactorData(double diameter) {
    super(diameter);
  }

  /**
   * <p>
   * Constructor for ReactorData.
   * </p>
   *
   * @param diameter a double
   * @param roughness a double
   */
  public ReactorData(double diameter, double roughness) {
    super(diameter, roughness);
    packing =
        new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking();
  }

  /**
   * <p>
   * Constructor for ReactorData.
   * </p>
   *
   * @param diameter a double
   * @param packingType a int
   */
  public ReactorData(double diameter, int packingType) {
    super(diameter);
    setPackingType(packingType);
  }

  /** {@inheritDoc} */
  @Override
  public void setPackingType(int i) {
    // if(i!=100){
    packing =
        new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking();
    // }
  }

  /**
   * <p>
   * setPackingType.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setPackingType(String name) {
    if (name.equals("pallring")) {
      packing =
          new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking();
    } else if (name.equals("rashigring")) {
      packing =
          new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.RachigRingPacking();
    } else {
      System.out.println("packing " + name + " not defined in database - using pallrings");
      packing =
          new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPackingType(String name, String material, int size) {
    if (name.equals("pallring")) {
      packing =
          new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking(
              material, size);
    } else if (name.equals("rashigring")) {
      packing =
          new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.RachigRingPacking(
              material, size);
    } else {
      System.out.println("packing " + name + " not defined in database - using pallrings");
      packing =
          new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking(
              material, size);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ReactorData clone() {
    ReactorData clonedPipe = null;
    try {
      clonedPipe = (ReactorData) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    return clonedPipe;
  }
}
