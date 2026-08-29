package com.cloudstream.core.model;

import java.util.Objects;

/**
 * Đại diện cho một mục (Section/Category) trên trang chủ.
 */
public class MainPageSection {
    public String name;
    public String path;

    public MainPageSection() {
    }

    public MainPageSection(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MainPageSection that = (MainPageSection) o;
        return Objects.equals(name, that.name) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }

    @Override
    public String toString() {
        return "MainPageSection{" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
