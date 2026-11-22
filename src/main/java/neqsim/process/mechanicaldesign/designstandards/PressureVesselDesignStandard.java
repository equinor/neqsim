package neqsim.process.mechanicaldesign.designstandards;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignMarginResult;

/**
 * PressureVesselDesignStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class PressureVesselDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private final MechanicalDesignMarginResult safetyMargins;

  /**
   * Constructor for PressureVesselDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public PressureVesselDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    safetyMargins = computeSafetyMargins();
  }

  /**
   * Calculates wall thickness using the separator's current internal diameter.
   *
   * @return wall thickness in metres, including corrosion allowance
   */
  public double calcWallThickness() {
    checkSupportedStandard();
    return calcWallThickness(((Separator) equipment.getProcessEquipment()).getInternalDiameter());
  }

  /**
   * Calculates wall thickness for an explicitly sized vessel without applying geometry to the process equipment.
   *
   * @param innerDiameter vessel internal diameter in metres
   * @return wall thickness in metres, including the configured corrosion allowance in millimetres
   * @throws UnsupportedOperationException if the selected pressure-vessel code is not implemented
   */
  public double calcWallThickness(double innerDiameter) {
    checkSupportedStandard();
    double wallT = 0;
    MaterialPlateDesignStandard matPlateStyandard = ((MaterialPlateDesignStandard) equipment.getDesignStandard()
        .get("material plate design codes"));
    JointEfficiencyPlateStandard JEPlateStyandard = ((JointEfficiencyPlateStandard) equipment.getDesignStandard()
        .get("plate Joint Efficiency design codes"));
    double maxAllowableStress = matPlateStyandard.getDivisionClass();
    double jointEfficiency = JEPlateStyandard.getJEFactor();

    if (standardName.equals("ASME - Pressure Vessel Code") || standardName.startsWith("ASME-VIII-Div1")) {
      wallT = equipment.getMaxOperationPressure() / 10.0 * innerDiameter * 1e3
          / (2.0 * maxAllowableStress * jointEfficiency - 1.2 * equipment.getMaxOperationPressure() / 10.0)
          + equipment.getCorrosionAllowance();
    } else if (standardName.equals("BS 5500 - Pressure Vessel") || standardName.startsWith("PD-5500")) {
      wallT = equipment.getMaxOperationPressure() / 10.0 * innerDiameter * 1e3
          / (2.0 * maxAllowableStress - jointEfficiency / 10.0) + equipment.getCorrosionAllowance();
    } else if (standardName.equals("European Code") || standardName.startsWith("EN-13445")) {
      wallT = equipment.getMaxOperationPressure() / 10.0 * innerDiameter / 2.0 * 1e3
          / (2.0 * maxAllowableStress * jointEfficiency - 0.2 * equipment.getMaxOperationPressure() / 10.0)
          + equipment.getCorrosionAllowance();
    } else {
      wallT = equipment.getMaxOperationPressure() / 10.0 * innerDiameter / 2.0 * 1e3
          / (2.0 * maxAllowableStress * jointEfficiency - 0.2 * equipment.getMaxOperationPressure() / 10.0)
          + equipment.getCorrosionAllowance();
    }
    return wallT / 1000.0; // return wall thickness in meter
  }

  private void checkSupportedStandard() {
    if (standardName.startsWith("ASME-VIII-Div2") || standardName.startsWith("API-620")
        || standardName.startsWith("API-625") || standardName.startsWith("API-650")) {
      throw new UnsupportedOperationException(
          "No edition-specific pressure-vessel calculation is implemented for " + standardName);
    }
  }

  public MechanicalDesignMarginResult getSafetyMargins() {
    return safetyMargins;
  }
}
