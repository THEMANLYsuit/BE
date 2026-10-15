package com.themanly.measurement.api;

import com.themanly.measurement.Measurement;
import com.themanly.measurement.MeasurementPoint;
import com.themanly.measurement.MeasurementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminMeasurementController {

    private final MeasurementService measurements;

    public AdminMeasurementController(MeasurementService measurements) {
        this.measurements = measurements;
    }

    @GetMapping("/measurement-points")
    public List<PointSpec> points() {
        return Arrays.stream(MeasurementPoint.values()).map(PointSpec::of).toList();
    }

    @GetMapping("/members/{memberId}/measurements")
    public List<AdminView> ofMember(@PathVariable UUID memberId) {
        return measurements.ofMember(memberId).stream().map(AdminView::from).toList();
    }

    @PostMapping("/members/{memberId}/measurements")
    public ResponseEntity<AdminView> record(@PathVariable UUID memberId, @Valid @RequestBody Input in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminView.from(measurements.record(memberId, in.toService())));
    }

    @PutMapping("/measurements/{id}")
    public AdminView update(@PathVariable UUID id, @Valid @RequestBody Input in) {
        return AdminView.from(measurements.update(id, in.toService()));
    }

    @DeleteMapping("/measurements/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        measurements.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record Input(@NotNull @PastOrPresent LocalDate measuredOn, @NotNull Map<MeasurementPoint, BigDecimal> points,
                        @Size(max = 1000) String bodyNote, @Size(max = 1000) String adminNote) {
        MeasurementService.Input toService() {
            return new MeasurementService.Input(measuredOn, points, bodyNote, adminNote);
        }
    }

    public record PointSpec(MeasurementPoint point, String label, MeasurementPoint.Group group, String groupLabel, String unit,
                            int min, int max) {
        static PointSpec of(MeasurementPoint p) {
            return new PointSpec(p, p.label(), p.group(), p.group().label(), p.unit().symbol(), p.min(), p.max());
        }
    }

    public record AdminView(UUID id, UUID memberId, LocalDate measuredOn, String bodyNote, String adminNote,
                            List<MeasurementController.PointView> points, Instant createdAt, Instant updatedAt) {
        static AdminView from(Measurement m) {
            return new AdminView(m.getId(), m.getMemberId(), m.getMeasuredOn(), m.getBodyNote(), m.getAdminNote(),
                    MeasurementController.PointView.of(m), m.getCreatedAt(), m.getUpdatedAt());
        }
    }
}