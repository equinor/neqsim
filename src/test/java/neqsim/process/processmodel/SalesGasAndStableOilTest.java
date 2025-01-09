package neqsim.process.processmodel;

import java.io.File;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

public class SalesGasAndStableOilTest extends neqsim.NeqSimTest {
  File file = new File("src/test/java/neqsim/process/processmodel");
  String fileFluid1 = file.getAbsolutePath() + "/feedfluid.e300";

  @Test
  public void testProcess() {
    SystemInterface wellFluid = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(fileFluid1);
    // wellFluid.setMultiPhaseCheck(true);

    neqsim.process.equipment.stream.Stream wellStream =
        new neqsim.process.equipment.stream.Stream("HP well stream", wellFluid);
    SystemInterface LPwellFLuid = wellFluid.clone();

    Stream wellStreamHP = new neqsim.process.equipment.stream.Stream("HP well stream", wellFluid);
    wellStreamHP.setFlowRate(10.0, "MSm3/day");
    wellStreamHP.run();

    Stream LPwellStream = new neqsim.process.equipment.stream.Stream("LP well stream", LPwellFLuid);
    LPwellStream.setFlowRate(15.0, "MSm3/day");
    LPwellStream.run();

    ThreePhaseSeparator firstStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("1st stage separator",
            wellStreamHP);
    firstStageSeparator.run();
  }

