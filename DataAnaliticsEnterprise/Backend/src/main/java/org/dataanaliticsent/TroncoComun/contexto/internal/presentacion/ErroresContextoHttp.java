package org.dataanaliticsent.TroncoComun.contexto.internal.presentacion;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.TroncoComun.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.TroncoComun.contexto.ContextoException;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadNoVerificadaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=ContextoController.class)
public class ErroresContextoHttp {
    private final ProblemasHttp salida;
    public ErroresContextoHttp(ProblemasHttp salida) { this.salida=salida; }
    @ExceptionHandler(ContextoException.class) public void contexto(ContextoException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        int status=switch(e.tipo()) { case INVALIDO->422; case NO_DISPONIBLE->404; case CONFLICTO->409; };
        salida.escribir(req,res,status,status==422?"VALIDATION_ERROR":status==404?"RESOURCE_NOT_FOUND":"CONTEXT_CONFLICT",
                status==422?"Revisa los campos indicados":status==404?"El recurso no está disponible":"La operación requiere revisar el estado actual");
    }
    @ExceptionHandler(AccesoDenegadoException.class) public void denegado(HttpServletRequest req,HttpServletResponse res) throws IOException {
        boolean contextual=req.getRequestURI().startsWith("/api/v1/organizations/");
        salida.escribir(req,res,contextual?404:403,contextual?"RESOURCE_NOT_FOUND":"ACCESS_DENIED",contextual?"El recurso no está disponible":"La operación no está autorizada");
    }
    @ExceptionHandler(IdentidadNoVerificadaException.class) public void identidad(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,401,"AUTHENTICATION_REQUIRED","Inicia sesión para continuar"); }
    @ExceptionHandler(HttpMessageNotReadableException.class) public void formato(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,422,"VALIDATION_ERROR","Revisa los campos indicados"); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class) public void tipo(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,415,"UNSUPPORTED_MEDIA_TYPE","Se requiere application/json"); }
    @ExceptionHandler(DataAccessException.class) public void datos(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,503,"CONTEXT_UNAVAILABLE","No se pudo completar la operación"); }
    @ExceptionHandler(Exception.class) public void inesperado(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,500,"INTERNAL_ERROR","No se pudo completar la operación"); }
}
