/*
 * Copyright 2014-2025 TNG Technology Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tngtech.archunit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.tngtech.archunit.base.Suppliers;
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.tngtech.archunit.PublicAPI.Usage.ACCESS;
import static com.tngtech.archunit.base.ClassLoaders.getCurrentClassLoader;

/**
 * Allows access to configured properties in {@value ARCHUNIT_PROPERTIES_RESOURCE_NAME}.
 */
@PublicAPI(usage = ACCESS)
public final class ArchConfiguration {
    @Internal // {@value ...} does not work on non public constants outside of the package
    public static final String ARCHUNIT_PROPERTIES_RESOURCE_NAME = "archunit.properties";
    @Internal // {@value ...} does not work on non public constants outside of the package
    public static final String RESOLVE_MISSING_DEPENDENCIES_FROM_CLASS_PATH = "resolveMissingDependenciesFromClassPath";
    static final String CLASS_RESOLVER = "classResolver";
    static final String CLASS_RESOLVER_ARGS = "classResolver.args";
    @Internal
    public static final String ENABLE_MD5_IN_CLASS_SOURCES = "enableMd5InClassSources";
    private static final String EXTENSION_PREFIX = "extension";

    private static final Logger LOG = LoggerFactory.getLogger(ArchConfiguration.class);

    private static final Supplier<ArchConfiguration> INSTANCE = Suppliers.memoize(ArchConfiguration::new);
    private static final ThreadLocal<ArchConfiguration> threadLocalConfiguration = new ThreadLocal<>();

    @PublicAPI(usage = ACCESS)
    public static ArchConfiguration get() {
        return threadLocalConfiguration.get() != null ? threadLocalConfiguration.get() : INSTANCE.get();
    }

    private final String propertiesResourceName;
    private PropertiesOverwritableBySystemProperties properties;

    private ArchConfiguration() {
        this(ARCHUNIT_PROPERTIES_RESOURCE_NAME);
    }

    private ArchConfiguration(String propertiesResourceName) {
        this(propertiesResourceName, readProperties(propertiesResourceName));
    }

    private ArchConfiguration(String propertiesResourceName, PropertiesOverwritableBySystemProperties properties) {
        this.propertiesResourceName = propertiesResourceName;
        this.properties = properties;
    }

    private static PropertiesOverwritableBySystemProperties readProperties(String propertiesResourceName) {
        PropertiesOverwritableBySystemProperties properties = new PropertiesOverwritableBySystemProperties();

        URL archUnitPropertiesUrl = getCurrentClassLoader(ArchConfiguration.class).getResource(propertiesResourceName);
        if (archUnitPropertiesUrl == null) {
            LOG.debug("No configuration found in classpath at {} => Using default configuration", propertiesResourceName);
            return properties;
        }

        try (InputStream inputStream = archUnitPropertiesUrl.openStream()) {
            LOG.info("Reading ArchUnit properties from {}", archUnitPropertiesUrl);
            properties.load(inputStream);
        } catch (IOException e) {
            LOG.warn("Error reading ArchUnit properties from " + archUnitPropertiesUrl, e);
        }
        return properties;
    }

    @PublicAPI(usage = ACCESS)
    public void reset() {
        properties = readProperties(propertiesResourceName);
    }

    @PublicAPI(usage = ACCESS)
    public boolean resolveMissingDependenciesFromClassPath() {
        return Boolean.parseBoolean(properties.getProperty(RESOLVE_MISSING_DEPENDENCIES_FROM_CLASS_PATH));
    }

    @PublicAPI(usage = ACCESS)
    public void setResolveMissingDependenciesFromClassPath(boolean newValue) {
        properties.setProperty(RESOLVE_MISSING_DEPENDENCIES_FROM_CLASS_PATH, String.valueOf(newValue));
    }

    @PublicAPI(usage = ACCESS)
    public boolean md5InClassSourcesEnabled() {
        return Boolean.parseBoolean(properties.getProperty(ENABLE_MD5_IN_CLASS_SOURCES));
    }

    @PublicAPI(usage = ACCESS)
    public void setMd5InClassSourcesEnabled(boolean enabled) {
        properties.setProperty(ENABLE_MD5_IN_CLASS_SOURCES, String.valueOf(enabled));
    }

    @PublicAPI(usage = ACCESS)
    public Optional<String> getClassResolver() {
        return Optional.ofNullable(properties.getProperty(CLASS_RESOLVER));
    }

    @PublicAPI(usage = ACCESS)
    public void setClassResolver(Class<? extends ClassResolver> classResolver) {
        properties.setProperty(CLASS_RESOLVER, classResolver.getName());
    }

    @PublicAPI(usage = ACCESS)
    public void unsetClassResolver() {
        properties.remove(CLASS_RESOLVER);
    }

    @PublicAPI(usage = ACCESS)
    public List<String> getClassResolverArguments() {
        return Splitter.on(",").trimResults().omitEmptyStrings()
                .splitToList(properties.getProperty(CLASS_RESOLVER_ARGS, ""));
    }

