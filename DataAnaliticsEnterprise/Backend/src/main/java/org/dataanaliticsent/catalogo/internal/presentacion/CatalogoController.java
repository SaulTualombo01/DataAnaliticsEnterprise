package org.dataanaliticsent.catalogo.internal.presentacion;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataanaliticsent.catalogo.CatalogoApi;
import org.dataanaliticsent.errores.ProblemasHttp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{project_id}/assets/{asset_id}")
public class CatalogoController {
    private final CatalogoApi catalogo;
    private final ProblemasHttp salida;
    public CatalogoController(CatalogoApi catalogo,ProblemasHttp salida) { this.catalogo=catalogo; this.salida=salida; }
    static class NoDisponible extends RuntimeException { }
    record Origen(String namespace,String object_path) { }
    record ActivoHttp(UUID id,UUID source_id,UUID organization_id,UUID project_id,String name,String kind,
            Origen origin,String status,UUID metadata_version_id,String structure_origin,String business_description,
            List<String> tags,Instant last_discovery_at,Instant updated_at) { }
    record CampoHttp(UUID id,UUID organization_id,UUID project_id,UUID asset_id,UUID metadata_version_id,
            String name,String path,int position,String native_type,String logical_type,Boolean nullable,
            String business_description,String status) { }
    record PageInfo(int page,int page_size,int returned_count,boolean has_next) { }

    @GetMapping
    public void activo(@PathVariable("project_id") String project,@PathVariable("asset_id") String asset,
            HttpServletRequest request,HttpServletResponse response) throws IOException {
        ParametrosCatalogo.query(request,Set.of());
        UUID proyecto=ParametrosCatalogo.id(project,"path","project_id"),activo=ParametrosCatalogo.id(asset,"path","asset_id");
        var a=catalogo.consultarActivo(proyecto,activo).orElseThrow(NoDisponible::new);
        salida.datos(request,response,new ActivoHttp(a.id(),a.sourceId(),a.organizationId(),a.projectId(),a.name(),code(a.kind()),
                new Origen(a.originNamespace(),a.originObjectPath()),code(a.status()),a.metadataVersionId(),code(a.structureOrigin()),
                a.businessDescription(),a.tags(),a.lastDiscoveryAt(),a.updatedAt()));
    }

    @GetMapping("/fields")
    public void campos(@PathVariable("project_id") String project,@PathVariable("asset_id") String asset,
            HttpServletRequest request,HttpServletResponse response) throws IOException {
        ParametrosCatalogo.query(request,Set.of("metadata_version_id","page","page_size","sort"));
        UUID proyecto=ParametrosCatalogo.id(project,"path","project_id"),activo=ParametrosCatalogo.id(asset,"path","asset_id");
        UUID version=ParametrosCatalogo.id(request.getParameter("metadata_version_id"),"query","metadata_version_id");
        int page=ParametrosCatalogo.entero(request,"page",1,Integer.MAX_VALUE);
        int size=ParametrosCatalogo.entero(request,"page_size",25,100);
        String sort=request.getParameter("sort");
        if(sort!=null && !sort.equals("position,id")) throw new ParametrosCatalogo.Invalida("query","sort","ENUM","El orden permitido es position,id");
        var result=catalogo.consultarCamposVersion(proyecto,activo,version,page,size).orElseThrow(NoDisponible::new);
        var fields=result.fields().stream().map(c -> new CampoHttp(c.id(),result.organizationId(),result.projectId(),c.assetId(),
                result.metadataVersionId(),c.name(),c.path(),c.position(),c.nativeType(),code(c.logicalType()),c.nullable(),
                c.businessDescription(),code(c.status()))).toList();
        salida.datos(request,response,fields,Map.of("page_info",new PageInfo(page,size,fields.size(),result.hasNext())));
    }
    private static String code(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}
