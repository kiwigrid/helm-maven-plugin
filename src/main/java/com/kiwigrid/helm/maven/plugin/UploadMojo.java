package com.kiwigrid.helm.maven.plugin;

import com.kiwigrid.helm.maven.plugin.exception.BadUploadException;
import com.kiwigrid.helm.maven.plugin.pojo.HelmRepository;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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

		HttpURLConnection connection = null;

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
        case HARBOR:
			uploadToHarbor(fileToUpload);
			return;

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
		HelmRepository helmUploadRepo = getHelmUploadRepo();
		if (StringUtils.isNotEmpty(helmUploadRepo.getUsername()) && StringUtils.isNotEmpty(helmUploadRepo.getPassword())) {
			String encoded = Base64.getEncoder().encodeToString((helmUploadRepo.getUsername() + ":" + helmUploadRepo.getPassword()).getBytes(StandardCharsets.UTF_8));  //Java 8
			connection.setRequestProperty("Authorization", "Basic " + encoded);
		}
		else
		{
			PasswordAuthentication authentication = getAuthentication(getHelmUploadRepo());
			if (authentication == null) {
				throw new IllegalArgumentException("Credentials has to be configured for uploading to Artifactory.");
			}
			connection.setRequestProperty(authentication.getUserName(), String.valueOf(authentication.getPassword()));
		}
	}

	private void setBasicAuthHeaderApacheHttpClient(HttpPost post) throws MojoExecutionException {
		HelmRepository helmUploadRepo = getHelmUploadRepo();
		if (StringUtils.isNotEmpty(helmUploadRepo.getUsername()) && StringUtils.isNotEmpty(helmUploadRepo.getPassword())) {
			post.addHeader(createAddHeader(helmUploadRepo.getUsername(), helmUploadRepo.getPassword()));
		}
		else
		{
			PasswordAuthentication authentication = getAuthentication(getHelmUploadRepo());
			if (authentication == null) {
				throw new IllegalArgumentException("Credentials has to be configured for uploading to Artifactory.");
			}
			post.addHeader(createAddHeader(authentication.getUserName(), String.valueOf(authentication.getPassword())));
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

		verifyAndSetAuthentication();

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

		setBasicAuthHeader(connection);

		return connection;
	}

	protected void uploadToHarbor(File file) throws IOException, BadUploadException, MojoExecutionException {
		HttpPost post = new HttpPost(getHelmUploadUrl());
		FileBody fileBody = new FileBody(file, ContentType.MULTIPART_FORM_DATA);
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		builder.addPart("chart", fileBody);
		HttpEntity entity = builder.build();
		post.setEntity(entity);
		setBasicAuthHeaderApacheHttpClient(post);
		CloseableHttpClient httpclient = HttpClients.custom()
			.setDefaultRequestConfig(RequestConfig.custom()
				.setCookieSpec(CookieSpecs.STANDARD).build())
			.build();
		HttpResponse response = httpclient.execute(post);

		if (response.getStatusLine().getStatusCode() >= 300) {
			throw new BadUploadException(EntityUtils.toString(response.getEntity()));
		} else {
			getLog().info(response.getStatusLine().getStatusCode() + " - " + EntityUtils.toString(response.getEntity()));
		}
	}

	private Header createAddHeader(String username, String password) {
		String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
		return new BasicHeader("Authorization", "Basic " + encoded);
	}

	private void verifyAndSetAuthentication() throws MojoExecutionException {

		PasswordAuthentication authentication = getAuthentication(getHelmUploadRepo());
		if (authentication == null) {
			throw new IllegalArgumentException("Credentials has to be configured for uploading to Artifactory.");
		}

		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return authentication;
			}
		});
	}
}
