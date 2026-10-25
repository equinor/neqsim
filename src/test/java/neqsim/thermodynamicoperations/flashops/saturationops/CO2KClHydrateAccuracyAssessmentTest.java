package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Diagnostic experimental-accuracy assessment for issue 4234. */
class CO2KClHydrateAccuracyAssessmentTest {
  private static final Logger logger = LogManager.getLogger(CO2KClHydrateAccuracyAssessmentTest.class);
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final double NACL_MOLAR_MASS_KG_PER_MOL = 0.05844277;
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double CACL2_MOLAR_MASS_KG_PER_MOL = 0.11098;

  @Tag("slow")
  @Test
  void k10AndK15KeepAccuracyAndContributionEvidenceSeparate() throws Exception {
    List<ReferencePoint> points = readReferencePoints();
    assertEquals(8, points.size());
    assertEquals(4, points.stream().filter(point -> "K10".equals(point.recipe)).count());
    assertEquals(4, points.stream().filter(point -> "K15".equals(point.recipe)).count());

    List<String> report = new ArrayList<String>();
    report.add("recipe,scenario,total_salt_wt_percent,pressure_bara,measured_K,calculated_K,error_K,"
        + "pure_water_baseline_K,model_suppression_K,water_activity_proxy,water_donor_site_fraction,"
        + "water_acceptor_site_fraction,water_water_incidence,water_co2_incidence,total_water_donor_incidence,"
        + "water_donor_symmetry_residual,water_acceptor_symmetry_residual,aqueous_x_co2,"
        + "guest_co2_fugacity_bara,aqueous_water_fugacity_bara,hydrate_water_fugacity_bara,"
        + "hydrate_residual,fluid_fugacity_residual,component_balance_residual,charge_residual,"
        + "fluid_phases,saturated_co2_boundary");

    int diagnosticEndpointCount = 0;
    for (ReferencePoint point : points) {
      DiagnosticResult pure = null;
      if (point.diagnosticEndpoint) {
        diagnosticEndpointCount++;
        pure = calculate(point, "pure-water", 0.0, 0.0, 0.0);
        report.add(pure.toCsv(point, Double.NaN, pure.temperature));
      }

      DiagnosticResult kcl = calculate(point, "KCl", 0.0, 1.0, 0.0);
      double pureTemperature = pure == null ? Double.NaN : pure.temperature;
      report.add(kcl.toCsv(point, point.measuredTemperature, pureTemperature));
      logger.info(
          "{} at {} bara: measured={} K, calculated={} K, error={} K, awProxy={}, xCO2aq={}, fCO2={} bara, "
              + "fWaterHydrate={} bara",
          point.recipe, point.pressureBara, point.measuredTemperature, kcl.temperature,
          kcl.temperature - point.measuredTemperature, kcl.waterActivityProxy, kcl.aqueousCo2MoleFraction,
          kcl.guestCo2Fugacity, kcl.hydrateWaterFugacity);

      if (point.diagnosticEndpoint) {
        report.add(calculate(point, "NaCl-equal-mass", 1.0, 0.0, 0.0).toCsv(point, Double.NaN, pure.temperature));
        report.add(calculate(point, "CaCl2-equal-mass", 0.0, 0.0, 1.0).toCsv(point, Double.NaN, pure.temperature));
        report.add(calculate(point, "NaCl-KCl-equal-mass", 0.5, 0.5, 0.0).toCsv(point, Double.NaN, pure.temperature));
        report.add(calculate(point, "NaCl-CaCl2-equal-mass", 0.5, 0.0, 0.5).toCsv(point, Double.NaN, pure.temperature));
      }
    }

    assertEquals(4, diagnosticEndpointCount);
    assertEquals(29, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/co2-kcl-hydrate-dholabhai1993-assessment.csv"), report, StandardCharsets.UTF_8);
    for (String row : report) {
      System.out.println("CO2_KCL_HYDRATE_DIAGNOSTIC," + row);
    }
  }

  private static DiagnosticResult calculate(ReferencePoint point, String scenario, double naclFraction,
      double kclFraction, double cacl2Fraction) throws Exception {
    double fractionSum = naclFraction + kclFraction + cacl2Fraction;
    assertTrue(fractionSum == 0.0 || Math.abs(fractionSum - 1.0) < 1.0e-12);
    double saltMassKg = fractionSum == 0.0 ? 0.0 : point.kclMassPercent / (100.0 - point.kclMassPercent);

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, point.pressureBara);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS_KG_PER_MOL);
    double naclMoles = saltMassKg * naclFraction / NACL_MOLAR_MASS_KG_PER_MOL;
    double kclMoles = saltMassKg * kclFraction / KCL_MOLAR_MASS_KG_PER_MOL;
    double cacl2Moles = saltMassKg * cacl2Fraction / CACL2_MOLAR_MASS_KG_PER_MOL;
    if (naclMoles > 0.0) {
      fluid.addComponent("Na+", naclMoles);
    }
    if (kclMoles > 0.0) {
      fluid.addComponent("K+", kclMoles);
    }
    if (cacl2Moles > 0.0) {
      fluid.addComponent("Ca++", cacl2Moles);
    }
    if (naclMoles + kclMoles + 2.0 * cacl2Moles > 0.0) {
      fluid.addComponent("Cl-", naclMoles + kclMoles + 2.0 * cacl2Moles);
    }
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.hydrateFormationTemperature(278.15);
    CO2BrineHydratePhaseStateTest.assertEndpoint(fluid, true);
    HydrateEquilibriumDiagnostics diagnostics = ((HydrateFormationTemperatureFlash) operations.getOperation())
        .getDiagnostics();
    assertTrue(diagnostics.isConverged());
    assertTrue(diagnostics.isSaturatedCO2Boundary());

