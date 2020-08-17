/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */
package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.MathLib.generalMath.GeneralMath;
import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.processSimulation.mechanicalDesign.pipeline.PipelineMechanicalDeisgn;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class AdiabaticPipe extends Pipeline implements ProcessEquipmentInterface, PipeLineInterface {

    private static final long serialVersionUID = 1000;
    
    double inletPressure = 0;
    boolean setTemperature = false, setPressureOut = false;
    protected double temperatureOut = 270, pressureOut = 0.0;
    double length = 100.0;
    double insideDiameter = 0.1;
    double velocity = 1.0;
    double pipeWallRoughness = 1e-5;
    private double inletElevation = 0;
    private double outletElevation = 0;
    double dH = 0.0;
    String flowPattern = "unknown";
    String pipeSpecification = "AP02";

    /**
     * Creates new Heater
     */
    public AdiabaticPipe() {
         mechanicalDesign = new PipelineMechanicalDeisgn(this);
    }
    
    public void setPipeSpecification(double nominalDiameter, String pipeSec) {
        pipeSpecification = pipeSec;
        insideDiameter = nominalDiameter / 1000.0;
    }
    
    public AdiabaticPipe(Stream inStream) {
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Stream getOutStream() {
        return outStream;
    }
    
    public void setOutTemperature(double temperature) {
        setTemperature = true;
        this.temperatureOut = temperature;
    }
    
    public void setOutPressure(double pressure) {
        setPressureOut = true;
        this.pressureOut = pressure;
    }
    
    public double calcWallFrictionFactor(double reynoldsNumber) {
        double relativeRoughnes = getPipeWallRoughness() / insideDiameter;
        if (Math.abs(reynoldsNumber) < 2000) {
            flowPattern = "laminar";
            return 64.0 / reynoldsNumber;
        } else {
            flowPattern = "turbulent";
            return Math.pow((1.0 / (-1.8 * GeneralMath.log10(6.9 / reynoldsNumber + Math.pow(relativeRoughnes / 3.7, 1.11)))), 2.0);
        }
    }
    
    public double calcPressureOut() {
        double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
        velocity = system.getPhase(0).getTotalVolume() / area / 1.0e5;
        double reynoldsNumber = velocity * insideDiameter / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
        double frictionFactor = calcWallFrictionFactor(reynoldsNumber);
        double dp = Math.pow(4.0 * system.getPhase(0).getNumberOfMolesInPhase() * system.getPhase(0).getMolarMass() / neqsim.thermo.ThermodynamicConstantsInterface.pi, 2.0) * frictionFactor * length * system.getPhase(0).getZ() * neqsim.thermo.ThermodynamicConstantsInterface.R / system.getPhase(0).getMolarMass() * system.getTemperature() / Math.pow(insideDiameter, 5.0);
        //\\System.out.println("friction fact" + frictionFactor + " velocity " + velocity + " reynolds number " + reynoldsNumber);
        System.out.println("dp gravity " + system.getDensity("kg/m3")*neqsim.thermo.ThermodynamicConstantsInterface.gravity*(inletElevation-outletElevation)/1.0e5);
        double dp_gravity=system.getDensity("kg/m3")*neqsim.thermo.ThermodynamicConstantsInterface.gravity*(inletElevation-outletElevation);
        return Math.sqrt(Math.pow(inletPressure * 1e5, 2.0) - dp )/ 1.0e5 + dp_gravity/1.0e5 ;
    }
    
    public double calcFlow() {
        double averagePressue = (inletPressure + pressureOut) / 2.0;
        system.setPressure(averagePressue);
        system.init(1);
        system.initPhysicalProperties();
        
        double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
        double presdrop2 = Math.pow(inletPressure * 1e2, 2.0) - Math.pow(pressureOut * 1e2, 2.0);
        double gasGravity = system.getMolarMass() / 0.028;
        double oldReynold = 0;
        double reynoldsNumber = -1000.0;
        double flow = 0;
        do {
            oldReynold = reynoldsNumber;
            velocity = system.getPhase(0).getTotalVolume() / area / 1.0e5;
            reynoldsNumber = velocity * insideDiameter / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
            double frictionFactor = calcWallFrictionFactor(reynoldsNumber)*4.0;
            double temp = Math.sqrt(presdrop2 * Math.pow(insideDiameter * 1000.0, 5.0) / (gasGravity * system.getPhase(0).getZ() * system.getTemperature() * frictionFactor * length / 1000.0));
            flow = 1.1494e-3 * 288.15 / (system.getPressure()*100) * temp;
            system.setTotalFlowRate(flow/1e6, "MSm^3/day");
            system.init(1);
            System.out.println("flow " + flow + " velocity "  + velocity);
        } while (Math.abs(reynoldsNumber - oldReynold) / reynoldsNumber > 1e-3);
        return flow;
    }
    
    public void run() {
        system = (SystemInterface) inStream.getThermoSystem().clone();
        inletPressure = system.getPressure();
        //  system.setMultiPhaseCheck(true);
        if (setTemperature) {
            system.setTemperature(this.temperatureOut);
        }
        
        double oldPressure = 0.0;
        int iter = 0;
        if (!setPressureOut) {
            do {
                iter++;
                oldPressure = system.getPressure();
                system.init(3);
                system.initPhysicalProperties();
                system.setPressure(calcPressureOut());
            } while (Math.abs(system.getPressure() - oldPressure) > 1e-2 && iter < 25);
        } else {
            
            calcFlow();
            system.setPressure(pressureOut);
            system.init(3);
        }
        ThermodynamicOperations testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
        // system.setMultiPhaseCheck(false);
        outStream.setThermoSystem(system);
    }
    
    public void displayResult() {
        system.display();
    }
    
    public String getName() {
        return name;
    }
    
    public void runTransient() {
    }
    
    public FlowSystemInterface getPipe() {
        return null;
    }
    
    public void setInitialFlowPattern(String flowPattern) {
    }

    /**
     * @return the length
     */
    public double getLength() {
        return length;
    }

    /**
     * @param length the length to set
     */
    public void setLength(double length) {
        this.length = length;
    }

    /**
     * @return the diameter
     */
    public double getDiameter() {
        return insideDiameter;
    }

    /**
     * @param diameter the diameter to set
     */
    public void setDiameter(double diameter) {
        this.insideDiameter = diameter;
    }

    /**
     * @return the pipeWallRoughness
     */
    public double getPipeWallRoughness() {
        return pipeWallRoughness;
    }

    /**
     * @param pipeWallRoughness the pipeWallRoughness to set
     */
    public void setPipeWallRoughness(double pipeWallRoughness) {
        this.pipeWallRoughness = pipeWallRoughness;
    }

    /**
     * @return the inletElevation
     */
    public double getInletElevation() {
        return inletElevation;
    }

    /**
     * @param inletElevation the inletElevation to set
     */
    public void setInletElevation(double inletElevation) {
        this.inletElevation = inletElevation;
    }

    /**
     * @return the outletElevation
     */
    public double getOutletElevation() {
        return outletElevation;
    }

    /**
     * @param outletElevation the outletElevation to set
     */
    public void setOutletElevation(double outletElevation) {
        this.outletElevation = outletElevation;
    }
    
    public static void main(String[] name) {
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 5.0), 220.00);
        testSystem.addComponent("methane", 24.0, "MSm^3/day");
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        
        Stream stream_1 = new Stream("Stream1", testSystem);
        
        AdiabaticPipe pipe = new AdiabaticPipe(stream_1);
        pipe.setLength(700000.0);
        pipe.setDiameter(0.7112);
        pipe.setPipeWallRoughness(5e-6);
        pipe.setOutPressure(112.0);
        
        
        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(pipe);
        operations.run();
        pipe.displayResult();
    }
}
