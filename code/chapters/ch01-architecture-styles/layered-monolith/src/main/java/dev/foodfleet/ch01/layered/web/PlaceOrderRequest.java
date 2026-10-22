package dev.foodfleet.ch01.layered.web;

import dev.foodfleet.ch01.layered.domain.OrderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(
        @NotBlank String customerId,
        @NotBlank String restaurantId,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines) {

    public record Line(
            @NotBlank String itemId,
            @NotBlank String name,
            @Min(1) int quantity,
            @PositiveOrZero long unitPricePaise) {

        OrderLine toDomain() {
            return new OrderLine(itemId, name, quantity, unitPricePaise);
        }
    }

    List<OrderLine> domainLines() {
        return lines.stream().map(Line::toDomain).toList();
    }
}
