package com.remotepocket.model;

import java.io.Serializable;

public class Shortcut implements Serializable {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String command;

    public Shortcut() {
    }

    public Shortcut(String id, String name, String description, String icon, String command) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.command = command;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Shortcut";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon != null ? icon : "terminal";
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getCommand() {
        return command != null ? command : "";
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
