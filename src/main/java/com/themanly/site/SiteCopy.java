package com.themanly.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
@Entity
@Table(name = "site_copy")
public class SiteCopy {
    public static final short SINGLETON_ID = 1;
    // 2026-11-02 새 메인(V17): 예전 메인 문구(hero · film · body · highlights · closer · manifesto)를 걷고 story 를 넣었다
    public record Content(@Valid @NotNull Story story, @NotBlank @Size(max = 200) String processIntro, @Valid @NotNull @Size(min = 6, max = 6) List<Step> process, @Valid @NotNull TwoLines booking, @Valid @NotNull Shop shop){}
    /** 새 메인 — Various Body Types → Custom Fit 두 제목과 마지막 장면의 설명 글 */
    public record Story(@NotBlank @Size(max = 40) String bodiesTitle, @NotBlank @Size(max = 40) String fitTitle, @NotBlank @Size(max = 600) String fitBody){}
    public record Step(@NotBlank @Size(max = 20) String title, @NotBlank @Size(max = 30) String en, @NotBlank @Size(max = 200) String body, @NotBlank @Size(max = 20) String duration){}
    public record TwoLines(@NotBlank @Size(max = 30) String line1, @NotBlank @Size(max =30) String line2){}
    public record Shop(@NotBlank @Size(max = 40) String openDays, @NotBlank @Size(max= 40) String hours, @NotBlank @Size(max = 40) String closedDays){}
    @Id
    private Short id;
    @Column(nullable = false)
    private String content;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
    protected SiteCopy(){}
    public String getContent(){return content;}
    public void changeContent(String json){this.content = Objects.requireNonNull(json);}
    public Instant getUpdatedAt(){return updatedAt;}
}

