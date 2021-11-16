/*
 * WettedWallColumnSampleCreator.java
 *
 * Created on 8. februar 2001, 09:14
 */

package neqsim.statistics.experimentalSampleCreation.sampleCreator.wettedWallColumnSampleCreator;

import Jama.Matrix;
import neqsim.statistics.dataAnalysis.dataSmoothing.DataSmoothor;
import neqsim.statistics.experimentalEquipmentData.ExperimentalEquipmentData;
import neqsim.statistics.experimentalEquipmentData.wettedWallColumnData.WettedWallColumnData;
import neqsim.statistics.experimentalSampleCreation.readDataFromFile.wettedWallColumnReader.WettedWallColumnDataObject;
import neqsim.statistics.experimentalSampleCreation.readDataFromFile.wettedWallColumnReader.WettedWallDataReader;
import neqsim.statistics.experimentalSampleCreation.sampleCreator.SampleCreator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author even solbraa
 * @version
 */
public class WettedWallColumnSampleCreator extends SampleCreator {
    private static final long serialVersionUID = 1000;
    WettedWallDataReader reader;
    DataSmoothor smoothor;
    double[] time, pressure, inletLiquidTemperature, outletLiquidTemperature, inletGasTemperature,
            inletTotalGasFlowRate, inletLiquidFlowRate, co2SupplyRate, columnWallTemperature, dPdt,
            dNdt, dPdn, dNdtOld, dnVdt;
    double[] smoothedPressure, smoothedInletLiquidTemperature, smoothedOutletLiquidTemperature,
            smoothedInletGasTemperature, smoothedInletTotalGasFlowRate, smoothedInletLiquidFlowRate,
            smoothedCo2SupplyRate, smoothedColumnWallTemperature;

    /** Creates new WettedWallColumnSampleCreator */
    public WettedWallColumnSampleCreator() {}

    public WettedWallColumnSampleCreator(String file) {
        reader = new WettedWallDataReader(file);
    }

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
            pressure[i] = ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                    .getPressure();
            inletLiquidTemperature[i] =
                    ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getInletLiquidTemperature();
            outletLiquidTemperature[i] =
                    ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getOutletLiquidTemperature();
            columnWallTemperature[i] =
                    ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getColumnWallTemperature();
            inletTotalGasFlowRate[i] =
                    ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getInletTotalGasFlow();
            co2SupplyRate[i] = ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                    .getCo2SupplyFlow();
            inletLiquidFlowRate[i] =
                    ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getInletLiquidFlow();
            i++;
        } while (i < reader.getSampleObjectList().size() - 1);
    }

    public void smoothData() {
        Matrix data = new Matrix(pressure, 1);
        data.print(10, 2);

        smoothor = new DataSmoothor(pressure, 10, 10, 0, 2);
        smoothor.runSmoothing();
        smoothedPressure = smoothor.getSmoothedNumbers();

        smoothor = new DataSmoothor(inletLiquidTemperature, 10, 10, 0, 2);
        smoothor.runSmoothing();
        smoothedInletLiquidTemperature = smoothor.getSmoothedNumbers();

        smoothor = new DataSmoothor(outletLiquidTemperature, 10, 10, 0, 2);
        smoothor.runSmoothing();
        smoothedOutletLiquidTemperature = smoothor.getSmoothedNumbers();

        smoothor = new DataSmoothor(columnWallTemperature, 10, 10, 0, 2);
        smoothor.runSmoothing();
        smoothedColumnWallTemperature = smoothor.getSmoothedNumbers();

        smoothor = new DataSmoothor(inletTotalGasFlowRate, 10, 10, 0, 2);
        smoothor.runSmoothing();
        smoothedInletTotalGasFlowRate = smoothor.getSmoothedNumbers();

        smoothor = new DataSmoothor(co2SupplyRate, 10, 10, 0, 2);
        smoothor.runSmoothing();
        smoothedCo2SupplyRate = smoothor.getSmoothedNumbers();

        smoothor = new DataSmoothor(inletLiquidFlowRate, 10, 10, 0, 2);
        smoothor.runSmoothing();
        smoothedInletLiquidFlowRate = smoothor.getSmoothedNumbers();

        data = new Matrix(smoothedPressure, 1);
        data.print(10, 2);

        System.out.println("data-smoothing finished!");
    }

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
                dPdt[i] = (smoothedPressure[i + 1] - smoothedPressure[i - 1])
                        / (time[i + 1] - time[i - 1]);
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
                dnVdt[i] = dNdt[i] * 8.314 * 298.15 / 101325.0 * 1000 * 60;
                System.out.println("dVdt: " + dnVdt[i]);
            }
            System.out.println("err: " + err);
        } while (err > 1e-10);
    }

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
