package io.github.railroad.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.project.data.Project;
import io.github.railroad.utility.ConfigHandler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProjectManager {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();

    public ProjectManager() {
        ConfigHandler.createDefaultConfigs();
        loadProjects();
    }

    private static void deleteDirectory(File dir) {
        for (File subfile : dir.listFiles()) {
            if(subfile.isDirectory()){
                deleteDirectory(subfile);
            }

            subfile.delete();
        }
    }

    public void loadProjects() {
        List<Project> projects = new ArrayList<>();

        JsonArray projectsJsonArray = ConfigHandler.getProjectsConfig();
        for (JsonElement projectElement : projectsJsonArray) {
            JsonObject projectObject = projectElement.getAsJsonObject();
            String projectPath = projectObject.get("path").getAsString();
            String projectAlias = projectObject.get("alias").getAsString();
            String uuid = projectObject.get("uuid").getAsString();
            Project project = new Project(Path.of(projectPath), projectAlias);
            if (projectObject.has("lastOpened")) {
                long lastOpened = projectObject.get("lastOpened").getAsLong();
                project.setLastOpened(lastOpened);
            }

            project.setId(uuid);
            project.setManager(this);
            projects.add(project);
        }

        setProjects(projects);
    }

    public void updateProjectInfo(Project project) {
        updateProjectInfo(project, false, false);
    }

    public void updateProjectInfo(Project project, boolean removeProject, boolean deleteProject) {
        JsonObject object = ConfigHandler.getConfigJson();
        JsonArray projects = object.getAsJsonArray("projects");
        System.out.println("Starting project update: " + project.getId());
        boolean found = false;
        for (JsonElement projectElement : projects) {
            JsonObject projectObject = projectElement.getAsJsonObject();
            if (projectObject.get("uuid").getAsString().equals(project.getId())) {
                if (removeProject) {
                    projects.remove(projectElement);
                    if(deleteProject) {
                        deleteProjectFiles(project);
                    }
                    break;
                } else {
                    found = true;
                    projectObject.addProperty("lastOpened", project.getLastOpened());
                    System.out.println("Starting update project: " + project.getId() + " last opened to: " + project.getLastOpened());
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

    public ObservableList<Project> getProjects() {
        return projects;
    }

    public Project newProject(Project project) {
        updateProjectInfo(project);
        project.setManager(this);
        this.projects.add(project);

        return project;
    }

    public boolean removeProject(Project project, boolean delete) {
        this.projects.remove(project);
        updateProjectInfo(project, true, delete);

        return true;
    }

    public static void deleteProjectFiles(Project project) {
        File projectdir = new File(project.getPathString());

        deleteDirectory(projectdir);
    }

    public void setProjects(List<Project> projectCollection) {
        this.projects.setAll(projectCollection);
    }
}