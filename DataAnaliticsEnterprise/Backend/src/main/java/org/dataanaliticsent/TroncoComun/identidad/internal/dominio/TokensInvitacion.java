package org.dataanaliticsent.TroncoComun.identidad.internal.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.dataanaliticsent.TroncoComun.identidad.CuentasException;

public final class TokensInvitacion {
    private static final SecureRandom RANDOM=new SecureRandom();
    private TokensInvitacion() { }
    public static String nuevo() {
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalida();
        byte[] decoded=Base64.getUrlDecoder().decode(token);
        if(decoded.length!=32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(token)) throw invalida();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static CuentasException invalida() { return new CuentasException(CuentasException.Tipo.INVITACION_INVALIDA); }
}
