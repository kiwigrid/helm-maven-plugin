package com.kiwigrid.helm.maven.plugin;

import static java.nio.file.Files.write;
import static java.util.Arrays.asList;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AbstractHelmMojoTest {

    private NoopHelmMojo subjectSpy;
    private Path testPath;
    private Path testHelmExecutablePath;

    private String chartDir;
    private String excludeDir1;
    private String excludeDir2;

    @BeforeEach
    void setUp() throws IOException {

        chartDir = getBaseChartsDirectory().toString();
        excludeDir1 = chartDir + File.separator + "exclude1";
        excludeDir2= chartDir + File.separator + "exclude2";

        subjectSpy = Mockito.spy(new NoopHelmMojo());
        testPath = Files.createTempDirectory("test").toAbsolutePath();
        testHelmExecutablePath = testPath.resolve(SystemUtils.IS_OS_WINDOWS ? "helm.exe" : "helm");
    }

    @Test
    void getChartDirectoriesReturnChartDirectories() throws MojoExecutionException {

        List<String> chartDirectories = subjectSpy.getChartDirectories(chartDir);
        List<String> expected = asList(chartDir, excludeDir1, excludeDir2);

        assertTrue(chartDirectories.containsAll(expected), "Charts dirs: " + chartDirectories + ", should contain all expected dirs: " + expected);
    }


    @Test
    void getChartDirectoriesReturnChartDirectoriesWithPlainExclusion() throws MojoExecutionException {
        Path baseChartsDirectory = getBaseChartsDirectory();

        subjectSpy.setExcludes(new String[]{excludeDir1});

        List<String> chartDirectories = subjectSpy.getChartDirectories(baseChartsDirectory.toString());

        assertFalse(chartDirectories.contains(excludeDir1), "Charts dirs [" + chartDirectories + "] should not contain excluded dirs [" + excludeDir1 + "]");
        assertTrue(chartDirectories.contains(excludeDir2), "Charts dirs [" + chartDirectories + "] should contain not excluded dirs [" + excludeDir2 + "]");
    }


    @Test
    void getChartDirectoriesReturnChartDirectoriesWithAntPatternsExclusion() throws MojoExecutionException {
        Path baseChartsDirectory = getBaseChartsDirectory();

        subjectSpy.setExcludes(new String[]{"**" + File.separator + "exclude*"});

        List<String> chartDirectories = subjectSpy.getChartDirectories(baseChartsDirectory.toString());

        assertFalse(chartDirectories.contains(excludeDir1), "Charts dirs [" + chartDirectories + "] should not contain excluded dirs [" + excludeDir1 + "]");
        assertFalse(chartDirectories.contains(excludeDir2), "Charts dirs [" + chartDirectories + "] should not contain excluded dirs [" + excludeDir2 + "]");
    }

    @Test
    void formatIfValueIsNotEmpty() {
        String formatString = "Test %s data";
        String value = "format";

        String formatWithValue = subjectSpy.formatIfValueIsNotEmpty(formatString, value);
        String formatWithNullValue = subjectSpy.formatIfValueIsNotEmpty(formatString, null);
        String formatWithEmptyValue = subjectSpy.formatIfValueIsNotEmpty(formatString, "");

        assertEquals("Test format data", formatWithValue);
        assertEquals("", formatWithNullValue);
        assertEquals("", formatWithEmptyValue);
    }

    @Nested
    class WhenUseLocalBinaryAndAutoDetectIsEnabled {

        @BeforeEach
        void setUp() {

            subjectSpy.setUseLocalHelmBinary(true);
            subjectSpy.setAutoDetectLocalHelmBinary(true);
            doReturn(new String[]{ testPath.toAbsolutePath().toString() }).when(subjectSpy).getPathsFromEnvironmentVariables();
        }

        @Test
        void helmIsAutoDetectedFromPATH() throws MojoExecutionException, IOException {

            final Path expectedPath = addHelmToTestPath();
            assertEquals(expectedPath, subjectSpy.getHelmExecuteablePath());
        }

        @Test
        void executionFailsWhenHelmIsNotFoundInPATH() {

            final MojoExecutionException exception = assertThrows(MojoExecutionException.class, subjectSpy::getHelmExecuteablePath);
            assertTrue(exception.getMessage().contains("not found"));
        }

        @Test
        void helmIsAutoDetectedEvenWhenExecutableDirectoryIsConfigured() throws IOException, MojoExecutionException {

            final String explicitExecutableDirectory = "/fish/in/da/sea";
            subjectSpy.setHelmExecutableDirectory(explicitExecutableDirectory);
            final Path expectedPath = addHelmToTestPath();
            assertEquals(expectedPath, subjectSpy.getHelmExecuteablePath());
            assertNotEquals(explicitExecutableDirectory, subjectSpy.getHelmExecuteablePath());
        }
    }

    @Nested
    class WhenExecutableDirectoryIsSpecifiedAndUseLocalBinaryIsDisabled {

        @BeforeEach
        void setUp() {

            subjectSpy.setUseLocalHelmBinary(false);
            subjectSpy.setHelmExecutableDirectory(testPath.toString());
        }

        @Test
        void helmIsInTheExplicitlyConfiguredDirectory() throws MojoExecutionException, IOException {

            final Path expectedPath = addHelmToTestPath();
            assertEquals(expectedPath, subjectSpy.getHelmExecuteablePath());
        }

        @Test
        void executionFailsWhenHelmIsNotFoundInConfiguredDirectory() {

            final MojoExecutionException exception = assertThrows(MojoExecutionException.class, subjectSpy::getHelmExecuteablePath);
            assertTrue(exception.getMessage().contains("not found"));
        }
    }

    private Path addHelmToTestPath() throws IOException { return write(testHelmExecutablePath, new byte[]{}); }

    private Path getBaseChartsDirectory() {
    	return new File(getClass().getResource("Chart.yaml").getFile()).toPath().getParent();
    }

    @AfterEach
    void tearDown() { deleteQuietly(testPath.toFile()); }

    private static class NoopHelmMojo extends AbstractHelmMojo {

        @Override
        public void execute() { /* Noop. */ }
    }
}
