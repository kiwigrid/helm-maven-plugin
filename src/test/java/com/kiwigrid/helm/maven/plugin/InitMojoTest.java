package com.kiwigrid.helm.maven.plugin;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.kiwigrid.helm.maven.plugin.junit.MojoExtension;
import com.kiwigrid.helm.maven.plugin.junit.MojoProperty;
import com.kiwigrid.helm.maven.plugin.junit.SystemPropertyExtension;
import com.kiwigrid.helm.maven.plugin.pojo.HelmRepository;
import com.kiwigrid.helm.maven.plugin.pojo.RepoType;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.Os;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

@ExtendWith({ SystemPropertyExtension.class, MojoExtension.class })
@MojoProperty(name = "helmDownloadUrl", value = "https://get.helm.sh/helm-v3.0.0-linux-amd64.tar.gz")
@MojoProperty(name = "chartDirectory", value = "junit-helm")
@MojoProperty(name = "chartVersion", value = "0.0.1")
public class InitMojoTest {

	@DisplayName("Init helm with different download urls.")
	@ParameterizedTest
	@ValueSource(strings = { "darwin", "linux", "windows" })
	public void initMojoHappyPathWhenDownloadHelm(String os, InitMojo mojo) throws Exception {

		// prepare execution
		doNothing().when(mojo).callCli(contains("helm "), anyString(), anyBoolean());
		// getHelmExecuteablePath is system-depending and has to be mocked for that reason
		// as SystemUtils.IS_OS_WINDOWS will always return false on a *NIX system
		doReturn(Paths.get("dummy/path/to/helm").toAbsolutePath()).when(mojo).getHelmExecuteablePath();
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL(os));

		// run init
		mojo.execute();

