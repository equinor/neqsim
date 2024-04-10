package neqsim.MathLib.matrix;

import java.util.Scanner;

public class Inverse
{

    public static double[][] invert(double[][] matrix) {
        int n = matrix.length;
        double[][] inverse = new double[n][n];
        double[][] augmented = new double[n][n * 2];

        // Initialize the inverse matrix as an identity matrix
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) inverse[i][j] = 1;
                else inverse[i][j] = 0;

                // Augment the original matrix with the identity matrix
                augmented[i][j] = matrix[i][j];
                augmented[i][j + n] = inverse[i][j];
            }
        }

        // Perform Gauss-Jordan elimination
        for (int i = 0; i < n; i++) {
            // Find pivot
            double pivot = augmented[i][i];
            if (pivot == 0) return null; // The matrix is singular

            // Normalize the pivot row
            for (int j = 0; j < n * 2; j++) {
                augmented[i][j] /= pivot;
            }

            // Eliminate the column entries other than the pivot
            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = augmented[k][i];
                    for (int j = 0; j < n * 2; j++) {
                        augmented[k][j] -= factor * augmented[i][j];
                    }
                }
            }
        }

        // Extract the inverse matrix from the augmented matrix
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                inverse[i][j] = augmented[i][j + n];
            }
        }

        return inverse;
    }

}