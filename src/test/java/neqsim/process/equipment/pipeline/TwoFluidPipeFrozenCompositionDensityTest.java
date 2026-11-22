package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Property updates of a packing line must compress the gas and oil that the cells hold.
 *
 * <p>
 * A 400 m line of volatile oil and gas at 170 bara is closed at the outlet and fed 50 kg/s for 20 s. The transient
 * moves no mass between gas and oil, so the final pressure is fixed by the gas mass, the oil mass and the pipe volume.
 * The reference is the pressure at which gas and oil of the initial phase compositions, each kept single-phase, fill
 * the pipe volume: 229 bara. Resetting each cell to the densities of the equilibrium flash of the feed gave 334 bara,
 * because the equilibrium oil is lighter at higher pressure and the cells were swollen without the dissolved gas that
 * would swell them.
 * </p>
 */
public class TwoFluidPipeFrozenCompositionDensityTest {
  private static final double START_PRESSURE_BARA = 170.0;
  private static final double TEMPERATURE_K = 273.15 + 80.0;
  private static final double FEED_KG_PER_S = 50.0;

  /** Pressure and density history of one pack-up run. */
  private static final class PackUp {
    private double startPressureBara;
    private double endPressureBara;
    private double referencePressureBara;
    private double startOilDensity;
    private double endOilDensity;
    private double largestPressureDropBar;
  }

