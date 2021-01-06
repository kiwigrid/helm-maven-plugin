package com.kiwigrid.helm.maven.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.nio.file.Paths;
import java.util.Collections;

import org.codehaus.plexus.util.Os;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.kiwigrid.helm.maven.plugin.junit.MojoExtension;
import com.kiwigrid.helm.maven.plugin.junit.MojoProperty;
import com.kiwigrid.helm.maven.plugin.junit.SystemPropertyExtension;
import com.kiwigrid.helm.maven.plugin.pojo.ValueOverride;

@ExtendWith({SystemPropertyExtension.class, MojoExtension.class})
@MojoProperty(name = "helmDownloadUrl", value = "https://get.helm.sh/helm-v2.14.3-linux-amd64.tar.gz")
@MojoProperty(name = "chartDirectory", value = "junit-chart")
@MojoProperty(name = "chartVersion", value = "0.0.1")
public class LintMojoTest {
    @Test
    public void valuesFile(LintMojo mojo) throws Exception {
        ValueOverride override = new ValueOverride();
        override.setYamlFile("overrideValues.yaml");
        mojo.setValues(override);
        mojo.setChartDirectory(Paths.get(getClass().getResource("Chart.yaml").toURI()).getParent().toString());

        ArgumentCaptor<String> helmCommandCaptor = ArgumentCaptor.forClass(String.class);
        doReturn(Collections.emptyList()).when(mojo).callCli(helmCommandCaptor.capture(), anyString(), anyBoolean());
        doReturn(Paths.get("helm" + (Os.OS_FAMILY == Os.FAMILY_WINDOWS ? ".exe" : ""))).when(mojo).getHelmExecuteablePath();

        mojo.execute();

        assertTrue(helmCommandCaptor.getValue().contains("--values overrideValues.yaml"));
    }
}
