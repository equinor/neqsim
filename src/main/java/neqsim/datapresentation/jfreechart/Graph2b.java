/*
 * graph2b.java
 *
 * Created on 17. juni 2000, 17:16
 */

package neqsim.datapresentation.jfreechart;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * graph2b class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Graph2b extends javax.swing.JFrame {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Graph2b.class);

  XYDataset xyData;
  String titl;
  String xaxtitle;
  String yaxtitle;

  /**
   * <p>
   * Constructor for graph2b.
   * </p>
   */
  public Graph2b() {
    initComponents();
    pack();
  }

  /**
   * <p>
   * Constructor for graph2b.
   * </p>
   *
   * @param points an array of type double
   */
  public Graph2b(double[][] points) {
    String[] seriesNames = new String[points.length];
    for (int i = 0; i < points.length; i++) {
      seriesNames[i] = "";
    }
    String tit = "";
    String xaxis = "";
    String yaxis = "";
    titl = tit;
    xaxtitle = xaxis;
    yaxtitle = yaxis;

    XYSeriesCollection seriesCol = new XYSeriesCollection();

    for (int serLen = 0; serLen < points.length / 2; serLen++) {
      XYSeries series = new XYSeries(seriesNames[serLen]);
      for (int i = 0; i < points[2 * serLen].length; i++) {
        series.add(points[2 * serLen][i], points[2 * serLen + 1][i]);
      }
      seriesCol.addSeries(series);
    }

    chart = ChartFactory.createScatterPlot(tit, xaxtitle, yaxtitle, seriesCol,
        org.jfree.chart.plot.PlotOrientation.VERTICAL, true, false, false);
    // StandardTitle title = (StandardTitle)chart.getTitle();
    // title.setTitle(titl);
    // chart.setBackgroundPaint(java.awt.P)ackgroundPaint(new GradientPaint(0, 0,
    // Color.white,0, 1000, Color.white));
    // org.jfree.chart.plot.Plot myPlot = chart.getPlot();

    chartPanel4 = new ChartPanel(chart);
    chartPanel4
        .setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4),
            BorderFactory.createLineBorder(Color.darkGray, 1)));
    initComponents();
    getContentPane().add(chartPanel4, java.awt.BorderLayout.WEST);
    pack();
  }

  /**
   * <p>
   * Constructor for graph2b.
   * </p>
   *
   * @param points an array of type double
   * @param seriesNames an array of {@link java.lang.String} objects
   * @param tit a {@link java.lang.String} object
   * @param xaxis a {@link java.lang.String} object
   * @param yaxis a {@link java.lang.String} object
   */
  public Graph2b(double[][] points, String[] seriesNames, String tit, String xaxis, String yaxis) {
    XYSeriesCollection seriesCol = new XYSeriesCollection();

    for (int serLen = 0; serLen < points.length / 2; serLen++) {
      XYSeries series = new XYSeries(seriesNames[serLen]);
      for (int i = 0; i < points[2 * serLen].length; i++) {
        series.add(points[2 * serLen][i], points[2 * serLen + 1][i]);
        // System.out.println(points[2*serLen][i]+" "+points[2*serLen+1][i]);
      }
      seriesCol.addSeries(series);
    }
    chart = ChartFactory.createScatterPlot(tit, xaxis, yaxis, seriesCol,
        org.jfree.chart.plot.PlotOrientation.VERTICAL, true, false, false);

    // StandardTitle title = (StandardTitle)chart.getTitle();
    // title.setTitle(titl);
    chart.setBackgroundPaint(Color.white);
    // ChartBackgroundPaint(new GradientPaint(0, 0, Color.white,0, 1000,
    // Color.blue));
    // org.jfree.chart.plot.Plot myPlot = chart.getPlot();

    chartPanel4 = new ChartPanel(chart);
    chartPanel4
        .setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4),
            BorderFactory.createLineBorder(Color.darkGray, 1)));
    chartPanel4.setBackground(Color.white);
    initComponents();
    getContentPane().add(chartPanel4, java.awt.BorderLayout.WEST);
    pack();
  }

  /**
   * <p>
   * Constructor for graph2b.
   * </p>
   *
   * @param xpoints an array of type double
   * @param points an array of type double
   * @param seriesNames an array of {@link java.lang.String} objects
   * @param tit a {@link java.lang.String} object
   * @param xaxis a {@link java.lang.String} object
   * @param yaxis a {@link java.lang.String} object
   */
  public Graph2b(double[][] xpoints, double[][] points, String[] seriesNames, String tit,
      String xaxis, String yaxis) {
    XYSeriesCollection seriesCol = new XYSeriesCollection();

    for (int serLen = 0; serLen < points.length; serLen++) {
      XYSeries series = new XYSeries(seriesNames[serLen]);
      for (int i = 0; i < points[serLen].length; i++) {
        series.add(xpoints[serLen][i], points[serLen][i]);
        // System.out.println(points[2*serLen][i]+" "+points[2*serLen+1][i]);
      }
      seriesCol.addSeries(series);
    }
    chart = ChartFactory.createScatterPlot(tit, xaxis, yaxis, seriesCol,
        org.jfree.chart.plot.PlotOrientation.VERTICAL, true, false, false);

    // StandardTitle title = (StandardTitle)chart.getTitle();
    // title.setTitle(titl);
    chart.setBackgroundPaint(Color.white);
    // ChartBackgroundPaint(new GradientPaint(0, 0, Color.white,0, 1000,
    // Color.blue));
    // org.jfree.chart.plot.Plot myPlot = chart.getPlot();

    chartPanel4 = new ChartPanel(chart);
    chartPanel4
        .setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4),
            BorderFactory.createLineBorder(Color.darkGray, 1)));
    chartPanel4.setBackground(Color.white);
    initComponents();
    getContentPane().add(chartPanel4, java.awt.BorderLayout.WEST);
    pack();
  }

  /**
   * <p>
   * saveFigure.
   * </p>
   *
   * @param fileName a {@link java.lang.String} object
   */
  public void saveFigure(String fileName) {
    try {
      System.out.println("start creating png figure...");
      java.io.File temp = new java.io.File(fileName);
      org.jfree.chart.ChartUtils.saveChartAsPNG(temp, chart, 500, 500);
      System.out.println("figure png created in " + neqsim.util.util.FileSystemSettings.tempDir
          + "NeqSimTempFig.png");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * getBufferedImage.
   * </p>
   *
   * @return a {@link java.awt.image.BufferedImage} object
   */
  public BufferedImage getBufferedImage() {
    BufferedImage buf = null;
    try {
      System.out.println("start creating png figure...");
      buf = chart.createBufferedImage(640, 400, null);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return buf;
  }

  /**
   * This method is called from within the constructor to initialize the form. WARNING: Do NOT
   * modify this code. The content of this method is always regenerated by the FormEditor.
   */
  private void initComponents() { // GEN-BEGIN:initComponents
    jTextPane1 = new javax.swing.JTextPane();
    jPanel1 = new javax.swing.JPanel();

    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        exitForm(evt);
      }

      @Override
      public void windowClosed(java.awt.event.WindowEvent evt) {
        exit(evt);
      }
    });

    jTextPane1.setFont(new java.awt.Font("Arial", 1, 16));
    jTextPane1.setText("2D-plot");
    jTextPane1.setSelectedTextColor(java.awt.Color.red);
    getContentPane().add(jTextPane1, java.awt.BorderLayout.NORTH);

    getContentPane().add(jPanel1, java.awt.BorderLayout.CENTER);
  } // GEN-END:initComponents

  private void exit(java.awt.event.WindowEvent evt) { // GEN-FIRST:event_exit
    // Add your handling code here:
  } // GEN-LAST:event_exit

  private void buttonclick(java.awt.event.MouseEvent evt) { // GEN-FIRST:event_buttonclick
    // Add your handling code here:
    this.setVisible(false);
  } // GEN-LAST:event_buttonclick

  /**
   * <p>
   * createCategoryDataSource.
   * </p>
   *
   * @return a {@link org.jfree.data.category.CategoryDataset} object
   */
  public CategoryDataset createCategoryDataSource() {
    /*
     * Number[][] data = new Integer[][] { { Integer.valueOf(10), Integer.valueOf(4),
     * Integer.valueOf(15), Integer.valueOf(14) }, { Integer.valueOf(5), Integer.valueOf(7),
     * Integer.valueOf(14), Integer.valueOf(3) }, { Integer.valueOf(6), Integer.valueOf(17),
     * Integer.valueOf(12), Integer.valueOf(7) }, { Integer.valueOf(7), Integer.valueOf(15),
     * Integer.valueOf(11), Integer.valueOf(0) }, { Integer.valueOf(8), Integer.valueOf(6),
     * Integer.valueOf(10), Integer.valueOf(9) }, { Integer.valueOf(9), Integer.valueOf(8),
     * Integer.valueOf(8), Integer.valueOf(6) }, { Integer.valueOf(10), Integer.valueOf(9),
     * Integer.valueOf(7), Integer.valueOf(7) }, { Integer.valueOf(11), Integer.valueOf(13),
     * Integer.valueOf(9), Integer.valueOf(9) }, { Integer.valueOf(3), Integer.valueOf(7),
     * Integer.valueOf(11), Integer.valueOf(10) } };
     */
    return null; // new DefaultCategoryDataset(data);
  }

  /**
   * Exit the Application.
   *
   * @param evt The event to exit the form
   */
  private void exitForm(java.awt.event.WindowEvent evt) { // GEN-FIRST:event_exitForm
    this.removeAll();
  } // GEN-LAST:event_exitForm

  /**
   * <p>
   * getChartPanel.
   * </p>
   *
   * @return a {@link org.jfree.chart.ChartPanel} object
   */
  public ChartPanel getChartPanel() {
    return chartPanel4;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args the command line arguments
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    new Graph2b().setVisible(true);
  }

  /**
   * Getter for property chart.
   *
   * @return Value of property chart.
   */
  public JFreeChart getChart() {
    return chart;
  }

  /**
   * Setter for property chart.
   *
   * @param chart New value of property chart.
   */
  public void setChart(JFreeChart chart) {
    this.chart = chart;
  }

  private CategoryDataset categoryData;
  private JFreeChart chart;
  private ChartPanel chartPanel4;
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextPane jTextPane1;
  private javax.swing.JPanel jPanel1;
  // End of variables declaration//GEN-END:variables
}
