package neqsim.fluidmechanics.geometrydefinitions.reactor;

import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinition;

/**
 * ReactorData class.
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
   * Constructor for ReactorData.
   */
  public ReactorData() {
  }

  /**
   * Constructor for ReactorData.
   *
   * @param diameter a double
   */
  public ReactorData(double diameter) {
    super(diameter);
  }

  /**
   * Constructor for ReactorData.
   *
   * @param diameter a double
   * @param roughness a double
   */
  public ReactorData(double diameter, double roughness) {
    super(diameter, roughness);
    packing = new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking();
  }

  /**
   * Constructor for ReactorData.
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
    packing = new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking();
    // }
  }

  /**
   * setPackingType.
   *
   * @param name packing family (Pall ring or Raschig ring), including legacy class-name aliases
   * @throws IllegalArgumentException if the family is not supported
   */
  public void setPackingType(String name) {
    String family = normalizePackingFamily(name);
    if (family.equals("pallring")) {
      packing = new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking();
    } else if (family.equals("rashigring")) {
      packing = new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.RachigRingPacking();
    } else {
      throw new IllegalArgumentException("Unknown packing family: " + name);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPackingType(String name, String material, int size) {
    String family = normalizePackingFamily(name);
    if (family.equals("pallring")) {
      packing = new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.PallRingPacking(material, size);
    } else if (family.equals("rashigring")) {
      packing = new neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings.RachigRingPacking(material,
          size);
    } else {
      throw new IllegalArgumentException("Unknown packing family: " + name);
    }
  }

  /**
   * Normalize supported historical packing family spellings.
   *
   * @param name family name or legacy class name
   * @return canonical family key
   * @throws IllegalArgumentException if the name is missing
   */
  private static String normalizePackingFamily(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Packing family must be specified");
    }
    String family = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    if (family.equals("pallringpacking")) {
      return "pallring";
    }
    if (family.equals("raschigring") || family.equals("rachigring") || family.equals("rachigringpacking")) {
      return "rashigring";
    }
    return family;
  }

  /** {@inheritDoc} */
  @Override
  public ReactorData clone() {
    ReactorData clonedPipe = null;
    try {
      clonedPipe = (ReactorData) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedPipe;
  }
}
