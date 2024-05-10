package io.github.railroad.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.utility.ConfigHandler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProjectManager {
    private List<Project> projectCollection;

    public ProjectManager() {
        ConfigHandler.createDefaultConfigs();
    }

    public List<Project> loadProjects() {
        List<Project> projects = new ArrayList<>();

        JsonArray projectsJsonArray = ConfigHandler.getProjectsConfig();
        for (JsonElement projectElement : projectsJsonArray) {
            JsonObject projectObject = projectElement.getAsJsonObject();
            String projectPath = projectObject.get("path").getAsString();
            String projectAlias = projectObject.get("alias").getAsString();
            String uuid = projectObject.get("uuid").getAsString();
            Project project = new Project(Path.of(projectPath), projectAlias);
            if (projectObject.has("lastopened")) {
                long lastOpened = projectObject.get("lastOpened").getAsLong();
                project.setLastOpened(lastOpened);
            }

            project.setId(uuid);
            project.setManager(this);
            projects.add(project);
        }

        setProjectCollection(projects);
        return projects;
    }

    public void updateProjectInfo(Project project) {
        updateProjectInfo(project, false);
    }

    public void updateProjectInfo(Project project, boolean removeProject) {
        JsonObject object = ConfigHandler.getConfigJson();
        JsonArray projects = object.getAsJsonArray("projects");
        System.out.println("Starting project update: "+ project.getId());
        boolean found = false;
        for (JsonElement projectElement : projects) {
            JsonObject projectObject = projectElement.getAsJsonObject();
            if (projectObject.get("uuid").getAsString().equals(project.getId())) {
                if (removeProject) {
                    projects.remove(projectElement);
                    break;
                } else {
                    found = true;
                    projectObject.addProperty("lastOpened", project.getLastOpened());
                    System.out.println("Starting update project: " +project.getId()+ " last opened to: "+ project.getLastOpened());
                }

            }
        }
        if (!found && !removeProject) {
            System.out.println("Create new Project");
            var newProject = new JsonObject();
            newProject.addProperty("uuid", project.getId());
            newProject.addProperty("path", project.getPath().toString());
            newProject.addProperty("alias", project.getAlias());
            projects.add(newProject);
        }

        ConfigHandler.updateConfig(object);
    }

    public List<Project> getProjectCollection() {
        return projectCollection;
    }

    public Project newProject(Project project) {
        updateProjectInfo(project);
        project.setManager(this);
        this.projectCollection.add(project);

        return project;
    }

    public boolean removeProject(Project project) {
        this.projectCollection.remove(project);
        updateProjectInfo(project, true);

        return true;
    }

    public void setProjectCollection(List<Project> projectCollection) {
        this.projectCollection = projectCollection;
    }
}
