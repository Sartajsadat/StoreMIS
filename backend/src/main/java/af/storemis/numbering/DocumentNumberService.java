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

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        DocumentSequence seq = repository.lockByType(type).orElseThrow(() -> new IllegalStateException("No sequence for " + type));
        return seq.take(clock.today().getYear());
    }
}
