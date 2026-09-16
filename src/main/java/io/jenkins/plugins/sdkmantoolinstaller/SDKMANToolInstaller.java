package io.jenkins.plugins.sdkmantoolinstaller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.input.CountingInputStream;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/*
 * #%L
 * SDKMAN Tool Installer Plugin
 * %%
 * Copyright (C) 2025 Brett Smith
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.tools.ZipExtractionInstaller;
import io.jenkins.plugins.sdkmantoolinstaller.SDKMan.CandidateVersion;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

/**
 * Install a SDK from from <a href="https://adoptium.net">SDKAMN</a>
 * Based on <a href="https://github.com/jenkinsci/adoptopenjdk-plugin">Eclipse Temurin installer Plugin</a>
 */
public class SDKMANToolInstaller extends ToolInstaller {

    private static boolean DISABLE_CACHE = Boolean.getBoolean(SDKMANToolInstaller.class.getName() + ".cache.disable");

    /**
     * Eclipse Temurin release id
     */
    public final String id;
    
    private final static SDKMan sdkMan = new SDKMan();

    @DataBoundConstructor
    public SDKMANToolInstaller(String id) {
        super(null);
        this.id = id;
    }

    /**
     * SDKMAN candidate handled by this installer implementation.
     */
    protected String sdkCandidate() {
        return "java";
    }
    
    private static SDKMan.Platform toPlatform(Platform platform, CPU cpu) {
    	switch(platform) {
		case ALPINE_LINUX:
    	case LINUX:
    		switch(cpu) {
    		case amd64:
    			return SDKMan.Platform.LINUX64;
    		case arm:
    			/* TODO can be more specific? */
    			return SDKMan.Platform.LINUXARM64;
    		case i386:
    			return SDKMan.Platform.LINUX32;
    		default:
    			throw new UnsupportedOperationException("Linux " + cpu + " Platform unsupported.");
    		}
    	case MACOS:
    		switch(cpu) {
    		case amd64:
    			return SDKMan.Platform.DARWINX64;
    		case arm:
    			return SDKMan.Platform.DARWINARM64;
    		default:
    			throw new UnsupportedOperationException("Mac OS " + cpu + " Platform unsupported.");
    		}
    	case WINDOWS:
    		switch(cpu) {
    		case amd64:
    			return SDKMan.Platform.WINDOWSX64;
    		default:
    			throw new UnsupportedOperationException("Windows " + cpu + " Platform unsupported.");
    		}
		case SOLARIS:
			return SDKMan.Platform.SUNOS;
		case AIX:
			return SDKMan.Platform.EXOTIC;
    	default:
			throw new UnsupportedOperationException("Platform " + platform + " unsupported.");
    	}
    }

    @NonNull
    protected static SDKMANVendorList getSDKMANVendorList(String candidate) throws IOException {
        try {
			return getSDKMANVendorList(candidate, Platform.current(), CPU.current());
        } catch (IOException | DetectionFailedException | RuntimeException e) {
			throw new IOException(Messages.SDKMANToolInstaller_vendorList_NoDownloadable(), e);
		}
    }
    
    @NonNull
    protected static SDKMANVendorList getSDKMANVendorList(String candidate, Platform platform, CPU cpu) throws IOException {
        SDKMANVendorList list = new SDKMANVendorList();
        if ("java".equalsIgnoreCase(candidate)) {
            Map<String, List<SDKManVersion>> vendorMap = new HashMap<>();
            List<CandidateVersion> versions = sdkMan.available(candidate, toPlatform(platform, cpu)).toList();

			for (CandidateVersion candidateVersion : versions) {
				String vendorName = candidateVersion.vendor();
				if (vendorName == null || vendorName.isBlank()) {
					vendorName = "SDKMAN";
				}
				List<SDKManVersion> family = vendorMap.computeIfAbsent(vendorName, k -> new ArrayList<>());
				SDKManVersion version = new SDKManVersion();
				version.id = candidateVersion.identifier();
				family.add(version);
            }

            list.data = vendorMap.entrySet().stream().map(e -> {
                SDKMANVendor family = new SDKMANVendor();
                family.name = e.getKey();
                family.versions = e.getValue().toArray(new SDKManVersion[0]);
                return family;
            }).toList().toArray(new SDKMANVendor[0]);
            return list;
        }

        List<String> versions = sdkMan.versions(candidate, toPlatform(platform, cpu));
        SDKMANVendor family = new SDKMANVendor();
        family.name = "SDKMAN";
        family.versions = versions.stream().map(v -> {
            SDKManVersion version = new SDKManVersion();
            version.id = v;
            return version;
        }).toArray(SDKManVersion[]::new);
        list.data = new SDKMANVendor[] { family };
        return list;
    }

