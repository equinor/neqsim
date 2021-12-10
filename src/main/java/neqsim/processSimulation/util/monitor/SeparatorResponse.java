package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;

public class SeparatorResponse {

    public String name;
    public Double gasLoadFactor;
    public Double massflow;
    public Fluid gasFluid, oilFluid;
    
    public SeparatorResponse() {
    }

    public SeparatorResponse(ThreePhaseSeparator inputSeparator){
        name = inputSeparator.getName();
        massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
        gasLoadFactor = inputSeparator.getGasLoadFactor();
        oilFluid = new Fluid(inputSeparator.getOilOutStream().getFluid());
        gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
    }

    public SeparatorResponse(Separator inputSeparator){
        name = inputSeparator.getName();
        massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
        gasLoadFactor = inputSeparator.getGasLoadFactor();
        oilFluid = new Fluid(inputSeparator.getLiquidOutStream().getFluid());
        gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
    }
}
