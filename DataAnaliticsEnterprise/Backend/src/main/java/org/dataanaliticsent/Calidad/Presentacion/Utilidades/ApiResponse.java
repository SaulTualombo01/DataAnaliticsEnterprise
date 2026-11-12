package org.dataanaliticsent.Calidad.Presentacion.Utilidades;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        ResponseMeta meta
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseMeta(
            String requestId,
            PageInfo pageInfo
    ) {}

    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, new ResponseMeta(requestId, null));
    }

    public static <T> ApiResponse<T> ofPage(T data, String requestId, PageInfo pageInfo) {
        return new ApiResponse<>(data, new ResponseMeta(requestId, pageInfo));
    }
}
