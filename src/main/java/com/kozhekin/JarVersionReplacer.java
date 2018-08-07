package com.kozhekin;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * User: Iggi
 * Date: 22/02/2017
 * Time: 11:51
 */

public class JarVersionReplacer {

    private Path root;
    private String artifactId;
    private String newVersion;
    private String newVersionGradle;
    private Collection<String> exclusions;
    private static final String POM = "pom.xml";
    private static final String GRADLE = "build.gradle";
    private Pattern versionPattern = Pattern.compile("<version>([0-9A-Za-z\\-\\.]+)</version>");
    private Pattern versionPatternGradle;

    public JarVersionReplacer(final Path root, final String artifactId, final String newVersion, final Iterable<String> exclusions) {
        this.root = root;
        this.artifactId = "<artifactId>" + artifactId + "</artifactId>";
        this.newVersion = "<version>" + newVersion + "</version>";
        this.newVersionGradle = ":" + artifactId + ":" + newVersion;
        this.versionPatternGradle = Pattern.compile(":" + artifactId + ":" + "([0-9A-Za-z\\-\\.]+)");
        this.exclusions = StreamSupport.stream(exclusions.spliterator(), false).map(s -> Paths.get(root.toString(), s).toString()).collect(Collectors.toSet());
    }

    public void replace() {
        replaceIn(root.toFile());
    }

    public void replaceIn(File root) {
        Preconditions.checkArgument(root.isDirectory(), root.toString() + " must be a directory");
        final File[] list = Preconditions.checkNotNull(root.listFiles());
        for (File f : list) {
            if (f.isDirectory() && !exclusions.contains(f.getAbsolutePath())) {
                replaceIn(f);
            }
            if (f.isFile()) {
                if (POM.equals(f.getName())) {
                    replaceInPlace(f);
                } else if (GRADLE.equals(f.getName())) {
                    replaceInPlaceGradle(f);
                }

            }
        }
    }

    private int skipPomHeader(int i, List<String> lines) {
        // skipping pom.xml header
        while (i < lines.size() && !lines.get(i).contains("<dependenc")) {
            ++i;
        }
        return i;
    }

    private void replaceInPlace(final File pom) {
        Preconditions.checkArgument(pom.exists());
        try {
            final FileInputStream fis = new FileInputStream(pom);
            final List<String> lines = IOUtils.readLines(fis, "UTF-8");
            IOUtils.closeQuietly(fis);
            int i = skipPomHeader(0, lines);
            for (; i < lines.size(); ++i) {
                if (lines.get(i).contains(artifactId)) {
                    final String oldLine = lines.get(i + 1);
                    final Matcher m = versionPattern.matcher(oldLine);
                    final String newLine = m.replaceFirst(newVersion);
                    if (!oldLine.equals(newLine)) {
                        lines.set(i + 1, newLine);
                        try (FileOutputStream fos = new FileOutputStream(pom)) {
                            IOUtils.writeLines(lines, "\n", fos, "UTF-8");
                        }
                        System.out.printf("file: %s, old: %s, new: %s\n", pom.getPath(), oldLine.trim(), newLine.trim());
                    }
                }
            }
        } catch (IOException e) {
            // empty
        }
    }

    private void replaceInPlaceGradle(final File gradle) {
        Preconditions.checkArgument(gradle.exists());
        try {
            final FileInputStream fis = new FileInputStream(gradle);
            final List<String> lines = IOUtils.readLines(fis, "UTF-8");
            IOUtils.closeQuietly(fis);
            int i = 0;
            for (; i < lines.size(); ++i) {
                final String oldLine = lines.get(i);
                final Matcher m = versionPatternGradle.matcher(oldLine);
                if (m.find()) {
                    final String newLine = m.replaceFirst(newVersionGradle);
                    if (!oldLine.equals(newLine)) {
                        lines.set(i, newLine);
                        try (FileOutputStream fos = new FileOutputStream(gradle)) {
                            IOUtils.writeLines(lines, "\n", fos, "UTF-8");
                        }
                        System.out.printf("file: %s, old: %s, new: %s\n", gradle.getPath(), oldLine.trim(), newLine.trim());
                    }
                }
            }
        } catch (IOException e) {
            // empty
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("<progname> artifactId version");
            return;
        }
        final JarVersionReplacer replacer = new JarVersionReplacer(
                Paths.get("/Users/user/projects/all-projects"),
                args[0],
                args[1],
                ImmutableList.of("eff/web.git", "eff/api", "context_ad", "qiberty"));
        replacer.replace();
        replacer.replaceIn(new File("/Users/user/projects/effector/effector-web"));
        replacer.replaceIn(new File("/Users/user/projects/effector/effector-api"));
        replacer.replaceIn(new File("/Users/user/projects/effector/context_ad"));
        replacer.replaceIn(new File("/Users/user/projects/qiberty"));

    }
}
