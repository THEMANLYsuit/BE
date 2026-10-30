package com.themanly.catalog;

import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.site.FrontRebuildTrigger;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원단 · 스타일 카탈로그 — 손님 읽기와 관리자 등록 · 수정.
 *
 * <p>손님 화면은 카탈로그를 <b>빌드할 때</b> 받는다. 손님에게 보이는 것이 바뀌면(공개된 원단 수정 · 공개 · 내림 · 순서,
 * 스타일 선택지) 커밋 뒤 손님 화면을 다시 만든다(FrontRebuildTrigger). 준비 중 원단만 고친 것은 다시 만들지 않는다.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final EnumSet<Fabric.Status> VISIBLE = EnumSet.of(Fabric.Status.PUBLISHED, Fabric.Status.SOLD_OUT);

    private final FabricRepository fabrics;
    private final StyleOptionRepository styleOptions;
    private final ApplicationEventPublisher events;

    public CatalogService(FabricRepository fabrics, StyleOptionRepository styleOptions, ApplicationEventPublisher events) {
        this.fabrics = fabrics;
        this.styleOptions = styleOptions;
        this.events = events;
    }

    private void rebuild(String reason) {
        events.publishEvent(new FrontRebuildTrigger.SiteChanged(reason));
    }

    // ── 손님 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Fabric> visibleFabrics() {
        return fabrics.findByStatusInOrderBySortOrderAscNameAsc(VISIBLE);
    }

    /** 코드로 한 장. 준비 중(DRAFT)은 없는 것과 같다 — 404. */
    @Transactional(readOnly = true)
    public Fabric visibleFabric(String code) {
        return fabrics.findByCode(Fabric.normalizeCode(code)).filter(Fabric::isVisible)
                .orElseThrow(() -> new ResourceNotFoundException("원단 없음 code=" + code));
    }

    @Transactional(readOnly = true)
    public List<StyleOption> enabledStyleOptions() {
        return styleOptions.findByEnabledTrueOrderByCategoryAscSortOrderAsc();
    }

    // ── 관리자: 원단 ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Fabric> allFabrics() {
        return fabrics.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public Fabric fabric(UUID id) {
        return fabrics.findById(id).orElseThrow(() -> new ResourceNotFoundException("원단 없음 id=" + id));
    }

    /** 새 원단은 준비 중(DRAFT)으로 맨 뒤에 들어간다. 코드가 겹치면 409. */
    @Transactional
    public Fabric createFabric(String code, Fabric.Details details) {
        String normalized = Fabric.normalizeCode(code);
        if (fabrics.existsByCode(normalized)) {
            throw new DomainRuleException("DUPLICATE_CODE", "이미 등록된 원단 번호입니다: " + normalized);
        }
        Fabric fabric = new Fabric(UUID.randomUUID(), normalized, fabrics.maxSortOrder() + 10);
        fabric.change(details);
        fabrics.save(fabric);
        log.info("원단 등록 id={} code={}", fabric.getId(), normalized);
        return fabric;
    }

    @Transactional
    public Fabric updateFabric(UUID id, Fabric.Details details) {
        Fabric fabric = fabric(id);
        fabric.change(details);
        log.info("원단 수정 id={}", id);
        if (fabric.isVisible()) {
            rebuild("원단 수정");
        }
        return fabric;
    }

    @Transactional
    public Fabric changeFabricStatus(UUID id, Fabric.Status status) {
        Fabric fabric = fabric(id);
        if (status != Fabric.Status.DRAFT && fabric.getName().isBlank()) {
            throw new DomainRuleException("NOT_READY", "이름이 없는 원단은 공개할 수 없습니다.");
        }
        boolean wasVisible = fabric.isVisible();
        fabric.changeStatus(status);
        log.info("원단 상태 id={} status={}", id, status);
        // 준비 중 → 준비 중은 손님 화면과 상관없다. 공개 · 품절 · 내림은 다시 만든다
        if (wasVisible || fabric.isVisible()) {
            rebuild("원단 상태");
        }
        return fabric;
    }

    /** 원단 순서 — 관리자가 끌어 놓은 순서대로 id 목록을 준다. 목록에 없는 원단은 뒤로 밀린다. */
    @Transactional
    public void reorderFabrics(List<UUID> ids) {
        List<Fabric> found = fabrics.findByIdIn(ids);
        if (found.size() != ids.stream().distinct().count()) {
            throw DomainRuleException.badRequest("UNKNOWN_FABRIC", "목록에 없는 원단이 섞여 있습니다. 화면을 새로고침해 주세요.");
        }
        Map<UUID, Fabric> byId = found.stream().collect(Collectors.toMap(Fabric::getId, f -> f));
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo((i + 1) * 10);
        }
        log.info("원단 순서 변경 count={}", ids.size());
        if (found.stream().anyMatch(Fabric::isVisible)) {
            rebuild("원단 순서");
        }
    }

    /** 스타일 선택지 순서 — 같은 묶음 안에서만. */
    @Transactional
    public void reorderStyleOptions(StyleOption.Category category, List<UUID> ids) {
        List<StyleOption> found = styleOptions.findByIdIn(ids);
        if (found.size() != ids.stream().distinct().count() || found.stream().anyMatch(o -> o.getCategory() != category)) {
            throw DomainRuleException.badRequest("UNKNOWN_STYLE", "같은 묶음의 선택지만 순서를 바꿀 수 있습니다. 화면을 새로고침해 주세요.");
        }
        Map<UUID, StyleOption> byId = found.stream().collect(Collectors.toMap(StyleOption::getId, o -> o));
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo((i + 1) * 10);
        }
        log.info("스타일 선택지 순서 변경 category={} count={}", category, ids.size());
        rebuild("스타일 순서");
    }

    // ── 관리자: 스타일 선택지 ─────────────────────────────

    @Transactional(readOnly = true)
    public List<StyleOption> allStyleOptions() {
        return styleOptions.findAllByOrderByCategoryAscSortOrderAsc();
    }

    @Transactional(readOnly = true)
    public StyleOption styleOption(UUID id) {
        return styleOptions.findById(id).orElseThrow(() -> new ResourceNotFoundException("스타일 선택지 없음 id=" + id));
    }

    @Transactional
    public StyleOption createStyleOption(StyleOption.Category category, String code, StyleOption.Details details) {
        String normalized = StyleOption.normalizeCode(code);
        if (styleOptions.existsByCategoryAndCode(category, normalized)) {
            throw new DomainRuleException("DUPLICATE_CODE", "같은 묶음에 이미 있는 코드입니다: " + normalized);
        }
        StyleOption option = new StyleOption(UUID.randomUUID(), category, normalized, styleOptions.maxSortOrder(category) + 10);
        applyStyleOption(option, details);
        styleOptions.save(option);
        log.info("스타일 선택지 등록 id={} category={} code={}", option.getId(), category, normalized);
        if (option.isEnabled()) {
            rebuild("스타일 등록");
        }
        return option;
    }

    @Transactional
    public StyleOption updateStyleOption(UUID id, StyleOption.Details details) {
        StyleOption option = styleOption(id);
        boolean wasEnabled = option.isEnabled();
        applyStyleOption(option, details);
        log.info("스타일 선택지 수정 id={}", id);
        if (wasEnabled || option.isEnabled()) {
            rebuild("스타일 수정");
        }
        return option;
    }

    /** 기본값은 묶음마다 하나 — 새로 기본값이 되면 전에 기본값이던 것을 내린다. 같은 트랜잭션 안이라 DB 유니크에 걸리지 않는다. */
    private void applyStyleOption(StyleOption option, StyleOption.Details details) {
        if (details.defaultChoice()) {
            styleOptions.findByCategoryAndDefaultChoiceTrue(option.getCategory())
                    .filter(other -> !other.getId().equals(option.getId()))
                    .ifPresent(other -> {
                        other.clearDefault();
                        styleOptions.flush();
                    });
        }
        option.change(details);
    }
}
