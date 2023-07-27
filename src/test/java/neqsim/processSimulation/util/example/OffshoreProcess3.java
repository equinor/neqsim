package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.MoleFractionControllerUtil;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.SetPoint;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * OffshoreProcess3 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class OffshoreProcess3 {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(354.2, 120.0);

    testSystem.addComponent("nitrogen", 1.0);
    testSystem.addComponent("CO2", 1.42);
    testSystem.addComponent("methane", 60.88);
    testSystem.addComponent("ethane", 8.07);
    testSystem.addComponent("propane", 3.54);
    testSystem.addComponent("i-butane", 0.54);
    testSystem.addComponent("n-butane", 0.2);
    testSystem.addComponent("i-pentane", 0.21);
    testSystem.addComponent("n-pentane", 0.19);
    testSystem.addComponent("n-hexane", 0.28);
    testSystem.addComponent("n-heptane", 5.0);
    testSystem.addComponent("water", 1.5);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);
    neqsim.thermo.system.SystemInterface fluid3 =
        new neqsim.thermo.Fluid().create("black oil with water");

    Stream wellStream = new Stream("well stream", fluid3);
    // wellStream.setFluid(testSystem);
    wellStream.setFlowRate(14.23, "MSm3/day");
    wellStream.setTemperature(41.0, "C");
    wellStream.setPressure(120.0, "bara");

    Heater inletTempControl = new Heater("well stream cooler/heater", wellStream);
    inletTempControl.setOutTemperature(50.0, "C");
    inletTempControl.setOutPressure(55.21, "bara");

    ThrottlingValve valve =
        new ThrottlingValve("inlet choke valve", inletTempControl.getOutletStream());
    valve.setOutletPressure(35.21);

    Stream oilToInletSep = new Stream("oilToInletSep", fluid3.clone());
    oilToInletSep.setFlowRate(1e-10, "kg/hr");
    ThreePhaseSeparator inletSeparator =
        new ThreePhaseSeparator("1st stage separator", valve.getOutletStream());
    inletSeparator.setEntrainment(0.005, "mass", "product", "aqueous", "oil");
    inletSeparator.setEntrainment(500e-6, "mole", "product", "oil", "aqueous");
    inletSeparator.addStream(oilToInletSep);

    Heater heater1 = new Heater("oil cooler/heater to 2nd stage", inletSeparator.getOilOutStream());
    heater1.setOutTemperature(85.0, "C");

    ThrottlingValve valve2 = new ThrottlingValve("oil HP to MP valve", heater1.getOutletStream());
    valve2.setOutletPressure(7.0);

    ThrottlingValve waterDPvalve =
        new ThrottlingValve("Water HP to LP valve", inletSeparator.getWaterOutStream());
    waterDPvalve.setOutletPressure(1.01325);

    Separator waterStabSep =
        new Separator("water degassing separator", waterDPvalve.getOutletStream());

    Stream waterToTreatment = new Stream("water to treatment", waterStabSep.getLiquidOutStream());

    Stream gasFromWaterTreatment =
        new Stream("gas from water treatment", waterStabSep.getGasOutStream());

    Stream oilToSep = new Stream("oilToSep", fluid3.clone());
    oilToSep.setFlowRate(1e-10, "kg/hr");

    ThreePhaseSeparator mpseparator =
        new ThreePhaseSeparator("2nd stage separator", valve2.getOutletStream());
    mpseparator.addStream(oilToSep);

    ThrottlingValve valvempValve =
        new ThrottlingValve("oil MP to LP valve", mpseparator.getOilOutStream());
    valvempValve.setOutletPressure(2.1);

    ThreePhaseSeparator lpseparator =
        new ThreePhaseSeparator("3rd stage separator", valvempValve.getOutletStream());
    Stream stableOilStream = (Stream) lpseparator.getOilOutStream();
    stableOilStream.setName("stable oil");

    Compressor lpcompressor =
        new Compressor("1st stage recompressor", lpseparator.getGasOutStream());
    // lpcompressor.setOutletPressure(15.0);
    SetPoint compressorPresSet2 =
        new SetPoint("comp pres LP set", lpcompressor, "pressure", valve2.getOutletStream());

    Cooler lpgasheater = new Cooler("1st stage gas cooler", lpcompressor.getOutletStream());
    lpgasheater.setOutTemperature(35.0, "C");

    neqsim.thermo.system.SystemInterface coolingWaterSYstm = fluid3.clone();
    // coolingWaterSYstm.setMolarComposition(new double[] { 0.0, 0.0, 1.0, 0.0, 0.0,
    // 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 });
    coolingWaterSYstm.setEmptyFluid();
    coolingWaterSYstm.addComponent("water", 1.0);

    /*
     * Stream coolingWater = new Stream("cooling water", coolingWaterSYstm);
     * coolingWater.setFlowRate(500900.2, "kg/hr"); coolingWater.setTemperature(10.0, "C");
     * coolingWater.setPressure(10.0, "bara");
     * 
     * HeatExchanger heatEx = new HeatExchanger(lpcompressor.getOutStream());
     * heatEx.setName("LP heat Exchanger"); heatEx.setGuessOutTemperature(273.15+40.0);
     * heatEx.setUAvalue(45000.0); heatEx.setFeedStream(1, coolingWater);
     * 
     */

    Cooler lpHeatExchanger =
        new Cooler("1st stage gas heat exchanger", lpcompressor.getOutletStream());
    lpgasheater.setOutTemperature(35.0, "C");

    Separator lpscrubber = new Separator("2nd stage scrubber", lpgasheater.getOutletStream());
    Stream liqFromlpscrubber = (Stream) lpscrubber.getLiquidOutStream();

    Recycle lpscrubberResyc = new Recycle("mpscrubberRes");
    lpscrubberResyc.addStream(liqFromlpscrubber);
    lpscrubberResyc.setOutletStream(oilToSep);

    Mixer mixermp = new Mixer("mp gas mixer");
    mixermp.addStream(lpscrubber.getGasOutStream());
    mixermp.addStream(mpseparator.getGasOutStream());

    Compressor compressor2stage =
        new Compressor("2nd stage recompressor", mixermp.getOutletStream());
    compressor2stage.setIsentropicEfficiency(0.75);
    // compressor2stage.setOutletPressure(75.0);

    SetPoint compressorPresSet =
        new SetPoint("comp pres set", compressor2stage, "pressure", inletSeparator);

    Heater secondndstagecooler = new Heater("2nd stage cooler", compressor2stage.getOutletStream());
    secondndstagecooler.setOutTemperature(290.0);

    Separator scrubbberfrom2ndstage =
        new Separator("scrubber of mix gas HP", secondndstagecooler.getOutletStream());

    Mixer mixer = new Mixer("HP rich gas mixer");
    mixer.addStream(inletSeparator.getGasOutStream());
    mixer.addStream(scrubbberfrom2ndstage.getGasOutStream());

    Heater dewPointScrubberCooler =
        new Heater("dew point scrubber cooler2", mixer.getOutletStream());
    dewPointScrubberCooler.setOutTemperature(273.15 + 32.3);

    Separator mpscrubber =
        new Separator("dew point control scrubber", dewPointScrubberCooler.getOutletStream());

    Stream liqFrommpscrubber = (Stream) mpscrubber.getLiquidOutStream();

    Recycle mpscrubberResyc = new Recycle("hpscrubberRes");
    mpscrubberResyc.addStream(liqFrommpscrubber);
    mpscrubberResyc.addStream(scrubbberfrom2ndstage.getLiquidOutStream());
    mpscrubberResyc.setOutletStream(oilToSep);

    MoleFractionControllerUtil waterRemoval =
        new MoleFractionControllerUtil(mpscrubber.getGasOutStream());
    waterRemoval.setMoleFraction("water", 10.0e-6);

    Stream richGas = new Stream("rich gas", waterRemoval.getOutletStream());

    Compressor exportGasCompressor = new Compressor("1st stage export compressor", richGas);
    exportGasCompressor.setIsentropicEfficiency(0.75);
    exportGasCompressor.setOutletPressure(richGas.getPressure() * 2.5);

    Cooler exportGasCompressorCooler =
        new Cooler("1st stage export gas cooler", exportGasCompressor.getOutletStream());
    exportGasCompressorCooler.setOutTemperature(35.0, "C");

    Compressor exportGasCompressor2 =
        new Compressor("2nd stage export compressor", exportGasCompressorCooler.getOutletStream());
    exportGasCompressor2.setIsentropicEfficiency(0.75);
    exportGasCompressor2
        .setOutletPressure(exportGasCompressorCooler.getOutletStream().getPressure() * 2.5 * 2.5);

    Cooler exportGasCompressorCooler2 =
        new Cooler("2nd stage export gas cooler", exportGasCompressor2.getOutletStream());
    exportGasCompressorCooler2.setOutTemperature(35.0, "C");

    Stream exportGas = new Stream("export gas", exportGasCompressorCooler2.getOutletStream());

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(wellStream);
    operations.add(inletTempControl);
    operations.add(valve);
    operations.add(inletSeparator);
    operations.add(oilToInletSep);
    operations.add(heater1);
    operations.add(valve2);
    operations.add(waterDPvalve);
    operations.add(waterStabSep);
    operations.add(waterToTreatment);
    operations.add(gasFromWaterTreatment);
    operations.add(mpseparator);
    operations.add(oilToSep);
    operations.add(valvempValve);
    operations.add(lpseparator);
    operations.add(stableOilStream);
    operations.add(compressorPresSet2);
    operations.add(lpcompressor);
    operations.add(lpgasheater);
    // operations.add(coolingWater);
    // operations.add(heatEx);
    operations.add(lpHeatExchanger);
    operations.add(lpscrubber);
    operations.add(liqFromlpscrubber);
    operations.add(lpscrubberResyc);
    operations.add(mixermp);

    operations.add(compressorPresSet);
    operations.add(compressor2stage);
    operations.add(secondndstagecooler);
    operations.add(scrubbberfrom2ndstage);
    operations.add(mixer);
    operations.add(dewPointScrubberCooler);
    operations.add(mpscrubber);
    operations.add(liqFrommpscrubber);
    operations.add(mpscrubberResyc);
    operations.add(waterRemoval);
    operations.add(richGas);
    operations.add(exportGasCompressor);
    operations.add(exportGasCompressorCooler);
    operations.add(exportGasCompressor2);
    operations.add(exportGasCompressorCooler2);
    operations.add(exportGas);
    // ProcessSystem operations2 = operations.open("c:/temp/offshorePro.neqsim");
    // ((Heater) operations2.getUnit("dew point scrubber
    // cooler2")).setOutTemperature(298.15);
    // operations2.run();
    // ((Stream) operations2.getUnit("rich gas")).phaseEnvelope();

    // operations.displayResult();
    // inletSeparator.displayResult();
    // richGas.displayResult();
    // richGas.displayResult();
    // mpseparator.displayResult();

    // oilToSep.displayResult();
    // lppump.displayResult();

    operations.run();
    exportGas.displayResult();
    exportGasCompressor2.generateCompressorCurves();
    exportGasCompressor2.setUsePolytropicCalc(true);
    exportGasCompressor2.setSpeed(11500);
    exportGasCompressor2.getCompressorChart().setHeadUnit("kJ/kg");
    exportGasCompressor2.run();
    System.out.println("power " + exportGasCompressor2.getPower());
    // operations.run();
    exportGas.displayResult();
    /*
     * // System.out.println("second stage comp power " + ((Compressor) //
     * operations.getUnit("2nd stage recompressor")).getPower()/1.0e3 + " kW");
     * 
     * // System.out.println("first stage   comp power " + ((Compressor)
     * operations.getUnit("1st stage recompressor")).getPower()/1.0e3 + " kW");
     * 
     * System.out.println("gas from inlet separator " + ((Separator)
     * operations.getUnit("1st stage separator"))
     * .getGasOutStream().getFluid().getFlowRate("MSm3/day"));
     * 
     * System.out .println("pressure of export oil  " + ((Stream)
     * operations.getUnit("stable oil")).getPressure("bara"));
     * 
     * System.out.println( "temperature of export oil  " + ((Stream)
     * operations.getUnit("stable oil")).getTemperature("C"));
     * 
     * System.out.println("TVP of export oil (30.0 C) " + ((Stream)
     * operations.getUnit("stable oil")).TVP(30.0, "C"));
     * 
     * // System.out.println("entropy production " + // operations.getEntropyProduction("J/K") +
     * " J/K"); // System.out.println("mass balance  separator " + ((Separator)
     * operations.getUnit("scrubber of mix gas HP")).getMassBalance("kg/sec") + " kg/sec");
     * 
     * 
     * // liqFromlpscrubber.displayResult(); // richGas.phaseEnvelope(); // richGas.displayResult();
     * operations.save("c:/temp/offshorePro.neqsim");
     * inletSeparator.getLiquidOutStream().getFluid().display();
     * inletSeparator.getWaterOutStream().getFluid().display(); exportGas.displayResult(); /*
     * Hydrocyclone hydroSyc = new Hydrocyclone(inletSeparator.getWaterOutStream()); hydroSyc.run();
     * hydroSyc.getGasOutStream().getFluid().display();
     * hydroSyc.getLiquidOutStream().getFluid().display();
     */
    // ProcessSystem operations2 = operations.open("c:/temp/offshorePro.neqsim");
    // operations2.run();
    // cooler1stagecomp.getOutStream().phaseEnvelope();
    // coolingWater.run();

    // liqFromlpscrubber.displayResult();
    // richGas.phaseEnvelope();
    // richGas.displayResult();
    operations.save("c:/temp/offshorePro.neqsim");
    inletSeparator.getLiquidOutStream().getFluid().display();
    inletSeparator.getWaterOutStream().getFluid().display();

    /*
     * Hydrocyclone hydroSyc = new Hydrocyclone(inletSeparator.getWaterOutStream()); hydroSyc.run();
     * hydroSyc.getGasOutStream().getFluid().display();
     * hydroSyc.getLiquidOutStream().getFluid().display();
     */
    // ProcessSystem operations2 = operations.open("c:/temp/offshorePro.neqsim");
    // operations2.run();
    // cooler1stagecomp.getOutStream().phaseEnvelope();
    // coolingWater.run();

    // coolingWater.getFluid().init(3);
    // heatEx.run();heatEx.run();
    // heatEx.getOutStream(0).getFluid().display();
    // heatEx.getOutStream(1).getFluid().display();
    // System.out.println("entropy production heat exchanger "
    // +heatEx.getEntropyProduction("J/K") + " J/K");
    // System.out.println("mass balance production heat exchanger "
    // +heatEx.getMassBalance("kg/sec"));

    System.out.println("Cooler Duty " + operations.getCoolerDuty("J/sec") / 1.0e6 + " MW");
    System.out.println("Heater Duty " + operations.getHeaterDuty("J/sec") / 1.0e6 + " MW");
    System.out.println("Power " + operations.getPower("W") / 1.0e6 + " MW");

    System.out.println("exergy change " + operations.getExergyChange("J"));
    // System.out.println("total weight " + operations.getMechanicalWeight("kg") + " kg");
  }
}
