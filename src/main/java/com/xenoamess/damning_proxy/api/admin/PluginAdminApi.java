package com.xenoamess.damning_proxy.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginScriptRevision;
import com.xenoamess.damning_proxy.plugin.PluginEngine;
import com.xenoamess.damning_proxy.plugin.storage.PluginPackageStorage;
import com.xenoamess.damning_proxy.plugin.storage.ZipBuilder;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import com.xenoamess.damning_proxy.repository.PluginScriptRevisionRepository;
import com.xenoamess.damning_proxy.util.BoundedInputStream;
import com.xenoamess.damning_proxy.util.Validation;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Path("/api/plugins")
@Produces(MediaType.APPLICATION_JSON)
public class PluginAdminApi {

    @Inject
    PluginRepository pluginRepository;

    @Inject
    PluginScriptRevisionRepository revisionRepository;

    @Inject
    PluginPackageStorage packageStorage;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    jakarta.enterprise.inject.Instance<PluginEngine> engines;

    @ConfigProperty(name = "damning-proxy.plugin.zip.max-size-bytes", defaultValue = "10485760")
    long maxPackageSizeBytes;

    @ConfigProperty(name = "damning-proxy.plugin.import.max-zip-size", defaultValue = "52428800")
    long maxImportZipSizeBytes;

    @ConfigProperty(name = "damning-proxy.plugin.import.max-entry-size", defaultValue = "10485760")
    long maxImportEntrySizeBytes;

    @ConfigProperty(name = "damning-proxy.plugin.import.max-entries", defaultValue = "100")
    int maxImportEntries;

