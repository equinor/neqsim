package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Published-data benchmark tests for separator entrainment models.
 *
 * <p>
 * These tests validate model behavior against published performance windows and design thresholds,
 * not against proprietary vendor test-rig datasets.
 * </p>
 *
 * <p>
 * References used in assertions:
 * </p>
 * <ul>
 * <li>Bothamley, M. (2013). Gas/Liquid Separators: Quantifying Separation Performance,
 * <i>Oil and Gas Facilities</i>.</li>
 * <li>Brunazzi, E., Paglianti, A. (1998). Wire mesh mist eliminator performance,
 * <i>Chem. Eng. Sci.</i>, 53(19), 3373-3380.</li>
 * <li>API Specification 12J (2014). Specification for Oil and Gas Separators.</li>
 * </ul>
 */
class SeparatorPublishedBenchmarkTest {

  @Test
  void testInletVaneBulkEfficiencyWithinPublishedRange() {
    // Published range for inlet vanes is typically 70-85% bulk liquid separation.
    InletDeviceModel vane = new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_VANE);
    vane.setInletNozzleDiameter(0.20);

    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

    // Choose flow conditions giving recommended momentum (below 6000 Pa max for inlet vane).
    vane.calculate(dsd, 50.0, 800.0,
        0.18, // gas flow [m3/s]
        0.015, // liquid flow [m3/s]
        0.025);

    assertTrue(vane.getMomentumFlux() > 1000.0 && vane.getMomentumFlux() < 6000.0,
        "Vane momentum should be in recommended published range, got: " + vane.getMomentumFlux());
    assertTrue(vane.getBulkSeparationEfficiency() >= 0.70 && vane.getBulkSeparationEfficiency() <= 0.85,
        "Inlet vane bulk efficiency should be within published 70-85% range, got: "
            + vane.getBulkSeparationEfficiency());
  }

  @Test
  void testInletCycloneBulkEfficiencyWithinPublishedRange() {
    // Published range for inlet cyclones is typically 90-99%.
    InletDeviceModel cyclone = new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_CYCLONE);
    cyclone.setInletNozzleDiameter(0.20);

    DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);
    cyclone.calculate(dsd, 50.0, 800.0, 0.20, 0.02, 0.025);

    assertTrue(cyclone.getBulkSeparationEfficiency() >= 0.90
        && cyclone.getBulkSeparationEfficiency() <= 0.99,
        "Inlet cyclone bulk efficiency should be in published 90-99% range, got: "
            + cyclone.getBulkSeparationEfficiency());
  }

  @Test
  void testWireMeshDemisterEfficiencyWindowFromPublishedCurve() {
    // Wire mesh default corresponds to literature-typical parameters.
    GradeEfficiencyCurve wire = GradeEfficiencyCurve.wireMeshDefault();

    // Brunazzi/Paglianti-type behavior: high capture for droplets above 10 um.
    double eta10um = wire.getEfficiency(10e-6);
    assertTrue(eta10um > 0.95,
        "Wire mesh should capture >95% at 10 um for standard high-efficiency pad, got: " + eta10um);

    // At d50 (5 um default), efficiency should be approximately 50% of max by definition.
    double etaAtD50 = wire.getEfficiency(5e-6);
    assertEquals(0.499, etaAtD50, 0.02,
        "Efficiency at d50 should be about 0.5 for the sigmoid grade curve");
  }

  @Test
  void testApi12JThresholdBoundaryCases() {
    // API 12J threshold check: vertical, no mist eliminator, K limit = 0.107 m/s.
    DropletSettlingCalculator.ApiComplianceResult verticalBoundary =
        DropletSettlingCalculator.checkApi12JCompliance(
            90e-6, // cut diameter <= 100 um
            0.107, // exact boundary
            false,
            180.0, // 2-phase minimum
            "vertical",
            false);
    assertTrue(verticalBoundary.isFullyCompliant(),
        "Case exactly at API 12J vertical K-threshold should be compliant");

    // Horizontal no-ME boundary K = 0.120 m/s should be compliant when other criteria pass.
    DropletSettlingCalculator.ApiComplianceResult horizontalBoundary =
        DropletSettlingCalculator.checkApi12JCompliance(95e-6, 0.120, false, 180.0, "horizontal",
            false);
    assertTrue(horizontalBoundary.isFullyCompliant(),
        "Case exactly at API 12J horizontal K-threshold should be compliant");
  }
}
