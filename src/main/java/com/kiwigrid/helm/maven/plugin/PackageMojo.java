package com.kiwigrid.helm.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

/**
 * Mojo for packaging charts
 *
 * @author Fabian Schlegel
 * @since 06.11.17
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractHelmMojo {

	@Parameter(property = "helm.package.skip", defaultValue = "false")
	private boolean skipPackage;

	@Parameter(property = "helm.package.keyring")
	private String keyring;

	@Parameter(property = "helm.package.key")
	private String key;

	@Parameter(property = "helm.package.passphrase")
	private String passphrase;

	public void execute()
			throws MojoExecutionException
	{
		if (skip || skipPackage) {
			getLog().info("Skip package");
			return;
		}

		for (String inputDirectory : getChartDirectories(getChartDirectory())) {

			getLog().info("Packaging chart " + inputDirectory + "...");

			String helmCommand = getHelmExecuteablePath()
					+ " package "
					+ inputDirectory
					+ " -d "
					+ getOutputDirectory()
					+ (StringUtils.isNotEmpty(getRegistryConfig()) ? " --registry-config=" + getRegistryConfig() : "")
					+ (StringUtils.isNotEmpty(getRepositoryCache()) ? " --repository-cache=" + getRepositoryCache() : "")
					+ (StringUtils.isNotEmpty(getRepositoryConfig()) ? " --repository-config=" + getRepositoryConfig() : "");

			if (getChartVersion() != null) {
				getLog().info(String.format("Setting chart version to %s", getChartVersion()));
				helmCommand = helmCommand + " --version " + getChartVersion();
			}

			if (getAppVersion() != null) {
				getLog().info(String.format("Setting App version to %s", getAppVersion()));
				helmCommand = helmCommand + " --app-version " + getAppVersion();
			}

			String stdin = null;
			if (isSignEnabled()) {
				getLog().info("Enable signing");
				helmCommand = helmCommand + " --sign --keyring " + keyring + " --key " + key;
				if (StringUtils.isNotEmpty(passphrase)) {
					helmCommand += " --passphrase-file -";
					stdin = passphrase;
				}
			}

			callCli(helmCommand, "Unable to package chart at " + inputDirectory, true, stdin);
		}
	}

	private boolean isSignEnabled() {
		return StringUtils.isNotEmpty(keyring) && StringUtils.isNotEmpty(key);
	}

}
