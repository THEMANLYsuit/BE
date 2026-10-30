package com.themanly.site;

import com.themanly.common.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteBusinessService {
    private static final Logger log = LoggerFactory.getLogger(SiteBusinessService.class);
    private final SiteBusinessRepository business;
    private final ApplicationEventPublisher events;
    public SiteBusinessService(SiteBusinessRepository business, ApplicationEventPublisher events){
        this.business = business;
        this.events = events;
    }
    @Transactional(readOnly = true)
    public SiteBusiness get(){
        return business.findById(SiteBusiness.SINGLETON_ID).orElseThrow(() -> new ResourceNotFoundException("사업자 정보 없음(v13 이 돌지 않음)"));
    }
    @Transactional
    public SiteBusiness save(SiteBusiness.Details details){
        SiteBusiness b = get();
        b.change(details);
        business.flush();
        log.info("푸터 사업자 정보 저장");
        events.publishEvent(new FrontRebuildTrigger.SiteChanged("사업자정보"));
        return b;
    }
}
