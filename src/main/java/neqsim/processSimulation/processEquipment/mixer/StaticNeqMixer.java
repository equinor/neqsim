/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */

package neqsim.processSimulation.processEquipment.mixer;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class StaticNeqMixer extends StaticMixer implements ProcessEquipmentInterface, MixerInterface {

    private static final long serialVersionUID = 1000;

    /** Creates new staticMixer */
    public StaticNeqMixer() {
    }

    public StaticNeqMixer(String name) {
        super(name);
    }

    public void mixStream() {
        int index = 0;
        String compName = new String();

        for (int k = 1; k < streams.size(); k++) {

            for (int i = 0; i < ((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getPhases()[0]
                    .getNumberOfComponents(); i++) {

                boolean gotComponent = false;
                String componentName = ((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem())
                        .getPhases()[0].getComponents()[i].getName();
                System.out.println("adding: " + componentName);
                int numberOfPhases = ((StreamInterface) streams.get(k)).getThermoSystem().getNumberOfPhases();
                double[] moles = new double[numberOfPhases];
                // her maa man egentlig sjekke at phase typen er den samme !!! antar at begge er
                // to fase elle gass - tofase
                for (int p = 0; p < numberOfPhases; p++) {
                    moles[p] = ((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getPhases()[p]
                            .getComponents()[i].getNumberOfMolesInPhase();
                }
                for (int p = 0; p < mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents(); p++) {
                    if (mixedStream.getThermoSystem().getPhases()[0].getComponents()[p].getName()
                            .equals(componentName)) {
                        gotComponent = true;
                        index = ((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem()).getPhases()[0]
                                .getComponents()[p].getComponentNumber();
                        compName = ((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem())
                                .getPhases()[0].getComponents()[p].getComponentName();

                    }
                }

                if (gotComponent) {
                    System.out.println("adding moles starting....");
                    for (int p = 0; p < numberOfPhases; p++) {
                        mixedStream.getThermoSystem().addComponent(index, moles[p], p);
                    }
                    System.out.println("adding moles finished");
                } else {
                    System.out.println("ikke gaa hit");
                    for (int p = 0; p < numberOfPhases; p++) {
                        mixedStream.getThermoSystem().addComponent(compName, moles[p], p);
                    }
                }
            }
        }
        mixedStream.getThermoSystem().init_x_y();
        mixedStream.getThermoSystem().initBeta();
        mixedStream.getThermoSystem().init(2);

    }

    public void run() {
        double enthalpy = 0.0;
        for (int k = 0; k < streams.size(); k++) {
            ((StreamInterface) streams.get(k)).getThermoSystem().init(3);
            enthalpy += ((StreamInterface) streams.get(k)).getThermoSystem().getEnthalpy();
        }

        mixedStream.setThermoSystem(((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem().clone()));
        mixedStream.getThermoSystem().setNumberOfPhases(2);
        mixedStream.getThermoSystem().reInitPhaseType();
        mixStream();

        SystemInterface syst = (SystemInterface) mixedStream.getThermoSystem().clone();
        syst.setTemperature(((StreamInterface) streams.get(0)).getThermoSystem().getTemperature());
        syst.setPressure(((StreamInterface) streams.get(0)).getThermoSystem().getPressure());
        ThermodynamicOperations testOps = new ThermodynamicOperations(syst);
        testOps.PHflash(enthalpy, 0);
        System.out.println("temp " + syst.getTemperature());
        mixedStream.getThermoSystem().setTemperature(syst.getTemperature());
        mixedStream.getThermoSystem().init(3);
        // double enthalpy = calcMixStreamEnthalpy();
        // System.out.println("temp guess " + guessTemperature());
        // mixedStream.getThermoSystem().setTemperature(guessTemperature());
        // testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
        // testOps.TPflash();
        // testOps.PHflash(enthalpy, 0);
        // System.out.println("enthalpy: " +
        // mixedStream.getThermoSystem().getEnthalpy());
        // System.out.println("enthalpy: " + enthalpy);
        // System.out.println("temperature: " +
        // mixedStream.getThermoSystem().getTemperature());
    }

    public String getName() {
        return name;
    }

}
