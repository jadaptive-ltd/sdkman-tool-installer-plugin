package io.jenkins.plugins.sdkmantoolinstaller;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class IO {

    public static Set<PosixFilePermission> fromBitmask(long mode) {
        var l = new LinkedHashSet<PosixFilePermission>();
        for (var perm : PosixFilePermission.values()) {
            if ((mode & toMask(perm)) != 0) {
                l.add(perm);
            }
        }
        return Collections.unmodifiableSet(l);
    }

    /**
     * Get the bitmask flag value for a given permission.
     *
     * @param permission permission
     * @return bitmask flag value
     */
    public static int toMask(PosixFilePermission permission) {
        switch (permission) {
            case OWNER_READ:
                return 0x100;
            case OWNER_WRITE:
                return 0x80;
            case OWNER_EXECUTE:
                return 0x40;
            case GROUP_READ:
                return 0x20;
            case GROUP_WRITE:
                return 0x10;
            case GROUP_EXECUTE:
                return 0x08;
            case OTHERS_READ:
                return 0x04;
            case OTHERS_WRITE:
                return 0x02;
            case OTHERS_EXECUTE:
                return 0x01;
            default:
                return 0;
        }
    }
}
