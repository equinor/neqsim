package neqsim.processSimulation.util.monitor;

import java.util.LinkedList;
import java.util.Arrays;
import java.util.List;
import neqsim.thermo.system.SystemInterface;
import org.apache.commons.lang.ArrayUtils;
import java.util.stream.Collectors;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.separator.Separator;

public class SeparatorResponse {
    public String name;

    public Double gasLoadFactor;
    public Double massflow;
    public Fluid gasFluid, oilFluid;

    public SeparatorResponse() {}

    public SeparatorResponse(ThreePhaseSeparator inputSeparator) {
        name = inputSeparator.getName();
        massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
        gasLoadFactor = inputSeparator.getGasLoadFactor();
        oilFluid = new Fluid(inputSeparator.getOilOutStream().getFluid());
        gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
    }

    public SeparatorResponse(Separator inputSeparator) {
        name = inputSeparator.getName();
        massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
        gasLoadFactor = inputSeparator.getGasLoadFactor();
        oilFluid = new Fluid(inputSeparator.getLiquidOutStream().getFluid());
        gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
    }
}
