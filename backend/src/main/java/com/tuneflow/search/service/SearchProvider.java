package com.tuneflow.search.service;

import com.tuneflow.search.dto.SearchResponse;

/**
 * Search backend abstraction (docs section 24).
 *
 * <p>{@code JpaSearchProvider} (PostgreSQL/JPQL) is the MVP implementation. When catalog size and
 * query requirements justify it, an {@code ElasticsearchSearchProvider} implementing this same
 * interface can be dropped in - with typo tolerance, ranking and faceting provided by the search
 * engine - without touching the API layer.
 */
public interface SearchProvider {

    SearchResponse search(String query, String type, int limit);

    String name();
}
