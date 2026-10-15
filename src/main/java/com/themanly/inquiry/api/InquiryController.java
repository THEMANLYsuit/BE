package com.themanly.inquiry.api;

import com.themanly.auth.MemberPrincipal;
import com.themanly.inquiry.Inquiry;
import com.themanly.inquiry.InquiryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {
    private final InquiryService inquiries;
    public InquiryController(InquiryService inquiries) {this.inquiries = inquiries;}
    @PostMapping
    public ResponseEntity<View> submit(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody Submit in) {if (in.website() != null && !in.website().isBlank()) {return ResponseEntity.status(HttpStatus.CREATED).build();}
        Inquiry saved = inquiries.submit(principal == null ? null : principal.getId(), in.name(), in.email(), in.phone(), in.topic(), in.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(View.from(saved));
    }
    @GetMapping("/mine")
    public List<View> mine(@AuthenticationPrincipal MemberPrincipal principal) {return inquiries.mine(principal.getId()).stream().map(View::from).toList();}
 public record Submit(@Size(max = 50) String name,
                         @Email @Size(max = 254) String email,
                         @Pattern(regexp = "^[0-9-]{9,20}$", message = "전화번호 형식이 올바르지 않습니다.") String phone,
                         @NotNull Inquiry.Topic topic,
                         @NotBlank @Size(min = 10, max = 3000, message = "문의 내용은 10자 이상 3000자 이하로 적어 주세요.") String message,
                         @Size(max = 200) String website) {
    }
    public record View(UUID id, Inquiry.Topic topic, String topicLabel, String message, Inquiry.Status status, String statusLabel, String answer, Instant answeredAt, Instant createdAt) {
        static View from(Inquiry i) {return new View(i.getId(), i.getTopic(), i.getTopic().label(), i.getMessage(), i.getStatus(), i.getStatus().label(), i.getAnswer(), i.getAnsweredAt(), i.getCreatedAt());}}
}