package com.themanly.site.api;

import com.themanly.media.MediaUrls;
import com.themanly.site.SiteImage;
import com.themanly.site.SiteImageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteImageController {

    private final SiteImageService images;
    private final MediaUrls urls;
    public SiteImageController(SiteImageService images, MediaUrls urls) {
        this.images = images;
        this.urls = urls;
    }

    @GetMapping("/api/site-images")
    public List<View> all() {return images.all().stream().map(this::view).toList();}
    @PutMapping("/api/admin/site-images/{slot}")
    public View put(@PathVariable SiteImage.Slot slot, @Valid @RequestBody Input in) {return view(images.put(slot, in.mediaId(), in.alt()));}
    @DeleteMapping("/api/admin/site-images/{slot}")
    public ResponseEntity<Void> clear(@PathVariable SiteImage.Slot slot) {
        images.clear(slot);
        return ResponseEntity.noContent().build();
    }
    private View view(SiteImage i) {return new View(i.getSlot(), i.getMedia().getId(), urls.urlFor(i.getMedia()), i.getAlt(), i.getMedia().getWidth(), i.getMedia().getHeight());}
    public record Input(@NotNull UUID mediaId, @NotBlank @Size(max = 200) String alt) { }
    public record View(SiteImage.Slot slot, UUID mediaId, String url, String alt, Integer width, Integer height) { }
}