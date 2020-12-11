package dev.necauqua.srgmacro;

import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@SuppressWarnings("unused")
public final class SrgMacroPlugin implements Plugin<Project> {
    private static final Pattern PATTERN = Pattern.compile("srg\\(\\s*\"(.*?)\"(?:\\s*,\\s*\"(.*?)\")?(?:\\s*,\\s*\"(.*?)\")?\\s*\\)");

    private static final class Mapping {
        final String srg;
        final String cls;
        @Nullable
        final String desc;

        Mapping(String srg, String cls, @Nullable String desc) {
            this.srg = srg;
            this.cls = cls;
            this.desc = desc;
        }

        @Override
        public String toString() {
            return "Mapping{srg='" + srg + "', cls='" + cls + "', desc='" + desc + "'}";
        }
    }

    private static final class BadMapping {
        final String mcp;
        final String filename;
        final int lineNumber;
        int counter;

        BadMapping(String mcp, String filename, int lineNumber, int counter) {
            this.mcp = mcp;
            this.filename = filename;
            this.lineNumber = lineNumber;
            this.counter = counter;
        }
    }

    @Override
    public void apply(Project project) {

        Path preprocessedFolder = project.getBuildDir().toPath().resolve("preprocessedSrc");

        project.getTasks().register("preprocessCopySrc", Copy.class, task -> {
            task.from("src/main/java");
            task.into(preprocessedFolder);
        });

        Task replaceSrgCalls = project.task("replaceSrgCalls", task -> {
            task.dependsOn("preprocessCopySrc");
            task.doLast(it -> {
                GenerateSRG genSrg = (GenerateSRG) project.getTasks().getByName("createSrgToMcp");
                File mapping = genSrg.getOutput();
                Map<String, List<Mapping>> map = new HashMap<>();
                try {
                    Files.lines(mapping.toPath())
                            .filter(line -> line.startsWith("MD:") || line.startsWith("FD:"))
                            .forEach(line -> {
                                String[] split = line.split(" ");
                                String type = split[0];
                                String srg = split[1];
                                String mcp = split[type.equals("FD:") ? 2 : 3];
                                int idx = srg.lastIndexOf('/');
                                String cls = srg.substring(srg.lastIndexOf('/', idx - 1) + 1, idx);
                                String srgName = srg.substring(idx + 1);
                                String mcpName = mcp.substring(mcp.lastIndexOf('/') + 1);
                                map.computeIfAbsent(mcpName, $ -> new ArrayList<>())
                                        .add(new Mapping(srgName, cls, type.equals("MD:") ? split[4] : null));
                            });
                } catch (IOException e) {
                    throw new AssertionError("ForgeGradle mapping location is invalid", e);
                }

                boolean plain = !project.getGradle().getTaskGraph().hasTask(":reobfJar");
                Map<String, BadMapping> notFound = new HashMap<>();
                Map<String, BadMapping> ambiguous = new HashMap<>();

                project.fileTree(preprocessedFolder).forEach(file -> {
                    String filename = file.getName();
                    List<String> list;
                    try {
                        Path path = file.toPath();
                        list = Files.readAllLines(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to open one of the source files", e);
                    }
                    StringBuffer result = new StringBuffer();
                    for (int i = 0; i < list.size(); i++) {
                        String line = list.get(i);
                        Matcher matcher = PATTERN.matcher(line);
                        while (matcher.find()) {
                            String mcp = matcher.group(1);
                            String cls = matcher.group(2);
                            String desc = matcher.group(3);
                            List<Mapping> mappings = new ArrayList<>(map.getOrDefault(mcp, emptyList()));
                            if (cls != null) {
                                mappings.removeIf(m -> !cls.equals(m.cls));
                            }
                            if (desc != null) {
                                mappings.removeIf(m -> !desc.equals(m.desc));
                            }
                            if (mappings.size() == 1) {
                                matcher.appendReplacement(result, "\"" + (plain ? mcp : mappings.get(0).srg) + "\"");
                                continue;
                            }
                            int lineNumber = i + 1;
                            (mappings.isEmpty() ? notFound : ambiguous)
                                    .computeIfAbsent(mcp, k -> new BadMapping(mcp, filename, lineNumber, 0))
                                    .counter++;
                            matcher.appendReplacement(result, "\"" + mcp + "\"");
                        }
                        matcher.appendTail(result);
                        result.append('\n');
                    }
                    try {
                        Files.write(file.toPath(), result.toString().getBytes());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write into one of the source files", e);
                    }
                });
                if (!notFound.isEmpty() || !ambiguous.isEmpty()) {
                    throw new IllegalStateException("\nProcessing SRG literals failed:\n" +
                            errorsToString(notFound, "Mappings not found") +
                            errorsToString(ambiguous, "Ambiguous mappings exist"));
                }
            });
        });

        JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
        compileJava.setSource(project.fileTree(preprocessedFolder));
        compileJava.dependsOn(replaceSrgCalls);
    }

    private static String errorsToString(Map<String, BadMapping> errors, String prefix) {
        if (errors.isEmpty()) {
            return "";
        }
        return "  * " + prefix + " for:\n    - " +
                errors.entrySet()
                        .stream()
                        .map(e -> {
                            BadMapping m = e.getValue();
                            return e.getKey() + " (at " + m.filename + ":" + m.lineNumber + ")" + (m.counter > 1 ? " (" + m.counter + " times)" : "");
                        })
                        .collect(Collectors.joining("\n    - ")) + "\n";
    }
}
