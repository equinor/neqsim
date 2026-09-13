package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DistillationColumn.ColumnPumparound;
import neqsim.process.equipment.distillation.SarirAtmosphericFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.SarirAtmosphericPumparoundScreen.Mapping;
import neqsim.process.equipment.distillation.SarirAtmosphericPumparoundScreen.PumparoundResult;
import neqsim.process.equipment.distillation.SarirAtmosphericPumparoundScreen.Result;
import neqsim.thermo.characterization.SarirAtmosphericReference;

/** Qualification tests for {@link SarirAtmosphericPumparoundScreen}. */
public class SarirAtmosphericPumparoundScreenTest {
  private static final double[] SPECIFIC_GRAVITY = { 0.641826000, 0.671826000, 0.691826000, 0.711826000, 0.741826000,
      0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000, 0.901826000,
      0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000 };
  private static final double[] MOLAR_MASS_KG_PER_MOL = { 0.092957679997, 0.105352037330, 0.117746394663,
      0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992, 0.272675861324,
      0.303661754657, 0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649,
      0.743661439976 };

  /** Keep source tray labels separate from explicit bottom-up NeqSim indices. */
  @Test
  public void explicitMappingsConfigurePublishedTemperatureDrops() {
    SarirAtmosphericFractionationCase model = createModel();
    Mapping top = new Mapping("Top pump around (TPA)", 30, 32, 0.01);
    Mapping bottom = new Mapping("Bottom pump around (BPA)", 12, 15, 0.02);
    SarirAtmosphericPumparoundScreen screen =
        SarirAtmosphericPumparoundScreen.configure(model, 20, 1.0e-4, top, bottom);

    Mapping[] mappings = screen.getMappings();
    assertEquals(2, mappings.length);
    assertNotSame(mappings, screen.getMappings());
    mappings[0] = null;
    assertTrue(screen.getMappings()[0] != null);

    ColumnPumparound topCircuit = model.getColumn().getPumparounds().get(0);
    assertEquals(top.getReferenceName(), topCircuit.getName());
    assertEquals(30, topCircuit.getDrawTrayNumber());
    assertEquals(32, topCircuit.getReturnTrayNumber());
    assertEquals(0.01, topCircuit.getDrawFraction(), 0.0);
    assertEquals(SarirAtmosphericReference.getPumparound(top.getReferenceName()).getTemperatureDropKelvin(),
        topCircuit.getTemperatureDrop(), 0.0);

    assertEquals(3,
        SarirAtmosphericReference.getPumparound(top.getReferenceName()).getSourceDrawTrayNumber());
    assertEquals(1,
        SarirAtmosphericReference.getPumparound(top.getReferenceName()).getSourceReturnTrayNumber());
    assertTrue(!SarirAtmosphericReference.hasExplicitPumparoundTrayNumberingBasis());
    assertThrows(IllegalStateException.class, screen::evaluate);
  }

  /** Reject inferred, duplicate, or physically invalid mappings before column mutation. */
  @Test
  public void invalidMappingsFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> new Mapping("unknown", 30, 32, 0.01));
    assertThrows(IllegalArgumentException.class,
        () -> new Mapping("Top pump around (TPA)", -1, 32, 0.01));
    assertThrows(IllegalArgumentException.class,
        () -> new Mapping("Top pump around (TPA)", 30, 30, 0.01));
    assertThrows(IllegalArgumentException.class,
        () -> new Mapping("Top pump around (TPA)", 30, 32, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericPumparoundScreen.configure(createModel(), 20, 1.0e-4));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericPumparoundScreen.configure(createModel(), 20, 1.0e-4,
            new Mapping("Top pump around (TPA)", 30, 32, 0.01),
            new Mapping("Top pump around (TPA)", 29, 31, 0.01)));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericPumparoundScreen.configure(createModel(), 20, 1.0e-4,
            new Mapping("Top pump around (TPA)", 30, 32, 0.01),
            new Mapping("Bottom pump around (BPA)", 30, 15, 0.02)));
  }

  /** Require a conservative non-fallback product solve and converged internal circuit. */
  @Test
  @Timeout(value = 240, unit = TimeUnit.SECONDS)
  public void smallExplicitPumparoundProducesQualifiedEvidence() {
    SarirAtmosphericFractionationCase model = createModel();
    SarirAtmosphericPumparoundScreen screen = SarirAtmosphericPumparoundScreen.configure(model, 20, 1.0e-4,
        new Mapping("Top pump around (TPA)", 30, 32, 0.005));

    Result result = screen.run(UUID.randomUUID());
    assertTrue(result.getProductResult().getMassClosureRelativeError() <= 5.0e-2);
    assertTrue(model.getColumn().isLastColumnTearConverged(), model.getColumn().getConvergenceDiagnostics());
    assertTrue(Double.isFinite(result.getLastPumparoundRelativeChange()));
    assertTrue(Double.isFinite(result.getLastColumnTearResidual()));
    assertTrue(result.getLastColumnTearIterationCount() > 0);

    PumparoundResult[] rows = result.getPumparounds();
    assertEquals(1, rows.length);
    assertNotSame(rows, result.getPumparounds());
    assertEquals(3, rows[0].getSourceDrawTrayNumber());
    assertEquals(1, rows[0].getSourceReturnTrayNumber());
    assertEquals(SarirAtmosphericReference.getTopPumpAroundRateKgPerHour(),
        rows[0].getSourceMassFlowKgPerHour(), 0.0);
    assertTrue(rows[0].getModeledDrawMassFlowKgPerHour() > 0.0);
    assertEquals(rows[0].getModeledDrawMassFlowKgPerHour(),
        rows[0].getModeledReturnMassFlowKgPerHour(),
        1.0e-8 * rows[0].getModeledDrawMassFlowKgPerHour());
    assertTrue(rows[0].getInternalFlowClosureRelativeError() <= 1.0e-8);
    assertTrue(rows[0].getDutyW() < 0.0);
    assertTrue(Double.isFinite(rows[0].getAbsoluteRelativeFlowErrorPercentAgainstSource()));
    assertTrue(Double.isFinite(rows[0].getAbsoluteDrawTemperatureErrorKelvin()));
    assertTrue(Double.isFinite(rows[0].getAbsoluteReturnTemperatureErrorKelvin()));
  }

  private static SarirAtmosphericFractionationCase createModel() {
    OperatingInputs inputs = new OperatingInputs(1.20,
        SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0, 700.0, 1.0, 24, 0.08,
        15, 0.15);
    return SarirAtmosphericFractionationCase.create("Sarir pump-around screen", SPECIFIC_GRAVITY,
        MOLAR_MASS_KG_PER_MOL, inputs);
  }
}
