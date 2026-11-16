package org.dataanaliticsent.TroncoComun.contexto.internal.presentacion;
import java.io.IOException;
import java.util.*;
import jakarta.servlet.http.*;
import org.dataanaliticsent.TroncoComun.contexto.*;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.springframework.web.bind.annotation.*;
@RestController
public class AdministracionActualController {
    public record AccesoHttp(List<String> platform_permissions,List<AdministracionActualApi.Empresa> companies,AdministracionActualApi.Empresa selected_organization,List<String> organization_permissions) { }
    private final AdministracionActualApi api; private final ProblemasHttp salida;
    public AdministracionActualController(AdministracionActualApi api,ProblemasHttp salida) { this.api=api; this.salida=salida; }
    @GetMapping("/api/v1/auth/administration")
    public void consultar(HttpServletRequest req,HttpServletResponse res) throws IOException {
        req.getParameterMap().forEach((key,values)->{ if(!"organization_id".equals(key) || values.length!=1) throw new ContextoException(ContextoException.Tipo.INVALIDO); });
        String value=req.getParameter("organization_id"); UUID org=null;
        if(value!=null) {
            if(!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new ContextoException(ContextoException.Tipo.INVALIDO);
            org=UUID.fromString(value);
        }
        var a=api.consultar(org); salida.datos(req,res,new AccesoHttp(a.platformPermissions(),a.companies(),a.selectedOrganization(),a.organizationPermissions()));
    }
}
