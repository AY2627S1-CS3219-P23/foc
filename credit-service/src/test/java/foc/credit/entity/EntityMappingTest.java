/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: each scaffold entity is saved and read back through its
 * repository against the Flyway V1 schema. ddl-auto is none, so nothing
 * else checks that the mappings match the tables.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import foc.credit.PostgresTestContainer;
import foc.credit.repository.CommonPoolRepository;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.CreditHistoryEntryRepository;
import foc.credit.repository.CreditLotRepository;
import foc.credit.repository.CreditReservationSliceRepository;
import foc.credit.repository.OutboxEventRepository;
import foc.credit.repository.RedistributionRunRepository;
import foc.credit.repository.ReservedRepository;

// @Transactional: every test rolls back, so the shared database stays clean
@SpringBootTest
@Transactional
class EntityMappingTest extends PostgresTestContainer {

    // Postgres keeps microseconds
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant LATER = NOW.plus(90, ChronoUnit.DAYS);

    private static final String REQUESTER = "u-requester";
    private static final String COURIER = "u-courier";
    private static final String REQUEST = "r-1";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CreditAccountRepository accounts;

    @Autowired
    private ReservedRepository reservations;

    @Autowired
    private CreditReservationSliceRepository slices;

    @Autowired
    private CreditLotRepository lots;

    @Autowired
    private CreditHistoryEntryRepository history;

    @Autowired
    private CommonPoolRepository commonPool;

    @Autowired
    private RedistributionRunRepository redistributionRuns;

    @Autowired
    private OutboxEventRepository outbox;

    // the other tables reference accounts
    @BeforeEach
    void saveAccounts() {
        accounts.save(new CreditAccount(REQUESTER, 5, 0, NOW));
        accounts.save(new CreditAccount(COURIER, 5, 0, NOW));
        flushAndClear();
    }

    // so the read comes from the database, not the persistence context
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void creditAccount() {
        CreditAccount account = accounts.findById(REQUESTER).orElseThrow();
        account.setAvailable(2);
        account.setReserved(3);
        flushAndClear();

        CreditAccount found = accounts.findById(REQUESTER).orElseThrow();
        assertThat(found.getAvailable()).isEqualTo(2);
        assertThat(found.getReserved()).isEqualTo(3);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void reserved() {
        Reserved reservation = new Reserved(REQUEST, REQUESTER, 3, ReservationStatus.HELD, NOW);
        reservations.save(reservation);
        flushAndClear();

        Reserved held = reservations.findById(REQUEST).orElseThrow();
        assertThat(held.getRequesterId()).isEqualTo(REQUESTER);
        assertThat(held.getAmount()).isEqualTo(3);
        assertThat(held.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(held.getCourierId()).isNull();
        assertThat(held.getSettledAt()).isNull();

        held.setStatus(ReservationStatus.TRANSFERRED);
        held.setCourierId(COURIER);
        held.setSettledAt(LATER);
        flushAndClear();

        Reserved transferred = reservations.findById(REQUEST).orElseThrow();
        assertThat(transferred.getStatus()).isEqualTo(ReservationStatus.TRANSFERRED);
        assertThat(transferred.getCourierId()).isEqualTo(COURIER);
        assertThat(transferred.getSettledAt()).isEqualTo(LATER);
        assertThat(transferred.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void creditReservationSlice() {
        reservations.save(new Reserved(REQUEST, REQUESTER, 3, ReservationStatus.HELD, NOW));
        Long id = slices.save(new CreditReservationSlice(REQUEST, 3, NOW, null)).getId();
        flushAndClear();

        CreditReservationSlice found = slices.findById(id).orElseThrow();
        assertThat(found.getRequestRef()).isEqualTo(REQUEST);
        assertThat(found.getAmount()).isEqualTo(3);
        assertThat(found.getEarnedAt()).isEqualTo(NOW);
        assertThat(found.getExpiresAt()).isNull();
    }

    @Test
    void creditLot() {
        Long id = lots.save(new CreditLot(COURIER, 4, NOW, LATER)).getId();
        flushAndClear();

        CreditLot lot = lots.findById(id).orElseThrow();
        assertThat(lot.getOwnerId()).isEqualTo(COURIER);
        assertThat(lot.getEarnedAt()).isEqualTo(NOW);
        assertThat(lot.getExpiresAt()).isEqualTo(LATER);

        lot.setRemaining(1);
        flushAndClear();

        assertThat(lots.findById(id).orElseThrow().getRemaining()).isEqualTo(1);
    }

    @Test
    void creditHistoryEntry() {
        Long id = history.save(
            new CreditHistoryEntry(REQUESTER, CreditHistoryType.PROVISION, 5, null, NOW)).getId();
        flushAndClear();

        CreditHistoryEntry found = history.findById(id).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(REQUESTER);
        assertThat(found.getType()).isEqualTo(CreditHistoryType.PROVISION);
        assertThat(found.getAmount()).isEqualTo(5);
        assertThat(found.getRequestRef()).isNull();
        assertThat(found.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void commonPool() {
        // V1 inserts the row
        CommonPool pool = commonPool.findById(CommonPool.ID).orElseThrow();
        assertThat(pool.getBalance()).isZero();

        pool.setBalance(7);
        flushAndClear();

        assertThat(commonPool.findById(CommonPool.ID).orElseThrow().getBalance()).isEqualTo(7);
    }

    @Test
    void redistributionRun() {
        redistributionRuns.save(new RedistributionRun("2026-10", 12, 2, NOW));
        flushAndClear();

        RedistributionRun found = redistributionRuns.findById("2026-10").orElseThrow();
        assertThat(found.getPeriod()).isEqualTo("2026-10");
        assertThat(found.getDistributed()).isEqualTo(12);
        assertThat(found.getRemainder()).isEqualTo(2);
        assertThat(found.getRanAt()).isEqualTo(NOW);
    }

    @Test
    void outboxEvent() {
        UUID eventId = UUID.randomUUID();
        outbox.save(new OutboxEvent(eventId, "credit.reserved", "{\"requestId\":\"r-1\"}", NOW));
        flushAndClear();

        OutboxEvent unsent = outbox.findById(eventId).orElseThrow();
        assertThat(unsent.getEventType()).isEqualTo("credit.reserved");
        assertThat(unsent.getPayload()).isEqualTo("{\"requestId\":\"r-1\"}");
        assertThat(unsent.getCreatedAt()).isEqualTo(NOW);
        assertThat(unsent.getSentAt()).isNull();

        unsent.setSentAt(LATER);
        flushAndClear();

        assertThat(outbox.findById(eventId).orElseThrow().getSentAt()).isEqualTo(LATER);
    }
}
