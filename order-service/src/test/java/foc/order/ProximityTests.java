/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: the Haversine distance and the 1 km limit (F8.1 refuses only
 * "more than 1 km").
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import foc.order.SupplierLocations.Coordinates;
import org.junit.jupiter.api.Test;

class ProximityTests {

	private static final Coordinates COM3 = new Coordinates(1.2948, 103.7743);

	@Test
	void aDegreeOfLatitudeIsAbout111Kilometres() {
		assertEquals(111.19, Proximity.kmBetween(new Coordinates(0, 0), new Coordinates(1, 0)), 0.01);
	}

	@Test
	void theSamePlaceIsZeroApart() {
		assertEquals(0, Proximity.kmBetween(COM3, COM3), 1e-9);
	}

	@Test
	void justUnderOneKilometreIsAllowedJustOverIsNot() {
		assertFalse(Proximity.tooFarApart(COM3, new Coordinates(COM3.latitude() + 0.0089, COM3.longitude())));
		assertTrue(Proximity.tooFarApart(COM3, new Coordinates(COM3.latitude() + 0.0091, COM3.longitude())));
	}
}
