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
    public record Content(@Valid @NotNull Hero hero, @Valid @NotNull @Size(min = 3, max = 3) List<Frame> film, @Valid @NotNull Body body, @Valid @NotNull @Size(min = 5, max = 5) List<Card> highlights, @NotBlank @Size(max = 200) String processIntro, @Valid @NotNull @Size(min = 6, max = 6) List<Step> process, @Valid @NotNull @Size(min = 5, max = 5) List<Card> closer, @NotBlank @Size(max = 300) String manifesto, @Valid @NotNull TwoLines booking, @Valid @NotNull Shop shop){}
    public record Hero(@NotBlank @Size(max = 40) String eyebrow, @NotBlank @Size(max = 30) String line1, @NotBlank @Size(max = 30) String line2, @NotBlank @Size(max = 200) String body){}
    public record Frame(@NotBlank @Size(max = 30) String eyebrow, @NotBlank @Size(max = 40) String title, @NotBlank @Size(max =120) String body){}
    public record Body(@NotBlank @Size(max = 30) String line1, @NotBlank @Size(max =30) String line2, @Valid @NotNull @Size(min = 3, max = 3) List<Card> promises){}
    public record Card(@NotBlank @Size(max = 40) String title, @NotBlank @Size(max = 200) String body) { }
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

