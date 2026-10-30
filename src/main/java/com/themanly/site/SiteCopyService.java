package com.themanly.site;

import com.themanly.common.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SiteCopyService {
    private static final Logger log = LoggerFactory.getLogger(SiteCopyService.class);
    private final SiteCopyRepository copies;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    public SiteCopyService(SiteCopyRepository copies, ObjectMapper json, ApplicationEventPublisher events){
        this.copies = copies;
        this.json = json;
        this.events = events;
    }
    @Transactional(readOnly = true)
    public SiteCopy.Content get(){return json.readValue(row().getContent(), SiteCopy.Content.class);}
    @Transactional
    public SiteCopy.Content save(SiteCopy.Content content){
        row().changeContent(json.writeValueAsString(content));
        copies.flush();
        log.info("사이트 문구 저장");
        events.publishEvent(new FrontRebuildTrigger.SiteChanged("사이트 문구"));
        return content;
    }
    private SiteCopy row(){return copies.findById(SiteCopy.SINGLETON_ID).orElseThrow(() -> new ResourceNotFoundException("사이트 문구 없음(v14가 돌지 않음)"));}
}
