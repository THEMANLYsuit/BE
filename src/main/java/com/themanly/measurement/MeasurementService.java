package com.themanly.measurement;

import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeasurementService {

    private static final Logger log = LoggerFactory.getLogger(MeasurementService.class);

    private final MeasurementRepository measurements;
    private final MemberRepository members;

    public MeasurementService(MeasurementRepository measurements, MemberRepository members) {
        this.measurements = measurements;
        this.members = members;
    }

    public record Input(LocalDate measuredOn, Map<MeasurementPoint, BigDecimal> points, String bodyNote, String adminNote) {
    }

    @Transactional(readOnly = true)
    public List<Measurement> ofMember(UUID memberId) {
        return measurements.findByMemberIdOrderByMeasuredOnDescCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public Measurement get(UUID id) {
        return measurements.findById(id).orElseThrow(() -> new ResourceNotFoundException("치수 없음 id=" + id));
    }

    @Transactional
    public Measurement record(UUID memberId, Input in) {
        Member member = members.findById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("회원 없음 id=" + memberId));
        Measurement m = new Measurement(UUID.randomUUID(), member.getId());
        m.change(in.measuredOn(), clean(in.points()), in.bodyNote(), in.adminNote());
        measurements.save(m);
        log.info("치수 기록 id={} memberId={} points={}", m.getId(), memberId, m.getPoints().size());
        return m;
    }

    @Transactional
    public Measurement update(UUID id, Input in) {
        Measurement m = get(id);
        m.change(in.measuredOn(), clean(in.points()), in.bodyNote(), in.adminNote());
        log.info("치수 수정 id={}", id);
        return m;
    }

    @Transactional
    public void delete(UUID id) {
        measurements.delete(get(id));
        log.info("치수 삭제 id={}", id);
    }

    static Map<MeasurementPoint, BigDecimal> clean(Map<MeasurementPoint, BigDecimal> raw) {
        Map<MeasurementPoint, BigDecimal> result = new EnumMap<>(MeasurementPoint.class);
        if (raw != null) {
            for (Map.Entry<MeasurementPoint, BigDecimal> e : raw.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                MeasurementPoint p = e.getKey();
                BigDecimal v = e.getValue().stripTrailingZeros();
                if (v.scale() > 1) {
                    throw DomainRuleException.badRequest("INVALID_MEASUREMENT", p.label() + " 은(는) 소수점 한 자리까지 적어 주세요.");
                }
                if (!p.accepts(v)) {
                    throw DomainRuleException.badRequest("INVALID_MEASUREMENT",
                            p.label() + " 값이 범위(" + p.min() + "~" + p.max() + p.unit().symbol() + ")를 벗어났습니다.");
                }
                result.put(p, v.setScale(1));
            }
        }
        if (result.isEmpty()) {
            throw DomainRuleException.badRequest("EMPTY_MEASUREMENT", "치수를 하나 이상 적어 주세요.");
        }
        return result;
    }
}