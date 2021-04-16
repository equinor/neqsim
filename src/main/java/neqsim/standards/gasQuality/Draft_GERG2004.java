/*
 * Standard_ISO1992.java
 *
 * Created on 13. juni 2004, 23:30
 */

package neqsim.standards.gasQuality;

import java.text.*;
import neqsim.thermo.system.SystemGERG2004Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class Draft_GERG2004 extends neqsim.standards.Standard {

    private static final long serialVersionUID = 1000;
    double specPressure = 70.0;
    double initTemperature = 273.15;

    /** Creates a new instance of Standard_ISO1992 */
    public Draft_GERG2004() {
        name = "Draft_GERG2004";
        standardDescription = "reference properties of natural gas";
    }

    public Draft_GERG2004(SystemInterface thermoSystemMet) {
        this();

        if (thermoSystemMet.getModelName().equals("GERG2004-EOS")) {
            this.thermoSystem = thermoSystemMet;
        } else {
            System.out.println("setting model GERG2004 EOS...");
            this.thermoSystem = new SystemGERG2004Eos(thermoSystemMet.getTemperature(), thermoSystemMet.getPressure());
            for (int i = 0; i < thermoSystemMet.getPhase(0).getNumberOfComponents(); i++) {
                this.thermoSystem.addComponent(thermoSystemMet.getPhase(0).getComponent(i).getName(),
                        thermoSystemMet.getPhase(0).getComponent(i).getNumberOfmoles());
            }
        }

        this.thermoSystem.setMixingRule(1);
        thermoSystem.init(0);
        thermoSystem.init(1);

        this.thermoOps = new ThermodynamicOperations(this.thermoSystem);
    }

    public void calculate() {

        try {
            this.thermoOps.TPflash();
            thermoSystem.display();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public double getValue(String returnParameter, java.lang.String returnUnit) {
        return 0.0;
    }

    public double getValue(String returnParameter) {
        if (returnParameter.equals("dewPointTemperature")) {
            return 0.0;
        }
        if (returnParameter.equals("pressure")) {
            return this.thermoSystem.getPressure();
        } else {
            return 0.0;
        }
    }

    public String getUnit(String returnParameter) {
        if (returnParameter.equals("dewPointTemperature")) {
            return "";
        }
        if (returnParameter.equals("pressureUnit")) {
            return "";
        } else {
            return "";
        }
    }

    public boolean isOnSpec() {
        return false;
    }

    public String[][] createTable(String name) {
        // thermoSystem.setNumberOfPhases(1);

        thermoSystem.createTable(name);

        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");
        String[][] table = new String[thermoSystem.getPhases()[0].getNumberOfComponents() + 30][6];
        String[] names = { "", "Phase 1", "Phase 2", "Phase 3", "Unit" };
        table[0][0] = "";// getPhases()[0].getPhaseTypeName();//"";

        for (int i = 0; i < thermoSystem.getPhases()[0].getNumberOfComponents() + 30; i++) {
            for (int j = 0; j < 6; j++) {
                table[i][j] = "";
            }
        }
        for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
            table[0][i + 1] = thermoSystem.getPhase(i).getPhaseTypeName();
        }

        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);
        for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
            for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 1][0] = thermoSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 1][i + 1] = nf
                        .format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getComponents()[j].getx(), buf,
                                test)
                        .toString();
                table[j + 1][4] = "[-]";
            }

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "Compressibility Factor";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] = nf
                    .format(thermoSystem.getPhase(i).getZ());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

            buf = new StringBuffer();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 4][0] = "Density";
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 4][i + 1] = nf
                    .format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getPhysicalProperties().getDensity(),
                            buf, test)
                    .toString();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 4][4] = "[kg/m^3]";

            // Double.longValue(system.getPhase(phaseIndex[i]).getBeta());

            buf = new StringBuffer();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents()
                    + 5][0] = "PhaseFraction";
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 5][i + 1] = nf
                    .format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getBeta(), buf, test).toString();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 5][4] = "[-]";

            buf = new StringBuffer();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 6][0] = "MolarMass";
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 6][i + 1] = nf
                    .format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getMolarMass() * 1000, buf, test)
                    .toString();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 6][4] = "[kg/kmol]";

            buf = new StringBuffer();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 7][0] = "Cp";
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 7][i + 1] = nf.format(
                    (thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getCp()
                            / (thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfMolesInPhase()
                                    * thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getMolarMass() * 1000)),
                    buf, test).toString();
            table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 7][4] = "[kJ/kg*K]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] = Double
                    .toString(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getPressure());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] = Double
                    .toString(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getTemperature());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
            Double.toString(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getTemperature());

        }

        resultTable = table;
        return table;
    }
}
