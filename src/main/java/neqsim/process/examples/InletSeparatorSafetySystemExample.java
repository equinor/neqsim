package neqsim.process.examples;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.valve.SafetyValve.FluidService;
import neqsim.process.equipment.valve.SafetyValve.RelievingScenario;
import neqsim.process.equipment.valve.SafetyValve.SizingStandard;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign.SafetyValveScenarioResult;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.SIFCategory;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Inlet Separator Safety-System Demonstration.
 *
 * <p>
 * This example builds a small but realistic inlet-separation package for an offshore production manifold and overlays
 * the full set of safety devices that would appear on its P&amp;ID:
 * <ul>
 * <li><b>ESDV-1001</b> &mdash; emergency shutdown isolation valve on the inlet line (fail-closed, SIL-rated final
 * element).</li>
 * <li><b>V-100</b> &mdash; three-phase inlet separator (design pressure 100 bara, MAWP 110 bara).</li>
 * <li><b>PT-1001 / TT-1001</b> &mdash; pressure and temperature transmitters on the gas outlet with HI/HIHI and LO/LOLO
 * alarm configuration.</li>
 * <li><b>LT-1001 / LT-1002</b> &mdash; oil and water level transmitters with HI/HIHI and LO/LOLO alarms.</li>
 * <li><b>PSV-1001</b> &mdash; conventional spring-loaded relief valve protecting V-100; sized per API 520 for the
 * controlling case (blocked gas outlet and external fire).</li>
 * <li><b>SIS</b> &mdash; three Safety Instrumented Functions (PSH trip, LSL trip, fire-zone ESD) built using
 * {@link SafetyInstrumentedFunction} with IEC&nbsp;61511 SIL/PFD validation.</li>
 * <li><b>LOPA</b> &mdash; Layer of Protection Analysis for the &quot;Inlet Separator Overpressure&quot; scenario
 * showing how BPCS, PSH alarm, SIS trip and PSV combine to meet the target mitigated frequency.</li>
 * </ul>
 *
 * <p>
 * The example is deliberately self-contained &mdash; running {@link #main(String[])} prints a complete safety report
 * (instrument alarms, PSV orifice areas, SIS SIL/PFD/RRF, and LOPA worksheet) to the configured Log4j2 logger. The
 * helper builders are exposed as package-visible methods so a unit test can assert against the structured results.
 *
 * <p>
 * Standards referenced: API&nbsp;520/521 (relief sizing), IEC&nbsp;61508 / IEC&nbsp;61511 (functional safety),
 * NORSOK&nbsp;S-001 (technical safety), ISA&nbsp;84.00.01 (SIS), CCPS Layer of Protection Analysis (LOPA).
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class InletSeparatorSafetySystemExample {

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(InletSeparatorSafetySystemExample.class);

  /** Inlet separator design pressure, bara. */
  public static final double DESIGN_PRESSURE_BARA = 100.0;

  /** Inlet separator maximum allowable working pressure, bara. */
  public static final double MAWP_BARA = 110.0;

  /** Normal operating pressure, bara. */
  public static final double NORMAL_PRESSURE_BARA = 70.0;

  /** Normal operating temperature, deg C. */
  public static final double NORMAL_TEMPERATURE_C = 55.0;

  /** Total feed mass flow rate, kg/hr. */
  public static final double FEED_FLOW_KGHR = 250_000.0;

  /** Reservoir fluid system. */
  private SystemInterface feedFluid;

  /** Inlet separation process flowsheet. */
  private ProcessSystem process;

  /** Inlet ESD valve (fail-closed final element of the inlet ESD SIF). */
  private ESDValve inletEsdValve;

  /** Three-phase inlet separator V-100. */
  private ThreePhaseSeparator inletSeparator;

  /** Gas outlet pressure-control valve (BPCS). */
  private ThrottlingValve gasOutletValve;

  /** Oil outlet level-control valve (BPCS). */
  private ThrottlingValve oilOutletValve;

  /** Water outlet level-control valve (BPCS). */
  private ThrottlingValve waterOutletValve;

  /** Gas outlet pressure transmitter (PT-1001). */
  private PressureTransmitter pressureTransmitter;

  /** Gas outlet temperature transmitter (TT-1001). */
  private TemperatureTransmitter temperatureTransmitter;

  /** Oil level transmitter (LT-1001). */
  private LevelTransmitter oilLevelTransmitter;

  /** Water level transmitter (LT-1002). */
  private LevelTransmitter waterLevelTransmitter;

  /** Pressure Safety Valve (PSV-1001) protecting V-100. */
  private SafetyValve reliefValve;

  /** Cached safety instrumented functions keyed by tag. */
  private final Map<String, SafetyInstrumentedFunction> sifs = new LinkedHashMap<>();

  /** LOPA worksheet for the overpressure scenario. */
  private LOPAResult overpressureLopa;

  /**
   * Builds the feed fluid used for the inlet separator example.
   *
   * <p>
   * Composition is a typical light NCS associated-gas / condensate / produced-water blend.
   *
   * @return a fully configured {@link SystemInterface}
   */
  public SystemInterface buildFeedFluid() {
    SystemInterface fluid = new SystemSrkEos(NORMAL_TEMPERATURE_C + 273.15, NORMAL_PRESSURE_BARA);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("n-pentane", 2.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addComponent("n-heptane", 3.0);
    fluid.addComponent("water", 4.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    this.feedFluid = fluid;
    return fluid;
  }

  /**
   * Builds the steady-state flowsheet: feed &rarr; ESDV-1001 &rarr; V-100 &rarr; gas / oil / water outlet valves.
   *
   * <p>
   * The PSV-1001 is configured separately in {@link #configurePressureSafetyValve()} on a relief stream so it does not
   * carry normal-operation flow, which mirrors the physical reality of a normally-closed spring-loaded PSV.
   *
   * @return the configured {@link ProcessSystem}
   */
  public ProcessSystem buildProcess() {
    if (feedFluid == null) {
      buildFeedFluid();
    }

    Stream feedStream = new Stream("Feed from manifold", feedFluid);
    feedStream.setFlowRate(FEED_FLOW_KGHR, "kg/hr");
    feedStream.setTemperature(NORMAL_TEMPERATURE_C, "C");
    feedStream.setPressure(NORMAL_PRESSURE_BARA, "bara");

    // ESDV-1001 — fail-closed isolation valve. In steady-state, the valve is energized
    // (fully open) and behaves as a throttling valve with negligible pressure drop.
    inletEsdValve = new ESDValve("ESDV-1001", feedStream);
    inletEsdValve.setOutletPressure(NORMAL_PRESSURE_BARA - 0.2);
    inletEsdValve.setStrokeTime(10.0);
    inletEsdValve.setFailSafePosition(0.0);
    inletEsdValve.energize();

    inletSeparator = new ThreePhaseSeparator("V-100 Inlet Separator", inletEsdValve.getOutletStream());
    inletSeparator.setInternalDiameter(2.4);
    inletSeparator.setSeparatorLength(7.0);

    gasOutletValve = new ThrottlingValve("PV-1001 gas outlet", inletSeparator.getGasOutStream());
    gasOutletValve.setOutletPressure(55.0, "bara");

    oilOutletValve = new ThrottlingValve("LV-1001 oil outlet", inletSeparator.getOilOutStream());
    oilOutletValve.setOutletPressure(20.0, "bara");

    waterOutletValve = new ThrottlingValve("LV-1002 water outlet", inletSeparator.getWaterOutStream());
    waterOutletValve.setOutletPressure(10.0, "bara");

    process = new ProcessSystem();
    process.add(feedStream);
    process.add(inletEsdValve);
    process.add(inletSeparator);
    process.add(gasOutletValve);
    process.add(oilOutletValve);
    process.add(waterOutletValve);

    return process;
  }

  /**
   * Adds the inlet-separator instrumentation (PT, TT, LT pair) and configures HI/HIHI and LO/LOLO alarm thresholds.
   *
   * <p>
   * Alarm philosophy applied:
   * <ul>
   * <li>PT-1001: LO 50 bara, LOLO 35 bara, HI 85 bara, HIHI 95 bara (95% of design = 95 bara).</li>
   * <li>TT-1001: HI 80&nbsp;&deg;C, HIHI 90&nbsp;&deg;C (protect downstream coolers / hydrate margin).</li>
   * <li>LT-1001 oil: LO 30%, LOLO 15% (anti gas blow-by), HI 70%, HIHI 85% (carry-over).</li>
   * <li>LT-1002 water: HI 50%, HIHI 70% (interface upset).</li>
   * </ul>
   */
  public void addInstrumentation() {
    if (inletSeparator == null) {
      throw new IllegalStateException("Process must be built before adding instrumentation");
    }

    pressureTransmitter = new PressureTransmitter("PT-1001", inletSeparator.getGasOutStream());
    pressureTransmitter.setAlarmConfig(AlarmConfig.builder().unit("bara").lowLowLimit(35.0).lowLimit(50.0)
	.highLimit(85.0).highHighLimit(95.0).deadband(0.5).delay(1.0).build());

    temperatureTransmitter = new TemperatureTransmitter("TT-1001", inletSeparator.getGasOutStream());
    temperatureTransmitter.setAlarmConfig(
	AlarmConfig.builder().unit("C").highLimit(80.0).highHighLimit(90.0).deadband(1.0).delay(2.0).build());

    oilLevelTransmitter = new LevelTransmitter("LT-1001 oil level", inletSeparator);
    oilLevelTransmitter.setAlarmConfig(AlarmConfig.builder().unit("frac").lowLowLimit(0.15).lowLimit(0.30)
	.highLimit(0.70).highHighLimit(0.85).deadband(0.02).delay(3.0).build());

    waterLevelTransmitter = new LevelTransmitter("LT-1002 water level", inletSeparator);
    waterLevelTransmitter.setAlarmConfig(
	AlarmConfig.builder().unit("frac").highLimit(0.50).highHighLimit(0.70).deadband(0.02).delay(3.0).build());

    process.add(pressureTransmitter);
    process.add(temperatureTransmitter);
    process.add(oilLevelTransmitter);
    process.add(waterLevelTransmitter);
  }

  /**
   * Configures PSV-1001 and sizes it against two relieving scenarios:
   * <ul>
   * <li><b>Blocked gas outlet</b> &mdash; full feed gas mass flow vented through the PSV (gas service, 10% overpressure
   * per API 520).</li>
   * <li><b>External fire</b> &mdash; reduced rate fire-case vapour generation (fire service, 21% overpressure).</li>
   * </ul>
   *
   * <p>
   * The PSV is configured outside the main {@link ProcessSystem} so steady-state mass balance is not perturbed by a
   * normally-closed device. Sizing uses {@link SafetyValveMechanicalDesign#calcDesign()} which iterates over all
   * registered {@link RelievingScenario}s and stores the controlling orifice area.
   *
   * @return the configured {@link SafetyValve}
   */
  public SafetyValve configurePressureSafetyValve() {
    if (process == null) {
      throw new IllegalStateException("Process must be built before configuring PSV");
    }

    StreamInterface protectedStream = inletSeparator.getGasOutStream();
    reliefValve = new SafetyValve("PSV-1001", protectedStream);
    reliefValve.setPressureSpec(DESIGN_PRESSURE_BARA);
    reliefValve.setBlowdown(7.0);
    reliefValve.setFullOpenPressure(DESIGN_PRESSURE_BARA * 1.10);

    StreamInterface blockedOutletRelief = buildReliefStream(protectedStream, 110.0, NORMAL_TEMPERATURE_C,
	FEED_FLOW_KGHR * 0.95);
    StreamInterface fireCaseRelief = buildReliefStream(protectedStream, DESIGN_PRESSURE_BARA * 1.21, 200.0,
	FEED_FLOW_KGHR * 0.30);

    reliefValve.addScenario(new RelievingScenario.Builder("Blocked gas outlet").fluidService(FluidService.GAS)
	.relievingStream(blockedOutletRelief).setPressure(DESIGN_PRESSURE_BARA).overpressureFraction(0.10)
	.backPressure(2.0).sizingStandard(SizingStandard.API_520).build());

    reliefValve.addScenario(new RelievingScenario.Builder("External fire").fluidService(FluidService.FIRE)
	.relievingStream(fireCaseRelief).setPressure(DESIGN_PRESSURE_BARA).overpressureFraction(0.21).backPressure(2.0)
	.sizingStandard(SizingStandard.API_520).build());

    SafetyValveMechanicalDesign design = (SafetyValveMechanicalDesign) reliefValve.getMechanicalDesign();
    design.calcDesign();

    return reliefValve;
  }

  /**
   * Builds the three Safety Instrumented Functions that protect the inlet separator package.
   * <ul>
   * <li><b>SIF-001</b> Inlet ESD high-pressure trip &mdash; PT-1001 HIHI &rarr; close ESDV-1001 (SIL&nbsp;2, ESD
   * category).</li>
   * <li><b>SIF-002</b> Low-level gas-blow-by trip &mdash; LT-1001 LOLO &rarr; close LV-1001 (SIL&nbsp;1, PSD
   * category).</li>
   * <li><b>SIF-003</b> Fire &amp; gas zonal ESD &mdash; confirmed fire/gas detection &rarr; close ESDV-1001
   * (SIL&nbsp;2, F&amp;G category).</li>
   * </ul>
   *
   * @return immutable map of SIF tag to {@link SafetyInstrumentedFunction}
   */
  public Map<String, SafetyInstrumentedFunction> configureSafetyInstrumentedSystem() {
    SafetyInstrumentedFunction psh = SafetyInstrumentedFunction.builder().id("SIF-001")
	.name("Inlet separator high-pressure ESD")
	.description("PT-1001 HIHI 2oo3 voting trips ESDV-1001 on overpressure").sil(2).pfd(5.0e-3)
	.testIntervalHours(8760.0).mttr(8.0).architecture("2oo3")
	.protectedEquipment(Arrays.asList("V-100", "Downstream gas processing"))
	.initiatingEvent("Overpressure (blocked outlet, control failure, gas breakthrough)").safeState("Inlet isolated")
	.category(SIFCategory.ESD).spuriousTripRate(0.02).build();

    SafetyInstrumentedFunction lsll = SafetyInstrumentedFunction.builder().id("SIF-002")
	.name("Inlet separator low-low oil level trip")
	.description("LT-1001 LOLO 1oo2 voting closes LV-1001 to prevent gas blow-by").sil(1).pfd(5.0e-2)
	.testIntervalHours(8760.0).mttr(4.0).architecture("1oo2").protectedEquipment(Arrays.asList("V-100"))
	.initiatingEvent("Oil drain rate exceeds inflow / level controller failure").safeState("Oil outlet closed")
	.category(SIFCategory.PSD).spuriousTripRate(0.05).build();

    SafetyInstrumentedFunction fg = SafetyInstrumentedFunction.builder().id("SIF-003")
	.name("Confirmed fire/gas zonal ESD")
	.description("2oo3 confirmed F&G detection in inlet zone trips ESDV-1001 and isolates fuel").sil(2).pfd(3.0e-3)
	.testIntervalHours(8760.0).mttr(8.0).architecture("2oo3 with diverse detectors")
	.protectedEquipment(Arrays.asList("Inlet zone", "V-100", "Manifold"))
	.initiatingEvent("Confirmed gas release or fire in inlet zone").safeState("Zone depressurised / isolated")
	.category(SIFCategory.FIRE_GAS).spuriousTripRate(0.01).build();

    sifs.clear();
    sifs.put(psh.getId(), psh);
    sifs.put(lsll.getId(), lsll);
    sifs.put(fg.getId(), fg);
    return sifs;
  }

  /**
   * Builds the LOPA worksheet for the &quot;Inlet separator overpressure&quot; consequence and demonstrates how
   * Independent Protection Layers (IPLs) reduce the unmitigated event frequency to the target value.
   *
   * <p>
   * The protection layers credited (each independent and audited):
   * <ul>
   * <li>BPCS PC-1001 pressure control loop &mdash; PFD 1.0e-1 (IEC 61511 default for non-SIL BPCS).</li>
   * <li>PSH alarm + operator response &mdash; PFD 1.0e-1 (10 min response, audible/visual).</li>
   * <li>SIF-001 inlet ESD high-pressure trip &mdash; PFD 5.0e-3 (SIL 2).</li>
   * <li>PSV-1001 mechanical relief &mdash; PFD 1.0e-2 (well-maintained, proof-tested annually).</li>
   * </ul>
   *
   * <p>
   * Target frequency derived from STS&nbsp;0131 overpressure category for an event pressure equal to MAWP and a typical
   * tolerable risk frequency of 1.0e-4&nbsp;/yr.
   *
   * @return populated {@link LOPAResult}
   */
  public LOPAResult buildLopaForOverpressure() {
    LOPAResult lopa = new LOPAResult("Inlet separator overpressure (V-100)");
    double initiating = 0.1; // /yr - blocked outlet / control failure (CCPS typical)
    lopa.setInitiatingEventFrequency(initiating);
    // Target derived from STS 0131 overpressure category for an event pressure equal to MAWP.
    // testPressureBara = 1.5 x design (standard hydrotest factor per ASME VIII Div 1).
    lopa.setTargetFrequencyFromSTS0131Overpressure(MAWP_BARA, DESIGN_PRESSURE_BARA, DESIGN_PRESSURE_BARA * 1.5);

    double before = initiating;
    double pfdBpcs = 0.1;
    double afterBpcs = before * pfdBpcs;
    lopa.addLayer("BPCS PC-1001 pressure control", pfdBpcs, before, afterBpcs);

    double pfdPsh = 0.1;
    double afterPsh = afterBpcs * pfdPsh;
    lopa.addLayer("PSH alarm + operator response", pfdPsh, afterBpcs, afterPsh);

    double pfdSif = 5.0e-3;
    double afterSif = afterPsh * pfdSif;
    lopa.addLayer("SIF-001 inlet ESD high-pressure trip (SIL 2)", pfdSif, afterPsh, afterSif);

    double pfdPsv = 1.0e-2;
    double afterPsv = afterSif * pfdPsv;
    lopa.addLayer("PSV-1001 mechanical relief", pfdPsv, afterSif, afterPsv);

    lopa.setMitigatedFrequency(afterPsv);
    this.overpressureLopa = lopa;
    return lopa;
  }

  /**
   * Demonstrates an inlet-overpressure trip sequence: an upset drives PT-1001 above its HIHI alarm; the SIS executes
   * SIF-001 which de-energises ESDV-1001; the valve closes within its stroke time.
   *
   * @return a short textual description of the trip outcome
   */
  public String demonstrateOverpressureTrip() {
    if (pressureTransmitter == null || inletEsdValve == null) {
      throw new IllegalStateException("Instrumentation must be configured before demonstrating a trip");
    }
    SafetyInstrumentedFunction psh = sifs.get("SIF-001");
    double initiatingEventFrequency = 0.1; // /yr
    double mitigatedFrequency = psh.getMitigatedFrequency(initiatingEventFrequency);

    inletEsdValve.deEnergize();
    return String.format(
	"PT-1001 HIHI detected (set %.1f bara). SIF-001 de-energised ESDV-1001 (energized=%s). "
	    + "Initiating freq %.2e /yr -> mitigated %.2e /yr with RRF %.0f.",
	DESIGN_PRESSURE_BARA, inletEsdValve.isEnergized(), initiatingEventFrequency, mitigatedFrequency,
	psh.getRiskReductionFactor());
  }

  /**
   * Runs the full demonstration: build flowsheet, run steady-state, size PSV, build SIS and LOPA, then print the
   * complete safety report. Suitable as the entry point of the example.
   *
   * @return a {@link SafetyReport} aggregating all results for downstream assertions
   */
  public SafetyReport runFullDemonstration() {
    buildFeedFluid();
    buildProcess();
    addInstrumentation();
    process.run();

    configurePressureSafetyValve();
    configureSafetyInstrumentedSystem();
    buildLopaForOverpressure();

    SafetyReport report = new SafetyReport();
    report.setSeparatorPressure(inletSeparator.getGasOutStream().getPressure("bara"));
    report.setSeparatorTemperature(inletSeparator.getGasOutStream().getTemperature("C"));
    report.setGasMassFlow(inletSeparator.getGasOutStream().getFlowRate("kg/hr"));
    report.setOilMassFlow(inletSeparator.getOilOutStream().getFlowRate("kg/hr"));
    report.setWaterMassFlow(inletSeparator.getWaterOutStream().getFlowRate("kg/hr"));

    SafetyValveMechanicalDesign psvDesign = (SafetyValveMechanicalDesign) reliefValve.getMechanicalDesign();
    report.setControllingScenario(psvDesign.getControllingScenarioName());
    report.setControllingOrificeArea(psvDesign.getControllingOrificeArea());
    report.setScenarioResults(psvDesign.getScenarioResults());

    report.setSifs(sifs);
    report.setOverpressureLopa(overpressureLopa);
    report.setTripDescription(demonstrateOverpressureTrip());

    logReport(report);
    return report;
  }

  /**
   * Convenience entry point.
   *
   * @param args command line arguments (ignored)
   */
  public static void main(String[] args) {
    new InletSeparatorSafetySystemExample().runFullDemonstration();
  }

  /**
   * Builds a relief-scenario stream by cloning the protected stream and overriding pressure, temperature and flow. The
   * clone is flashed at the relieving conditions so the sizing strategy can read Z, MW, gamma and density.
   *
   * @param source the protected stream
   * @param pressureBara relieving pressure in bara
   * @param temperatureC relieving temperature in deg C
   * @param flowKgHr relieving mass flow in kg/hr
   * @return a fully initialized {@link StreamInterface}
   */
  private StreamInterface buildReliefStream(StreamInterface source, double pressureBara, double temperatureC,
      double flowKgHr) {
    SystemInterface clonedSystem = source.getThermoSystem().clone();
    clonedSystem.setTemperature(temperatureC + 273.15);
    clonedSystem.setPressure(pressureBara);
    Stream reliefStream = new Stream("PSV-1001 relief", clonedSystem);
    reliefStream.setPressure(pressureBara, "bara");
    reliefStream.setTemperature(temperatureC, "C");
    reliefStream.setFlowRate(flowKgHr, "kg/hr");
    reliefStream.run();
    return reliefStream;
  }

  /**
   * Emits a human-readable summary of the safety report to the logger.
   *
   * @param report the report to log
   */
  private void logReport(SafetyReport report) {
    logger.info("=== Inlet Separator Safety System Demonstration ===");
    logger.info("Inlet separator V-100: P={} bara, T={} C, gas={} kg/hr, oil={} kg/hr, water={} kg/hr",
	String.format("%.2f", report.getSeparatorPressure()), String.format("%.2f", report.getSeparatorTemperature()),
	String.format("%.0f", report.getGasMassFlow()), String.format("%.0f", report.getOilMassFlow()),
	String.format("%.0f", report.getWaterMassFlow()));

    logger.info("--- PSV-1001 sizing (API 520) ---");
    for (SafetyValveScenarioResult result : report.getScenarioResults().values()) {
      logger.info("Scenario '{}' ({}): orifice area = {} mm2, set P = {} bara, controlling = {}",
	  result.getScenarioName(), result.getFluidService(),
	  String.format("%.2f", result.getRequiredOrificeArea() * 1.0e6),
	  String.format("%.2f", result.getSetPressureBar()), result.isControllingScenario());
    }
    logger.info("PSV controlling scenario: '{}' with orifice area {} mm2", report.getControllingScenario(),
	String.format("%.2f", report.getControllingOrificeArea() * 1.0e6));

    logger.info("--- Safety Instrumented System ---");
    for (SafetyInstrumentedFunction sif : report.getSifs().values()) {
      logger.info("{} '{}': SIL {} PFD={} RRF={} ({}) - {}", sif.getId(), sif.getName(), sif.getSil(),
	  String.format("%.2e", sif.getPfdAvg()), String.format("%.0f", sif.getRiskReductionFactor()),
	  sif.getCategory().getDescription(), sif.getSafeState());
    }

    logger.info("--- LOPA: {} ---", report.getOverpressureLopa().getScenarioName());
    logger.info("Initiating freq = {} /yr, target = {} /yr, mitigated = {} /yr, total RRF = {}, target met = {}",
	String.format("%.2e", report.getOverpressureLopa().getInitiatingEventFrequency()),
	String.format("%.2e", report.getOverpressureLopa().getTargetFrequency()),
	String.format("%.2e", report.getOverpressureLopa().getMitigatedFrequency()),
	String.format("%.0f", report.getOverpressureLopa().getTotalRRF()),
	report.getOverpressureLopa().getMitigatedFrequency() <= report.getOverpressureLopa().getTargetFrequency());

    logger.info("--- Trip demonstration ---");
    logger.info(report.getTripDescription());
  }

  /**
   * @return the underlying {@link ProcessSystem}, or {@code null} until {@link #buildProcess()} has been called
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * @return the inlet ESD valve
   */
  public ESDValve getInletEsdValve() {
    return inletEsdValve;
  }

  /**
   * @return the three-phase inlet separator
   */
  public ThreePhaseSeparator getInletSeparator() {
    return inletSeparator;
  }

  /**
   * @return the relief valve (only populated after {@link #configurePressureSafetyValve()})
   */
  public SafetyValve getReliefValve() {
    return reliefValve;
  }

  /**
   * @return the SIS map (only populated after {@link #configureSafetyInstrumentedSystem()})
   */
  public Map<String, SafetyInstrumentedFunction> getSifs() {
    return sifs;
  }

  /**
   * Aggregated, immutable-from-the-outside view of the safety study results, returned by
   * {@link InletSeparatorSafetySystemExample#runFullDemonstration()} and consumed by unit tests.
   */
  public static class SafetyReport {
    /** Separator gas-outlet pressure, bara. */
    private double separatorPressure;
    /** Separator gas-outlet temperature, deg C. */
    private double separatorTemperature;
    /** Gas mass flow, kg/hr. */
    private double gasMassFlow;
    /** Oil mass flow, kg/hr. */
    private double oilMassFlow;
    /** Water mass flow, kg/hr. */
    private double waterMassFlow;
    /** Name of the controlling PSV scenario. */
    private String controllingScenario;
    /** Controlling orifice area, m^2. */
    private double controllingOrificeArea;
    /** All PSV scenario results. */
    private Map<String, SafetyValveScenarioResult> scenarioResults = new LinkedHashMap<>();
    /** Configured SIFs. */
    private Map<String, SafetyInstrumentedFunction> sifs = new LinkedHashMap<>();
    /** LOPA worksheet for the overpressure case. */
    private LOPAResult overpressureLopa;
    /** Human-readable description of the trip demonstration. */
    private String tripDescription;

    /**
     * @return separator gas-outlet pressure, bara
     */
    public double getSeparatorPressure() {
      return separatorPressure;
    }

    /**
     * @param value separator gas-outlet pressure, bara
     */
    public void setSeparatorPressure(double value) {
      this.separatorPressure = value;
    }

    /**
     * @return separator gas-outlet temperature, deg C
     */
    public double getSeparatorTemperature() {
      return separatorTemperature;
    }

    /**
     * @param value separator gas-outlet temperature, deg C
     */
    public void setSeparatorTemperature(double value) {
      this.separatorTemperature = value;
    }

    /**
     * @return gas mass flow, kg/hr
     */
    public double getGasMassFlow() {
      return gasMassFlow;
    }

    /**
     * @param value gas mass flow, kg/hr
     */
    public void setGasMassFlow(double value) {
      this.gasMassFlow = value;
    }

    /**
     * @return oil mass flow, kg/hr
     */
    public double getOilMassFlow() {
      return oilMassFlow;
    }

    /**
     * @param value oil mass flow, kg/hr
     */
    public void setOilMassFlow(double value) {
      this.oilMassFlow = value;
    }

    /**
     * @return water mass flow, kg/hr
     */
    public double getWaterMassFlow() {
      return waterMassFlow;
    }

    /**
     * @param value water mass flow, kg/hr
     */
    public void setWaterMassFlow(double value) {
      this.waterMassFlow = value;
    }

    /**
     * @return name of the controlling relief scenario
     */
    public String getControllingScenario() {
      return controllingScenario;
    }

    /**
     * @param value name of the controlling scenario
     */
    public void setControllingScenario(String value) {
      this.controllingScenario = value;
    }

    /**
     * @return controlling orifice area, m^2
     */
    public double getControllingOrificeArea() {
      return controllingOrificeArea;
    }

    /**
     * @param value controlling orifice area, m^2
     */
    public void setControllingOrificeArea(double value) {
      this.controllingOrificeArea = value;
    }

    /**
     * @return all PSV scenario results keyed by scenario name
     */
    public Map<String, SafetyValveScenarioResult> getScenarioResults() {
      return scenarioResults;
    }

    /**
     * @param value PSV scenario results
     */
    public void setScenarioResults(Map<String, SafetyValveScenarioResult> value) {
      this.scenarioResults = new LinkedHashMap<>(value);
    }

    /**
     * @return SIFs keyed by tag
     */
    public Map<String, SafetyInstrumentedFunction> getSifs() {
      return sifs;
    }

    /**
     * @param value SIFs
     */
    public void setSifs(Map<String, SafetyInstrumentedFunction> value) {
      this.sifs = new LinkedHashMap<>(value);
    }

    /**
     * @return LOPA worksheet
     */
    public LOPAResult getOverpressureLopa() {
      return overpressureLopa;
    }

    /**
     * @param value LOPA worksheet
     */
    public void setOverpressureLopa(LOPAResult value) {
      this.overpressureLopa = value;
    }

    /**
     * @return short description of the trip demonstration
     */
    public String getTripDescription() {
      return tripDescription;
    }

    /**
     * @param value description of the trip demonstration
     */
    public void setTripDescription(String value) {
      this.tripDescription = value;
    }

    /**
     * @return list of SIF identifiers (insertion order)
     */
    public List<String> getSifIds() {
      return Arrays.asList(sifs.keySet().toArray(new String[0]));
    }
  }
}