		// check helm file
		Path helm = Paths.get(mojo.getHelmExecutableDirectory(), "windows".equals(os) ? "helm.exe" : "helm")
				.toAbsolutePath();
		assertTrue(Files.exists(helm), "Helm executable not found at: " + helm);
	}

	@DisplayName("Init helm with a automatically detected URL")
	@Test
	public void autoDownloadHelm(InitMojo mojo) throws Exception {

		// prepare execution
		doNothing().when(mojo).callCli(contains("helm "), anyString(), anyBoolean());
		// getHelmExecuteablePath is system-depending and has to be mocked for that reason
		// as SystemUtils.IS_OS_WINDOWS will always return false on a *NIX system
		doReturn(Paths.get("dummy/path/to/helm").toAbsolutePath()).when(mojo).getHelmExecuteablePath();
		mojo.setHelmDownloadUrl(null);
		mojo.setHelmVersion("3.2.0");

		// run init
		mojo.execute();

		// check helm file
		Path helm = Paths.get(mojo.getHelmExecutableDirectory(), "windows".equals(Os.OS_FAMILY) ? "helm.exe" : "helm")
				.toAbsolutePath();
		assertTrue(Files.exists(helm), "Helm executable not found at: " + helm);
	}

	@Test
	public void verifyAddingStableByDefault(InitMojo mojo) throws Exception {

		// prepare execution
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(true);
		mojo.setAddUploadRepos(false);

		// run init
		mojo.execute();

		// check captured argument
		String helmDefaultCommand = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.findAny().orElseThrow(() -> new IllegalArgumentException("Only one helm repo command expected"));

		assertTrue(helmDefaultCommand.contains("repo add stable "+ InitMojo.STABLE_HELM_REPO), "Adding stable repo by default expected");
	}

	@Test
	public void verifyAddingUploadSnapshotRepoNoDefaultRepo(InitMojo mojo) throws Exception {

		// prepare execution
		final HelmRepository helmRepo = new HelmRepository();
		helmRepo.setType(RepoType.ARTIFACTORY);
		helmRepo.setName("my-artifactory-snapshot");
		helmRepo.setUrl("https://somwhere.com/repo");
		mojo.setUploadRepoSnapshot(helmRepo);
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(false);
		mojo.setAddUploadRepos(true);

		// run init
		mojo.execute();

		// check captured argument
		String helmCommand = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.findAny().orElseThrow(() -> new IllegalArgumentException("Only one helm repo command expected"));

		assertTrue(helmCommand.contains("repo add my-artifactory-snapshot https://somwhere.com/repo"), "Adding upload snapshot repo expected");
	}

	@Test
	public void verifyAddingUploadStableRepoNoDefaultRepo(InitMojo mojo) throws Exception {

		// prepare execution
		final HelmRepository helmRepo = new HelmRepository();
		helmRepo.setType(RepoType.ARTIFACTORY);
		helmRepo.setName("my-artifactory-stable");
		helmRepo.setUrl("https://somwhere.com/repo/stable");
		mojo.setUploadRepoStable(helmRepo);
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(false);
		mojo.setAddUploadRepos(true);

		// run init
		mojo.execute();

		// check captured argument
		String helmCommand = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.findAny().orElseThrow(() -> new IllegalArgumentException("Only one helm repo command expected"));

		assertTrue(helmCommand.contains("repo add my-artifactory-stable https://somwhere.com/repo/stable"), "Adding upload stable repo expected");
	}

	@Test
	public void verifyAddingUploadReposButNoRepoDefined(InitMojo mojo) throws Exception {

		// prepare execution
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(false);
		mojo.setAddUploadRepos(true);

		// run init
		mojo.execute();

		// check captured argument
		Optional<String> helmCommand = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.findFirst();

		assertFalse(helmCommand.isPresent(), "Adding repos not expected");
	}

	@Test
	public void verifyAddingUploadSnapshotStableRepoAndDefaultRepo(InitMojo mojo) throws Exception {

		// prepare execution
		final HelmRepository helmUploadStableRepo = new HelmRepository();
		helmUploadStableRepo.setType(RepoType.ARTIFACTORY);
		helmUploadStableRepo.setName("my-artifactory-stable");
		helmUploadStableRepo.setUrl("https://somwhere.com/repo/stable");
		mojo.setUploadRepoStable(helmUploadStableRepo);
		final HelmRepository helmUploadSnapshotRepo = new HelmRepository();
		helmUploadSnapshotRepo.setType(RepoType.ARTIFACTORY);
		helmUploadSnapshotRepo.setName("my-artifactory-snapshot");
		helmUploadSnapshotRepo.setUrl("https://somwhere.com/repo/snapshot");
		mojo.setUploadRepoSnapshot(helmUploadSnapshotRepo);
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(true);
		mojo.setAddUploadRepos(true);

		// run init7
		mojo.execute();

		// check captured commands
		Set<String> helmCommands = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.map(cmd -> cmd.substring(cmd.indexOf("repo add"))) //remove everything before repo add for easier verification
				.collect(Collectors.toSet());

		assertEquals(3, helmCommands.size(), "Expected 3 helm commands");
		assertTrue(helmCommands.contains("repo add my-artifactory-stable https://somwhere.com/repo/stable"), "Adding upload stable repo expected");
		assertTrue(helmCommands.contains("repo add my-artifactory-snapshot https://somwhere.com/repo/snapshot"), "Adding upload snapshot repo expected");
		assertTrue(helmCommands.contains("repo add stable "+ InitMojo.STABLE_HELM_REPO), "Adding helm stable repo expected");
	}

	@Test
	public void verifyAddingUploadSnapshotStableRepoSameRepoName(InitMojo mojo) throws Exception {

		// prepare execution
		final HelmRepository helmUploadStableRepo = new HelmRepository();
		helmUploadStableRepo.setType(RepoType.ARTIFACTORY);
		helmUploadStableRepo.setName("my-artifactory");
		helmUploadStableRepo.setUrl("https://somwhere.com/repo");
		mojo.setUploadRepoStable(helmUploadStableRepo);
		final HelmRepository helmUploadSnapshotRepo = new HelmRepository();
		helmUploadSnapshotRepo.setType(RepoType.ARTIFACTORY);
		helmUploadSnapshotRepo.setName("my-artifactory");
		helmUploadSnapshotRepo.setUrl("https://somwhere.com/repo");
		mojo.setUploadRepoSnapshot(helmUploadSnapshotRepo);
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(true);
		mojo.setAddUploadRepos(true);

		// run init7
		mojo.execute();

		// check captured commands
		Set<String> helmCommands = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.map(cmd -> cmd.substring(cmd.indexOf("repo add"))) //remove everything before repo add for easier verification
				.collect(Collectors.toSet());

		assertEquals(2, helmCommands.size(), "Expected 2 helm commands");
		assertTrue(helmCommands.contains("repo add my-artifactory https://somwhere.com/repo"), "Adding upload stable repo expected");
		assertTrue(helmCommands.contains("repo add stable "+ InitMojo.STABLE_HELM_REPO), "Adding helm stable repo expected");
	}

	@Test
	public void verifyAddingUploadSnapshotRepoStableNotPresent(InitMojo mojo) throws Exception {

		// prepare execution
		final HelmRepository helmUploadSnapshotRepo = new HelmRepository();
		helmUploadSnapshotRepo.setType(RepoType.ARTIFACTORY);
		helmUploadSnapshotRepo.setName("my-artifactory-snapshot");
		helmUploadSnapshotRepo.setUrl("https://somwhere.com/repo/snapshot");
		mojo.setUploadRepoSnapshot(helmUploadSnapshotRepo);
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(false);
		mojo.setAddUploadRepos(true);

		// run init
		mojo.execute();

		// check captured commands
		Set<String> helmCommands = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.map(cmd -> cmd.substring(cmd.indexOf("repo add"))) //remove everything before repo add for easier verification
				.collect(Collectors.toSet());

		assertEquals(1, helmCommands.size(), "Expected 1 helm command");
		assertTrue(helmCommands.contains("repo add my-artifactory-snapshot https://somwhere.com/repo/snapshot"), "Adding upload snapshot repo expected");
	}

	@Test
	public void verifyAddingUploadStableRepoSnapshotNotPresent(InitMojo mojo) throws Exception {

		// prepare execution
		final HelmRepository helmUploadStableRepo = new HelmRepository();
		helmUploadStableRepo.setType(RepoType.ARTIFACTORY);
		helmUploadStableRepo.setName("my-artifactory-stable");
		helmUploadStableRepo.setUrl("https://somwhere.com/repo/stable");
		mojo.setUploadRepoStable(helmUploadStableRepo);
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setAddDefaultRepo(false);
		mojo.setAddUploadRepos(true);

		// run init
		mojo.execute();

		// check captured commands
		Set<String> helmCommands = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe repo" : "helm repo"))
				.map(cmd -> cmd.substring(cmd.indexOf("repo add"))) //remove everything before repo add for easier verification
				.collect(Collectors.toSet());

		assertEquals(1, helmCommands.size(), "Expected 1 helm command");
		assertTrue(helmCommands.contains("repo add my-artifactory-stable https://somwhere.com/repo/stable"), "Adding upload stable repo expected");
	}

	@Test
	public void verifyCustomConfigOptions(InitMojo mojo) throws Exception {

		// prepare execution
		ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
		doNothing().when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
		mojo.setHelmDownloadUrl(getOsSpecificDownloadURL());
		mojo.setRegistryConfig("/path/to/my/registry.json");
		mojo.setRepositoryCache("/path/to/my/repository/cache");
		mojo.setRepositoryConfig("/path/to/my/repositories.yaml");
		mojo.setAddDefaultRepo(true);

		// run init
		mojo.execute();

		// check captured argument
		List<String> helmCommands = helmCommandCaptor.getAllValues()
				.stream()
				.filter(cmd -> cmd.contains(Os.OS_FAMILY == Os.FAMILY_WINDOWS ? "helm.exe " : "helm "))
				.collect(Collectors.toList());
		assertEquals(1, helmCommands.size(), "Only helm init command expected");
		String helmDefaultCommand = helmCommands.get(0);
		assertTrue(helmDefaultCommand.contains("--registry-config=/path/to/my/registry.json"), "Option 'registry-config' not set");
		assertTrue(helmDefaultCommand.contains("--repository-cache=/path/to/my/repository/cache"), "Option 'repository-cache' not set");
		assertTrue(helmDefaultCommand.contains("--repository-config=/path/to/my/repositories.yaml"), "Option 'repository-config' not set");
	}

	@Test
	public void verifyLocalHelmBinaryUsage(InitMojo mojo) throws MojoExecutionException {
		// Because the download URL is hardcoded to linux, only proceed if the OS is indeed linux.
		assumeTrue(isOSUnix());

		final URL resource = this.getClass().getResource("helm.tar.gz");
		final String helmExecutableDir = new File(resource.getFile()).getParent();
		mojo.callCli("tar -xf "
				+ helmExecutableDir
				+ File.separator
				// flatten directory structure using --strip to get helm executeable on basedir, see https://www.systutorials.com/docs/linux/man/1-tar/#lbAS
				+ "helm.tar.gz --strip=1 --directory="
				+ helmExecutableDir, "Unable to unpack helm to " + helmExecutableDir, false);

		// configure mojo
		mojo.setUseLocalHelmBinary(true);
		mojo.setHelmExecutableDirectory(helmExecutableDir);

		// execute
		mojo.execute();
	}

	private boolean isOSUnix() {
		return System.getProperty("os.name").matches(".*n[i|u]x.*");
	}

	private String getOsSpecificDownloadURL() {
		String osForDownload;
		switch (Os.OS_FAMILY) {
		case Os.FAMILY_UNIX:
			osForDownload = "linux";
			break;
		case Os.FAMILY_MAC:
			osForDownload = "darwin";
			break;
		default:
			osForDownload = Os.OS_FAMILY;
		}

		return getOsSpecificDownloadURL(osForDownload);
	}

	private String getOsSpecificDownloadURL(final String os) {
		return "https://get.helm.sh/helm-v3.0.0-" + os + "-amd64." + ("windows".equals(os) ? "zip" : "tar.gz");
	}
}
