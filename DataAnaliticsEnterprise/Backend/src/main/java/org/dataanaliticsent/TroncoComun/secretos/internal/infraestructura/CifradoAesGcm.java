package org.dataanaliticsent.TroncoComun.secretos.internal.infraestructura;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.dataanaliticsent.TroncoComun.secretos.*;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.CifradorSecretos;
import org.dataanaliticsent.TroncoComun.secretos.internal.dominio.RepositorioSecretos.Cifrado;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
public final class CifradoAesGcm implements CifradorSecretos {
    private final Map<String,SecretKeySpec> keys=new HashMap<>();
    private final String active;
    private final SecureRandom random=new SecureRandom();
    public CifradoAesGcm(Environment environment) {
        String ids=environment.getProperty("tic.secrets.key-ids","");
        active=environment.getProperty("tic.secrets.active-key-id","");
        if(ids.isEmpty()&&active.isEmpty())return;
        try {
            String[] list=ids.split(",",-1);
            if(list.length<1||list.length>8)throw new IllegalArgumentException();
            for(String id:list) {
                if(!id.matches("[a-z][a-z0-9_]{0,31}")||keys.containsKey(id))throw new IllegalArgumentException();
                String value=environment.getProperty("TIC_SECRETS_KEY_"+id.toUpperCase(Locale.ROOT));
                byte[] key=Base64.getDecoder().decode(value==null?"":value);
                try {
                    if(key.length!=32)throw new IllegalArgumentException();
                    keys.put(id,new SecretKeySpec(key,"AES"));
                } finally { Arrays.fill(key,(byte)0); }
            }
            if(!keys.containsKey(active))throw new IllegalArgumentException();
        } catch(RuntimeException e) { throw new IllegalStateException("Configuracion de claves TC11 invalida"); }
    }
    private static SecretosException unavailable(){return new SecretosException(SecretosException.Codigo.CIFRADO_NO_DISPONIBLE);}
    public Cifrado cifrar(Vinculo v,CredencialPostgres material) {
        SecretKeySpec key=keys.get(active);if(key==null)throw unavailable();
        byte[] plain=serializar(material);
        try {
            byte[] nonce=new byte[12];random.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(v,active));
            return new Cifrado(active,nonce,cipher.doFinal(plain));
        } catch(GeneralSecurityException e) { throw unavailable(); }
        finally {Arrays.fill(plain,(byte)0);}
    }
    public CredencialPostgres abrir(Vinculo v,Cifrado encrypted) {
        if(encrypted==null)throw unavailable();
        SecretKeySpec key=keys.get(encrypted.keyId());if(key==null)throw unavailable();
        byte[] plain=null;
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,encrypted.nonce()));
            cipher.updateAAD(aad(v,encrypted.keyId()));plain=cipher.doFinal(encrypted.ciphertext());
            return deserializar(plain);
        } catch(GeneralSecurityException|RuntimeException e) { throw unavailable(); }
        finally {if(plain!=null)Arrays.fill(plain,(byte)0);}
    }
    private static byte[] aad(Vinculo v,String key) {
        try {
            var stream=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(stream)) {
                out.writeUTF("tic-secret-postgres-v1");out.writeUTF(v.id().toString());out.writeUTF(v.project().toString());
                out.writeUTF(v.source().toString());out.writeLong(v.revision());out.writeUTF(key);
                out.writeUTF(v.destino().host());out.writeInt(v.destino().port());out.writeUTF(v.destino().database());out.writeUTF(v.destino().sslmode());
            }
            return stream.toByteArray();
        } catch(IOException e){throw unavailable();}
    }
    private static byte[] serializar(CredencialPostgres material) {
        char[] user=material.usuario(),password=material.clave();
        try {
            ByteBuffer buffer=ByteBuffer.allocate(8+2*(user.length+password.length));
            buffer.putInt(user.length).putInt(password.length);
            for(char c:user)buffer.putChar(c);for(char c:password)buffer.putChar(c);
            return buffer.array();
        } finally {Arrays.fill(user,'\0');Arrays.fill(password,'\0');}
    }
    private static CredencialPostgres deserializar(byte[] plain) {
        ByteBuffer buffer=ByteBuffer.wrap(plain);
        if(buffer.remaining()<8)throw unavailable();
        int u=buffer.getInt(),p=buffer.getInt();
        if(u<1||u>256||p<1||p>4096||buffer.remaining()!=2*(u+p))throw unavailable();
        char[] user=new char[u],password=new char[p];
        try {
            for(int i=0;i<u;i++)user[i]=buffer.getChar();for(int i=0;i<p;i++)password[i]=buffer.getChar();
            return new CredencialPostgres(user,password);
        } finally {Arrays.fill(user,'\0');Arrays.fill(password,'\0');}
    }
    @Override public String toString(){return "CifradoAesGcm[protegido]";}
}
