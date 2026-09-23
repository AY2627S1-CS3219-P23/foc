/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-23.
 * Scope: fixed a compile error (missing java.io.FileReader import), a
 * Maven build failure (CsvToBean has no setType(Class) method in
 * opencsv 5.9; rebuilt via CsvToBeanBuilder.withType(...) instead), and
 * a runtime failure in the containerized service (a relative
 * filesystem path can't resolve inside the runtime image, which only
 * contains the built jar — src/main/resources/csv/... is packaged as a
 * classpath resource, not a file on disk there; switched to loading it
 * via ClassPathResource). No design changes.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.supplier.seed;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

import foc.supplier.model.Suppliers;
import foc.supplier.repository.SuppliersRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class SuppliersSeeder implements CommandLineRunner {
    private final SuppliersRepository suppliersRepository;

    public SuppliersSeeder(SuppliersRepository suppliersRepository) {
        this.suppliersRepository = suppliersRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (suppliersRepository.count() == 0) {
            ClassPathResource csvResource = new ClassPathResource("csv/supplier-seed-data.csv");

            try (InputStreamReader reader = new InputStreamReader(csvResource.getInputStream(), StandardCharsets.UTF_8)) {
                CsvToBean<Suppliers> csvToBean = new CsvToBeanBuilder<Suppliers>(reader)
                        .withType(Suppliers.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .build();

                for (Suppliers supplier : csvToBean) {
                    suppliersRepository.save(supplier);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        }
    }
}