package com.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Query execution statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class QueryStats {
    private double queryTimeMs;
    private int docsScanned;
    private int shardsQueried;
    private int resultsCount;
}
