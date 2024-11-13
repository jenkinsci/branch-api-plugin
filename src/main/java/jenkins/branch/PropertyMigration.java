package jenkins.branch;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.PluginWrapper;
import hudson.model.AdministrativeMonitor;
import hudson.model.UpdateSite;
import hudson.util.DescribableList;
import hudson.util.VersionNumber;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jvnet.localizer.Localizable;

/**
 * An extension point that captures the need for a complex migration of a folder property into some other configuration
 * of the containing folder.
 *
 * @param <F> the type of folder to which the migration is scoped.
 * @param <P> the type of property to which the migration applies.
 */
public abstract class PropertyMigration<F extends AbstractFolder<?>, P extends AbstractFolderProperty<F>>
        implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(PropertyMigration.class.getName());
    private final Class<F> folderClass;
    private final Class<P> propertyClass;
    private final String pluginName;
    private final String pluginVersion;

    public PropertyMigration(Class<F> folderClass, Class<P> propertyClass, String pluginName) {
        this.folderClass = folderClass;
        this.propertyClass = propertyClass;
        int index = pluginName.indexOf(':');
        this.pluginName = index == -1 ? pluginName : pluginName.substring(0, index);
        this.pluginVersion = index == -1 ? null : pluginName.substring(index + 1);
    }

    public final boolean isApplicable(AbstractFolder<?> folder) {
        return folderClass.isInstance(folder) && folder.getProperties().get(propertyClass) != null;
    }

    public final boolean canApply() {
        for (Migrator<?, ?> migrator : ExtensionList.lookup(Migrator.class)) {
            if (folderClass.equals(migrator.folderClass) && propertyClass.equals(migrator.propertyClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this {@link PropertyMigration} is enabled. If it is enabled, and a corresponding {@link Migrator}
     * extension is available, the migration will be applied automatically. If it is enabled, and no corresponding
     * {@link Migrator} extension is available, then an admin monitor will be shown suggested that the user install
     * or update a plugin that provides the migration. If it is disabled, then neither of these things will happen.
     * @return true if this {@link PropertyMigration} is enabled, false if it is disabled.
     */
    protected boolean isEnabled() {
        return true;
    }

    public final String getPluginName() {
        return pluginName;
    }

    public final String getPluginDisplayName() {
        UpdateSite.Plugin plugin = Jenkins.get().getUpdateCenter().getPlugin(pluginName);
        return plugin == null ? pluginName : plugin.getDisplayName();
    }

    public final String getPluginInstallId() {
        UpdateSite.Plugin plugin = Jenkins.get().getUpdateCenter().getPlugin(pluginName);
        if (pluginVersion != null) {
            VersionNumber versionNumber = plugin == null ? null : new VersionNumber(plugin.version);
            if (new VersionNumber(pluginVersion).isNewerThan(versionNumber)) {
                return null;
            }
        }
        return plugin == null ? null : "plugin." + pluginName + "." + plugin.sourceId;
    }

    public final String getPluginVersion() {
        return pluginVersion;
    }

    public final boolean isPendingRestart() {
        PluginWrapper plugin = Jenkins.get().getPluginManager().getPlugin(pluginName);
        return plugin != null && !plugin.isActive() && plugin.isEnabled();
    }

    public final boolean isPluginUpgrade() {
        PluginWrapper plugin = Jenkins.get().getPluginManager().getPlugin(pluginName);
        VersionNumber versionNumber = plugin == null ? null : plugin.getVersionNumber();
        return pluginVersion != null && versionNumber != null && new VersionNumber(pluginVersion)
                .isNewerThan(versionNumber);
    }

    public abstract Localizable getDescription();

    @SuppressWarnings("unchecked")
    public final void apply(AbstractFolder<?> folder) {
        if (!folderClass.isInstance(folder)) {
            throw new IllegalArgumentException();
        }
        F f = folderClass.cast(folder);
        P p = f.getProperties().get(propertyClass);
        if (p == null) {
            throw new IllegalArgumentException();
        }
        for (Migrator<?, ?> migrator : ExtensionList.lookup(Migrator.class)) {
            if (folderClass.equals(migrator.folderClass) && propertyClass.equals(migrator.propertyClass)) {
                ((Migrator<F, P>) migrator).apply(f, p);
                return;
            }
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PropertyMigration<?, ?> migration = (PropertyMigration<?, ?>) o;

        if (!folderClass.equals(migration.folderClass)) {
            return false;
        }
        if (!propertyClass.equals(migration.propertyClass)) {
            return false;
        }
        return pluginName.equals(migration.pluginName);
    }

    @Override
    public final int hashCode() {
        int result = folderClass.hashCode();
        result = 31 * result + propertyClass.hashCode();
        result = 31 * result + pluginName.hashCode();
        return result;
    }

    public static void applyAll(AbstractFolder<?> folder) {
        DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties =
                folder.getProperties();
        for (PropertyMigration<?, ?> migration : ExtensionList.lookup(PropertyMigration.class)) {
            if (migration.isEnabled() && migration.folderClass.isInstance(folder) && properties.get(migration.propertyClass) != null) {
                if (migration.canApply()) {
                    migration.apply(folder);
                } else {
                    MonitorImpl monitor = ExtensionList.lookup(AdministrativeMonitor.class).get(MonitorImpl.class);
                    if (monitor != null) {
                        monitor.add(migration);
                    } else {
                        LOGGER.log(Level.SEVERE, "{0} is loaded but no {1} singleton present.",
                                new Object[] {PropertyMigration.class, MonitorImpl.class});
                        // we didn't apply, so we can continue and let something else worry about the
                        // failure to load extensions.
                    }
                }
            }
        }
    }

    public static abstract class Migrator<F extends AbstractFolder<?>, P extends AbstractFolderProperty<F>>
            implements ExtensionPoint {
        private final Class<F> folderClass;
        private final Class<P> propertyClass;

        public Migrator(Class<F> folderClass, Class<P> propertyClass) {
            this.folderClass = folderClass;
            this.propertyClass = propertyClass;
        }

        public final boolean isApplicable(AbstractFolder<?> folder) {
            return folderClass.isInstance(folder) && folder.getProperties().get(propertyClass) != null;
        }

        public abstract void apply(F folder, P property);
    }

    @Extension
    public static class MonitorImpl extends AdministrativeMonitor {

        private Set<PropertyMigration<?, ?>> missing = new HashSet<>();

        @Override
        public boolean isActivated() {
            return !missing.isEmpty();
        }

        @Override
        public String getDisplayName() {
            return Messages.PropertyMigration_MonitorImpl_DisplayName();
        }

        public List<PropertyMigration<?, ?>> getPending() {
            List<PropertyMigration<?, ?>> result = new ArrayList<>(missing.size());
            for (PropertyMigration<?, ?> m : missing) {
                if (!m.canApply()) {
                    result.add(m);
                }
            }
            result.sort(Comparator.comparing(o -> o.getClass().getName())); // just want a deterministic sort
            return result;
        }

        public boolean isReady() {
            for (PropertyMigration<?, ?> m : missing) {
                if (!m.canApply()) {
                    return false;
                }
            }
            return true;
        }

        public synchronized void add(PropertyMigration<?, ?> migration) {
            missing.add(migration);
        }
    }
}
