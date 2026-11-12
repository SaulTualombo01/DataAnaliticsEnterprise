package org.dataanaliticsent.catalogo.internal.presentacion;

import java.util.Set;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ParametrosCatalogoTests {
    @Test void idsAbreviadosYMayusculasNoSeNormalizanSilenciosamente() {
        assertThrows(ParametrosCatalogo.Invalida.class,()->ParametrosCatalogo.id("1-1-1-1-1","path","asset_id"));
        assertThrows(ParametrosCatalogo.Invalida.class,()->ParametrosCatalogo.id("ABCDEFAB-0000-0000-0000-000000000000","path","asset_id"));
        UUID id=UUID.randomUUID(); assertEquals(id,ParametrosCatalogo.id(id.toString(),"path","asset_id"));
    }
    @Test void desconocidosYDuplicadosSeRechazanSinReflejarSusValores() {
        var request=new MockHttpServletRequest(); request.addParameter("actor","valor-privado");
        var unsupported=request;
        var e=assertThrows(ParametrosCatalogo.Invalida.class,()->ParametrosCatalogo.query(unsupported,Set.of("page")));
        assertFalse(e.getMessage().contains("valor-privado"));
        request=new MockHttpServletRequest(); request.addParameter("page","1","2");
        var duplicated=request;
        assertThrows(ParametrosCatalogo.Invalida.class,()->ParametrosCatalogo.query(duplicated,Set.of("page")));
    }
    @Test void paginacionNoDesbordaNiAceptaDecimales() {
        for(String value:new String[]{"0","-1","1.5","2147483648","9999999999999999999999999999"}) {
            var request=new MockHttpServletRequest(); request.addParameter("page",value);
            assertThrows(ParametrosCatalogo.Invalida.class,()->ParametrosCatalogo.entero(request,"page",1,Integer.MAX_VALUE));
        }
    }
    @Test void ocultacionDeRecursosNoExponeMotivoInterno() throws Exception {
        var json=new ObjectMapper(); var writer=new ProblemasHttp(json); var advice=new ErroresCatalogoHttp(writer);
        for(Motivo motivo:new Motivo[]{Motivo.CONTEXTO_NO_AUTORIZADO,Motivo.RECURSO_NO_VALIDADO,Motivo.ALCANCE_INSUFICIENTE}) {
            var response=new MockHttpServletResponse();
            advice.prohibido(new AccesoDenegadoException(motivo),new MockHttpServletRequest(),response);
            assertEquals(404,response.getStatus()); assertFalse(response.getContentAsString().contains(motivo.name()));
            assertTrue(response.getContentAsString().contains("urn:request:"));
        }
    }
    @Test void errorDeDatosTieneTipoYStatusComunes() throws Exception {
        var response=new MockHttpServletResponse();
        new ErroresCatalogoHttp(new ProblemasHttp(new ObjectMapper())).datos(new MockHttpServletRequest(),response);
        assertEquals(503,response.getStatus()); assertTrue(response.getContentAsString().contains("CATALOG_UNAVAILABLE"));
    }
    @Test void metadatosExtraNoPuedenSuplantarCorrelacion() throws Exception {
        var request=new MockHttpServletRequest(); var response=new MockHttpServletResponse(); var json=new ObjectMapper();
        new ProblemasHttp(json).datos(request,response,java.util.List.of(),java.util.Map.of("request_id","falso"));
        assertFalse(response.getContentAsString().contains("falso"));
        assertEquals(ProblemasHttp.requestId(request),response.getHeader("X-Request-Id"));
    }
}
