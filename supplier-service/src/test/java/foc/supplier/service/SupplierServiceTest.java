/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-28 (PR #134 review, LeongWZ).
 * Scope: unit tests for listSuppliers's sort allow-list — an
 * unrecognized `sort` property must throw InvalidSortException before
 * ever reaching the repository, instead of surfacing as an unhandled
 * PropertyReferenceException/500. Tests use Mockito to isolate the
 * service from the database, following ProfileServiceTest's pattern
 * (user-service).
 * Reviewed by: [pending]
 */
package foc.supplier.service;

import foc.supplier.exception.InvalidSortException;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierCategoriesRepository;
import foc.supplier.repository.SuppliersRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SuppliersRepository suppliersRepository;

    @Mock
    private SupplierCategoriesRepository supplierCategoriesRepository;

    @InjectMocks
    private SupplierService supplierService;

    @Test
    @DisplayName("Should reject a sort property that isn't on the allow-list, before querying the repository")
    void listSuppliers_rejectsUnrecognizedSortProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("location").ascending());

        assertThatThrownBy(() -> supplierService.listSuppliers(null, null, null, null, pageable))
                .isInstanceOf(InvalidSortException.class);

        verify(suppliersRepository, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject the frontend's 'distance' sentinel if it ever reaches Sort directly")
    void listSuppliers_rejectsDistanceAsASortProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("distance").ascending());

        assertThatThrownBy(() -> supplierService.listSuppliers(null, null, null, null, pageable))
                .isInstanceOf(InvalidSortException.class);
    }

    @Test
    @DisplayName("Should accept an allow-listed sort property")
    void listSuppliers_acceptsAllowedSortProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").ascending());
        lenient().when(suppliersRepository.search(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<Suppliers>(List.of()));
        lenient().when(supplierCategoriesRepository.findBySupplier_IdIn(any())).thenReturn(List.of());

        assertThatCode(() -> supplierService.listSuppliers(null, null, null, null, pageable))
                .doesNotThrowAnyException();
    }
}
