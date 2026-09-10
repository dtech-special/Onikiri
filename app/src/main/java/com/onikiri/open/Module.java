package com.onikiri.open;

public class Module {

    private final String id;
    private final String title;
    private final String description;
    private final String githubRepo;
    private final String status;

    public Module(
            String id,
            String title,
            String description,
            String githubRepo,
            String status
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.githubRepo = githubRepo;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public String getStatus() {
        return status;
    }
}