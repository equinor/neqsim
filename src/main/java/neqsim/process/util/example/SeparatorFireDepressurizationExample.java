package neqsim.process.util.example;

import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.util.fire.SeparatorFireExposure;
import neqsim.process.util.fire.SeparatorFireExposure.FireExposureResult;
import neqsim.process.util.fire.SeparatorFireExposure.FireScenarioConfig;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example combining separator depressurization to a flare with fire heat load and integrity checks.
 */
public class SeparatorFireDepressurizationExample {

  /**
   * Runs a short dynamic blowdown to the flare and evaluates fire effects on the vessel wall.
   *
   * <p>The example demonstrates how the fire heat-transfer utilities can be paired with a dynamic
   * separator depressurization. At each timestep the example calculates fire heat loads, wetted vs.
   * unwetted wall temperatures, and a Scandpower-style rupture margin while routing gas to a flare.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // Build the separator system and initialize in steady state.
    SystemInterface separatorFluid = new SystemSrkEos(308.15, 45.0);
    separatorFluid.addComponent("methane", 90.0);
    separatorFluid.addComponent("ethane", 5.0);
    separatorFluid.addComponent("propane", 3.0);
    separatorFluid.addComponent("n-butane", 2.0);
    separatorFluid.setMixingRule(2);

    Stream separatorFeed = new Stream("Separator Feed", separatorFluid);
    separatorFeed.setFlowRate(7000.0, "kg/hr");
    separatorFeed.setPressure(45.0, "bara");
    separatorFeed.setTemperature(35.0, "C");

    Separator separator = new Separator("HP Separator", separatorFeed);
    separator.setCalculateSteadyState(true);
    Stream separatorGas = new Stream("Separator Gas", separator.getGasOutStream());

    // Blowdown valve and orifice take suction directly from separator gas outlet.
    BlowdownValve blowdownValve = new BlowdownValve("BDV-201", separatorGas);
    blowdownValve.setOpeningTime(4.0);
    blowdownValve.setCv(180.0);

    Stream blowdownValveOut = new Stream("BDV Outlet", blowdownValve.getOutletStream());

    Orifice blowdownOrifice = new Orifice("BD Orifice", 0.35, 0.08, 45.0, 1.5, 0.61);
    blowdownOrifice.setInletStream(blowdownValveOut);

    Stream toFlare = new Stream("To Flare", blowdownOrifice.getOutletStream());
    Flare flare = new Flare("Emergency Flare", toFlare);
    flare.setTipDiameter(0.9);
    flare.setRadiantFraction(0.20);
    flare.setFlameHeight(55.0);

    // Initialize separator steady state before switching to dynamic mode.
    separatorFeed.run();
    separator.run();
    separatorGas.run();
    blowdownValve.run();
    blowdownValveOut.run();
    blowdownOrifice.run();
    toFlare.run();
    flare.run();

    // Activate blowdown and switch to transient calculation.
    blowdownValve.activate();
    separator.setCalculateSteadyState(false);
    separatorFeed.setFlowRate(0.1, "kg/hr"); // isolate feed to depressurize

    FireScenarioConfig fireConfig = new FireScenarioConfig()
        .setFireTemperatureK(1200.0)
        .setWallThicknessM(0.02)
        .setThermalConductivityWPerMPerK(45.0)
        .setAllowableTensileStrengthPa(2.4e8)
        .setWettedInternalFilmCoefficientWPerM2K(1500.0)
        .setUnwettedInternalFilmCoefficientWPerM2K(50.0)
        .setExternalFilmCoefficientWPerM2K(35.0)
        .setEmissivity(0.35)
        .setViewFactor(0.7);

    double timeStep = 0.5;
    double duration = 8.0;
    double flareGroundDistanceM = 35.0;

    System.out.println(
        "Time (s) | P_sep (bara) | Flow to flare (kg/hr) | Tsep (K) | Twet (K) | Tunwet (K) | Rupture margin (MPa)");
    System.out.println(
        "--------|--------------|-----------------------|----------|----------|-----------|---------------------");

    FireExposureResult fireState = separator.evaluateFireExposure(fireConfig, flare,
        flareGroundDistanceM);

    for (double time = 0.0; time <= duration; time += timeStep) {
      separator.setDuty(fireState.totalFireHeat());
      separatorFeed.run();
      separator.runTransient(timeStep, java.util.UUID.randomUUID());
      separatorGas.run();
      blowdownValve.runTransient(timeStep, java.util.UUID.randomUUID());
      blowdownValveOut.run();
      blowdownOrifice.runTransient(timeStep, java.util.UUID.randomUUID());
      toFlare.run();
      flare.run();
      flare.updateCumulative(timeStep);

      fireState = separator.evaluateFireExposure(fireConfig, flare, flareGroundDistanceM);
      double separatorTemperatureK = separator.getThermoSystem().getTemperature();
      double separatorPressureBara = separator.getGasOutStream().getPressure("bara");
      double ruptureMarginMpa = fireState.ruptureMarginPa() / 1.0e6;

      double flareHeatMw = flare.getHeatDuty("MW");
      System.out.printf("%7.1f | %12.2f | %21.1f | %8.1f | %8.1f | %9.1f | %19.2f%n",
          time, separatorPressureBara, toFlare.getFlowRate("kg/hr"),
          separatorTemperatureK, fireState.wettedWall().outerWallTemperatureK(),
          fireState.unwettedWall().outerWallTemperatureK(), ruptureMarginMpa);

      if (time == 0.0) {
        System.out.printf("  API 521 pool-fire heat load: %.2f MW%n",
            fireState.poolFireHeatLoad() / 1.0e6);
        System.out.printf("  Radiative heat flux (SB): %.1f kW/m2%n",
            fireState.radiativeHeatFlux() / 1000.0);
        System.out.printf("  Flare radiation flux at %.0f m: %.1f kW/m2%n", flareGroundDistanceM,
            fireState.flareRadiativeFlux() / 1000.0);
        System.out.printf("  Wetted / unwetted areas (m2): %.1f / %.1f%n", fireState.wettedArea(),
            fireState.unwettedArea());
        System.out.printf("  Radiative load on unwetted wall: %.2f MW%n",
            fireState.unwettedRadiativeHeat() / 1.0e6);
        System.out.printf("  Flare-derived heat on shell: %.2f MW%n",
            fireState.flareRadiativeHeat() / 1.0e6);
        System.out.printf("  Initial flare heat duty: %.2f MW%n", flareHeatMw);
      }
    }

    System.out.printf("Total gas to flare: %.1f kg%n", flare.getCumulativeGasBurned("kg"));
    System.out.printf("Total heat released: %.2f GJ%n", flare.getCumulativeHeatReleased("GJ"));
  }
}
