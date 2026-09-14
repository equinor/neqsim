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
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Qualification tests for {@link SarirAtmosphericMainSteamScreen}. */
public class SarirAtmosphericMainSteamScreenTest {
  private static final double[] SPECIFIC_GRAVITY = {0.641826000, 0.671826000, 0.691826000,
      0.711826000, 0.741826000, 0.761826000, 0.781826000, 0.801826000, 0.821826000,
      0.841826000, 0.861826000, 0.881826000, 0.901826000, 0.921826000, 0.941826000,
      0.961826000, 0.981826000, 1.021826000};
  private static final double[] MOLAR_MASS_KG_PER_MOL = {0.092957679997, 0.105352037330,
      0.117746394663, 0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993,
      0.223098431993, 0.241689967992, 0.272675861324, 0.303661754657, 0.334647647989,
      0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649,
      0.743661439976};

  /** Retain an explicit mapping and prepared gas state without deriving either from the source. */
  @Test
  public void explicitPreparedVaporBoundaryConfiguresAdditionalFeed() {
    SarirAtmosphericFractionationCase model = createModel();
    StreamInterface steam = createPreparedStream("water", PhaseType.GAS);
    SarirAtmosphericMainSteamScreen screen = SarirAtmosphericMainSteamScreen.configure(model, 1,
        steam, "independent saturated-vapor enthalpy calculation");

    assertEquals(1, screen.getInjectionTrayIndex());
    assertSame(steam, screen.getSteamStream());
    assertEquals("independent saturated-vapor enthalpy calculation",
        screen.getThermodynamicStateBasis());
    assertEquals(340.2, screen.getSourceReference().getMassFlowRateKgPerHour(), 0.0);
    assertEquals(1, model.getColumn().getFeedStreams(1).size());
    assertSame(steam, model.getColumn().getFeedStreams(1).get(0));
    assertTrue(!SarirAtmosphericReference.hasExplicitSteamInjectionLocations());
    assertTrue(!SarirAtmosphericReference.hasExplicitSteamQuality());
    assertTrue(!SarirAtmosphericReference.hasExplicitSteamThermodynamicState());
    assertThrows(IllegalStateException.class, screen::evaluate);
  }

  /** Reject missing provenance, inferred locations, wrong boundaries, and non-steam states. */
  @Test
  public void invalidOrUnsupportedPreparedStatesFailBeforeMutation() {
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericMainSteamScreen.configure(createModel(), -1,
            createPreparedStream("water", PhaseType.GAS), "independent basis"));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericMainSteamScreen.configure(createModel(),
            SarirAtmosphericFractionationCase.SIMPLE_TRAY_COUNT,
            createPreparedStream("water", PhaseType.GAS), "independent basis"));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericMainSteamScreen.configure(createModel(), 1,
            createPreparedStream("water", PhaseType.GAS), " "));

    StreamInterface wrongFlow = createPreparedStream("water", PhaseType.GAS);
    wrongFlow.setFlowRate(300.0, "kg/hr");
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericMainSteamScreen.configure(createModel(), 1, wrongFlow,
            "independent basis"));

    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericMainSteamScreen.configure(createModel(), 1,
            createPreparedStream("methane", PhaseType.GAS), "independent basis"));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericMainSteamScreen.configure(createModel(), 1,
            createPreparedStream("water", PhaseType.OIL), "independent basis"));

    SarirAtmosphericFractionationCase augmented = createModel();
    SarirAtmosphericMainSteamScreen.configure(augmented, 1,
        createPreparedStream("water", PhaseType.GAS), "first independent basis");
    assertThrows(IllegalStateException.class,
        () -> SarirAtmosphericMainSteamScreen.configure(augmented, 2,
            createPreparedStream("water", PhaseType.GAS), "second independent basis"));
    assertEquals(1, augmented.getColumn().getFeedStreams(1).size());
    assertEquals(0, augmented.getColumn().getFeedStreams(2).size());
  }

  /** Execute the explicit main-column steam screen and qualify total inlet/product closure. */
  @Test
  @Timeout(value = 240, unit = TimeUnit.SECONDS)
  public void explicitMainColumnSteamScreenRunsConservatively() {
    SarirAtmosphericFractionationCase model = createModel();
    SarirAtmosphericMainSteamScreen screen = SarirAtmosphericMainSteamScreen.configure(model, 1,
        createPreparedStream("water", PhaseType.GAS),
        "independent saturated-vapor enthalpy calculation");

    SarirAtmosphericMainSteamScreen.Result result = screen.run(UUID.randomUUID());

    assertTrue(model.getColumn().solved(), model.getColumn().getConvergenceDiagnostics());
    assertEquals(1, result.getInjectionTrayIndex());
    assertEquals(340.2, result.getSourceMassFlowKgPerHour(), 0.0);
    assertEquals(340.2, result.getModeledMassFlowKgPerHour(), 340.2e-9);
    assertEquals(150.0, result.getModeledTemperatureCelsius(), 1.0e-7);
    assertEquals(476.0, result.getModeledPressureKPa(), 1.0e-7);
    assertEquals(1.0, result.getVaporMoleFraction(), 0.0);
    assertTrue(result.getTotalMassClosureRelativeError() <= 5.0e-2);
    assertTrue(result.getColumnMassBalanceError() <= 5.0e-2);
    assertTrue(result.getColumnEnergyBalanceError() <= 5.0e-2);
  }

  private static StreamInterface createPreparedStream(String component, PhaseType phaseType) {
    double temperatureKelvin =
        SarirAtmosphericReference.getSteamInjection("Main atmospheric column")
            .getTemperatureCelsius() + 273.15;
    double pressureBara =
        SarirAtmosphericReference.getSteamInjection("Main atmospheric column").getPressureKPa()
            / 100.0;
    SystemInterface fluid = new SystemSrkEos(temperatureKelvin, pressureBara);
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(
        SarirAtmosphericReference.getSteamInjection("Main atmospheric column")
            .getMassFlowRateKgPerHour(),
        "kg/hr");
    fluid.setNumberOfPhases(1);
    fluid.setMaxNumberOfPhases(1);
    fluid.setForcePhaseTypes(true);
    fluid.setPhaseType(0, phaseType);
    fluid.init(3);
    return new Stream("explicit Sarir prepared steam", fluid);
  }

  private static SarirAtmosphericFractionationCase createModel() {
    OperatingInputs inputs = new OperatingInputs(1.20,
        SarirAtmosphericReference.getColumnFeedPressureKPa() / 100.0, 700.0, 1.0, 24, 0.08,
        15, 0.15);
    return SarirAtmosphericFractionationCase.create("Sarir main steam screen", SPECIFIC_GRAVITY,
        MOLAR_MASS_KG_PER_MOL, inputs);
  }
}
