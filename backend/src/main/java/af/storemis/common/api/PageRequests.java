package af.storemis.common.api;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds safe Pageable objects: bounded page size and an allow-list of sortable properties. */
public final class PageRequests {

    public static final int MAX_SIZE = 200;

    private PageRequests() {}

    public static Pageable of(int page, int size, String sort, Set<String> allowedSort, Sort defaultSort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int safePage = Math.max(page, 0);
        Sort s = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = parts[0].trim();
            if (allowedSort.contains(property)) {
                Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
                s = Sort.by(dir, property);
            }
        }
        return PageRequest.of(safePage, safeSize, s);
    }
}
