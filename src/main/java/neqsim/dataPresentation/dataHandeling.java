/*
 * dataRepresentation.java
 *
 * Created on 15. juni 2000, 18:21
 */

package neqsim.dataPresentation;

import java.io.*;
import java.text.*;

/**
 * @author  Even Solbraa
 * @version
 */
public class dataHandeling {

    private static final long serialVersionUID = 1000;

    /** Creates new dataRepresentation */
    public dataHandeling() {
    }

    public Number getXValue(int series, int item) {
        return Double.valueOf(-10.0 + (item * 0.2));
    }

    /**
     * Returns the y-value for the specified series and item. Series are numbered 0,
     * 1, ...
     * 
     * @param  series The index (zero-based) of the series;
     * @param  item   The index (zero-based) of the required item;
     * @return        The y-value for the specified series and item.
     */
    public Number getYValue(int series, int item) {
        if (series == 0) {
            return Double.valueOf(Math.cos(-10.0 + (item * 0.2)));
        } else {
            return Double.valueOf(2 * (Math.sin(-10.0 + (item * 0.2))));
        }
    }

    /**
     * Returns the number of series in the data source.
     * 
     * @return The number of series in the data source.
     */
    public int getSeriesCount() {
        return 2;
    }

    /**
     * Returns the name of the series.
     * 
     * @param  series The index (zero-based) of the series;
     * @return        The name of the series.
     */
    public String getSeriesName(int series) {
        if (series == 0) {
            return "y = cosine(x)";
        } else if (series == 1) {
            return "y = 2*sine(x)";
        } else {
            return "Error";
        }
    }

    /**
     * Returns the number of items in the specified series.
     * 
     * @param  series The index (zero-based) of the series;
     * @return        The number of items in the specified series.
     */
    public int getItemCount(int series) {
        return 81;
    }

    public int getLegendItemCount() {
        return 2;
    }

    public String[] getLegendItemLabels() {
        String[] str = new String[2];
        str[1] = "";
        str[2] = "";
        return str;
    }

    public void printToFile(double[][] points, String filename) {

        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.####E0");

        try {
            DataOutputStream rt = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(new File("c:/temp/" + filename))));

            for (int i = 0; i < points.length; i++) {
                for (int j = 0; j < points[i].length; j++) {
                    rt.writeBytes(nf.format(points[i][j]) + "\t");
                    if (j == (points[i].length - 1)) {
                        rt.writeBytes("\n");
                    }
                }
            }
            rt.close();
        } catch (Exception e) {
            String err = e.toString();
            System.out.println(err);
        }

    }

}
