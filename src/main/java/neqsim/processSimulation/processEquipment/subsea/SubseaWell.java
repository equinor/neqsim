package neqsim.processSimulation.processEquipment.subsea;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.processSimulation.processEquipment.reservoir.SimpleReservoir;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Adjuster;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;

/**
 * <p>
 * SubseaWell class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SubseaWell extends TwoPortEquipment {
    private static final long serialVersionUID = 1000;

    public double height = 1000.0, length = 1200.0;
    AdiabaticTwoPhasePipe pipeline;

    /**
     * <p>
     * Constructor for SubseaWell.
     * </p>
     *
     * @param instream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    @Deprecated
    public SubseaWell(StreamInterface instream) {
        super("SubseaWell");
        this.inStream = instream;
        setOutStream(instream.clone());
        pipeline = new AdiabaticTwoPhasePipe("pipeline", instream);
    }

    /**
     * <p>
     * Getter for the field <code>pipeline</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.pipeline.AdiabaticTwoPhasePipe}
     *         object
     */
    public AdiabaticTwoPhasePipe getPipeline() {
        return pipeline;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        pipeline.run();
        getOutStream().setFluid(pipeline.getOutStream().getFluid());

        /*
         * System.out.println("stary P " ); SystemInterface fluidIn = (inStream.getFluid()).clone();
         * fluidIn.initProperties();
         * 
         * double density = fluidIn.getDensity("kg/m3");
         * 
         * double deltaP =
         * density*height*neqsim.thermo.ThermodynamicConstantsInterface.gravity/1.0e5;
         * 
         * System.out.println("density " +density + " delta P " + deltaP);
         * 
         * fluidIn.setPressure(fluidIn.getPressure("bara")-deltaP);
         * 
         * ThermodynamicOperations ops = new ThermodynamicOperations(fluidIn); ops.TPflash();
         * 
         * getOutStream().setFluid(fluidIn);
         */
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 100.0), 250.00);
        testSystem.addComponent("nitrogen", 0.100);
        testSystem.addComponent("methane", 70.00);
        testSystem.addComponent("ethane", 1.0);
        testSystem.addComponent("propane", 1.0);
        testSystem.addComponent("i-butane", 1.0);
        testSystem.addComponent("n-butane", 1.0);
        testSystem.addComponent("n-hexane", 0.1);
        testSystem.addComponent("n-heptane", 0.1);
        testSystem.addComponent("n-nonane", 1.0);
        testSystem.addComponent("nC10", 1.0);
        testSystem.addComponent("nC12", 3.0);
        testSystem.addComponent("nC15", 13.0);
        testSystem.addComponent("nC20", 13.0);
        testSystem.addComponent("water", 11.0);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);

        SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
        reservoirOps.setReservoirFluid(testSystem, 5.0 * 1e7, 552.0 * 1e6, 10.0e6);
        StreamInterface producedOilStream = reservoirOps.addOilProducer("oilproducer_1");
        producedOilStream.setFlowRate(3500.0 * 24.0 * 600.0, "kg/day");

        reservoirOps.run();

        System.out.println("water volume"
                + reservoirOps.getReservoirFluid().getPhase("aqueous").getVolume("m3") / 1.0e6);
        System.out.println(
                "oil production  total" + reservoirOps.getOilProductionTotal("Sm3") + " Sm3");
        System.out.println(
                "total produced  " + reservoirOps.getProductionTotal("MSm3 oe") + " MSm3 oe");

        SubseaWell well1 = new SubseaWell(reservoirOps.getOilProducer("oilproducer_1").getStream());
        well1.getPipeline().setDiameter(0.3);
        well1.getPipeline().setLength(5500.0);
        well1.getPipeline().setInletElevation(-1000.0);
        well1.getPipeline().setOutletElevation(-100.0);
        ThrottlingValve subseaChoke = new ThrottlingValve("subseaChoke", well1.getOutStream());
        subseaChoke.setOutletPressure(90.0);
        subseaChoke.setAcceptNegativeDP(false);
        SimpleFlowLine flowLine = new SimpleFlowLine("flowLine", subseaChoke.getOutletStream());
        flowLine.getPipeline().setDiameter(0.4);
        flowLine.getPipeline().setLength(2000.0);
        flowLine.getPipeline().setInletElevation(-100.0);
        // flowLine.set
        ThrottlingValve topsideChoke = new ThrottlingValve("topsideChoke", flowLine.getOutStream());
        topsideChoke.setOutletPressure(50.0, "bara");
        topsideChoke.setAcceptNegativeDP(false);

        Adjuster adjust = new Adjuster("adjust");
        adjust.setActivateWhenLess(true);
        adjust.setTargetVariable(flowLine.getOutStream(), "pressure", 70.0, "bara");
        adjust.setAdjustedVariable(producedOilStream, "flow rate");

        ProcessSystem ops = new ProcessSystem();
        ops.add(well1);
        ops.add(subseaChoke);
        ops.add(flowLine);
        ops.add(topsideChoke);
        ops.add(adjust);

        ArrayList<double[]> res = new ArrayList<double[]>();
        // for(int i=0;i<152;i++) {
        // do {
        reservoirOps.runTransient(60 * 60 * 24 * 1);
        ops.run();
        res.add(new double[] {reservoirOps.getTime(),
                producedOilStream.getFluid().getFlowRate("kg/hr"),
                reservoirOps.getOilProductionTotal("MSm3 oe")});
        System.out.println("subsea choke DP " + subseaChoke.getDeltaPressure("bara"));
        System.out.println("topside  choke DP " + topsideChoke.getDeltaPressure("bara"));
        System.out.println("oil production " + producedOilStream.getFluid().getFlowRate("kg/hr"));
        // }
        // while(producedOilStream.getFluid().getFlowRate("kg/hr")>1.0e5);

        ProcessSystem GasOilProcess = ProcessSystem.open("c:/temp/offshorePro.neqsim");
        ((StreamInterface) GasOilProcess.getUnit("well stream"))
                .setThermoSystem(topsideChoke.getOutletStream().getFluid());
        ((StreamInterface) GasOilProcess.getUnit("well stream")).setPressure(70.0, "bara");
        ((StreamInterface) GasOilProcess.getUnit("well stream")).setTemperature(65.0, "C");
        GasOilProcess.run();

        System.out.println("power " + GasOilProcess.getPower("MW"));
        for (int i = 0; i < res.size(); i++) {
            System.out.println("time " + res.get(i)[0] + " oil production " + res.get(i)[1]
                    + " total production MSm3 oe " + res.get(i)[2]);
        }
    }

    /**
     * <p>
     * Getter for the field <code>outStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    @Deprecated
    public StreamInterface getOutStream() {
        return outStream;
    }

    /**
     * <p>
     * Setter for the field <code>outStream</code>.
     * </p>
     *
     * @param outStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    @Deprecated
    public void setOutStream(StreamInterface outStream) {
        this.outStream = outStream;
    }
}
