package com.themanly.rental;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "rental_item")
public class RentalItem {
    public enum Status{
        DRAFT,
        PUBLISHED,
        UNAVAILABLE
    }
    public record Details(String name, String category, String description, String imageUrl, Long priceKrw, Long depositKrw, int rentalDays, List<String> sizes){}
    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String description;
    @Column(name = "image_url")
    private String imageUrl;
    @Column(name = "price_krw")
    private Long priceKrw;
    @Column(name = "deposit_krw")
    private Long depositKrw;
    @Column(name = "rental_days", nullable = false)
    private short rentalDays;
    @Column(nullable = false)
    private String sizes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
    protected RentalItem(){}
    public RentalItem(UUID id, int sortOrder){
        this.id = Objects.requireNonNull(id, "id");
        this.sortOrder = sortOrder;
        this.name = "";
        this.category = "";
        this.description = "";
        this.sizes = "";
        this.rentalDays = 3;
    }
    public void change(Details d){
        this.name = clean(d.name());
        this.category = clean(d.category());
        this.description = clean(d.description());
        this.imageUrl = d.imageUrl() == null || d.imageUrl().isBlank() ? null : d.imageUrl().trim();
        this.priceKrw = d.priceKrw();
        this.depositKrw = d.depositKrw();
        this.rentalDays = (short) d.rentalDays();
        this.sizes = d.sizes() == null ? "" : String.join(",", d.sizes().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
    }
    public void changeStatus(Status status){this.status = Objects.requireNonNull(status, "status");}
    public boolean isVisible(){return status != Status.DRAFT;}
    public boolean isRentable(){return status == Status.PUBLISHED;}
    public List<String> sizes(){return sizes.isEmpty() ? List.of() : Arrays.asList(sizes.split(","));}
    public boolean accepts(String size){
        List<String> all = sizes();
        return all.isEmpty() ? size == null || size.isBlank() : size != null && all.contains(size.trim());
    }
    private static String clean(String s){return s == null ? "" : s.trim();}
    public UUID getId(){return id;}
    public String getName(){return name;}
    public String getCategory(){return category;}
    public String getDescription(){return description;}
    public String getImageUrl(){return imageUrl;}
    public Long getPriceKrw(){return priceKrw;}
    public Long getDepositKrw(){return depositKrw;}
    public int getRentalDays(){return rentalDays;}
    public Status getStatus(){return status;}
    public int getSortOrder(){return sortOrder;}
    public Instant getUpdatedAt(){return updatedAt;}
}
