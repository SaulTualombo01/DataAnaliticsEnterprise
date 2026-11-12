import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Receptor SMTP de pruebas. Solo escucha en loopback y nunca reenvía mensajes. */
final class LocalSmtpInbox implements AutoCloseable {
    record Message(String recipient, String text) {}
    private final ServerSocket server;
    private final ExecutorService listener = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    private final AtomicBoolean reject = new AtomicBoolean();
    private volatile Consumer<Message> observer = message -> {};

    LocalSmtpInbox() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        listener.submit(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    receive(socket);
                } catch (Exception failure) {
                    if (!server.isClosed()) messages.offer(new Message("", "SMTP_TEST_FAILED"));
                }
            }
        });
    }

    int port() { return server.getLocalPort(); }
    void rejectNext() { reject.set(true); }
    void observe(Consumer<Message> callback) { observer = callback; }
    void reset() { messages.clear(); reject.set(false); observer = message -> {}; }
    Message next(Duration timeout) throws InterruptedException { return messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS); }

    private void receive(Socket socket) throws Exception {
        socket.setSoTimeout(10000);
        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        reply(output, "220 localhost SMTP test");
        String command;
        while ((command = input.readLine()) != null) {
            if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                reply(output, "250 localhost");
            } else if (command.equals("DATA")) {
                if (reject.getAndSet(false)) {
                    reply(output, "451 Temporary test failure");
                    continue;
                }
                reply(output, "354 End with a single dot");
                StringBuilder raw = new StringBuilder();
                String line;
                while ((line = input.readLine()) != null && !line.equals("."))
                    raw.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                MimeMessage mail = new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(raw.toString().getBytes(StandardCharsets.US_ASCII)));
                Message message = new Message(mail.getAllRecipients()[0].toString(), (String) mail.getContent());
                observer.accept(message);
                messages.add(message);
                reply(output, "250 Accepted");
            } else if (command.equals("QUIT")) {
                reply(output, "221 Goodbye");
                return;
            } else if (command.startsWith("STARTTLS")) {
                reply(output, "454 TLS unavailable in test fixture");
            } else {
                reply(output, "250 OK");
            }
        }
    }

    private static void reply(BufferedWriter output, String line) throws IOException {
        output.write(line + "\r\n");
        output.flush();
    }

    @Override public void close() throws IOException {
        server.close();
        listener.shutdownNow();
    }
}
