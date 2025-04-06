package neqsim.process.util.monitor;

import neqsim.process.equipment.distillation.DistillationColumn;

/**
 * <p>
 * PumpResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class DistillationColumnResponse extends BaseResponse {

  public Double massBalanceError;
  public Double[] trayTemperature;
  public Double[] trayPressure;
  public int numberOfTrays;
  public Double[] trayVaporFlowRate;
  public Double[] trayLiquidFlowRate;
  public Double[] trayFeedFlow;
  public Double[] trayMassBalance;


  /**
   * <p>
   * Constructor for DistillationColumnResponse.
   * </p>
   *
   * @param column a {@link neqsim.process.equipment.distillation.DistillationColumn} object
   */
  public DistillationColumnResponse(DistillationColumn column) {
    super(column);
    massBalanceError = column.getMassBalanceError();

    int numberOfTrays = column.getNumerOfTrays();
    trayTemperature = new Double[numberOfTrays];
    trayMassBalance = new Double[numberOfTrays];
    trayVaporFlowRate = new Double[numberOfTrays];
    trayLiquidFlowRate = new Double[numberOfTrays];
    trayFeedFlow = new Double[numberOfTrays];
    trayFeedFlow = new Double[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      trayFeedFlow[i] = column.getTray(i).getFeedRate("kg/hr");
    }
    for (int i = 0; i < numberOfTrays; i++) {
      trayMassBalance[i] = column.getTray(i).massBalance();
    }
    trayPressure = new Double[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      trayTemperature[i] = column.getTray(i).getTemperature() - 273.15;
      trayPressure[i] = column.getTray(i).getPressure("bara");
      trayVaporFlowRate[i] = column.getTray(i).getVaporFlowRate("kg/hr");
      trayLiquidFlowRate[i] = column.getTray(i).getLiquidFlowRate("kg/hr");
    }
  }
}
