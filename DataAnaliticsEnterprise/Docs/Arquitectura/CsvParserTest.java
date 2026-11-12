import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.dataanaliticsent.Calidad.Dominio.*;
import org.dataanaliticsent.Calidad.Infraestructura.CsvFileStore;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CsvParserTest {
    @Test void maximumUnicodeHeaderMetadataCanBeRead(@TempDir Path directory) {
        byte[] key=new byte[32],hmac=new byte[32];Arrays.fill(key,(byte)1);Arrays.fill(hmac,(byte)2);
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule()).setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        var store=new CsvFileStore(directory.toString(),new SensitiveDataCipher("test",Map.of("test",key),hmac),mapper,new org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties());
        List<String> headers=new ArrayList<>();Map<String,CsvParser.ColumnType> types=new LinkedHashMap<>();
        for(int i=0;i<128;i++){String header="列".repeat(125)+String.format("%03d",i);headers.add(header);types.put(header,CsvParser.ColumnType.TEXT);}
        byte[] bytes=String.join(",",headers).getBytes(StandardCharsets.UTF_8);
        var metadata=new CsvFileStore.Metadata(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Clientes","clientes.csv","COMMA",headers,types,"a".repeat(64),bytes.length,0,Instant.now());
        store.write(metadata,bytes);assertEquals(metadata,store.metadata(metadata.projectId(),metadata.assetId()));
    }
    private CsvParser.Parsed parse(String data){return new CsvParser().parse(data.getBytes(StandardCharsets.UTF_8),"AUTO",10000,10000000);}
    @Test void commasQuotesEscapesAndPhysicalLinesArePreserved(){var parsed=parse("id,nota,correo\r\n001,\"hola, \"\"Ana\"\"\",\r\n002,\"primera\r\nsegunda\",malo\r\n003,fin,ok@example.test\r\n");assertEquals(3,parsed.records().size());assertEquals("hola, \"Ana\"",parsed.records().getFirst().get(1));assertEquals("primera\r\nsegunda",parsed.records().get(1).get(1));assertEquals(List.of(2L,3L,5L),parsed.lineNumbers());assertEquals("",parsed.records().getFirst().get(2));}
    @Test void bomSemicolonAndAccentedHeaders(){var parsed=parse("\ufeffCédula;Nombre Cliente;correo\n001;Ana;\n");assertEquals(';',parsed.delimiter());assertEquals("Cédula",parsed.headers().getFirst());assertEquals("001",parsed.input(Map.of()).rows().getFirst().get("Cédula"));}
    @Test void separatorInsideQuotesIsNotADelimiter(){var parsed=parse("\"Apellido, nombre\";correo\n\"Pérez, Ana\";ana@example.test");assertEquals(2,parsed.headers().size());assertEquals("Pérez, Ana",parsed.records().getFirst().getFirst());}
    @Test void explicitTabIsSupported(){var parsed=new CsvParser().parse("id\tcorreo\n1\tx".getBytes(StandardCharsets.UTF_8),"TAB",10,1000);assertEquals(2,parsed.headers().size());}
    @Test void blankAndDuplicateRecordsAreNotRemoved(){var parsed=parse("id,correo\n1,\n1,\n,\n");assertEquals(3,parsed.records().size());assertEquals(parsed.records().get(0),parsed.records().get(1));}
    @Test void headerOnlyHasNoArtificialRows(){assertTrue(parse("id,correo\n").records().isEmpty());}
    @Test void emptyFileIsRejected(){assertThrows(IllegalArgumentException.class,() -> parse(""));}
    @Test void duplicateHeadersAreRejected(){assertThrows(IllegalArgumentException.class,() -> parse("id,id\na,b"));}
    @Test void blankHeaderIsRejected(){assertThrows(IllegalArgumentException.class,() -> parse("id,\na,b"));}
    @Test void unevenRowsAreContractErrors(){var error=assertThrows(IllegalArgumentException.class,() -> parse("id,correo\n1,ok\n2"));assertTrue(error.getMessage().contains("3"));}
    @Test void unclosedAndUnescapedQuotesAreRejected(){assertThrows(IllegalArgumentException.class,() -> parse("id,nombre\n1,\"Ana"));assertThrows(IllegalArgumentException.class,() -> parse("id,nombre\n1,A\"na"));}
    @Test void invalidUtf8IsRejected(){assertThrows(IllegalArgumentException.class,() -> new CsvParser().parse(new byte[]{(byte)0xc3,0x28},"AUTO",10,1000));}
    @Test void rowAndByteLimitsDoNotTruncate(){assertThrows(CsvParser.LimitException.class,() -> new CsvParser().parse("id\n1\n2\n3".getBytes(StandardCharsets.UTF_8),"AUTO",2,1000));assertThrows(CsvParser.LimitException.class,() -> new CsvParser().parse("id\n1".getBytes(StandardCharsets.UTF_8),"AUTO",10,3));}
    @Test void allColumnsRemainTextUntilExplicitlyTyped(){var parsed=parse("Cédula,edad\n001,25\n002,invalido\n003,\n");assertEquals("001",parsed.input(Map.of()).rows().getFirst().get("Cédula"));var input=parsed.input(Map.of("edad",CsvParser.ColumnType.NUMBER));assertEquals(new BigDecimal("25"),input.rows().getFirst().get("edad"));assertEquals("invalido",input.rows().get(1).get("edad"));assertEquals("",input.rows().get(2).get("edad"));}
    @Test void findingsPointToOriginalCsvLineAndUnicodeField(){var input=parse("Cédula,Correo electrónico\n001,ok@example.test\n002,\n").input(Map.of());var rule=new QualityEngine.Rule(UUID.randomUUID(),QualityEngine.Dimension.COMPLETENESS,QualityEngine.Kind.REQUIRED,List.of("Correo electrónico"),BigDecimal.valueOf(100),BigDecimal.valueOf(100),false,true,null,null,null,null,null,null,null,null,null);var result=new QualityEngine().evaluate(input,List.of(rule),Instant.now(),Duration.ofSeconds(2)).results().getFirst();assertEquals(3,result.findings().getFirst().rowNumber());assertEquals("Correo electrónico",result.findings().getFirst().field());assertEquals(1,result.numerator());}
    @Test void originalCsvAndMetadataAreEncryptedAndBoundToProject(@TempDir Path directory)throws Exception {
        byte[] key=new byte[32],hmac=new byte[32];Arrays.fill(key,(byte)1);Arrays.fill(hmac,(byte)2);
        var cipher=new SensitiveDataCipher("test",Map.of("test",key),hmac);var mapper=new ObjectMapper().registerModule(new JavaTimeModule()).setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        var store=new CsvFileStore(directory.toString(),cipher,mapper,new org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties());UUID project=UUID.randomUUID(),asset=UUID.randomUUID();byte[] bytes="correo\nsecreto@example.test".getBytes(StandardCharsets.UTF_8);
        var metadata=new CsvFileStore.Metadata(project,asset,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Clientes","clientes.csv","COMMA",List.of("correo"),Map.of(),"a".repeat(64),bytes.length,1,Instant.now());
        store.write(metadata,bytes);assertEquals(metadata,store.metadata(project,asset));assertArrayEquals(bytes,store.bytes(metadata,10000));
        try(var files=Files.list(directory)){for(Path path:files.toList())assertFalse(Files.readString(path).contains("secreto@example.test"));}
        assertFalse(store.exists(UUID.randomUUID(),asset));
    }
}
