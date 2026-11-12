package org.dataanaliticsent.catalogo.internal.presentacion;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.dataanaliticsent.identidad.IdentidadNoVerificadaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=CatalogoController.class)
public class ErroresCatalogoHttp {
    private final ProblemasHttp problemas;
    public ErroresCatalogoHttp(ProblemasHttp problemas) { this.problemas=problemas; }
    @ExceptionHandler(ParametrosCatalogo.Invalida.class)
    public void invalida(ParametrosCatalogo.Invalida error,HttpServletRequest req,HttpServletResponse res) throws IOException {
        problemas.escribir(req,res,422,"VALIDATION_ERROR","Revisa los parámetros indicados",error.errors());
    }
    @ExceptionHandler(IdentidadNoVerificadaException.class)
    public void identidad(HttpServletRequest req,HttpServletResponse res) throws IOException {
        problemas.escribir(req,res,401,"AUTHENTICATION_REQUIRED","Inicia sesión para continuar");
    }
    @ExceptionHandler(AccesoDenegadoException.class)
    public void prohibido(AccesoDenegadoException error,HttpServletRequest req,HttpServletResponse res) throws IOException {
        switch(error.motivo()) {
            case CONTEXTO_NO_AUTORIZADO,RECURSO_NO_VALIDADO,ALCANCE_INSUFICIENTE -> noDisponible(req,res);
            case SIN_IDENTIDAD,CUENTA_NO_ACTIVA -> identidad(req,res);
            default -> problemas.escribir(req,res,403,"ACCESS_DENIED","La operación no está autorizada");
        }
    }
    @ExceptionHandler(CatalogoController.NoDisponible.class)
    public void noDisponible(HttpServletRequest req,HttpServletResponse res) throws IOException {
        problemas.escribir(req,res,404,"RESOURCE_NOT_FOUND","El recurso no está disponible para esta consulta");
    }
    @ExceptionHandler(DataAccessException.class)
    public void datos(HttpServletRequest req,HttpServletResponse res) throws IOException {
        problemas.escribir(req,res,503,"CATALOG_UNAVAILABLE","No se pudo completar la consulta de catálogo");
    }
    @ExceptionHandler(Exception.class)
    public void interno(HttpServletRequest req,HttpServletResponse res) throws IOException {
        problemas.escribir(req,res,500,"INTERNAL_ERROR","No se pudo completar la consulta de catálogo");
    }
}
