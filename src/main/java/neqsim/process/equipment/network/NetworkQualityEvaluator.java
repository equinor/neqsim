package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.oilquality.Standard_ASTM_D4052;
import neqsim.standards.oilquality.Standard_ASTM_D6377;
import neqsim.standards.oilquality.Standard_TVP;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Evaluates typed network quality profiles against a thermodynamic state and governed measured attributes.
 */
public final class NetworkQualityEvaluator {
  private NetworkQualityEvaluator() {
  }

  /**
   * Evaluate a named point.
   *
   * @param nodeName named point
   * @param profile quality profile
   * @param fluid thermodynamic state
   * @param measuredAttributes governed measured/assay attributes
   * @return compliance report
   */
  public static NetworkQualityComplianceReport evaluate(String nodeName, NetworkQualityProfile profile,
      SystemInterface fluid, Map<String, NetworkMeasuredAttribute> measuredAttributes) {
    if (profile == null) {
      throw new IllegalArgumentException("Quality profile cannot be null");
    }
    Map<String, NetworkMeasuredAttribute> attributes = measuredAttributes == null
        ? Collections.<String, NetworkMeasuredAttribute>emptyMap()
        : measuredAttributes;
    List<NetworkQualityResult> results = new ArrayList<NetworkQualityResult>();
    for (NetworkQualityLimit limit : profile.getLimits()) {
      results.add(evaluateLimit(limit, fluid, attributes));
    }
    return new NetworkQualityComplianceReport(nodeName, profile, results);
  }

  private static NetworkQualityResult evaluateLimit(NetworkQualityLimit limit, SystemInterface fluid,
      Map<String, NetworkMeasuredAttribute> attributes) {
    try {
      Calculation calculation = calculate(limit, fluid, attributes);
      if (calculation == null || !Double.isFinite(calculation.value)) {
        return notCalculable(limit, "Metric is not available from the EOS state or supplied attributes");
      }
      Double margin = calculateMargin(calculation.value, limit.getLowerLimit(), limit.getUpperLimit());
      NetworkQualityResult.Status status = margin != null && margin >= 0.0 ? NetworkQualityResult.Status.PASS
          : NetworkQualityResult.Status.FAIL;
      String method = limit.getMethod() == null ? calculation.method : limit.getMethod();
      String provenance = calculation.provenance == null ? limit.getProvenance() : calculation.provenance;
      return new NetworkQualityResult(limit.getMetricKey(), limit.getAttributeName(), calculation.value,
          limit.getUnit(), limit.getReference(), limit.getLowerLimit(), limit.getUpperLimit(), margin, status, method,
          provenance,
          status == NetworkQualityResult.Status.PASS ? "Within configured limits" : "Outside configured limits");
    } catch (Exception ex) {
      return notCalculable(limit, ex.getMessage());
    }
  }

  private static NetworkQualityResult notCalculable(NetworkQualityLimit limit, String message) {
    return new NetworkQualityResult(limit.getMetricKey(), limit.getAttributeName(), null, limit.getUnit(),
        limit.getReference(), limit.getLowerLimit(), limit.getUpperLimit(), null,
        NetworkQualityResult.Status.NOT_CALCULABLE, limit.getMethod(), limit.getProvenance(), message);
  }

  private static Double calculateMargin(double value, Double lower, Double upper) {
    double margin = Double.POSITIVE_INFINITY;
    if (lower != null) {
      margin = Math.min(margin, value - lower);
    }
    if (upper != null) {
      margin = Math.min(margin, upper - value);
    }
    return Double.isInfinite(margin) ? null : margin;
  }

