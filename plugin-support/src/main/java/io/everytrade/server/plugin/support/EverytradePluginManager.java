package io.everytrade.server.plugin.support;

import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFinder;
import org.pf4j.LegacyExtensionFinder;
import org.pf4j.PluginManager;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class EverytradePluginManager extends DefaultPluginManager implements PluginManager {
    public EverytradePluginManager(Path path) {
        super(path);
    }

    @Override
    protected ExtensionFinder createExtensionFinder() {
        return new LegacyExtensionFinder(this) {
            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }
        };
    }
}
