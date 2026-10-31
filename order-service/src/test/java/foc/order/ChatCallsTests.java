/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: which transitions owe chat-service a call (C3-C5, C11), against
 * the real schema, and the dispatcher's send, retry, give-up and
 * in-order rules with a stubbed ChatClient.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import foc.order.model.ChatCall;
import foc.order.model.Requests;
import foc.order.repository.ChatCallRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ChatCallsTests extends PostgresTestContainer {

	private static final Long REQUESTER_ID = 1L;
	private static final Long COURIER_ID = 7L;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private ChatCallRepository chatCalls;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ---- which transitions owe a call --------------------------------

	@Test
	void anAcceptOpensTheCouriersChat() {
		Long id = newCreatedRequest();

		requestsService.accept(id, COURIER_ID);

		List<ChatCall> calls = chatCalls.findByRequestIdOrderById(id);
		assertEquals(1, calls.size());
		assertEquals(ChatCall.Kind.OPEN, calls.get(0).getKind());
		assertEquals(REQUESTER_ID, calls.get(0).getRequesterId());
		assertEquals(COURIER_ID, calls.get(0).getCourierId());
	}

	@Test
	void collectingAndArrivingKeepTheChatOpen() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.collect(id, COURIER_ID);
		requestsService.arrive(id, COURIER_ID);

		assertEquals(List.of(ChatCall.Kind.OPEN), kinds(id));
	}

	@Test
	void completingClosesTheChat() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);
		requestsService.arrive(id, COURIER_ID);

		requestsService.complete(id, REQUESTER_ID);

		assertEquals(List.of(ChatCall.Kind.OPEN, ChatCall.Kind.CLOSE), kinds(id));
	}

	@Test
	void aDropClosesTheChatAndTheNextCourierGetsANewOne() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.drop(id, COURIER_ID);
		requestsService.accept(id, 8L);

		List<ChatCall> calls = chatCalls.findByRequestIdOrderById(id);
		assertEquals(List.of(ChatCall.Kind.OPEN, ChatCall.Kind.CLOSE, ChatCall.Kind.OPEN), kinds(id));
		assertEquals(8L, calls.get(2).getCourierId());
	}

	@Test
	void aDisputeClosesTheChat() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.collect(id, COURIER_ID);
		requestsService.arrive(id, COURIER_ID);

		requestsService.dispute(id, REQUESTER_ID, "WRONG_ITEM", null);

		assertEquals(List.of(ChatCall.Kind.OPEN, ChatCall.Kind.CLOSE), kinds(id));
	}

	@Test
	void expiringAnAcceptedRequestClosesTheChat() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);

		requestsService.expire(id);

		assertEquals(List.of(ChatCall.Kind.OPEN, ChatCall.Kind.CLOSE), kinds(id));
	}

	@Test
	void requestsThatNeverHadACourierOweNoCall() {
		Long cancelled = newCreatedRequest();
		requestsService.cancel(cancelled, REQUESTER_ID);
		Long expired = newCreatedRequest();
		requestsService.expire(expired);

		assertEquals(List.of(), kinds(cancelled));
		assertEquals(List.of(), kinds(expired));
	}

	@Test
	void aRefusedAcceptOwesNothing() {
		Long id = newCreatedRequest();

		// a requester can't accept their own request: the rollback takes the
		// call with it
		assertThrows(IllegalStateException.class, () -> requestsService.accept(id, REQUESTER_ID));

		assertEquals(List.of(), kinds(id));
	}

	// ---- the dispatcher ---------------------------------------------

	@Test
	void aTakenCallIsDeleted() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		StubClient client = new StubClient(call -> true);

		dispatcher(client).dispatch();

		assertTrue(client.sent.stream().anyMatch(call -> call.getRequestId().equals(id)));
		assertEquals(List.of(), kinds(id));
	}

	@Test
	void aFailedCallIsDueAgainFifteenSecondsLaterAndGivenUpAfterTwentyAttempts() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		StubClient client = new StubClient(call -> !call.getRequestId().equals(id));

		Instant before = Instant.now();
		dispatcher(client).dispatch();
		ChatCall once = chatCalls.findByRequestIdOrderById(id).get(0);
		assertEquals(1, once.getAttempts());
		assertEquals(ChatCall.Status.PENDING, once.getStatus());
		assertTrue(!once.getNextAttemptAt().isBefore(before.plusSeconds(15)));

		for (int attempt = 2; attempt <= 20; attempt++) {
			makeDue(id);
			dispatcher(client).dispatch();
		}

		ChatCall given = chatCalls.findByRequestIdOrderById(id).get(0);
		assertEquals(20, given.getAttempts());
		assertEquals(ChatCall.Status.FAILED, given.getStatus());
		// no 21st attempt
		makeDue(id);
		int sentBefore = client.sent.size();
		dispatcher(client).dispatch();
		assertTrue(client.sent.subList(sentBefore, client.sent.size()).stream()
				.noneMatch(call -> call.getRequestId().equals(id)));
	}

	@Test
	void aCloseWaitsWhileItsOpenIsStillBeingRetried() {
		Long id = newCreatedRequest();
		requestsService.accept(id, COURIER_ID);
		requestsService.drop(id, COURIER_ID);
		StubClient failing = new StubClient(call -> !call.getRequestId().equals(id));

		dispatcher(failing).dispatch();
		makeDue(id);
		dispatcher(failing).dispatch();

		// only the open was ever tried
		assertTrue(failing.sent.stream().filter(call -> call.getRequestId().equals(id))
				.allMatch(call -> call.getKind() == ChatCall.Kind.OPEN));

		StubClient working = new StubClient(call -> true);
		makeDue(id);
		dispatcher(working).dispatch();
		dispatcher(working).dispatch();
		assertEquals(List.of(ChatCall.Kind.OPEN, ChatCall.Kind.CLOSE),
				working.sent.stream().filter(call -> call.getRequestId().equals(id)).map(ChatCall::getKind).toList());
		assertEquals(List.of(), kinds(id));
	}

	// ---- helpers ----------------------------------------------------

	private ChatCallDispatcher dispatcher(ChatClient client) {
		return new ChatCallDispatcher(chatCalls, client, 100, 15_000, 20);
	}

	private void makeDue(Long requestId) {
		jdbcTemplate.update("update chat_call set next_attempt_at = now() - interval '1 second' where request_id = ?",
				requestId);
	}

	private List<ChatCall.Kind> kinds(Long requestId) {
		return chatCalls.findByRequestIdOrderById(requestId).stream().map(ChatCall::getKind).toList();
	}

	private Long newCreatedRequest() {
		Requests request = new Requests();
		request.setRequesterId(REQUESTER_ID);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		Long id = requestsService.create(request).getId();
		requestsService.reserve(id);
		return id;
	}

	// records every call it is given; answers with the predicate
	private static final class StubClient implements ChatClient {

		private final Predicate<ChatCall> answer;
		private final List<ChatCall> sent = new ArrayList<>();

		StubClient(Predicate<ChatCall> answer) {
			this.answer = answer;
		}

		@Override
		public boolean send(ChatCall call) {
			sent.add(call);
			return answer.test(call);
		}
	}
}
