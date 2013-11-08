package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;

public final class GDriveFileFactory {

	public static GDriveFile create(File googleFile) {
		if (googleFile == null)
			return null;
		GDriveFile newFile = new GDriveFile(getFilename(googleFile));
		newFile.setId(googleFile.getId());
		newFile.setLastModified2(getLastModified(googleFile));
		newFile.setLength(getFileSize(googleFile));
		newFile.setDirectory(isDirectory(googleFile));
		newFile.setMd5Checksum(googleFile.getMd5Checksum());
		newFile.setParents(new HashSet<String>());
		for (ParentReference ref : googleFile.getParents()) {
			if (ref.getIsRoot()) {
				newFile.getParents().add("root");
			} else {
				newFile.getParents().add(ref.getId());
			}
		}
		if (googleFile.getLabels().getTrashed()) {
			newFile.setLabels(Collections.singleton("trashed"));
		} else {
			newFile.setLabels(Collections.<String> emptySet());
		}
		return newFile;
	}

	public static List<GDriveFile> create(List<File> googleFiles, long revision) {
		List<GDriveFile> ret = new ArrayList<>(googleFiles.size());
		for (File child : googleFiles) {
			GDriveFile localFile = create(child);
			localFile.setRevision(revision);
			ret.add(localFile);
		}
		return ret;
	}

	private static String getFilename(File file) {
		// System.out.print("getFilename(" + file.getId() + ")");
		String filename = file.getTitle() != null ? file.getTitle() : file
				.getOriginalFilename();
		// logger.info("=" + filename);
		return filename;
	}

	private static boolean isDirectory(File googleFile) {
		// System.out.print("isDirectory(" + getFilename(googleFile) + ")=");
		boolean isDirectory = "application/vnd.google-apps.folder"
				.equals(googleFile.getMimeType());
		// logger.info("=" + isDirectory);
		return isDirectory;
	}

	private static long getLastModified(File googleFile) {
		final boolean b = googleFile != null
				&& googleFile.getModifiedDate() != null;
		if (b) {
			return googleFile.getModifiedDate().getValue();
		} else {
			return 0;
		}

	}

	private static long getFileSize(File googleFile) {
		return googleFile.getFileSize() == null ? 0 : googleFile.getFileSize();
	}

}