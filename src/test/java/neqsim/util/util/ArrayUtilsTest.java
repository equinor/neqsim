package neqsim.util.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

/**
 * Tests for defensive array-copy utilities.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ArrayUtilsTest {
  /** Tests primitive and reference array copies, including null inputs and matrix rows. */
  @Test
  void copiesArraysAndMatrixRowsIndependently() {
    double[] values = {1.0, 2.0};
    double[] copiedValues = ArrayUtils.copyArray(values);
    assertNotSame(values, copiedValues);
    assertArrayEquals(values, copiedValues, 0.0);
    assertNull(ArrayUtils.copyArray((double[]) null));

    String[] labels = {"first", "second"};
    String[] copiedLabels = ArrayUtils.copyArray(labels);
    assertNotSame(labels, copiedLabels);
    assertSame(labels[0], copiedLabels[0]);

    double[][] matrix = {{1.0, 2.0}, null, {3.0}};
    double[][] copiedMatrix = ArrayUtils.copyMatrixArray(matrix);
    assertNotSame(matrix, copiedMatrix);
    assertNotSame(matrix[0], copiedMatrix[0]);
    assertArrayEquals(matrix[0], copiedMatrix[0], 0.0);
    assertNull(copiedMatrix[1]);
    assertNotSame(matrix[2], copiedMatrix[2]);
    assertNull(ArrayUtils.copyMatrixArray((double[][]) null));

    String[][] labelsByGroup = {{"first"}, null};
    String[][] copiedLabelsByGroup = ArrayUtils.copyMatrixArray(labelsByGroup);
    assertNotSame(labelsByGroup, copiedLabelsByGroup);
    assertNotSame(labelsByGroup[0], copiedLabelsByGroup[0]);
    assertSame(labelsByGroup[0][0], copiedLabelsByGroup[0][0]);
    assertNull(copiedLabelsByGroup[1]);
  }
}