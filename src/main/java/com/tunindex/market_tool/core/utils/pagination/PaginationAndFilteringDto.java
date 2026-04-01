package com.tunindex.market_tool.core.utils.pagination;


import com.tunindex.market_tool.core.utils.pagination.enums.SortingDirection;
import lombok.Data;
import java.util.Map;

@Data
public class PaginationAndFilteringDto {

    private Integer page = 1;
    private Integer size = 5;
    private String sortField = "id";
    private SortingDirection sortDirection = SortingDirection.DESC;
    private Map<String, String> filters;

}
