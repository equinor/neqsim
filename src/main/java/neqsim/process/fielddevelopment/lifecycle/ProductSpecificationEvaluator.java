package neqsim.process.fielddevelopment.lifecycle;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.HydrocarbonDewPointAnalyser;
import neqsim.process.measurementdevice.WaterDewPointAnalyser;
import neqsim.standards.gasquality.Standard_ISO6976_2016;
import neqsim.standards.oilquality.Standard_BSW;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/** Evaluates gas export, stabilized oil, and produced-water quality using live NeqSim streams. */
public final class ProductSpecificationEvaluator {
  private static final double MINIMUM_FLOW_KG_PER_SECOND = 1.0e-9;

  /** Evaluates configured limits using streams exposed by a lifecycle model. */
  public ProductSpecificationResult evaluate(FieldLifecycleModel model,
      FieldProductSpecifications specifications) {
    return evaluate(model, specifications, Double.NaN);
  }

  /**
   * Evaluates configured limits with an optional measured oil-in-water concentration.
   *
   * @param model solved lifecycle model
   * @param specifications limits to evaluate
   * @param measuredOilInWaterMgPerL measured treated-water OIW, or NaN to derive it from the water stream
   * @return measured compliance result
   */
  public ProductSpecificationResult evaluate(FieldLifecycleModel model,
      FieldProductSpecifications specifications, double measuredOilInWaterMgPerL) {
    if (model == null || specifications == null) {
      return ProductSpecificationResult.notEvaluated();
    }
    List<String> violations = new ArrayList<String>();
    double gasCo2 = Double.NaN;
    double gasH2s = Double.NaN;
    double gasOxygen = Double.NaN;
    double waterDewPoint = Double.NaN;
    double hydrocarbonDewPoint = Double.NaN;
    double gasGrossCalorificValue = Double.NaN;
    double gasWobbeIndex = Double.NaN;
    double gasRelativeDensity = Double.NaN;
    double oilRvp = Double.NaN;
    double oilBsw = Double.NaN;
    double oilInWater = Double.NaN;

    StreamInterface gasExport = model.getGasExport();
    boolean gasExporting = hasFlow(gasExport);
    if (gasExporting) {
      gasCo2 = gasMoleConcentration(gasExport, "CO2", 100.0);
      gasH2s = gasMoleConcentration(gasExport, "H2S", 1.0e6);
      gasOxygen = gasMoleConcentration(gasExport, "oxygen", 100.0);
      if (Double.isFinite(specifications.getMaximumGasWaterDewPointC())) {
        waterDewPoint = calculateWaterDewPoint(gasExport,
            specifications.getGasDewPointReferencePressureBara(), violations);
      }
      if (Double.isFinite(specifications.getMaximumGasHydrocarbonDewPointC())) {
        hydrocarbonDewPoint = calculateHydrocarbonDewPoint(gasExport,
            specifications.getGasDewPointReferencePressureBara(), violations);
      }
      if (hasGasEnergyLimits(specifications)) {
        double[] energyQuality = calculateGasEnergyQuality(gasExport, violations);
        gasGrossCalorificValue = energyQuality[0];
        gasWobbeIndex = energyQuality[1];
        gasRelativeDensity = energyQuality[2];
      }
    }

    StreamInterface oilExport = model.getStabilizedOilExport();
    boolean oilExporting = hasFlow(oilExport);
    if (oilExporting) {
      if (Double.isFinite(specifications.getMaximumOilRvpBara())) {
        oilRvp = calculateOilRvp(oilExport, violations);
      }
      if (Double.isFinite(specifications.getMaximumOilBswVolumePercent())) {
        oilBsw = calculateOilBsw(oilExport, violations);
      }
    }

    if (Double.isFinite(specifications.getMaximumOilInWaterMgPerL())) {
      oilInWater = Double.isNaN(measuredOilInWaterMgPerL)
          ? calculateOilInWater(model.getTreatedWaterDischarge(), violations)
          : measuredOilInWaterMgPerL;
    }

    if (gasExporting) {
      checkMaximum("gas CO2", gasCo2, specifications.getMaximumGasCo2MolePercent(), "mol%",
          violations);
      checkMaximum("gas H2S", gasH2s, specifications.getMaximumGasH2sPpm(), "ppm", violations);
      checkMaximum("gas oxygen", gasOxygen, specifications.getMaximumGasOxygenMolePercent(),
          "mol%", violations);
      checkMaximum("gas water dew point", waterDewPoint,
          specifications.getMaximumGasWaterDewPointC(), "C", violations);
      checkMaximum("gas hydrocarbon dew point", hydrocarbonDewPoint,
          specifications.getMaximumGasHydrocarbonDewPointC(), "C", violations);
      checkRange("gas gross calorific value", gasGrossCalorificValue,
          specifications.getMinimumGasGrossCalorificValueMjPerSm3(),
          specifications.getMaximumGasGrossCalorificValueMjPerSm3(), "MJ/Sm3", violations);
      checkRange("gas Wobbe index", gasWobbeIndex,
          specifications.getMinimumGasWobbeIndexMjPerSm3(),
          specifications.getMaximumGasWobbeIndexMjPerSm3(), "MJ/Sm3", violations);
      checkMaximum("gas relative density", gasRelativeDensity,
          specifications.getMaximumGasRelativeDensity(), "-", violations);
    }
    if (oilExporting) {
      checkMaximum("oil RVP", oilRvp, specifications.getMaximumOilRvpBara(), "bara", violations);
      checkMaximum("oil BS&W", oilBsw, specifications.getMaximumOilBswVolumePercent(), "vol%",
          violations);
    }
    checkMaximum("oil in water", oilInWater, specifications.getMaximumOilInWaterMgPerL(), "mg/L", violations);

    return new ProductSpecificationResult(specifications.hasActiveLimits(), gasCo2, gasH2s,
        gasOxygen, waterDewPoint, hydrocarbonDewPoint, gasGrossCalorificValue, gasWobbeIndex,
        gasRelativeDensity, oilRvp, oilBsw, oilInWater, violations);
  }

