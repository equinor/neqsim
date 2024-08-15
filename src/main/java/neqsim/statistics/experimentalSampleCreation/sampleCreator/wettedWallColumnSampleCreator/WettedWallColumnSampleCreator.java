/*
 * WettedWallColumnSampleCreator.java
 *
 * Created on 8. februar 2001, 09:14
 */

package neqsim.statistics.experimentalSampleCreation.sampleCreator.wettedWallColumnSampleCreator;

import Jama.Matrix;
import neqsim.statistics.dataanalysis.datasmoothing.DataSmoother;
import neqsim.statistics.experimentalEquipmentData.ExperimentalEquipmentData;
import neqsim.statistics.experimentalEquipmentData.wettedWallColumnData.WettedWallColumnData;
import neqsim.statistics.experimentalSampleCreation.readDataFromFile.wettedWallColumnReader.WettedWallColumnDataObject;
import neqsim.statistics.experimentalSampleCreation.readDataFromFile.wettedWallColumnReader.WettedWallDataReader;
import neqsim.statistics.experimentalSampleCreation.sampleCreator.SampleCreator;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * WettedWallColumnSampleCreator class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class WettedWallColumnSampleCreator extends SampleCreator {
  WettedWallDataReader reader;
  double[] time;
  double[] pressure;
  double[] inletLiquidTemperature;
  double[] outletLiquidTemperature;
  double[] inletGasTemperature;
  double[] inletTotalGasFlowRate;
  double[] inletLiquidFlowRate;
  double[] co2SupplyRate;
  double[] columnWallTemperature;
  double[] dPdt;
  double[] dNdt;
  double[] dPdn;
  double[] dNdtOld;
  double[] dnVdt;
  double[] smoothedPressure;
  double[] smoothedInletLiquidTemperature;
  double[] smoothedOutletLiquidTemperature;
  double[] smoothedInletGasTemperature;
  double[] smoothedInletTotalGasFlowRate;
  double[] smoothedInletLiquidFlowRate;
  double[] smoothedCo2SupplyRate;
  double[] smoothedColumnWallTemperature;

  /**
   * <p>
   * Constructor for WettedWallColumnSampleCreator.
   * </p>
   */
  public WettedWallColumnSampleCreator() {}

  /**
   * <p>
   * Constructor for WettedWallColumnSampleCreator.
   * </p>
   *
   * @param file a {@link java.lang.String} object
   */
  public WettedWallColumnSampleCreator(String file) {
    reader = new WettedWallDataReader(file);
  }

  /**
   * <p>
   * setSampleValues.
   * </p>
   */
  public void setSampleValues() {
    time = new double[reader.getSampleObjectList().size() - 1];
    pressure = new double[reader.getSampleObjectList().size() - 1];
    inletLiquidTemperature = new double[reader.getSampleObjectList().size() - 1];
    outletLiquidTemperature = new double[reader.getSampleObjectList().size() - 1];
    inletGasTemperature = new double[reader.getSampleObjectList().size() - 1];
    inletTotalGasFlowRate = new double[reader.getSampleObjectList().size() - 1];
    inletLiquidFlowRate = new double[reader.getSampleObjectList().size() - 1];
    co2SupplyRate = new double[reader.getSampleObjectList().size() - 1];
    columnWallTemperature = new double[reader.getSampleObjectList().size() - 1];

    int i = 0;
    do {
      time[i] = ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getTime();
      pressure[i] =
          ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getPressure();
      inletLiquidTemperature[i] = ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
          .getInletLiquidTemperature();
      outletLiquidTemperature[i] =
          ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
              .getOutletLiquidTemperature();
      columnWallTemperature[i] = ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
          .getColumnWallTemperature();
      inletTotalGasFlowRate[i] =
          ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getInletTotalGasFlow();
      co2SupplyRate[i] =
          ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getCo2SupplyFlow();
      inletLiquidFlowRate[i] =
          ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getInletLiquidFlow();
      i++;
    } while (i < reader.getSampleObjectList().size() - 1);
  }

  /**
   * <p>
   * smoothData.
   * </p>
   */
  public void smoothData() {
    Matrix data = new Matrix(pressure, 1);
    data.print(10, 2);

    DataSmoother smoother = new DataSmoother(pressure, 10, 10, 0, 2);
    smoother.runSmoothing();
    smoothedPressure = smoother.getSmoothedNumbers();

    smoother = new DataSmoother(inletLiquidTemperature, 10, 10, 0, 2);
    smoother.runSmoothing();
    smoothedInletLiquidTemperature = smoother.getSmoothedNumbers();

    smoother = new DataSmoother(outletLiquidTemperature, 10, 10, 0, 2);
    smoother.runSmoothing();
    smoothedOutletLiquidTemperature = smoother.getSmoothedNumbers();

    smoother = new DataSmoother(columnWallTemperature, 10, 10, 0, 2);
    smoother.runSmoothing();
    smoothedColumnWallTemperature = smoother.getSmoothedNumbers();

    smoother = new DataSmoother(inletTotalGasFlowRate, 10, 10, 0, 2);
    smoother.runSmoothing();
    smoothedInletTotalGasFlowRate = smoother.getSmoothedNumbers();

    smoother = new DataSmoother(co2SupplyRate, 10, 10, 0, 2);
    smoother.runSmoothing();
    smoothedCo2SupplyRate = smoother.getSmoothedNumbers();

    smoother = new DataSmoother(inletLiquidFlowRate, 10, 10, 0, 2);
    smoother.runSmoothing();
    smoothedInletLiquidFlowRate = smoother.getSmoothedNumbers();

    data = new Matrix(smoothedPressure, 1);
    data.print(10, 2);

    System.out.println("data-smoothing finished!");
  }

  /**
   * <p>
   * calcdPdt.
   * </p>
   */
  public void calcdPdt() {
    system.init(0);
    dPdt = new double[reader.getSampleObjectList().size() - 1];
    dPdt[0] = 0;
    dPdt[reader.getSampleObjectList().size() - 2] = 0;

    dPdn = new double[reader.getSampleObjectList().size() - 1];
    dPdn[0] = 0;
    dPdn[reader.getSampleObjectList().size() - 2] = 0;

    double err = 0;
    dNdt = new double[reader.getSampleObjectList().size() - 1];
    dNdtOld = new double[reader.getSampleObjectList().size() - 1];
    dnVdt = new double[reader.getSampleObjectList().size() - 1];

    do {
      for (int i = 1; i < (reader.getSampleObjectList().size() - 3); i++) {
        system.setTemperature(smoothedInletLiquidTemperature[i]);
        system.setPressure(smoothedPressure[i]);
        system.getPhases()[0].addMoles(1, dNdt[i] * (time[i] - time[i - 1]));
        system.getPhases()[1].addMoles(1, -dNdt[i] * (time[i] - time[i - 1]));
        system.init(1);
        // her bor det komme en funksjon som finer nummeret til Co2!
        dPdt[i] = (smoothedPressure[i + 1] - smoothedPressure[i - 1]) / (time[i + 1] - time[i - 1]);
        // dPdn[i] = system.getPhases()[1].getdPdn(1);
      }

      dNdt[0] = 0;
      dNdt[reader.getSampleObjectList().size() - 2] = 0;
      err = 0;

      for (int i = 1; i < (reader.getSampleObjectList().size() - 3); i++) {
        dNdtOld[i] = dNdt[i];
        dNdt[i] = dPdt[i] * 1.0 / dPdn[i];
        err += Math.abs((dNdtOld[i] - dNdt[i]));
        // System.out.println("dndt: " + dNdt[i]);
        dnVdt[i] = dNdt[i] * ThermodynamicConstantsInterface.R * 298.15
            / ThermodynamicConstantsInterface.atm * 1000 * 60;
        System.out.println("dVdt: " + dnVdt[i]);
      }
      System.out.println("err: " + err);
    } while (err > 1e-10);
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    WettedWallColumnSampleCreator creator = new WettedWallColumnSampleCreator("31011250");
    ExperimentalEquipmentData eq = new WettedWallColumnData(0.025, 1.48, 4.9);
    creator.setExperimentalEquipment(eq);
    creator.setSampleValues();
    creator.smoothData();
    SystemInterface sys = new SystemSrkEos(298, 10);
    sys.addComponent("methane", 100);
    sys.addComponent("CO2", 10);
    // sys.addComponent("water",10);
    creator.setThermoSystem(sys);
    creator.calcdPdt();
  }
}
