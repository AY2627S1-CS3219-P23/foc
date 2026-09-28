package foc.supplier.repository;

import foc.supplier.model.SupplierCategories;
import foc.supplier.model.SupplierCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierCategoriesRepository extends JpaRepository<SupplierCategories, SupplierCategoryId> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!
}
