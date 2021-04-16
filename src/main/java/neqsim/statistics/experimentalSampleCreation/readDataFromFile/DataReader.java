/*
 * DataReader.java
 *
 * Created on 1. februar 2001, 11:38
 */

package neqsim.statistics.experimentalSampleCreation.readDataFromFile;

import java.io.*;
import java.util.*;

/**
 *
 * @author even solbraa
 * @version
 */
public class DataReader extends Object implements DataReaderInterface {

    private static final long serialVersionUID = 1000;

    protected String fileName;
    protected ArrayList sampleObjectList = new ArrayList();

    /** Creates new DataReader */
    public DataReader() {
    }

    public DataReader(String fileName) {
        this.fileName = fileName;
        readData();
    }

    public void readData() {
        StringTokenizer tokenizer;
        String token;

        String path = "c:/logdata/" + this.fileName + ".log";
        System.out.println(path);

        try {
            RandomAccessFile file = new RandomAccessFile(path, "r");
            long filepointer = 0;
            long length = file.length();
            for (int i = 0; i < 6; i++) {
                file.readLine();
            }
            do {
                System.out.println("test");
                String s = file.readLine();
                tokenizer = new StringTokenizer(s);
                token = tokenizer.nextToken();

                filepointer = file.getFilePointer();
                tokenizer.nextToken();
            }

            while (filepointer < length);
        } catch (Exception e) {
            String err = e.toString();
            System.out.println(err);
        }
    }

    public ArrayList getSampleObjectList() {
        return sampleObjectList;
    }

    public static void main(String[] args) {
        DataReader reader = new DataReader("31011222");
    }

}
