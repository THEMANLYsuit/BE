package com.themanly.site.api;

import com.themanly.site.SiteCopy;
import com.themanly.site.SiteCopyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteCopyController {
    private final SiteCopyService copies;
    public SiteCopyController(SiteCopyService copies){this.copies = copies;}
    @GetMapping("/api/site-copy")
    public SiteCopy.Content publicView(){return copies.get();}
    @GetMapping("/api/admin/site-copy")
    public SiteCopy.Content adminView(){return copies.get();}
    @PutMapping("/api/admin/site-copy")
    public SiteCopy.Content save(@Valid @RequestBody SiteCopy.Content in){return copies.save(in);}
}
