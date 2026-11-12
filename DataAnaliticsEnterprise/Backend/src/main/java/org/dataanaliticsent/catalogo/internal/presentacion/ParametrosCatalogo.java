package org.dataanaliticsent.catalogo.internal.presentacion;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

final class ParametrosCatalogo {
    private ParametrosCatalogo() { }
    record ErrorCampo(String location,String field,String code,String message) { }
    static class Invalida extends RuntimeException {
        private final List<ErrorCampo> errors;
        Invalida(String location,String field,String code,String message) {
            super("Parámetros de catálogo inválidos"); errors=List.of(new ErrorCampo(location,field,code,message));
        }
        List<ErrorCampo> errors() { return errors; }
    }
    static void query(HttpServletRequest request,Set<String> permitidos) {
        for(var entry:request.getParameterMap().entrySet()) {
            if(!permitidos.contains(entry.getKey())) throw new Invalida("query","query","UNSUPPORTED","Parámetro no admitido");
            if(entry.getValue().length!=1) throw new Invalida("query",entry.getKey(),"DUPLICATE","Envía una sola vez el parámetro");
        }
    }
    static UUID id(String value,String location,String field) {
        if(value==null || value.isBlank()) throw new Invalida(location,field,"REQUIRED","Se requiere el identificador");
        if(!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new Invalida(location,field,"FORMAT","Utiliza un UUID canónico en minúsculas");
        }
        return UUID.fromString(value);
    }
    static int entero(HttpServletRequest request,String field,int defecto,int maximo) {
        String raw=request.getParameter(field);
        if(raw==null) return defecto;
        if(!raw.matches("[0-9]+")) throw new Invalida("query",field,"FORMAT","Utiliza un entero positivo");
        try {
            int value=Integer.parseInt(raw);
            if(value<1 || value>maximo) throw new NumberFormatException();
            return value;
        } catch(NumberFormatException error) { throw new Invalida("query",field,"RANGE","Valor fuera del intervalo permitido"); }
    }
}
