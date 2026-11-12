import org.dataanaliticsent.Calidad.Docker.BackupCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class BackupCipherTest {
    @TempDir Path directory;
    private char[] password() { return "Isolated-Backup-Password-2026".toCharArray(); }

    @Test void multiblockBackupRestoresExactlyAndRejectsWrongPasswordTamperingAndTruncation() throws Exception {
        byte[] bytes = new byte[1048576 + 137]; new java.security.SecureRandom().nextBytes(bytes);
        Path original = directory.resolve("original"), encrypted = directory.resolve("encrypted");
        Files.write(original, bytes);
        BackupCipher.transform(original, encrypted, password(), true);
        Path restored = directory.resolve("restored");
        BackupCipher.transform(encrypted, restored, password(), false);
        assertArrayEquals(bytes, Files.readAllBytes(restored));
        assertRejected(encrypted, "Wrong-Backup-Password-2026".toCharArray());
        byte[] archive = Files.readAllBytes(encrypted);
        Path damaged = directory.resolve("damaged");
        byte[] tampered = archive.clone(); tampered[100] ^= 1; Files.write(damaged, tampered);
        assertRejected(damaged, password());
        Files.write(damaged, Arrays.copyOf(archive, archive.length - 20));
        assertRejected(damaged, password());
        byte[] appended = Arrays.copyOf(archive, archive.length + 1); Files.write(damaged, appended);
        assertRejected(damaged, password());
    }

    private void assertRejected(Path source, char[] password) {
        Path target = directory.resolve("must-not-exist");
        assertThrows(Exception.class, () -> BackupCipher.transform(source, target, password, false));
        assertFalse(Files.exists(target));
    }

    @Test void anExistingDestinationIsNeverOverwritten() throws Exception {
        Path file = Files.writeString(directory.resolve("data"), "conservar");
        assertThrows(IllegalArgumentException.class, () -> BackupCipher.transform(file, file, password(), true));
        assertEquals("conservar", Files.readString(file));
    }
}
