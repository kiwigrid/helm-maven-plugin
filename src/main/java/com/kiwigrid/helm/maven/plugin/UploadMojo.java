package com.kiwigrid.helm.maven.plugin;

import com.kiwigrid.helm.maven.plugin.exception.BadUploadException;
import com.kiwigrid.helm.maven.plugin.pojo.HelmRepository;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Mojo for uploading to helm repo (e.g. chartmuseum)
 *
 * @author Fabian Schlegel
 * @since 02.01.18
 */
@Mojo(name = "upload", defaultPhase = LifecyclePhase.DEPLOY)
public class UploadMojo extends AbstractHelmMojo {

	@Parameter(property = "helm.upload.skip", defaultValue = "false")
	private boolean skipUpload;

	public void execute()
			throws MojoExecutionException
	{
		if (skip || skipUpload) {
			getLog().info("Skip upload");
			return;
		}
		getLog().info("Uploading to " + getHelmUploadUrl() + "\n");
		for (String chartPackageFile : getChartTgzs(getOutputDirectory())) {
			getLog().info("Uploading " + chartPackageFile + "...");
			try {
				uploadSingle(chartPackageFile);
			} catch (BadUploadException | IOException e) {
				getLog().error(e.getMessage());
				throw new MojoExecutionException("Error uploading " + chartPackageFile + " to " + getHelmUploadUrl(),
						e);
			}
		}
	}

	protected void uploadSingle(String file) throws IOException, BadUploadException, MojoExecutionException {
		final File fileToUpload = new File(file);
		final HelmRepository uploadRepo = getHelmUploadRepo();

		HttpURLConnection connection;

		if(uploadRepo.getType() == null){
			throw new IllegalArgumentException("Repository type missing. Check your plugin configuration.");
		}

		switch (uploadRepo.getType()) {
		case ARTIFACTORY:
			connection = getConnectionForUploadToArtifactory(fileToUpload);
			break;
		case CHARTMUSEUM:
			connection = getConnectionForUploadToChartmuseum();
			break;
		case NEXUS:
			connection = getConnectionForUploadToNexus(fileToUpload);
			break;
		default:
			throw new IllegalArgumentException("Unsupported repository type for upload.");
		}

		try (FileInputStream fileInputStream = new FileInputStream(fileToUpload)) {
			IOUtils.copy(fileInputStream, connection.getOutputStream());
		}
		if (connection.getResponseCode() >= 300) {
			String response;
			if (connection.getErrorStream() != null) {
				response = IOUtils.toString(connection.getErrorStream(), Charset.defaultCharset());
			} else if (connection.getInputStream() != null) {
				response = IOUtils.toString(connection.getInputStream(), Charset.defaultCharset());
			} else {
				response = "No details provided";
			}
			throw new BadUploadException(response);
		} else {
			String message = Integer.toString(connection.getResponseCode());
			if (connection.getInputStream() != null) {
				message += " - " + IOUtils.toString(connection.getInputStream(), Charset.defaultCharset());
			}
			getLog().info(message);
		}
		connection.disconnect();
	}

	protected HttpURLConnection getConnectionForUploadToChartmuseum() throws IOException, MojoExecutionException {
		final HttpURLConnection connection = (HttpURLConnection) new URL(getHelmUploadUrl()).openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/gzip");

		setBasicAuthHeader(connection);

		return connection;
	}

	private void setBasicAuthHeader(HttpURLConnection connection) throws MojoExecutionException {
		PasswordAuthentication authentication = getAuthentication(getHelmUploadRepo());
		if (authentication != null) {
			String encoded = Base64.getEncoder().encodeToString((authentication.getUserName() + ":" + new String(authentication.getPassword())).getBytes(StandardCharsets.UTF_8));  //Java 8
			connection.setRequestProperty("Authorization", "Basic " + encoded);
		}
	}

	protected HttpURLConnection getConnectionForUploadToArtifactory(File file) throws IOException, MojoExecutionException {
		String uploadUrl = getHelmUploadUrl();
		// Append slash if not already in place
		if (!uploadUrl.endsWith("/")) {
			uploadUrl += "/";
		}
		uploadUrl = uploadUrl + file.getName();

		final HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("PUT");
		connection.setRequestProperty("Content-Type", "application/gzip");

		verifyAndSetAuthentication(true);

		return connection;
	}

	protected HttpURLConnection getConnectionForUploadToNexus(File file) throws IOException, MojoExecutionException {
		String uploadUrl = getHelmUploadUrl();
		// Append slash if not already in place
		if (!uploadUrl.endsWith("/")) {
			uploadUrl += "/";
		}
		uploadUrl = uploadUrl + file.getName();

		final HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("PUT");
		connection.setRequestProperty("Content-Type", "application/gzip");

		verifyAndSetAuthentication(false);

		return connection;
	}

	/**
	 *
	 * @param requireCredentials The need for credentials depends on how the repository is configured.
	 *                           For instance on nexus it is possible to configure a repository without authentication
	 * @throws MojoExecutionException
	 */
	private void verifyAndSetAuthentication(boolean requireCredentials) throws MojoExecutionException {

		PasswordAuthentication authentication = getAuthentication(getHelmUploadRepo());
		if (authentication != null) {
			Authenticator.setDefault(new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return authentication;
				}
			});
		} else if (requireCredentials) {
			throw new IllegalArgumentException("Credentials has to be configured for uploading to Artifactory.");
		}

	}

}
