/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.1, N8.2) unit tests for BookmarkService: add,
 * the idempotent re-add, the cap of 5 (D10), a missing supplier, the
 * per-user lock refusing a simultaneous request without saving, remove,
 * and the list's count/limit.
 * Reviewed by: [pending]
 */
package foc.supplier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import foc.supplier.exception.BookmarkLimitException;
import foc.supplier.exception.BookmarkTooFastException;
import foc.supplier.exception.SupplierNotFoundException;
import foc.supplier.model.SupplierBookmarkId;
import foc.supplier.model.SupplierBookmarks;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierBookmarksRepository;
import foc.supplier.repository.SuppliersRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long SUPPLIER_ID = 12L;

    @Mock
    private SupplierBookmarksRepository bookmarksRepository;

    @Mock
    private SuppliersRepository suppliersRepository;

    @Mock
    private BookmarkLock bookmarkLock;

    @InjectMocks
    private BookmarkService bookmarkService;

    private static Suppliers supplier() {
        Suppliers supplier = new Suppliers();
        ReflectionTestUtils.setField(supplier, "id", SUPPLIER_ID);
        supplier.setName("A Hot Hideout");
        return supplier;
    }

    private void supplierExistsAndLockFree() {
        when(suppliersRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier()));
        when(bookmarkLock.tryLock(USER_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("Should save a new bookmark and report the new count")
    void add_savesAndCounts() {
        supplierExistsAndLockFree();
        when(bookmarksRepository.findById(new SupplierBookmarkId(USER_ID, SUPPLIER_ID))).thenReturn(Optional.empty());
        when(bookmarksRepository.countByUserId(USER_ID)).thenReturn(2L);
        when(bookmarksRepository.save(any(SupplierBookmarks.class))).thenAnswer(i -> i.getArgument(0));

        var response = bookmarkService.add(USER_ID, SUPPLIER_ID);

        assertThat(response.bookmarked()).isTrue();
        assertThat(response.supplierId()).isEqualTo("12");
        assertThat(response.count()).isEqualTo(3);
        verify(bookmarksRepository).save(any(SupplierBookmarks.class));
    }

    @Test
    @DisplayName("Should treat re-adding an existing bookmark as a no-op, even at the cap")
    void add_existingBookmarkIsIdempotentEvenAtTheCap() {
        supplierExistsAndLockFree();
        SupplierBookmarks existing = new SupplierBookmarks(USER_ID, supplier());
        when(bookmarksRepository.findById(new SupplierBookmarkId(USER_ID, SUPPLIER_ID)))
                .thenReturn(Optional.of(existing));
        when(bookmarksRepository.countByUserId(USER_ID)).thenReturn(5L);

        var response = bookmarkService.add(USER_ID, SUPPLIER_ID);

        assertThat(response.count()).isEqualTo(5);
        verify(bookmarksRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a sixth bookmark with the limit message and save nothing")
    void add_rejectsBeyondTheCap() {
        supplierExistsAndLockFree();
        when(bookmarksRepository.findById(new SupplierBookmarkId(USER_ID, SUPPLIER_ID))).thenReturn(Optional.empty());
        when(bookmarksRepository.countByUserId(USER_ID)).thenReturn(5L);

        assertThatThrownBy(() -> bookmarkService.add(USER_ID, SUPPLIER_ID))
                .isInstanceOf(BookmarkLimitException.class)
                .hasMessage("You have reached the limit of 5 bookmarks.");
        verify(bookmarksRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should refuse a simultaneous request from the same user and save nothing")
    void add_refusesWhenTheUsersLockIsHeld() {
        when(suppliersRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier()));
        when(bookmarkLock.tryLock(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> bookmarkService.add(USER_ID, SUPPLIER_ID))
                .isInstanceOf(BookmarkTooFastException.class)
                .hasMessage("You are bookmarking too fast, please try again.");
        verify(bookmarksRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject an unknown supplier before touching the lock or the table")
    void add_rejectsUnknownSupplier() {
        when(suppliersRepository.findById(SUPPLIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.add(USER_ID, SUPPLIER_ID))
                .isInstanceOf(SupplierNotFoundException.class);
        verify(bookmarkLock, never()).tryLock(any());
        verify(bookmarksRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should remove only the caller's own bookmark")
    void remove_deletesForTheCallerOnly() {
        bookmarkService.remove(USER_ID, SUPPLIER_ID);

        verify(bookmarksRepository).deleteByUserIdAndSupplierId(USER_ID, SUPPLIER_ID);
    }

    @Test
    @DisplayName("Should list the caller's bookmarks with their count and the limit")
    void list_returnsCountAndLimit() {
        SupplierBookmarks bookmark = new SupplierBookmarks(USER_ID, supplier());
        ReflectionTestUtils.setField(bookmark, "supplierId", SUPPLIER_ID);
        when(bookmarksRepository.findByUserIdNewestFirst(USER_ID)).thenReturn(List.of(bookmark));

        var response = bookmarkService.list(USER_ID);

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.limit()).isEqualTo(5);
        assertThat(response.bookmarks().get(0).name()).isEqualTo("A Hot Hideout");
        assertThat(response.bookmarks().get(0).supplierId()).isEqualTo("12");
    }
}
