package org.dataanaliticsent.Calidad.Docker;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Arrays;

/** Utilidad operativa: archivo autenticado por bloques, sin cargar el respaldo entero en memoria. */
public final class BackupCipher {
    private static final byte[] MAGIC = {'M', '2', 'B', 'A', 'K', '0', '0', '1'};
    private static final int BLOCK_SIZE = 1024 * 1024;

    private BackupCipher() {}

    public static void main(String[] args) {
        try {
            if (args.length != 3 || !(args[0].equals("encrypt") || args[0].equals("decrypt")))
                throw new IllegalArgumentException();
            String password = System.getenv("M2_BACKUP_PASSWORD");
            if (password == null || password.length() < 20) throw new IllegalArgumentException();
            transform(Path.of(args[1]), Path.of(args[2]), password.toCharArray(), args[0].equals("encrypt"));
        } catch (Exception failure) {
            System.err.println("BACKUP_FAILED: comprueba archivos, permisos, contraseña e integridad del respaldo.");
            System.exit(1);
        }
    }

    public static void transform(Path source, Path target, char[] password, boolean encrypt) throws Exception {
        if (password.length < 20 || Files.exists(target) || source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Destino o contraseña de respaldo inválidos.");
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".m2-backup-", ".pending");
        try {
            try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)));
                 var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                byte[] header;
                if (encrypt) {
                    header = new byte[28];
                    new SecureRandom().nextBytes(header);
                    System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
                    output.write(header);
                } else {
                    header = input.readNBytes(28);
                    if (header.length != 28 || !Arrays.equals(MAGIC, Arrays.copyOf(header, 8)))
                        throw new IOException("BACKUP_FORMAT_INVALID");
                }
                var spec = new PBEKeySpec(password, Arrays.copyOfRange(header, 8, 24), 600000, 256);
                byte[] derived;
                try { derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
                finally { spec.clearPassword(); }
                try {
                    var key = new SecretKeySpec(derived, "AES");
                    for (long index = 0; ; index++) {
                        byte[] plain = encrypt ? input.readNBytes(BLOCK_SIZE) : null;
                        int size = encrypt ? plain.length : input.readInt();
                        if (size < 0 || size > BLOCK_SIZE) throw new IOException("BACKUP_BLOCK_INVALID");
                        byte[] nonce = ByteBuffer.allocate(12).put(header, 24, 4).putLong(index).array();
                        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
                        cipher.updateAAD(ByteBuffer.allocate(40).put(header).putLong(index).putInt(size).array());
                        if (encrypt) {
                            output.writeInt(size);
                            output.write(cipher.doFinal(plain));
                        } else {
                            byte[] encrypted = input.readNBytes(size + 16);
                            if (encrypted.length != size + 16) throw new EOFException();
                            output.write(cipher.doFinal(encrypted));
                        }
                        if (size == 0) {
                            if (input.read() != -1) throw new IOException("BACKUP_TRAILING_DATA");
                            break;
                        }
                    }
                } finally { Arrays.fill(derived, (byte) 0); }
            }
            Files.move(temporary, target.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Arrays.fill(password, '\0');
            Files.deleteIfExists(temporary);
        }
    }
}
