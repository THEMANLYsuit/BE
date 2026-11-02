package com.themanly.film;

import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 메인 스크롤 필름 만들기 — 영상 → 사진 묶음 (V11). AI 가 아니라 ffmpeg(영상 변환 도구)로 1초에 {@code fps} 장씩 뽑는다.
 *
 * <p>왜 사진 묶음인가: 영상을 스크롤로 되감으면(currentTime) 브라우저가 매번 영상을 다시 풀어 끊긴다. 사진을 미리 받아 두고
 * 캔버스에 넘기면 매끄럽다 — 애플 제품 페이지도 같은 방식이다.
 *
 * <p>받는 영상은 MP4 · MOV(아이폰) · WebM. 앞머리 바이트로 확인하고, 실제로 풀 수 있는지는 ffmpeg 가 판단한다.
 * 앞 {@code max-seconds} 초까지만 쓴다(사진 수 = 그 초 × fps). 변환이 실패하면 만든 것을 지우고 아무것도 바꾸지 않는다.
 * 성공하면 새 필름이 메인에 걸리고(active), 지난 필름은 남는다.
 *
 * <p>2026-11-02 새 메인(디자이너 시안)은 첫 화면에 영상을 그대로 반복 재생한다 — 그래서 사진 묶음과 함께
 * 반복 재생용 영상({@value #LOOP}: H.264 · 소리 없음 · 가로 최대 1920 · 앞부분부터 내려받으며 재생)도 만든다.
 * 첫 장면 사진은 사진 묶음의 첫 장(f001.webp)이다.
 */
@Service
public class FilmService {

    private static final Logger log = LoggerFactory.getLogger(FilmService.class);

    /** 반복 재생용 영상 파일 이름 — 필름 폴더 안 */
    static final String LOOP = "loop.mp4";

    /** 영상 상한 — application.yml 의 max-file-size 와 같은 값 */
    public static final long MAX_BYTES = 60L * 1024 * 1024;

    private final FilmRepository films;
    private final Path root;
    private final String ffmpeg;
    private final int fps;
    private final int width;
    private final int maxSeconds;
    private final String apiBase;

    public FilmService(FilmRepository films,
                       @Value("${app.media.storage-dir:./media-store}") String storageDir,
                       @Value("${app.film.ffmpeg:ffmpeg}") String ffmpeg,
                       @Value("${app.film.fps:12}") int fps,
                       @Value("${app.film.width:1280}") int width,
                       @Value("${app.film.max-seconds:30}") int maxSeconds,
                       @Value("${app.api.base-url:http://localhost:8081}") String apiBase) {
        this.films = films;
        this.root = Path.of(storageDir).toAbsolutePath().normalize().resolve("film");
        this.ffmpeg = ffmpeg;
        this.fps = fps;
        this.width = width;
        this.maxSeconds = maxSeconds;
        this.apiBase = apiBase.replaceAll("/+$", "");
    }

    /**
     * 화면이 쓰는 모양 — 사진 주소는 {@code base + 001 + ext} 처럼 만든다(세 자리, 1부터).
     * {@code video} — 반복 재생용 영상, {@code poster} — 첫 장면 사진(새 메인 첫 화면).
     */
    public record Sequence(UUID id, int frames, int fps, String base, int pad, String ext, String video, String poster) {
    }

    public Sequence sequenceOf(Film f) {
        String dir = apiBase + "/film/" + f.getId() + "/";
        return new Sequence(f.getId(), f.getFrameCount(), f.getFps(), dir + "f", 3, ".webp", dir + LOOP, dir + "f001.webp");
    }

    @Transactional(readOnly = true)
    public Film current() {
        return films.findFirstByActiveTrue().orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Film> recent() {
        return films.findTop10ByOrderByCreatedAtDesc();
    }

    /** 올린 영상 → 사진 묶음 → 메인에 건다. 실패하면 400 VIDEO_INVALID(만든 것은 지운다). */
    @Transactional
    public Film upload(MultipartFile file, UUID adminId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid("영상 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("영상이 너무 큽니다. " + (MAX_BYTES / 1024 / 1024) + "MB 이하로 올려 주세요.");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(64);
        }
        if (!isVideo(head)) {
            throw invalid("MP4 · MOV · WebM 영상만 올릴 수 있습니다.");
        }

        UUID id = UUID.randomUUID();
        Path dir = root.resolve(id.toString());
        Files.createDirectories(dir);
        Path source = dir.resolve("source.tmp");
        try {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
            }
            int frames = extract(source, dir);
            encodeLoop(source, dir);
            Files.deleteIfExists(source);

            films.findFirstByActiveTrue().ifPresent(old -> {
                old.deactivate();
                films.flush(); // 활성은 한 줄뿐(uq_film_active) — 새 줄을 넣기 전에 먼저 내린다
            });
            Film film = new Film(id, frames, fps, displayName(file.getOriginalFilename()), file.getSize(), adminId);
            film.activate();
            films.save(film);
            log.info("필름 등록 filmId={} frames={} bytes={}", id, frames, file.getSize());
            return film;
        } catch (RuntimeException | IOException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    /** ffmpeg 로 사진을 뽑고 몇 장인지 돌려준다. 없으면 · 시간이 넘으면 · 0장이면 400. */
    private int extract(Path source, Path dir) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-v", "error", "-y", "-i", source.toString(),
                "-t", String.valueOf(maxSeconds),
                "-vf", "fps=" + fps + ",scale='min(" + width + ",iw)':-2",
                "-c:v", "libwebp", "-quality", "62", "-compression_level", "4",
                dir.resolve("f%03d.webp").toString()));
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(dir.resolve("ffmpeg.log").toFile()).start();
        } catch (IOException e) {
            log.error("ffmpeg 를 실행하지 못함 path={}", ffmpeg, e);
            throw new DomainRuleException("FILM_UNAVAILABLE", "서버에서 영상 변환 도구를 찾지 못했습니다. 관리자에게 알려 주세요.",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw invalid("영상 변환이 너무 오래 걸립니다. 더 짧거나 작은 영상으로 올려 주세요.");
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw invalid("영상 변환이 중단되었습니다. 다시 올려 주세요.");
        }
        int frames;
        try (Stream<Path> s = Files.list(dir)) {
            frames = (int) s.filter(f -> f.getFileName().toString().matches("^f\\d{3}\\.webp$")).count();
        }
        if (p.exitValue() != 0 || frames == 0) {
            log.info("필름 변환 실패 exit={} frames={}", p.exitValue(), frames);
            throw invalid("영상을 읽지 못했습니다. 다른 파일로 올려 주세요.");
        }
        Files.deleteIfExists(dir.resolve("ffmpeg.log"));
        return frames;
    }

    /**
     * 반복 재생용 영상을 만든다 — 소리는 뺀다(첫 화면은 소리 없이 자동 재생해야 브라우저가 막지 않는다).
     * faststart: 앞부분에 색인을 둬서 다 내려받기 전에 재생이 시작된다. 실패하면 400(만든 것은 upload 가 지운다).
     */
    private void encodeLoop(Path source, Path dir) throws IOException {
        run(List.of(ffmpeg, "-v", "error", "-y", "-i", source.toString(),
                "-t", String.valueOf(maxSeconds), "-an",
                "-vf", "scale='min(1920,iw)':-2",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "26", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                dir.resolve(LOOP).toString()), dir);
        if (!Files.isRegularFile(dir.resolve(LOOP)) || Files.size(dir.resolve(LOOP)) == 0) {
            throw invalid("영상을 읽지 못했습니다. 다른 파일로 올려 주세요.");
        }
        Files.deleteIfExists(dir.resolve("ffmpeg.log"));
    }

    /** ffmpeg 한 번 — 못 찾으면 503, 시간이 넘거나 실패하면 400. */
    private void run(List<String> cmd, Path dir) throws IOException {
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(dir.resolve("ffmpeg.log").toFile()).start();
        } catch (IOException e) {
            log.error("ffmpeg 를 실행하지 못함 path={}", ffmpeg, e);
            throw new DomainRuleException("FILM_UNAVAILABLE", "서버에서 영상 변환 도구를 찾지 못했습니다. 관리자에게 알려 주세요.",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw invalid("영상 변환이 너무 오래 걸립니다. 더 짧거나 작은 영상으로 올려 주세요.");
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw invalid("영상 변환이 중단되었습니다. 다시 올려 주세요.");
        }
        if (p.exitValue() != 0) {
            log.info("반복 영상 변환 실패 exit={}", p.exitValue());
            throw invalid("영상을 읽지 못했습니다. 다른 파일로 올려 주세요.");
        }
    }

    /** 필름 폴더의 파일 — 사진(f001.webp …)과 반복 영상(loop.mp4)만 내보낸다. */
    public Path frame(UUID id, String name) {
        if (!name.matches("^f\\d{3}\\.webp$") && !name.equals(LOOP)) {
            throw new ResourceNotFoundException("필름 사진 이름 아님 name=" + name);
        }
        Path path = root.resolve(id.toString()).resolve(name).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {throw new ResourceNotFoundException("필름 사진 없음 id=" + id + " name=" + name);}return path;
    }
    static boolean isVideo(byte[] head) {
        if (head.length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {return true;
        }return head.length >= 4 && (head[0] & 0xff) == 0x1A && (head[1] & 0xff) == 0x45 && (head[2] & 0xff) == 0xDF && (head[3] & 0xff) == 0xA3;
    }
    private static String displayName(String raw) {if (raw == null || raw.isBlank()) {return "video";}
        String cleaned = raw.replaceAll("[\\p{Cntrl}/\\\\]", "").trim();
        if (cleaned.isBlank()) {return "video";}return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }
    private static void deleteQuietly(Path dir) {try (Stream<Path> s = Files.walk(dir)) {s.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());} catch (IOException ignored) {}
    }
    private static DomainRuleException invalid(String message) {return DomainRuleException.badRequest("VIDEO_INVALID", message);}
}