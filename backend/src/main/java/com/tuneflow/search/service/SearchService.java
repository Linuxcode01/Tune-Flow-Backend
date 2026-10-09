package com.tuneflow.search.service;

import com.tuneflow.search.dto.SearchResponse;
import com.tuneflow.search.dto.SearchSuggestionResponse;

public interface SearchService {

    SearchResponse search(String query, String type, int limit);

    SearchSuggestionResponse suggest(String query, int limit);
}
