package neqsim.processSimulation.processSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class OilGasProcessTest extends neqsim.NeqSimTest {

  @Test
  public void runProcess() throws InterruptedException {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("water", 51.0);
    thermoSystem.addComponent("nitrogen", 51.0);
    thermoSystem.addComponent("CO2", 51.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 51.0);
    thermoSystem.addComponent("propane", 51.0);
    thermoSystem.addComponent("i-butane", 51.0);
    thermoSystem.addComponent("n-butane", 51.0);
    thermoSystem.addComponent("iC5", 51.0);
    thermoSystem.addComponent("nC5", 1.0);

    thermoSystem.addTBPfraction("C6", 1.0, 86.0 / 1000.0, 0.66);
    thermoSystem.addTBPfraction("C7", 1.0, 91.0 / 1000.0, 0.74);
    thermoSystem.addTBPfraction("C8", 1.0, 103.0 / 1000.0, 0.77);
    thermoSystem.addTBPfraction("C9", 1.0, 117.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10_C12", 1.0, 145.0 / 1000.0, 0.80);
    thermoSystem.addPlusFraction("C13_C14", 1.0, 181.0 / 1000.0, 0.8279);
    thermoSystem.addPlusFraction("C15_C16", 1.0, 212.0 / 1000.0, 0.837);
    thermoSystem.addPlusFraction("C17_C19", 1.0, 248.0 / 1000.0, 0.849);
    thermoSystem.addPlusFraction("C20_C22", 1.0, 289.0 / 1000.0, 0.863);
    thermoSystem.addPlusFraction("C23_C25", 1.0, 330.0 / 1000.0, 0.875);
    thermoSystem.addPlusFraction("C26_C30", 1.0, 387.0 / 1000.0, 0.88);
    thermoSystem.addPlusFraction("C31_C38", 1.0, 471.0 / 1000.0, 0.90);
    thermoSystem.addPlusFraction("C38_C80", 1.0, 662.0 / 1000.0, 0.92);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setMultiPhaseCheck(true);
    thermoSystem.setMolarComposition(new double[] {0.034266, 0.005269, 0.039189, 0.700553, 0.091154,
        0.050908, 0.007751, 0.014665, 0.004249, 0.004878, 0.004541, 0.007189, 0.006904, 0.004355,
        0.007658, 0.003861, 0.003301, 0.002624, 0.001857, 0.001320, 0.001426, 0.001164, 0.000916});
    // thermoSystem.prettyPrint();

    Stream feedStream = new Stream("feed stream", thermoSystem);
    feedStream.setFlowRate(604094, "kg/hr");
    feedStream.setTemperature(25.5, "C");
    feedStream.setPressure(26.0, "bara");

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator seprator1stStage =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "1st stage separator", feedStream);

    ThrottlingValve valve1 = new ThrottlingValve("valve1", seprator1stStage.getLiquidOutStream());
    valve1.setOutletPressure(19.0);

    Heater oilHeater = new Heater("oil heater", valve1.getOutletStream());
    oilHeater.setOutTemperature(359.0);

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator seprator2ndStage =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "2nd stage separator", oilHeater.getOutletStream());

    ThrottlingValve valve2 = new ThrottlingValve("valve2", seprator2ndStage.getLiquidOutStream());
    valve2.setOutletPressure(2.7);

    StreamInterface recircstream1 = valve2.getOutletStream().clone();
    recircstream1.setName("oilRecirc1");
    recircstream1.setFlowRate(1e-6, "kg/hr");

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator seprator3rdStage =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "3rd stage separator");
    seprator3rdStage.addStream(valve2.getOutletStream());
    seprator3rdStage.addStream(recircstream1);

    ThrottlingValve pipeloss1st =
        new ThrottlingValve("pipeloss1st", seprator3rdStage.getGasOutStream());
    pipeloss1st.setOutletPressure(2.7 - 0.03);

    Heater coolerLP = new Heater("cooler LP", pipeloss1st.getOutletStream());
    coolerLP.setOutTemperature(273.15 + 25.0);

    Separator sepregenGas = new Separator("sepregenGas", coolerLP.getOutletStream());

    Pump oil1pump = new Pump("oil1pump", sepregenGas.getLiquidOutStream());
    oil1pump.setOutletPressure(19.);

    ThrottlingValve valveLP1 = new ThrottlingValve("valvseLP1", oil1pump.getOutletStream());
    valveLP1.setOutletPressure(2.7);

    Recycle recycle1 = new Recycle("oil recirc 1");
    recycle1.addStream(valveLP1.getOutletStream());
    recycle1.setOutletStream(recircstream1);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(feedStream);
    operations.add(seprator1stStage);
    operations.add(valve1);
    operations.add(oilHeater);
    operations.add(seprator2ndStage);
    operations.add(valve2);
    operations.add(recircstream1);
    operations.add(seprator3rdStage);
    operations.add(pipeloss1st);
    operations.add(coolerLP);
    operations.add(sepregenGas);
    operations.add(oil1pump);
    operations.add(valveLP1);
    operations.add(recycle1);

    operations.run();

    assertEquals(17195.25050, seprator3rdStage.getGasOutStream().getFlowRate("kg/hr"), 0.001);

    assertEquals(seprator3rdStage.getGasOutStream().getFlowRate("kg/hr"),
        coolerLP.getOutletStream().getFlowRate("kg/hr"), 1e-4);

    // System.out.println("recycle flow " + recycle1.getOutletStream().getFlowRate("kg/hr"));
    // valveLP1.getOutletStream().getFluid().prettyPrint();
  }

  @Test
  public void runProcessOilSepWithStabColumn() throws InterruptedException {

    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("nitrogen", 51.0);
    thermoSystem.addComponent("CO2", 51.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 51.0);
    thermoSystem.addComponent("propane", 51.0);
    thermoSystem.addComponent("i-butane", 51.0);
    thermoSystem.addComponent("n-butane", 51.0);
    thermoSystem.addComponent("iC5", 51.0);
    thermoSystem.addComponent("nC5", 1.0);

    thermoSystem.addTBPfraction("C6", 1.0, 86.0 / 1000.0, 0.66);
    thermoSystem.addTBPfraction("C7", 1.0, 91.0 / 1000.0, 0.74);
    thermoSystem.addTBPfraction("C8", 1.0, 103.0 / 1000.0, 0.77);
    thermoSystem.addTBPfraction("C9", 1.0, 117.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10_C12", 1.0, 145.0 / 1000.0, 0.80);
    thermoSystem.addPlusFraction("C13_C14", 1.0, 181.0 / 1000.0, 0.8279);
    thermoSystem.addPlusFraction("C15_C16", 1.0, 212.0 / 1000.0, 0.837);
    thermoSystem.addPlusFraction("C17_C19", 1.0, 248.0 / 1000.0, 0.849);
    thermoSystem.addPlusFraction("C20_C22", 1.0, 289.0 / 1000.0, 0.863);
    thermoSystem.addPlusFraction("C23_C25", 1.0, 330.0 / 1000.0, 0.875);
    thermoSystem.addPlusFraction("C26_C30", 1.0, 387.0 / 1000.0, 0.88);
    thermoSystem.addPlusFraction("C31_C38", 1.0, 471.0 / 1000.0, 0.90);
    thermoSystem.addPlusFraction("C38_C80", 1.0, 662.0 / 1000.0, 0.92);
    thermoSystem.setMixingRule("classic");

    double topsidePressure = 80.0;
    double topsideTemperature = 46.16;
    double temperatureOilHeater = 90.0;
    double secondStagePressure = 26.78;
    double thirdStagePressure = 8.96;
    double fourthStagePressure = 3;
    double firstStageSuctionCoolerTemperature = 45.0;
    double secondStageSuctionCoolerTemperature = 40.0;

    StreamInterface wellFluid = new Stream(thermoSystem);
    wellFluid.setFlowRate(1757595.58297715, "kg/hr");

    Heater feedTPsetter = new Heater(wellFluid);
    feedTPsetter.setName("inletTP");
    feedTPsetter.setOutPressure(topsidePressure, "bara");
    feedTPsetter.setOutTemperature(topsideTemperature, "C");

    StreamInterface feedToOffshoreProcess = new Stream(feedTPsetter.getOutStream());
    feedToOffshoreProcess.setName("feed to offshore");

    Separator firstStageSeparator = new Separator(feedToOffshoreProcess);
    firstStageSeparator.setName("1st stage separator");

    ThrottlingValve oilThrotValve = new ThrottlingValve(firstStageSeparator.getLiquidOutStream());
    oilThrotValve.setName("valve oil from first stage");
    oilThrotValve.setOutletPressure(secondStagePressure);

    Heater oilHeatEx = new Heater(oilThrotValve.getOutStream());
    oilHeatEx.setName("oil heat exchanger");
    oilHeatEx.setOutTemperature(temperatureOilHeater, "C");

    Separator secondStageSeparator = new Separator(oilHeatEx.getOutStream());
    secondStageSeparator.setName("2nd stage separator");

    ThrottlingValve oilThrotValve2 = new ThrottlingValve(secondStageSeparator.getLiquidOutStream());
    oilThrotValve2.setName("valve oil from second stage");
    oilThrotValve2.setOutletPressure(thirdStagePressure);

    StreamInterface oilThirdStageToSep = (Stream) wellFluid.clone();
    oilThirdStageToSep.setFlowRate(1.0, "kg/hr");

    Separator thirdStageSeparator = new Separator(oilThrotValve2.getOutStream());
    thirdStageSeparator.setName("3rd stage separator");

    ThrottlingValve oilThrotValve3 = new ThrottlingValve(thirdStageSeparator.getLiquidOutStream());
    oilThrotValve3.setName("valve oil from third stage");
    oilThrotValve3.setOutletPressure(fourthStagePressure);

    DistillationColumn stabilizer = new DistillationColumn(5, true, false);
    stabilizer.setName("stabilizer");
    stabilizer.addFeedStream(oilThrotValve3.getOutStream(), 5);
    stabilizer.getReboiler().setOutTemperature(273.15 + 100);
    stabilizer.setTopPressure(fourthStagePressure);
    stabilizer.setBottomPressure(fourthStagePressure);

    StreamInterface stableOil = new Stream(stabilizer.getLiquidOutStream());
    stableOil.setName("stable oil");

    Cooler firstStageCooler = new Cooler(stabilizer.getGasOutStream());
    firstStageCooler.setName("1st stage cooler");
    firstStageCooler.setOutTemperature(firstStageSuctionCoolerTemperature, "C");

    Separator firstStageScrubber = new Separator(firstStageCooler.getOutStream());
    firstStageScrubber.setName("1st stage scrubber");

    Compressor firstStageCompressor = new Compressor(firstStageScrubber.getGasOutStream());
    firstStageCompressor.setName("1st stage compressor");
    firstStageCompressor.setOutletPressure(thirdStagePressure);
    firstStageCompressor.setIsentropicEfficiency(0.75);

    Mixer secondStageGasMixer = new Mixer("2nd stage mixer");
    secondStageGasMixer.addStream(thirdStageSeparator.getGasOutStream());
    secondStageGasMixer.addStream(firstStageCompressor.getOutStream());

    Cooler secondStageCooler = new Cooler(secondStageGasMixer.getOutStream());
    secondStageCooler.setName("2nd stage cooler");
    secondStageCooler.setOutTemperature(secondStageSuctionCoolerTemperature, "C");

    Separator secondStageScrubber = new Separator(secondStageCooler.getOutStream());
    secondStageScrubber.setName("2nd stage scrubber");

    Compressor secondStageCompressor = new Compressor(secondStageScrubber.getGasOutStream());
    secondStageCompressor.setName("2nd stage compressor");
    secondStageCompressor.setOutletPressure(secondStagePressure);
    secondStageCompressor.setIsentropicEfficiency(0.75);

    Mixer thirdStageMixer = new Mixer("3rd stage mixer");
    thirdStageMixer.addStream(secondStageSeparator.getGasOutStream());
    thirdStageMixer.addStream(secondStageCompressor.getOutStream());

    Cooler thirdStageCooler = new Cooler(thirdStageMixer.getOutStream());
    thirdStageCooler.setName("3rd stage cooler");
    thirdStageCooler.setOutTemperature(secondStageSuctionCoolerTemperature, "C");

    Separator thirdStageScrubber = new Separator(thirdStageCooler.getOutStream());
    thirdStageScrubber.setName("3rd stage scrubber");

    Compressor thirdStageCompressor = new Compressor(thirdStageScrubber.getGasOutStream());
    thirdStageCompressor.setName("3rd stage compressor");
    thirdStageCompressor.setOutletPressure(topsidePressure);
    thirdStageCompressor.setIsentropicEfficiency(0.75);

    Mixer richGasMixer = new Mixer("fourth Stage mixer");
    richGasMixer.addStream(thirdStageCompressor.getOutStream());
    richGasMixer.addStream(firstStageSeparator.getGasOutStream());

    Cooler dewPointControlCooler = new Cooler(richGasMixer.getOutStream());
    dewPointControlCooler.setName("dew point cooler");
    dewPointControlCooler.setOutTemperature(35.0, "C");

    Mixer lpLiqmixer = new Mixer("LP liq mixer");
    lpLiqmixer.addStream(firstStageScrubber.getLiquidOutStream());
    lpLiqmixer.addStream(secondStageScrubber.getLiquidOutStream());
    lpLiqmixer.addStream(thirdStageScrubber.getLiquidOutStream());

    Pump pumpLiquid = new Pump(lpLiqmixer.getOutStream());
    pumpLiquid.setName("pump1");
    pumpLiquid.setOutletPressure(secondStagePressure);

    Recycle recycle1 = new Recycle("recyle");
    recycle1.addStream(pumpLiquid.getOutStream());
    recycle1.setOutletStream(oilThirdStageToSep);
    recycle1.setFlowAccuracy(1e-3);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(wellFluid);
    operations.add(feedTPsetter);
    operations.add(feedToOffshoreProcess);
    operations.add(firstStageSeparator);
    operations.add(oilThrotValve);
    operations.add(oilHeatEx);
    operations.add(secondStageSeparator);
    operations.add(oilThrotValve2);
    operations.add(oilThirdStageToSep);
    operations.add(thirdStageSeparator);
    operations.add(oilThrotValve3);
    operations.add(stabilizer);
    operations.add(stableOil);
    operations.add(firstStageCooler);

    operations.add(firstStageScrubber);
    operations.add(firstStageCompressor);
    operations.add(secondStageGasMixer);
    operations.add(secondStageCooler);
    operations.add(secondStageScrubber);
    operations.add(secondStageCompressor);

    operations.add(thirdStageMixer);
    operations.add(thirdStageCooler);
    operations.add(thirdStageScrubber);
    operations.add(thirdStageCompressor);
    operations.add(richGasMixer);
    operations.add(dewPointControlCooler);

    operations.add(lpLiqmixer);
    operations.add(pumpLiquid);
    operations.add(recycle1);
    Thread thread = operations.runAsThread();
    thread.join(1 * 60 * 1000);
    secondStageSeparator.addStream(oilThirdStageToSep);
    thread = operations.runAsThread();
    thread.join(1 * 60 * 1000);
    double massbalance = (feedToOffshoreProcess.getFluid().getFlowRate("kg/hr")
        - dewPointControlCooler.getOutStream().getFlowRate("kg/hr")
        - stableOil.getFlowRate("kg/hr")) / (feedToOffshoreProcess.getFluid().getFlowRate("kg/hr"));

    assertEquals(0.0, massbalance * 100, 1.0);
  }
}
