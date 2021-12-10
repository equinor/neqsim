/*
 * WettedWallDataReader.java
 *
 * Created on 1. februar 2001, 13:05
 */

package neqsim.statistics.experimentalSampleCreation.readDataFromFile.wettedWallColumnReader;

import java.io.RandomAccessFile;
import java.util.StringTokenizer;

import neqsim.statistics.experimentalSampleCreation.readDataFromFile.DataReader;

/**
 *
 * @author even solbraa
 * @version
 */
public class WettedWallDataReader extends DataReader {

    /** Creates new WettedWallDataReader */
    public WettedWallDataReader() {}

    public WettedWallDataReader(String fileName) {
        super(fileName);
    }

    @Override
    public void readData() {
        StringTokenizer tokenizer;
        String token;
        int k = 0;
        String path = "c:/logdata/" + this.fileName + ".log";
        System.out.println(path);

        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long filepointer = 0;
            long length = file.length();
            for (int i = 0; i < 6; i++) {
                file.readLine();
            }
            do {
                k++;
                WettedWallColumnDataObject dataObject = new WettedWallColumnDataObject();
                String s = file.readLine();
                tokenizer = new StringTokenizer(s);
                tokenizer.nextToken();
                dataObject.setTime(tokenizer.nextToken());
                tokenizer.nextToken();
                tokenizer.nextToken();
                dataObject.setInletLiquidTemperature(Double.parseDouble(tokenizer.nextToken()));
                dataObject.setInletGasTemperature(Double.parseDouble(tokenizer.nextToken()));
                tokenizer.nextToken(); // vaeske inn paa vaeskefordeler
                dataObject.setOutletLiquidTemperature(Double.parseDouble(tokenizer.nextToken()));
                dataObject.setColumnWallTemperature(Double.parseDouble(tokenizer.nextToken()));
                dataObject.setOutletGasTemperature(Double.parseDouble(tokenizer.nextToken()));
                tokenizer.nextToken();
                tokenizer.nextToken();
                tokenizer.nextToken();
                dataObject.setPressure(Double.parseDouble(tokenizer.nextToken()));
                tokenizer.nextToken();
                tokenizer.nextToken();
                dataObject.setCo2SupplyFlow(Double.parseDouble(tokenizer.nextToken()));
                dataObject.setInletTotalGasFlow(Double.parseDouble(tokenizer.nextToken()));
                dataObject.setInletLiquidFlow(Double.parseDouble(tokenizer.nextToken()));

                filepointer = file.getFilePointer();
                tokenizer.nextToken();
                sampleObjectList.add(dataObject);
            }

            while (filepointer < length);
        } catch (Exception e) {
            String err = e.toString();
            System.out.println(err);
        }
        System.out.println("Read data from file done!");
        System.out.println(k + " datapoints imported from file");
    }

    public static void main(String[] args) {
        WettedWallDataReader reader = new WettedWallDataReader("31011222");
        int i = 0;
        do {
            System.out.println("svar: "
                    + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getTime());
            System.out.println("total gas flow: "
                    + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getInletTotalGasFlow());
            System.out.println("co2 flow: "
                    + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getCo2SupplyFlow());
            System.out.println("pressure: "
                    + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
                            .getPressure());
            i++;
        } while (i < reader.getSampleObjectList().size() - 1);
    }
}
