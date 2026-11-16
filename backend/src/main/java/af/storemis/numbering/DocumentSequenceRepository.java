package af.storemis.numbering;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentType> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.docType = :type")
    Optional<DocumentSequence> lockByType(DocumentType type);
}
