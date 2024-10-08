package neqsim.processsimulation.util.example;

import neqsim.processsimulation.processequipment.heatexchanger.Heater;
import neqsim.processsimulation.processequipment.mixer.Mixer;
import neqsim.processsimulation.processequipment.separator.Separator;
import neqsim.processsimulation.processequipment.separator.ThreePhaseSeparator;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processequipment.util.Recycle;

/**
 * <p>
 * simpleTopSideProcess2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class simpleTopSideProcess2 {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.Fluid fluidCreator = new neqsim.thermo.Fluid();
    fluidCreator.setHasWater(true);
    neqsim.thermo.system.SystemInterface fluid = fluidCreator.create("gas condensate");
    fluid.setTemperature(45.0, "C");
    fluid.setPressure(5.0, "bara");
    Stream stream_inlet = new Stream("Stream1", fluid);

    Mixer mixer_inlet = new neqsim.processsimulation.processequipment.mixer.StaticMixer("Mixer HP");
    mixer_inlet.addStream(stream_inlet);

    ThreePhaseSeparator separator_inlet =
        new ThreePhaseSeparator("Separator 1", mixer_inlet.getOutletStream());

    Stream stream_gasFromSep = new Stream("stream_gasFromSep", separator_inlet.getGasOutStream());

    Heater cooler1 = new Heater("cooler1", stream_gasFromSep);
    cooler1.setOutTemperature(285.25);

    Separator scrubber = new Separator("Scrubber 1", cooler1.getOutletStream());

    Recycle recyleOp = new Recycle("resyc");
    recyleOp.addStream(scrubber.getLiquidOutStream());
    mixer_inlet.addStream(recyleOp.getOutletStream());

    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(stream_inlet);
    operations.add(mixer_inlet);
    operations.add(separator_inlet);
    operations.add(stream_gasFromSep);
    operations.add(cooler1);
    operations.add(scrubber);
    operations.add(recyleOp);
    operations.run();
    scrubber.displayResult();

    stream_inlet.getThermoSystem().setTemperature(273.15 + 35.0);
    operations.run();

    // scrubber.displayResult();

    stream_inlet.getThermoSystem().setTemperature(273.15 + 30.0);
    operations.run();
    // scrubber.displayResult();

    stream_inlet.getThermoSystem().setTemperature(273.15 + 16.0);
    operations.run();
    // scrubber.displayResult();
  }
}
