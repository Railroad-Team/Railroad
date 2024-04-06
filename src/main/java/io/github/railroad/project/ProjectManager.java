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
                String lastopened = projectObject.getString("lastopened");
                project.setLastOpened(parseLong(lastopened));
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

    }

    public Collection<Project> getProjects() {
        return projects;
    }

    public void setProjects(Collection<Project> projects) {
        this.projects = projects;
    }
}