    protected static List<SDKMANVendor> getInstallableVendors(String candidate) throws IOException {
        return Arrays.asList(getSDKMANVendorList(candidate).data);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {
        FilePath expected = preferredLocation(tool, node);

        try {
            // already installed?
            FilePath marker = expected.child(".installedByJenkins");
            if (marker.exists() && marker.readToString().equals(id)) {
                return expected;
            }
            expected.deleteRecursive();
            expected.mkdirs();

            Platform p = Platform.of(node);
            CPU c = CPU.of(node);
            String candidate = sdkCandidate();
            if (candidate == null || candidate.isBlank()) {
                throw new IOException("SDKMAN candidate must be configured.");
            }

            File cache = getLocalCacheFile(p, c);
            if (!DISABLE_CACHE && cache.exists()) {
                try (InputStream in = cache.toURI().toURL().openStream()) {
                    CountingInputStream cis = new CountingInputStream(in);
                    try {
                        log.getLogger()
                                .println(Messages.SDKMANToolInstaller_performInstallation_fromCache(
                                        cache, expected, node.getDisplayName()));
                        // the zip contains already the directory so we unzip to parent directory
                        FilePath parent = expected.getParent();
                        if (parent != null) {
                            parent.unzipFrom(cis);
                        } else {
                            throw new NullPointerException("Parent directory of " + expected + " is null");
                        }
                    } catch (IOException e) {
                        throw new IOException(
                                Messages.SDKMANToolInstaller_performInstallation_failedToUnpack(
                                        cache.toURI().toURL(), cis.getByteCount()),
                                e);
                    }
                }
                return expected;
            } 
            
            SDKMan.Platform platform = toPlatform(p, c);
            
            CandidateVersion candidateVersion = sdkMan.version(candidate, platform, id).orElseThrow(() -> {
    			return new IOException(Messages.SDKMANToolInstaller_performInstallation_versionNotFound(id));
            });
            

            String url = candidateVersion.url(platform).toExternalForm();
            ZipExtractionInstaller zipExtractionInstaller = new ZipExtractionInstaller(null, url, null);
            FilePath installation = zipExtractionInstaller.performInstallation(tool, node, log);
            
            installation.child(".timestamp").delete(); // we don't use the timestamp
            FilePath base = findPullUpDirectory(installation, p);
            if (base != null && base != expected) {
                base.moveAllChildrenTo(expected);
            }
            marker.write(id, null);
            if (!DISABLE_CACHE) {
                // update the local cache on master
                // download to a temporary file and rename it in to handle concurrency and failure correctly,
                Path tmp = new File(cache.getPath() + ".tmp").toPath();
                try {
                    Path tmpParent = tmp.getParent();
                    if (tmpParent != null) {
                        Files.createDirectories(tmpParent);
                    }
                    try (OutputStream out = Files.newOutputStream(tmp)) {
                        expected.zip(out);
                    }
                    Files.move(tmp, cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        } catch (DetectionFailedException e) {
            log.getLogger().println(Messages.SDKMANToolInstaller_performInstallation_JdkSkipped(e.getMessage()));
        }

        return expected;
    }

    private File getLocalCacheFile(Platform platform, CPU cpu) {
        // we force .zip file
        return new File(Jenkins.get().getRootDir(), "caches/sdkmantoolinstaller/" + sdkCandidate() + "/" + platform + "/" + cpu + "/" + id + ".zip");
    }

    protected static boolean toolTypeNameIs(Class<? extends ToolInstallation> toolType, String className) {
        return toolType != null && className.equals(toolType.getName());
    }

    /**
     * Often an archive contains an extra top-level directory that's unnecessary when extracted on the disk
     * into the expected location. If your installation sources provide that kind of archives, override
     * this method to find the real root location.
     *
     * <p>
     * The caller will "pull up" the discovered real root by throw away the intermediate directory,
     * so that the user-configured "tool home" directory contains the right files.
     *
     * <p>
     * The default implementation applies some heuristics to auto-determine if the pull up is necessary.
     * This should work for typical archive files.
     *
     * @param root     The directory that contains the extracted archive. This directory contains nothing but the
     *                 extracted archive. For example, if the user installed
     *                 <a href="https://archive.apache.org/dist/ant/binaries/jakarta-ant-1.1.zip">jakarta-ant-1.1.zip</a>, this directory would contain
     *                 a single directory "jakarta-ant".
     * @param platform The platform for which to find pull up directory for.
     * @return Return the real top directory inside {@code root} that contains the meat. In the above example,
     * {@code root.child("jakarta-ant")} should be returned. If there's no directory to pull up, return null.
     * @throws IOException          Signals that an I/O exception of some sort has occurred.
     * @throws InterruptedException Thrown when a thread is interrupted.
     */
    protected FilePath findPullUpDirectory(FilePath root, Platform platform) throws IOException, InterruptedException {
        // if the directory just contains one directory and that alone, assume that's the pull up subject
        // otherwise leave it as is.
        List<FilePath> children = root.listDirectories();
        if (children.size() != 1) return null;

        // Since the MacOS tar.gz file uses a different layout we need to handle this platform differently
        // https://blog.adoptopenjdk.net/2018/10/macos-binary-changes
        if (platform == Platform.MACOS) {
            FilePath contents = children.get(0).child("Contents/Home");
            if (contents.exists() && contents.isDirectory()) return contents;
        }
        return children.get(0);
    }

    /**
     * Supported platform
     */
    public enum Platform {
        LINUX("linux"),
        ALPINE_LINUX("alpine-linux"),
        WINDOWS("windows"),
        MACOS("mac"),
        SOLARIS("solaris"),
        AIX("aix");

        private final String id;

        Platform(String id) {
            this.id = id;
        }

        public static Platform of(Node n) throws IOException, InterruptedException, DetectionFailedException {
            VirtualChannel channel = n.getChannel();
            if (channel == null) {
                throw new IOException(Messages.SDKMANToolInstaller_Platform_nullChannel(n.getDisplayName()));
            }
            return channel.call(new Platform.GetCurrentPlatform());
        }

        public static Platform current() throws DetectionFailedException {
            String arch = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            if (arch.contains("linux")) return isAlpineLinux() ? ALPINE_LINUX : LINUX;
            if (arch.contains("windows")) return WINDOWS;
            if (arch.contains("sun") || arch.contains("solaris")) return SOLARIS;
            if (arch.contains("mac")) return MACOS;
            if (arch.contains("aix")) return AIX;
            throw new DetectionFailedException(Messages.SDKMANToolInstaller_Platform_unknownPlatform(arch));
        }

        // Package protected so that tests can modify it
        static String osReleaseLocation = "/etc/os-release";

        // Package protected so that it can be tested
        static boolean isAlpineLinux() {
            File osRelease = new File(osReleaseLocation);
            try (Stream<String> lines = Files.lines(osRelease.toPath(), Charset.defaultCharset())) {
                return lines.anyMatch("ID=alpine"::equalsIgnoreCase);
            } catch (IOException ioe) {
                return false; // Do not fail OS detection if osReleaseLocation does not exist
            }
        }

        public String getId() {
            return id;
        }

        static class GetCurrentPlatform extends MasterToSlaveCallable<Platform, DetectionFailedException> {
            private static final long serialVersionUID = 1L;

            public Platform call() throws DetectionFailedException {
                return current();
            }
        }
    }

    /**
     * Supported CPU architecture
     */
    public enum CPU {
        i386,
        amd64,
        Sparc,
        s390x,
        ppc64,
        arm;

        public static CPU of(Node n) throws IOException, InterruptedException, DetectionFailedException {
            VirtualChannel channel = n.getChannel();
            if (channel == null) {
                throw new IOException(Messages.SDKMANToolInstaller_CPU_nullChannel(n.getDisplayName()));
            }
            return channel.call(new CPU.GetCurrentCPU());
        }

        public static CPU current() throws DetectionFailedException {
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
            if (arch.contains("sparc")) return Sparc;
            if (arch.contains("amd64") || arch.contains("86_64")) return amd64;
            if (arch.contains("86")) return i386;
            if (arch.contains("s390x")) return s390x;
            if (arch.contains("ppc64")) return ppc64;
            if (arch.contains("arm") || arch.contains("aarch64")) return arm;
            throw new DetectionFailedException(Messages.SDKMANToolInstaller_CPU_unknownCpu(arch));
        }

        static class GetCurrentCPU extends MasterToSlaveCallable<CPU, DetectionFailedException> {
            private static final long serialVersionUID = 1L;

            public CPU call() throws DetectionFailedException {
                return current();
            }
        }
    }

    @Extension
    @Symbol("sdkmanToolInstaller")
    public static class DescriptorImpl extends ToolInstallerDescriptor<SDKMANToolInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SDKMANToolInstaller_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == JDK.class;
        }

        public List<SDKMANVendor> getInstallableVendors() throws IOException {
            return SDKMANToolInstaller.getInstallableVendors("java");
        }
    }

    @SuppressWarnings("serial")
	private static final class DetectionFailedException extends Exception {
        private DetectionFailedException(String message) {
            super(message);
        }
    }

    @SuppressFBWarnings(
            value = "NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
            justification = "Field initialized during deserialization from JSON object")
    public static final class SDKMANVendorList {
        public SDKMANVendor[] data = new SDKMANVendor[0];
        public int version;

        public boolean isEmpty() {
            for (SDKMANVendor f : data) {
                if (f.versions.length > 0) {
                    return false;
                }
            }
            return true;
        }

        public SDKManVersion getVersion(String productCode) {
            for (SDKMANVendor f : data) {
                for (SDKManVersion r : f.versions) {
                    if (r.matchesId(productCode)) {
                        return r;
                    }
                }
            }
            return null;
        }
    }

    @SuppressFBWarnings(
            value = {"UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD", "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"},
            justification = "Field initialized during deserialization from JSON object")
    public static final class SDKMANVendor {
        public String name;
        public SDKManVersion[] versions;
    }

    @SuppressFBWarnings(
            value = {"UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", "NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"},
            justification = "Field initialized during deserialization from JSON object")
    public static final class SDKManVersion {
        public String id;

        public boolean matchesId(String rhs) {
            return rhs != null && rhs.equals(id);
        }

    }

}
