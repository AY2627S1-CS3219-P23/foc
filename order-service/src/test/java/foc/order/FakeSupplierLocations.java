/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: the test context's SupplierLocations, in place of the HTTP one
 * (no supplier-service in tests). By default every supplier is at the
 * same spot, so the many tests where one courier holds several requests
 * pass the proximity check; the F8.1 tests place suppliers, hide them,
 * or take supplier-service down, and reset() after.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
class FakeSupplierLocations implements SupplierLocations {

	static final Coordinates DEFAULT = new Coordinates(1.2966, 103.7764);

	private final Map<Long, Coordinates> placed = new HashMap<>();
	private final Set<Long> missing = new HashSet<>();
	private volatile boolean down;
	final AtomicInteger calls = new AtomicInteger();

	@Override
	public synchronized Map<Long, Coordinates> of(Set<Long> supplierIds) {
		calls.incrementAndGet();
		if (down) {
			throw new SupplierLookupException("supplier-service is down (test)");
		}
		Map<Long, Coordinates> found = new HashMap<>();
		for (Long id : supplierIds) {
			if (!missing.contains(id)) {
				found.put(id, placed.getOrDefault(id, DEFAULT));
			}
		}
		return found;
	}

	synchronized void place(long supplierId, double latitude, double longitude) {
		placed.put(supplierId, new Coordinates(latitude, longitude));
	}

	synchronized void hide(long supplierId) {
		missing.add(supplierId);
	}

	void down() {
		down = true;
	}

	synchronized void reset() {
		placed.clear();
		missing.clear();
		down = false;
		calls.set(0);
	}
}
