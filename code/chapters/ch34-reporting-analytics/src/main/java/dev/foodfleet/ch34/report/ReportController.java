package dev.foodfleet.ch34.report;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final RevenueReports reports;

    public ReportController(RevenueReports reports) {
        this.reports = reports;
    }

    @GetMapping("/cities/{city}/daily-revenue")
    public RevenueReports.Report<RevenueReports.DayRevenue> daily(@PathVariable String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.daily(city, from, to);
    }

    @GetMapping("/cities/{city}/top-restaurants")
    public RevenueReports.Report<RevenueReports.RestaurantRevenue> top(@PathVariable String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reports.topRestaurants(city, from, to, Math.min(limit, 100));
    }

    @GetMapping("/monthly-revenue")
    public List<RevenueReports.MonthRevenue> monthly() {
        return reports.monthly();
    }
}
