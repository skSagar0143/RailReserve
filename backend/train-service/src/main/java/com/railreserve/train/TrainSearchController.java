package com.railreserve.train;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trains")
public class TrainSearchController {
    private final TrainSearchService trainSearchService;
    public TrainSearchController(TrainSearchService trainSearchService) { this.trainSearchService = trainSearchService; }

    @GetMapping("/search")
    public List<TrainSearchDtos.TrainSearchResult> search(
        @RequestParam @NotBlank String from,
        @RequestParam @NotBlank String to,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return trainSearchService.search(from, to, date);
    }
}
