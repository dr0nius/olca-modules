package org.openlca.julia;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the Java interface for the native Julia libraries and contains some
 * utility methods for loading these libraries.
 */
public final class Julia {

	private static AtomicBoolean _loaded = new AtomicBoolean(false);

	/** Returns true if the Julia libraries with openLCA bindings are loaded. */
	public static boolean loaded() {
		return _loaded.get();
	}

	/**
	 * Loads the libraries from a folder specified by the "OLCA_JULIA"
	 * environment variable.
	 */
	public static boolean load() {
		if (loaded())
			return true;
		Logger log = LoggerFactory.getLogger(Julia.class);
		String path = System.getenv("OLCA_JULIA");
		if (path == null || path.isEmpty()) {
			log.warn("Could not load Julia libs and bindings;"
					+ "OLCA_JULIA is not defined");
			return false;
		}
		File dir = new File(path);
		return load(dir);
	}

	/**
	 * Loads the Julia libraries and openLCA bindings from the given folder.
	 * Returns true if the libraries could be loaded (at least there should be a
	 * `libjolca` library in the folder that could be loaded).
	 */
	public static boolean load(File dir) {
		Logger log = LoggerFactory.getLogger(Julia.class);
		log.info("Try to load Julia libs and bindings from {}", dir);
		if (loaded()) {
			log.info("Julia libs already loaded; do nothing");
			return true;
		}
		if (!containsBindings(dir)) {
			log.warn("{} does not contain the openLCA bindings libjolca", dir);
			return false;
		}
		try {
			for (File file : dir.listFiles()) {
				log.info("load library {}", file);
				System.load(file.getAbsolutePath());
			}
			_loaded.set(true);
			return true;
		} catch (Exception e) {
			log.error("Failed to load Julia libs from " + dir, e);
			return false;
		}
	}

	private static boolean containsBindings(File dir) {
		if (dir == null || !dir.exists())
			return false;
		for (File lib : dir.listFiles()) {
			if (!lib.isFile())
				continue;
			if (lib.getName().contains("libjolca"))
				return true;
		}
		return false;
	}

	// BLAS

	/**
	 * Matrix-matrix multiplication: C := A * B
	 *
	 * @param rowsA [in] number of rows of matrix A
	 * @param colsB [in] number of columns of matrix B
	 * @param k     [in] number of columns of matrix A and number of rows of
	 *              matrix B
	 * @param a     [in] matrix A (size = rowsA*k)
	 * @param b     [in] matrix B (size = k * colsB)
	 * @param c     [out] matrix C (size = rowsA * colsB)
	 */
	public static native void mmult(int rowsA, int colsB, int k,
			double[] a, double[] b, double[] c);

	/**
	 * Matrix-vector multiplication: y:= A * x
	 *
	 * @param rowsA [in] rows of matrix A
	 * @param colsA [in] columns of matrix A
	 * @param a     [in] the matrix A
	 * @param x     [in] the vector x
	 * @param y     [out] the resulting vector y
	 */
	public static native void mvmult(int rowsA, int colsA,
			double[] a, double[] x, double[] y);

	// LAPACK

	/**
	 * Solves a system of linear equations A * X = B for general matrices. It
	 * calls the LAPACK DGESV routine.
	 *
	 * @param n    [in] the dimension of the matrix A (n = rows = columns of A)
	 * @param nrhs [in] the number of columns of the matrix B
	 * @param a    [io] on entry the matrix A, on exit the LU factorization of A
	 *             (size = n * n)
	 * @param b    [io] on entry the matrix B, on exit the solution of the
	 *             equation (size = n * bColums)
	 * @return the LAPACK return code
	 */
	public static native int solve(int n, int nrhs, double[] a, double[] b);

	// UMFPACK
	public static native void umfSolve(
			int n,
			int[] columnPointers,
			int[] rowIndices,
			double[] values,
			double[] demand,
			double[] result);

	public static native long umfFactorize(
			int n,
			int[] columnPointers,
			int[] rowIndices,
			double[] values);

	public static native void umfDispose(long pointer);

	public static native long umfSolveFactorized(
			long pointer, double[] demand, double[] result);
}
