package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Long-horizon acceptance case for the coupled transient pressure-momentum path.
 */
@Tag("slow")
class TwoFluidPressureMomentumLongHorizonTest {
  @Test
  void liquidRichLineRemainsBoundedForTwelveHundredSeconds() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 60.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50.0 * 3600.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.30);
    pipe.setNumberOfSections(40);
    pipe.setElevationProfile(new double[40]);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setAllowOutletPhaseBackflow(true);
    pipe.setCflNumber(0.5);
    pipe.run();

    double initialInventory = pipe.getTotalMassInventory();
    double cumulativeAbsoluteLiquidOutletMassKg = 0.0;
    double maximumLiquidHoldup = 0.0;
    for (int interval = 0; interval < 240; interval++) {
      pipe.runTransient(5.0, null);
      assertTrue(pipe.isCoupledPressureMomentumConverged(),
          "pressure-momentum correction failed at t=" + (interval + 1) * 5.0 + " s");
      assertTrue(pipe.getCoupledPressureMomentumVolumeResidual() < 1.0e-6,
          "cell-volume residual exceeded tolerance at t=" + (interval + 1) * 5.0 + " s");

      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
      assertTrue(balance.isWithinTolerance(TwoFluidMassBalanceReport.Phase.TOTAL, 1.0e-6, 1.0e-8),
          "discrete mass balance failed at t=" + (interval + 1) * 5.0 + " s");
      cumulativeAbsoluteLiquidOutletMassKg +=
          Math.abs(balance.getOutletMassKg(TwoFluidMassBalanceReport.Phase.LIQUID));
      maximumLiquidHoldup =
          Math.max(maximumLiquidHoldup, maximumAbsolute(pipe.getLiquidHoldupProfile()));
    }

    double inletPressureBara = pipe.getPressureProfile()[0] / 1.0e5;
    double finalInventoryRatio = pipe.getTotalMassInventory() / initialInventory;
    assertTrue(inletPressureBara > 30.0 && inletPressureBara < 120.0,
        "60 bara feed reconstructed an inlet pressure of " + inletPressureBara + " bara");
    assertTrue(finalInventoryRatio > 0.25 && finalInventoryRatio < 1.75,
        "liquid-rich line inventory ratio became " + finalInventoryRatio);
    assertTrue(maximumLiquidHoldup < 0.94,
        "liquid-rich line packed to a maximum holdup of " + maximumLiquidHoldup);
    assertTrue(cumulativeAbsoluteLiquidOutletMassKg > 1.0,
        "liquid outlet transfer remained frozen over the long-horizon run");
    assertTrue(maximumAbsolute(pipe.getGasVelocityProfile()) < 80.0);
    assertTrue(maximumAbsolute(pipe.getLiquidVelocityProfile()) < 40.0);
    assertFalse(pipe.isTransientOutletBackflowClamped(),
        "a coupled pressure solution must not rely on the one-way outlet clamp");
  }

  private static double maximumAbsolute(double[] values) {
    double maximum = 0.0;
    for (double value : values) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }
}
