package com.themanly.film.api;

import com.themanly.auth.MemberPrincipal;
import com.themanly.film.Film;
import com.themanly.film.FilmService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
public class FilmController {

    private final FilmService films;

    public FilmController(FilmService films) {
        this.films = films;
    }

    @GetMapping("/api/film")
    public ResponseEntity<FilmService.Sequence> current() {
        Film f = films.current();
        return f == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(films.sequenceOf(f));
    }

    @GetMapping("/film/{id}/{name}")
    public ResponseEntity<Resource> frame(@PathVariable UUID id, @PathVariable String name) throws IOException {
        Path path = films.frame(id, name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .contentLength(Files.size(path))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }

    @PostMapping("/api/admin/film")
    public FilmView upload(@AuthenticationPrincipal MemberPrincipal admin, @RequestParam("file") MultipartFile file)
            throws IOException {
        return FilmView.of(films.upload(file, admin.getId()), films);
    }

    @GetMapping("/api/admin/film")
    public AdminView admin() {
        Film current = films.current();
        return new AdminView(current == null ? null : FilmView.of(current, films),
                films.recent().stream().map(f -> FilmView.of(f, films)).toList());
    }

    public record FilmView(UUID id, int frames, int fps, String originalFilename, long byteSize, boolean active,
                           Instant createdAt, FilmService.Sequence sequence) {
        static FilmView of(Film f, FilmService films) {
            return new FilmView(f.getId(), f.getFrameCount(), f.getFps(), f.getOriginalFilename(), f.getByteSize(),
                    f.isActive(), f.getCreatedAt(), films.sequenceOf(f));
        }
    }

    public record AdminView(FilmView current, List<FilmView> recent) {
    }
}