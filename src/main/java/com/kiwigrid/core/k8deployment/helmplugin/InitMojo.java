package com.kiwigrid.core.k8deployment.helmplugin;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.StringUtils;

/**
 * Mojo for initializing helm
 *
 * @author Fabian Schlegel
 * @since 06.11.17
 */
@Mojo(name = "init", defaultPhase = LifecyclePhase.INITIALIZE)
public class InitMojo extends AbstractHelmMojo {

	private static final String HELM_OS_LOCATION;

	static {
		String osName = System.getProperty("os.name");

		if (osName.toLowerCase().contains("mac")) {
			HELM_OS_LOCATION = "darwin-amd64";
		} else {
			HELM_OS_LOCATION = "linux-amd64";
		}
	}

	public void execute()
			throws MojoExecutionException
	{
		getLog().info("Initializing Helm...");
		getLog().info("Creating output directory...");
		callCli("mkdir -p " + getOutputDirectory(), "Unable to create output directory at " + getOutputDirectory(),
				false);
		getLog().info("Downloading Helm...");
		callCli("wget -qO "
						+ getHelmExecuteableDirectory()
						+ File.separator
						+ "helm.tar.gz "
						+ getHelmDownloadUrl(),
				"Unable to download helm", false);
		getLog().info("Unpacking Helm...");
		callCli("tar -xf "
				+ getHelmExecuteableDirectory()
				+ File.separator
				+ "helm.tar.gz -C "
				+ getHelmExecuteableDirectory(), "Unable to unpack helm to " + getHelmExecuteableDirectory(), false);
		getLog().info("Run helm init...");
		callCli(getHelmExecuteableDirectory()
						+ File.separator
						+ HELM_OS_LOCATION
						+ File.separator
						+ "helm init --client-only"
						+ (StringUtils.isNotEmpty(getHelmHomeDirectory()) ? " --home=" + getHelmHomeDirectory() : ""),
				"Unable to call helm init",
				false);

		getLog().info("Enable incubator repo...");
		callCli(getHelmExecuteableDirectory()
						+ File.separator
						+ HELM_OS_LOCATION
						+ File.separator
						+ "helm repo add incubator https://kubernetes-charts-incubator.storage.googleapis.com",
				"Unable add incubator repo",
				false);
	}
}
