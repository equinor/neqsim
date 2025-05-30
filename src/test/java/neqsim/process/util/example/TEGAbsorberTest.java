package neqsim.process.util.example;

import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.heatexchanger.ReBoiler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.GasScrubberSimple;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TEGAbsorberTest class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TEGAbsorberTest {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    neqsim.thermo.system.SystemSrkEos testSystem =
        new neqsim.thermo.system.SystemSrkSchwartzentruberEos((273.15 + 20.0), 80.00);
    testSystem.addComponent("methane", 120.00);
    testSystem.addComponent("water", 0.1);
    testSystem.addComponent("TEG", 1e-10);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    neqsim.thermo.system.SystemSrkEos testSystem2 =
        new neqsim.thermo.system.SystemSrkSchwartzentruberEos((273.15 + 20.0), 80.00);
    testSystem2.addComponent("methane", 1e-10);
    testSystem2.addComponent("water", 1e-9);
    testSystem2.addComponent("TEG", 0.10);
    testSystem2.setMixingRule(2);

    Stream fluidStreamIn = new Stream("stream to scrubber", testSystem);

    Separator gasScrubber = new GasScrubberSimple("gasInletScrubber", fluidStreamIn);

    Stream gasToAbsorber = new Stream("gas from scrubber", gasScrubber.getGasOutStream());

    Stream TEGstreamIn = new Stream("TEGstreamIn", testSystem2);

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("SimpleTEGAbsorber");
    absorber.addGasInStream(gasToAbsorber);
    absorber.addSolventInStream(TEGstreamIn);
    absorber.setNumberOfStages(5);
    absorber.setStageEfficiency(0.5);

    Stream gasStreamOut = new Stream("gasStreamOut", absorber.getGasOutStream());

    Stream TEGStreamOut = new Stream("TEGStreamOut", absorber.getSolventOutStream());

    ThrottlingValve TEG_HPLP_valve = new ThrottlingValve("ventil", TEGStreamOut);
    TEG_HPLP_valve.setOutletPressure(10.0);

    Separator MPseparator = new Separator("Separator_MP", TEG_HPLP_valve.getOutletStream());

    StreamInterface MPstreamGas = MPseparator.getGasOutStream();
    MPstreamGas.setName("MPGasStream");

    StreamInterface MPstreamLiq = MPseparator.getLiquidOutStream();
    MPstreamLiq.setName("MPLiqStream");

    ThrottlingValve LP_valve = new ThrottlingValve("LPventil", MPstreamLiq);
    LP_valve.setOutletPressure(1.5);

    ReBoiler reboiler = new ReBoiler("reboiler", LP_valve.getOutletStream());
    reboiler.setReboilerDuty(20000.0);

    neqsim.thermo.system.SystemSrkEos testSystem3 =
        new neqsim.thermo.system.SystemSrkSchwartzentruberEos((273.15 + 20.0), 1.500);
    testSystem3.addComponent("methane", 0.39);
    testSystem3.addComponent("water", 1e-10);
    testSystem3.addComponent("TEG", 1e-10);
    testSystem3.createDatabase(true);
    testSystem3.setMixingRule(2);

    Stream mixStream = new Stream("mixStream", testSystem3);

    Mixer mix = new Mixer("mixer");
    mix.addStream(reboiler.getOutletStream());
    mix.addStream(mixStream);

    StreamInterface ReboilLiqStream = mix.getOutletStream();
    ReboilLiqStream.setName("ReboilLiqStream");

    // Stream ReboilGasStream = reboiler.getOutStream();
    // ReboilLiqStream.setName("ReboilLiqStream");

    // processSimulation.processEquipment.absorber.SimpleGlycolAbsorber TEGabsorber = new
    // processSimulation.processEquipment.absorber.SimpleGlycolAbsorber(gasStreamIn);
    // TEGabsorber.setName("TEGabsorber");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(fluidStreamIn);
    operations.add(gasScrubber);
    operations.add(gasToAbsorber);
    operations.add(TEGstreamIn);
    operations.add(absorber);
    operations.add(gasStreamOut);
    operations.add(TEGStreamOut);
    operations.add(TEG_HPLP_valve);
    operations.add(MPseparator);
    operations.add(MPstreamGas);
    operations.add(MPstreamLiq);
    operations.add(LP_valve);
    operations.add(reboiler);
    operations.add(mixStream);
    operations.add(mix);
    operations.add(ReboilLiqStream);

    operations.run();
    mix.displayResult();
    // operations.displayResult();
  }
}