    @GET
    public List<Plugin> list() {
        return pluginRepository.listAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return pluginRepository.findById(id)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response create(PluginForm form) {
        Validation.validateSlug(form.slug);
        Plugin plugin = toEntity(form);
        plugin.sample = false;
        String validationError = validatePlugin(plugin);
        if (validationError != null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(validationError).build();
        }
        pluginRepository.save(plugin);
        if (plugin.mode == Plugin.Mode.ZIP_PACKAGE && form.packageFile != null) {
            try {
                long size = Files.size(form.packageFile.uploadedFile());
                if (size > maxPackageSizeBytes) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Plugin package file too large: " + size + " bytes (max " + maxPackageSizeBytes + ")").build();
                }
                String stored = packageStorage.storePackage(plugin, Files.newInputStream(form.packageFile.uploadedFile()));
                plugin.packagePath = stored;
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
            } catch (IOException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to store plugin package: " + e.getMessage()).build();
            }
        }
        return Response.status(Response.Status.CREATED).entity(plugin).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response update(@PathParam("id") Long id, PluginForm form) {
        return pluginRepository.findById(id)
            .map(existing -> {
                Validation.validateSlug(form.slug);
                Plugin plugin = toEntity(form);
                plugin.id = id;
                plugin.sample = false;
                String validationError = validatePlugin(plugin);
                if (validationError != null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity(validationError).build();
                }
                if (plugin.mode == Plugin.Mode.ZIP_PACKAGE && form.packageFile != null) {
                    packageStorage.deletePackage(existing);
                    try {
                        long size = Files.size(form.packageFile.uploadedFile());
                        if (size > maxPackageSizeBytes) {
                            return Response.status(Response.Status.BAD_REQUEST)
                                .entity("Plugin package file too large: " + size + " bytes (max " + maxPackageSizeBytes + ")").build();
                        }
                        String stored = packageStorage.storePackage(plugin, Files.newInputStream(form.packageFile.uploadedFile()));
                        plugin.packagePath = stored;
                    } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
                    } catch (IOException e) {
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Failed to store plugin package: " + e.getMessage()).build();
                    }
                } else if (plugin.mode == Plugin.Mode.SINGLE_SCRIPT) {
                    packageStorage.deletePackage(existing);
                    plugin.packagePath = null;
                } else {
                    plugin.packagePath = existing.packagePath;
                }
                // Snapshot the current script before overwriting it.
                if (existing.script != null && !existing.script.isBlank()) {
                    revisionRepository.save(new PluginScriptRevision(id, existing.script));
                }
                pluginRepository.save(plugin);
                evictPluginCache(existing);
                return Response.ok(plugin).build();
            })
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        packageStorage.deletePackage(plugin);
        revisionRepository.deleteByPluginId(id);
        boolean deleted = pluginRepository.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/{id}/entries")
    public Response entries(@PathParam("id") Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            List<String> entries = packageStorage.listEntries(plugin);
            return Response.ok(entries).build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}/revisions")
    public Response listRevisions(@PathParam("id") Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(revisionRepository.findByPluginId(id)).build();
    }

    @POST
    @Path("/{id}/revisions/{revisionId}/rollback")
    @Transactional
    public Response rollback(@PathParam("id") Long id, @PathParam("revisionId") Long revisionId) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        PluginScriptRevision revision = revisionRepository.findById(revisionId).orElse(null);
        if (revision == null || !id.equals(revision.pluginId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // Snapshot the current script before rolling back, so users can undo the rollback.
        if (plugin.script != null && !plugin.script.isBlank()) {
            revisionRepository.save(new PluginScriptRevision(id, plugin.script));
        }
        plugin.script = revision.script;
        pluginRepository.save(plugin);
        evictPluginCache(plugin);
        return Response.ok(plugin).build();
    }

    private void evictPluginCache(Plugin plugin) {
        for (PluginEngine engine : engines) {
            if (engine.supports(plugin.language)) {
                engine.evictCache(plugin);
            }
        }
    }

    private String validatePlugin(Plugin plugin) {
        if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
            // ZIP packages are validated when their main script is read during execution.
            return null;
        }
        for (PluginEngine engine : engines) {
            if (engine.supports(plugin.language)) {
                return engine.validate(plugin);
            }
        }
        return "No engine for language: " + plugin.language;
    }

    @GET
    @Path("/template")
    @Produces("application/zip")
    public Response template(@QueryParam("language") String language, @QueryParam("mode") String mode) {
        Plugin.Language lang = language != null ? Plugin.Language.valueOf(language) : Plugin.Language.GROOVY;
        Plugin.Mode m = mode != null ? Plugin.Mode.valueOf(mode) : Plugin.Mode.SINGLE_SCRIPT;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (m == Plugin.Mode.ZIP_PACKAGE) {
            String mainName = lang == Plugin.Language.GROOVY ? "main.groovy" : "main.js";
            entries.put(mainName, buildTemplateScript(lang).getBytes(StandardCharsets.UTF_8));
            entries.put("assets/info.txt", "Put your assets here.".getBytes(StandardCharsets.UTF_8));
        } else {
            String mainName = lang == Plugin.Language.GROOVY ? "main.groovy" : "main.js";
            entries.put(mainName, buildTemplateScript(lang).getBytes(StandardCharsets.UTF_8));
        }
        try {
            byte[] zipBytes = ZipBuilder.buildZip(entries);
            return Response.ok(zipBytes)
                .header("Content-Disposition", "attachment; filename=\"plugin-template-" + lang.name().toLowerCase() + "-" + m.name().toLowerCase() + ".zip\"")
                .build();
        } catch (IOException e) {
            throw new WebApplicationException("Failed to build template zip: " + e.getMessage(),
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildTemplateScript(Plugin.Language lang) {
        if (lang == Plugin.Language.GROOVY) {
            return "def body = context.getRequestBody()\n" +
                "if (body == null) return\n" +
                "def messages = body.get(\"messages\")\n" +
                "if (!(messages instanceof List)) return\n" +
                "context.log(\"Groovy plugin executed, messages: \" + messages.size())\n";
        }
        return "const body = context.getRequestBody();\n" +
            "if (!body || !Array.isArray(body.messages)) return;\n" +
            "context.log(\"JS plugin executed, messages: \" + body.messages.length);\n";
    }

    @POST
    @Path("/export")
    @Produces("application/zip")
    public Response export(ExportRequest request) {
        List<Plugin> plugins;
        if (request != null && request.ids != null && !request.ids.isEmpty()) {
            plugins = request.ids.stream()
                .map(id -> pluginRepository.findById(id).orElse(null))
                .filter(p -> p != null)
                .collect(Collectors.toList());
        } else {
            plugins = pluginRepository.listAll();
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<ExportManifest> manifest = new ArrayList<>();

        for (Plugin p : plugins) {
            byte[] pluginZip = buildPluginZip(p);
            String zipName = "plugin-" + sanitize(p.slug) + ".zip";
            entries.put(zipName, pluginZip);
            manifest.add(new ExportManifest(p.name, p.slug, p.description, p.language.name(), p.executionPhase.name(), p.mode.name(), p.enabled, zipName));
        }

        entries.put("manifest.json", toJson(manifest).getBytes(StandardCharsets.UTF_8));

        try {
            byte[] zipBytes = ZipBuilder.buildZip(entries);
            return Response.ok(zipBytes)
                .header("Content-Disposition", "attachment; filename=\"damning_proxy_plugins_export.zip\"")
                .build();
        } catch (IOException e) {
            throw new WebApplicationException("Failed to build export zip: " + e.getMessage(),
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response importPluginsJson(List<ExportPlugin> plugins) {
        return doImportPlugins(plugins.stream().map(this::toManifest).collect(Collectors.toList()), null);
    }

    @POST
    @Path("/import")
    @Consumes("application/zip")
    @Transactional
    public Response importPluginsZip(InputStream zipInput) {
        Map<String, byte[]> pluginZips = new LinkedHashMap<>();
        List<ExportManifest> manifest = null;
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new BoundedInputStream(zipInput, maxImportZipSizeBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                entryCount++;
                if (entryCount > maxImportEntries) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Import zip has too many entries: " + entryCount + " (max " + maxImportEntries + ")").build();
                }
                byte[] content = readEntryBounded(zis, maxImportEntrySizeBytes, entry.getName());
                if ("manifest.json".equals(entry.getName())) {
                    manifest = objectMapper.readValue(content, objectMapper.getTypeFactory().constructCollectionType(List.class, ExportManifest.class));
                } else if (entry.getName().endsWith(".zip")) {
                    pluginZips.put(entry.getName(), content);
                }
            }
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Failed to read import zip: " + e.getMessage()).build();
        }
        if (manifest == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Import zip must contain manifest.json").build();
        }
        return doImportPlugins(manifest, pluginZips);
    }

    private byte[] readEntryBounded(ZipInputStream zis, long maxEntrySize, String entryName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = zis.read(buffer)) != -1) {
            total += read;
            if (total > maxEntrySize) {
                throw new IOException("Import zip entry too large: " + entryName + " (max " + maxEntrySize + " bytes)");
            }
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private Response doImportPlugins(List<ExportManifest> manifests, Map<String, byte[]> pluginZips) {
        if (manifests == null || manifests.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No plugins to import").build();
        }
        int imported = 0;
        int skipped = 0;
        for (ExportManifest m : manifests) {
            Validation.validateSlug(m.slug);
            if (pluginRepository.findBySlug(m.slug).isPresent()) {
                skipped++;
                continue;
            }
            Plugin plugin = new Plugin();
            plugin.name = m.name;
            plugin.slug = m.slug;
            plugin.description = m.description;
            plugin.language = Plugin.Language.valueOf(m.language);
            plugin.executionPhase = Plugin.ExecutionPhase.valueOf(m.executionPhase);
            plugin.mode = Plugin.Mode.valueOf(m.mode);
            plugin.enabled = m.enabled;
            plugin.sample = false;
            plugin.script = "";

            if (plugin.mode == Plugin.Mode.ZIP_PACKAGE && pluginZips != null) {
                byte[] zipBytes = pluginZips.get(m.packageFile);
                if (zipBytes != null) {
                    try {
                        String stored = packageStorage.storePackage(plugin, new ByteArrayInputStream(zipBytes));
                        plugin.packagePath = stored;
                    } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Invalid plugin package for " + m.slug + ": " + e.getMessage()).build();
                    } catch (IOException e) {
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Failed to store imported plugin package for " + m.slug + ": " + e.getMessage()).build();
                    }
                }
            }
            pluginRepository.save(plugin);
            imported++;
        }
        return Response.ok(new ImportResult(imported, skipped)).build();
    }

    private byte[] buildPluginZip(Plugin p) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (p.mode == Plugin.Mode.ZIP_PACKAGE && p.packagePath != null) {
            java.nio.file.Path path = packageStorage.getPackagePath(p);
            if (path != null && Files.exists(path)) {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    throw new WebApplicationException(
                        "Failed to read plugin package for export: " + e.getMessage(),
                        Response.Status.INTERNAL_SERVER_ERROR);
                }
            }
        }
        String entryName = p.language == Plugin.Language.GROOVY ? "main.groovy" : "main.js";
        entries.put(entryName, (p.script != null ? p.script : "").getBytes(StandardCharsets.UTF_8));
        try {
            return ZipBuilder.buildZip(entries);
        } catch (IOException e) {
            throw new WebApplicationException(
                "Failed to build plugin zip for export: " + e.getMessage(),
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Plugin toEntity(PluginForm form) {
        Plugin plugin = new Plugin();
        plugin.name = form.name;
        plugin.slug = form.slug;
        plugin.description = form.description;
        plugin.language = form.language != null ? Plugin.Language.valueOf(form.language) : Plugin.Language.GROOVY;
        plugin.executionPhase = form.executionPhase != null ? Plugin.ExecutionPhase.valueOf(form.executionPhase) : Plugin.ExecutionPhase.BOTH;
        plugin.mode = form.mode != null ? Plugin.Mode.valueOf(form.mode) : Plugin.Mode.SINGLE_SCRIPT;
        plugin.enabled = form.enabled != null ? form.enabled : true;
        plugin.script = form.script != null ? form.script : "";
        return plugin;
    }

    private ExportManifest toManifest(ExportPlugin ep) {
        return new ExportManifest(ep.name, ep.slug, ep.description, ep.language, ep.executionPhase, ep.mode != null ? ep.mode : Plugin.Mode.SINGLE_SCRIPT.name(), ep.enabled, null);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new WebApplicationException(
                "Failed to serialize export manifest: " + e.getMessage(),
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private String sanitize(String slug) {
        return slug.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public static class PluginForm {
        @jakarta.ws.rs.FormParam("name")
        public String name;
        @jakarta.ws.rs.FormParam("slug")
        public String slug;
        @jakarta.ws.rs.FormParam("description")
        public String description;
        @jakarta.ws.rs.FormParam("language")
        public String language;
        @jakarta.ws.rs.FormParam("executionPhase")
        public String executionPhase;
        @jakarta.ws.rs.FormParam("mode")
        public String mode;
        @jakarta.ws.rs.FormParam("enabled")
        public Boolean enabled;
        @jakarta.ws.rs.FormParam("script")
        public String script;
        @org.jboss.resteasy.reactive.PartType(MediaType.APPLICATION_OCTET_STREAM)
        @jakarta.ws.rs.FormParam("packageFile")
        public FileUpload packageFile;
    }

    public record ExportRequest(List<Long> ids) {
    }

    public record ExportPlugin(String name, String slug, String description, String language, String executionPhase, String mode, String script, boolean enabled) {
    }

    public record ExportManifest(String name, String slug, String description, String language, String executionPhase, String mode, boolean enabled, String packageFile) {
    }

    public record ImportResult(int imported, int skipped) {
    }
}
