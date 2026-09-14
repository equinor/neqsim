package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.SarirAtmosphericFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.SarirAtmosphericMainSteamScreen.ReportedPressureBasis;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.SteamInjectionReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.SteamInjectionService;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/** Qualification tests for {@link SarirAtmosphericSideStripperContactScreen}. */
public class SarirAtmosphericSideStripperContactScreenTest {
  private static final double[] SPECIFIC_GRAVITY = { 0.641826000, 0.671826000, 0.691826000, 0.711826000, 0.741826000,
      0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000, 0.901826000,
      0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000 };
  private static final double[] MOLAR_MASS_KG_PER_MOL = { 0.092957679997, 0.105352037330, 0.117746394663,
      0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992, 0.272675861324,
      0.303661754657, 0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649,
      0.743661439976 };

  /** Require a solved qualified column and reject unsupported or implicit boundaries. */
  @Test
  public void incompleteOrUnsupportedBoundariesFailClosed() {
    SarirAtmosphericFractionationCase unsolved = createModel();
    StreamInterface keroseneSteam = createPreparedSteam(unsolved, SteamInjectionService.KEROSENE_SIDE_STRIPPER);

    assertThrows(IllegalStateException.class,
        () -> SarirAtmosphericSideStripperContactScreen.configure(unsolved,
            SteamInjectionService.KEROSENE_SIDE_STRIPPER, keroseneSteam, ReportedPressureBasis.ABSOLUTE,
            "independent vapor-state calculation", 1.20));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericSideStripperContactScreen.configure(unsolved,
            SteamInjectionService.MAIN_ATMOSPHERIC_COLUMN, keroseneSteam, ReportedPressureBasis.ABSOLUTE,
            "independent vapor-state calculation", 1.20));
    assertThrows(IllegalArgumentException.class, () -> SarirAtmosphericSideStripperContactScreen.configure(unsolved,
        SteamInjectionService.KEROSENE_SIDE_STRIPPER, keroseneSteam, ReportedPressureBasis.ABSOLUTE, " ", 1.20));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericSideStripperContactScreen.configure(unsolved,
            SteamInjectionService.KEROSENE_SIDE_STRIPPER, keroseneSteam, ReportedPressureBasis.ABSOLUTE,
            "independent vapor-state calculation", Double.NaN));
  }

  /** Qualify both published side-stripper rows on explicit single equilibrium contacts. */
  @Test
  @Timeout(value = 240, unit = TimeUnit.SECONDS)
  public void publishedSideStripperRowsProduceClosedEquilibriumContacts() {
    SarirAtmosphericFractionationCase model = createModel();
    model.run(UUID.randomUUID());
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, model.getColumn().getLastSolveStatus(),
        model.getColumn().getConvergenceDiagnostics());

    assertContact(model, SteamInjectionService.KEROSENE_SIDE_STRIPPER, "Kerosene side stripper", 68.04);
    assertContact(model, SteamInjectionService.DIESEL_SIDE_STRIPPER, "Diesel side stripper", 226.8);
  }

  private static void assertContact(SarirAtmosphericFractionationCase model, SteamInjectionService service,
      String expectedSourceRow, double expectedSteamFlow) {
    StreamInterface steam = createPreparedSteam(model, service);
    SarirAtmosphericSideStripperContactScreen screen = SarirAtmosphericSideStripperContactScreen.configure(model,
        service, steam, ReportedPressureBasis.ABSOLUTE, "independent vapor-state calculation", 1.20);

    OperatingInputs inputs = model.getOperatingInputs();
    int tray = service == SteamInjectionService.KEROSENE_SIDE_STRIPPER ? inputs.getKeroseneSideDrawTray()
        : inputs.getDieselSideDrawTray();
    assertSame(model.getColumn().getSideDrawStream(tray, DistillationColumn.SideDrawPhase.LIQUID),
        screen.getSideDrawStream());
    assertSame(steam, screen.getSteamStream());
    assertEquals(expectedSourceRow, screen.getSourceReference().getName());
    assertEquals(expectedSteamFlow, screen.getSourceReference().getMassFlowRateKgPerHour(), 0.0);

    SarirAtmosphericSideStripperContactScreen.Result result = screen.run(UUID.randomUUID());
    assertEquals(service, result.getService());
    assertEquals(expectedSourceRow, result.getSourceRowName());
    assertEquals(expectedSteamFlow, result.getSourceSteamMassFlowKgPerHour(), 0.0);
    assertEquals(expectedSteamFlow, result.getModeledSteamMassFlowKgPerHour(), expectedSteamFlow * 1.0e-9);
    assertTrue(result.getSideDrawMassFlowKgPerHour() > 0.0);
    assertTrue(result.getVaporProductMassFlowKgPerHour() > 0.0);
    assertTrue(result.getLiquidProductMassFlowKgPerHour() > 0.0);
    assertTrue(Double.isFinite(result.getContactTemperatureKelvin()));
    assertTrue(result.getContactTemperatureKelvin() > 0.0);
    assertTrue(result.getMassClosureRelativeError() <= 1.0e-6);
  }

  private static StreamInterface createPreparedSteam(SarirAtmosphericFractionationCase model,
      SteamInjectionService service) {
    String rowName = service == SteamInjectionService.KEROSENE_SIDE_STRIPPER ? "Kerosene side stripper"
        : "Diesel side stripper";
    SteamInjectionReference source = SarirAtmosphericReference.getSteamInjection(rowName);
    SystemInterface fluid = model.getFeedStream().getFluid().getEmptySystemClone();
    fluid.setTemperature(source.getTemperatureCelsius() + 273.15);
    fluid.setPressure(source.getPressureKPa() / 100.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(source.getMassFlowRateKgPerHour(), "kg/hr");
    fluid.setNumberOfPhases(1);
    fluid.setMaxNumberOfPhases(1);
    fluid.setForcePhaseTypes(true);
    fluid.setPhaseType(0, PhaseType.GAS);
    fluid.init(3);
    return new Stream("prepared " + rowName + " steam", fluid);
  }

  private static SarirAtmosphericFractionationCase createModel() {
    OperatingInputs inputs = new OperatingInputs(1.20, SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0,
        700.0, 1.0, 24, 0.08, 15, 0.15);
    return SarirAtmosphericFractionationCase.create("Sarir side-stripper contact", SPECIFIC_GRAVITY,
        MOLAR_MASS_KG_PER_MOL, inputs);
  }
}