    @PublicAPI(usage = ACCESS)
    public void setClassResolverArguments(String... args) {
        properties.setProperty(CLASS_RESOLVER_ARGS, Joiner.on(",").join(args));
    }

    @PublicAPI(usage = ACCESS)
    public void setExtensionProperties(String extensionIdentifier, Properties properties) {
        String propertyPrefix = getFullExtensionPropertyPrefix(extensionIdentifier);
        clearPropertiesWithPrefix(propertyPrefix);
        for (String propertyName : properties.stringPropertyNames()) {
            String fullPropertyName = propertyPrefix + "." + propertyName;
            this.properties.setProperty(fullPropertyName, properties.getProperty(propertyName));
        }
    }

    private void clearPropertiesWithPrefix(String propertyPrefix) {
        for (String propertyToRemove : filterNamesWithPrefix(properties.stringPropertyNames(), propertyPrefix)) {
            properties.remove(propertyToRemove);
        }
    }

    @PublicAPI(usage = ACCESS)
    public Properties getExtensionProperties(String extensionIdentifier) {
        String propertyPrefix = getFullExtensionPropertyPrefix(extensionIdentifier);
        return getSubProperties(propertyPrefix);
    }

    private String getFullExtensionPropertyPrefix(String extensionIdentifier) {
        return EXTENSION_PREFIX + "." + extensionIdentifier;
    }

    @PublicAPI(usage = ACCESS)
    public ExtensionProperties configureExtension(String extensionIdentifier) {
        return new ExtensionProperties(extensionIdentifier);
    }

    /**
     * Returns a set of properties where all keys share a common prefix. The prefix is removed from those property names. Example:
     * <pre><code>
     * some.custom.prop1=value1
     * some.custom.prop2=value2
     * unrelated=irrelevant</code></pre>
     * Then {@code getSubProperties("some.custom")} would return the properties
     * <pre><code>
     * prop1=value1
     * prop2=value2</code></pre>
     *
     * @param propertyPrefix A prefix for a set of properties
     * @return All properties with this prefix, where the prefix is removed from the keys.
     */
    @PublicAPI(usage = ACCESS)
    public Properties getSubProperties(String propertyPrefix) {
        return getSubProperties(propertyPrefix, properties.getMergedProperties());
    }

    private static Properties getSubProperties(String propertyPrefix, Properties properties) {
        Properties result = new Properties();
        for (String key : filterNamesWithPrefix(properties.stringPropertyNames(), propertyPrefix)) {
            String extensionPropertyKey = removePrefix(key, propertyPrefix);
            result.put(extensionPropertyKey, properties.getProperty(key));
        }
        return result;
    }

    private static Iterable<String> filterNamesWithPrefix(Iterable<String> propertyNames, String prefix) {
        List<String> result = new ArrayList<>();
        String fullPrefix = prefix + ".";
        for (String propertyName : propertyNames) {
            if (propertyName.startsWith(fullPrefix)) {
                result.add(propertyName);
            }
        }
        return result;
    }

    private static String removePrefix(String string, String prefix) {
        return string.substring(prefix.length() + 1);
    }

    /**
     * @param propertyName Full name of a property
     * @return true, if and only if the property is configured within the global ArchUnit configuration.
     * @see #getProperty(String)
     * @see #setProperty(String, String)
     */
    @PublicAPI(usage = ACCESS)
    public boolean containsProperty(String propertyName) {
        return properties.containsKey(propertyName);
    }

    /**
     * @param propertyName Full name of a property
     * @return A property of the global ArchUnit configuration. This method will throw an exception if the property is not set within the configuration.
     * @see #containsProperty(String)
     * @see #setProperty(String, String)
     */
    @PublicAPI(usage = ACCESS)
    public String getProperty(String propertyName) {
        return checkNotNull(properties.getProperty(propertyName), "Property '%s' is not configured", propertyName);
    }

    /**
     * Overwrites a property of the global ArchUnit configuration. Note that this change will persist for the whole life time of this JVM
     * unless overwritten another time.
     *
     * @param propertyName Full name of a property
     * @param value The new value to set. Overwrites any existing property with the same name.
     * @see #containsProperty(String)
     * @see #getProperty(String)
     */
    @PublicAPI(usage = ACCESS)
    public void setProperty(String propertyName, String value) {
        properties.setProperty(propertyName, value);
    }

    /**
     * @param propertyName Full name of a property
     * @param defaultValue A value to return if property is not configured
     * @return The property of the global ArchUnit configuration with the supplied name
     *         or {@code defaultValue} if this property is not configured.
     */
    @PublicAPI(usage = ACCESS)
    public String getPropertyOrDefault(String propertyName, String defaultValue) {
        return properties.getProperty(propertyName, defaultValue);
    }

    /**
     * Same as {@link #withThreadLocalScope(Function)} but does not return a value.
     */
    @PublicAPI(usage = ACCESS)
    public static void withThreadLocalScope(Consumer<ArchConfiguration> doWithThreadLocalConfiguration) {
        withThreadLocalScope(configuration -> {
            doWithThreadLocalConfiguration.accept(configuration);
            return null;
        });
    }

