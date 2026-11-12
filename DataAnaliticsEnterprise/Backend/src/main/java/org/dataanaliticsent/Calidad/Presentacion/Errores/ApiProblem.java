package org.dataanaliticsent.Calidad.Presentacion.Errores;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Locale;

/** Contrato de error compartido entre los filtros de seguridad y los controladores. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiProblem(String type, String title, int status, String detail, String instance,
                         String code, @com.fasterxml.jackson.annotation.JsonProperty("request_id") String requestId,
                         List<ApiException.FieldErrorDetail> errors) {
    public static ApiProblem of(int status, String code, String title, String detail, String requestId,
                                List<ApiException.FieldErrorDetail> errors) {
        String slug = code.toLowerCase(Locale.ROOT).replace('_', '-');
        return new ApiProblem("urn:saulongo:problem:" + slug, title, status, detail,
                "urn:request:" + requestId, code, requestId, errors == null || errors.isEmpty() ? null : errors);
    }
}
