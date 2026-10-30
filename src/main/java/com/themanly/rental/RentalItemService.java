package com.themanly.rental;
import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.site.FrontRebuildTrigger;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalItemService {
    private static final Logger log = LoggerFactory.getLogger(RentalItemService.class);
    private static final EnumSet<RentalItem.Status> VISIBLE = EnumSet.of(RentalItem.Status.PUBLISHED, RentalItem.Status.UNAVAILABLE);
    private final RentalItemRepository items;
    private final ApplicationEventPublisher events;
    public RentalItemService(RentalItemRepository items, ApplicationEventPublisher events){
        this.items = items;
        this.events = events;
    }
    private void rebuild(String reason){events.publishEvent(new FrontRebuildTrigger.SiteChanged(reason));}
    //손님
    @Transactional(readOnly = true)
    public List<RentalItem> visibleItems(){return items.findByStatusInOrderBySortOrderAscNameAsc(VISIBLE);}
    @Transactional(readOnly = true)
    public RentalItem visibleItem(UUID id){return items.findById(id).filter(RentalItem::isVisible).orElseThrow(() -> new ResourceNotFoundException("대여 옷 없음 id = " + id));}
    @Transactional(readOnly = true)
    public List<RentalItem> allItems(){return items.findAllByOrderBySortOrderAscNameAsc();}
    @Transactional(readOnly = true)
    public RentalItem item(UUID id){return items.findById(id).orElseThrow(()-> new ResourceNotFoundException("대여 옷 없음 id = " + id));}
    @Transactional
    public RentalItem create(RentalItem.Details details){
        RentalItem item = new RentalItem(UUID.randomUUID(), items.maxSortOrder() + 10);
        item.change(details);
        items.save(item);
        log.info("대여 옷 등록 id = {}", item.getId());
        return item;
    }
    @Transactional
    public RentalItem update(UUID id, RentalItem.Details details){
        RentalItem item = item(id);
        item.change(details);
        log.info("대여 옷 수정 id={}", id);
        if(item.isVisible()){rebuild("대여 옷 수정");}
        return item;
    }
    @Transactional
    public RentalItem changeStatus(UUID id, RentalItem.Status status){
        RentalItem item = item(id);
        if(status != RentalItem.Status.DRAFT && item.getName().isBlank()){throw new DomainRuleException("NOT_READY", "이름이 없는 옷은 공개할 수 없습니다.");
    }
        boolean wasVisible = item.isVisible();
        item.changeStatus(status);
        log.info("대여 옷 상태 id={} status={}", id, status);
        if (wasVisible || item.isVisible()) {rebuild("대여 옷 상태");}
        return item;
    }
}
