/**
 * Developer: Kadvin Date: 14-7-15 上午9:55
 */
package org.apache.ibatis.migration;

import java.io.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * 扩展的能够读取 jar path 指定目录下的script的 migration loader
 */
public class FileAndJarMigrationLoader extends FileMigrationLoader implements FilenameFilter {
    public static final String APP_JAR_SUFFIX  = "_app";
    public static final String APP_JAR_MIGRATE = "META-INF/migrate/";
    private File jarsDir;

    public FileAndJarMigrationLoader(File scriptsDir, File jarsDir, String charset, Properties properties) {
        super(scriptsDir, charset, properties);
        this.jarsDir = jarsDir;
    }

    @Override
    public List<Change> getMigrations() {
        List<Change> migrations = super.getMigrations();
        //读取 jarsDir指定的jar中的migrations
        if (jarsDir.exists()) {
            scanAppJars(jarsDir, migrations);
        }
        //重新排序
        Change[] changes = migrations.toArray(new Change[migrations.size()]);
        Arrays.sort(changes);
        return Arrays.asList(changes);
    }

    @Override
    public Reader getScriptReader(Change change, boolean undo) {
        //目录下的普通文件对应的change
        if (!change.getFilename().contains("!"))
            return super.getScriptReader(change, undo);
        try {
            String[] jarAndPath = change.getFilename().split("!");
            JarFile jar = new JarFile(jarAndPath[0]);
            ZipEntry entry = jar.getEntry(jarAndPath[1]);
            InputStream stream = jar.getInputStream(entry);
            return new MigrationReader(stream, charset, undo, properties);
        } catch (IOException e) {
            throw new MigrationException("Error reading " + change.getFilename(), e);
        }

    }

    private void scanAppJars(File jarsDir, List<Change> migrations) {
        String[] appJars = jarsDir.list(this);
        for (String appJar : appJars) {
            try {
                scanMigrations(appJar, migrations);
            } catch (IOException e) {
                System.err.println("Can't scan app jar: " + appJar + ", because of :" + e.getMessage());
            }
        }
    }

    private void scanMigrations(String appJar, List<Change> migrations) throws IOException {
        JarFile jar = new JarFile(new File(jarsDir, appJar));
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (entry.getName().startsWith(APP_JAR_MIGRATE)) {
                    migrations.add(parseChangeFromJarEntry(jar, entry));
                }
            }
        } finally {
            jar.close();
        }
    }

    private Change parseChangeFromJarEntry(JarFile jarFile, JarEntry entry) {
        try {
            String filename = entry.getName().substring(entry.getName().lastIndexOf("/") + 1);
            Change change = new Change();
            String[] parts = filename.split("\\.")[0].split("_");
            change.setId(new BigDecimal(parts[0]));
            StringBuilder builder = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    builder.append(" ");
                }
                builder.append(parts[i]);
            }
            change.setDescription(builder.toString());
            change.setFilename(jarFile.getName() + "!" + entry.getName());
            return change;
        } catch (Exception e) {
            throw new MigrationException("Error parsing change from file.  Cause: " + e, e);
        }
    }

    public boolean accept(File dir, String name) {
        if (!name.endsWith(".jar")) return false;
        int ending = name.indexOf("-");
        if (ending > 0) {
            String prev = name.substring(0, ending);
            int beginning = prev.lastIndexOf(".");
            if (beginning > 0) {
                String artifactId = prev.substring(beginning + 1);
                return artifactId.endsWith(APP_JAR_SUFFIX);
            }
        }
        return false;
    }
}