    PhaseInterface aqueous = fluid.getPhase("aqueous");
    assertNotNull(aqueous);
    int waterIndex = aqueous.getComponent("water").getComponentNumber();
    double waterActivityProxy = aqueous.getComponent("water").getx() * aqueous.getActivityCoefficient(waterIndex);
    ComponentCPAInterface waterComponent = (ComponentCPAInterface) aqueous.getComponent("water");
    double[] waterSiteFractions = waterComponent.getXsite();
    assertEquals(4, waterSiteFractions.length);
    double waterDonorSiteFraction = 0.5 * (waterSiteFractions[0] + waterSiteFractions[1]);
    double waterAcceptorSiteFraction = 0.5 * (waterSiteFractions[2] + waterSiteFractions[3]);
    double waterWaterIncidence = 1.0 - waterAcceptorSiteFraction;
    double waterCo2Incidence = waterAcceptorSiteFraction - waterDonorSiteFraction;
    double totalWaterDonorIncidence = 1.0 - waterDonorSiteFraction;
    double waterDonorSymmetryResidual = Math.abs(waterSiteFractions[0] - waterSiteFractions[1]);
    double waterAcceptorSymmetryResidual = Math.abs(waterSiteFractions[2] - waterSiteFractions[3]);
    assertEquals(totalWaterDonorIncidence, waterWaterIncidence + waterCo2Incidence, 1.0e-14);
    PhaseInterface co2Rich = findCo2RichPhase(fluid);
    double guestCo2Fugacity = co2Rich.getFugacity("CO2");
    double aqueousWaterFugacity = aqueous.getFugacity("water");
    double hydrateWaterFugacity = fluid.getPhase(4).getFugacity("water");

