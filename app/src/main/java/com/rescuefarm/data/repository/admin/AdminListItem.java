package com.rescuefarm.data.repository.admin;

public final class AdminListItem {
    private final String id;
    private final String title;
    private final String subtitle;
    private final String status;
    private final boolean active;
    private final String imageUrl;

    public AdminListItem(String id, String title, String subtitle, String status, boolean active) {
        this(id, title, subtitle, status, active, "");
    }

    public AdminListItem(String id, String title, String subtitle, String status, boolean active,
            String imageUrl) {
        this.id = clean(id);
        this.title = clean(title);
        this.subtitle = clean(subtitle);
        this.status = clean(status);
        this.active = active;
        this.imageUrl = clean(imageUrl);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getStatus() { return status; }
    public boolean isActive() { return active; }
    public String getImageUrl() { return imageUrl; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
