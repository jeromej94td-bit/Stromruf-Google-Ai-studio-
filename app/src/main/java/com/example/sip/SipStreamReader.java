package com.example.sip;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Byte framing for a persistent SIP TCP/TLS stream. Never use a character
 * reader for Content-Length: it counts octets, not Unicode characters. */
public final class SipStreamReader {
    public interface PingHandler { void reply() throws IOException; }
    private final InputStream input;
    private final PingHandler pingHandler;
    private static final int MAX_HEADERS = 65536;
    private static final int MAX_BODY = 1048576;

    public SipStreamReader(InputStream input, PingHandler pingHandler) {
        this.input = input;
        this.pingHandler = pingHandler;
    }

    public String readMessage() throws IOException {
        String first;
        int emptyLines = 0;
        while ((first = readLine()).isEmpty()) {
            // A single CRLF is a pong: do not echo it. A double CRLF
            // is a ping: reply immediately, even without a following SIP message.
            if (++emptyLines == 2) {
                pingHandler.reply();
                emptyLines = 0;
            }
        }
        StringBuilder headers = new StringBuilder(first).append("\r\n");
        Integer length = null;
        while (true) {
            String line = readLine();
            if (line.isEmpty()) break;
            headers.append(line).append("\r\n");
            if (headers.length() > MAX_HEADERS) throw new IOException("SIP headers too large");
            int separator = line.indexOf(':');
            if (separator > 0) {
                String name = line.substring(0, separator).trim();
                if (name.equalsIgnoreCase("Content-Length") || name.equalsIgnoreCase("l")) {
                    int parsed;
                    try {
                        parsed = Integer.parseInt(line.substring(separator + 1).trim());
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid SIP Content-Length", e);
                    }
                    if (parsed < 0 || parsed > MAX_BODY ||
                            (length != null && length != parsed)) {
                        throw new IOException("Invalid/conflicting SIP Content-Length");
                    }
                    length = parsed;
                }
            }
        }
        if (length == null) throw new IOException("Missing SIP stream Content-Length");
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < body.length) {
            int read = input.read(body, offset, body.length - offset);
            if (read < 0) throw new EOFException("SIP body truncated");
            if (read == 0) continue;
            offset += read;
        }
        return headers.append("\r\n").toString() + new String(body, StandardCharsets.UTF_8);
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) throw new EOFException("SIP stream closed");
            if (value == '\r') {
                if (input.read() != '\n') throw new IOException("Invalid SIP CRLF");
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
            if (value == '\n') throw new IOException("Bare LF in SIP headers");
            bytes.write(value);
            if (bytes.size() > MAX_HEADERS) throw new IOException("SIP line too large");
        }
    }
}
