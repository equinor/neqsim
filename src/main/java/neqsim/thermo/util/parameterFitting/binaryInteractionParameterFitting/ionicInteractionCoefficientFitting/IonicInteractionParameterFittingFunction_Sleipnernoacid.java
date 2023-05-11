package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * IonicInteractionParameterFittingFunction_Sleipnernoacid class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IonicInteractionParameterFittingFunction_Sleipnernoacid
    extends LevenbergMarquardtFunction implements ThermodynamicConstantsInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger =
      LogManager.getLogger(IonicInteractionParameterFittingFunction_Sleipnernoacid.class);

  /**
   * <p>
   * Constructor for IonicInteractionParameterFittingFunction_Sleipnernoacid.
   * </p>
   */
  public IonicInteractionParameterFittingFunction_Sleipnernoacid() {}

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    try {
      thermoOps.bubblePointPressureFlash(false);
      // System.out.println("pressure: " + system.getPressure());
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    return system.getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double calcTrueValue(double val) {
    return val;
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
    /*
     * int MDEAplusNumb = 0, MDEANumb = 0, CO2Numb = 0, HCO3Numb = 0, WaterNumb = 0, AcidnegNumb =
     * 0; int j = 0; do { MDEAplusNumb = j; j++; } while (!system.getPhases()[1].getComponents()[j -
     * 1].getComponentName().equals("MDEA+")); j = 0;
     * 
     * do { MDEANumb = j; j++; } while (!system.getPhases()[1].getComponents()[j -
     * 1].getComponentName().equals("MDEA")); j = 0;
     * 
     * do { CO2Numb = j; j++; } while (!system.getPhases()[1].getComponents()[j -
     * 1].getComponentName().equals("CO2"));
     * 
     * // System.out.println("CO2 " + CO2Numb ); j = 0; do { HCO3Numb = j; j++; } while
     * (!system.getPhases()[1].getComponents()[j - 1].getComponentName().equals("HCO3-"));
     * 
     * j = 0; do { WaterNumb = j; j++; } while (!system.getPhases()[1].getComponents()[j -
     * 1].getComponentName().equals("water"));
     * 
     * j = 0; do { AcidnegNumb = j; j++; } while (!system.getPhases()[1].getComponents()[j -
     * 1].getComponentName().equals("Ac-"));
     */
    // System.out.println("HCO3- " +
    // system.getPhase(1).getComponent(HCO3Numb).getx());

    /*
     * System.out.println("Ac- " + system.getPhase(1).getComponent(AcidnegNumb).getx());
     * System.out.println("HAc " + system.getPhase(1).getComponent(AcidNumb).getx());
     */

    /*
     * if(i==0){ ((ElectrolyteMixingRulesInterface)
     * ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb,WaterNumb, value);
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb,WaterNumb, value); } if(i==1){
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb, CO2Numb,value);
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb, CO2Numb,value); } if(i==2){
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb, MDEANumb, value);
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb, MDEANumb, value); } if(i==3){
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb,HCO3Numb, value);
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb,HCO3Numb, value); }
     */

    /*
     * if(i==0){ ((ElectrolyteMixingRulesInterface)
     * ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[0]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb, AcidnegNumb, value);
     * ((ElectrolyteMixingRulesInterface) ((PhaseModifiedFurstElectrolyteEos)system.getPhases()[1]).
     * getElectrolyteMixingRule()).setWijParameter(MDEAplusNumb, AcidnegNumb, value); }
     */
  }
}
