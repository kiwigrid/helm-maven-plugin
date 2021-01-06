package com.kiwigrid.helm.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

import com.kiwigrid.helm.maven.plugin.pojo.HelmPlugin;

/**
 * Mojo for installing Helm plugins
 *
 * @author Rob Vesse
 * @since 10.11.2020
 */
@Mojo(name = "install-plugin", defaultPhase = LifecyclePhase.INITIALIZE)
public class InstallPluginMojo extends AbstractHelmPluginMojo {

	@Parameter(property = "helm.install-plugin.skip", defaultValue = "false")
	private boolean skipInstallPlugin;

	public void execute() throws MojoExecutionException {
		if (skip || skipInstallPlugin) {
			getLog().info("Skip install plugin");
			return;
		}

		findCurrentlyInstalledPlugins();

		for (HelmPlugin plugin : helmPlugins) {
			getLog().info("Installing  plugin " + plugin + "...");

			if (isPluginInstalled(plugin, false)) {
				// Plugin is already installed
				getLog().info("Plugin is already installed, nothing to do");
			} else {
				// Attempt to install the plugin
				String helmCommand = getHelmExecuteablePath() 
						+ " plugin " 
						+ " install "
						+ (StringUtils.isNotEmpty(getRegistryConfig()) ? " --registry-config=" + getRegistryConfig() : "")
						+ (StringUtils.isNotEmpty(getRepositoryCache()) ? " --repository-cache=" + getRepositoryCache() : "")
						+ (StringUtils.isNotEmpty(getRepositoryConfig()) ? " --repository-config=" + getRepositoryConfig() : "")
						+ plugin.getUrl();
				if (StringUtils.isNotEmpty(plugin.getVersion())) {
					helmCommand += " --version "
							+ plugin.getVersion();
				}
				callCli(helmCommand, "Unable to install plugin " + plugin, true);
			}
		}
	}

}
