package org.orpheus.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.orpheus.domain.matching.HungarianAssignment.INFEASIBLE;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.orpheus.domain.matching.HungarianAssignment.Assignment;
import org.orpheus.domain.matching.HungarianAssignment.Result;

class HungarianAssignmentTest {

	private static final Offset<Double> TOLERANCE = offset(1e-9);

	@Test
	void squareMatrixWithASingleSolution() {
		double[][] cost = {
		                   {
		                    1,
		                    10,
		                    10
						   },
		                   {
		                    10,
		                    1,
		                    10
						   },
		                   {
		                    10,
		                    10,
		                    1
						   }
		};

		Result result = HungarianAssignment.solve(cost);

		assertThat(result.totalCost()).isCloseTo(3.0, TOLERANCE);
		assertThat(result.assignments()).hasSize(3);
		assertThat(result.assignments())
		                                .containsExactlyInAnyOrder(
		                                        new Assignment(0, 0, 1), new Assignment(1, 1, 1), new Assignment(2, 2, 1));
	}

	@Test
	void rectangularMatrixWithMoreFilesThanTracks() {
		double[][] cost = {
		                   {
		                    1,
		                    10
						   },
		                   {
		                    10,
		                    1
						   },
		                   {
		                    5,
		                    5
						   },
		                   {
		                    9,
		                    9
						   }
		};

		Result result = HungarianAssignment.solve(cost);

		assertThat(result.totalCost()).isCloseTo(2.0, TOLERANCE);
		assertThat(result.assignments()).hasSize(2);
		assertThat(result.assignments())
		                                .containsExactlyInAnyOrder(new Assignment(0, 0, 1), new Assignment(1, 1, 1));
		assertValidMatching(result);
	}

	@Test
	void rectangularMatrixWithMoreTracksThanFiles() {
		double[][] cost = {
		                   {
		                    1,
		                    10,
		                    5,
		                    9
						   },
		                   {
		                    10,
		                    1,
		                    5,
		                    9
						   }
		};

		Result result = HungarianAssignment.solve(cost);

		assertThat(result.totalCost()).isCloseTo(2.0, TOLERANCE);
		assertThat(result.assignments()).hasSize(2);
		assertThat(result.assignments())
		                                .containsExactlyInAnyOrder(new Assignment(0, 0, 1), new Assignment(1, 1, 1));
		assertValidMatching(result);
	}

	@Test
	void excludedInfiniteCostsForceAnAlternativeMatch() {
		double[][] cost = {
		                   {
		                    INFEASIBLE,
		                    5
						   },
		                   {
		                    3,
		                    INFEASIBLE
						   }
		};

		Result result = HungarianAssignment.solve(cost);

		assertThat(result.totalCost()).isCloseTo(8.0, TOLERANCE);
		assertThat(result.assignments())
		                                .containsExactlyInAnyOrder(new Assignment(0, 1, 5), new Assignment(1, 0, 3));
	}

	@Test
	void infeasiblePairNotForcedWhenUnavoidable() {
		double[][] cost = {
		                   {
		                    INFEASIBLE,
		                    INFEASIBLE
						   },
		                   {
		                    3,
		                    INFEASIBLE
						   }
		};

		Result result = HungarianAssignment.solve(cost);

		assertThat(result.totalCost()).isCloseTo(3.0, TOLERANCE);
		assertThat(result.assignments()).containsExactly(new Assignment(1, 0, 3));
		assertThat(result.assignments()).noneMatch(a -> a.cost() == INFEASIBLE);
	}

	@Test
	void multipleOptimalSolutionsAcceptAnyValidMatch() {
		double[][] cost = {
		                   {
		                    1,
		                    1
						   },
		                   {
		                    1,
		                    1
						   }
		};

		Result result = HungarianAssignment.solve(cost);

		assertThat(result.totalCost()).isCloseTo(2.0, TOLERANCE);
		assertThat(result.assignments()).hasSize(2);
		assertValidMatching(result);
	}

	@Test
	void multipleOptimalSolutions3x3AcceptAnyValidMatch() {
		double[][] cost = {
		                   {
		                    5,
		                    5,
		                    5
						   },
		                   {
		                    5,
		                    5,
		                    5
						   },
		                   {
		                    5,
		                    5,
		                    5
						   }
		};

		Result result = HungarianAssignment.solve(cost);

		assertThat(result.totalCost()).isCloseTo(15.0, TOLERANCE);
		assertThat(result.assignments()).hasSize(3);
		assertValidMatching(result);
	}

	@Test
	void oneByOneMatrix() {
		Result result = HungarianAssignment.solve(new double[][]{
		                                                         {
		                                                          7
																 }
		});

		assertThat(result.totalCost()).isCloseTo(7.0, TOLERANCE);
		assertThat(result.assignments()).containsExactly(new Assignment(0, 0, 7));
	}

	@Test
	void oneByOneInfeasibleMatrixProducesNoMatch() {
		Result result = HungarianAssignment.solve(new double[][]{
		                                                         {
		                                                          INFEASIBLE
																 }
		});

		assertThat(result.assignments()).isEmpty();
		assertThat(result.totalCost()).isCloseTo(0.0, TOLERANCE);
	}

	@Test
	void zeroFilesAndZeroTracks() {
		Result result = HungarianAssignment.solve(new double[0][0]);

		assertThat(result.assignments()).isEmpty();
		assertThat(result.totalCost()).isCloseTo(0.0, TOLERANCE);
	}

