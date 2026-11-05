package org.dataanaliticsent.errores;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemasHttp {
    public static final String REQUEST_ID = ProblemasHttp.class.getName()+".request_id";
    private final ObjectMapper json;
    public ProblemasHttp(ObjectMapper json) { this.json=json; }
    public static String requestId(HttpServletRequest request) {
        Object actual=request.getAttribute(REQUEST_ID);
        if(actual instanceof String id) return id;
        String id=UUID.randomUUID().toString(); request.setAttribute(REQUEST_ID,id); return id;
    }
    public void escribir(HttpServletRequest request,HttpServletResponse response,int status,String code,String detail) throws IOException {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("type","about:blank"); body.put("title",switch(status) {
            case 400 -> "Solicitud inválida"; case 401 -> "Autenticación requerida";
            case 403 -> "Acceso denegado"; case 409 -> "Conflicto"; case 415 -> "Formato no soportado";
            case 429 -> "Demasiados intentos"; default -> "Servicio no disponible";
        });
        body.put("status",status); body.put("detail",detail); body.put("instance",request.getRequestURI());
        body.put("code",code); body.put("request_id",requestId(request));
        response.setStatus(status); response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8"); response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Request-Id",requestId(request));
        json.writeValue(response.getOutputStream(),body);
    }
    public void datos(HttpServletRequest request,HttpServletResponse response,Object data) throws IOException {
        response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        json.writeValue(response.getOutputStream(),Map.of("data",data,"meta",Map.of("request_id",requestId(request))));
    }
}
