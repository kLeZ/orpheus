package org.orpheus.domain.port;

import org.orpheus.domain.model.ReviewItem;

/**
 * Records a {@link ReviewItem} in the review queue (SPEC.md section 5). The persistence adapter
 * (ADR-010) implements this port; the domain knows nothing about SQLite or Hibernate.
 */
public interface ReviewQueuePort {

	void record(ReviewItem item);
}
