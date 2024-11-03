package neqsim.processsimulation.util.example;

import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * <p>
 * TestSeparationTrain class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestSeparationTrain {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    double inletPressure = 15.00; // bar
    double inletTemperatuure = 273.15 + 50.0; // K

    double secondstagePressure = 5.00; // bar'
    double thirdstagePressure = 1.50; // bar

    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkCPAstatoil(inletTemperatuure, inletPressure);

    testSystem.addComponent("methane", 50);
    testSystem.addComponent("propane", 5);
    testSystem.addComponent("nC10", 50);
    testSystem.addComponent("water", 50);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    Stream wellStream = new Stream("Well stream", testSystem);
    Separator inletSeparator = new Separator("Inlet separator", wellStream);

    Heater liquidOutHeater = new Heater("liquidOutHeater", inletSeparator.getLiquidOutStream());
    liquidOutHeater.setOutTemperature(273.15 + 55.0);

    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st stage Separator", liquidOutHeater.getOutletStream());

    ThrottlingValve valve1 =
        new ThrottlingValve("snohvit valve", firstStageSeparator.getOilOutStream());
    valve1.setOutletPressure(secondstagePressure);

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("2nd stage Separator", valve1.getOutletStream());

    ThrottlingValve thirdStageValve =
        new ThrottlingValve("snohvit valve2", secondStageSeparator.getLiquidOutStream());
    thirdStageValve.setOutletPressure(thirdstagePressure);

    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("3rd stage Separator", thirdStageValve.getOutletStream());

    Compressor thirdStageCompressor =
        new Compressor("thirdStageCompressor", thirdStageSeparator.getGasOutStream());
    thirdStageCompressor.setOutletPressure(secondstagePressure);

    Mixer thirdStageMixer = new Mixer("thirdStageMixer");
    thirdStageMixer.addStream(thirdStageCompressor.getOutletStream());
    thirdStageMixer.addStream(secondStageSeparator.getGasOutStream());

    Cooler thirdSstageCoooler = new Cooler("thirdSstageCoooler", thirdStageMixer.getOutletStream());
    thirdSstageCoooler.setOutTemperature(273.15 + 30.0);

    ThreePhaseSeparator thirdStageScrubber = new ThreePhaseSeparator(
        "Third stage gas resirc scrubber", thirdSstageCoooler.getOutletStream());
    secondStageSeparator.addStream(thirdStageScrubber.getOilOutStream());
    secondStageSeparator.addStream(thirdStageScrubber.getWaterOutStream());

    Compressor secondStageCompressor =
        new Compressor("secondStageCompressor", thirdStageScrubber.getGasOutStream());
    secondStageCompressor.setOutletPressure(inletPressure);

    Mixer HPgasMixer = new Mixer("HPgasMixer");
    HPgasMixer.addStream(inletSeparator.getGasOutStream());
    HPgasMixer.addStream(secondStageCompressor.getOutletStream());

    Cooler oilCooler = new Cooler("oilCooler", thirdStageSeparator.getLiquidOutStream());
    oilCooler.setOutTemperature(273.15 + 30.0);

    Cooler inletGasCooler = new Cooler("inletGasCooler", HPgasMixer.getOutletStream());
    inletGasCooler.setOutTemperature(273.15 + 30.0);

    Separator gasInletScrubber =
        new Separator("Gas scrubber inlet", inletGasCooler.getOutletStream());

    Recycle HPliquidRecycle = new Recycle("HPliquidRecycle");
    double tolerance = 1e-10;
    HPliquidRecycle.setTolerance(tolerance);
    HPliquidRecycle.addStream(gasInletScrubber.getLiquidOutStream());
    inletSeparator.addStream(HPliquidRecycle.getOutletStream());

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(wellStream);
    operations.add(inletSeparator);
    operations.add(liquidOutHeater);
    operations.add(firstStageSeparator);
    operations.add(valve1);
    operations.add(secondStageSeparator);
    operations.add(thirdStageValve);
    operations.add(thirdStageSeparator);
    operations.add(thirdStageCompressor);
    operations.add(thirdStageMixer);
    operations.add(thirdSstageCoooler);
    operations.add(thirdStageScrubber);
    operations.add(HPliquidRecycle);

    operations.add(secondStageCompressor);

    operations.add(oilCooler);
    operations.add(HPgasMixer);
    operations.add(inletGasCooler);
    operations.add(gasInletScrubber);

    operations.run();
    // secondStageSeparator.addStream(thirdStageScrubber.getWaterOutStream());
    // operations.run();
    // secondStageSeparator.displayResult();
    gasInletScrubber.getGasOutStream().displayResult();
    firstStageSeparator.displayResult();
    // secondStageSeparator.displayResult();
    // thirdStageSeparator.displayResult();
    // inletSeparator.displayResult();
    // operations.displayResult();
    // liquidOutHeater.getOutStream().getThermoSystem().display();
  }
}