  @Test
  public void testProcess2() {
    double[] values = {91.7877475, 166.4337708, 9439.315801, 818.1486699, 344.9999383, 44.79751175,
        87.37151962, 29.04201342, 33.49897183, 50.83560884, 52.79138188, 43.39787788, 35.33509333,
        118.1702641, 74.28553034, 26.39921569};

    // Calculate HP1 and LP1
    double[] HP1 = new double[values.length];
    double[] LP1 = new double[values.length];

    for (int i = 0; i < values.length; i++) {
      HP1[i] = values[i] * 0.8; // Multiply by 0.8
      LP1[i] = values[i] * 0.2; // Multiply by 0.2
    }

    SystemInterface wellFluid = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(fileFluid1);
    wellFluid.setMolarFlowRates(HP1);
    SystemInterface LPwellFLuid = wellFluid.clone();
    LPwellFLuid.setMolarFlowRates(LP1);

    Stream wellStreamHP = new neqsim.process.equipment.stream.Stream("HP well stream", wellFluid);
    wellStreamHP.setTemperature(67.0, "C");
    wellStreamHP.setPressure(62.0, "bara");
    wellStreamHP.run();

    Stream LPwellStream = new neqsim.process.equipment.stream.Stream("LP well stream", LPwellFLuid);
    LPwellStream.setTemperature(80.0, "C");
    LPwellStream.setPressure(20.0, "bara");
    LPwellStream.run();

    ThreePhaseSeparator firstStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("1st stage separator",
            wellStreamHP);
    firstStageSeparator.run();

    ThrottlingValve oilvalve1 = new neqsim.process.equipment.valve.ThrottlingValve(
        "oil depres valve", firstStageSeparator.getOilOutStream());
    oilvalve1.setOutletPressure(20.0, "bara");
    oilvalve1.run();

    Stream oilFirstStage = wellStreamHP.clone();
    oilFirstStage.setName("frist stage oil reflux");
    oilFirstStage.setFlowRate(10.0, "kg/hr");
    oilFirstStage.setPressure(20.0, "bara");
    oilFirstStage.setTemperature(30.0, "C");
    oilFirstStage.run();

    Mixer oilFirstStageMixer = new neqsim.process.equipment.mixer.Mixer("first stage oil mixer");
    oilFirstStageMixer.addStream(oilvalve1.getOutletStream());
    oilFirstStageMixer.addStream(oilFirstStage);
    oilFirstStageMixer.run();

    Heater oilHeaterFromFirstStage = new neqsim.process.equipment.heatexchanger.Heater(
        "oil heater second stage", oilFirstStageMixer.getOutletStream());
    oilHeaterFromFirstStage.setOutTemperature(80.0, "C");
    oilHeaterFromFirstStage.run();

    ThreePhaseSeparator secondStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("2nd stage separator",
            oilHeaterFromFirstStage.getOutStream());
    secondStageSeparator.addStream(LPwellStream);
    secondStageSeparator.run();

    ThrottlingValve valve_oil_from_seccond_stage =
        new neqsim.process.equipment.valve.ThrottlingValve("valve oil from seccond stage",
            secondStageSeparator.getOilOutStream());
    valve_oil_from_seccond_stage.setOutletPressure(7.0, "bara");
    valve_oil_from_seccond_stage.run();

    Stream oilSeccondStage = wellStreamHP.clone();
    oilSeccondStage.setName("seccond stage oil reflux");
    oilSeccondStage.setFlowRate(10.0, "kg/hr");
    oilSeccondStage.setPressure(6.0, "bara");
    oilSeccondStage.setTemperature(30.0, "C");
    oilSeccondStage.run();

    Mixer oilSeccondStageMixer =
        new neqsim.process.equipment.mixer.Mixer("seccond stage oil mixer");
    oilSeccondStageMixer.addStream(valve_oil_from_seccond_stage.getOutletStream());
    oilSeccondStageMixer.addStream(oilSeccondStage);
    oilSeccondStageMixer.run();

    ThreePhaseSeparator thirdStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("3rd stage separator",
            oilSeccondStageMixer.getOutletStream());
    thirdStageSeparator.run();

    ThrottlingValve valve_oil_from_third_stage = new neqsim.process.equipment.valve.ThrottlingValve(
        "valve oil from third stage", thirdStageSeparator.getOilOutStream());
    valve_oil_from_third_stage.setOutletPressure(3.0, "bara");
    valve_oil_from_third_stage.run();

    Stream oilThirdStage = wellStreamHP.clone();
    oilThirdStage.setName("third stage oil reflux");
    oilThirdStage.setFlowRate(10.0, "kg/hr");
    oilThirdStage.setPressure(3.0, "bara");
    oilThirdStage.setTemperature(30.0, "C");
    oilThirdStage.run();

    Mixer oilThirdStageMixer = new neqsim.process.equipment.mixer.Mixer("third stage oil mixer");
    oilThirdStageMixer.addStream(valve_oil_from_third_stage.getOutletStream());
    oilThirdStageMixer.addStream(oilThirdStage);
    oilThirdStageMixer.run();

    ThreePhaseSeparator fourthStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("4th stage separator",
            oilThirdStageMixer.getOutStream());
    fourthStageSeparator.run();

    Cooler firstStageCooler = new neqsim.process.equipment.heatexchanger.Cooler("1st stage cooler",
        fourthStageSeparator.getGasOutStream());
    firstStageCooler.setOutTemperature(28.9, "C");
    firstStageCooler.run();

    Separator firstStageScrubber = new neqsim.process.equipment.separator.Separator(
        "1st stage scrubber", firstStageCooler.getOutStream());
    firstStageScrubber.run();

    Pump firststagescrubberpump = new neqsim.process.equipment.pump.Pump("1st stage scrubber pump",
        firstStageScrubber.getLiquidOutStream());
    firststagescrubberpump.setOutletPressure(7.0, "bara");
    firststagescrubberpump.run();

    Compressor firstStageCompressor = new neqsim.process.equipment.compressor.Compressor(
        "1st stage compressor", firstStageScrubber.getGasOutStream());
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.8);
    firstStageCompressor.setOutletPressure(7.0, "bara");
    firstStageCompressor.run();

    Mixer firststagegasmixer = new neqsim.process.equipment.mixer.Mixer("first stage mixer");
    firststagegasmixer.addStream(firstStageCompressor.getOutletStream());
    firststagegasmixer.addStream(thirdStageSeparator.getGasOutStream());
    firststagegasmixer.run();

    Cooler firstStageCooler2 = new neqsim.process.equipment.heatexchanger.Cooler(
        "1st stage cooler2", firststagegasmixer.getOutletStream());
    firstStageCooler2.setOutTemperature(28.9, "C");
    firstStageCooler2.run();

    Separator firstStageScrubber2 = new neqsim.process.equipment.separator.Separator(
        "1st stage scrubber2", firstStageCooler2.getOutStream());
    firstStageScrubber2.run();

