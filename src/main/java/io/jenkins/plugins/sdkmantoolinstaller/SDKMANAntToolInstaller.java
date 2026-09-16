package io.jenkins.plugins.sdkmantoolinstaller;

import java.io.IOException;
import java.util.List;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;

public class SDKMANAntToolInstaller extends SDKMANToolInstaller {

    private static final String ANT_INSTALLATION_CLASS = "hudson.tasks.Ant$AntInstallation";

    @DataBoundConstructor
    public SDKMANAntToolInstaller(String id) {
        super(id);
    }

    @Override
    protected String sdkCandidate() {
        return "ant";
    }

    @Extension
    @Symbol("sdkmanAntToolInstaller")
    public static class DescriptorImpl extends ToolInstallerDescriptor<SDKMANAntToolInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SDKMANAntToolInstaller_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolTypeNameIs(toolType, ANT_INSTALLATION_CLASS);
        }

        public List<SDKMANVendor> getInstallableVendors() throws IOException {
            return SDKMANToolInstaller.getInstallableVendors("ant");
        }
    }
}
