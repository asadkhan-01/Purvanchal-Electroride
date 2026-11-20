package in.purvanchal.electroride.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Gapless invoice/receipt numbers per branch and Indian financial year (April-March),
 * e.g. INV/VAR/2026-27/0001. The upsert row-locks the counter, so concurrent requests
 * serialize, and a rolled-back document also rolls back its number.
 */
@Component
public class DocumentNumbers {
    private final JdbcTemplate jdbc;

    public DocumentNumbers(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String docType, String branch, LocalDate date) {
        String fy = financialYear(date);
        Integer n = jdbc.queryForObject("""
            INSERT INTO document_sequences (doc_type, branch, financial_year, last_number)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (doc_type, branch, financial_year)
            DO UPDATE SET last_number = document_sequences.last_number + 1
            RETURNING last_number""", Integer.class, docType, branch, fy);
        return String.format(Locale.ROOT, "%s/%s/%s/%04d", docType, branchCode(branch), fy, n);
    }

    static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return start + "-" + String.format(Locale.ROOT, "%02d", (start + 1) % 100);
    }

    static String branchCode(String branch) {
        String letters = branch.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        return letters.length() >= 3 ? letters.substring(0, 3) : (letters + "XXX").substring(0, 3);
    }
}
