package af.storemis.numbering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_sequences")
public class DocumentSequence {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocumentType docType;

    @Column(name = "prefix", nullable = false)
    private String prefix;

    @Column(name = "yearly_reset", nullable = false)
    private boolean yearlyReset;

    @Column(name = "current_year", nullable = false)
    private int currentYear;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    @Column(name = "padding", nullable = false)
    private short padding;

    public DocumentType getDocType() {
        return docType;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean isYearlyReset() {
        return yearlyReset;
    }

    public long getNextNumber() {
        return nextNumber;
    }

    public short getPadding() {
        return padding;
    }

    /** What the next number will look like, without taking it. */
    public String preview(int year) {
        long n = yearlyReset && year != currentYear ? 1 : nextNumber;
        String digits = String.format("%0" + padding + "d", n);
        return yearlyReset ? prefix + "-" + year + "-" + digits : prefix + "-" + digits;
    }

    /** Returns the next number (e.g. INV-2026-000123) and advances the counter. Caller must hold the row lock. */
    String take(int year) {
        if (yearlyReset && year != currentYear) {
            currentYear = year;
            nextNumber = 1;
        }
        long n = nextNumber++;
        String digits = String.format("%0" + padding + "d", n);
        return yearlyReset ? prefix + "-" + currentYear + "-" + digits : prefix + "-" + digits;
    }
}
