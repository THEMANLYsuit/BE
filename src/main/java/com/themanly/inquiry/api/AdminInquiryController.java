package com.themanly.inquiry.api;

import com.themanly.inquiry.Inquiry;
import com.themanly.inquiry.InquiryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {
    private final InquiryService inquiries;
    public AdminInquiryController(InquiryService inquiries) {this.inquiries = inquiries;}
    @GetMapping
    public PageView list(@RequestParam(required = false) Inquiry.Status status, @RequestParam(defaultValue = "0") int page) {
        Page<Inquiry> found = inquiries.list(status, page);
        return new PageView(found.getContent().stream().map(View::from).toList(), found.getNumber(), found.getTotalPages(), found.getTotalElements(), inquiries.openCount());}
    @GetMapping("/{id}")
    public View get(@PathVariable UUID id) {return View.from(inquiries.get(id));}
    @PostMapping("/{id}/answer")
    public View answer(@PathVariable UUID id, @Valid @RequestBody Answer in) {return View.from(inquiries.answer(id, in.answer()));}
    @PostMapping("/{id}/close")
    public View close(@PathVariable UUID id) {return View.from(inquiries.close(id));}
    public record Answer(@NotBlank @Size(max = 3000) String answer) { }
    public record View(UUID id, UUID memberId, String name, String email, String phone, Inquiry.Topic topic, String topicLabel, String message, Inquiry.Status status, String statusLabel, String answer, Instant answeredAt, Instant createdAt) {
        static View from(Inquiry i) {return new View(i.getId(), i.getMemberId(), i.getName(), i.getEmail(), i.getPhone(), i.getTopic(), i.getTopic().label(), i.getMessage(), i.getStatus(), i.getStatus().label(), i.getAnswer(), i.getAnsweredAt(), i.getCreatedAt());
        }}public record PageView(List<View> items, int page, int totalPages, long total, long openCount) { }
}