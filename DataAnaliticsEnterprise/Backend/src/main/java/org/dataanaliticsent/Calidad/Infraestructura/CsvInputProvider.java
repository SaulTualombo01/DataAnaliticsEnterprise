package org.dataanaliticsent.Calidad.Infraestructura;

import org.dataanaliticsent.Calidad.Aplicacion.*;
import org.dataanaliticsent.Calidad.Dominio.CsvParser;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.*;
import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;
import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

/** Adaptador local de entrada: CSV propio y proveedor externo conservan el mismo puerto de evaluación. */
@Component @Primary @ConditionalOnProperty(name="m2.csv.enabled",havingValue="true")
public class CsvInputProvider implements InputProvider {
    private final InputProvider external;private final CsvFileStore store;private final ProjectTransaction tx;private final QualityRepository repo;private final AccessService access;private final M2Properties properties;private final InputRetention retention;
    public CsvInputProvider(@Qualifier("externalInputProvider") InputProvider external,CsvFileStore store,ProjectTransaction tx,QualityRepository repo,AccessService access,M2Properties properties,InputRetention retention){this.external=external;this.store=store;this.tx=tx;this.repo=repo;this.access=access;this.properties=properties;this.retention=retention;}
    public List<Asset> assets(UUID actor,UUID project){
        List<Asset> assets=new ArrayList<>(external.assets(actor,project));
        var refs=tx.execute(actor,project,() -> repo.list(AssetRef.class,project,null,0,1000));
        for(var ref:refs)if(store.exists(project,ref.assetId)){var m=store.metadata(project,ref.assetId);assets.add(asset(m));}
        return List.copyOf(assets);
    }
    public static Asset asset(CsvFileStore.Metadata m){return new Asset(m.assetId(),m.sourceId(),m.metadataVersionId(),m.name(),new LinkedHashSet<>(m.headers()),"CSV");}
    public Capture acquire(UUID actor,UUID project,UUID asset,UUID metadata,UUID snapshot){
        if(!isCsv(actor,project,asset))return external.acquire(actor,project,asset,metadata,snapshot);
        var m=store.metadata(project,asset);
        if(!m.metadataVersionId().equals(metadata)||snapshot!=null&&!snapshot.equals(m.snapshotId()))throw missing();
        return capture(m);
    }
    public Capture read(UUID actor,UUID project,UUID asset,UUID metadata,UUID read,UUID snapshot){
        if(!isCsv(actor,project,asset))return external.read(actor,project,asset,metadata,read,snapshot);
        var m=store.metadata(project,asset);
        if(!m.metadataVersionId().equals(metadata)||!m.readId().equals(read)||!Objects.equals(snapshot,m.snapshotId()))throw missing();
        return capture(m);
    }
    private boolean isCsv(UUID actor,UUID project,UUID asset){return tx.execute(actor,project,() -> {access.require(actor,project,asset,Action.MANAGE_QUALITY);return repo.list(AssetRef.class,project,asset,0,1).size()==1&&store.exists(project,asset);});}
    private Capture capture(CsvFileStore.Metadata m){
        var expiresAt = retention.csvExpiresAt(m.acquiredAt());
        if (retention.expired(expiresAt))
            throw new org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException(org.springframework.http.HttpStatus.GONE,"INPUT_EXPIRED","Entrada caducada.","El CSV superó su plazo de conservación. Se mantiene el historial de resultados.");
        byte[] bytes=store.bytes(m,properties.getLimits().getMaxInputBytes());
        if(!CsvImportService.sha256(bytes).equals(m.sha256())||bytes.length!=m.byteCount())throw new IllegalStateException("INPUT_INTEGRITY_MISMATCH");
        var parsed=new CsvParser().parse(bytes,m.separator(),properties.getLimits().getMaxInputRows(),properties.getLimits().getMaxInputBytes());
        if(!parsed.headers().equals(m.headers())||parsed.records().size()!=m.rowCount())throw new IllegalStateException("INPUT_INTEGRITY_MISMATCH");
        return new Capture(m.readId(),m.snapshotId(),m.assetId(),m.metadataVersionId(),m.acquiredAt(),expiresAt,m.sha256(),m.byteCount(),parsed.input(m.columnTypes()));
    }
}
