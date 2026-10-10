package com.remotepocket.model;

import java.io.Serializable;
import java.util.Objects;

public class Device implements Serializable {
    private String deviceName;
    private String host;
    private int port;
    private boolean isRemote;
    private boolean hasPin;
    private long lastSeen;

    public Device() {
    }

    public Device(String deviceName, String host, int port) {
        this(deviceName, host, port, false, false);
    }

    public Device(String deviceName, String host, int port, boolean isRemote, boolean hasPin) {
        this.deviceName = deviceName;
        this.host = host;
        this.port = port;
        this.isRemote = isRemote;
        this.hasPin = hasPin;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getDeviceName() {
        return deviceName != null && !deviceName.isEmpty() ? deviceName : "PC Personal";
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getHost() {
        return host != null ? host : "127.0.0.1";
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port > 0 ? port : 5050;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isRemote() {
        return isRemote;
    }

    public void setRemote(boolean remote) {
        isRemote = remote;
    }

    public boolean hasPin() {
        return hasPin;
    }

    public void setHasPin(boolean hasPin) {
        this.hasPin = hasPin;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getBaseHttpUrl() {
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host;
        }
        return "http://" + host + ":" + getPort();
    }

    public String getWebSocketUrl() {
        if (host.startsWith("http://")) {
            return host.replace("http://", "ws://");
        }
        if (host.startsWith("https://")) {
            return host.replace("https://", "wss://");
        }
        if (host.startsWith("ws://") || host.startsWith("wss://")) {
            return host;
        }
        return "ws://" + host + ":" + getPort();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return port == device.port && Objects.equals(host, device.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return getDeviceName() + " (" + host + ")";
    }
}
