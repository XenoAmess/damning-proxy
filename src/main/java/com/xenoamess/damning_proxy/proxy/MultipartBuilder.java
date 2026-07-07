package com.xenoamess.damning_proxy.proxy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultipartBuilder {

    private static final String BOUNDARY = "----DamningProxyBoundary" + UUID.randomUUID();
    private static final String CRLF = "\r\n";

    private final List<Field> fields = new ArrayList<>();
    private final List<File> files = new ArrayList<>();

    public MultipartBuilder field(String name, String value) {
        if (name != null && value != null) {
            fields.add(new Field(name, value));
        }
        return this;
    }

    public MultipartBuilder file(String name, String fileName, String contentType, byte[] data) {
        if (name != null && data != null) {
            files.add(new File(name, fileName != null ? fileName : "audio", contentType != null ? contentType : "application/octet-stream", data));
        }
        return this;
    }

    public byte[] build() {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        for (Field field : fields) {
            write(baos, "--" + BOUNDARY + CRLF);
            write(baos, "Content-Disposition: form-data; name=\"" + field.name + "\"" + CRLF);
            write(baos, CRLF);
            write(baos, field.value);
            write(baos, CRLF);
        }
        for (File file : files) {
            write(baos, "--" + BOUNDARY + CRLF);
            write(baos, "Content-Disposition: form-data; name=\"" + file.name + "\"; filename=\"" + file.fileName + "\"" + CRLF);
            write(baos, "Content-Type: " + file.contentType + CRLF);
            write(baos, CRLF);
            try {
                baos.write(file.data);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to write multipart file", e);
            }
            write(baos, CRLF);
        }
        write(baos, "--" + BOUNDARY + "--" + CRLF);
        return baos.toByteArray();
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + BOUNDARY;
    }

    private static void write(java.io.ByteArrayOutputStream baos, String value) {
        try {
            baos.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to write multipart data", e);
        }
    }

    private record Field(String name, String value) {
    }

    private record File(String name, String fileName, String contentType, byte[] data) {
    }
}
