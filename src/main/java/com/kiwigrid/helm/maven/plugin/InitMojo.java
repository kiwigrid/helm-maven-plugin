package com.kiwigrid.helm.maven.plugin;

import com.kiwigrid.helm.maven.plugin.pojo.HelmRepository;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mojo for initializing helm
 *
 * @author Fabian Schlegel
 * @since 06.11.17
 */
@Mojo(name = "init", defaultPhase = LifecyclePhase.INITIALIZE)
public class InitMojo extends AbstractHelmMojo {

	@Parameter(property = "helm.init.skipRefresh", defaultValue = "false")
	private boolean skipRefresh;
	@Parameter(property = "helm.init.skip", defaultValue = "false")
	private boolean skipInit;

	public void execute() throws MojoExecutionException {

		if (skip || skipInit) {
			getLog().info("Skip init");
			return;
		}

		getLog().info("Initializing Helm...");
		Path outputDirectory = Paths.get(getOutputDirectory()).toAbsolutePath();
		if (!Files.exists(outputDirectory)) {
			getLog().info("Creating output directory...");
			try {
				Files.createDirectories(outputDirectory);
			} catch (IOException e) {
				throw new MojoExecutionException("Unable to create output directory at " + outputDirectory, e);
			}
		}

		if(isUseLocalHelmBinary()) {
			verifyLocalHelmBinary();
			getLog().info("Using local HELM binary ["+ replaceSpaces(getHelmExecuteablePath()) +"]");
		} else {
			downloadAndUnpackHelm();
		}

		if (getHelmExtraRepos() != null) {
			for (HelmRepository repository : getHelmExtraRepos()) {
				getLog().info("Adding repo " + repository);
				PasswordAuthentication auth = getAuthentication(repository);

				List<String> command = new ArrayList<>();
				command.add(getHelmExecuteablePath().toString());
				command.add("repo");
				command.add("add");
				command.add(repository.getName());
				command.add(repository.getUrl());
				if (StringUtils.isNotEmpty(getHelmHomeDirectory())) command.add("--home=" + getHelmHomeDirectory());
				if (auth != null) {
					command.add("--username=" + auth.getUserName());
					command.add("--password=" + String.valueOf(auth.getPassword()));
				}

				callCli(command, "Unable add repo", false);
			}
		}
	}

	protected void downloadAndUnpackHelm() throws MojoExecutionException {

		Path directory = Paths.get(getHelmExecutableDirectory());
		if (Files.exists(directory.resolve(SystemUtils.IS_OS_WINDOWS ? "helm.exe" : "helm"))) {
			getLog().info("Found helm executable, skip init.");
			return;
		}

		getLog().info("Downloading Helm ...");
		boolean found = false;
		try (InputStream dis = new URL(getHelmDownloadUrl()).openStream();
			 InputStream cis = createCompressorInputStream(dis);
			 ArchiveInputStream is = createArchiverInputStream(cis)) {

			// create directory if not present
			Files.createDirectories(directory);

			// get helm executable entry
			ArchiveEntry entry = null;
			while ((entry = is.getNextEntry()) != null) {

				String name = entry.getName();
				if (entry.isDirectory() || (!name.endsWith("helm.exe") && !name.endsWith("helm"))) {
					getLog().debug("Skip archive entry with name: " + name);
					continue;
				}

				getLog().debug("Use archive entry with name: " + name);
				Path helm = directory.resolve(name.endsWith(".exe") ? "helm.exe" : "helm");
				try (FileOutputStream output = new FileOutputStream(helm.toFile())) {
					IOUtils.copy(is, output);
				}

				addExecPermission(helm);

				found = true;
				break;
			}

		} catch (IOException e) {
			throw new MojoExecutionException("Unable to download and extract helm executable.", e);
		} 

		if (!found) {
			throw new MojoExecutionException("Unable to find helm executable in tar file.");
		}

		initHelmClient();
	}

	private void addExecPermission(final Path helm) throws IOException {
		Set<String> fileAttributeView = FileSystems.getDefault().supportedFileAttributeViews();

		if (fileAttributeView.contains("posix")) {
			final Set<PosixFilePermission> permissions;
			try {
				permissions = Files.getPosixFilePermissions(helm);
			} catch (UnsupportedOperationException e) {
				getLog().debug("Exec file permission is not set", e);
				return;
			}
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
			Files.setPosixFilePermissions(helm, permissions);

		} else if (fileAttributeView.contains("acl")) {
			String username = System.getProperty("user.name");
			UserPrincipal userPrincipal = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(username);
			AclEntry aclEntry = AclEntry.newBuilder().setPermissions(AclEntryPermission.EXECUTE).setType(AclEntryType.ALLOW).setPrincipal(userPrincipal).build();

			AclFileAttributeView acl = Files.getFileAttributeView(helm, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			List<AclEntry> aclEntries = acl.getAcl();
			aclEntries.add(aclEntry);
			acl.setAcl(aclEntries);
		}
	}

	private void verifyLocalHelmBinary() throws MojoExecutionException {
		List<String> command = new ArrayList<>();
		command.add(replaceSpaces(getHelmExecuteablePath()));
		command.add("version");
		command.add("--client");

		callCli(command, "Unable to verify local HELM binary", false);

		initHelmClient();
	}

	public boolean isSkipRefresh() {
		return skipRefresh;
	}

	public void setSkipRefresh(boolean skipRefresh) {
		this.skipRefresh = skipRefresh;
	}

	private void initHelmClient() throws MojoExecutionException {
		getLog().info("Run helm init...");

		List<String> command = new ArrayList<>();
		command.add(replaceSpaces(getHelmExecuteablePath()));
		command.add("init");
		command.add("--client-only");
		if (skipRefresh) command.add("--skip-refresh");
		if (StringUtils.isNotEmpty(getHelmHomeDirectory())) command.add("--home=" + getHelmHomeDirectory());

		getLog().info("Running: initHelmClient");
		callCli(command, "Unable to call helm init", false);
	}

	private ArchiveInputStream createArchiverInputStream(InputStream is) throws MojoExecutionException {
		// Stream must support mark to allow for auto detection of archiver
		if (!is.markSupported()) {
			is = new BufferedInputStream(is);
		}

		try {
			ArchiveStreamFactory archiveStreamFactory = new ArchiveStreamFactory();
			return archiveStreamFactory.createArchiveInputStream(is);

		} catch (ArchiveException e) {
			throw new MojoExecutionException("Unsupported archive type downloaded", e);
		}
	}

	private InputStream createCompressorInputStream(InputStream is) throws MojoExecutionException {
		// Stream must support mark to allow for auto detection of compressor
		if (!is.markSupported()) {
			is = new BufferedInputStream(is);
		}

		// Detect if stream is compressed
		String compressorType = null;
		try {
			compressorType = CompressorStreamFactory.detect(is);
		} catch (CompressorException e) {
			getLog().debug("Unknown type of compressed stream", e);
		}

		// If compressed then wrap with compressor stream
		if (compressorType != null) {
			try {
				CompressorStreamFactory compressorFactory = new CompressorStreamFactory();
				return compressorFactory.createCompressorInputStream(compressorType, is);
			} catch (CompressorException e) {
				throw new MojoExecutionException("Unsupported compressor type: " + compressorType);
			}
		}

		return is;
	}
}
