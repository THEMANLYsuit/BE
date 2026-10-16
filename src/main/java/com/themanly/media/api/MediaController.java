package com.themanly.media.api;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.media.MediaAsset;
import com.themanly.media.MediaAssetRepository;
import com.themanly.media.MediaService;
import com.themanly.media.MediaStorage;
import com.themanly.media.MediaUrls;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
    private final MediaService media;
    private final MediaAssetRepository assets;
    private final MediaStorage storage;
    private final MediaUrls urls;
    public MediaController(MediaService media, MediaAssetRepository assets, MediaStorage storage, MediaUrls urls){
        this.media = media;
        this.assets = assets;
        this.storage = storage;
        this.urls = urls;
    }
    @PostMapping("/api/admin/media")
    public Uploaded upload(@RequestParam("file") MultipartFile file) throws IOException{
        MediaAsset a = media.upload(file);
        return new Uploaded(a.getId(), urls.urlFor(a), a.getOriginalFilename(), a.getByteSize(), a.getWidth(), a.getHeight());}
    public record Uploaded(UUID id, String url, String filename, long byteSize, Integer width, Integer height){}
    @GetMapping("/media/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) throws IOException{
        String key = keyOf(request.getRequestURI());
        MediaAsset asset = assets.findByStorageKey(key).orElseThrow(() -> new ResourceNotFoundException("미디어 없음 key=" + key));
        Path path = storage.read(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .contentLength(Files.size(path))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }
    private static String keyOf(String uri){
        int at = uri.indexOf("/media/");
        if(at < 0){
            throw new ResourceNotFoundException("미디어 경로 아님 uri=" + uri);
        }
        return URLDecoder.decode(uri.substring(at + "/media/".length()),StandardCharsets.UTF_8);
    }
}
