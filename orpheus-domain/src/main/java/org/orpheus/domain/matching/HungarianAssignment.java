package org.orpheus.domain.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Optimal file-track assignment through the Hungarian algorithm (Kuhn-Munkres), ADR-016.
 *
 * <p>Solves the minimum cost assignment problem on a generic cost matrix
 * {@code costMatrix[file][track]}, without requiring a square matrix: if the number of files and of
 * candidate tracks differ, the extra files or tracks simply stay unassigned in the returned
 * {@link Result}, instead of forcing a match.
 *
 * <p>A file-track pair to exclude up front (incompatible, already discarded by an upstream filter,
 * etc.) is marked with cost {@link #INFEASIBLE}. Such a pair never appears in the result: if the only
 * possible complete assignment would require it, the algorithm leaves that file and track unassigned
 * instead, because reporting a missing match is better than a silently wrong one (see DESIGN.md,
 * failure criteria).
 */
public final class HungarianAssignment {

	public static final double INFEASIBLE = Double.POSITIVE_INFINITY;

	private HungarianAssignment() {
	}

	public static Result solve(double[][] costMatrix) {
		validate(costMatrix);
		int rows = costMatrix.length;
		int cols = rows == 0 ? 0 : costMatrix[0].length;
		if (rows == 0 || cols == 0) {
			return new Result(List.of(), 0.0);
		}

		int n = Math.max(rows, cols);
		double bigM = infeasiblePenalty(costMatrix, rows, cols);
		double[][] a = new double[n + 1][n + 1];
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= n; j++) {
				if (i <= rows && j <= cols) {
					double cost = costMatrix[i - 1][j - 1];
					a[i][j] = cost == INFEASIBLE ? bigM : cost;
				}
			}
		}

		int[] rowAssignedToColumn = solveSquare(a, n);

		List<Assignment> assignments = new ArrayList<>();
		double totalCost = 0.0;
		for (int j = 1; j <= n; j++) {
			int i = rowAssignedToColumn[j];
			if (i == 0 || i > rows || j > cols) {
				continue;
			}
			double cost = costMatrix[i - 1][j - 1];
			if (cost != INFEASIBLE) {
				assignments.add(new Assignment(i - 1, j - 1, cost));
				totalCost += cost;
			}
		}
		return new Result(List.copyOf(assignments), totalCost);
	}

	private static int[] solveSquare(double[][] a, int n) {
		double[] u = new double[n + 1];
		double[] v = new double[n + 1];
		int[] p = new int[n + 1];
		int[] way = new int[n + 1];

		for (int i = 1; i <= n; i++) {
			p[0] = i;
			int j0 = 0;
			double[] minCostToColumn = new double[n + 1];
			boolean[] columnVisited = new boolean[n + 1];
			Arrays.fill(minCostToColumn, Double.POSITIVE_INFINITY);
			do {
				columnVisited[j0] = true;
				int i0 = p[j0];
				int nextColumn = -1;
				double delta = Double.POSITIVE_INFINITY;
				// Reduced cost against the current potentials: finds both the next node of the
				// augmenting tree (j1) and how much the potentials can be lowered (delta) without
				// making any reduced cost not yet visited negative.
				for (int j = 1; j <= n; j++) {
					if (!columnVisited[j]) {
						double reduced = a[i0][j] - u[i0] - v[j];
						if (reduced < minCostToColumn[j]) {
							minCostToColumn[j] = reduced;
							way[j] = j0;
						}
						if (minCostToColumn[j] < delta) {
							delta = minCostToColumn[j];
							nextColumn = j;
						}
					}
				}
				for (int j = 0; j <= n; j++) {
					if (columnVisited[j]) {
						u[p[j]] += delta;
						v[j] -= delta;
					} else {
						minCostToColumn[j] -= delta;
					}
				}
				j0 = nextColumn;
			} while (p[j0] != 0);
			// Augmenting path found: walk back the "way" chain swapping the assignment of each
			// column with that of the previous column, until row i is reached again.
			do {
				int j1 = way[j0];
				p[j0] = p[j1];
				j0 = j1;
			} while (j0 != 0);
		}
		return p;
	}

	private static double infeasiblePenalty(double[][] costMatrix, int rows, int cols) {
		double sumOfFiniteCosts = 0.0;
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				double cost = costMatrix[i][j];
				if (cost != INFEASIBLE) {
					sumOfFiniteCosts += cost;
					// If the sum overflows to Infinity the big-M becomes indistinguishable from
					// INFEASIBLE itself: reduced costs on an excluded pair would become
					// Infinity-Infinity = NaN, silently corrupting the comparison in the augmenting
					// path search (see solveSquare). An explicit refusal here is better.
					if (Double.isInfinite(sumOfFiniteCosts)) {
						throw new IllegalArgumentException(
						        "sum of the allowed costs too large: the internal big-M computation overflows");
					}
				}
			}
		}
		// It must exceed the cost of any complete assignment that uses only allowed pairs, so that
		// the algorithm picks an INFEASIBLE pair only when it has no alternative.
		return sumOfFiniteCosts + 1.0;
	}

	private static void validate(double[][] costMatrix) {
		if (costMatrix == null) {
			throw new IllegalArgumentException("costMatrix cannot be null");
		}
		int cols = -1;
		for (double[] row : costMatrix) {
			if (row == null) {
				throw new IllegalArgumentException("costMatrix cannot contain null rows");
			}
			if (cols == -1) {
				cols = row.length;
			} else if (row.length != cols) {
				throw new IllegalArgumentException("costMatrix must be rectangular");
			}
			for (double cost : row) {
				if (Double.isNaN(cost) || cost < 0.0) {
					throw new IllegalArgumentException(
					        "costs must be non-negative or INFEASIBLE, found: " + cost);
				}
			}
		}
	}

	public record Assignment(int fileIndex, int trackIndex, double cost) {
	}

	public record Result(List<Assignment> assignments, double totalCost) {
	}
}
