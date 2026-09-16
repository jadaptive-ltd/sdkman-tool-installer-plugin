package io.jenkins.plugins.sdkmantoolinstaller;

import java.io.IOException;
import java.util.List;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;

public class SDKMANGroovyToolInstaller extends SDKMANToolInstaller {

    private static final String GROOVY_INSTALLATION_CLASS = "hudson.plugins.groovy.GroovyInstallation";

    @DataBoundConstructor
    public SDKMANGroovyToolInstaller(String id) {
        super(id);
    }

    @Override
    protected String sdkCandidate() {
        return "groovy";
    }

    @Extension
    @Symbol("sdkmanGroovyToolInstaller")
    public static class DescriptorImpl extends ToolInstallerDescriptor<SDKMANGroovyToolInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SDKMANGroovyToolInstaller_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolTypeNameIs(toolType, GROOVY_INSTALLATION_CLASS);
        }

        public List<SDKMANVendor> getInstallableVendors() throws IOException {
            return SDKMANToolInstaller.getInstallableVendors("groovy");
        }
    }
}
