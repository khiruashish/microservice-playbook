package dev.foodfleet.lab05.menu;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows which settings are in force: what an operator checks after a configuration change. Never secret values. */
@RestController
public class SettingsController {

    private final MenuSettings settings;
    private final PartnerApiKey partnerKey;

    public SettingsController(MenuSettings settings, PartnerApiKey partnerKey) {
        this.settings = settings;
        this.partnerKey = partnerKey;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of("discountPercent", settings.discountPercent(),
                "maxItemsPerOrder", settings.maxItemsPerOrder(),
                "partnerApiKeyPresent", partnerKey.present());
    }
}
