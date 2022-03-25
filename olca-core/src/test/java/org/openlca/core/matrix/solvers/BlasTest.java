package org.openlca.core.matrix.solvers;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openlca.core.DataDir;
import org.openlca.julia.Julia;
import org.openlca.nativelib.NativeLib;

public class BlasTest {

	@BeforeClass
	public static void setup() {
		NativeLib.loadFrom(DataDir.root());
	}

	@Test
	public void testMatrixMatrixMult() {
		double[] a = { 1, 4, 2, 5, 3, 6 };
		double[] b = { 7, 8, 9, 10, 11, 12 };
		double[] c = new double[4];
		Julia.mmult(2, 2, 3, a, b, c);
		Assert.assertArrayEquals(new double[] { 50, 122, 68, 167 }, c, 1e-16);
	}

	@Test
	public void testMatrixVectorMult() {
		double[] a = { 1, 4, 2, 5, 3, 6 };
		double[] x = { 2, 1, 0.5 };
		double[] y = new double[2];
		Julia.mvmult(2, 3, a, x, y);
		Assert.assertArrayEquals(new double[] { 5.5, 16 }, y, 1e-16);
	}
}
