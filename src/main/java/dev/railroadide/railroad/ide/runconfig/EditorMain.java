package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.io.File;
import dev.railroadide.railroad.ide.runconfig.defaults.data.GradleRunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.defaults.data.JavaApplicationRunConfigurationData;
import java.util.Map;

/**
 * A main declaration in the editor and the binary name accepted by the Java launcher.
 */
public record EditorMain(int line, String className) {
    public static List<EditorMain> find(String source, String filename) {
        if (!source.contains("main"))
            return List.of();

        var parser = ASTParser.newParser(AST.getJLSLatest());
        Map<String, String> options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(JavaCore.latestSupportedJavaVersion(), options);
        options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
        parser.setCompilerOptions(options);
        parser.setSource(source.toCharArray());
        parser.setUnitName(filename);
        parser.setIgnoreMethodBodies(true);
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        String prefix = unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName() + ".";
        List<EditorMain> targets = new ArrayList<>();
        for (Object declaration : unit.types()) {
            if (declaration instanceof AbstractTypeDeclaration type) {
                String name = type instanceof ImplicitTypeDeclaration
                    ? filename.replaceFirst("\\.java$", "")
                    : type.getName().getIdentifier();
                collect(unit, type, prefix + name, targets);
            }
        }

        return List.copyOf(targets);
    }

    private static void collect(
        CompilationUnit unit,
        AbstractTypeDeclaration owner,
        String name,
        List<EditorMain> targets
    ) {
        for (Object member : owner.bodyDeclarations()) {
            if (member instanceof AbstractTypeDeclaration nested) {
                collect(unit, nested, name + "$" + nested.getName().getIdentifier(), targets);
            } else if (member instanceof MethodDeclaration method && isMain(method)) {
                targets.add(new EditorMain(unit.getLineNumber(method.getName().getStartPosition()), name));
            }
        }
    }

    private static boolean isMain(MethodDeclaration method) {
        if (!method.getName().getIdentifier().equals("main") || Modifier.isPrivate(method.getModifiers())
            || Modifier.isAbstract(method.getModifiers()) || method.isConstructor()
            || !(method.getReturnType2() instanceof PrimitiveType result)
            || result.getPrimitiveTypeCode() != PrimitiveType.VOID)
            return false;

        if (method.parameters().isEmpty())
            return true;

        if (method.parameters().size() != 1)
            return false;

        SingleVariableDeclaration parameter = (SingleVariableDeclaration) method.parameters().getFirst();
        Type type = parameter.getType();
        int dimensions = parameter.getExtraDimensions() + (parameter.isVarargs() ? 1 : 0);
        if (type instanceof ArrayType array) {
            dimensions += array.getDimensions();
            type = array.getElementType();
        }

        return dimensions == 1 && (type.toString().equals("String") || type.toString().equals("java.lang.String"));
    }

    public static boolean supports(Project project, Path file) {
        Path root = ProjectPathIdentityUtils.normalize(project.getPath());
        Path source = ProjectPathIdentityUtils.normalize(file);
        return source.startsWith(root) && (project.getGradleManager().isGradleProject()
            || (!project.hasFacet(FacetManager.MAVEN) && source.startsWith(root.resolve("src/main/java"))));
    }

    public RunConfiguration<?> configuration(Project project, Path file) {
        String source = ProjectPathIdentityUtils.normalize(project.getPath())
            .relativize(ProjectPathIdentityUtils.normalize(file)).toString().replace(File.separatorChar, '/');
        UUID id = UUID.nameUUIDFromBytes(("editor-main:" + source + ":" + className).getBytes(StandardCharsets.UTF_8));
        RunConfigurationManager manager = project.getRunConfigManager();
        RunConfiguration<?> existing = manager.getConfigurationByUUID(id);
        if (existing != null)
            return existing;

        RunConfiguration<?> config;
        if (project.getGradleManager().isGradleProject()) {
            var type = RunConfigurationTypes.GRADLE;
            GradleRunConfigurationData data = type.createDataInstance(project);
            data.setMainClass(className);
            data.setMainSource(source);
            var jdk = project.getGradleManager().getGradleSettings().getGradleJvm();
            if (jdk != null) {
                data.setJavaHome(jdk);
            }

            config = new RunConfiguration<>(id, type, data);
        } else {
            var type = RunConfigurationTypes.JAVA_APPLICATION;
            JavaApplicationRunConfigurationData data = type.createDataInstance(project);
            data.setMainClass(className);
            data.setBuildBeforeRun(true);
            data.setClasspathEntries(new String[]{".railroad/classes"});

            config = new RunConfiguration<>(id, type, data);
        }

        config.data().setName(className + ".main()");
        manager.addConfiguration(config);
        return config;
    }
}