  private static boolean hasFlow(StreamInterface stream) {
    return stream != null && stream.getFluid() != null
        && stream.getFlowRate("kg/sec") > MINIMUM_FLOW_KG_PER_SECOND;
  }

  private static double gasMoleConcentration(StreamInterface stream, String component,
      double multiplier) {
    SystemInterface fluid = stream.getFluid();
    if (!fluid.hasComponent(component)) {
      return 0.0;
    }
    PhaseInterface phase = fluid.hasPhaseType("gas") ? fluid.getPhase("gas") : fluid.getPhase(0);
    return phase.getComponent(component).getx() * multiplier;
  }

  private static double calculateWaterDewPoint(StreamInterface stream, double pressureBara,
      List<String> violations) {
    try {
      if (!stream.getFluid().hasComponent("water")) {
        return Double.NEGATIVE_INFINITY;
      }
      WaterDewPointAnalyser analyser = new WaterDewPointAnalyser("lifecycle gas water dew point", stream);
      analyser.setReferencePressure(pressureBara);
      return analyser.getMeasuredValue("C");
    } catch (RuntimeException ex) {
      violations.add("gas water dew point could not be calculated");
      return Double.NaN;
    }
  }

  private static double calculateHydrocarbonDewPoint(StreamInterface stream, double pressureBara,
      List<String> violations) {
    try {
      HydrocarbonDewPointAnalyser analyser =
          new HydrocarbonDewPointAnalyser("lifecycle gas hydrocarbon dew point", stream);
      analyser.setReferencePressure(pressureBara);
      return analyser.getMeasuredValue("C");
    } catch (RuntimeException ex) {
      violations.add("gas hydrocarbon dew point could not be calculated");
      return Double.NaN;
    }
  }

  private static boolean hasGasEnergyLimits(FieldProductSpecifications specifications) {
    return Double.isFinite(specifications.getMinimumGasGrossCalorificValueMjPerSm3())
        || Double.isFinite(specifications.getMaximumGasGrossCalorificValueMjPerSm3())
        || Double.isFinite(specifications.getMinimumGasWobbeIndexMjPerSm3())
        || Double.isFinite(specifications.getMaximumGasWobbeIndexMjPerSm3())
        || Double.isFinite(specifications.getMaximumGasRelativeDensity());
  }