    Compressor firstStageCompressor2 = new neqsim.process.equipment.compressor.Compressor(
        "2nd stage compressor", firstStageScrubber2.getGasOutStream());
    firstStageCompressor2.setUsePolytropicCalc(true);
    firstStageCompressor2.setPolytropicEfficiency(0.8);
    firstStageCompressor2.setOutletPressure(20.0, "bara");
    firstStageCompressor2.run();

    Mixer secondstagegasmixer = new neqsim.process.equipment.mixer.Mixer("second Stage mixer");
    secondstagegasmixer.addStream(firstStageCompressor2.getOutletStream());
    secondstagegasmixer.addStream(secondStageSeparator.getGasOutStream());
    secondstagegasmixer.run();

    Cooler secondStageCooler = new neqsim.process.equipment.heatexchanger.Cooler("2nd stage cooler",
        secondstagegasmixer.getOutStream());
    secondStageCooler.setOutTemperature(29.0, "C");
    secondStageCooler.run();

    Separator secondStageScrubber = new neqsim.process.equipment.separator.Separator(
        "2nd stage scrubber", secondStageCooler.getOutletStream());
    secondStageScrubber.run();

    Compressor secondStageCompressor = new neqsim.process.equipment.compressor.Compressor(
        "3rd stage compressor", secondStageScrubber.getGasOutStream());
    secondStageCompressor.setUsePolytropicCalc(true);
    secondStageCompressor.setPolytropicEfficiency(0.8);
    secondStageCompressor.setOutletPressure(62.0, "bara");
    secondStageCompressor.run();

    Mixer richGasMixer = new neqsim.process.equipment.mixer.Mixer("fourth Stage mixer");
    richGasMixer.addStream(firstStageSeparator.getGasOutStream());
    richGasMixer.addStream(secondStageCompressor.getOutStream());
    richGasMixer.run();

    Cooler dewPointControlCooler = new neqsim.process.equipment.heatexchanger.Cooler(
        "dew point cooler", richGasMixer.getOutStream());
    dewPointControlCooler.setOutTemperature(29.0, "C");
    dewPointControlCooler.run();

    // dewPointControlCooler.getOutStream().getFluid().prettyPrint();
    Assertions.assertEquals(0.9964199113579,
        dewPointControlCooler.getOutStream().getFluid().getBeta(), 1e-6);
    Separator dewPointScrubber = new neqsim.process.equipment.separator.Separator(
        "dew point scrubber", dewPointControlCooler.getOutStream());
    dewPointScrubber.run();

    Cooler dewPointControlCooler2 = new neqsim.process.equipment.heatexchanger.Cooler(
        "dew point cooler 2", dewPointScrubber.getGasOutStream());
    dewPointControlCooler2.setOutTemperature(-15.0, "C");
    dewPointControlCooler2.setOutPressure(59.5, "bara");
    dewPointControlCooler2.run();
    Assertions.assertEquals(0.96737504327,
        dewPointControlCooler2.getOutStream().getFluid().getBeta(), 1e-6);
    Separator dewPointScrubber2 = new neqsim.process.equipment.separator.Separator(
        "dew point scrubber 2", dewPointControlCooler2.getOutStream());
    dewPointScrubber2.run();

    // dewPointScrubber2.getFluid().prettyPrint();

    Mixer hpLiqmixer = new neqsim.process.equipment.mixer.Mixer("HP liq gas mixer");
    hpLiqmixer.addStream(dewPointScrubber.getLiquidOutStream());
    hpLiqmixer.run();

    Mixer mpLiqmixer = new neqsim.process.equipment.mixer.Mixer("MP liq gas mixer");
    mpLiqmixer.addStream(secondStageScrubber.getLiquidOutStream());
    mpLiqmixer.run();

    Mixer lpLiqmixer = new neqsim.process.equipment.mixer.Mixer("LP liq gas mixer");
    lpLiqmixer.addStream(firststagescrubberpump.getOutletStream());
    lpLiqmixer.addStream(firstStageScrubber2.getLiquidOutStream());
    lpLiqmixer.run();

