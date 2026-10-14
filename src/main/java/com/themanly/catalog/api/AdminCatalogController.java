package com.themanly.catalog.api;

import com.themanly.catalog.CatalogService;
import com.themanly.catalog.Fabric;
import com.themanly.catalog.StyleOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카탈로그 관리 (관리자). 길이 제한은 V3 의 CHECK 와 같다. */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final CatalogService catalog;

    public AdminCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    // ── 원단 ────────────────────────────────────────────

    @GetMapping("/fabrics")
    public List<FabricAdminView> fabrics() {
        return catalog.allFabrics().stream().map(FabricAdminView::from).toList();
    }

    @GetMapping("/fabrics/{id}")
    public FabricAdminView fabric(@PathVariable UUID id) {
        return FabricAdminView.from(catalog.fabric(id));
    }

    @PostMapping("/fabrics")
    public ResponseEntity<FabricAdminView> createFabric(@Valid @RequestBody FabricInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(FabricAdminView.from(catalog.createFabric(in.code(), in.details())));
    }

    @PutMapping("/fabrics/{id}")
    public FabricAdminView updateFabric(@PathVariable UUID id, @Valid @RequestBody FabricInput in) {
        return FabricAdminView.from(catalog.updateFabric(id, in.details()));
    }

    @PutMapping("/fabrics/{id}/status")
    public FabricAdminView changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusInput in) {
        return FabricAdminView.from(catalog.changeFabricStatus(id, in.status()));
    }

    // ── 스타일 선택지 ────────────────────────────────────

    @GetMapping("/style-options")
    public List<StyleOptionAdminView> styleOptions() {
        return catalog.allStyleOptions().stream().map(StyleOptionAdminView::from).toList();
    }

    @PostMapping("/style-options")
    public ResponseEntity<StyleOptionAdminView> createStyleOption(@Valid @RequestBody StyleOptionInput in) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StyleOptionAdminView.from(catalog.createStyleOption(in.category(), in.code(), in.details())));
    }

    @PutMapping("/style-options/{id}")
    public StyleOptionAdminView updateStyleOption(@PathVariable UUID id, @Valid @RequestBody StyleOptionInput in) {
        return StyleOptionAdminView.from(catalog.updateStyleOption(id, in.details()));
    }

    // ── 입력 ────────────────────────────────────────────

    /** 수정 때는 code 를 바꾸지 않는다(무시) — 원단 번호는 손님 화면 주소의 일부다. */
    public record FabricInput(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,40}$", message = "원단 번호는 영문 · 숫자 · 하이픈 2~40자") String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 80) String mill,
            @Size(max = 120) String composition,
            @Min(100) @Max(800) Short weightGsm,
            @Size(max = 40) String color,
            @Size(max = 40) String pattern,
            Fabric.Season season,
            @PositiveOrZero Long priceFromKrw,
            @Size(max = 2000) String description,
            @Size(max = 500) @Pattern(regexp = "^$|^(https://|/)\\S+$", message = "사진 주소는 https:// 또는 / 로 시작") String imageUrl) {

        Fabric.Details details() {
            return new Fabric.Details(name, mill, composition, weightGsm, color, pattern, season, priceFromKrw, description, imageUrl);
        }
    }

    public record StatusInput(@NotNull Fabric.Status status) {
    }

    public record StyleOptionInput(
            @NotNull StyleOption.Category category,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,40}$", message = "코드는 영문 · 숫자 · 하이픈 2~40자") String code,
            @NotBlank @Size(max = 60) String name,
            @Size(max = 500) String description,
            @Size(max = 500) @Pattern(regexp = "^$|^(https://|/)\\S+$", message = "사진 주소는 https:// 또는 / 로 시작") String imageUrl,
            boolean isDefault,
            @NotNull Boolean enabled) {

        StyleOption.Details details() {
            return new StyleOption.Details(name, description, imageUrl, isDefault, enabled);
        }
    }

    // ── 응답 ────────────────────────────────────────────

    public record FabricAdminView(UUID id, String code, String name, String mill, String composition, Short weightGsm,
                                  String color, String pattern, Fabric.Season season, Long priceFromKrw, String description,
                                  String imageUrl, Fabric.Status status, int sortOrder, Instant updatedAt) {
        static FabricAdminView from(Fabric f) {
            return new FabricAdminView(f.getId(), f.getCode(), f.getName(), f.getMill(), f.getComposition(), f.getWeightGsm(),
                    f.getColor(), f.getPattern(), f.getSeason(), f.getPriceFromKrw(), f.getDescription(), f.getImageUrl(),
                    f.getStatus(), f.getSortOrder(), f.getUpdatedAt());
        }
    }

    public record StyleOptionAdminView(UUID id, StyleOption.Category category, String categoryLabel, String code, String name,
                                       String description, String imageUrl, boolean isDefault, boolean enabled, int sortOrder) {
        static StyleOptionAdminView from(StyleOption o) {
            return new StyleOptionAdminView(o.getId(), o.getCategory(), o.getCategory().label(), o.getCode(), o.getName(),
                    o.getDescription(), o.getImageUrl(), o.isDefaultChoice(), o.isEnabled(), o.getSortOrder());
        }
    }
}
