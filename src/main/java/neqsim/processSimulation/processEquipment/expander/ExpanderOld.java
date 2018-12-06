/*
 * ThrottelValve.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.expander;

import java.awt.*;
import java.text.*;
import javax.swing.*;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 * @version
 */
public class ExpanderOld extends ProcessEquipmentBaseClass implements ExpanderInterface {

    private static final long serialVersionUID = 1000;

    protected String name = new String();
    SystemInterface thermoSystem;
    ThermodynamicOperations thermoOps;
    StreamInterface inletStream;
    StreamInterface outStream;
    double dH = 0.0;
    double pressure = 0.0;

    /**
     * Creates new ThrottelValve
     */
    public ExpanderOld() {
    }

    public ExpanderOld(Stream inletStream) {
        setInletStream(inletStream);
    }

    public ExpanderOld(String name, StreamInterface inletStream) {
        this.name = name;
        setInletStream(inletStream);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        outStream = (StreamInterface) inletStream.clone();
    }

    public void setOutletPressure(double pressure) {
        this.pressure = pressure;
    }

    public double getEnergy() {
        return dH;
    }

    public StreamInterface getOutStream() {
        return outStream;
    }

    public void run() {
        System.out.println("expander running..");
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoSystem.init(3);
        double hinn = thermoSystem.getEnthalpy();
        double entropy = thermoSystem.getEntropy();
        thermoSystem.setPressure(pressure);
        System.out.println("entropy inn.." + entropy);
        thermoOps.PSflash(entropy);
        dH = thermoSystem.getEnthalpy() - hinn;
        outStream.setThermoSystem(thermoSystem);
    }

    public void displayResult() {

        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");

        JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new FlowLayout());

        thermoSystem.initPhysicalProperties();
        String[][] table = new String[20][5];
        String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
        table[0][0] = "";
        table[0][1] = "";
        table[0][2] = "";
        table[0][3] = "";
        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);

        for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
            for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 1][0] = thermoSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 1][i + 1] = nf.format(thermoSystem.getPhases()[i].getComponents()[j].getx(), buf, test).toString();
                table[j + 1][4] = "[-]";
            }
            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] = nf.format(thermoSystem.getPhases()[i].getPhysicalProperties().getDensity(), buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[kg/m^3]";

            //  Double.longValue(thermoSystem.getPhases()[i].getBeta());
            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] = nf.format(thermoSystem.getPhases()[i].getBeta(), buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][i + 1] = nf.format(thermoSystem.getPhases()[i].getMolarMass() * 1000, buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][4] = "[kg/kmol]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Cp";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] = nf.format((thermoSystem.getPhases()[i].getCp() / thermoSystem.getPhases()[i].getNumberOfMolesInPhase() * 1.0 / thermoSystem.getPhases()[i].getMolarMass() * 1000), buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/kg*K]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Viscosity";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] = nf.format((thermoSystem.getPhases()[i].getPhysicalProperties().getViscosity()), buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[kg/m*sec]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Conductivity";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] = nf.format(thermoSystem.getPhases()[i].getPhysicalProperties().getConductivity(), buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[W/m*K]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] = Double.toString(thermoSystem.getPhases()[i].getPressure());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] = Double.toString(thermoSystem.getPhases()[i].getTemperature());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
            Double.toString(thermoSystem.getPhases()[i].getTemperature());

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Stream";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
        }

        JTable Jtab = new JTable(table, names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.pack();
        dialog.setVisible(true);
    }

    public String getName() {
        return name;
    }

    public void runTransient() {
    }

}
