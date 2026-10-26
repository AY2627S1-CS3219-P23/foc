/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.1, N8.2), bookmark list/add/remove for the calling
 * user. The cap (D10) is checked inside a per-user lock (BookmarkLock): a
 * second simultaneous add from the same user is refused with nothing
 * saved, rather than retried. Adding an existing bookmark is a no-op that
 * does not count against the cap. Removing one that does not exist
 * succeeds. purgeUsers removes every bookmark of purged accounts (N8.5).
 * PR review: the insert is flushed inside add, so a supplier deleted between
 * the lookup and the insert (a foreign-key violation) answers 404 like any
 * unknown supplier instead of surfacing as a 500 at commit.
 * Reviewed by: [pending]
 */
package foc.supplier.service;

import foc.supplier.dto.BookmarkListResponse;
import foc.supplier.dto.BookmarkResponse;
import foc.supplier.exception.BookmarkLimitException;
import foc.supplier.exception.BookmarkTooFastException;
import foc.supplier.exception.SupplierNotFoundException;
import foc.supplier.model.SupplierBookmarkId;
import foc.supplier.model.SupplierBookmarks;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierBookmarksRepository;
import foc.supplier.repository.SuppliersRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {

    public static final int BOOKMARK_LIMIT = 5;

    private final SupplierBookmarksRepository bookmarksRepository;
    private final SuppliersRepository suppliersRepository;
    private final BookmarkLock bookmarkLock;

    public BookmarkService(SupplierBookmarksRepository bookmarksRepository,
            SuppliersRepository suppliersRepository, BookmarkLock bookmarkLock) {
        this.bookmarksRepository = bookmarksRepository;
        this.suppliersRepository = suppliersRepository;
        this.bookmarkLock = bookmarkLock;
    }

    @Transactional(readOnly = true)
    public BookmarkListResponse list(Long userId) {
        List<BookmarkListResponse.Item> items = bookmarksRepository.findByUserIdNewestFirst(userId).stream()
                .map(b -> new BookmarkListResponse.Item(
                        String.valueOf(b.getSupplierId()), b.getSupplier().getName(), b.getBookmarkedAt()))
                .toList();
        return new BookmarkListResponse(items.size(), BOOKMARK_LIMIT, items);
    }

    @Transactional
    public BookmarkResponse add(Long userId, Long supplierId) {
        Suppliers supplier = suppliersRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));
        if (!bookmarkLock.tryLock(userId)) {
            throw new BookmarkTooFastException();
        }
        SupplierBookmarks existing = bookmarksRepository
                .findById(new SupplierBookmarkId(userId, supplierId)).orElse(null);
        long count = bookmarksRepository.countByUserId(userId);
        if (existing != null) {
            return new BookmarkResponse(String.valueOf(supplierId), true, existing.getBookmarkedAt(), (int) count);
        }
        if (count >= BOOKMARK_LIMIT) {
            throw new BookmarkLimitException(BOOKMARK_LIMIT);
        }
        SupplierBookmarks saved;
        try {
            saved = bookmarksRepository.saveAndFlush(new SupplierBookmarks(userId, supplier));
        } catch (DataIntegrityViolationException e) {
            // the supplier was deleted after the lookup above
            throw new SupplierNotFoundException(supplierId);
        }
        return new BookmarkResponse(String.valueOf(supplierId), true, saved.getBookmarkedAt(), (int) count + 1);
    }

    @Transactional
    public void remove(Long userId, Long supplierId) {
        bookmarksRepository.deleteByUserIdAndSupplierId(userId, supplierId);
    }

    // N8.5 / D12: every bookmark of the given (purged) accounts. Idempotent.
    @Transactional
    public int purgeUsers(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return 0;
        }
        return bookmarksRepository.deleteByUserIdIn(userIds);
    }
}