  private static Calculation calculate(NetworkQualityLimit limit, SystemInterface sourceFluid,
      Map<String, NetworkMeasuredAttribute> attributes) throws Exception {
    if (sourceFluid == null) {
      throw new IllegalStateException("No thermodynamic fluid is available at the named point");
    }
    String key = limit.getMetricKey();
    QualityReference reference = limit.getReference();
    SystemInterface fluid = sourceFluid.clone();
    ThermodynamicOperations initialization = new ThermodynamicOperations(fluid);
    initialization.TPflash();
    fluid.initProperties();

    if ("superiorCalorificValue".equals(key) || "inferiorCalorificValue".equals(key) || "wobbeIndex".equals(key)
        || "relativeDensity".equals(key)) {
      Standard_ISO6976 standard = new Standard_ISO6976(fluid);
      standard.setReferenceState("real");
      double volumeTemperature = reference != null && reference.getVolumeReferenceTemperatureC() != null
          ? reference.getVolumeReferenceTemperatureC()
          : 15.0;
      double combustionTemperature = reference != null && reference.getCombustionReferenceTemperatureC() != null
          ? reference.getCombustionReferenceTemperatureC()
          : 15.0;
      standard.setVolRefT(volumeTemperature);
      standard.setEnergyRefT(combustionTemperature);
      standard.calculate();
      if ("superiorCalorificValue".equals(key)) {
        return new Calculation(standard.getValue("SuperiorCalorificValue"), "ISO 6976", null);
      }
      if ("inferiorCalorificValue".equals(key)) {
        return new Calculation(standard.getValue("InferiorCalorificValue"), "ISO 6976", null);
      }
      if ("wobbeIndex".equals(key)) {
        return new Calculation(standard.getValue("SuperiorWobbeIndex") / 1.0e3, "ISO 6976", null);
      }
      return new Calculation(standard.getValue("RelativeDensity"), "ISO 6976", null);
    }

    if ("co2MolePercent".equals(key)) {
      double value = fluid.hasComponent("CO2") ? fluid.getComponent("CO2").getz() * 100.0 : 0.0;
      return new Calculation(value, "EOS composition", null);
    }
    if ("componentMolePercent".equals(key)) {
      String componentName = limit.getAttributeName();
      if (componentName == null || !fluid.hasComponent(componentName)) {
        throw new IllegalStateException("Component is not represented in the fluid: " + componentName);
      }
      return new Calculation(fluid.getComponent(componentName).getz() * 100.0, "EOS composition", null);
    }
    if ("hydrocarbonDewPointTemperature".equals(key)) {
      requirePressureReference(reference);
      fluid.setPressure(reference.getPressureBara(), "bara");
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.dewPointTemperatureFlash();
      return new Calculation(convertTemperatureFromK(fluid.getTemperature(), limit.getUnit()),
          "EOS hydrocarbon dew-point temperature flash", null);
    }
    if ("hydrocarbonDewPointPressure".equals(key)) {
      requireTemperatureReference(reference);
      fluid.setTemperature(reference.getTemperatureK(), "K");
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.dewPointPressureFlashHC();
      return new Calculation(convertPressureFromBara(fluid.getPressure("bara"), limit.getUnit()),
          "EOS upper hydrocarbon dew-point pressure flash", null);
    }
    if ("waterDewPointTemperature".equals(key)) {
      requirePressureReference(reference);
      fluid.setPressure(reference.getPressureBara(), "bara");
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.waterDewPointTemperatureMultiphaseFlash();
      return new Calculation(convertTemperatureFromK(fluid.getTemperature(), limit.getUnit()),
          "EOS multiphase water dew-point flash", null);
    }
    if ("cricondenbar".equals(key) || "cricondentherm".equals(key)) {
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.calcPTphaseEnvelope();
      if ("cricondenbar".equals(key)) {
        double[] point = operations.get("cricondenbar");
        return new Calculation(convertPressureFromBara(point[1], limit.getUnit()), "EOS PT phase envelope", null);
      }
      double[] point = operations.get("cricondentherm");
      return new Calculation(convertTemperatureFromK(point[0], limit.getUnit()), "EOS PT phase envelope", null);
    }
    if ("operatingPressure".equals(key)) {
      return new Calculation(convertPressureFromBara(fluid.getPressure("bara"), limit.getUnit()),
          "Solved network state", null);
    }
    if ("operatingTemperature".equals(key)) {
      return new Calculation(convertTemperatureFromK(fluid.getTemperature(), limit.getUnit()), "Solved network state",
          null);
    }

    if ("trueVaporPressure".equals(key)) {
      double temperatureK = reference != null && reference.getTemperature() != null ? reference.getTemperatureK()
          : fluid.getTemperature();
      Standard_TVP standard = new Standard_TVP(fluid);
      standard.setReferenceTemperature(temperatureK, "K");
      standard.calculate();
      return new Calculation(standard.getValue("TVP", limit.getUnit()), "True vapor pressure (Standard_TVP)", null);
    }
    if ("bubblePointPressure".equals(key)) {
      double temperatureK = reference != null && reference.getTemperature() != null ? reference.getTemperatureK()
          : fluid.getTemperature();
      fluid.setTemperature(temperatureK, "K");
      fluid.setPressure(1.01325, "bara");
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.bubblePointPressureFlash(false);
      return new Calculation(convertPressureFromBara(fluid.getPressure("bara"), limit.getUnit()),
          "EOS bubble-point pressure flash", null);
    }
    if ("reidVaporPressure".equals(key) || "vpcr4".equals(key)) {
      Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(fluid);
      if (reference != null && reference.getTemperature() != null) {
        standard.setReferenceTemperature(reference.getTemperatureK(), "K");
      }
      if ("reidVaporPressure".equals(key)) {
        standard.setMethodRVP(Standard_ASTM_D6377.RvpMethod.RVP_ASTM_D6377);
      } else {
        standard.setMethodRVP(Standard_ASTM_D6377.RvpMethod.VPCR4);
      }
      standard.calculate();
      return new Calculation(standard.getValue("RVP", limit.getUnit()),
          "reidVaporPressure".equals(key) ? "ASTM D6377 RVP equivalent" : "ASTM D6377 VPCR4", null);
    }
    if ("density".equals(key) || "apiGravity".equals(key)) {
      if (reference == null || reference.getTemperature() == null
          || Math.abs(reference.getTemperatureK() - 288.706) < 0.2) {
        Standard_ASTM_D4052 standard = new Standard_ASTM_D4052(fluid);
        standard.calculate();
        return new Calculation("apiGravity".equals(key) ? standard.getValue("API") : standard.getValue("density"),
            "ASTM D4052", null);
      }
      fluid.setTemperature(reference.getTemperatureK(), "K");
      fluid.setPressure(1.01325, "bara");
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.TPflash();
      fluid.initPhysicalProperties("density");
      double density = fluid.getDensity("kg/m3");
      if ("apiGravity".equals(key)) {
        double relativeDensity = density / 999.016;
        return new Calculation(141.5 / relativeDensity - 131.5, "API gravity from EOS density", null);
      }
      return new Calculation(density, "EOS density at reference temperature", null);
    }
    if ("dynamicViscosity".equals(key) || "kinematicViscosity".equals(key)) {
      if (reference == null || reference.getTemperature() == null) {
        throw new IllegalStateException("A viscosity reference temperature is required");
      }
      fluid.setTemperature(reference.getTemperatureK(), "K");
      fluid.setPressure(1.01325, "bara");
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.TPflash();
      fluid.initProperties();
      if ("dynamicViscosity".equals(key)) {
        double value = fluid.getViscosity("kg/msec");
        if ("mPa.s".equalsIgnoreCase(limit.getUnit()) || "cP".equalsIgnoreCase(limit.getUnit())) {
          value *= 1000.0;
        }
        return new Calculation(value, "EOS physical-property model", null);
      }
      double value = fluid.getKinematicViscosity("m2/sec");
      if ("cSt".equalsIgnoreCase(limit.getUnit())) {
        value *= 1.0e6;
      }
      return new Calculation(value, "EOS physical-property model", null);
    }

    NetworkMeasuredAttribute attribute = attributes
        .get(limit.getAttributeName() == null ? key : limit.getAttributeName());
    if (attribute == null) {
      throw new IllegalStateException("Required measured or assay-backed attribute was not supplied");
    }
    if (limit.getUnit() != null && attribute.getUnit() != null
        && !limit.getUnit().equalsIgnoreCase(attribute.getUnit())) {
      throw new IllegalStateException(
          "Attribute unit " + attribute.getUnit() + " does not match specification unit " + limit.getUnit());
    }
    return new Calculation(attribute.getValue(), attribute.getMethod(), attribute.getProvenance());
  }

  private static void requirePressureReference(QualityReference reference) {
    if (reference == null || reference.getPressure() == null) {
      throw new IllegalStateException("A pressure reference including bara or barg is required");
    }
  }

  private static void requireTemperatureReference(QualityReference reference) {
    if (reference == null || reference.getTemperature() == null) {
      throw new IllegalStateException("A temperature reference is required");
    }
  }

  private static double convertPressureFromBara(double bara, String unit) {
    if ("barg".equalsIgnoreCase(unit)) {
      return bara - 1.01325;
    }
    if ("Pa".equalsIgnoreCase(unit)) {
      return bara * 1.0e5;
    }
    if ("kPa".equalsIgnoreCase(unit)) {
      return bara * 100.0;
    }
    return bara;
  }

  private static double convertTemperatureFromK(double kelvin, String unit) {
    if ("C".equalsIgnoreCase(unit) || "degC".equalsIgnoreCase(unit)) {
      return kelvin - 273.15;
    }
    return kelvin;
  }

  private static final class Calculation {
    private final double value;
    private final String method;
    private final String provenance;

    private Calculation(double value, String method, String provenance) {
      this.value = value;
      this.method = method;
      this.provenance = provenance;
    }
  }
}
