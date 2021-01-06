package com.kiwigrid.helm.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import com.kiwigrid.helm.maven.plugin.pojo.HelmPlugin;

/**
 * Mojo for installing Helm plugins
 *
 * @author Rob Vesse
 * @since 10.11.2020
 */
@Mojo(name = "invoke-plugin", defaultPhase = LifecyclePhase.TEST)
public class InvokePluginMojo extends AbstractHelmPluginMojo {

	@Parameter(property = "helm.invoke-plugin.skip", defaultValue = "false")
	private boolean skipInvokePlugin;

	@Parameter(property = "helm.invoke-plugin.per-chart", required = false, defaultValue = "true")
	private boolean perChart = true;

	public void execute() throws MojoExecutionException {
		if (skip || skipInvokePlugin) {
			getLog().info("Skip invoke plugin");
			return;
		}
		
		findCurrentlyInstalledPlugins();
		
		for (HelmPlugin plugin : helmPlugins) {
			getLog().info("Invoking plugin " + plugin + "...");

			// Ensure it is actually installed, if not fail
			isPluginInstalled(plugin, true);

			// Invoke the plugin, either per-chart or once depending on execution configuration
			if (perChart) {
				for (String inputDirectory : getChartDirectories(getChartDirectory())) {
					invokePlugin(plugin, new String[] { inputDirectory });
				}
			} else {
				invokePlugin(plugin, new String[0]);
			}
		}
	}

	protected void invokePlugin(HelmPlugin plugin, String[] extraArgs) throws MojoExecutionException {
		// Attempt to invoke the plugin
		String helmCommand = getHelmExecuteablePath() + " " + plugin.getName();
		for (String arg : plugin.getArgs()) {
			helmCommand += " " + arg;
		}
		for (String extraArg : extraArgs) {
			helmCommand += " " + extraArg;
		}
		callCli(helmCommand, "Unable to invoke plugin " + plugin, true);
	}

}
