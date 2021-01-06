package com.kiwigrid.helm.maven.plugin.pojo;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugins.annotations.Parameter;

import lombok.Getter;
import lombok.Setter;

/**
 * POJO for Helm Plugin configuration
 *
 * @author Rob Vesse
 * @since 10.11.2020
 */
@Getter
@Setter
public class HelmPlugin {

	/**
	 * Name of plugin
	 */
	@Parameter(property = "helm.plugin.name", required = true)
	private String name;

	/**
	 * URL to install plugin from
	 */
	@Parameter(property = "helm.plugin.url", required = true)
	private String url;

	@Parameter(property = "helm.plugin.version", required = false)
	private String version;

	@Parameter(name = "helm.plugin.args")
	private String[] args;

	@Override
	public String toString() {
		return "[" + name + " (Version: " + (StringUtils.isNotEmpty(version) ? version : "latest") + ") / " + url + "]";
	}
}
