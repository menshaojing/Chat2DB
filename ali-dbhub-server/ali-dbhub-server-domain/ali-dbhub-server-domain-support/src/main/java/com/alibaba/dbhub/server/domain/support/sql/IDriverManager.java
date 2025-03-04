/**
 * alibaba.com Inc.
 * Copyright (c) 2004-2023 All Rights Reserved.
 */
package com.alibaba.dbhub.server.domain.support.sql;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.dbhub.server.domain.support.enums.DriverTypeEnum;
import com.alibaba.dbhub.server.domain.support.model.DriverEntry;
import com.alibaba.dbhub.server.domain.support.util.JdbcJarUtils;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.alibaba.dbhub.server.domain.support.util.JdbcJarUtils.getNewFullPath;

/**
 * @author jipengfei
 * @version : IsolationDriverManager.java
 */
public class IDriverManager {
    private static final Logger log = LoggerFactory.getLogger(IDriverManager.class);
    private static final Map<String, ClassLoader> CLASS_LOADER_MAP = new ConcurrentHashMap();
    private static final Map<DriverTypeEnum, DriverEntry> DRIVER_ENTRY_MAP = new ConcurrentHashMap();

    public static Connection getConnection(String url, DriverTypeEnum driver) throws SQLException {
        Properties info = new Properties();
        return getConnection(url, info, driver);
    }

    public static Connection getConnection(String url, String user, String password, DriverTypeEnum driverTypeEnum)
        throws SQLException {
        Properties info = new Properties();
        if (user != null) {
            info.put("user", user);
        }

        if (password != null) {
            info.put("password", password);
        }

        return getConnection(url, info, driverTypeEnum);
    }

    public static Connection getConnection(String url, String user, String password, DriverTypeEnum driverTypeEnum,
        Map<String, String> properties)
        throws SQLException {
        Properties info = new Properties();
        if (user != null) {
            info.put("user", user);
        }

        if (password != null) {
            info.put("password", password);
        }
        info.putAll(properties);
        return getConnection(url, info, driverTypeEnum);
    }

    public static Connection getConnection(String url, Properties info, DriverTypeEnum driverTypeEnum)
        throws SQLException {
        if (url == null) {
            throw new SQLException("The url cannot be null", "08001");
        }
        DriverManager.println("DriverManager.getConnection(\"" + url + "\")");
        SQLException reason = null;
        DriverEntry driverEntry = DRIVER_ENTRY_MAP.get(driverTypeEnum);
        if (driverEntry == null) {
            driverEntry = getJDBCDriver(driverTypeEnum);
        }
        try {
            Connection con = driverEntry.getDriver().connect(url, info);
            if (con != null) {
                DriverManager.println("getConnection returning " + driverEntry.getDriver().getClass().getName());
                return con;
            }
        } catch (SQLException var7) {
            if (reason == null) {
                reason = var7;
            }
        }

        if (reason != null) {
            DriverManager.println("getConnection failed: " + reason);
            throw reason;
        } else {
            DriverManager.println("getConnection: no suitable driver found for " + url);
            throw new SQLException("No suitable driver found for " + url, "08001");
        }
    }

    private static DriverEntry getJDBCDriver(DriverTypeEnum driverTypeEnum)
        throws SQLException {
        synchronized (driverTypeEnum) {
            try {
                if (DRIVER_ENTRY_MAP.containsKey(driverTypeEnum)) {
                    return DRIVER_ENTRY_MAP.get(driverTypeEnum);
                }
                ClassLoader cl = getClassLoader(driverTypeEnum);
                Driver driver = (Driver)cl.loadClass(driverTypeEnum.getDriverClass()).newInstance();
                DriverEntry driverEntry = DriverEntry.builder().driverTypeEnum(driverTypeEnum).driver(driver).build();
                DRIVER_ENTRY_MAP.put(driverTypeEnum, driverEntry);
                return driverEntry;
            } catch (Exception e) {
                log.error("getJDBCDriver error", e);
                throw new SQLException("getJDBCDriver error", "08001");
            }
        }

    }

    public static ClassLoader getClassLoader(DriverTypeEnum driverTypeEnum) throws MalformedURLException {
        String jarPath = driverTypeEnum.getJar();
        if (CLASS_LOADER_MAP.containsKey(jarPath)) {
            return CLASS_LOADER_MAP.get(jarPath);
        } else {
            synchronized (jarPath) {
                if (CLASS_LOADER_MAP.containsKey(jarPath)) {
                    return CLASS_LOADER_MAP.get(jarPath);
                }
                String[] jarPaths = jarPath.split(",");
                URL[] urls = new URL[jarPaths.length];
                for (int i = 0; i < jarPaths.length; i++) {
                    File driverFile = new File(JdbcJarUtils.getFullPath(jarPaths[i]));
                    urls[i] = driverFile.toURI().toURL();
                }
                ClassLoader cl = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
                try {
                    cl.loadClass(driverTypeEnum.getDriverClass());
                } catch (ClassNotFoundException e) {
                    //如果报错删除目录重试一次
                    for (int i = 0; i < jarPaths.length; i++) {
                        File driverFile = new File(JdbcJarUtils.getNewFullPath(jarPaths[i]));
                        urls[i] = driverFile.toURI().toURL();
                    }
                    cl = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
                }
                CLASS_LOADER_MAP.put(jarPath, cl);
                return cl;
            }
        }
    }
}