    for (double value : new double[] {fluid.getTemperature(), waterActivityProxy, waterDonorSiteFraction,
        waterAcceptorSiteFraction, waterWaterIncidence, waterCo2Incidence, totalWaterDonorIncidence,
        waterDonorSymmetryResidual, waterAcceptorSymmetryResidual, aqueous.getComponent("CO2").getx(), guestCo2Fugacity,
        aqueousWaterFugacity, hydrateWaterFugacity, diagnostics.getHydrateResidual(),
        diagnostics.getComponentBalanceResidual(), diagnostics.getChargeResidual()}) {
      assertTrue(Double.isFinite(value), scenario + " produced a non-finite diagnostic");
    }
    return new DiagnosticResult(scenario, fractionSum == 0.0 ? 0.0 : point.kclMassPercent, fluid.getTemperature(),
        waterActivityProxy, waterDonorSiteFraction, waterAcceptorSiteFraction, waterWaterIncidence, waterCo2Incidence,
        totalWaterDonorIncidence, waterDonorSymmetryResidual, waterAcceptorSymmetryResidual,
        aqueous.getComponent("CO2").getx(), guestCo2Fugacity, aqueousWaterFugacity, hydrateWaterFugacity, diagnostics);
  }

  private static PhaseInterface findCo2RichPhase(SystemInterface fluid) {
    PhaseInterface selected = null;
    double largestCo2Fraction = -1.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface candidate = fluid.getPhase(phase);
      if (candidate.getType() != PhaseType.AQUEOUS && candidate.hasComponent("CO2")
          && candidate.getComponent("CO2").getx() > largestCo2Fraction) {
        selected = candidate;
        largestCo2Fraction = candidate.getComponent("CO2").getx();
      }
    }
    assertNotNull(selected);
    return selected;
  }

  private static List<ReferencePoint> readReferencePoints() throws Exception {
    String resource = "/data/chemistry_benchmarks/co2_kcl_hydrate_dholabhai1993.csv";
    List<ReferencePoint> points = new ArrayList<ReferencePoint>();
    try (InputStream stream = CO2KClHydrateAccuracyAssessmentTest.class.getResourceAsStream(resource)) {
      assertNotNull(stream);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        reader.readLine();
        String line;
        while ((line = reader.readLine()) != null) {
          String[] values = line.split(",");
          points.add(new ReferencePoint(values[0], Double.parseDouble(values[1]), 10.0 * Double.parseDouble(values[2]),
              Double.parseDouble(values[3]), Boolean.parseBoolean(values[5])));
        }
      }
    }
    return points;
  }

  private static final class ReferencePoint {
    private final String recipe;
    private final double kclMassPercent;
    private final double pressureBara;
    private final double measuredTemperature;
    private final boolean diagnosticEndpoint;

    private ReferencePoint(String recipe, double kclMassPercent, double pressureBara, double measuredTemperature,
        boolean diagnosticEndpoint) {
      this.recipe = recipe;
      this.kclMassPercent = kclMassPercent;
      this.pressureBara = pressureBara;
      this.measuredTemperature = measuredTemperature;
      this.diagnosticEndpoint = diagnosticEndpoint;
    }
  }

  private static final class DiagnosticResult {
    private final String scenario;
    private final double totalSaltMassPercent;
    private final double temperature;
    private final double waterActivityProxy;
    private final double waterDonorSiteFraction;
    private final double waterAcceptorSiteFraction;
    private final double waterWaterIncidence;
    private final double waterCo2Incidence;
    private final double totalWaterDonorIncidence;
    private final double waterDonorSymmetryResidual;
    private final double waterAcceptorSymmetryResidual;
    private final double aqueousCo2MoleFraction;
    private final double guestCo2Fugacity;
    private final double aqueousWaterFugacity;
    private final double hydrateWaterFugacity;
    private final HydrateEquilibriumDiagnostics diagnostics;

    private DiagnosticResult(String scenario, double totalSaltMassPercent, double temperature,
        double waterActivityProxy, double waterDonorSiteFraction, double waterAcceptorSiteFraction,
        double waterWaterIncidence, double waterCo2Incidence, double totalWaterDonorIncidence,
        double waterDonorSymmetryResidual, double waterAcceptorSymmetryResidual, double aqueousCo2MoleFraction,
        double guestCo2Fugacity, double aqueousWaterFugacity, double hydrateWaterFugacity,
        HydrateEquilibriumDiagnostics diagnostics) {
      this.scenario = scenario;
      this.totalSaltMassPercent = totalSaltMassPercent;
      this.temperature = temperature;
      this.waterActivityProxy = waterActivityProxy;
      this.waterDonorSiteFraction = waterDonorSiteFraction;
      this.waterAcceptorSiteFraction = waterAcceptorSiteFraction;
      this.waterWaterIncidence = waterWaterIncidence;
      this.waterCo2Incidence = waterCo2Incidence;
      this.totalWaterDonorIncidence = totalWaterDonorIncidence;
      this.waterDonorSymmetryResidual = waterDonorSymmetryResidual;
      this.waterAcceptorSymmetryResidual = waterAcceptorSymmetryResidual;
      this.aqueousCo2MoleFraction = aqueousCo2MoleFraction;
      this.guestCo2Fugacity = guestCo2Fugacity;
      this.aqueousWaterFugacity = aqueousWaterFugacity;
      this.hydrateWaterFugacity = hydrateWaterFugacity;
      this.diagnostics = diagnostics;
    }

    private String toCsv(ReferencePoint point, double measuredTemperature, double pureWaterTemperature) {
      double error = Double.isFinite(measuredTemperature) ? temperature - measuredTemperature : Double.NaN;
      double suppression = Double.isFinite(pureWaterTemperature) ? pureWaterTemperature - temperature : Double.NaN;
      return point.recipe + "," + scenario + "," + totalSaltMassPercent + "," + point.pressureBara + ","
          + measuredTemperature + "," + temperature + "," + error + "," + pureWaterTemperature + "," + suppression + ","
          + waterActivityProxy + "," + waterDonorSiteFraction + "," + waterAcceptorSiteFraction + ","
          + waterWaterIncidence + "," + waterCo2Incidence + "," + totalWaterDonorIncidence + ","
          + waterDonorSymmetryResidual + "," + waterAcceptorSymmetryResidual + "," + aqueousCo2MoleFraction + ","
          + guestCo2Fugacity + "," + aqueousWaterFugacity + "," + hydrateWaterFugacity + ","
          + diagnostics.getHydrateResidual() + "," + diagnostics.getFluidFugacityResidual() + ","
          + diagnostics.getComponentBalanceResidual() + "," + diagnostics.getChargeResidual() + ","
          + String.join("+", diagnostics.getPhaseTypes()) + "," + diagnostics.isSaturatedCO2Boundary();
    }
  }
}
