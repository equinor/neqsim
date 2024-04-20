package neqsim.MathLib.matrix;

import java.util.Scanner;

public class Inverse
{

    public static double[] invert(double[] matrix, int numRowsCols) {
        int n = numRowsCols;
        double[] inverse = new double[n*n];

        // Create identity matrix
        for (int i = 0; i < n; i++) {
            inverse[i*n+i] = 1;
        }

        // Perform Gaussian elimination
        for (int i = 0; i < n; i++) {
            double pivot = matrix[i*n+i];
            if (pivot == 0) {
                return null; // Cannot invert, pivot is zero
            }
            for (int j = 0; j < n; j++) {
                matrix[i*n+j] /= pivot;
                inverse[i*n+j] /= pivot;
            }
            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = matrix[k*n+i];
                    for (int j = 0; j < n; j++) {
                        matrix[k*n+j] -= factor * matrix[i*n+j];
                        inverse[k*n+j] -= factor * inverse[i*n+j];
                    }
                }
            }
        }

        return inverse;
    }

    public static double[][] invertGaussian(double[][] matrix) {
        int n = matrix.length;
        double[][] inverse = new double[n][n];

        // Create identity matrix
        for (int i = 0; i < n; i++) {
            inverse[i][i] = 1;
        }

        // Perform Gaussian elimination
        for (int i = 0; i < n; i++) {
            double pivot = matrix[i][i];
            if (pivot == 0) {
                return null; // Cannot invert, pivot is zero
            }
            for (int j = 0; j < n; j++) {
                matrix[i][j] /= pivot;
                inverse[i][j] /= pivot;
            }
            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = matrix[k][i];
                    for (int j = 0; j < n; j++) {
                        matrix[k][j] -= factor * matrix[i][j];
                        inverse[k][j] -= factor * inverse[i][j];
                    }
                }
            }
        }

        return inverse;
    }

    public static double[][] invertGaussian2(double[][] matrix) {
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
                        double scaled_factor = factor * augmented[i][j];
                        augmented[k][j] -= scaled_factor;
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