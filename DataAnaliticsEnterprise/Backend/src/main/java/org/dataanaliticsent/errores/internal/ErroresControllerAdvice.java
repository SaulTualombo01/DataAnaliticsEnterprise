package org.dataanaliticsent.errores.internal;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.dataanaliticsent.identidad.IdentidadNoVerificadaException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ErroresControllerAdvice {
    private final ProblemasHttp problemas;
    public ErroresControllerAdvice(ProblemasHttp problemas) { this.problemas=problemas; }
    @ExceptionHandler(IdentidadNoVerificadaException.class)
    public void identidad(HttpServletRequest req,HttpServletResponse res) throws IOException { problemas.escribir(req,res,401,"AUTHENTICATION_REQUIRED","Inicia sesión para continuar"); }
    @ExceptionHandler(AccesoDenegadoException.class)
    public void prohibido(HttpServletRequest req,HttpServletResponse res) throws IOException { problemas.escribir(req,res,403,"ACCESS_DENIED","La operación no está autorizada"); }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public void invalido(HttpServletRequest req,HttpServletResponse res) throws IOException { problemas.escribir(req,res,400,"INVALID_REQUEST","Revisa los campos y la política de contraseña"); }
    @ExceptionHandler(DataAccessException.class)
    public void datos(HttpServletRequest req,HttpServletResponse res) throws IOException { problemas.escribir(req,res,503,"IDENTITY_UNAVAILABLE","No se pudo completar la operación"); }
    @ExceptionHandler(IllegalStateException.class)
    public void conflicto(HttpServletRequest req,HttpServletResponse res) throws IOException { problemas.escribir(req,res,409,"CONFLICT","La operación requiere volver a consultar el estado actual"); }
}
