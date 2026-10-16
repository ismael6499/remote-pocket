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

    public boolean isSecure() {
        if (isRemote) return true;
        if (host == null) return false;
        String h = host.toLowerCase().trim();
        return h.startsWith("https://") || h.startsWith("wss://") || h.contains("ngrok") || h.endsWith(".app") || h.endsWith(".dev");
    }

    private void sanitizeHostAndPort() {
        if (this.host == null || this.host.trim().isEmpty()) {
            this.host = "127.0.0.1";
        }
        String h = this.host.trim();
        boolean hadTlsPrefix = h.startsWith("https://") || h.startsWith("wss://");
        if (h.startsWith("http://")) h = h.substring(7);
        else if (h.startsWith("https://")) h = h.substring(8);
        else if (h.startsWith("ws://")) h = h.substring(5);
        else if (h.startsWith("wss://")) h = h.substring(6);

        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1).trim();
        }

        boolean hasExplicitPort = false;
        int colonIdx = h.indexOf(':');
        if (colonIdx != -1) {
            String pStr = h.substring(colonIdx + 1).trim();
            h = h.substring(0, colonIdx).trim();
            try {
                int parsedPort = Integer.parseInt(pStr);
                if (parsedPort > 0 && parsedPort <= 65535) {
                    this.port = parsedPort;
                    hasExplicitPort = true;
                }
            } catch (Exception ignored) {}
        }

        this.host = h;
        boolean isDomainOrTunnel = hadTlsPrefix || isRemote || h.contains("ngrok") || h.contains(".app") || h.contains(".dev");
        if (!hasExplicitPort) {
            this.port = isDomainOrTunnel ? 443 : 5050;
        }
    }

    public String getBaseHttpUrl() {
        sanitizeHostAndPort();
        boolean secure = isSecure();
        String scheme = secure ? "https://" : "http://";
        if ((secure && port == 443) || (!secure && port == 80)) {
            return scheme + getHost();
        }
        return scheme + getHost() + ":" + getPort();
    }

    public String getHttpUrl() {
        return getBaseHttpUrl();
    }

    public String getWebSocketUrl() {
        sanitizeHostAndPort();
        boolean secure = isSecure();
        String scheme = secure ? "wss://" : "ws://";
        if ((secure && port == 443) || (!secure && port == 80)) {
            return scheme + getHost();
        }
        return scheme + getHost() + ":" + getPort();
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
