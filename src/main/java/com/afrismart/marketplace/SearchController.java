package com.afrismart.marketplace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam String q,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Integer radiusMeters
    ) {
        return searchService.search(q, lat, lng, radiusMeters);
    }
}
