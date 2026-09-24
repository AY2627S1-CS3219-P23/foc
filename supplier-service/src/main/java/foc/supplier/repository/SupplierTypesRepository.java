package foc.supplier.repository;

import foc.supplier.model.SupplierTypeId;
import foc.supplier.model.SupplierTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierTypesRepository extends JpaRepository<SupplierTypes, SupplierTypeId> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!
}
