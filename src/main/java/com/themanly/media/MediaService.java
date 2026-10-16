package com.themanly.media;

import com.themanly.common.error.DomainRuleException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 — 원단 · 스타일 사진 (관리자). 여기가 신뢰 경계다: 선언된 타입을 믿지 않고 앞머리 바이트로 판별하며,
 * 모르면 거부한다. 압축 폭탄(작은 파일 · 수억 픽셀)은 헤더로 크기를 재서 막는다.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    /** 한 장 상한. application.yml 의 max-file-size 와 같은 값 — 서블릿이 먼저 끊고, 여기서 한 번 더. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** 픽셀 수 상한 ≈ 10000 × 10000. 원단 사진이 이보다 클 이유가 없다. */
    private static final long MAX_PIXELS = 100_000_000L;

    private final MediaAssetRepository assets;
    private final MediaStorage storage;

    public MediaService(MediaAssetRepository assets, MediaStorage storage) {
        this.assets = assets;
        this.storage = storage;
    }

    @Transactional
    public MediaAsset upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid("이미지 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("이미지가 너무 큽니다. " + (MAX_BYTES / 1024 / 1024) + "MB 이하로 올려 주세요.");
        }
        // 판별과 저장으로 두 번 읽어야 해서 메모리에 올린다. 10MB 상한이 이미 걸려 있다
        byte[] bytes = file.getBytes();
        ImageFormat format = ImageFormat.sniff(bytes);
        if (format == null) {
            // 무엇이 틀렸는지 자세히 말하지 않는다 — 우회 시도에 힌트가 된다
            log.info("받지 않는 이미지 형식 거부 size={}", bytes.length);
            throw invalid("JPG · PNG · WebP · GIF · AVIF 이미지만 올릴 수 있습니다. 아이폰 사진(HEIC)은 JPG 로 바꿔 올려 주세요.");
        }
        int[] size = measure(bytes);
        String key = storage.store(new ByteArrayInputStream(bytes), format);
        MediaAsset asset = assets.save(new MediaAsset(displayName(file.getOriginalFilename()), format, bytes.length,
                size == null ? null : size[0], size == null ? null : size[1], key));
        log.info("이미지 업로드 mediaId={} format={} bytes={}", asset.getId(), format, bytes.length);
        return asset;
    }

    /** 가로 · 세로. 헤더만 읽어 픽셀 수를 먼저 본다. 읽을 리더가 없으면(WebP · AVIF — JDK 기본에 없다) 크기를 모를 뿐 통과. */
    private int[] measure(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes); ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if ((long) w * h > MAX_PIXELS) {
                    throw invalid("이미지 해상도가 너무 큽니다.");
                }
                return new int[] {w, h};
            } finally {
                reader.dispose();
            }
        } catch (IIOException e) {
            throw invalid("이미지를 읽을 수 없습니다. 다른 파일로 올려 주세요.");
        }
    }

    /** 표시용 이름 — 경로 구분자 · 제어문자를 걷는다. 경로에는 쓰지 않지만 관리자 화면에 그대로 그려진다. */
    private static String displayName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "image";
        }
        String cleaned = raw.replaceAll("[\\p{Cntrl}/\\\\]", "").trim();
        if (cleaned.isBlank()) {
            return "image";
        }
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static DomainRuleException invalid(String message) {
        return DomainRuleException.badRequest("INVALID_IMAGE", message);
    }
}