package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.util.database.NeqSimDataBase;

/** Invalid and copied correlation data must not appear to be available measurements. */
class AntoineHazopRegressionTest {
  @Test
  void extendedDatabaseUsesTheSameReviewedCorrections() {
    try {
      NeqSimDataBase.useExtendedComponentDatabase(true);
      unverifiedCopiesAndHybridHydrogenPeroxideDataAreUnavailable();
      ammoniaAndHydrogenSulfideAgreeWithSourcedNistCorrelations();
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }

  @Test
  void unverifiedCopiesAndHybridHydrogenPeroxideDataAreUnavailable() {
    for (String name : new String[] {"H2O2", "PG", "SF6", "R12", "R134a", "COS", "sulfuric acid", "NO2", "nitric acid",
        "3-methyl-1-butene", "2-M-C7", "M-cy-C6", "i-p-cy-C5", "cis-12-DM-cy-C6", "234-TM-C5", "trans-12-DM-cy-C6",
        "cis-14-DM-cy-C6", "trans-14-DM-cy-C6"}) {
      ComponentSrk c = new ComponentSrk(name, 1, 1, 0);
      assertFalse(c.hasAntoineVaporPressureCorrelation(), name);
      assertTrue(Double.isNaN(c.getAntoineVaporPressure(298.15)), name);
      assertTrue(Double.isNaN(c.getAntoineVaporPressuredT(298.15)), name);
      assertTrue(Double.isNaN(c.getAntoineVaporTemperature(1.0)), name);
    }
  }

  @Test
  void ammoniaAndHydrogenSulfideAgreeWithSourcedNistCorrelations() {
    // NIST WebBook, Stull (1947), P in bar, T in K. See data documentation for fit ranges.
    for (String name : new String[] {"ammonia", "H2S"}) {
      ComponentSrk c = new ComponentSrk(name, 1, 1, 0);
      double a = name.equals("ammonia") ? 4.86886 : 4.52887;
      double b = name.equals("ammonia") ? 1113.928 : 958.587;
      double offset = name.equals("ammonia") ? -10.409 : -0.539;
      for (double t : new double[] {250.0, 280.0, 320.0, 340.0}) {
        assertEquals(Math.pow(10, a - b / (t + offset)), c.getAntoineVaporPressure(t), 1e-10, name);
        double numeric = (c.getAntoineVaporPressure(t + 0.001) - c.getAntoineVaporPressure(t - 0.001)) / 0.002;
        assertEquals(numeric, c.getAntoineVaporPressuredT(t), 1e-8);
        assertEquals(t, c.getAntoineVaporTemperature(c.getAntoineVaporPressure(t)), 0.002);
      }
    }
  }

  @Test
  void legacyOrCustomOverflowDoesNotEscapeAsInfinityOrFalseInverseSuccess() {
    ComponentSrk c = new ComponentSrk("water", 1, 1, 0);
    c.antoineLiqVapPresType = "log";
    c.AntoineA = -8.54796;
    c.AntoineB = 0.76982;
    c.AntoineC = -3.1085;
    c.AntoineD = 1.54481;
    c.AntoineE = 2.1;
    assertTrue(Double.isNaN(c.getAntoineVaporPressure(298.15)));
    assertTrue(Double.isNaN(c.getAntoineVaporPressuredT(298.15)));
    assertTrue(Double.isNaN(c.getAntoineVaporTemperature(1.0)));
  }
}
