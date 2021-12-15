package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 *
 * @author ESOL
 */
public class TestSeparationTrain {
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

        Heater liquidOutHeater = new Heater(inletSeparator.getLiquidOutStream());
        liquidOutHeater.setOutTemperature(273.15 + 55.0);

        ThreePhaseSeparator firstStageSeparator =
                new ThreePhaseSeparator("1st stage Separator", liquidOutHeater.getOutStream());

        ThrottlingValve valve1 =
                new ThrottlingValve("snohvit valve", firstStageSeparator.getOilOutStream());
        valve1.setOutletPressure(secondstagePressure);

        ThreePhaseSeparator secondStageSeparator =
                new ThreePhaseSeparator("2nd stage Separator", valve1.getOutStream());

        ThrottlingValve thirdStageValve =
                new ThrottlingValve("snohvit valve2", secondStageSeparator.getLiquidOutStream());
        thirdStageValve.setOutletPressure(thirdstagePressure);
        //
        ThreePhaseSeparator thirdStageSeparator =
                new ThreePhaseSeparator("3rd stage Separator", thirdStageValve.getOutStream());

        Compressor thirdStageCompressor = new Compressor(thirdStageSeparator.getGasOutStream());
        thirdStageCompressor.setOutletPressure(secondstagePressure);

        Mixer thirdStageMixer = new Mixer();
        thirdStageMixer.addStream(thirdStageCompressor.getOutStream());
        thirdStageMixer.addStream(secondStageSeparator.getGasOutStream());

        Cooler thirdSstageCoooler = new Cooler(thirdStageMixer.getOutStream());
        thirdSstageCoooler.setOutTemperature(273.15 + 30.0);

        ThreePhaseSeparator thirdStageScrubber = new ThreePhaseSeparator(
                "Third stage gas resirc scrubber", thirdSstageCoooler.getOutStream());
        secondStageSeparator.addStream(thirdStageScrubber.getOilOutStream());
        secondStageSeparator.addStream(thirdStageScrubber.getWaterOutStream());

        Compressor secondStageCompressor = new Compressor(thirdStageScrubber.getGasOutStream());
        secondStageCompressor.setOutletPressure(inletPressure);

        Mixer HPgasMixer = new Mixer();
        HPgasMixer.addStream(inletSeparator.getGasOutStream());
        HPgasMixer.addStream(secondStageCompressor.getOutStream());

        Cooler oilCooler = new Cooler(thirdStageSeparator.getLiquidOutStream());
        oilCooler.setOutTemperature(273.15 + 30.0);

        Cooler inletGasCooler = new Cooler(HPgasMixer.getOutStream());
        inletGasCooler.setOutTemperature(273.15 + 30.0);

        Separator gasInletScrubber =
                new Separator("Gas scrubber inlet", inletGasCooler.getOutStream());

        Recycle HPliquidRecycle = new Recycle();
        double tolerance = 1e-10;
        HPliquidRecycle.setTolerance(tolerance);
        HPliquidRecycle.addStream(gasInletScrubber.getLiquidOutStream());
        inletSeparator.addStream(HPliquidRecycle.getOutStream());

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
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
