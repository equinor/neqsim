/*
 * =============== JFreeChart Demo =============== Version 1.4; (C) Copyright 2000, Simba Management
 * Limited; Contact: David Gilbert (david.gilbert@bigfoot.com);
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307, USA.
 */

package neqsim.dataPresentation;

/**
 * A dummy data source for an XY plot.
 * <P>
 * Note that the aim of this class is to create a self-contained data source for demo purposes - it
 * is NOT intended to show how you should go about writing your own data sources.
 *
 * @author esol
 * @version $Id: $Id
 */
public class SampleXYDataSource {
    private static final long serialVersionUID = 1000;
    // implements XYDataSource, java.io.Serializable {
    double[][] points;
    int numberOfSeries;
    int[] items = new int[10];
    String[] seriesName;

    /**
     * Default constructor.
     */
    public SampleXYDataSource() {
        // items=0;
    }

    /**
     * <p>
     * Constructor for SampleXYDataSource.
     * </p>
     *
     * @param p an array of {@link double} objects
     * @param name an array of {@link java.lang.String} objects
     * @param title a {@link java.lang.String} object
     * @param xaxis a {@link java.lang.String} object
     * @param yaxsis a {@link java.lang.String} object
     */
    public SampleXYDataSource(double[][] p, String[] name, String title, String xaxis,
            String yaxsis) {
        // items = p[0].length;
        numberOfSeries = p.length / 2;

        for (int i = 0; i < numberOfSeries; i++) {
            items[i] = p[i * 2].length;

            System.out.println("items =" + items[i]);
        }

        System.out.println("series =" + numberOfSeries);
        seriesName = name;
        points = p;
    }

    /**
     * Returns the x-value for the specified series and item. Series are numbered 0, 1, ...
     *
     * @param series The index (zero-based) of the series;
     * @param item The index (zero-based) of the required item;
     * @return The x-value for the specified series and item.
     */
    public Number getXValue(int series, int item) {
        return (Double.valueOf(points[2 * series][item]));
    }

    /**
     * Returns the y-value for the specified series and item. Series are numbered 0, 1, ...
     *
     * @param series The index (zero-based) of the series;
     * @param item The index (zero-based) of the required item;
     * @return The y-value for the specified series and item.
     */
    public Number getYValue(int series, int item) {
        return (Double.valueOf(points[(series * 2 + 1)][item]));
    }

    /**
     * Returns the number of series in the data source.
     *
     * @return The number of series in the data source.
     */
    public int getSeriesCount() {
        return numberOfSeries;
    }

    /**
     * Returns the name of the series.
     *
     * @param series The index (zero-based) of the series;
     * @return The name of the series.
     */
    public String getSeriesName(int series) {
        return seriesName[series];
    }

    /**
     * Returns the number of items in the specified series.
     *
     * @param series The index (zero-based) of the series;
     * @return The number of items in the specified series.
     */
    public int getItemCount(int series) {
        return items[series];
    }
}
