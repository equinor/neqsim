package neqsim.processSimulation.util.monitor;

import java.util.Map;
import java.util.HashMap;
import neqsim.thermo.system.SystemInterface;

public class Fluid {
    public String name;

    public Double volumeFlow;
    public Double molarMass;
    public Double massDensity;
    public Double massflow;

    public Map<String, Double> compProp;

    public Map<String, Map> definedComponent;
    public Map<String, Map> oilComponent;

    public Fluid() {
        this.definedComponent = new HashMap<>();
        this.oilComponent = new HashMap<>();
    }

    public Fluid(SystemInterface inputFluid) {
        this.definedComponent = new HashMap<>();
        this.oilComponent = new HashMap<>();

        name = inputFluid.getFluidName();

        for (int i = 0; i < inputFluid.getNumberOfComponents(); i++) {
            compProp = new HashMap<>();
            if (inputFluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
                compProp.put("molFraction", inputFluid.getPhase(0).getComponent(i).getz());
                compProp.put("massFlow",
                        inputFluid.getPhase(0).getComponent(i).getFlowRate("kg/hr"));
                compProp.put("molarMass", inputFluid.getPhase(0).getComponent(i).getMolarMass());
                compProp.put("normalLiquidDensity",
                        inputFluid.getPhase(0).getComponent(i).getNormalLiquidDensity());
                oilComponent.put(inputFluid.getPhase(0).getComponent(i).getComponentName(),
                        compProp);
            } else {
                compProp.put("molFraction", inputFluid.getPhase(0).getComponent(i).getz());
                compProp.put("massFlow",
                        inputFluid.getPhase(0).getComponent(i).getFlowRate("kg/hr"));
                definedComponent.put(inputFluid.getPhase(0).getComponent(i).getComponentName()
                        .replaceAll("-", ""), compProp);
            }
        }

        molarMass = inputFluid.getMolarMass();
        massDensity = inputFluid.getDensity("kg/m3");
        massflow = inputFluid.getFlowRate("kg/hr");
        volumeFlow = inputFluid.getFlowRate("m3/hr");
    }

    SystemInterface getNeqSimFluid() {
        SystemInterface tempFluid = new neqsim.thermo.system.SystemSrkEos();

        definedComponent.keySet().forEach(key -> {
            tempFluid.addComponent(key, (Double) definedComponent.get(key).get("molFraction"));
        });

        oilComponent.keySet().forEach(key -> {
            tempFluid.addTBPfraction(key, (Double) definedComponent.get(key).get("molFraction"),
                    (Double) definedComponent.get(key).get("molarMass"),
                    (Double) definedComponent.get(key).get("normalLiquidDensity"));
        });

        tempFluid.setMixingRule(2);


        return tempFluid;
    }

    public void print() {}
}
