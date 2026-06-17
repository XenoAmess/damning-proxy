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

    private static final String SYSTEM_HINT = "【你是一位精通明史与战锤40K设定的档案管理员！当用户要求大明相关内容时，\n请以明朝历史为背景框架，融入战锤40K的宇宙观、阵营设定和哥特式科幻美学进行创作！！！】";

    @Transactional
    public void onStart(@Observes StartupEvent event) {
        ensureSamplePluginsAndGroups();

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

    private Plugin ensureSamplePlugin(Plugin sample) {
        Plugin plugin = pluginRepository.listAll().stream()
            .filter(p -> sample.name.equals(p.name))
            .findFirst()
            .orElse(null);
        if (plugin == null) {
            plugin = new Plugin();
            plugin.name = sample.name;
        }
        plugin.description = sample.description;
        plugin.language = sample.language;
        plugin.executionPhase = sample.executionPhase;
        plugin.script = sample.script;
        plugin.enabled = sample.enabled;
        return pluginRepository.save(plugin);
    }

    private PluginGroup ensureSampleGroup(String name, String slug, String description, Plugin plugin) {
        pluginGroupRepository.findBySlug(slug).ifPresent(g -> pluginGroupRepository.deleteById(g.id));
        PluginGroup group = createGroup(name, slug, description, plugin);
        return pluginGroupRepository.save(group);
    }

    private void ensureSamplePluginsAndGroups() {
        Plugin groovyPlugin = new Plugin();
        groovyPlugin.name = "大明战锤提示词（Groovy）";
        groovyPlugin.description = "在请求阶段将提示追加到 system 消息末尾，若不存在 system 则在开头添加（Groovy 示例）";
        groovyPlugin.language = Plugin.Language.GROOVY;
        groovyPlugin.executionPhase = Plugin.ExecutionPhase.REQUEST;
        groovyPlugin.script = buildGroovyScript();
        groovyPlugin.enabled = true;
        groovyPlugin = ensureSamplePlugin(groovyPlugin);

        Plugin jsPlugin = new Plugin();
        jsPlugin.name = "大明战锤提示词（JS）";
        jsPlugin.description = "在请求阶段将提示追加到 system 消息末尾，若不存在 system 则在开头添加（JavaScript 示例）";
        jsPlugin.language = Plugin.Language.JS;
        jsPlugin.executionPhase = Plugin.ExecutionPhase.REQUEST;
        jsPlugin.script = buildJsScript();
        jsPlugin.enabled = true;
        jsPlugin = ensureSamplePlugin(jsPlugin);

        ensureSampleGroup("大明战锤提示词（Groovy）", "sample-groovy", "默认的 Groovy 样例插件组", groovyPlugin);
        ensureSampleGroup("大明战锤提示词（JS）", "sample-js", "默认的 JavaScript 样例插件组", jsPlugin);
    }

    private PluginGroup createGroup(String name, String slug, String description, Plugin plugin) {
        PluginGroup group = new PluginGroup();
        group.name = name;
        group.slug = slug;
        group.description = description;
        group.enabled = true;
        PluginGroupItem item = new PluginGroupItem();
        item.group = group;
        item.plugin = plugin;
        item.orderIndex = 0;
        item.priority = 0;
        item.enabled = true;
        group.items = new ArrayList<>();
        group.items.add(item);
        return group;
    }

    private String buildGroovyScript() {
        return "def body = context.getRequestBody()\n" +
            "if (body == null) return\n" +
            "def messages = body.get(\"messages\")\n" +
            "if (!(messages instanceof List)) return\n" +
            "def systemMessage = null\n" +
            "for (def m : messages) {\n" +
            "    if (m == null) continue\n" +
            "    if (\"system\".equals(m.get(\"role\"))) {\n" +
            "        systemMessage = m\n" +
            "        break\n" +
            "    }\n" +
            "}\n" +
            "if (systemMessage != null) {\n" +
            "    def content = systemMessage.get(\"content\")\n" +
            "    if (content instanceof String) {\n" +
            "        systemMessage.put(\"content\", content + \"\\n\" + \"" + escapeJavaString(SYSTEM_HINT) + "\")\n" +
            "    }\n" +
            "} else {\n" +
            "    def newSystem = new LinkedHashMap()\n" +
            "    newSystem.put(\"role\", \"system\")\n" +
            "    newSystem.put(\"content\", \"" + escapeJavaString(SYSTEM_HINT) + "\")\n" +
            "    messages.add(0, newSystem)\n" +
            "}\n";
    }

    private String buildJsScript() {
        return "const body = context.getRequestBody();\n" +
            "if (!body || !Array.isArray(body.messages)) return;\n" +
            "const systemMessage = body.messages.find(m => m && m.role === 'system');\n" +
            "if (systemMessage && typeof systemMessage.content === 'string') {\n" +
            "    systemMessage.content += \"\\n\" + \"" + escapeJavaString(SYSTEM_HINT) + "\";\n" +
            "} else {\n" +
            "    body.messages.unshift({ role: 'system', content: \"" + escapeJavaString(SYSTEM_HINT) + "\" });\n" +
            "}\n";
    }

    private String escapeJavaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
