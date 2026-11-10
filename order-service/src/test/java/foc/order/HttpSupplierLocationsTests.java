/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: HttpSupplierLocations against an in-process HTTP server standing
 * in for supplier-service's GET /suppliers: the caller's bearer token is
 * passed on, pages are read until every asked-for id is seen, ids it does
 * not list (or lists without coordinates) are absent, and an error
 * answer, an unreachable service or a missing caller token throws
 * SupplierLookupException. No Spring context.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import foc.order.SupplierLocations.Coordinates;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HttpSupplierLocationsTests {

	private static final String TOKEN = "Bearer courier-token";

	private HttpServer server;
	private final List<String> queries = new CopyOnWriteArrayList<>();
	private final List<String> authorizations = new CopyOnWriteArrayList<>();
	// one JSON page per page number; status 200 unless set
	private volatile List<String> pages = List.of();
	private volatile int status = 200;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/suppliers", this::answer);
		server.start();
		MockHttpServletRequest accept = new MockHttpServletRequest("POST", "/accept");
		accept.addHeader("Authorization", TOKEN);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(accept));
	}

	@AfterEach
	void stop() {
		server.stop(0);
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void readsTheAskedForSuppliersWithTheCallersToken() {
		pages = List.of(page(1, supplier(1, 1.30, 103.77), supplier(2, 1.31, 103.78), supplier(3, 1.32, 103.79)));

		Map<Long, Coordinates> found = locations().of(Set.of(1L, 3L));

		assertEquals(Map.of(1L, new Coordinates(1.30, 103.77), 3L, new Coordinates(1.32, 103.79)), found);
		assertEquals(List.of(TOKEN), authorizations);
		assertEquals(List.of("page=0&size=" + HttpSupplierLocations.PAGE_SIZE), queries);
	}

	@Test
	void pagesOnUntilEveryIdIsSeen() {
		pages = List.of(page(3, supplier(1, 1.30, 103.77)), page(3, supplier(2, 1.31, 103.78)),
				page(3, supplier(3, 1.32, 103.79)));

		Map<Long, Coordinates> found = locations().of(Set.of(2L));

		assertEquals(Map.of(2L, new Coordinates(1.31, 103.78)), found);
		// stops once 2 is seen: page 2 is never asked for
		assertEquals(2, queries.size());
	}

	@Test
	void unlistedSuppliersAndSuppliersWithoutCoordinatesAreAbsent() {
		pages = List.of(page(1, supplier(1, 1.30, 103.77), "{\"id\":\"2\",\"name\":\"No place\"}"));

		Map<Long, Coordinates> found = locations().of(Set.of(1L, 2L, 99L));

		assertEquals(Set.of(1L), found.keySet());
	}

	@Test
	void anErrorAnswerThrows() {
		status = 500;

		assertThrows(SupplierLookupException.class, () -> locations().of(Set.of(1L)));
	}

	@Test
	void anUnreachableServiceThrows() {
		HttpSupplierLocations nowhere = new HttpSupplierLocations("http://127.0.0.1:1");

		assertThrows(SupplierLookupException.class, () -> nowhere.of(Set.of(1L)));
	}

	@Test
	void withoutACallerTokenNothingIsAsked() {
		RequestContextHolder.resetRequestAttributes();

		assertThrows(SupplierLookupException.class, () -> locations().of(Set.of(1L)));
		assertFalse(queries.size() > 0);
	}

	private HttpSupplierLocations locations() {
		return new HttpSupplierLocations("http://127.0.0.1:" + server.getAddress().getPort());
	}

	private void answer(HttpExchange exchange) throws IOException {
		String query = exchange.getRequestURI().getQuery();
		queries.add(query);
		authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
		int page = Integer.parseInt(query.replaceAll(".*page=(\\d+).*", "$1"));
		byte[] body = (status == 200 ? pages.get(page) : "{\"message\":\"boom\"}").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static String supplier(long id, double lat, double lng) {
		return "{\"id\":\"" + id + "\",\"name\":\"S" + id + "\",\"latitude\":" + lat + ",\"longitude\":" + lng
				+ ",\"categories\":[],\"bookmarked\":false}";
	}

	private static String page(int totalPages, String... suppliers) {
		return "{\"content\":[" + String.join(",", suppliers) + "],\"page\":0,\"size\":"
				+ HttpSupplierLocations.PAGE_SIZE + ",\"totalElements\":" + suppliers.length + ",\"totalPages\":"
				+ totalPages + "}";
	}
}
