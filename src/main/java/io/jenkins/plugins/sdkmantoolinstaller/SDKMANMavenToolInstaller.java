package io.jenkins.plugins.sdkmantoolinstaller;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import java.io.IOException;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class SDKMANMavenToolInstaller extends SDKMANToolInstaller {

    @DataBoundConstructor
    public SDKMANMavenToolInstaller(String id) {
        super(id);
    }

    @Override
    protected String sdkCandidate() {
        return "maven";
    }

    @Extension
    @Symbol("sdkmanMavenToolInstaller")
    public static class DescriptorImpl extends ToolInstallerDescriptor<SDKMANMavenToolInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SDKMANMavenToolInstaller_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == MavenInstallation.class;
        }

        public List<SDKMANVendor> getInstallableVendors() throws IOException {
            return SDKMANToolInstaller.getInstallableVendors("maven");
        }
    }
}
