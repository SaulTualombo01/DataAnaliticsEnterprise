package org.dataanaliticsent.TroncoComun.identidad.internal.presentacion;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.TroncoComun.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.TroncoComun.identidad.CuentasException;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadNoVerificadaException;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=CuentasController.class)
public class ErroresCuentasHttp {
    private final ProblemasHttp salida;
    public ErroresCuentasHttp(ProblemasHttp salida) { this.salida=salida; }
    @ExceptionHandler(CuentasException.class) public void cuenta(CuentasException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        int status=switch(e.tipo()) { case INVALIDO,INVITACION_INVALIDA->422; case NO_DISPONIBLE->404; case CONFLICTO->409; case ENTREGA_NO_CONFIGURADA->503; };
        String code=switch(e.tipo()) { case INVALIDO->"VALIDATION_ERROR"; case INVITACION_INVALIDA->"INVALID_INVITATION"; case NO_DISPONIBLE->"RESOURCE_NOT_FOUND"; case CONFLICTO->"ACCOUNT_CONFLICT"; case ENTREGA_NO_CONFIGURADA->"DELIVERY_UNAVAILABLE"; };
        String detail=switch(e.tipo()) { case INVALIDO->"Revisa los campos indicados"; case INVITACION_INVALIDA->"La invitación no está disponible";
            case NO_DISPONIBLE->"El recurso no está disponible"; case CONFLICTO->"La operación requiere revisar el estado actual"; case ENTREGA_NO_CONFIGURADA->"No hay un canal de entrega habilitado"; };
        salida.escribir(req,res,status,code,detail);
    }
    @ExceptionHandler(AccesoDenegadoException.class) public void denegado(HttpServletRequest req,HttpServletResponse res) throws IOException {
        boolean scoped=req.getRequestURI().startsWith("/api/v1/organizations/");
        salida.escribir(req,res,scoped?404:403,scoped?"RESOURCE_NOT_FOUND":"ACCESS_DENIED",scoped?"El recurso no está disponible":"La operación no está autorizada");
    }
    @ExceptionHandler(IdentidadNoVerificadaException.class) public void identidad(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,401,"AUTHENTICATION_REQUIRED","Inicia sesión para continuar"); }
    @ExceptionHandler(HttpMessageNotReadableException.class) public void formato(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,422,"VALIDATION_ERROR","Revisa los campos indicados"); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class) public void tipo(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,415,"UNSUPPORTED_MEDIA_TYPE","Se requiere application/json"); }
    @ExceptionHandler(DataAccessException.class) public void datos(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,503,"IDENTITY_UNAVAILABLE","No se pudo completar la operación"); }
    @ExceptionHandler(Exception.class) public void inesperado(HttpServletRequest req,HttpServletResponse res) throws IOException { salida.escribir(req,res,500,"INTERNAL_ERROR","No se pudo completar la operación"); }
}
