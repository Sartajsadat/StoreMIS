package af.storemis.numbering;

import af.storemis.common.time.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues document numbers inside the posting transaction. The sequence row is locked FOR UPDATE, so numbers are
 * unique and — because a failed posting rolls the counter back too — gap-free for confirmed documents.
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository repository;
    private final BusinessClock clock;

    public DocumentNumberService(DocumentSequenceRepository repository, BusinessClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Rows of the numbering settings page. */
    public record SequenceInfo(DocumentType docType, String prefix, String nextNumberPreview) {}

    @Transactional(readOnly = true)
    public java.util.List<SequenceInfo> list() {
        int year = clock.today().getYear();
        return repository.findAll().stream().sorted(java.util.Comparator.comparing(s -> s.getDocType().ordinal()))
                .map(s -> new SequenceInfo(s.getDocType(), s.getPrefix(), s.preview(year))).toList();
    }

    /** Changes the prefix of future numbers. Existing documents keep the numbers they were given. */
    @Transactional
    public SequenceInfo updatePrefix(DocumentType type, String prefix) {
        DocumentSequence seq = repository.lockByType(type).orElseThrow();
        seq.setPrefix(prefix.trim().toUpperCase());
        return new SequenceInfo(seq.getDocType(), seq.getPrefix(), seq.preview(clock.today().getYear()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        DocumentSequence seq = repository.lockByType(type).orElseThrow(() -> new IllegalStateException("No sequence for " + type));
        return seq.take(clock.today().getYear());
    }
}
