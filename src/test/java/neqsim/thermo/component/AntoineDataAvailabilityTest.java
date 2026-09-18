package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import org.h2.tools.Csv;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.FluidPropertyEstimator;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/** Regression checks for unavailable, inapplicable and corrected vapor-pressure data. */
class AntoineDataAvailabilityTest {
  @Test
  void placeholderRowsDoNotPublishSaturationProperties() {
    for (String name : new String[] {"hydrogen", "argon", "nC20", "MDEA", "formic acid", "NaCl", "1-heptene",
        "default"}) {
      ComponentInterface component = new ComponentSrk(name, 1.0, 1.0, 0);
      assertFalse(component.hasAntoineVaporPressureCorrelation(), name);
      double temperature = 0.7 * component.getTC();
      assertTrue(Double.isNaN(component.getAntoineVaporPressure(temperature)), name);
      assertTrue(Double.isNaN(component.getAntoineVaporPressuredT(temperature)), name);
      assertTrue(Double.isNaN(component.getAntoineVaporTemperature(1.0)), name);
    }
  }

  @Test
  void everyChargedDatabaseRowHasNoLiquidVaporPressure() throws Exception {
    try (
        InputStreamReader reader = new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("data/COMP.csv"), StandardCharsets.UTF_8);
        ResultSet rows = new Csv().read(reader, null)) {
      int ions = 0;
      while (rows.next()) {
        if (rows.getInt("IONICCHARGE") != 0) {
          ions++;
          String name = rows.getString("NAME");
          ComponentInterface component = new ComponentSrk(name, 1.0, 1.0, 0);
          assertEquals("none", rows.getString("AntoineVapPresLiqType"), name);
          assertFalse(component.hasAntoineVaporPressureCorrelation(), name);
          assertTrue(Double.isNaN(component.getAntoineVaporPressure(298.15)), name);
          assertTrue(Double.isNaN(component.getAntoineVaporPressuredT(298.15)), name);
          assertTrue(Double.isNaN(component.getAntoineVaporTemperature(1.0)), name);
        }
      }
      assertTrue(ions >= 59, "The complete charged-species population must be checked");
    }
  }

  @Test
  void acetoneMatchesNistCorrelationInBar() {
    ComponentInterface acetone = new ComponentSrk("acetone", 1.0, 1.0, 0);
    assertTrue(acetone.hasAntoineVaporPressureCorrelation());
    // NIST WebBook, Ambrose et al. (1974), valid from 259.16 to 507.60 K.
    assertEquals(0.306, acetone.getAntoineVaporPressure(298.15), 0.001);
    assertEquals(0.7260971861, acetone.getAntoineVaporPressure(320.0), 1.0e-10);
    assertEquals(1.0063984293, acetone.getAntoineVaporPressure(329.22), 1.0e-10);
    for (double temperature : new double[] {280.0, 298.15, 320.0, 400.0, 480.0}) {
      double step = 1.0e-3;
      double numerical = (acetone.getAntoineVaporPressure(temperature + step)
          - acetone.getAntoineVaporPressure(temperature - step)) / (2.0 * step);
      assertEquals(numerical, acetone.getAntoineVaporPressuredT(temperature), 1.0e-8);
      assertEquals(temperature, acetone.getAntoineVaporTemperature(acetone.getAntoineVaporPressure(temperature)),
          0.002);
    }
  }

  @Test
  void saturationPropertiesRejectNonphysicalAndSupercriticalInputs() {
    ComponentInterface methane = new ComponentSrk("methane", 1.0, 1.0, 0);
    for (double temperature : new double[] {0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY, 298.15}) {
      assertTrue(Double.isNaN(methane.getAntoineVaporPressure(temperature)));
      assertTrue(Double.isNaN(methane.getAntoineVaporPressuredT(temperature)));
    }
    for (double pressure : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 2.0 * methane.getPC()}) {
      assertTrue(Double.isNaN(methane.getAntoineVaporTemperature(pressure)));
    }
    assertTrue(methane.getAntoineVaporPressure(111.66) > 0.0);
  }

  @Test
  void ionicChargeRejectsCorrelationsFromLegacyOrCustomTables() {
    ComponentSrk ion = new ComponentSrk("Na+", 1.0, 1.0, 0);
    ion.antoineLiqVapPresType = "pow10";
    ion.AntoineA = 5.11564;
    ion.AntoineB = 1687.537;
    ion.AntoineC = 230.17;
    assertFalse(ion.hasAntoineVaporPressureCorrelation());
    assertTrue(Double.isNaN(ion.getAntoineVaporPressure(298.15)));
    assertTrue(Double.isNaN(ion.getAntoineVaporPressuredT(298.15)));
    assertTrue(Double.isNaN(ion.getAntoineVaporTemperature(1.0)));
  }

  @Test
  void ordinaryWaterAndEosFallbackRemainUsable() throws Exception {
    ComponentInterface water = new ComponentSrk("water", 1.0, 1.0, 0);
    assertEquals(0.0317, water.getAntoineVaporPressure(298.15), 0.0003);
    assertEquals(1.01325, water.getAntoineVaporPressure(373.15), 0.01);
    SystemSrkEos hydrogen = new SystemSrkEos(20.369, 1.0);
    hydrogen.addComponent("hydrogen", 1.0);
    new ThermodynamicOperations(hydrogen).bubblePointPressureFlash();
    assertEquals(1.01325, hydrogen.getPressure(), 0.10);
    SystemSrkEos hydrocarbon = new SystemSrkEos(400.0, 1.0);
    hydrocarbon.addComponent("nC20", 1.0);
    ComponentInterface component = hydrocarbon.getComponent(0);
    assertEquals(
        FluidPropertyEstimator.estimateSaturationPressure(400.0, component.getTC(), component.getPC(),
            component.getAcentricFactor()),
        FluidPropertyEstimator.estimateSaturationPressure(hydrocarbon, 0, 0), 1.0e-12);
  }

  @Test
  void extendedDatabaseRetainsStandardVaporPressureCorrections() {
    try {
      NeqSimDataBase.useExtendedComponentDatabase(true);
      assertEquals(0.306, new ComponentSrk("acetone", 1.0, 1.0, 0).getAntoineVaporPressure(298.15), 0.001);
      assertTrue(Double.isNaN(new ComponentSrk("nC20", 1.0, 1.0, 0).getAntoineVaporPressure(400.0)));
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }

  @Test
  void knownTemplatesCannotReappearAsUsableCorrelations() throws Exception {
    try (
        InputStreamReader reader = new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("data/COMP.csv"), StandardCharsets.UTF_8);
        ResultSet rows = new Csv().read(reader, null)) {
      while (rows.next()) {
        String name = rows.getString("NAME");
        double a = rows.getDouble("ANTOINEA");
        double b = rows.getDouble("ANTOINEB");
        double c = rows.getDouble("ANTOINEC");
        double d = rows.getDouble("ANTOINED");
        double e = rows.getDouble("ANTOINEE");
        assertFalse(a == -7.76451 && b == 1.45838 && c == -2.7758 && d == -1.23303 && e == 0.0, name);
        assertFalse(a == -8.54796 && b == 0.76982 && c == -3.1085 && d == 1.54481 && e == 0.0, name);
        assertFalse(a == -8.54 && b == 0.76 && c == -3.1 && d == 1.54 && e == 0.0, name);
        if (!"water".equals(name) && !"seawater".equals(name)) {
          assertFalse(a == 5.11564 && b == 1687.537 && c == 230.17 && d == 0.0 && e == 0.0, name);
        }
        if ("none".equals(rows.getString("AntoineVapPresLiqType"))) {
          assertEquals(0.0, Math.abs(a) + Math.abs(b) + Math.abs(c) + Math.abs(d) + Math.abs(e), 0.0, name);
          ComponentInterface component = new ComponentSrk(name, 1.0, 1.0, 0);
          assertFalse(component.hasAntoineVaporPressureCorrelation(), name);
          assertTrue(Double.isNaN(component.getAntoineVaporPressure(0.7 * component.getTC())), name);
        }
      }
    }
  }
}
