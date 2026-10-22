package dev.foodfleet.lab02;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    private final MenuItemRepository items;

    public MenuService(MenuItemRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<MenuItem> menu(String restaurantId, boolean vegetarianOnly) {
        var rows = vegetarianOnly
                ? items.findByRestaurantIdAndVegetarianTrueOrderById(restaurantId)
                : items.findByRestaurantIdOrderById(restaurantId);
        return rows.stream().map(MenuItemEntity::toRecord).toList();
    }

    /**
     * Changes a price only if the caller saw the current version (If-Match). Two support agents editing the same
     * dish can't silently overwrite each other: the second gets a conflict and re-reads.
     */
    @Transactional
    public MenuItem reprice(long itemId, long expectedVersion, long newPricePaise) {
        MenuItemEntity item = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("no item " + itemId));
        if (item.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("item %d is at version %d, not %d"
                    .formatted(itemId, item.getVersion(), expectedVersion));
        }
        item.reprice(newPricePaise);
        return items.saveAndFlush(item).toRecord();
    }
}
