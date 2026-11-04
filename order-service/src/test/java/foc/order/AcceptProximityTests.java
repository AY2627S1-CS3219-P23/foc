/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: accept's pickup-distance rule against real Postgres: a courier
 * holding nothing is not checked; pickups within 1 km of every held one
 * (ACCEPTED, COLLECTED, ARRIVED) are accepted, a farther one is refused
 * (409) and the request stays open; finished requests don't count; an
 * unreadable location fails closed (503). Suppliers are placed through
 * FakeSupplierLocations. Each test has its own courier, as the database
 * is shared across test classes.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AcceptProximityTests extends PostgresTestContainer {

	// couriers no other test class uses
	private static final AtomicLong COURIERS = new AtomicLong(700_000);
	private static final Long REQUESTER_ID = 1L;

	// suppliers on one north-south line: 0.0089 deg of latitude is about
	// 0.99 km, 0.0091 deg about 1.01 km
	private static final long HERE = 9001;
	private static final long NEAR = 9002;
	private static final long FAR = 9003;
	private static final double LAT = 1.2966;
	private static final double LNG = 103.7764;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private RequestsRepository requestsRepository;

	@Autowired
	private FakeSupplierLocations suppliers;

	private long courier;

	@BeforeEach
	void placeSuppliers() {
		courier = COURIERS.incrementAndGet();
		suppliers.reset();
		suppliers.place(HERE, LAT, LNG);
		suppliers.place(NEAR, LAT + 0.0089, LNG);
		suppliers.place(FAR, LAT + 0.0091, LNG);
	}

	@AfterEach
	void resetSuppliers() {
		suppliers.reset();
	}

	@Test
	void aCourierHoldingNothingIsNotChecked() {
		Long id = openRequestAt(HERE);
		suppliers.down();

		requestsService.accept(id, courier);

		assertEquals(OrderStates.ACCEPTED, stored(id).getStatus());
		assertEquals(0, suppliers.calls.get());
	}

	@Test
	void aPickupWithinOneKilometreOfAHeldOneIsAccepted() {
		requestsService.accept(openRequestAt(HERE), courier);
		Long near = openRequestAt(NEAR);

		requestsService.accept(near, courier);

		assertEquals(OrderStates.ACCEPTED, stored(near).getStatus());
		assertEquals(courier, stored(near).getCourierId());
	}

	@Test
	void aPickupMoreThanOneKilometreAwayIsRefusedAndStaysOpen() {
		Long held = openRequestAt(HERE);
		requestsService.accept(held, courier);
		Long far = openRequestAt(FAR);

		IllegalStateException refused = assertThrows(IllegalStateException.class,
				() -> requestsService.accept(far, courier));

		assertTrue(refused.getMessage().contains("more than 1 km"), refused.getMessage());
		assertTrue(refused.getMessage().contains("#" + held), refused.getMessage());
		assertEquals(OrderStates.CREATED, stored(far).getStatus());
		assertNull(stored(far).getCourierId());
	}

	@Test
	void everyHeldPickupMustBeNear() {
		// FAR is a few metres from NEAR, but over 1 km from HERE:
		// holding HERE still rules FAR out
		requestsService.accept(openRequestAt(NEAR), courier);
		requestsService.accept(openRequestAt(HERE), courier);

		assertThrows(IllegalStateException.class, () -> requestsService.accept(openRequestAt(FAR), courier));
	}

	@Test
	void collectedAndArrivedRequestsAreHeldToo() {
		Long collected = openRequestAt(HERE);
		requestsService.accept(collected, courier);
		requestsService.collect(collected, courier);

		assertThrows(IllegalStateException.class, () -> requestsService.accept(openRequestAt(FAR), courier));

		requestsService.arrive(collected, courier);

		assertThrows(IllegalStateException.class, () -> requestsService.accept(openRequestAt(FAR), courier));
	}

	@Test
	void finishedAndDroppedRequestsDoNotCount() {
		Long done = openRequestAt(HERE);
		requestsService.accept(done, courier);
		requestsService.collect(done, courier);
		requestsService.arrive(done, courier);
		requestsService.complete(done, REQUESTER_ID);
		Long dropped = openRequestAt(HERE);
		requestsService.accept(dropped, courier);
		requestsService.drop(dropped, courier);
		Long far = openRequestAt(FAR);

		requestsService.accept(far, courier);

		assertEquals(OrderStates.ACCEPTED, stored(far).getStatus());
	}

	@Test
	void supplierServiceDownFailsClosed() {
		requestsService.accept(openRequestAt(HERE), courier);
		Long near = openRequestAt(NEAR);
		suppliers.down();

		assertThrows(SupplierLookupException.class, () -> requestsService.accept(near, courier));

		assertEquals(OrderStates.CREATED, stored(near).getStatus());
		assertNull(stored(near).getCourierId());
	}

	@Test
	void aPickupWithoutALocationFailsClosed() {
		requestsService.accept(openRequestAt(HERE), courier);
		Long near = openRequestAt(NEAR);
		suppliers.hide(NEAR);

		assertThrows(SupplierLookupException.class, () -> requestsService.accept(near, courier));

		assertEquals(OrderStates.CREATED, stored(near).getStatus());
	}

	// A request open for couriers (CREATED) with the given pickup.
	private Long openRequestAt(long supplierId) {
		Requests request = new Requests();
		request.setRequesterId(REQUESTER_ID);
		request.setSupplierId(supplierId);
		request.setSupplierName("Supplier " + supplierId);
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		Long id = requestsService.create(request).getId();
		requestsService.reserve(id);
		return id;
	}

	private Requests stored(Long id) {
		return requestsRepository.findById(id).orElseThrow();
	}
}
