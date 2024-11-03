package neqsim.processsimulation.util.example;

import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * <p>
 * propaneCoolingCycle class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class propaneCoolingCycle {
    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemPrEos((273.15 + 30.0), 10.700);
        testSystem.addComponent("propane", 4759.0, "kg/hr");
        testSystem.createDatabase(true);

        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);
        stream_1.setSpecification("bubT");

        ThrottlingValve JTvalve = new ThrottlingValve("JTvalve", stream_1);
        JTvalve.setOutletPressure(1.11325);

        Cooler cooler = new Cooler("cooler", JTvalve.getOutletStream());
        // cooler.setPressureDrop(0.35);
        cooler.setSpecification("out stream");

        Stream stream_2 = new Stream("stream_2", cooler.getOutletStream());
        stream_2.setSpecification("dewP");
        // stream_2.setTemperature(-40.0, "C");

        cooler.setOutletStream(stream_2);

        SetPoint setLPpressure = new SetPoint("set", JTvalve, "pressure", stream_2);

        Compressor compressor1 = new Compressor("compressor1", stream_2);
        // compressor1.setIsentropicEfficiency(0.75);
        // compressor1.setPower(180000);
        compressor1.setSpecification("out stream");
        compressor1.setOutletPressure(stream_1.getPressure());

        Heater heater = new Heater("heater", compressor1.getOutletStream());
        heater.setPressureDrop(0.07);
        heater.setSpecification("out stream");
        heater.setOutletStream(stream_1);

        neqsim.process.processmodel.ProcessSystem operations =
                new neqsim.process.processmodel.ProcessSystem();
        operations.add(stream_1);

        operations.add(JTvalve);
        operations.add(cooler);

        operations.add(stream_2);
        operations.add(setLPpressure);

        operations.add(compressor1);
        operations.add(heater);

        operations.run();
        operations.run();
        stream_1.displayResult();
        JTvalve.displayResult();
        // compressor1.displayResult();
        // stream_2.displayResult();
        // operations.displayResult();
        cooler.run();

        JTvalve.getOutletStream().displayResult();
        stream_2.displayResult();

        System.out.println("compressor work" + compressor1.getEnergy() / 1.0e3 + " kW "
                + " compressor temperature " + compressor1.getOutTemperature());
        // System.out.println("compressor isentropic ef " +
        // compressor1.getIsentropicEfficiency());
        System.out.println("cooler duty " + cooler.getEnergyInput() / 1.0e3 + " kW");
        System.out.println("heater duty " + heater.getEnergyInput() / 1.0e3 + " kW");
    }
}
