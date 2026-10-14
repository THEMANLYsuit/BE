package com.themanly.catalog.api;

import com.themanly.catalog.CatalogService;
import com.themanly.catalog.Fabric;
import com.themanly.catalog.StyleOption;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 손님이 보는 카탈로그. 로그인 없이 읽힌다(SecurityConfig). 준비 중(DRAFT) 원단은 나가지 않는다. */
@RestController
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/fabrics")
    public List<FabricView> fabrics() {
        return catalog.visibleFabrics().stream().map(FabricView::from).toList();
    }

    @GetMapping("/api/fabrics/{code}")
    public FabricView fabric(@PathVariable String code) {
        return FabricView.from(catalog.visibleFabric(code));
    }

    @GetMapping("/api/style-options")
    public List<StyleOptionView> styleOptions() {
        return catalog.enabledStyleOptions().stream().map(StyleOptionView::from).toList();
    }

    /** {@code soldOut} — 보이지만 고를 수 없다. */
    public record FabricView(String code, String name, String mill, String composition, Short weightGsm, String color,
                             String pattern, Fabric.Season season, Long priceFromKrw, String description, String imageUrl,
                             boolean soldOut) {
        static FabricView from(Fabric f) {
            return new FabricView(f.getCode(), f.getName(), f.getMill(), f.getComposition(), f.getWeightGsm(), f.getColor(),
                    f.getPattern(), f.getSeason(), f.getPriceFromKrw(), f.getDescription(), f.getImageUrl(),
                    f.getStatus() == Fabric.Status.SOLD_OUT);
        }
    }

    public record StyleOptionView(StyleOption.Category category, String categoryLabel, String code, String name,
                                  String description, String imageUrl, boolean isDefault) {
        static StyleOptionView from(StyleOption o) {
            return new StyleOptionView(o.getCategory(), o.getCategory().label(), o.getCode(), o.getName(), o.getDescription(),
                    o.getImageUrl(), o.isDefaultChoice());
        }
    }
}