	@Test
	void filesWithoutCandidateTracks() {
		Result result = HungarianAssignment.solve(new double[3][0]);

		assertThat(result.assignments()).isEmpty();
	}

	@Test
	void tracksWithoutFiles() {
		Result result = HungarianAssignment.solve(new double[0][4]);

		assertThat(result.assignments()).isEmpty();
	}

	@Test
	void nullMatrixNotAllowed() {
		assertThatThrownBy(() -> HungarianAssignment.solve(null))
		                                                         .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nonRectangularMatrixNotAllowed() {
		double[][] cost = {
		                   {
		                    1,
		                    2
						   },
		                   {
		                    1,
		                    2,
		                    3
						   }
		};

		assertThatThrownBy(() -> HungarianAssignment.solve(cost))
		                                                         .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void negativeCostNotAllowed() {
		double[][] cost = {
		                   {
		                    -1
						   }
		};

		assertThatThrownBy(() -> HungarianAssignment.solve(cost))
		                                                         .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nanCostNotAllowed() {
		double[][] cost = {
		                   {
		                    Double.NaN
						   }
		};

		assertThatThrownBy(() -> HungarianAssignment.solve(cost))
		                                                         .isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void finiteCostsLargeEnoughToOverflowTheBigMAreRejectedExplicitly() {
		double huge = Double.MAX_VALUE / 2;
		double[][] cost = {
		                   {
		                    HungarianAssignment.INFEASIBLE,
		                    huge
						   },
		                   {
		                    huge,
		                    huge
						   }
		};

		assertThatThrownBy(() -> HungarianAssignment.solve(cost))
		                                                         .isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = {
	                     1,
	                     2,
	                     3,
	                     4,
	                     5,
	                     6
	})
	void bruteForceOracleOnRandomSquareAndRectangularMatrices(int seed) {
		Random random = new Random(seed);
		for (int trial = 0; trial < 20; trial++) {
			int rows = 1 + random.nextInt(5);
			int cols = 1 + random.nextInt(5);
			double[][] cost = new double[rows][cols];
			for (int i = 0; i < rows; i++) {
				for (int j = 0; j < cols; j++) {
					if (random.nextInt(5) == 0) {
						cost[i][j] = INFEASIBLE;
					} else {
						cost[i][j] = random.nextInt(20);
					}
				}
			}

			Result result = HungarianAssignment.solve(cost);
			assertValidMatching(result);
			assertThat(result.assignments()).allMatch(a -> a.cost() != INFEASIBLE);

			BruteForceBest expected = bruteForceBest(cost, rows, cols);
			assertThat(result.assignments())
			                                .as("expected cardinality for %s (seed %d, trial %d)", matrixToString(cost), seed, trial)
			                                .hasSize(expected.count());
			assertThat(result.totalCost())
			                              .as("expected cost for %s (seed %d, trial %d)", matrixToString(cost), seed, trial)
			                              .isCloseTo(expected.sum(), TOLERANCE);
		}
	}

	private static void assertValidMatching(Result result) {
		Set<Integer> files = new HashSet<>();
		Set<Integer> tracks = new HashSet<>();
		for (Assignment a : result.assignments()) {
			assertThat(files.add(a.fileIndex()))
			                                    .as("file %d assigned more than once", a.fileIndex())
			                                    .isTrue();
			assertThat(tracks.add(a.trackIndex()))
			                                      .as("track %d assigned more than once", a.trackIndex())
			                                      .isTrue();
		}
		double sum = result.assignments().stream().mapToDouble(Assignment::cost).sum();
		assertThat(result.totalCost()).isCloseTo(sum, TOLERANCE);
	}

	private record BruteForceBest(int count, double sum) {
	}

	/**
	 * Explores every subset of allowed file-track matches (finite cost), whatever the cardinality, and
	 * keeps the best by (maximum cardinality, then minimum cost at equal cardinality): the same
	 * criterion implied by the big-M cost of the implementation under test, checked here completely
	 * independently of that logic.
	 */
	private static BruteForceBest bruteForceBest(double[][] cost, int rows, int cols) {
		BruteForceBest[] best = {
		                         new BruteForceBest(-1, 0.0)
		};
		bruteForceRecurse(cost, rows, cols, 0, new boolean[cols], 0, 0.0, best);
		return best[0];
	}

	private static void bruteForceRecurse(
	                                      double[][] cost, int rows, int cols, int row, boolean[] usedColumns, int count, double sum, BruteForceBest[] best) {
		if (row == rows) {
			if (count > best[0].count() || (count == best[0].count() && sum < best[0].sum())) {
				best[0] = new BruteForceBest(count, sum);
			}
			return;
		}
		bruteForceRecurse(cost, rows, cols, row + 1, usedColumns, count, sum, best);
		for (int j = 0; j < cols; j++) {
			if (!usedColumns[j] && cost[row][j] != INFEASIBLE) {
				usedColumns[j] = true;
				bruteForceRecurse(cost, rows, cols, row + 1, usedColumns, count + 1, sum + cost[row][j], best);
				usedColumns[j] = false;
			}
		}
	}

	private static String matrixToString(double[][] cost) {
		StringBuilder sb = new StringBuilder();
		for (double[] row : cost) {
			sb.append(java.util.Arrays.toString(row)).append(' ');
		}
		return sb.toString();
	}
}
