package neqsim.processSimulation.util.example;

import neqsim.processsimulation.processequipment.absorber.WaterStripperColumn;
import neqsim.processsimulation.processequipment.stream.Stream;

/**
 * <p>
 * TEGstripperProcess class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGstripperProcess {
    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface feedGas =
                new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
        feedGas.addComponent("nitrogen", 1.03);
        feedGas.addComponent("CO2", 1.42);
        feedGas.addComponent("methane", 83.88);
        feedGas.addComponent("ethane", 8.07);
        feedGas.addComponent("propane", 3.54);
        feedGas.addComponent("i-butane", 0.54);
        feedGas.addComponent("n-butane", 0.84);
        feedGas.addComponent("i-pentane", 0.21);
        feedGas.addComponent("n-pentane", 0.19);
        feedGas.addComponent("n-hexane", 0.28);
        feedGas.addComponent("water", 0.0);
        feedGas.addComponent("TEG", 0);
        feedGas.createDatabase(true);
        feedGas.setMixingRule(10);
        feedGas.setMultiPhaseCheck(true);

        Stream dryFeedGas = new Stream("input stream", feedGas);
        dryFeedGas.setFlowRate(10.0, "kg/hr");
        dryFeedGas.setTemperature(200.5, "C");
        dryFeedGas.setPressure(1.21, "bara");

        neqsim.thermo.system.SystemInterface feedTEG = feedGas.clone();
        feedTEG.setMolarComposition(
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.09, 0.91});

        Stream TEGFeed = new Stream("feed TEG", feedTEG);
        TEGFeed.setFlowRate(6.1 * 1100.0, "kg/hr");
        TEGFeed.setTemperature(206.3, "C");
        TEGFeed.setPressure(1.21, "bara");

        WaterStripperColumn stripper = new WaterStripperColumn("SimpleTEGstripper");
        stripper.addGasInStream(dryFeedGas);
        stripper.addSolventInStream(TEGFeed);
        stripper.setNumberOfStages(3);
        stripper.setStageEfficiency(0.5);

        neqsim.processsimulation.processsystem.ProcessSystem operations =
                new neqsim.processsimulation.processsystem.ProcessSystem();
        operations.add(dryFeedGas);
        operations.add(TEGFeed);
        operations.add(stripper);

        operations.run();

        double wtInletTEG = TEGFeed.getFluid().getPhase("aqueous").getWtFrac("TEG");
        double wtInletTEG2 =
                stripper.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG");
        double a = 1;

        stripper.displayResult();
    }
}