    /**
     * Sets up a thread local copy of the current {@link ArchConfiguration} to be freely modified.
     * Within the current thread and the scope of {@code doWithThreadLocalConfiguration}
     * {@link ArchConfiguration#get() ArchConfiguration.get()} will return the thread local configuration,
     * i.e. adjustments to the configuration passed to {@code doWithThreadLocalConfiguration} will be
     * picked up by ArchUnit while executing from this thread within the scope of
     * {@code doWithThreadLocalConfiguration}.<br><br>
     * For example:
     *
     * <pre><code>
     * ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(true);
     *
     * JavaClasses classesWithoutResolvingFromClasspath =
     *   ArchConfiguration.withThreadLocalScope((ArchConfiguration configuration) -> {
     *     configuration.setResolveMissingDependenciesFromClassPath(false);
     *     return new ClassFileImporter().importPackages(..) // will now not resolve from classpath
     *   });
     *
     * JavaClasses classesWithResolvingFromClasspath =
     *   new ClassFileImporter().importPackages(..) // will now see the original value and resolve from classpath
     * </code></pre>
     *
     * @param doWithThreadLocalConfiguration A lambda that allows to execute code that will see the thread
     *                                       local {@link ArchConfiguration} instead of the global one. Once
     *                                       the lambda has been executed the thread local configuration
     *                                       is cleaned up and all threads will see the global configuration
     *                                       again.
     */
    @PublicAPI(usage = ACCESS)
    public static <T> T withThreadLocalScope(Function<ArchConfiguration, T> doWithThreadLocalConfiguration) {
        ArchConfiguration configuration = INSTANCE.get().copy();
        ArchConfiguration.threadLocalConfiguration.set(configuration);
        try {
            return doWithThreadLocalConfiguration.apply(configuration);
        } finally {
            ArchConfiguration.threadLocalConfiguration.set(null);
        }
    }

    private ArchConfiguration copy() {
        return new ArchConfiguration(propertiesResourceName, properties.copy());
    }

    private static class PropertiesOverwritableBySystemProperties {
        private static final Properties PROPERTY_DEFAULTS = createProperties(ImmutableMap.of(
                RESOLVE_MISSING_DEPENDENCIES_FROM_CLASS_PATH, Boolean.TRUE.toString(),
                ENABLE_MD5_IN_CLASS_SOURCES, Boolean.FALSE.toString()
        ));

        private final Properties baseProperties;
        private final Properties overwrittenProperties;

        PropertiesOverwritableBySystemProperties() {
            this(createProperties(PROPERTY_DEFAULTS), new Properties());
        }

        PropertiesOverwritableBySystemProperties(Properties baseProperties, Properties overwrittenProperties) {
            this.baseProperties = baseProperties;
            this.overwrittenProperties = overwrittenProperties;
        }

        void load(InputStream inputStream) throws IOException {
            baseProperties.load(inputStream);
        }

        Set<String> stringPropertyNames() {
            return getMergedProperties().stringPropertyNames();
        }

        boolean containsKey(String propertyName) {
            return getMergedProperties().containsKey(propertyName);
        }

        String getProperty(String propertyName) {
            return getMergedProperties().getProperty(propertyName);
        }

        String getProperty(String propertyName, String defaultValue) {
            return getMergedProperties().getProperty(propertyName, defaultValue);
        }

        void setProperty(String propertyName, String value) {
            baseProperties.setProperty(propertyName, value);
        }

        void remove(String propertyName) {
            baseProperties.remove(propertyName);
        }

        Properties getMergedProperties() {
            Properties result = createProperties(baseProperties);
            Properties currentlyOverwritten = getSubProperties("archunit", System.getProperties());
            result.putAll(currentlyOverwritten);

            if (!overwrittenProperties.equals(currentlyOverwritten)) {
                replaceProperties(overwrittenProperties, currentlyOverwritten);
                if (!currentlyOverwritten.isEmpty()) {
                    LOG.info("Merging properties: The following properties have been overwritten by system properties: {}", currentlyOverwritten);
                }
            }

            return result;
        }

        private static void replaceProperties(Properties properties, Properties newProperties) {
            properties.clear();
            properties.putAll(newProperties);
        }

        private static Properties createProperties(Map<?, ?> entries) {
            Properties result = new Properties();
            result.putAll(entries);
            return result;
        }

        PropertiesOverwritableBySystemProperties copy() {
            return new PropertiesOverwritableBySystemProperties(copy(baseProperties), copy(overwrittenProperties));
        }

        private Properties copy(Properties properties) {
            return (Properties) properties.clone();
        }
    }

    @PublicAPI(usage = ACCESS)
    public final class ExtensionProperties {
        private final String extensionIdentifier;

        private ExtensionProperties(String extensionIdentifier) {
            this.extensionIdentifier = extensionIdentifier;
        }

        @PublicAPI(usage = ACCESS)
        public ExtensionProperties setProperty(String key, Object value) {
            String fullKey = Joiner.on(".").join(EXTENSION_PREFIX, extensionIdentifier, key);
            properties.setProperty(fullKey, String.valueOf(value));
            return this;
        }
    }
}
