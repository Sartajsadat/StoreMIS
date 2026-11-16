package af.storemis.files.controller;

import af.storemis.common.exception.NotFoundException;
import af.storemis.files.service.FileStorageService;
import af.storemis.files.service.FileStorageService.Area;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }

    /** Public files (the business logo). File names are random UUIDs, so they can be cached for a long time. */
    @GetMapping("/public/{name}")
    public ResponseEntity<Resource> publicFile(@PathVariable String name) {
        return serve(Area.PUBLIC, name);
    }

    /**
     * Product images. Served without a token so plain {@code <img>} tags work; names are random UUIDs and the
     * images are not sensitive.
     */
    @GetMapping("/products/{name}")
    public ResponseEntity<Resource> productImage(@PathVariable String name) {
        return serve(Area.PRODUCTS, name);
    }

    private ResponseEntity<Resource> serve(Area area, String name) {
        Path p = storage.resolve(area, name).orElseThrow(() -> new NotFoundException("File", name));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(MediaType.parseMediaType(storage.contentType(p))).header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(p));
    }
}
