package com.Netfilx.Catalog.DTO.Response;

import java.io.Serializable;
import java.util.List;

public record PaginatedResponse<T>(
        List<T> content,
        boolean hasNext,
        int currentPage
) implements Serializable {}