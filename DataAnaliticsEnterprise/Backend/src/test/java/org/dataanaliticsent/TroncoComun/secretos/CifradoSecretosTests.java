package org.dataanaliticsent.TroncoComun.secretos;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.dataanaliticsent.TroncoComun.secretos.SecretosApi.*;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.CifradorSecretos.Vinculo;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.RepositorioSecretos.Cifrado;
import org.dataanaliticsent.TroncoComun.secretos.internal.infraestructura.CifradoAesGcm;
import static org.junit.jupiter.api.Assertions.*;
class CifradoSecretosTests {
    static MockEnvironment env(String active,String ids){
        return new MockEnvironment().withProperty("tic.secrets.active-key-id",active).withProperty("tic.secrets.key-ids",ids)
            .withProperty("TIC_SECRETS_KEY_ONE",Base64.getEncoder().encodeToString(new byte[32]))
            .withProperty("TIC_SECRETS_KEY_TWO",Base64.getEncoder().encodeToString("2".repeat(32).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static Vinculo link(){return new Vinculo(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,new DestinoPostgres("db.example.test",5432,"empresa","verify-full"));}
    static CredencialPostgres material(){return new CredencialPostgres("lectura_ñ".toCharArray(),"Clave sintética 🔒 2026".toCharArray());}
    @Test void idaVueltaUnicodeYNoncesDiferentes(){
        var crypto=new CifradoAesGcm(env("one","one"));var v=link();
        try(var m=material()){
            var a=crypto.cifrar(v,m);var b=crypto.cifrar(v,m);assertFalse(Arrays.equals(a.nonce(),b.nonce()));
            assertFalse(Arrays.equals(a.ciphertext(),b.ciphertext()));
            try(var opened=crypto.abrir(v,a)){assertArrayEquals(m.usuario(),opened.usuario());assertArrayEquals(m.clave(),opened.clave());}
            assertFalse(a.toString().contains("Clave"));assertFalse(m.toString().contains("lectura"));assertFalse(crypto.toString().contains("AAAA"));
        }
    }
    @Test void autenticacionVinculaIdentidadProyectoFuenteRevisionDestinoYClave(){
        var c=new CifradoAesGcm(env("one","one"));var v=link();
        try(var m=material()){
            var a=c.cifrar(v,m);
            var wrong=List.of(new Vinculo(UUID.randomUUID(),v.project(),v.source(),1,v.destino()),
                new Vinculo(v.id(),UUID.randomUUID(),v.source(),1,v.destino()),new Vinculo(v.id(),v.project(),UUID.randomUUID(),1,v.destino()),
                new Vinculo(v.id(),v.project(),v.source(),2,v.destino()),new Vinculo(v.id(),v.project(),v.source(),1,new DestinoPostgres("otro.test",5432,"empresa","verify-full")));
            for(var link:wrong)assertThrows(SecretosException.class,()->c.abrir(link,a));
            byte[] bytes=a.ciphertext();bytes[0]^=1;
            assertThrows(SecretosException.class,()->c.abrir(v,new Cifrado(a.keyId(),a.nonce(),bytes)));
            byte[] nonce=a.nonce();nonce[0]^=1;
            assertThrows(SecretosException.class,()->c.abrir(v,new Cifrado(a.keyId(),nonce,a.ciphertext())));
            assertThrows(SecretosException.class,()->c.abrir(v,new Cifrado("two",a.nonce(),a.ciphertext())));
        }
    }
    @Test void rotacionConservaLecturaAnteriorHastaRecifrarYFallaSiSeQuitaLaClaveVieja(){
        var old=new CifradoAesGcm(env("one","one"));var next=new CifradoAesGcm(env("two","one,two"));var onlyNew=new CifradoAesGcm(env("two","two"));var v=link();
        try(var m=material()){
            var a=old.cifrar(v,m);
            try(var plain=next.abrir(v,a)){var b=next.cifrar(v,plain);assertEquals("two",b.keyId());try(var again=onlyNew.abrir(v,b)){assertArrayEquals(m.clave(),again.clave());}}
            assertThrows(SecretosException.class,()->onlyNew.abrir(v,a));
        }
    }
    @Test void sinConfiguracionFallaCerradoYConfiguracionParcialFallaSinFiltrarValor(){
        var c=new CifradoAesGcm(new MockEnvironment());try(var m=material()){assertThrows(SecretosException.class,()->c.cifrar(link(),m));}
        var e=env("one","one").withProperty("TIC_SECRETS_KEY_ONE","valor-sintetico-privado");
        var ex=assertThrows(IllegalStateException.class,()->new CifradoAesGcm(e));
        assertNull(ex.getCause());assertFalse(ex.toString().contains("valor-sintetico"));
        assertThrows(IllegalStateException.class,()->new CifradoAesGcm(env("one","one,one")));
        assertThrows(IllegalStateException.class,()->new CifradoAesGcm(env("absent","one")));
    }
    @Test void cierreImpideReusarMaterialYCopiaDefensiva(){
        char[] u="user".toCharArray(),p="secret".toCharArray();var m=new CredencialPostgres(u,p);u[0]='x';p[0]='x';
        assertArrayEquals("user".toCharArray(),m.usuario());char[] copy=m.clave();copy[0]='x';assertEquals('s',m.clave()[0]);
        m.close();m.close();assertThrows(SecretosException.class,m::usuario);assertThrows(SecretosException.class,m::clave);
        assertThrows(SecretosException.class,()->new CredencialPostgres(new char[0],p));
    }
    @Test void destinoNoAceptaInyeccionDeUrlNiPuertoInvalido(){
        assertThrows(SecretosException.class,()->new DestinoPostgres("host?user=x",5432,"db","verify-full"));
        assertThrows(SecretosException.class,()->new DestinoPostgres("host",0,"db","verify-full"));
        assertThrows(SecretosException.class,()->new DestinoPostgres("host",5432,"db\nsecret","disable"));
        assertEquals("db.example.test",new DestinoPostgres("DB.Example.Test",5432,"db","verify-full").host());
    }
}
