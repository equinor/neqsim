package neqsim.process.util.monitor;

import neqsim.process.equipment.distillation.DistillationColumn;

/**
 * <p>
 * HeaterResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class DistillationColumnResponse {
  public String name = "DistillationColumnResponse";

  public Double[] temperature;
  public Double[] pressure;
  public Double[] reboilerDuty;
  public Double[] condenserDuty;

  /**
   * <p>
   * Constructor for MultiStreamHeatExchangerResponse.
   * </p>
   *
   * @param inputHX a {@link neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger} object
   */
  public DistillationColumnResponse(DistillationColumn column) {
    name = column.getName();

    // Initialize arrays based on the number of feed streams
    int sections = column.getNumberOfSections();
    temperature = new Double[sections];
    pressure = new Double[sections];

    for (int i = 0; i < sections; i++) {
      temperature[i] = column.getTray(i).getTemperature() - 273.15;
      pressure[i] = column.getTray(i).getPressure() - 273.15;
    }
    if (column.isHasReboiler()) {
      reboilerDuty = new Double[1];
      reboilerDuty[0] = column.getReboiler().getDuty();
    } else {
      reboilerDuty = new Double[0];
    }
    if (column.isHasCondenser()) {
      condenserDuty = new Double[1];
      condenserDuty[0] = column.getCondenser().getDuty();
    } else {
      condenserDuty = new Double[0];
    }
  }
}
