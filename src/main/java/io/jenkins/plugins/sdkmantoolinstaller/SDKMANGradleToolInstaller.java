package io.jenkins.plugins.sdkmantoolinstaller;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import java.io.IOException;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class SDKMANGradleToolInstaller extends SDKMANToolInstaller {

    private static final String GRADLE_INSTALLATION_CLASS = "hudson.plugins.gradle.GradleInstallation";

    @DataBoundConstructor
    public SDKMANGradleToolInstaller(String id) {
        super(id);
    }

    @Override
    protected String sdkCandidate() {
        return "gradle";
    }

    @Extension
    @Symbol("sdkmanGradleToolInstaller")
    public static class DescriptorImpl extends ToolInstallerDescriptor<SDKMANGradleToolInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SDKMANGradleToolInstaller_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolTypeNameIs(toolType, GRADLE_INSTALLATION_CLASS);
        }

        public List<SDKMANVendor> getInstallableVendors() throws IOException {
            return SDKMANToolInstaller.getInstallableVendors("gradle");
        }
    }
}
