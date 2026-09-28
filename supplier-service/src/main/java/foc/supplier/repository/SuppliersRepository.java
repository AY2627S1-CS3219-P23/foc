package foc.supplier.repository;

import foc.supplier.model.Suppliers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuppliersRepository extends JpaRepository<Suppliers, Long> {
    // Basic CRUD operations (save, findAll, findById, delete) are automatically included!
}
