/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-28 (PR #134 review, LeongWZ).
 * Scope: unit tests for listSuppliers's sort allow-list — an
 * unrecognized `sort` property must throw InvalidSortException before
 * ever reaching the repository, instead of surfacing as an unhandled
 * PropertyReferenceException/500. Tests use Mockito to isolate the
 * service from the database, following ProfileServiceTest's pattern
 * (user-service).
 * 2026-09-28: added tests for normalizeImageUrl (via listSuppliers) —
 * the seed CSV's ImageURL column points at GitHub's blob (file-viewer)
 * URL, which doesn't serve raw image bytes; SupplierService rewrites it
 * to the raw.githubusercontent.com equivalent at the API response
 * boundary, since the CSV itself (course-provided data) can't be
 * edited.
 * 2026-09-29, issue #104: added unit tests for createSupplier/
 * updateSupplier/deleteSupplier — location merging (the single
 * request field replaces building+locationDescription, not appended to
 * the old building), category reconciliation (old rows deleted before
 * the new set is saved), not-found on update/delete, and the FK-safe
 * delete order (categories before the supplier row).
 * Reviewed by: [pending]
 */
package foc.supplier.service;

import foc.supplier.dto.SupplierRequest;
import foc.supplier.exception.InvalidSortException;
import foc.supplier.exception.SupplierNotFoundException;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierCategoriesRepository;
import foc.supplier.repository.SuppliersRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    @DisplayName("Should rewrite a GitHub blob (file-viewer) image URL to its raw-content equivalent")
    void listSuppliers_normalizesGithubBlobImageUrl() {
        Suppliers supplier = supplierWithImageUrl(
                "https://github.com/CS3219-AY2627S1/FoC-Template/blob/main/data/images/ANNA.jpeg");
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").ascending());
        when(suppliersRepository.search(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(supplier)));
        when(supplierCategoriesRepository.findBySupplier_IdIn(any())).thenReturn(List.of());

        var response = supplierService.listSuppliers(null, null, null, null, pageable);

        assertThat(response.getContent().get(0).getImageUrl())
                .isEqualTo("https://raw.githubusercontent.com/CS3219-AY2627S1/FoC-Template/main/data/images/ANNA.jpeg");
    }

    @Test
    @DisplayName("Should leave a non-GitHub-blob image URL unchanged")
    void listSuppliers_leavesOtherImageUrlsUnchanged() {
        Suppliers supplier = supplierWithImageUrl("https://example.com/anna.jpeg");
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").ascending());
        when(suppliersRepository.search(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(supplier)));
        when(supplierCategoriesRepository.findBySupplier_IdIn(any())).thenReturn(List.of());

        var response = supplierService.listSuppliers(null, null, null, null, pageable);

        assertThat(response.getContent().get(0).getImageUrl()).isEqualTo("https://example.com/anna.jpeg");
    }

    @Test
    @DisplayName("Should leave a missing image URL as-is (null/blank)")
    void listSuppliers_leavesMissingImageUrlAsIs() {
        Suppliers supplier = supplierWithImageUrl(null);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name").ascending());
        when(suppliersRepository.search(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(supplier)));
        when(supplierCategoriesRepository.findBySupplier_IdIn(any())).thenReturn(List.of());

        var response = supplierService.listSuppliers(null, null, null, null, pageable);

        assertThat(response.getContent().get(0).getImageUrl()).isNull();
    }

    @Test
    @DisplayName("createSupplier should save the supplier and its categories")
    void createSupplier_savesSupplierAndCategories() {
        when(suppliersRepository.save(any())).thenAnswer(invocation -> {
            Suppliers s = invocation.getArgument(0);
            s.setId(7L);
            return s;
        });
        SupplierRequest request = new SupplierRequest("Cafe", "Building A, Level 2",
                List.of("Food", "Coffee"), "09:00", "18:00", "desc", 1.3, 103.8, null);

        var response = supplierService.createSupplier(request);

        assertThat(response.getId()).isEqualTo("7");
        assertThat(response.getName()).isEqualTo("Cafe");
        assertThat(response.getCategories()).containsExactly("Food", "Coffee");
        verify(supplierCategoriesRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("createSupplier should store the single location field as locationDescription, with building cleared")
    void createSupplier_storesLocationWithoutBuilding() {
        ArgumentCaptor<Suppliers> captor = ArgumentCaptor.forClass(Suppliers.class);
        when(suppliersRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        SupplierRequest request = new SupplierRequest("Cafe", "Building A, Level 2",
                List.of(), "09:00", "18:00", null, null, null, null);

        supplierService.createSupplier(request);

        assertThat(captor.getValue().getBuilding()).isNull();
        assertThat(captor.getValue().getLocationDescription()).isEqualTo("Building A, Level 2");
    }

    @Test
    @DisplayName("updateSupplier should replace an existing supplier's categories, not merge with the old set")
    void updateSupplier_reconcilesCategories() {
        Suppliers existing = new Suppliers();
        existing.setId(7L);
        existing.setBuilding("Old Building");
        when(suppliersRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(suppliersRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SupplierRequest request = new SupplierRequest("Cafe", "New Location",
                List.of("Shopping"), "09:00", "18:00", null, null, null, null);

        supplierService.updateSupplier(7L, request);

        InOrder order = inOrder(supplierCategoriesRepository);
        order.verify(supplierCategoriesRepository).deleteBySupplierId(7L);
        order.verify(supplierCategoriesRepository).save(any());
    }

    @Test
    @DisplayName("updateSupplier should throw when the id doesn't exist")
    void updateSupplier_throwsWhenNotFound() {
        when(suppliersRepository.findById(99L)).thenReturn(Optional.empty());
        SupplierRequest request = new SupplierRequest("Cafe", "Loc", List.of(), "09:00", "18:00", null, null, null,
                null);

        assertThatThrownBy(() -> supplierService.updateSupplier(99L, request))
                .isInstanceOf(SupplierNotFoundException.class);

        verify(suppliersRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteSupplier should delete categories before the supplier row (FK)")
    void deleteSupplier_deletesCategoriesFirst() {
        when(suppliersRepository.existsById(7L)).thenReturn(true);

        supplierService.deleteSupplier(7L);

        InOrder order = inOrder(supplierCategoriesRepository, suppliersRepository);
        order.verify(supplierCategoriesRepository).deleteBySupplierId(7L);
        order.verify(suppliersRepository).deleteById(7L);
    }

    @Test
    @DisplayName("deleteSupplier should throw when the id doesn't exist, without deleting anything")
    void deleteSupplier_throwsWhenNotFound() {
        when(suppliersRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> supplierService.deleteSupplier(99L))
                .isInstanceOf(SupplierNotFoundException.class);

        verify(supplierCategoriesRepository, never()).deleteBySupplierId(any());
        verify(suppliersRepository, never()).deleteById(any());
    }

    private static Suppliers supplierWithImageUrl(String imageUrl) {
        Suppliers supplier = new Suppliers();
        supplier.setId(1L);
        supplier.setName("Anna's x Soup Union");
        supplier.setCategory("Food");
        supplier.setStartingTime(LocalTime.of(9, 0));
        supplier.setClosingTime(LocalTime.of(18, 0));
        supplier.setImageURL(imageUrl);
        return supplier;
    }
}
