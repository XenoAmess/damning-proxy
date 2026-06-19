package com.xenoamess.damning_proxy.plugin.storage;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@ApplicationScoped
public class ZipBuilder {

    public static byte[] buildZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipArchiveEntry zipEntry = new ZipArchiveEntry(entry.getKey());
                zipEntry.setSize(entry.getValue().length);
                zos.putArchiveEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeArchiveEntry();
            }
            zos.finish();
        }
        return baos.toByteArray();
    }

    public static byte[] textEntry(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static InputStream textStream(String text) {
        return new ByteArrayInputStream(textEntry(text));
    }
}
