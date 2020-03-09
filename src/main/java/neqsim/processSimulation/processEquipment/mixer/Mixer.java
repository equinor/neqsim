/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */
package neqsim.processSimulation.processEquipment.mixer;

import java.awt.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class Mixer extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface, MixerInterface {

    private static final long serialVersionUID = 1000;

    protected ArrayList streams = new ArrayList(0);
    protected int numberOfInputStreams = 0;
    protected Stream mixedStream;
    public ThermodynamicOperations testOps = null;

    /** Creates new staticMixer */
    public Mixer() {
    }

    public Mixer(String name) {
       super(name);
    }

    public SystemInterface getThermoSystem() {
        return mixedStream.getThermoSystem();
    }

    public void replaceStream(int i, StreamInterface newStream) {
        streams.set(i, newStream);
    }

    public void addStream(StreamInterface newStream) {
        streams.add(newStream);

        try{
        if (numberOfInputStreams == 0) {
            mixedStream = (Stream) ((StreamInterface) streams.get(0)).clone(); // cloning the first stream
//            mixedStream.getThermoSystem().setNumberOfPhases(2);
//            mixedStream.getThermoSystem().reInitPhaseType();
//            mixedStream.getThermoSystem().init(0);
//            mixedStream.getThermoSystem().init(3);
        }
        }catch(Exception e){
            e.printStackTrace();
        }

        numberOfInputStreams++;
    }

    public StreamInterface getStream(int i) {
        return (StreamInterface) streams.get(i);
    }

    public void mixStream() {
        int index = 0;
        String compName = new String();
        double lowestPressure = mixedStream.getThermoSystem().getPhase(0).getPressure();

        for (int k = 1; k < streams.size(); k++) {
            if (((StreamInterface) streams.get(k)).getThermoSystem().getPhase(0).getPressure() < lowestPressure){
                lowestPressure = ((StreamInterface) streams.get(k)).getThermoSystem().getPhase(0).getPressure();
                mixedStream.getThermoSystem().getPhase(0).setPressure(lowestPressure);
            }
            for (int i = 0; i < ((StreamInterface) streams.get(k)).getThermoSystem().getPhase(0).getNumberOfComponents(); i++) {

                boolean gotComponent = false;
                String componentName = ((StreamInterface) streams.get(k)).getThermoSystem().getPhase(0).getComponent(i).getName();
                //System.out.println("adding: " + componentName);
                int numberOfPhases = ((StreamInterface) streams.get(k)).getThermoSystem().getNumberOfPhases();

                double moles = ((StreamInterface) streams.get(k)).getThermoSystem().getPhase(0).getComponent(i).getNumberOfmoles();
                //System.out.println("moles: " + moles + "  " + mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents());
                for (int p = 0; p < mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents(); p++) {
                    if (mixedStream.getThermoSystem().getPhase(0).getComponent(p).getName().equals(componentName)) {
                        gotComponent = true;
                        index = ((StreamInterface) streams.get(0)).getThermoSystem().getPhase(0).getComponent(p).getComponentNumber();
                        compName = ((StreamInterface) streams.get(0)).getThermoSystem().getPhase(0).getComponent(p).getComponentName();

                    }
                }

                if (gotComponent) {
                    //  System.out.println("adding moles starting....");
                    mixedStream.getThermoSystem().addComponent(index, moles);
                //mixedStream.getThermoSystem().init_x_y();
                //System.out.println("adding moles finished");
                } else {
                    System.out.println("ikke gaa hit");
                    mixedStream.getThermoSystem().addComponent(compName, moles);
                }
            }
        }
//        mixedStream.getThermoSystem().init_x_y();
//        mixedStream.getThermoSystem().initBeta();
//        mixedStream.getThermoSystem().init(2);
    }

    public double guessTemperature() {
        double gtemp = 0;
        for (int k = 0; k < streams.size(); k++) {
            gtemp += ((StreamInterface) streams.get(k)).getThermoSystem().getTemperature() * ((StreamInterface) streams.get(k)).getThermoSystem().getNumberOfMoles() / mixedStream.getThermoSystem().getNumberOfMoles();

        }
        return gtemp;
    }

    public double calcMixStreamEnthalpy() {
        double enthalpy = 0;
        for (int k = 0; k < streams.size(); k++) {
            ((StreamInterface) streams.get(k)).getThermoSystem().init(3);
            enthalpy += ((StreamInterface) streams.get(k)).getThermoSystem().getEnthalpy();
        //System.out.println("total enthalpy k : " + ((SystemInterface) ((Stream) streams.get(k)).getThermoSystem()).getEnthalpy());
        }
        // System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    public Stream getOutStream() {
        return mixedStream;
    }

    public void runTransient() {
        run();
    }

    public void run() {
        double enthalpy = 0.0;

//        ((Stream) streams.get(0)).getThermoSystem().display();

        SystemInterface thermoSystem2 = (SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem().clone();
        
        //System.out.println("total number of moles " + thermoSystem2.getTotalNumberOfMoles());
        mixedStream.setThermoSystem(thermoSystem2);
        //thermoSystem2.display();
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
        if (streams.size() > 0) {
            mixedStream.getThermoSystem().setNumberOfPhases(2);
            mixedStream.getThermoSystem().reInitPhaseType();
            mixedStream.getThermoSystem().init(0);

            mixStream();

            enthalpy = calcMixStreamEnthalpy();
           //  System.out.println("temp guess " + guessTemperature());
            mixedStream.getThermoSystem().setTemperature(guessTemperature());
            testOps.PHflash(enthalpy, 0);
           //System.out.println("filan temp  " + mixedStream.getTemperature());
        } else {
            testOps.TPflash();
        }
    //System.out.println("enthalpy: " + mixedStream.getThermoSystem().getEnthalpy());
    //        System.out.println("enthalpy: " + enthalpy);
    // System.out.println("temperature: " + mixedStream.getThermoSystem().getTemperature());


    //    System.out.println("beta " + mixedStream.getThermoSystem().getBeta());
    // outStream.setThermoSystem(mixedStream.getThermoSystem());
    }

    public void displayResult() {
        SystemInterface thermoSystem = mixedStream.getThermoSystem();
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");


        JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new FlowLayout());

        thermoSystem.initPhysicalProperties();
        String[][] table = new String[50][5];
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
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] = nf.format((thermoSystem.getPhases()[i].getCp() / (thermoSystem.getPhases()[i].getNumberOfMolesInPhase() * thermoSystem.getPhases()[i].getMolarMass() * 1000)), buf, test).toString();
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

    public void setPressure(double pres) {
        for (int k = 0; k < streams.size(); k++) {
            ((StreamInterface) streams.get(k)).getThermoSystem().setPressure(pres);
        }
        mixedStream.getThermoSystem().setPressure(pres);
    }

    public void setTemperature(double temp) {
        for (int k = 0; k < streams.size(); k++) {
            ((StreamInterface) streams.get(k)).getThermoSystem().setTemperature(temp);
        }
        mixedStream.getThermoSystem().setTemperature(temp);
    }
}