    Recycle hpResycle = new neqsim.process.equipment.util.Recycle("HP liq resycle");
    hpResycle.addStream(hpLiqmixer.getOutStream());
    hpResycle.setOutletStream(oilFirstStage);
    hpResycle.setTolerance(1e-2);
    hpResycle.run();

    Recycle mpResycle = new neqsim.process.equipment.util.Recycle("MP liq resycle");
    mpResycle.addStream(mpLiqmixer.getOutStream());
    mpResycle.setOutletStream(oilSeccondStage);
    mpResycle.setTolerance(1e-2);
    mpResycle.run();

    Recycle lpResycle = new neqsim.process.equipment.util.Recycle("LP liq resycle");
    lpResycle.addStream(lpLiqmixer.getOutStream());
    lpResycle.setOutletStream(oilThirdStage);
    lpResycle.setTolerance(1e-2);
    lpResycle.run();

    Expander turboexpander =
        new neqsim.process.equipment.expander.Expander("TEX", dewPointScrubber2.getGasOutStream());
    turboexpander.setIsentropicEfficiency(0.80);
    turboexpander.setOutletPressure(50.0);
    turboexpander.run();
    // turboexpander.getFluid().prettyPrint();

    Separator DPCUScrubber = new neqsim.process.equipment.separator.Separator("TEX LT scrubber",
        turboexpander.getOutStream());
    DPCUScrubber.run();

    // DPCUScrubber.getFluid().prettyPrint();
    // richGasMixer.getOutStream().getFluid().prettyPrint();
    /*
     * hpResycle = jneqsim.process.equipment.util.Recycle("HP liq resycle")
     * hpResycle.addStream(hpLiqmixer.getOutStream()) hpResycle.setOutletStream(oilFirstStage)
     * hpResycle.setTolerance(1e-2)
     * 
     * mpResycle = jneqsim.process.equipment.util.Recycle("MP liq resycle")
     * mpResycle.addStream(mpLiqmixer.getOutStream()) mpResycle.setOutletStream(oilSeccondStage)
     * mpResycle.setTolerance(1e-2)
     * 
     * lpResycle = jneqsim.process.equipment.util.Recycle("LP liq resycle")
     * lpResycle.addStream(lpLiqmixer.getOutStream()) lpResycle.setOutletStream(oilThirdStage)
     * lpResycle.setTolerance(1e-2)
     * 
     * #expander_energy_stream = jneqsim.process.equipment.stream.EnergyStream("expander energy");
     * 
     * turboexpander = jneqsim.process.equipment.compressor.Compressor("TEX",
     * dewPointScrubber2.getGasOutStream()) turboexpander.setIsentropicEfficiency(0.80)
     * #turboexpander.setUsePolytropicCalc(True) #turboexpander.setOutletPressure(55.0, 'bara')
     * #turboexpander.setEnergyStream(expander_energy_stream)
     * 
     * /* 'moleRateHP': HP1, 'moleRateLP': LP1, #'flowFirstStage': 10.0, #'flowSecondStage': 5.0,
     * 'gasCoolerTemperature': 28.0, 'export_gas_temperature': 55.0, 'export_gas_pressure': 126.5,
     * 'firstStagePressure': 62.0, 'firstStageTemperature': 67.0, 'secondStagePressure': 20.0,
     * 'secondStageTemperature': 80.0, 'thirdStagePressure': 6.0, 'fourthStagePressure': 2.0,
     * 'export_oil_temperature': 20.0, 'export_oil_pressure': 20.8, 'dewPointScrubberTemperature':
     * 24.0, 'dewPointScrubberPressure': 60.6, 'inlet_tex_temperature': -5.0, 'inlet_tex_pressure':
     * 59.6, 'outlet_tex_pressure': 50.0, 'pre_flash_temperature': 0.0, 'pre_flash_pressure' :20.6,
     * 'nglColumnTopPressure': 7.3, 'nglColumnBottomPressure' :7.3, 'nglColumnBottomTemperature'
     * :75.0, 'ngl_routing_to_oil': 0.1, 'TEXcompOutPressure': 47.0, 'KA27831A_outpressure': 95.3,
     * 'KA27831B_outpressure':95.3, 'KA27841A_outpressure': 150.5, 'KA27841B_outpressure':150.5,
     */
  }
}
