package org.openlca.core.results.solutions;

import org.openlca.core.matrix.DIndex;
import org.openlca.core.matrix.FlowIndex;
import org.openlca.core.matrix.MatrixData;
import org.openlca.core.matrix.TechIndex;
import org.openlca.core.matrix.solvers.Factorization;
import org.openlca.core.matrix.solvers.IMatrixSolver;

import gnu.trove.map.hash.TIntObjectHashMap;
import org.openlca.core.model.descriptors.ImpactCategoryDescriptor;

public class LazySolutionProvider implements SolutionProvider {

	private final MatrixData data;
	private final IMatrixSolver solver;
	private final Factorization factorization;

	private final double[] scalingVector;
	private final double[] totalFlows;
	private final double[] totalImpacts;
	private final double totalCosts;

	private final TIntObjectHashMap<double[]> solutions;
	private final TIntObjectHashMap<double[]> intensities;
	private final TIntObjectHashMap<double[]> impacts;

	private LazySolutionProvider(MatrixData data, IMatrixSolver solver) {
		this.data = data;
		this.solver = solver;
		this.factorization = solver.factorize(data.techMatrix);

		solutions = new TIntObjectHashMap<>();
		intensities = data.flowMatrix == null
				? null
				: new TIntObjectHashMap<>();
		impacts = data.impactMatrix == null
				? null
				: new TIntObjectHashMap<>();

		// calculate the scaling vector
		var refIdx = data.techIndex.getIndex(
				data.techIndex.getRefFlow());
		var s = solutionOfOne(refIdx);
		var d = data.techIndex.getDemand();
		scalingVector = new double[s.length];
		for (int i = 0; i < s.length; i++) {
			scalingVector[i] = s[i] * d;
		}

		// calculate the total results
		totalFlows = data.flowMatrix != null
				? solver.multiply(data.flowMatrix, scalingVector)
				: null;
		totalImpacts = totalFlows != null && data.impactMatrix != null
				? solver.multiply(data.impactMatrix, totalFlows)
				: null;
		var tCosts = 0.0;
		if (data.costVector != null) {
			for (int i = 0; i < scalingVector.length; i++) {
				tCosts += data.costVector[i] * scalingVector[i];
			}
		}
		totalCosts = tCosts;
	}

	public static LazySolutionProvider create(
			MatrixData data,
			IMatrixSolver solver) {
		return new LazySolutionProvider(data, solver);
	}

	@Override
	public TechIndex techIndex() {
		return data.techIndex;
	}

	@Override
	public FlowIndex flowIndex() {
		return data.flowIndex;
	}

	@Override
	public DIndex<ImpactCategoryDescriptor> impactIndex() {
		return data.impactIndex;
	}

	@Override
	public double[] scalingVector() {
		return scalingVector;
	}

	@Override
	public double[] techColumnOf(int j) {
		return data.techMatrix.getColumn(j);
	}

	@Override
	public double techValueOf(int row, int col) {
		return data.techMatrix.get(row, col);
	}

	@Override
	public double[] solutionOfOne(int product) {
		var s = solutions.get(product);
		if (s != null)
			return s;
		s = factorization.solve(product, 1.0);
		solutions.put(product, s);
		return s;
	}

	@Override
	public double[] flowColumnOf(int j) {
		return data.flowMatrix.getColumn(j);
	}

	@Override
	public double flowValueOf(int flow, int product) {
		return data.flowMatrix.get(flow, product);
	}

	@Override
	public double[] totalFlowResults() {
		return totalFlows == null
				? new double[0]
				: totalFlows;
	}

	@Override
	public double[] totalFlowResultsOfOne(int product) {
		var m = intensities.get(product);
		if (m != null)
			return m;
		var s = solutionOfOne(product);
		m = solver.multiply(data.flowMatrix, s);
		intensities.put(product, m);
		return m;
	}

	@Override
	public double[] totalImpacts() {
		return totalImpacts == null
				? new double[0]
				: totalImpacts;
	}

	@Override
	public double[] totalImpactsOfOne(int product) {
		if (impacts == null)
			return new double[0];
		var h = impacts.get(product);
		if (h != null)
			return h;
		var g = totalFlowResultsOfOne(product);
		h = solver.multiply(data.impactMatrix, g);
		impacts.put(product, h);
		return h;
	}

	@Override
	public double totalImpactOfOne(int indicator, int product) {
		if (impacts == null)
			return 0;
		return totalImpactsOfOne(product)[indicator];
	}

	@Override
	public double totalCosts() {
		return totalCosts;
	}

	@Override
	public double totalCostsOfOne(int i) {
		if (data.costVector == null)
			return 0;
		var s = solutionOfOne(i);
		double c = 0.0;
		for (int j = 0; j < s.length; j++) {
			c += s[j] * data.costVector[j];
		}
		return c;
	}

	@Override
	public double loopFactorOf(int i) {
		var aii = data.techMatrix.get(i, i);
		var eii = solutionOfOne(i)[i];
		var f = aii * eii;
		return f == 0
				? 0
				: 1 / f;
	}

}
