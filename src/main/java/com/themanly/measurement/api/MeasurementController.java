package com.themanly.measurement.api;

import com.themanly.auth.MemberPrincipal;
import com.themanly.measurement.Measurement;
import com.themanly.measurement.MeasurementPoint;
import com.themanly.measurement.MeasurementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeasurementController {

    private final MeasurementService measurements;

    public MeasurementController(MeasurementService measurements) {
        this.measurements = measurements;
    }

    @GetMapping("/api/me/measurements")
    public List<View> mine(@AuthenticationPrincipal MemberPrincipal principal) {
        return measurements.ofMember(principal.getId()).stream().map(View::from).toList();
    }

    public record PointView(MeasurementPoint point, String label, MeasurementPoint.Group group, String groupLabel,
                            String unit, BigDecimal value) {
        static List<PointView> of(Measurement m) {
            return m.getPoints().entrySet().stream()
                    .map(e -> new PointView(e.getKey(), e.getKey().label(), e.getKey().group(), e.getKey().group().label(),
                            e.getKey().unit().symbol(), e.getValue()))
                    .toList();
        }
    }

    public record View(UUID id, LocalDate measuredOn, String bodyNote, List<PointView> points) {
        static View from(Measurement m) {
            return new View(m.getId(), m.getMeasuredOn(), m.getBodyNote(), PointView.of(m));
        }
    }
}