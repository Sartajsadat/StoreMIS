package af.storemis.files.service;

import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploads on the server's file volume, outside the web root.
 * The content type is detected from the bytes (not the file name), files get random names, and paths can never escape the base folder.
 */
@Service
public class FileStorageService {

    public enum Area {
        PUBLIC("public"),   // business logo — shown on the login screen
        PRODUCTS("products"),
        ATTACHMENTS("attachments");

        final String folder;

        Area(String folder) {
            this.folder = folder;
        }
    }

    public static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    public static final Set<String> DOCUMENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");
    private static final Map<String, String> EXTENSIONS = Map.of("image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "application/pdf", "pdf");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path baseDir;
    private final Tika tika = new Tika();

    public FileStorageService(AppProperties properties) {
        this.baseDir = Path.of(properties.files().dir()).toAbsolutePath().normalize();
    }

    /** @return the stored file name (random), to be saved on the owning record */
    public String store(Area area, MultipartFile file, Set<String> allowedTypes) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        if (file.getSize() > MAX_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE, Map.of("maxMb", 5));
        try (InputStream in = file.getInputStream()) {
            String detected = tika.detect(in);
            if (!allowedTypes.contains(detected)) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
            }
            String name = UUID.randomUUID() + "." + EXTENSIONS.get(detected);
            Path dir = baseDir.resolve(area.folder);
            Files.createDirectories(dir);
            try (InputStream data = file.getInputStream()) {
                Files.copy(data, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Path> resolve(Area area, String name) {
        if (name == null || !name.matches("^[0-9a-fA-F-]{36}\\.(png|jpg|webp|pdf)$")) return Optional.empty();
        Path dir = baseDir.resolve(area.folder).normalize();
        Path p = dir.resolve(name).normalize();
        return p.startsWith(dir) && Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    public void delete(Area area, String name) {
        resolve(area, name).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // An orphaned file is harmless; it is no longer referenced.
            }
        });
    }

    public String contentType(Path p) {
        try {
            return tika.detect(p);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
