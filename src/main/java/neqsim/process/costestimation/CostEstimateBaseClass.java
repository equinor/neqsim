package neqsim.process.costestimation;

import java.util.ArrayList;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;

/**
 * <p>
 * CostEstimateBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CostEstimateBaseClass implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CostEstimateBaseClass.class);

  private SystemMechanicalDesign processdesign;
  private double CAPEXperWeight = 1000.0; // KNOK/tones

  /**
   * <p>
   * Constructor for CostEstimateBaseClass.
   * </p>
   *
   * @param processdesign a {@link neqsim.process.mechanicaldesign.SystemMechanicalDesign} object
   */
  public CostEstimateBaseClass(SystemMechanicalDesign processdesign) {
    this.processdesign = processdesign;
  }

  /**
   * <p>
   * Constructor for CostEstimateBaseClass.
   * </p>
   *
   * @param processdesign a {@link neqsim.process.mechanicaldesign.SystemMechanicalDesign}
   * @param costFactor cost factor
   */
  public CostEstimateBaseClass(SystemMechanicalDesign processdesign, double costFactor) {
    this(processdesign);
    this.CAPEXperWeight = costFactor;
  }

  /**
   * <p>
   * getWeightBasedCAPEXEstimate.
   * </p>
   *
   * @return a double
   */
  public double getWeightBasedCAPEXEstimate() {
    return this.processdesign.getTotalWeight() * CAPEXperWeight;
  }

  /**
   * <p>
   * getCAPEXestimate.
   * </p>
   *
   * @return a double
   */
  public double getCAPEXestimate() {
    double cost = 0;
    ArrayList<String> names = processdesign.getProcess().getAllUnitNames();
    for (int i = 0; i < names.size(); i++) {
      try {
        if (!(this.processdesign.getProcess()
            .getUnit(names.get(i)) == null)) {
          cost +=
              this.processdesign.getProcess().getUnit(names.get(i))
                  .getMechanicalDesign().getCostEstimate().getTotaltCost();
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    return cost;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(CAPEXperWeight);
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
    CostEstimateBaseClass other = (CostEstimateBaseClass) obj;
    return Double.doubleToLongBits(CAPEXperWeight) == Double.doubleToLongBits(other.CAPEXperWeight);
  }
}