  /**
   * Creates the volatile-oil feed at a pressure.
   *
   * @param pressureBara pressure in bara
   * @return the feed fluid
   */
  private static SystemInterface feed(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(TEMPERATURE_K, pressureBara);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("n-heptane", 0.10);
    fluid.addComponent("nC10", 0.12);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Packs the closed line and records the pressure of its middle cell.
   *
   * @param frozenCompositionUpdate value for {@link TwoFluidPipe#setFrozenCompositionDensityUpdate(boolean)}
   * @return pressure and density history
   */
  private static PackUp packUp(boolean frozenCompositionUpdate) {
    Stream stream = new Stream("feed", feed(START_PRESSURE_BARA));
    stream.setFlowRate(FEED_KG_PER_S, "kg/sec");
    stream.run();

    int cells = 8;
    TwoFluidPipe pipe = new TwoFluidPipe("pack-up", stream);
    pipe.setLength(400.0);
    pipe.setDiameter(0.3);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(cells);
    pipe.setElevationProfile(new double[cells + 1]);
    pipe.setOutletPressure(START_PRESSURE_BARA, "bara");
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setFrozenCompositionDensityUpdate(frozenCompositionUpdate);
    pipe.run();
    pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CONSTANT_FLOW);
    pipe.setInletMassFlow(FEED_KG_PER_S, "kg/sec");
    pipe.closeOutlet();

    PackUp result = new PackUp();
    int middle = cells / 2;
    TwoFluidSection start = pipe.getSectionSnapshots()[middle];
    result.startPressureBara = start.getPressure() / 1.0e5;
    result.startOilDensity = start.getOilDensity();

    UUID id = UUID.randomUUID();
    double previous = Double.NaN;
    double timeStep = 0.1;
    for (int step = 1; step <= 200; step++) {
      pipe.runTransient(timeStep, id);
      double pressure = pipe.getSectionSnapshots()[middle].getPressure() / 1.0e5;
      // The outlet closes at t = 0 and its pressure wave crosses the line in the first seconds.
      if (step * timeStep > 3.0 && !Double.isNaN(previous)) {
        result.largestPressureDropBar = Math.max(result.largestPressureDropBar, previous - pressure);
      }
      previous = pressure;
    }

    double gasMass = 0.0;
    double oilMass = 0.0;
    double volume = 0.0;
    double temperature = 0.0;
    TwoFluidSection[] sections = pipe.getSectionSnapshots();
    for (TwoFluidSection section : sections) {
      gasMass += section.getGasMassPerLength() * section.getLength();
      oilMass += section.getOilMassPerLength() * section.getLength();
      volume += section.getArea() * section.getLength();
      temperature += section.getTemperature() / sections.length;
    }
    result.endPressureBara = previous;
    result.endOilDensity = sections[middle].getOilDensity();
    result.referencePressureBara = frozenCompositionPressure(gasMass, oilMass, volume, temperature);
    return result;
  }

  /**
   * Finds the pressure at which fixed masses of the initial gas and oil fill a volume.
   *
   * @param gasMass gas mass in kg
   * @param oilMass oil mass in kg
   * @param volume volume in m3
   * @param temperature temperature in K
   * @return pressure in bara
   */
  private static double frozenCompositionPressure(double gasMass, double oilMass, double volume, double temperature) {
    SystemInterface flash = feed(START_PRESSURE_BARA);
    new ThermodynamicOperations(flash).TPflash();
    flash.initProperties();
    SystemInterface gas = singlePhase(flash, "gas", PhaseType.GAS);
    SystemInterface oil = singlePhase(flash, "oil", PhaseType.OIL);
    double low = START_PRESSURE_BARA;
    double high = 600.0;
    for (int iteration = 0; iteration < 50; iteration++) {
      double pressure = 0.5 * (low + high);
      double occupied = gasMass / density(gas, pressure, temperature) + oilMass / density(oil, pressure, temperature);
      if (occupied > volume) {
        low = pressure;
      } else {
        high = pressure;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Builds a one-phase fluid with the composition of a phase of a flash.
   *
   * @param flash flashed fluid
   * @param phaseName phase to copy
   * @param type phase type of the new fluid
   * @return the one-phase fluid
   */
  private static SystemInterface singlePhase(SystemInterface flash, String phaseName, PhaseType type) {
    SystemInterface fluid = new SystemSrkEos(flash.getTemperature(), flash.getPressure());
    for (int i = 0; i < flash.getPhase(phaseName).getNumberOfComponents(); i++) {
      fluid.addComponent(flash.getPhase(phaseName).getComponent(i).getComponentName(),
          flash.getPhase(phaseName).getComponent(i).getx());
    }
    fluid.setMixingRule("classic");
    fluid.setNumberOfPhases(1);
    fluid.setPhaseType(0, type);
    return fluid;
  }

  /**
   * Density of a one-phase fluid.
   *
   * @param fluid one-phase fluid
   * @param pressureBara pressure in bara
   * @param temperature temperature in K
   * @return density in kg/m3
   */
  private static double density(SystemInterface fluid, double pressureBara, double temperature) {
    fluid.setPressure(pressureBara, "bara");
    fluid.setTemperature(temperature, "K");
    fluid.init(0);
    fluid.setNumberOfPhases(1);
    fluid.init(3);
    fluid.initPhysicalProperties();
    return fluid.getPhase(0).getDensity("kg/m3");
  }

  @Test
  void packedLineFollowsFrozenCompositionCompression() {
    TwoFluidPipe defaults = new TwoFluidPipe("defaults", new Stream("unused", feed(START_PRESSURE_BARA)));
    assertTrue(defaults.isFrozenCompositionDensityUpdate());

    PackUp run = packUp(true);
    double rise = run.endPressureBara - run.startPressureBara;
    double referenceRise = run.referencePressureBara - run.startPressureBara;

    assertTrue(referenceRise > 40.0, "The case must compress the line far enough to test the update");
    assertEquals(referenceRise, rise, 0.15 * referenceRise,
        "Pressure rise must match the compression of the gas and oil in the pipe");
    assertTrue(run.endOilDensity > run.startOilDensity, "Oil that keeps its composition is denser at higher pressure");
    // Pressure rises about 1.3 bar between property updates on the isentropic response of the pressure solve; each
    // update takes back the 0.1 bar by which that exceeds the isothermal response.
    assertTrue(run.largestPressureDropBar < 0.3,
        "Pressure of a line that only receives mass must not alternate: " + run.largestPressureDropBar + " bar");
  }

  @Test
  void equilibriumResetSwellsTheOilAndOverstatesThePressureRise() {
    PackUp run = packUp(false);
    double rise = run.endPressureBara - run.startPressureBara;
    double referenceRise = run.referencePressureBara - run.startPressureBara;

    assertFalse(run.endOilDensity > run.startOilDensity,
        "The equilibrium oil of the feed is lighter at the higher pressure");
    assertTrue(rise > 2.0 * referenceRise,
        "Resetting to equilibrium densities at fixed phase masses more than doubles the pressure rise");
  }
}
