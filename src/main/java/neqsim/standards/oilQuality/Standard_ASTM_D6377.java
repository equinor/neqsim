package neqsim.standards.oilQuality;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class Standard_ASTM_D6377 extends neqsim.standards.Standard {
    private static final long serialVersionUID = 1000;
    String unit = "bara";
    double RVP = 1.0;

    public Standard_ASTM_D6377(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    @Override
    public void calculate() {
        this.thermoSystem.setTemperature(273.15 + 37.8);
        this.thermoSystem.setPressure(1.01325);
        this.thermoOps = new ThermodynamicOperations(thermoSystem);
        try {
            this.thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        double TVP = this.thermoSystem.getPressure();
        double liquidVolume = thermoSystem.getVolume();
        
        this.thermoSystem.setPressure(0.9);
        try {
            this.thermoOps.TVflash(liquidVolume*4.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        RVP = (0.752 * (100.0*this.thermoSystem.getPressure()) + 6.07)/100.0;

    }

    @Override
    public boolean isOnSpec() {
        return true;
    }

    @Override
    public String getUnit(String returnParameter) {
        return unit;
    }

    @Override
    public double getValue(String returnParameter, java.lang.String returnUnit) {
        return RVP;
    }

    @Override
    public double getValue(String returnParameter) {
        return RVP;
    }
    
    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
        testSystem.addComponent("methane", 0.0006538);
        testSystem.addComponent("ethane", 0.006538);
        testSystem.addComponent("propane", 0.006538);
        testSystem.addComponent("n-pentane", 0.545);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
        standard.calculate();
        System.out.println("RVP " + standard.getValue("RVP", "bara"));
       
    }
}
