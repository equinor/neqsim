package neqsim.process.controllerdevice;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Integration style test that builds an offshore separation train similar to the Python example
 * shared by the user and exercises the {@link ModelPredictiveController} in multivariable mode.
 * <p>
 * The MPC optimises three manipulated variables – dew point control temperature, stabiliser heater
 * duty and export compression pressure – to reduce energy usage while keeping gas production and
 * product qualities (Wobbe Index for gas and RVP for oil) within specification.
 */
public class OffshoreProcessMpcIntegrationTest extends neqsim.NeqSimTest {
  private static final double GAS_REFERENCE_T = 15.0; // degC
  private static final double GAS_REFERENCE_COMBUSTION_T = 15.0; // degC
  private static final double OIL_RVP_TEMPERATURE = 37.8; // degC

  /** Measurement device returning the negative gas production rate in tonne/hr. */
  private static final class GasProductionMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private final StreamInterface stream;

    GasProductionMeasurement(String name, StreamInterface stream) {
      super(name, "tonne/hr");
      this.stream = stream;
    }

    @Override
    public double getMeasuredValue() {
      return -stream.getFlowRate("kg/hr") / 1000.0;
    }

    @Override
    public double getMeasuredValue(String unit) {
      if (unit == null || unit.isEmpty() || unit.equals(getUnit())) {
        return getMeasuredValue();
      }
      throw new IllegalArgumentException(
          "Unsupported unit for gas production measurement: " + unit);
    }
  }

  /** Measurement device returning the negative Wobbe Index in MJ/Sm3. */
  private static final class WobbeQualityMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private final StreamInterface stream;

    WobbeQualityMeasurement(String name, StreamInterface stream) {
      super(name, "MJ/Sm3");
      this.stream = stream;
    }

    @Override
    public double getMeasuredValue() {
      Standard_ISO6976 standard = new Standard_ISO6976(stream.getThermoSystem().clone(),
          GAS_REFERENCE_T, GAS_REFERENCE_COMBUSTION_T, "volume");
      standard.setReferenceState("real");
      standard.calculate();
      double wobbe = standard.getValue("SuperiorWobbeIndex") / 1.0e3;
      return -wobbe;
    }

    @Override
    public double getMeasuredValue(String unit) {
      if (unit == null || unit.isEmpty() || unit.equals(getUnit())) {
        return getMeasuredValue();
      }
      throw new IllegalArgumentException("Unsupported unit for Wobbe measurement: " + unit);
    }
  }

  /** Measurement device returning Reid Vapour Pressure in bara. */
  private static final class OilRvpMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private final StreamInterface stream;

    OilRvpMeasurement(String name, StreamInterface stream) {
      super(name, "bara");
      this.stream = stream;
    }

    @Override
    public double getMeasuredValue() {
      return stream.getRVP(OIL_RVP_TEMPERATURE, "C", "bara");
    }

    @Override
    public double getMeasuredValue(String unit) {
      if (unit == null || unit.isEmpty() || unit.equals(getUnit())) {
        return getMeasuredValue();
      }
      throw new IllegalArgumentException("Unsupported unit for RVP measurement: " + unit);
    }
  }

  /** Container for the full offshore process model. */
  private static final class OffshoreProcess {
    final ProcessSystem system;
    final Cooler dewPointCooler;
    final Heater oilHeater2;
    final Compressor exportCompressor2;
    final Compressor exportCompressor1;
    final Compressor firstStageCompressor;
    final Compressor secondStageCompressor;
    final Pump recyclePump;
    final Heater oilHeater1;
    final Heater oilHeatEx;
    final Stream salesGas;
    final Stream stableOil;
    final Stream lpg;

    OffshoreProcess(ProcessSystem system, Cooler dewPointCooler, Heater oilHeater2,
        Compressor exportCompressor2, Compressor exportCompressor1, Compressor firstStageCompressor,
        Compressor secondStageCompressor, Pump recyclePump, Heater oilHeater1, Heater oilHeatEx,
        Stream salesGas, Stream stableOil, Stream lpg) {
      this.system = system;
      this.dewPointCooler = dewPointCooler;
      this.oilHeater2 = oilHeater2;
      this.exportCompressor2 = exportCompressor2;
      this.exportCompressor1 = exportCompressor1;
      this.firstStageCompressor = firstStageCompressor;
      this.secondStageCompressor = secondStageCompressor;
      this.recyclePump = recyclePump;
      this.oilHeater1 = oilHeater1;
      this.oilHeatEx = oilHeatEx;
      this.salesGas = salesGas;
      this.stableOil = stableOil;
      this.lpg = lpg;
    }
  }

  @Test
  public void testMpcOptimisesEnergyWhileMeetingQuality() {
    SystemInterface fluid = createWellFluid();
    Map<String, Double> inputs = createInputData();
    OffshoreProcess process = buildProcess(fluid, inputs);

    process.system.run();

    double baselineEnergy = totalCompressionEnergy(process);
    double baselineGas = salesGasRate(process.salesGas);
    double baselineWobbe = wobbeIndex(process.salesGas);
    double baselineRvp = process.stableOil.getRVP(OIL_RVP_TEMPERATURE, "C", "bara");

    GasProductionMeasurement gasMeasurement =
        new GasProductionMeasurement("sales gas production", process.salesGas);
    WobbeQualityMeasurement wobbeMeasurement =
        new WobbeQualityMeasurement("sales gas wobbe", process.salesGas);
    OilRvpMeasurement rvpMeasurement =
        new OilRvpMeasurement("stabilised oil rvp", process.stableOil);

    ModelPredictiveController controller = new ModelPredictiveController("fpsoMpc");
    controller.configureControls("dewPointTemperature", "oilHeaterTemperature", "exportPressure");

    double[] initialControls = new double[] {inputs.get("dewPointSuctionCoolerTemperature"),
        inputs.get("temperatureOilHeater2"), inputs.get("exportCompressorStage2")};
    controller.setInitialControlValues(initialControls);
    controller.setPreferredControlVector(new double[] {initialControls[0] - 2.0,
        initialControls[1] - 3.0, initialControls[2] - 10.0});
    controller.setControlLimits("dewPointTemperature", 0.0, 40.0);
    controller.setControlLimits("oilHeaterTemperature", 40.0, 90.0);
    controller.setControlLimits("exportPressure", 200.0, 260.0);
    controller.setControlWeights(new double[] {0.05, 0.05, 3.0});
    controller.setMoveWeights(new double[] {0.01, 0.01, 0.2});

    double baseGasMeasurement = gasMeasurement.getMeasuredValue();
    double baseWobbeMeasurement = wobbeMeasurement.getMeasuredValue();
    double baseRvpMeasurement = rvpMeasurement.getMeasuredValue();

    double[][] sensitivities = computeControlSensitivities(process, initialControls, gasMeasurement,
        wobbeMeasurement, rvpMeasurement);

    double gasFlowTarget = -baseGasMeasurement * 0.97; // tonne/hr
    double wobbeSpecification = -baseWobbeMeasurement - 0.05; // MJ/Sm3
    double rvpSpecification = baseRvpMeasurement * 1.02; // bara

    controller.addQualityConstraint(ModelPredictiveController.QualityConstraint.builder("gasRate")
        .measurement(gasMeasurement).unit("tonne/hr").limit(-gasFlowTarget).margin(1.0)
        .controlSensitivity(sensitivities[0]).build());
    controller.addQualityConstraint(ModelPredictiveController.QualityConstraint.builder("wobbe")
        .measurement(wobbeMeasurement).unit("MJ/Sm3").limit(-wobbeSpecification).margin(0.05)
        .controlSensitivity(sensitivities[1]).build());
    controller.addQualityConstraint(ModelPredictiveController.QualityConstraint.builder("rvp")
        .measurement(rvpMeasurement).unit("bara").limit(rvpSpecification).margin(0.05)
        .controlSensitivity(sensitivities[2]).build());

    double[] currentControls = Arrays.copyOf(initialControls, initialControls.length);
    for (int iteration = 0; iteration < 2; iteration++) {
      controller.setInitialControlValues(currentControls);
      controller.runTransient(Double.NaN, 1.0, UUID.randomUUID());
      double[] recommendation = controller.getControlVector();
      applyControl(process, 0, recommendation[0]);
      applyControl(process, 1, recommendation[1]);
      applyControl(process, 2, recommendation[2]);
      process.system.run();
      currentControls = Arrays.copyOf(recommendation, recommendation.length);
    }

    double optimisedEnergy = totalCompressionEnergy(process);
    double optimisedGas = salesGasRate(process.salesGas);
    double optimisedWobbe = wobbeIndex(process.salesGas);
    double optimisedRvp = process.stableOil.getRVP(OIL_RVP_TEMPERATURE, "C", "bara");

    Assertions.assertTrue(optimisedEnergy < baselineEnergy,
        "MPC should reduce total compression and pumping duty");
    Assertions.assertTrue(optimisedGas >= gasFlowTarget,
        "Sales gas rate must stay above contractual target");
    Assertions.assertTrue(optimisedWobbe >= wobbeSpecification,
        "Gas Wobbe Index must satisfy specification");
    Assertions.assertTrue(optimisedRvp <= rvpSpecification,
        "Stabilised oil RVP must satisfy specification");
  }

  private double[][] computeControlSensitivities(OffshoreProcess process, double[] baseControls,
      GasProductionMeasurement gasMeasurement, WobbeQualityMeasurement wobbeMeasurement,
      OilRvpMeasurement rvpMeasurement) {
    double[] baseValues = new double[] {gasMeasurement.getMeasuredValue(),
        wobbeMeasurement.getMeasuredValue(), rvpMeasurement.getMeasuredValue()};

    double[][] sensitivities = new double[3][baseControls.length];
    double[] stepSizes = new double[] {1.0, 1.0, 5.0};

    for (int controlIndex = 0; controlIndex < baseControls.length; controlIndex++) {
      double original = baseControls[controlIndex];
      double step = stepSizes[controlIndex];
      applyControl(process, controlIndex, original + step);
      process.system.run();

      double gasValue = gasMeasurement.getMeasuredValue();
      double wobbeValue = wobbeMeasurement.getMeasuredValue();
      double rvpValue = rvpMeasurement.getMeasuredValue();

      sensitivities[0][controlIndex] = (gasValue - baseValues[0]) / step;
      sensitivities[1][controlIndex] = (wobbeValue - baseValues[1]) / step;
      sensitivities[2][controlIndex] = (rvpValue - baseValues[2]) / step;

      applyControl(process, controlIndex, original);
      process.system.run();
    }

    return sensitivities;
  }

  private void applyControl(OffshoreProcess process, int controlIndex, double value) {
    switch (controlIndex) {
      case 0:
        process.dewPointCooler.setOutTemperature(value, "C");
        break;
      case 1:
        process.oilHeater2.setOutTemperature(value, "C");
        break;
      case 2:
        process.exportCompressor2.setOutletPressure(value);
        break;
      default:
        throw new IllegalArgumentException("Unsupported control index " + controlIndex);
    }
  }

  private double totalCompressionEnergy(OffshoreProcess process) {
    double compressorPower =
        process.firstStageCompressor.getPower("MW") + process.secondStageCompressor.getPower("MW")
            + process.exportCompressor1.getPower("MW") + process.exportCompressor2.getPower("MW");
    double pumpPower = process.recyclePump.getPower("MW");
    return compressorPower + pumpPower;
  }

  private double salesGasRate(Stream salesGas) {
    return salesGas.getFlowRate("kg/hr") / 1000.0;
  }

  private double wobbeIndex(Stream salesGas) {
    Standard_ISO6976 standard = new Standard_ISO6976(salesGas.getThermoSystem().clone(),
        GAS_REFERENCE_T, GAS_REFERENCE_COMBUSTION_T, "volume");
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("SuperiorWobbeIndex") / 1.0e3;
  }

  private OffshoreProcess buildProcess(SystemInterface fluid, Map<String, Double> input) {
    ProcessSystem process = new ProcessSystem("fpso process");

    Stream wellFluid = new Stream("well fluid", fluid);
    wellFluid.setFlowRate(1.909e6, "kg/hr");
    wellFluid.setTemperature(input.get("topsideTemperature"), "C");
    wellFluid.setPressure(input.get("topsidePressure"), "bara");

    Heater feedTpSetter = new Heater("inletTP", wellFluid);
    feedTpSetter.setOutPressure(input.get("topsidePressure"));
    feedTpSetter.setOutTemperature(input.get("topsideTemperature"), "C");

    Stream feedToProcess = new Stream("feed to offshore", feedTpSetter.getOutletStream());

    ThrottlingValve inletValve = new ThrottlingValve("feed valve", feedToProcess);
    inletValve.setOutletPressure(input.get("firstStagePressure"));

    Separator firstStageSeparator =
        new Separator("1st stage separator", inletValve.getOutletStream());

    ThrottlingValve oilValve1 =
        new ThrottlingValve("valve oil from first stage", firstStageSeparator.getLiquidOutStream());
    oilValve1.setOutletPressure(input.get("secondStagePressure"));

    Mixer recompressionLiqMixer = new Mixer("recompression liquids mixer");
    recompressionLiqMixer.addStream(oilValve1.getOutletStream());

    Heater oilHeatEx = new Heater("oil heat exchanger", recompressionLiqMixer.getOutletStream());
    oilHeatEx.setOutTemperature(input.get("temperatureOilHeaterEx"), "C");

    Heater oilHeater1 = new Heater("oil heater 1", oilHeatEx.getOutletStream());
    oilHeater1.setOutTemperature(input.get("temperatureOilHeater1"), "C");

    Separator secondStageSeparator =
        new Separator("2nd stage separator", oilHeater1.getOutletStream());

    ThrottlingValve oilValve2 = new ThrottlingValve("valve oil from second stage",
        secondStageSeparator.getLiquidOutStream());
    oilValve2.setOutletPressure(input.get("thirdStagePressure"));

    Heater oilHeater2 = new Heater("oil heater 2", oilValve2.getOutletStream());
    oilHeater2.setOutTemperature(input.get("temperatureOilHeater2"), "C");

    Separator thirdStageSeparator =
        new Separator("third stage separator", oilHeater2.getOutletStream());

    Stream stableOil = new Stream("stable oil", thirdStageSeparator.getLiquidOutStream());

    Cooler firstStageCooler = new Cooler("1st stage cooler", thirdStageSeparator.getGasOutStream());
    firstStageCooler.setOutTemperature(input.get("firstStageSuctionCoolerTemperature"), "C");

    Separator firstStageScrubber =
        new Separator("1st stage scrubber", firstStageCooler.getOutletStream());

    Compressor firstStageCompressor =
        new Compressor("1st stage compressor", firstStageScrubber.getGasOutStream());
    firstStageCompressor.setOutletPressure(input.get("secondStagePressure"));
    firstStageCompressor.setIsentropicEfficiency(0.75);

    Mixer secondStageGasMixer = new Mixer("2nd stage mixer");
    secondStageGasMixer.addStream(secondStageSeparator.getGasOutStream());
    secondStageGasMixer.addStream(firstStageCompressor.getOutletStream());

    Cooler secondStageCooler =
        new Cooler("2nd stage cooler", secondStageGasMixer.getOutletStream());
    secondStageCooler.setOutTemperature(input.get("secondStageSuctionCoolerTemperature"), "C");

    Separator secondStageScrubber =
        new Separator("2nd stage scrubber", secondStageCooler.getOutletStream());

    Compressor secondStageCompressor =
        new Compressor("2nd stage compressor", secondStageScrubber.getGasOutStream());
    secondStageCompressor.setOutletPressure(input.get("firstStagePressure"));
    secondStageCompressor.setIsentropicEfficiency(0.75);

    Mixer richGasMixer = new Mixer("fourth stage mixer");
    richGasMixer.addStream(secondStageCompressor.getOutletStream());
    richGasMixer.addStream(firstStageSeparator.getGasOutStream());

    Cooler dewPointCooler = new Cooler("dew point cooler", richGasMixer.getOutletStream());
    dewPointCooler.setOutTemperature(input.get("dewPointSuctionCoolerTemperature"), "C");

    Separator dewPointScrubber =
        new Separator("dewpoint scrubber", dewPointCooler.getOutletStream());

    Stream oilThirdStageRecycle = wellFluid.clone("oil third stage recycle");
    oilThirdStageRecycle.setFlowRate(1.0, "kg/hr");
    oilThirdStageRecycle.setTemperature(input.get("temperatureOilHeater2"), "C");
    oilThirdStageRecycle.setPressure(input.get("secondStagePressure"), "bara");
    recompressionLiqMixer.addStream(oilThirdStageRecycle);

    Stream gasRecycleSeed = wellFluid.clone("gas recycle seed");
    gasRecycleSeed.setFlowRate(1.0, "kg/hr");
    gasRecycleSeed.setTemperature(input.get("secondStageSuctionCoolerTemperature"), "C");
    gasRecycleSeed.setPressure(input.get("secondStagePressure"), "bara");
    firstStageScrubber.addStream(gasRecycleSeed);

    Mixer lpLiquidMixer = new Mixer("LP liq mixer");
    lpLiquidMixer.addStream(firstStageScrubber.getLiquidOutStream());
    lpLiquidMixer.addStream(secondStageScrubber.getLiquidOutStream());
    lpLiquidMixer.addStream(dewPointScrubber.getLiquidOutStream());

    Separator pumpSeparator = new Separator("pre pump separator", lpLiquidMixer.getOutletStream());

    Pump recyclePump = new Pump("pump1", pumpSeparator.getLiquidOutStream());
    recyclePump.setOutletPressure(input.get("secondStagePressure"));

    Recycle liquidRecycle = new Recycle("recycle liquids");
    liquidRecycle.addStream(recyclePump.getOutletStream());
    liquidRecycle.setOutletStream(oilThirdStageRecycle);
    liquidRecycle.setTolerance(1e-2);

    Recycle gasRecycle = new Recycle("recycle gas");
    gasRecycle.addStream(pumpSeparator.getGasOutStream());
    gasRecycle.setOutletStream(gasRecycleSeed);
    gasRecycle.setTolerance(1e-2);

    Cooler heatExchangerHot1 =
        new Cooler("cross heat-exchanger1 hot side", dewPointScrubber.getGasOutStream());
    heatExchangerHot1.setOutTemperature(input.get("heatEx1HotStreamOutlet"), "C");

    Cooler heatExchangerHot2 =
        new Cooler("cross heat-exchanger2 hot side", heatExchangerHot1.getOutletStream());
    heatExchangerHot2.setOutTemperature(input.get("heatEx2HotStreamOutlet"), "C");

    Separator fourthSeparator =
        new Separator("fourth separator", heatExchangerHot2.getOutletStream());

    ThrottlingValve jtValve = new ThrottlingValve("JT valve", fourthSeparator.getGasOutStream());
    jtValve.setOutletPressure(input.get("jtOutletPressure"));

    ThrottlingValve lpgValve =
        new ThrottlingValve("LPG valve", fourthSeparator.getLiquidOutStream());
    lpgValve.setOutletPressure(input.get("jtOutletPressure"));

    Separator coldSeparator = new Separator("cold separator", jtValve.getOutletStream());

    Mixer lpgMixer = new Mixer("lpg mixer");
    lpgMixer.addStream(lpgValve.getOutletStream());
    lpgMixer.addStream(coldSeparator.getLiquidOutStream());

    Heater coldSideHeatEx1 =
        new Heater("cross heat-exchanger1 cold side", coldSeparator.getGasOutStream());
    coldSideHeatEx1.setOutTemperature(input.get("heatEx1ColdStreamOutlet"), "C");

    Heater coldSideHeatEx2 =
        new Heater("cross heat-exchanger2 cold side", lpgMixer.getOutletStream());
    coldSideHeatEx2.setOutTemperature(input.get("heatEx2ColdStreamOutlet"), "C");

    Compressor exportCompressor1 =
        new Compressor("export compressor stage 1", coldSideHeatEx1.getOutletStream());
    exportCompressor1.setOutletPressure(input.get("exportCompressorStage1"));
    exportCompressor1.setIsentropicEfficiency(0.75);

    Cooler exportCooler1 = new Cooler("export cooler 1", exportCompressor1.getOutletStream());
    exportCooler1.setOutTemperature(input.get("firstStageExportCoolerTemperature"), "C");

    Compressor exportCompressor2 =
        new Compressor("export compressor stage 2", exportCooler1.getOutletStream());
    exportCompressor2.setOutletPressure(input.get("exportCompressorStage2"));
    exportCompressor2.setIsentropicEfficiency(0.75);

    Stream salesGas = new Stream("sales gas", exportCompressor2.getOutletStream());
    Stream lpgProduct = new Stream("lpg", coldSideHeatEx2.getOutletStream());

    process.add(wellFluid);
    process.add(feedTpSetter);
    process.add(feedToProcess);
    process.add(inletValve);
    process.add(firstStageSeparator);
    process.add(oilValve1);
    process.add(recompressionLiqMixer);
    process.add(oilHeatEx);
    process.add(oilHeater1);
    process.add(secondStageSeparator);
    process.add(oilValve2);
    process.add(oilHeater2);
    process.add(thirdStageSeparator);
    process.add(stableOil);
    process.add(firstStageCooler);
    process.add(firstStageScrubber);
    process.add(gasRecycleSeed);
    process.add(firstStageCompressor);
    process.add(secondStageGasMixer);
    process.add(secondStageCooler);
    process.add(secondStageScrubber);
    process.add(secondStageCompressor);
    process.add(richGasMixer);
    process.add(dewPointCooler);
    process.add(dewPointScrubber);
    process.add(oilThirdStageRecycle);
    process.add(lpLiquidMixer);
    process.add(pumpSeparator);
    process.add(recyclePump);
    process.add(liquidRecycle);
    process.add(gasRecycle);
    process.add(heatExchangerHot1);
    process.add(heatExchangerHot2);
    process.add(fourthSeparator);
    process.add(jtValve);
    process.add(lpgValve);
    process.add(coldSeparator);
    process.add(lpgMixer);
    process.add(coldSideHeatEx1);
    process.add(coldSideHeatEx2);
    process.add(exportCompressor1);
    process.add(exportCooler1);
    process.add(exportCompressor2);
    process.add(salesGas);
    process.add(lpgProduct);

    return new OffshoreProcess(process, dewPointCooler, oilHeater2, exportCompressor2,
        exportCompressor1, firstStageCompressor, secondStageCompressor, recyclePump, oilHeater1,
        oilHeatEx, salesGas, stableOil, lpgProduct);
  }

  private SystemInterface createWellFluid() {
    SystemInterface fluid = new SystemPrEos(298.15, 100.0);
    double[] lightFractions =
        new double[] {0.59, 0.001, 66.02, 8.27, 5.0, 0.94, 1.88, 0.7, 0.812, 0.91};
    String[] lightComponents = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane",
        "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane"};
    for (int i = 0; i < lightComponents.length; i++) {
      fluid.addComponent(lightComponents[i], lightFractions[i]);
    }

    addHeavyComponent(fluid, "C7", 1.20, 0.09832, 0.737);
    addHeavyComponent(fluid, "C8", 1.25, 0.11227, 0.758);
    addHeavyComponent(fluid, "C9", 0.76, 0.12627, 0.775);
    addHeavyComponent(fluid, "C10", 0.75, 0.14689, 0.794);
    addHeavyComponent(fluid, "C11", 0.75, 0.14689, 0.794);
    addHeavyComponent(fluid, "C12", 0.65, 0.174, 0.814);
    addHeavyComponent(fluid, "C13", 0.65, 0.174, 0.814);
    addHeavyComponent(fluid, "C14", 0.565, 0.202, 0.830);
    addHeavyComponent(fluid, "C15", 0.565, 0.202, 0.830);
    double c16to18 = 1.4328 / 3.0;
    addHeavyComponent(fluid, "C16", c16to18, 0.237, 0.846);
    addHeavyComponent(fluid, "C17", c16to18, 0.237, 0.846);
    addHeavyComponent(fluid, "C18", c16to18, 0.237, 0.846);
    double c19to20 = 0.802 / 2.0;
    addHeavyComponent(fluid, "C19", c19to20, 0.272, 0.860);
    addHeavyComponent(fluid, "C20", c19to20, 0.272, 0.860);
    double c21to23 = 1.0127 / 3.0;
    addHeavyComponent(fluid, "C21", c21to23, 0.307, 0.872);
    addHeavyComponent(fluid, "C22", c21to23, 0.307, 0.872);
    addHeavyComponent(fluid, "C23", c21to23, 0.307, 0.872);
    double c24to29 = 1.4921 / 6.0;
    addHeavyComponent(fluid, "C24", c24to29, 0.367, 0.889);
    addHeavyComponent(fluid, "C25", c24to29, 0.367, 0.889);
    addHeavyComponent(fluid, "C26", c24to29, 0.367, 0.889);
    addHeavyComponent(fluid, "C27", c24to29, 0.367, 0.889);
    addHeavyComponent(fluid, "C28", c24to29, 0.367, 0.889);
    addHeavyComponent(fluid, "C29", c24to29, 0.367, 0.889);
    addHeavyComponent(fluid, "C30", 2.9922, 0.594, 0.935);

    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private void addHeavyComponent(SystemInterface fluid, String name, double fraction, double mw,
      double density) {
    fluid.addTBPfraction(name, fraction, mw, density);
  }

  private Map<String, Double> createInputData() {
    Map<String, Double> values = new LinkedHashMap<>();
    values.put("topsidePressure", 90.0);
    values.put("topsideTemperature", 46.0);
    values.put("temperatureOilHeaterEx", 34.3);
    values.put("temperatureOilHeater1", 68.0);
    values.put("temperatureOilHeater2", 63.0);
    values.put("firstStagePressure", 90.0);
    values.put("secondStagePressure", 8.0);
    values.put("thirdStagePressure", 1.5);
    values.put("firstStageSuctionCoolerTemperature", 40.0);
    values.put("secondStageSuctionCoolerTemperature", 50.0);
    values.put("dewPointSuctionCoolerTemperature", 35.0);
    values.put("firstStageExportCoolerTemperature", 35.0);
    values.put("jtOutletPressure", 20.0);
    values.put("heatEx1HotStreamOutlet", 6.56);
    values.put("heatEx2HotStreamOutlet", -3.46);
    values.put("heatEx1ColdStreamOutlet", 30.0);
    values.put("heatEx2ColdStreamOutlet", 1.56);
    values.put("exportCompressorStage1", 70.7);
    values.put("exportCompressorStage2", 250.0);
    return values;
  }
}
