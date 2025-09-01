package neqsim.process.mechanicaldesign.heatexchanger;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for a generic heat exchanger. Provides rough estimates of size
 * and weight based on duty and assumed overall heat-transfer coefficients.
 */
public class HeatExchangerMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for HeatExchangerMechanicalDesign.
   *
   * @param equipment {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public HeatExchangerMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    HeatExchanger exchanger = (HeatExchanger) getProcessEquipment();

    double U = 500.0; // W/(m^2*K)
    double area = exchanger.getUAvalue() / U;
    if (area <= 0.0) {
      double duty = Math.abs(exchanger.getDuty());
      double deltaT =
          Math.abs(exchanger.getOutTemperature(0) - exchanger.getOutTemperature(1));
      if (deltaT > 0.0) {
        area = duty / (U * deltaT);
      }
    }
    if (area <= 0.0) {
      area = 1.0;
    }

    innerDiameter = Math.sqrt(area / (2.0 * Math.PI));
    tantanLength = 2.0 * innerDiameter;
    wallThickness = 0.01; // 10 mm
    outerDiameter = innerDiameter + 2.0 * wallThickness;

    double shellArea = Math.PI * innerDiameter * tantanLength;
    double steelDensity = 7850.0; // kg/m3
    weightVessel = shellArea * wallThickness * steelDensity;
    setWeigthVesselShell(weightVessel);
    setWeightTotal(weightVessel);
    setModuleLength(tantanLength + 1.0);
    setModuleWidth(innerDiameter + 1.0);
    setModuleHeight(innerDiameter + 1.0);
  }
}