  private static double[] calculateGasEnergyQuality(StreamInterface stream,
      List<String> violations) {
    try {
      Standard_ISO6976_2016 standard =
          new Standard_ISO6976_2016(stream.getFluid(), 15.0, 25.0, "volume");
      standard.setReferenceState("real");
      standard.setReferenceType("volume");
      standard.calculate();
      return new double[] { standard.getValue("GCV") / 1000.0,
          standard.getValue("SuperiorWobbeIndex") / 1000.0,
          standard.getValue("RelativeDensity") };
    } catch (RuntimeException ex) {
      violations.add("gas energy quality could not be calculated");
      return new double[] { Double.NaN, Double.NaN, Double.NaN };
    }
  }

  private static double calculateOilRvp(StreamInterface stream, List<String> violations) {
    try {
      return stream.getRVP(37.8, "C", "bara");
    } catch (RuntimeException ex) {
      violations.add("oil RVP could not be calculated");
      return Double.NaN;
    }
  }

  private static double calculateOilBsw(StreamInterface stream, List<String> violations) {
    try {
      Standard_BSW standard = new Standard_BSW(stream.getFluid());
      standard.calculate();
      return standard.getValue("BSW");
    } catch (RuntimeException ex) {
      violations.add("oil BS&W could not be calculated");
      return Double.NaN;
    }
  }

  private static double calculateOilInWater(StreamInterface stream, List<String> violations) {
    if (!hasFlow(stream)) {
      return 0.0;
    }
    try {
      SystemInterface fluid = stream.getFluid();
      if (!fluid.hasPhaseType("aqueous")) {
        violations.add("treated-water stream has no aqueous phase");
        return Double.NaN;
      }
      PhaseInterface aqueous = fluid.getPhase("aqueous");
      double hydrocarbonWeightFraction = 0.0;
      for (int component = 0; component < aqueous.getNumberOfComponents(); component++) {
        if (aqueous.getComponent(component).isHydrocarbon()
            || aqueous.getComponent(component).isIsTBPfraction()) {
          hydrocarbonWeightFraction += aqueous.getWtFrac(component);
        }
      }
      return hydrocarbonWeightFraction * aqueous.getDensity("kg/m3") * 1000.0;
    } catch (RuntimeException ex) {
      violations.add("oil-in-water concentration could not be calculated");
      return Double.NaN;
    }
  }

  private static void checkMaximum(String name, double measured, double maximum, String unit,
      List<String> violations) {
    if (!Double.isFinite(maximum)) {
      return;
    }
    if (Double.isNaN(measured)) {
      if (!containsMeasurementFailure(violations, name)) {
        violations.add(name + " was not measured");
      }
    } else if (measured > maximum + 1.0e-12) {
      violations.add(String.format("%s %.3f %s > %.3f %s", name, measured, unit, maximum, unit));
    }
  }

  private static void checkRange(String name, double measured, double minimum, double maximum,
      String unit, List<String> violations) {
    if (!Double.isFinite(minimum) && !Double.isFinite(maximum)) {
      return;
    }
    if (Double.isNaN(measured)) {
      if (!containsMeasurementFailure(violations, name)) {
        violations.add(name + " was not measured");
      }
    } else if (Double.isFinite(minimum) && measured < minimum - 1.0e-12) {
      violations.add(String.format("%s %.3f %s < %.3f %s", name, measured, unit, minimum, unit));
    } else if (Double.isFinite(maximum) && measured > maximum + 1.0e-12) {
      violations.add(String.format("%s %.3f %s > %.3f %s", name, measured, unit, maximum, unit));
    }
  }

  private static boolean containsMeasurementFailure(List<String> violations, String name) {
    for (String violation : violations) {
      if (violation.startsWith(name)) {
        return true;
      }
    }
    return false;
  }
}
