/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
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
package org.jkiss.dbeaver.model.dpi.app;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.osgi.internal.location.EquinoxLocations;
import org.eclipse.osgi.service.datalocation.Location;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.app.DBPApplication;
import org.jkiss.dbeaver.model.dpi.process.ConfigUtils;
import org.jkiss.dbeaver.model.dpi.server.DPIRestServer;
import org.jkiss.dbeaver.registry.DesktopApplicationImpl;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.utils.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DPI application
 */
public class DPIApplication extends DesktopApplicationImpl {

    private static final Log log = Log.getLog(DPIApplication.class);
    public static final String PARAM_SERVER_PORT = "serverPort";
    public static final String SERVER_INI_FILE = "server.ini";

    @Override
    public Object start(IApplicationContext context) {
        Location instanceLoc = Platform.getInstanceLocation();
        //log.debug("Starting DPI application at " + instanceLoc.getURL());

        DPIPlatform.createInstance();

        DBPApplication application = DBWorkbench.getPlatform().getApplication();

        try {
            runServer(context, application);
        } catch (IOException e) {
            log.error(e);
        }

        log.debug("Exiting DPI application");

        return EXIT_OK;
    }

    private void runServer(IApplicationContext appContext, DBPApplication application) throws IOException {
        int portNumber = IOUtils.findFreePort(20000, 65000);
        DPIRestServer server = new DPIRestServer(application, portNumber);
        saveServerInfo(appContext, application, portNumber);
        try {
            log.debug("Started DPI Server at " + portNumber);
            server.join();
        } finally {
            deleteServerInfo(appContext, application);
        }
    }

    private void saveServerInfo(IApplicationContext appContext, DBPApplication application, int portNumber) throws IOException {
        Path serverIniFile = getServerIniFile();
        try (BufferedWriter out = Files.newBufferedWriter(serverIniFile, StandardOpenOption.CREATE)) {
            Map<String, String> props = new LinkedHashMap<>();
            props.put(PARAM_SERVER_PORT, String.valueOf(portNumber));
            props.put("startTime", new Date().toString());
            ConfigUtils.storeProperties(out, props);
        }
    }

    private void deleteServerInfo(IApplicationContext appContext, DBPApplication application) throws IOException {
        Path serverIniFile = getServerIniFile();
        if (Files.exists(serverIniFile)) {
            Files.delete(serverIniFile);
        }
    }

    @NotNull
    private Path getServerIniFile() throws IOException {
        String configPath = System.getProperty(EquinoxLocations.PROP_CONFIG_AREA);
        if (configPath == null) {
            throw new IOException("OSGI configuration area property not set");
        }
        configPath = normalizeFileReference(configPath);
        Path configFolder = Path.of(configPath);
        if (!Files.exists(configFolder)) {
            throw new IOException("Configuration folder '" + configFolder + "' doesn't exists");
        }
        Path serverIniFile = configFolder.resolve(SERVER_INI_FILE);
        return serverIniFile;
    }

    @NotNull
    private String normalizeFileReference(String configPath) {
        if (configPath.startsWith("file:")) {
            configPath = configPath.substring(configPath.indexOf(':') + 1);
        }
        // Check Windows-specific path (/C:/Temp)
        while (configPath.contains(":/") && configPath.startsWith("/")) {
            configPath = configPath.substring(1);
        }
        return configPath;
    }

    @Override
    public void stop() {
        System.out.println("Stopping DPI application");
        super.stop();
    }

    @Override
    public @Nullable Path getDefaultWorkingFolder() {
        return null;
    }

    @Override
    public String getDefaultProjectName() {
        return "default";
    }

}