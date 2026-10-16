package com.themanly.media;

import com.themanly.common.error.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MediaStorage {
    private final Path root;
    public MediaStorage(@Value("${app.media.storage-dir:./media-store}") String dir) {this.root = Path.of(dir).toAbsolutePath().normalize();}
    public String store(InputStream bytes, ImageFormat format) throws IOException {
       String key = LocalDate.now() + "/" + UUID.randomUUID() + format.extension();
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(bytes, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }
    public Path read(String key) {Path path = resolve(key);
        if (!Files.isRegularFile(path)) {throw new ResourceNotFoundException("미디어 파일 없음 key=" + key);}return path;}
   private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {throw new ResourceNotFoundException("저장 경로를 벗어나는 키 key=" + key);}return path;}
}