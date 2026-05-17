package neqsim.process.electricaldesign.pump;

import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pump.Pump;

/**
 * Electrical design for pumps.
 *
 * <p>
 * Extends {@link ElectricalDesign} with pump-specific electrical requirements. Pumps are typically
 * smaller loads than compressors and may use single-phase or three-phase motors depending on size.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PumpElectricalDesign extends ElectricalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for PumpElectricalDesign.
   *
   * @param processEquipment the pump equipment
   */
  public PumpElectricalDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
  }

  /** {@inheritDoc} */
  @Override
  protected double getProcessShaftPowerKW() {
    if (getProcessEquipment() instanceof Pump) {
      Pump pump = (Pump) getProcessEquipment();
      return pump.getPower("kW");
    }
    return getShaftPowerKW();
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    double power = getProcessShaftPowerKW();
    if (power > 200) {
      setRatedVoltageV(6600);
    } else if (power > 75) {
      setRatedVoltageV(690);
    } else if (power > 2.2) {
      setRatedVoltageV(400);
    } else {
      setRatedVoltageV(230);
      setPhases(1);
    }
  }
}
