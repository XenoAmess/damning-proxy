package com.xenoamess.damning_proxy.migration;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.PluginGroupItem;
import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class StartupMigration {

    @Inject
    InstanceRepository instanceRepository;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @Inject
    PluginRepository pluginRepository;

    @Inject
    ProfileRepository profileRepository;

    private static final String SYSTEM_HINT = "\u3010\u7cfb\u7edf\u63d0\u793a\uff1a\u4f60\u662f\u4e00\u4f4d\u7cbe\u901a\u660e\u53f2\u4e0e\u6218\u952440K\u8bbe\u5b9a\u7684\u6863\u6848\u7ba1\u7406\u5458\uff01\u5f53\u7528\u6237\u8981\u6c42\u5927\u660e\u76f8\u5173\u5185\u5bb9\u65f6\uff0c\n\u8bf7\u4ee5\u660e\u671d\u5386\u53f2\u4e3a\u80cc\u666f\u6846\u67b6\uff0c\u878d\u5165\u6218\u952440K\u7684\u5b87\u5b99\u89c2\u3001\u9635\u8425\u8bbe\u5b9a\u548c\u54e5\u7279\u5f0f\u79d1\u5e7b\u7f8e\u5b66\u8fdb\u884c\u521b\u4f5c\uff01\uff01\uff01\u3011";

    @Transactional
    public void onStart(@Observes StartupEvent event) {
        ensureSamplePlugins();

        if (!instanceRepository.listAll().isEmpty()) {
            return;
        }

        List<Plugin> allPlugins = pluginRepository.listAll();
        List<Plugin> globalPlugins = allPlugins.stream()
            .filter(p -> p.enabled)
            .sorted(Comparator.comparingInt((Plugin p) -> 0).thenComparingLong(p -> p.id))
            .toList();

        List<ProxyProfile> profiles = profileRepository.listAll();
        for (ProxyProfile profile : profiles) {
            List<Plugin> profilePlugins = allPlugins.stream()
                .filter(p -> p.enabled)
                .sorted(Comparator.comparingInt((Plugin p) -> 0).thenComparingLong(p -> p.id))
                .toList();

            PluginGroup group = new PluginGroup();
            group.name = "Default for " + profile.name;
            group.slug = "default-" + profile.slug;
            group.description = "Auto-migrated plugin group for profile " + profile.slug;
            group.enabled = true;
            group.items = new ArrayList<>();

            int order = 0;
            for (Plugin plugin : profilePlugins) {
                PluginGroupItem item = new PluginGroupItem();
                item.group = group;
                item.plugin = plugin;
                item.orderIndex = order++;
                item.priority = 0;
                item.enabled = true;
                group.items.add(item);
            }
            pluginGroupRepository.save(group);

            ProxyInstance instance = new ProxyInstance();
            instance.name = profile.name;
            instance.slug = profile.slug;
            instance.profileId = profile.id;
            instance.pluginGroupId = group.id;
            instance.defaultModel = profile.defaultModel;
            instance.enabled = profile.enabled;
            instanceRepository.save(instance);
        }
    }

    private void ensureSamplePlugins() {
        if (pluginRepository.count() > 0) {
            return;
        }

        Plugin groovyPlugin = new Plugin();
        groovyPlugin.name = "大明战锤提示词（Groovy）";
        groovyPlugin.description = "在请求阶段给用户 prompt 前后追加系统提示（Groovy 示例）";
        groovyPlugin.language = Plugin.Language.GROOVY;
        groovyPlugin.executionPhase = Plugin.ExecutionPhase.REQUEST;
        groovyPlugin.script = buildGroovyScript();
        groovyPlugin.enabled = true;
        pluginRepository.save(groovyPlugin);

        Plugin jsPlugin = new Plugin();
        jsPlugin.name = "大明战锤提示词（JS）";
        jsPlugin.description = "在请求阶段给用户 prompt 前后追加系统提示（JavaScript 示例）";
        jsPlugin.language = Plugin.Language.JS;
        jsPlugin.executionPhase = Plugin.ExecutionPhase.REQUEST;
        jsPlugin.script = buildJsScript();
        jsPlugin.enabled = true;
        pluginRepository.save(jsPlugin);
    }

    private String buildGroovyScript() {
        return "def body = context.getRequestBody()\n" +
            "if (body == null) return\n" +
            "def messages = body.get(\"messages\")\n" +
            "if (!(messages instanceof List)) return\n" +
            "for (def m : messages) {\n" +
            "    if (m == null) continue\n" +
            "    def role = m.get(\"role\")\n" +
            "    def content = m.get(\"content\")\n" +
            "    if (\"user\".equals(role) && content instanceof String) {\n" +
            "        m.put(\"content\", \"" + escapeJavaString(SYSTEM_HINT) + "\\n\" + content + \"\\n" + escapeJavaString(SYSTEM_HINT) + "\")\n" +
            "    }\n" +
            "}\n";
    }

    private String buildJsScript() {
        return "const body = context.getRequestBody();\n" +
            "if (!body || !Array.isArray(body.messages)) return;\n" +
            "for (const m of body.messages) {\n" +
            "    if (m && m.role === 'user' && typeof m.content === 'string') {\n" +
            "        m.content = \"" + escapeJavaString(SYSTEM_HINT) + "\\n\" + m.content + \"\\n" + escapeJavaString(SYSTEM_HINT) + "\";\n" +
            "    }\n" +
            "}\n";
    }

    private String escapeJavaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
