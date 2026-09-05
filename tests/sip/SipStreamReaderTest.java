import com.example.sip.SipStreamReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class SipStreamReaderTest {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static String message(String body) {
        return "SIP/2.0 200 OK\r\nContent-Length: " +
            body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
    }
    static SipStreamReader reader(String wire, AtomicInteger pings) {
        // Fragment every read to simulate arbitrary TCP packet boundaries.
        InputStream in = new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)) {
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(1, len));
            }
        };
        return new SipStreamReader(in, () -> pings.incrementAndGet());
    }
    static void rejects(String wire) throws Exception {
        try { reader(wire, new AtomicInteger()).readMessage(); throw new AssertionError(); }
        catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger pings = new AtomicInteger();
        String first = message("s=Gespräch äöü\r\n");
        String second = message("");
        SipStreamReader r = reader("\r\n\r\n" + first + "\r\n" + second, pings);
        check(r.readMessage().equals(first));
        check(pings.get() == 1);
        check(r.readMessage().equals(second));
        check(pings.get() == 1); // pong must not be echoed
        SipStreamReader onlyPing = reader("\r\n\r\n", pings);
        try { onlyPing.readMessage(); } catch (EOFException expected) { }
        check(pings.get() == 2); // replied without waiting for a SIP message
        check(reader("SIP/2.0 200 OK\r\nl: 0\r\n\r\n", pings)
            .readMessage().endsWith("\r\n\r\n"));
        rejects("SIP/2.0 200 OK\r\nContent-Length: 4\r\n\r\nab");
        rejects("SIP/2.0 200 OK\r\nContent-Length: -1\r\n\r\n");
        rejects("SIP/2.0 200 OK\r\nContent-Length: 1\r\nl: 2\r\n\r\na");
        rejects("SIP/2.0 200 OK\r\nContent-Length: 999999999\r\n\r\n");
        rejects("SIP/2.0 200 OK\r\n");
        rejects("SIP/2.0 200 OK\r\n\r\n");
        System.out.println("PASS: UTF-8 byte framing, fragmented/coalesced messages, ping/pong, compact length, EOF and invalid lengths");
    }
}
