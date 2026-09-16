package io.jenkins.plugins.sdkmantoolinstaller;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;

public class SDKMANCustomToolInstaller extends SDKMANToolInstaller {

    private static final String CUSTOM_TOOL_CLASS = "com.cloudbees.jenkins.plugins.customtools.CustomTool";

    public final String candidate;

    @DataBoundConstructor
    public SDKMANCustomToolInstaller(String candidate, String id) {
        super(id);
        this.candidate = candidate == null ? "" : candidate.trim();
    }

    @Override
    protected String sdkCandidate() {
        return candidate;
    }

    @Extension
    @Symbol("sdkmanCustomToolInstaller")
    public static class DescriptorImpl extends ToolInstallerDescriptor<SDKMANCustomToolInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SDKMANCustomToolInstaller_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolTypeNameIs(toolType, CUSTOM_TOOL_CLASS);
        }
    }
}
