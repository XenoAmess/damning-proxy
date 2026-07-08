package com.xenoamess.damning_proxy.plugin.storage;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public final class ZipBuilder {

    private ZipBuilder() {
    }

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
}
