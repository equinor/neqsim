package neqsim.process.mechanicaldesign;

import java.util.ArrayList;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * <p>
 * SystemMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SystemMechanicalDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SystemMechanicalDesign.class);

  ProcessSystem processSystem = null;
  double totalPlotSpace = 0.0;
  double totalVolume = 0.0;
  double totalWeight = 0.0;
  int numberOfModules = 0;

  /**
   * <p>
   * Constructor for SystemMechanicalDesign.
   * </p>
   *
   * @param processSystem a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public SystemMechanicalDesign(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * <p>
   * getProcess.
   * </p>
   *
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public ProcessSystem getProcess() {
    return processSystem;
  }

  /**
   * <p>
   * setCompanySpecificDesignStandards.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setCompanySpecificDesignStandards(String name) {
    for (int i = 0; i < this.processSystem.getUnitOperations().size(); i++) {
      this.getProcess().getUnitOperations().get(i).getMechanicalDesign()
          .setCompanySpecificDesignStandards(name);
    }
  }

  /**
   * <p>
   * runDesignCalculation.
   * </p>
   */
  public void runDesignCalculation() {
    ArrayList<String> names = this.processSystem.getAllUnitNames();
    for (int i = 0; i < names.size(); i++) {
      try {
        if (!(this.processSystem.getUnit(names.get(i)) == null)) {
          this.processSystem.getUnit(names.get(i))
              .initMechanicalDesign();
          this.processSystem.getUnit(names.get(i))
              .getMechanicalDesign().calcDesign();
          totalPlotSpace += this.processSystem.getUnit(names.get(i))
              .getMechanicalDesign().getModuleHeight()
              * this.processSystem.getUnit(names.get(i))
                  .getMechanicalDesign().getModuleLength();
          totalVolume += this.processSystem.getUnit(names.get(i))
              .getMechanicalDesign().getVolumeTotal();
          totalWeight += this.processSystem.getUnit(names.get(i))
              .getMechanicalDesign().getWeightTotal();
          numberOfModules++;
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
  }

  /**
   * <p>
   * setDesign.
   * </p>
   */
  public void setDesign() {
    for (int i = 0; i < this.processSystem.getUnitOperations().size(); i++) {
      this.processSystem.getUnitOperations().get(i).getMechanicalDesign().setDesign();
    }
  }

  /**
   * <p>
   * Getter for the field <code>totalPlotSpace</code>.
   * </p>
   *
   * @return a double
   */
  public double getTotalPlotSpace() {
    return totalPlotSpace;
  }

  /**
   * <p>
   * Getter for the field <code>totalVolume</code>.
   * </p>
   *
   * @return a double
   */
  public double getTotalVolume() {
    return totalVolume;
  }

  /**
   * <p>
   * Getter for the field <code>totalWeight</code>.
   * </p>
   *
   * @return a double
   */
  public double getTotalWeight() {
    return totalWeight;
  }

  /**
   * <p>
   * getTotalNumberOfModules.
   * </p>
   *
   * @return a int
   */
  public int getTotalNumberOfModules() {
    return numberOfModules;
  }

  /**
   * <p>
   * getMechanicalWeight.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getMechanicalWeight(String unit) {
    double weight = 0.0;
    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      processSystem.getUnitOperations().get(i).getMechanicalDesign().calcDesign();
      System.out.println("Name " + processSystem.getUnitOperations().get(i).getName() + "  weight "
          + processSystem.getUnitOperations().get(i).getMechanicalDesign().getWeightTotal());
      weight += processSystem.getUnitOperations().get(i).getMechanicalDesign().getWeightTotal();
    }
    return weight;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(numberOfModules, processSystem, totalPlotSpace, totalVolume, totalWeight);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    SystemMechanicalDesign other = (SystemMechanicalDesign) obj;
    return numberOfModules == other.numberOfModules
        && Objects.equals(processSystem, other.processSystem)
        && Double.doubleToLongBits(totalPlotSpace) == Double.doubleToLongBits(other.totalPlotSpace)
        && Double.doubleToLongBits(totalVolume) == Double.doubleToLongBits(other.totalVolume)
        && Double.doubleToLongBits(totalWeight) == Double.doubleToLongBits(other.totalWeight);
  }
}
