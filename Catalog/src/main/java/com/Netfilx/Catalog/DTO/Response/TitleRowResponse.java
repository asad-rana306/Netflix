
package com.Netfilx.Catalog.DTO.Response;

import java.util.List;

public record TitleRowResponse(
        String rowTitle,
        List<TitleSummaryDto> titles
) {}