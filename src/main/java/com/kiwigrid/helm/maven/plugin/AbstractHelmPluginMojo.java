package com.kiwigrid.helm.maven.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

import com.kiwigrid.helm.maven.plugin.pojo.HelmPlugin;

/**
 * Base class for mojos that work with Helm plugins
 * 
 * @author rvesse
 *
 */
public abstract class AbstractHelmPluginMojo extends AbstractHelmMojo {

	protected Map<String, String> currentPlugins;
	
	@Parameter(property = "helm.plugins", required = true)
	protected HelmPlugin[] helmPlugins;

	public AbstractHelmPluginMojo() {
		super();
	}

	/**
	 * Finds the currently installed Helm plugins and parses their information
	 * 
	 * @return Map of plugin names to versions
	 * @throws MojoExecutionException
	 *             Thrown if plugins cannot be listed
	 */
	protected void findCurrentlyInstalledPlugins() throws MojoExecutionException {
		String checkCommand = getHelmExecuteablePath() + " plugin list";
		List<String> installedPlugins = callCli(checkCommand, "Unable to list currently installed plugins", false);

		this.currentPlugins = new HashMap<>();
		for (int i = 1; i < installedPlugins.size(); i++) {
			String[] pluginData = installedPlugins.get(i).split("\\s+", 3);

			currentPlugins.put(pluginData[0], pluginData[1]);
		}
	}

	/**
	 * Verifies whether a specified plugin is installed (included enforcing the requested version if present)
	 * 
	 * @param plugin
	 *            Plugin to check for
	 * @param failIfNotInstalled
	 *            Whether to fail and throw a {@link MojoExecutionException} if the plugin is not installed
	 * @return True if plugin is installed, false otherwise
	 * @throws MojoExecutionException
	 *             Thrown if the plugin version is mismatched or it was not installed and {@code failIfNotInstalled} was {@code true}
	 */
	protected boolean isPluginInstalled(HelmPlugin plugin, boolean failIfNotInstalled) throws MojoExecutionException {
		if (currentPlugins.containsKey(plugin.getName())) {
			// Is installed

			// Ensure version is correct if a specific version was requested
			if (StringUtils.isNotBlank(plugin.getVersion())) {
				if (!StringUtils.equals(plugin.getVersion(), currentPlugins.get(plugin.getName()))) {
					throw new MojoExecutionException("Plugin "
							+ plugin.getName() 
							+ " version is mismatched, requested "
							+ plugin.getVersion() 
							+ " but " 
							+ currentPlugins.get(plugin.getName())
							+ " is currently installed");
				}
			}

			return true;
		} else if (failIfNotInstalled) {
			throw new MojoExecutionException("Plugin " 
					+ plugin.getName()
					+ " is not currently installed, consider using the install-plugin goal to install it as part of your build");
		}

		// Not installed
		return false;
	}

}