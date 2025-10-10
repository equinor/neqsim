package neqsim.process.equipment.reactor;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Two-port equipment that encapsulates the common CO2/acid gas Gibbs reactor sequence used in
 * tests. It takes an inlet Stream (set via setInletStream) and updates the outlet stream after
 * running one or two GibbsReactors depending on inlet composition.
 */
public class GibbsReactorCO2 extends TwoPortEquipment {

  private static final long serialVersionUID = 1L;

  public GibbsReactorCO2(String name) {
    super(name);
  }

  public GibbsReactorCO2(String name, StreamInterface inlet) {
    super(name, inlet);
  }

  @Override
  public void run(UUID id) {
    StreamInterface inlet = getInletStream();
    if (inlet == null) {
      return;
    }

    GibbsReactor reactor = new GibbsReactor("Gibbs Reactor", inlet);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(0.01);
    reactor.setMaxIterations(15000);
    reactor.setConvergenceTolerance(1e-3);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    // Always mark CO and COS as inert for these reactor runs
    reactor.setComponentAsInert("CO");
    reactor.setComponentAsInert("COS");

    SystemInterface outletSystem2 = null;

    try {
      // Use ppm thresholds from inlet thermo system components
      double no2ppm = 0.0;
      double h2sppm = 0.0;
      double oxyppm = 0.0;
      try {
        no2ppm = inlet.getThermoSystem().getComponent("NO2").getz() * 1e6;
      } catch (Exception ignored) {
      }
      try {
        h2sppm = inlet.getThermoSystem().getComponent("H2S").getz() * 1e6;
      } catch (Exception ignored) {
      }
      try {
        oxyppm = inlet.getThermoSystem().getComponent("oxygen").getz() * 1e6;
      } catch (Exception ignored) {
      }

      if (no2ppm > 0.01 && h2sppm > 0.01) {
        reactor.run();
        outletSystem2 = reactor.getOutletStream().getThermoSystem();
      } else {
        if (oxyppm > 0.01) {
          GibbsReactor H2Sreactor = new GibbsReactor("Gibbs Reactor", inlet);
          H2Sreactor.setUseAllDatabaseSpecies(false);
          H2Sreactor.setDampingComposition(0.01);
          H2Sreactor.setMaxIterations(15000);
          H2Sreactor.setConvergenceTolerance(1e-3);
          H2Sreactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
          // set CO and COS inert
          H2Sreactor.setComponentAsInert("CO");
          H2Sreactor.setComponentAsInert("COS");
          H2Sreactor.setComponentAsInert("sulfuric acid");
          H2Sreactor.setComponentAsInert("NH4HSO4");
          H2Sreactor.setComponentAsInert("SO3");
          if (no2ppm < 0.01) {
            H2Sreactor.setComponentAsInert("SO2");
          }
          double so2ppm = 0.0;
          try {
            so2ppm = inlet.getThermoSystem().getComponent("SO2").getz() * 1e6;
          } catch (Exception ignored) {
          }
          if (so2ppm > 0.01) {
            H2Sreactor.setComponentAsInert("H2S");
          }
          H2Sreactor.run();

          GibbsReactor SO2reactor = new GibbsReactor("Gibbs Reactor", H2Sreactor.getOutletStream());
          SO2reactor.setUseAllDatabaseSpecies(false);
          SO2reactor.setDampingComposition(0.01);
          SO2reactor.setMaxIterations(15000);
          SO2reactor.setConvergenceTolerance(1e-3);
          SO2reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
          // set CO and COS inert
          SO2reactor.setComponentAsInert("CO");
          SO2reactor.setComponentAsInert("COS");
          try {
            double outOxy =
                H2Sreactor.getOutletStream().getThermoSystem().getComponent("oxygen").getz();
            if (outOxy > 0.01) {
              SO2reactor.setComponentAsInert("SO2");
            } else {
              SO2reactor.setComponentAsInert("oxygen");
              SO2reactor.setComponentAsInert("SO2");
            }
          } catch (Exception ignored) {
            SO2reactor.setComponentAsInert("oxygen");
            SO2reactor.setComponentAsInert("SO2");
          }
          SO2reactor.run();
          outletSystem2 = SO2reactor.getOutletStream().getThermoSystem();
        } else {
          reactor.setComponentAsInert("SO2");
          reactor.run();
          outletSystem2 = reactor.getOutletStream().getThermoSystem();
        }
      }
    } catch (Exception e) {
      // leave outletSystem2 as null
    }

    if (outletSystem2 != null) {
      getOutletStream().setThermoSystem(outletSystem2);
    }

    getOutletStream().run();
  }

  @Override
  public void run() {
    run(UUID.randomUUID());
  }
}
