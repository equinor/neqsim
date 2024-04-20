package neqsim.MathLib.matrix;

public class EigenDecomposition {

    private double[] matrix;
    int numRows;
    int numCols;

    public EigenDecomposition( double[][] input ) {

        //this.matrix = input;
        this.numRows = input.length;
        this.numCols = input[0].length;

        double[] inputArray = new double[this.numRows*this.numCols];

        for (int row = 0; row < numRows; row++)
            for (int col = 0; col < numCols; col++)
                inputArray[row*this.numCols+col] = input[row][col];

        this.matrix = inputArray;

    }

    public EigenDecomposition( double[] input, int numRows, int numCols ) {

        this.matrix = input;
        this.numRows = numRows;
        this.numCols = numCols;
    }

    public int getNumberOfEigenvalues()
    {
        //Only square matrices
        return this.numRows;
    }

    public SimpleMatrix getEigenVector(int a)
    {
        int iterations = 100;
        double eigenvalue = 0;
        double[] eigenvector = new double[this.numRows];

        for (int i = 0; i < this.numRows; i++){
            eigenvector[i] = 1;
        }

        for (int i = 0; i < iterations; i++) {
            // Multiply matrix by vector
            double[] nextVector = multiplyMatrixByVector(matrix, eigenvector);

            // Normalize the resulting vector
            normalizeVector(nextVector);

            // Copy nextVector to vector for the next iteration
            System.arraycopy(nextVector, 0, eigenvector, 0, eigenvector.length);
        }

        double[][] result = new double[this.numRows][1];
        for (int i = 0; i < this.numRows; i++){
            result[i][0] = eigenvector[i];
        }

        return new SimpleMatrix(result);
    }

    public double[] multiplyMatrixByVector(double[] matrix, double[] vector)
    {
        double[] answer = new double[vector.length];
        for (int row = 0; row < answer.length; ++row) {
            for (int col = 0; col < vector.length; ++col) {
                answer[row] += matrix[row*answer.length+col] * vector[col];
            }
        }
        return answer;
    }



    // Method to find the norm of a vector
    public double vectorNorm(double[] vector) {
        double sum = 0;
        for (double v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    // Method to normalize a vector
    public void normalizeVector(double[] vector) {
        double norm = vectorNorm(vector);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
