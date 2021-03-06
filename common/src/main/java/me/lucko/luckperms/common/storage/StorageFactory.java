/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.storage;

import lombok.experimental.UtilityClass;

import com.google.common.collect.ImmutableSet;

import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.storage.backing.AbstractBacking;
import me.lucko.luckperms.common.storage.backing.JSONBacking;
import me.lucko.luckperms.common.storage.backing.MongoDBBacking;
import me.lucko.luckperms.common.storage.backing.SQLBacking;
import me.lucko.luckperms.common.storage.backing.YAMLBacking;
import me.lucko.luckperms.common.storage.backing.sqlprovider.H2Provider;
import me.lucko.luckperms.common.storage.backing.sqlprovider.MySQLProvider;
import me.lucko.luckperms.common.storage.backing.sqlprovider.PostgreSQLProvider;
import me.lucko.luckperms.common.storage.backing.sqlprovider.SQLiteProvider;
import me.lucko.luckperms.common.utils.ImmutableCollectors;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class StorageFactory {

    public static Set<StorageType> getRequiredTypes(LuckPermsPlugin plugin, StorageType defaultMethod) {
        plugin.getLog().info("Detecting storage method...");
        if (plugin.getConfiguration().get(ConfigKeys.SPLIT_STORAGE)) {
            plugin.getLog().info("Loading split storage options.");

            Map<String, String> types = new HashMap<>(plugin.getConfiguration().get(ConfigKeys.SPLIT_STORAGE_OPTIONS));
            types.entrySet().stream()
                    .filter(e -> StorageType.parse(e.getValue()) == null)
                    .forEach(e -> {
                        plugin.getLog().severe("Storage method for " + e.getKey() + " - " + e.getValue() + " not recognised. " +
                                "Using the default instead.");
                        e.setValue(defaultMethod.getIdentifiers().get(0));
                    });

            Set<String> neededTypes = new HashSet<>();
            neededTypes.addAll(types.values());

            return neededTypes.stream().map(StorageType::parse).collect(ImmutableCollectors.toImmutableSet());
        } else {
            String method = plugin.getConfiguration().get(ConfigKeys.STORAGE_METHOD);
            StorageType type = StorageType.parse(method);
            if (type == null) {
                plugin.getLog().severe("Storage method '" + method + "' not recognised. Using the default instead.");
                type = defaultMethod;
            }
            return ImmutableSet.of(type);
        }
    }

    public static Storage getInstance(LuckPermsPlugin plugin, StorageType defaultMethod) {
        Storage storage;

        plugin.getLog().info("Initializing storage backings...");
        if (plugin.getConfiguration().get(ConfigKeys.SPLIT_STORAGE)) {
            plugin.getLog().info("Using split storage.");

            Map<String, String> types = new HashMap<>(plugin.getConfiguration().get(ConfigKeys.SPLIT_STORAGE_OPTIONS));
            types.entrySet().stream()
                    .filter(e -> StorageType.parse(e.getValue()) == null)
                    .forEach(e -> e.setValue(defaultMethod.getIdentifiers().get(0)));

            Set<String> neededTypes = new HashSet<>();
            neededTypes.addAll(types.values());

            Map<String, AbstractBacking> backing = new HashMap<>();

            for (String type : neededTypes) {
                backing.put(type, makeBacking(StorageType.parse(type), plugin));
            }

            storage = AbstractStorage.wrap(plugin, new SplitBacking(plugin, backing, types));

        } else {
            String method = plugin.getConfiguration().get(ConfigKeys.STORAGE_METHOD);
            StorageType type = StorageType.parse(method);
            if (type == null) {
                type = defaultMethod;
            }

            storage = makeInstance(type, plugin);
        }

        plugin.getLog().info("Initialising storage provider...");
        storage.init();
        return storage;
    }

    private static Storage makeInstance(StorageType type, LuckPermsPlugin plugin) {
        return AbstractStorage.wrap(plugin, makeBacking(type, plugin));
    }

    private static AbstractBacking makeBacking(StorageType method, LuckPermsPlugin plugin) {
        switch (method) {
            case MYSQL:
                return new SQLBacking(plugin, new MySQLProvider(plugin.getConfiguration().get(ConfigKeys.DATABASE_VALUES)), plugin.getConfiguration().get(ConfigKeys.SQL_TABLE_PREFIX));
            case SQLITE:
                return new SQLBacking(plugin, new SQLiteProvider(new File(plugin.getDataDirectory(), "luckperms.sqlite")), plugin.getConfiguration().get(ConfigKeys.SQL_TABLE_PREFIX));
            case H2:
                return new SQLBacking(plugin, new H2Provider(new File(plugin.getDataDirectory(), "luckperms.db")), plugin.getConfiguration().get(ConfigKeys.SQL_TABLE_PREFIX));
            case POSTGRESQL:
                return new SQLBacking(plugin, new PostgreSQLProvider(plugin.getConfiguration().get(ConfigKeys.DATABASE_VALUES)), plugin.getConfiguration().get(ConfigKeys.SQL_TABLE_PREFIX));
            case MONGODB:
                return new MongoDBBacking(plugin, plugin.getConfiguration().get(ConfigKeys.DATABASE_VALUES));
            case YAML:
                return new YAMLBacking(plugin, plugin.getDataDirectory());
            default:
                return new JSONBacking(plugin, plugin.getDataDirectory());
        }
    }
}
