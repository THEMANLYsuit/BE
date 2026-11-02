package com.themanly.site;

import com.themanly.common.error.DomainRuleException;
import com.themanly.media.MediaAsset;
import com.themanly.media.MediaAssetRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteImageService {

    private static final Logger log = LoggerFactory.getLogger(SiteImageService.class);
    private final SiteImageRepository images;
    private final MediaAssetRepository media;
    private final ApplicationEventPublisher events;
    public SiteImageService(SiteImageRepository images, MediaAssetRepository media, ApplicationEventPublisher events) {
        this.images = images;
        this.media = media;
        this.events = events;
    }
   @Transactional(readOnly = true)
    public List<SiteImage> all() {return images.findAllByOrderBySlotAsc();}

    @Transactional
    public SiteImage put(SiteImage.Slot slot, UUID mediaId, String alt) {
        MediaAsset asset = media.findById(mediaId).orElseThrow(() -> DomainRuleException.badRequest("UNKNOWN_MEDIA", "올린 사진을 찾지 못했습니다. 다시 올려 주세요."));
        SiteImage image = images.findById(slot).orElse(null);
        if (image == null) {image = images.save(new SiteImage(slot, asset, alt));
        } else {image.change(asset, alt);}
        log.info("메인 사진 걸기 slot={} mediaId={}", slot, mediaId);
        events.publishEvent(new FrontRebuildTrigger.SiteChanged("메인 사진"));
        return image;
    }

    @Transactional
    public void clear(SiteImage.Slot slot) {
        if (!images.existsById(slot)) {return;}
        images.deleteById(slot);
        log.info("메인 사진 비우기 slot={}", slot);
        events.publishEvent(new FrontRebuildTrigger.SiteChanged("메인 사진"));
    }
}