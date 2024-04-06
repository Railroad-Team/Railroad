package io.github.railroad.project;

import io.github.railroad.utility.ConfigHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.Long.parseLong;

public class ProjectManager {

    private final ConfigHandler configHandler = new ConfigHandler();
    private Collection<Project> projects;
    public ProjectManager() {

    }
    public Collection<Project> loadProjects() {
        List<Project> projects = new ArrayList<>();

        JSONArray projectsJsonArray = configHandler.getProjectsConfig();
        for (int i = 0; i < projectsJsonArray.length(); i++) {
            JSONObject projectObject = projectsJsonArray.getJSONObject(i);
            String projectPath = projectObject.getString("path");
            String projectAlias = projectObject.getString("alias");
            String uuid = projectObject.getString("uuid");
            Project project = new Project(Path.of(projectPath),projectAlias);
            if (projectObject.has("lastopened")) {
                Long lastopened = projectObject.getLong("lastopened");
                project.setLastOpened(lastopened);
            }
            project.setUUID(uuid);
            project.setManager(this);
            projects.add(project);
            System.out.println(projectAlias);
        }
        this.projects = projects;
        return projects;
    }

    public void UpdateProjectInfo(Project project) {
        JSONObject object = configHandler.getConfigJson();
        JSONArray projects = object.getJSONArray("projects");
        for (int i = 0; i < projects.length(); i++) {
            JSONObject projectObject = projects.getJSONObject(i);
            System.out.println(projectObject.get("uuid"));
            System.out.println(project.getUUID());
            System.out.println("Finsihed compare");
            if (projectObject.get("uuid").toString().equals(project.getUUID())) {
                projectObject.putOnce("lastopened", project.getLastOpened());
                System.out.println("Found Project");
            }
        }
        configHandler.updateConfig(object);

    }

    public Collection<Project> getProjects() {
        return projects;
    }

    public void setProjects(Collection<Project> projects) {
        this.projects = projects;
    }
}
