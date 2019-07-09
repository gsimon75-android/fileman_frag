package org.dyndns.fules;

import android.util.Log;

import java.util.HashSet;
import java.io.File;
import java.io.IOException;
import java.net.URI;

public class PosixFile extends File {
    static {
        System.loadLibrary("posixfile");
		init();
    }
	private static native void init(); // may be safely called to ensure that the lib is loaded as well
    public static final String TAG = "fileman";


	public PosixFile(File dir, String name) {
		super(dir, name);
	}

	public PosixFile(String path) {
		super(path);
	}

	public PosixFile(String dirPath, String name) {
		super(dirPath, name);
	}

	public PosixFile(URI uri) {
		super(uri);
	}

	public native long lastAccessed(); // Returns the time when this file was last accessed, measured in milliseconds since January 1st, 1970, midnight.
	
	public native boolean setLastAccessed(long time); // Sets the time this file was last accessed, measured in milliseconds since January 1st, 1970, midnight.

	public native long getDeviceId();

	public native int getPermissions();
	
	public native boolean setPermissions(int perms);

	public String getPermissionString() {
		StringBuilder result = new StringBuilder(9);
		int p = getPermissions();

		result.append((p & 0400) != 0 ? 'r' : '-');
		result.append((p & 0200) != 0 ? 'w' : '-');
		result.append((p & 0100) != 0 ? ( (p & 04000) != 0 ? 's' : 'x') : '-');
		result.append((p & 0040) != 0 ? 'r' : '-');
		result.append((p & 0020) != 0 ? 'w' : '-');
		result.append((p & 0010) != 0 ? ( (p & 02000) != 0 ? 's' : 'x') : '-');
		result.append((p & 0004) != 0 ? 'r' : '-');
		result.append((p & 0002) != 0 ? 'w' : '-');
		result.append((p & 0001) != 0 ? ( (p & 01000) != 0 ? 't' : 'x') : '-');

		return result.toString();
	}

	public native long getOwner();
	
	public native long getGroup();
	
	public native boolean setOwnerAndGroup(long owner, long group);
	
	public boolean setOwner(long owner) {
		return setOwnerAndGroup(owner, -1);
	}
	
	public boolean setGroup(long group) {
		return setOwnerAndGroup(-1, group);
	}

	public native boolean isLink(); // Indicates if this file represents a symbolic link on the underlying file system.
	
	public native boolean symlink(String from);
    
	public native String getLink();

    public PosixFile getLinkFile() {
		String s = getLink();
		return (s == null) ? null : new PosixFile(s);
	}

    public File resolveLinkFile() {
        HashSet<String> links = new HashSet<String>();
		PosixFile f = this;
		for ( ; (f != null) && f.isLink(); f = f.getLinkFile())
			if (!links.add(f.getPath())) 
				return null; // Circular symlink found
        return f;
    }

}

