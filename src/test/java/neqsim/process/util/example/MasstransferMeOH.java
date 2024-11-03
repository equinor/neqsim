package neqsim.process.util.example;

import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.mixer.StaticPhaseMixer;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * MasstransferMeOH class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class MasstransferMeOH {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    // Create the input fluid to the TEG process and saturate it with water at
    // scrubber conditions
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);

    feedGas.addComponent("methane", 83.88);
    feedGas.addComponent("water", 0.0);
    feedGas.addComponent("methanol", 0);
    feedGas.createDatabase(true);
    feedGas.setMixingRule(10);
    feedGas.setMultiPhaseCheck(true);

    Stream dryFeedGas = new Stream("dry feed gas", feedGas);
    dryFeedGas.setFlowRate(1.23, "MSm3/day");
    dryFeedGas.setTemperature(10.4, "C");
    dryFeedGas.setPressure(52.21, "bara");

    StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil("water saturator", dryFeedGas);

    Stream waterSaturatedFeedGas =
        new Stream("water saturated feed gas", saturatedFeedGas.getOutletStream());

    neqsim.thermo.system.SystemInterface feedMeOH = feedGas.clone();
    feedMeOH.setMolarComposition(new double[] {0.0, 0.0, 1.0});

    Stream MeOHFeed = new Stream("lean TEG to absorber", feedMeOH);
    MeOHFeed.setFlowRate(680.5, "kg/hr");
    MeOHFeed.setTemperature(10.4, "C");
    MeOHFeed.setPressure(52.21, "bara");

    StaticMixer mainMixer = new StaticPhaseMixer("gas MeOH mixer");
    mainMixer.addStream(waterSaturatedFeedGas);
    mainMixer.addStream(MeOHFeed);

    neqsim.process.equipment.util.NeqSimUnit pipeline =
        new neqsim.process.equipment.util.NeqSimUnit(mainMixer.getOutletStream(),
            "pipeline", "stratified");
    pipeline.setLength(123.01);

    GasScrubber scrubber = new GasScrubber("gas scrub", pipeline.getOutletStream());

    Stream gasFromScrubber = new Stream("gasFromScrubber", scrubber.getGasOutStream());

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(dryFeedGas);
    operations.add(saturatedFeedGas);
    operations.add(waterSaturatedFeedGas);
    operations.add(MeOHFeed);
    operations.add(mainMixer);
    operations.add(pipeline);
    operations.add(pipeline);
    operations.add(scrubber);
    operations.add(gasFromScrubber);
    operations.run();
    // operations.run();

    operations.save("c:/temp/MeOhmasstrans.neqsim");
    // operations = ProcessSystem.open("c:/temp/TEGprocess.neqsim");
    // ((DistillationColumn)operations.getUnit("TEG regeneration
    // column")).setTopPressure(1.2);
    // operations.run();
    // ((DistillationColumn)operations.getUnit("TEG regeneration
    // column")).setNumberOfTrays(2);
    System.out.println(
        "water in wet gas [kg/MSm3] " + ((Stream) operations.getUnit("water saturated feed gas"))
            .getFluid().getPhase(0).getComponent("water").getz() * 1.0e6 * 0.01802
            * ThermodynamicConstantsInterface.atm / (ThermodynamicConstantsInterface.R * 288.15));
    // mainMixer.getFluid().display();
    // scrubber.getGasOutStream().displayResult();
    System.out.println("hydt " + gasFromScrubber.getHydrateEquilibriumTemperature());
  }
}
