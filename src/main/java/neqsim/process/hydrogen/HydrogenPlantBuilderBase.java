package neqsim.process.hydrogen;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Shared stream factory methods for hydrogen-production plant builders.
 *
 * <p>
 * The helper creates simple methane, steam, oxygen, fuel, and air streams with the syngas product
 * components seeded at trace amounts so equilibrium reactors can form hydrogen, carbon monoxide,
 * and carbon dioxide without requiring callers to remember the component bookkeeping.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
abstract class HydrogenPlantBuilderBase {
  /** Trace mole amount used for product seeding. */
  private static final double TRACE = 1.0e-20;

  /**
   * Creates a methane and steam feed stream.
   *
   * @param name stream name
   * @param methaneMolesPerSec methane flow in mole/sec
   * @param steamToCarbon steam-to-carbon molar ratio
   * @param temperatureK feed temperature in Kelvin
   * @param pressureBara feed pressure in bara
   * @return configured stream
   */
  protected Stream createMethaneSteamFeed(String name, double methaneMolesPerSec,
      double steamToCarbon, double temperatureK, double pressureBara) {
    SystemInterface system = createBaseSystem(temperatureK, pressureBara);
    system.addComponent("methane", methaneMolesPerSec, "mole/sec");
    system.addComponent("water", methaneMolesPerSec * steamToCarbon, "mole/sec");
    seedSyngasProducts(system);
    Stream stream = new Stream(name, system);
    stream.setFlowRate(system.getFlowRate("mole/sec"), "mole/sec");
    return stream;
  }

  /**
   * Creates a methane, steam, and oxygen feed stream.
   *
   * @param name stream name
   * @param methaneMolesPerSec methane flow in mole/sec
   * @param steamToCarbon steam-to-carbon molar ratio
   * @param oxygenToCarbon oxygen-to-carbon molar ratio
   * @param temperatureK feed temperature in Kelvin
   * @param pressureBara feed pressure in bara
   * @return configured stream
   */
  protected Stream createMethaneSteamOxygenFeed(String name, double methaneMolesPerSec,
      double steamToCarbon, double oxygenToCarbon, double temperatureK, double pressureBara) {
    SystemInterface system = createBaseSystem(temperatureK, pressureBara);
    system.addComponent("methane", methaneMolesPerSec, "mole/sec");
    system.addComponent("water", methaneMolesPerSec * steamToCarbon, "mole/sec");
    system.addComponent("oxygen", methaneMolesPerSec * oxygenToCarbon, "mole/sec");
    seedSyngasProducts(system);
    Stream stream = new Stream(name, system);
    stream.setFlowRate(system.getFlowRate("mole/sec"), "mole/sec");
    return stream;
  }

  /**
   * Creates a methane fuel stream.
   *
   * @param name stream name
   * @param methaneMolesPerSec methane flow in mole/sec
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured stream
   */
  protected Stream createMethaneFuel(String name, double methaneMolesPerSec, double temperatureK,
      double pressureBara) {
    SystemInterface system = createBaseSystem(temperatureK, pressureBara);
    system.addComponent("methane", methaneMolesPerSec, "mole/sec");
    system.addComponent("CO2", TRACE, "mole/sec");
    system.addComponent("CO", TRACE, "mole/sec");
    system.addComponent("water", TRACE, "mole/sec");
    system.addComponent("oxygen", TRACE, "mole/sec");
    system.addComponent("nitrogen", TRACE, "mole/sec");
    initialize(system);
    Stream stream = new Stream(name, system);
    stream.setFlowRate(system.getFlowRate("mole/sec"), "mole/sec");
    return stream;
  }

  /**
   * Creates a dry air stream.
   *
   * @param name stream name
   * @param oxygenMolesPerSec oxygen flow in mole/sec
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured air stream
   */
  protected Stream createAir(String name, double oxygenMolesPerSec, double temperatureK,
      double pressureBara) {
    SystemInterface system = createBaseSystem(temperatureK, pressureBara);
    system.addComponent("oxygen", oxygenMolesPerSec, "mole/sec");
    system.addComponent("nitrogen", oxygenMolesPerSec * 3.76, "mole/sec");
    system.addComponent("water", TRACE, "mole/sec");
    system.addComponent("CO2", TRACE, "mole/sec");
    initialize(system);
    Stream stream = new Stream(name, system);
    stream.setFlowRate(system.getFlowRate("mole/sec"), "mole/sec");
    return stream;
  }

  /**
   * Creates a base SRK thermodynamic system.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return thermodynamic system
   */
  private SystemInterface createBaseSystem(double temperatureK, double pressureBara) {
    return new SystemSrkEos(temperatureK, pressureBara);
  }

  /**
   * Adds trace syngas products and initializes the system.
   *
   * @param system thermodynamic system to update
   */
  private void seedSyngasProducts(SystemInterface system) {
    system.addComponent("oxygen", TRACE, "mole/sec");
    system.addComponent("hydrogen", TRACE, "mole/sec");
    system.addComponent("CO", TRACE, "mole/sec");
    system.addComponent("CO2", TRACE, "mole/sec");
    system.addComponent("nitrogen", TRACE, "mole/sec");
    initialize(system);
  }

  /**
   * Initializes component database and thermodynamic state.
   *
   * @param system thermodynamic system to initialize
   */
  private void initialize(SystemInterface system) {
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);
    system.init(3);
  }
}
