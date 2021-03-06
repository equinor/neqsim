/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */
package neqsim.processSimulation.processEquipment.util;

import java.util.*;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.mixer.MixerInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Recycle extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface, MixerInterface {

    private static final long serialVersionUID = 1000;

    protected ArrayList<StreamInterface> streams = new ArrayList<StreamInterface>(0);
    private ArrayList<String> downstreamProperty = new ArrayList<String>(0);
    protected int numberOfInputStreams = 0;
    protected StreamInterface mixedStream;
    StreamInterface lastIterationStream = null;
    private StreamInterface outletStream = null;
    private double tolerance = 1e-4;
    private double error = 1e6;
    private int priority = 100;
    boolean firstTime = true;
    int iterations = 0;
    int maxIterations = 10;

    /** Creates new staticMixer */
    public Recycle() {
    }

    public Recycle(String name) {
        super(name);
    }

    @Override
	public SystemInterface getThermoSystem() {
        return mixedStream.getThermoSystem();
    }

    public void setDownstreamProperty(String property) {
        downstreamProperty.add(property);
    }

    @Override
	public void replaceStream(int i, StreamInterface newStream) {
        streams.set(i, newStream);
    }

    @Override
	public void addStream(StreamInterface newStream) {
        streams.add(newStream);

        if (numberOfInputStreams == 0) {
            mixedStream = (StreamInterface) streams.get(0).clone();
//            mixedStream.getThermoSystem().setNumberOfPhases(2);
//            mixedStream.getThermoSystem().reInitPhaseType();
//            mixedStream.getThermoSystem().init(0);
//            mixedStream.getThermoSystem().init(3);
        }
        mixedStream.setEmptyThermoSystem(streams.get(0).getThermoSystem());
        numberOfInputStreams++;
        lastIterationStream = (StreamInterface) mixedStream.clone();
    }

    public StreamInterface getStream(int i) {
        return streams.get(i);
    }

    public void mixStream() {
        int index = 0;
        String compName = new String();

        for (int k = 1; k < streams.size(); k++) {

            for (int i = 0; i < streams.get(k).getThermoSystem().getPhase(0)
                    .getNumberOfComponents(); i++) {

                boolean gotComponent = false;
                String componentName = streams.get(k).getThermoSystem().getPhase(0).getComponent(i)
                        .getName();
                // System.out.println("adding: " + componentName);
                int numberOfPhases = streams.get(k).getThermoSystem().getNumberOfPhases();

                double moles = streams.get(k).getThermoSystem().getPhase(0).getComponent(i)
                        .getNumberOfmoles();
                // System.out.println("moles: " + moles + " " +
                // mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents());
                for (int p = 0; p < mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents(); p++) {
                    if (mixedStream.getThermoSystem().getPhase(0).getComponent(p).getName().equals(componentName)) {
                        gotComponent = true;
                        index = streams.get(0).getThermoSystem().getPhase(0).getComponent(p)
                                .getComponentNumber();
                        compName = streams.get(0).getThermoSystem().getPhase(0).getComponent(p)
                                .getComponentName();

                    }
                }

                if (gotComponent) {
                    // System.out.println("adding moles starting....");
                    mixedStream.getThermoSystem().addComponent(index, moles);
                    // mixedStream.getThermoSystem().init_x_y();
                    // System.out.println("adding moles finished");
                } else {
                    System.out.println("ikke gaa hit");
                    mixedStream.getThermoSystem().addComponent(index, moles);
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
            gtemp += streams.get(k).getThermoSystem().getTemperature()
                    * streams.get(k).getThermoSystem().getNumberOfMoles()
                    / mixedStream.getThermoSystem().getNumberOfMoles();

        }
        return gtemp;
    }

    public double calcMixStreamEnthalpy() {
        double enthalpy = 0;
        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().init(3);
            enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
            // System.out.println("total enthalpy k : " + ((SystemInterface) ((Stream)
            // streams.get(k)).getThermoSystem()).getEnthalpy());
        }
        // System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }

    @Override
	public Stream getOutStream() {
        return (Stream) mixedStream;
    }

    @Override
	public void runTransient() {
        run();
    }

    public void initiateDownstreamProperties(Stream outstream) {
        lastIterationStream = (Stream) outstream.clone();
    }

    public void setDownstreamProperties() {
        if (downstreamProperty.size() > 0) {
            for (int i = 0; i < downstreamProperty.size(); i++) {
                if (downstreamProperty.get(i).equals("flow rate"))
                    mixedStream.setFlowRate(outletStream.getFlowRate("kg/hr"), "kg/hr");
            }
        }
    }

    @Override
	public void run() {
        iterations++;
        /*
         * if(firstTime || iterations>maxIterations) { firstTime=false; return;
         * 
         * }
         */
        double enthalpy = 0.0;
        // System.out.println("flow rate old in recycle " +
        // outletStream.getFlowRate("kg/hr"));
//        ((Stream) streams.get(0)).getThermoSystem().display();
        SystemInterface thermoSystem2 = (SystemInterface) streams.get(0).getThermoSystem().clone();
        // System.out.println("total number of moles " +
        // thermoSystem2.getTotalNumberOfMoles());
        mixedStream.setThermoSystem(thermoSystem2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
        if (streams.size() > 1) {
            mixedStream.getThermoSystem().setNumberOfPhases(2);
            mixedStream.getThermoSystem().reInitPhaseType();
            mixedStream.getThermoSystem().init(0);

            mixStream();

            setDownstreamProperties();
            try {
                enthalpy = calcMixStreamEnthalpy();
            } catch (Exception e) {
                String error = e.getMessage();
                return;
            }
            // System.out.println("temp guess " + guessTemperature());
            mixedStream.getThermoSystem().setTemperature(guessTemperature());
            testOps.PHflash(enthalpy, 0);
            // System.out.println("filan temp " + mixedStream.getTemperature());
        } else {
            setDownstreamProperties();
            testOps.TPflash();
        }
        setError(massBalanceCheck());
        System.out.println(name + " recycle error: " + getError());
        lastIterationStream = (Stream) mixedStream.clone();
        outletStream.setThermoSystem(mixedStream.getThermoSystem());
        // System.out.println("flow rate new in recycle " +
        // outletStream.getFlowRate("kg/hr"));

        // System.out.println("enthalpy: " +
        // mixedStream.getThermoSystem().getEnthalpy());
        // System.out.println("enthalpy: " + enthalpy);
        // System.out.println("temperature: " +
        // mixedStream.getThermoSystem().getTemperature());

        // System.out.println("beta " + mixedStream.getThermoSystem().getBeta());
        // outStream.setThermoSystem(mixedStream.getThermoSystem());
    }

    public double massBalanceCheck() {
        double error = 0.0;
        // System.out.println("flow rate new " +
        // mixedStream.getThermoSystem().getFlowRate("kg/hr"));
        // System.out.println("temperature " +
        // mixedStream.getThermoSystem().getTemperature("C"));
        // System.out.println("pressure " +
        // mixedStream.getThermoSystem().getPressure("bara"));
        if (lastIterationStream.getFluid().getNumberOfComponents() != mixedStream.getFluid().getNumberOfComponents()) {
            return 10.0;
        }
        for (int i = 0; i < mixedStream.getThermoSystem().getPhase(0).getNumberOfComponents(); i++) {
            // System.out.println("x last " +
            // lastIterationStream.getThermoSystem().getPhase(0).getComponent(i).getx());
            // System.out.println("x new " +
            // mixedStream.getThermoSystem().getPhase(0).getComponent(i).getx());
            error += Math.abs(mixedStream.getThermoSystem().getPhase(0).getComponent(i).getx()
                    - lastIterationStream.getThermoSystem().getPhase(0).getComponent(i).getx());
        }
        return Math.abs(error);
    }

    @Override
	public void displayResult() {

    }

    @Override
	public void setPressure(double pres) {
        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().setPressure(pres);
        }
        mixedStream.getThermoSystem().setPressure(pres);
    }

    public void setTemperature(double temp) {
        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().setTemperature(temp);
        }
        mixedStream.getThermoSystem().setTemperature(temp);
    }

    /**
     * @return the tolerance
     */
    public double getTolerance() {
        return tolerance;
    }

    /**
     * @param tolerance the tolerance to set
     */
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * @return the error
     */
    public double getError() {
        return error;
    }

    /**
     * @param error the error to set
     */
    public void setError(double error) {
        this.error = error;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
	public boolean solved() {
        if (error < tolerance)
            return true;
        else
            return false;
    }

    public ArrayList<String> getDownstreamProperty() {
        return downstreamProperty;
    }

    public void setDownstreamProperty(ArrayList<String> upstreamProperty) {
        this.downstreamProperty = upstreamProperty;
    }

    public StreamInterface getOutletStream() {
        return outletStream;
    }

    public void setOutletStream(StreamInterface outletStream) {
        this.outletStream = outletStream;
        lastIterationStream = (StreamInterface) this.outletStream.clone();
    }
}
