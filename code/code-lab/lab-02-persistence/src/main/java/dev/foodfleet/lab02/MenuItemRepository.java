package dev.foodfleet.lab02;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, Long> {

    List<MenuItemEntity> findByRestaurantIdOrderById(String restaurantId);

    List<MenuItemEntity> findByRestaurantIdAndVegetarianTrueOrderById(String restaurantId);
}
