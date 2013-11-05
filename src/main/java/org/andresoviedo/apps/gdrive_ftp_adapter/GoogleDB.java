package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.ftpserver.ftplet.FtpFile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * TODO:
 * 
 * 1- Reparar caché 2- Sincronización
 * 
 * @author Andres Oviedo
 * 
 */
public final class GoogleDB {

	private static final String TABLE_FILES = "gdrive";

	public static final String FILE_SEPARATOR = "/";

	private static GoogleDB instance;

	private RowMapper<FtpFile> rowMapper;

	private JdbcTemplate jdbcTemplate;

	private GoogleDB() {
		try {
			BasicDataSource dataSource = new BasicDataSource();
			dataSource.setDriverClassName("org.sqlite.JDBC");
			dataSource.setUrl("jdbc:sqlite:gdrive.db");

			jdbcTemplate = new JdbcTemplate(dataSource);

			rowMapper = new RowMapper<FtpFile>() {
				@Override
				public FtpFile mapRow(ResultSet rs, int rowNum)
						throws SQLException {
					GDriveFile ret = new GDriveFile();
					ret.setId(rs.getString("id"));
					ret.setParentId(rs.getString("parentId"));
					ret.setRelativePath(rs.getString("path"));
					ret.setLargestChangeId(rs.getLong("largestChangeId"));
					ret.setDirectory(rs.getBoolean("isDirectory"));
					ret.setLength(rs.getLong("length"));
					ret.setLastModified2(rs.getLong("lastModified"));
					ret.setMd5Checksum(rs.getString("md5Checksum"));
					ret.setExists(true);
					return ret;
				}
			};

			instance = this;
			init();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static GoogleDB getInstance() {
		if (instance == null) {
			instance = new GoogleDB();
		}
		return instance;
	}

	private void init() {
		Integer ret = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM sqlite_master WHERE type='table' AND name='"
						+ TABLE_FILES + "';", Integer.class);
		if (ret == 0) {
			jdbcTemplate
					.execute("CREATE TABLE "
							+ TABLE_FILES
							+ " (id text primary key, parentId text, largestChangeId integer, path text not null unique, "
							+ "isDirectory boolean, length integer, "
							+ "lastModified integer, md5Checksum integer)");
		}

		System.out.println("table created");
	}

	public GDriveFile getFile(String id) {
		try {
			// System.out.println("queryFile(" + id + ")");
			final GDriveFile file = (GDriveFile) jdbcTemplate.queryForObject(
					"select * from " + TABLE_FILES + " where id=?",
					new Object[] { id }, rowMapper);
			file.setExists(true);
			return file;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		}
	}

	public void addFile(GDriveFile file) {
		jdbcTemplate
				.update("insert into "
						+ TABLE_FILES
						+ " (id,parentId,largestChangeId,path,isDirectory,length,lastModified,md5checksum)"
						+ " values(?,?,?,?,?,?,?,?)",
						new Object[] { file.getId(), file.getParentId(),
								file.getLargestChangeId(), file.getPath(),
								file.isDirectory(), file.getLength(),
								file.getLastModified(), file.getMd5Checksum() });
	}

	public void updateFile(GDriveFile file) {
		jdbcTemplate
				.update("update "
						+ TABLE_FILES
						+ " set parentId=?,largestChangeId=?,path=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=?",
						new Object[] { file.getParentId(),
								file.getLargestChangeId(), file.getPath(),
								file.isDirectory(), file.getLength(),
								file.getLastModified(), file.getMd5Checksum(),
								file.getId() });
	}

	public void addFiles(List<GDriveFile> files) {
		List<Object[]> args = new ArrayList<Object[]>();
		for (GDriveFile file : files) {
			args.add(new Object[] { file.getId(), file.getParentId(),
					file.getLargestChangeId(), file.getPath(),
					file.isDirectory(), file.getLength(),
					file.getLastModified(), file.getMd5Checksum() });
		}

		// TODO: return number of affected row
		jdbcTemplate
				.batchUpdate(
						"insert or replace into "
								+ TABLE_FILES
								+ " (id,parentId,largestChangeId,path,isDirectory,length,lastModified,md5checksum)"
								+ " values(?,?,?,?,?,?,?,?)", args);
	}

	// TODO:
	// No se esta poniendo el parentId, ni el path, ni el lastModified....

	public List<FtpFile> getFiles(String parentId) {
		return jdbcTemplate.query("select * from " + TABLE_FILES
				+ " where parentId=?", new Object[] { parentId }, rowMapper);
	}

	public List<String> getUnsynchDirs(long largestChangedId) {
		return jdbcTemplate.queryForList("select id from " + TABLE_FILES
				+ " where isDirectory=1 and largestChangeId = 0", String.class);
	}

	public FtpFile getFileByPath(String path) {
		try {
			String normalizedPath = path.startsWith("/")
					|| path.startsWith("\\") ? path.substring(1) : path;
			if (normalizedPath.endsWith("./")) {
				normalizedPath = normalizedPath.substring(0,
						normalizedPath.length() - 2);
			}
			if (normalizedPath.endsWith("/")) {
				normalizedPath = normalizedPath.substring(0,
						normalizedPath.length() - 1);
			}
			System.out.println("Searching file: '" + normalizedPath + "'...");
			return jdbcTemplate.queryForObject("select * from " + TABLE_FILES
					+ " where path=?", new Object[] { normalizedPath },
					rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public void updateFileLargestChangeId(GDriveFile folderFile) {
		jdbcTemplate.update("update " + TABLE_FILES
				+ " set largestChangeId=? where id=?", new Object[] {
				folderFile.getLargestChangeId(), folderFile.getId() });
	}

	public int deleteFileByPath(String path) {
		if (path.contains("%")) {
			// cuidado con el sql injection!
			throw new IllegalArgumentException("path '" + path
					+ "' is not a valid path");
		}

		return jdbcTemplate.update("delete from " + TABLE_FILES
				+ " where path like ?", new Object[] { path + "%" });

	}

	public long getLargestChangeId() {
		return jdbcTemplate.queryForObject("select max(largestChangeId) from "
				+ TABLE_FILES, Long.class);
	}

	public long getLowerChangedId() {
		return jdbcTemplate.queryForObject("select min(largestChangeId) from "
				+ TABLE_FILES + " where largestChangeId > 0", Long.class);
	}
}
