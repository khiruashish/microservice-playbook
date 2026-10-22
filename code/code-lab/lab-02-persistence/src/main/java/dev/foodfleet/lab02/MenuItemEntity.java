package dev.foodfleet.lab02;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The persistence model. It stays inside the service: controllers return the MenuItem record, never this entity,
 * so table changes don't leak into the API and JSON can't write to columns (ADR-035).
 */
@Entity
@Table(name = "menu_item")
public class MenuItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private String restaurantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    private boolean vegetarian;

    private boolean available = true;

    @Version
    private long version;     // optimistic locking: a stale update fails instead of overwriting (ADR-021)

    protected MenuItemEntity() {
    }

    public MenuItemEntity(String restaurantId, String name, long pricePaise, boolean vegetarian) {
        this.restaurantId = restaurantId;
        this.name = name;
        this.pricePaise = pricePaise;
        this.vegetarian = vegetarian;
    }

    public void reprice(long newPricePaise) {
        if (newPricePaise <= 0) {
            throw new IllegalArgumentException("a menu item can't cost nothing");
        }
        this.pricePaise = newPricePaise;
    }

    public MenuItem toRecord() {
        return new MenuItem("m-" + id, name, pricePaise, vegetarian, available, version);
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }
}
