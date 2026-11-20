package com.fongmi.android.tv.server;

import com.fongmi.android.tv.service.PlaybackService;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Task;
import com.github.catvod.utils.Util;

import java.io.IOException;

public class Server {

    private volatile PlaybackService service;
    private volatile Nano nano;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public PlaybackService getService() {
        return service;
    }

    public void setService(PlaybackService service) {
        this.service = service;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getPageAddress(String tab) {
        return getAddress(false) + "/?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    public synchronized boolean start() {
        if (nano != null) return true;
        try {
            nano = new Nano(0, this);
            nano.start(500);
            int port = nano.getListeningPort();
            if (port <= 0) throw new IOException("No listening port");
            Proxy.set(port);
            return true;
        } catch (Throwable e) {
            if (nano != null) nano.stop();
            nano = null;
            return false;
        }
    }

    boolean isCurrent(Nano current) {
        return nano == current;
    }

    public synchronized void stop() {
        LogSession.get().stop();
        Nano current = nano;
        service = null;
        nano = null;
        if (current != null) Task.execute(current::stop);
    }
}
