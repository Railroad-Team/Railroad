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
    private Collection<Project> projectCollection;
    public ProjectManager() {
        configHandler.CreateDefaultConfigs();
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
            project.setID(uuid);
            project.setManager(this);
            projects.add(project);
            System.out.println(projectAlias);
        }
        this.setProjectCollection(projects);
        return projects;
    }

    public void UpdateProjectInfo(Project project) {
        UpdateProjectInfo(project, false);
    }

    public void UpdateProjectInfo(Project project, boolean removeProject) {
        JSONObject object = configHandler.getConfigJson();
        JSONArray projects = object.getJSONArray("projects");
        boolean found = false;
        for (int i = 0; i < projects.length(); i++) {
            JSONObject projectObject = projects.getJSONObject(i);
            if (projectObject.get("uuid").toString().equals(project.getID())) {
                if (removeProject) {
                    projects.remove(i);
                    break;
                } else {
                    found = true;
                    projectObject.put("lastopened",project.getLastOpened());
                }

            }
        }
        if ((!found) && (!removeProject)) {
            System.out.println("Create new Project");
            JSONObject newProject = new JSONObject();
            newProject.put("uuid", project.getID());
            newProject.put("path", project.getPath());
            newProject.put("alias", project.getAlias());
            projects.put(newProject);
        }
        configHandler.updateConfig(object);

    }

    public Collection<Project> getProjectCollection() {
        return projectCollection;
    }

    public Project NewProject(Project project) {
        this.UpdateProjectInfo(project);
        project.setManager(this);
        this.projectCollection.add(project);
        return project;
    }

    public boolean RemoveProject(Project project) {
        this.projectCollection.remove(project);
        UpdateProjectInfo(project, true);
        return true;
    }

    public void setProjectCollection(Collection<Project> projectCollection) {
        this.projectCollection = projectCollection;
    }


}
