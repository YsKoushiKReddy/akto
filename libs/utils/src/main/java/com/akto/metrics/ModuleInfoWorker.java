package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.ClientActor;
import com.akto.data_actor.DataActor;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.util.VersionUtil;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ModuleInfoWorker {
    private static LoggerMaker loggerMaker = new LoggerMaker(ModuleInfoWorker.class, LoggerMaker.LogDb.RUNTIME);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ModuleInfo.ModuleType moduleType;
    private final int startedTs = Context.now();
    private final String version;
    private final DataActor dataActor;
    private final String moduleName;
    private ModuleInfoWorker(ModuleInfo.ModuleType moduleType, String version, DataActor dataActor, String name) {
        this.moduleType = moduleType;
        this.version = version;
        this.dataActor = dataActor;
        this.moduleName = name;
    }

    private ModuleInfoWorker() {
        this.moduleType = null;
        this.version = null;
        this.dataActor = null;
        this.moduleName = null;
    }

    private void scheduleHeartBeatUpdate () {
        ModuleInfoWorker _this = this;
        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setModuleType(this.moduleType);
        moduleInfo.setCurrentVersion(this.version);
        moduleInfo.setStartedTs(this.startedTs);
        moduleInfo.setId(moduleInfo.getId());//Setting new uuid for id
        moduleInfo.setName(this.moduleName);

        scheduler.scheduleWithFixedDelay(() -> {
            moduleInfo.setLastHeartbeatReceived(Context.now());
            assert _this.dataActor != null;
            _this.dataActor.updateModuleInfo(moduleInfo);
            loggerMaker.info("Sending heartbeat at : " + moduleInfo.getLastHeartbeatReceived() + " for module: " + moduleInfo.getModuleType().name());
        }, 0, 30, TimeUnit.SECONDS);
    }

    public static void init(ModuleInfo.ModuleType moduleType, DataActor dataActor) {
        init(moduleType, dataActor, null);
    }

    public static void init(ModuleInfo.ModuleType moduleType, DataActor dataActor, String name) {
        String version;
        try (InputStream in = ModuleInfoWorker.class.getResourceAsStream("/version.txt")) {
            if (in != null) {
                version = VersionUtil.getVersion(in);
            } else {
                throw new Exception("Input stream null");
            }
        } catch (Exception e) {
            loggerMaker.error("Error getting local version, skipping heartbeat check");
            return;
        }
        loggerMaker.infoAndAddToDb("Starting heartbeat update for module: " + moduleType.name() + " with version: " + version + " and name: " + name);
        ModuleInfoWorker infoWorker = new ModuleInfoWorker(moduleType, version, dataActor, name);
        infoWorker.scheduleHeartBeatUpdate();
    }
}
