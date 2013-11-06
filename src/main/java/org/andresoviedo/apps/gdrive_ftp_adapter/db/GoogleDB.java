package org.andresoviedo.apps.gdrive_ftp_adapter.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.andresoviedo.apps.gdrive_ftp_adapter.GDriveFile;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.FtpFile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

/**
 * TODO:
 * 
 * 1- Reparar caché 2- Sincronización
 * 
 * @author Andres Oviedo
 * 
 */
public final class GoogleDB {

	private static final String DUPLICATED_FILE_TOKEN = "__DUPLICATED__";

	private static Log logger = LogFactory.getLog(GoogleDB.class);

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
			// jdbcTemplate
			// .execute("CREATE TABLE "
			// + TABLE_FILES
			// +
			// " (id text primary key, parentId text, largestChangeId integer, path text not null unique, "
			// + "isDirectory boolean, length integer, "
			// + "lastModified integer, md5Checksum integer)");
			jdbcTemplate
					.execute("create table gdrive (id text, parentId text, largestChangeId integer, "
							+ "path text not null, isDirectory boolean, length integer, lastModified integer, "
							+ "md5Checksum text, "
							+ "primary key (id,parentId))");
			jdbcTemplate.execute("create index idx_path on gdrive (path)");
		}

		logger.info("table created");
	}

	public GDriveFile getFile(String id) {
		try {
			// logger.info("queryFile(" + id + ")");
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

	void addFiles(List<GDriveFile> files) {
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
		final List<FtpFile> query = jdbcTemplate.query("select * from "
				+ TABLE_FILES + " where parentId=?", new Object[] { parentId },
				rowMapper);

		// interceptar para "codificar" los ficheros duplicados
		Map<String, GDriveFile> nonDuplicatedNames = new HashMap<String, GDriveFile>(
				query.size());
		for (FtpFile file : query) {
			final GDriveFile file2 = (GDriveFile) file;
			if (nonDuplicatedNames.containsKey(file2.getPath())) {
				GDriveFile file3 = nonDuplicatedNames.get(file2.getPath());
				file3.setRelativePath(file3.getPath() + DUPLICATED_FILE_TOKEN
						+ file3.getId());
				final String encodedName = file2.getPath()
						+ DUPLICATED_FILE_TOKEN + file2.getId();
				logger.debug("Detected duplicated file '" + encodedName + "'");
				// assert nonDuplicatedNames.contains(encodedName);
				file2.setRelativePath(encodedName);
				nonDuplicatedNames.put(encodedName, file2);
			} else {
				nonDuplicatedNames.put(file2.getPath(), file2);
			}
		}
		return query;
	}

	public List<String> getAllFolderIdsByChangeId(long largestChangedId) {
		return jdbcTemplate.queryForList("select id from " + TABLE_FILES
				+ " where isDirectory=1 and largestChangeId = ?",
				new Object[] { largestChangedId }, String.class);
	}

	public FtpFile getFileByPath(String path) {
		try {
			// TODO: revisar esta caca
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
			logger.debug("Searching file: '" + normalizedPath + "'...");

			// Normal case
			if (StringUtils.countOccurrencesOf(normalizedPath,
					GoogleDB.DUPLICATED_FILE_TOKEN) == 0) {
				return jdbcTemplate.queryForObject("select * from "
						+ TABLE_FILES + " where path=?",
						new Object[] { normalizedPath }, rowMapper);

			}

			GDriveFile lastKnownFile = null;
			String pendingPathToResolve = normalizedPath;
			for (int i = 0; i < StringUtils.countOccurrencesOf(normalizedPath,
					GoogleDB.DUPLICATED_FILE_TOKEN); i++) {

				int nextIdx = pendingPathToResolve
						.indexOf(GoogleDB.DUPLICATED_FILE_TOKEN);
				int idIdx = pendingPathToResolve.indexOf('/', nextIdx) != -1 ? pendingPathToResolve
						.indexOf('/', nextIdx) : pendingPathToResolve.length();
				String currentPathToResolve = pendingPathToResolve.substring(0,
						nextIdx);
				String idToken = pendingPathToResolve.substring(nextIdx
						+ GoogleDB.DUPLICATED_FILE_TOKEN.length(), idIdx);

				logger.info("Searching part: '" + pendingPathToResolve
						+ "'==>'" + currentPathToResolve + "':'" + idToken
						+ "'...");
				lastKnownFile = (GDriveFile) jdbcTemplate.queryForObject(
						"select * from " + TABLE_FILES
								+ " where path=? and id=?", new Object[] {
								currentPathToResolve, idToken }, rowMapper);
				pendingPathToResolve = pendingPathToResolve.substring(idIdx);
				if (pendingPathToResolve.startsWith("/")) {
					pendingPathToResolve = pendingPathToResolve.substring(1);
				}
			}
			if (pendingPathToResolve.length() > 0) {
				String[] pathsToResolve = pendingPathToResolve.split("/");
				for (String pathToResolve : pathsToResolve) {
					lastKnownFile = (GDriveFile) jdbcTemplate.queryForObject(
							"select * from " + TABLE_FILES
									+ " where path=? and parentId=?",
							new Object[] {
									lastKnownFile.getPath()
											+ GoogleDB.FILE_SEPARATOR
											+ pathToResolve,
									lastKnownFile.getId() }, rowMapper);
				}
			}

			if (lastKnownFile != null) {
				lastKnownFile.setRelativePath(path);
			}
			return lastKnownFile;
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

		// Intercept here to process duplicated file names
		String realPath = path;
		String maybeTheId = null;
		if (path.indexOf(GoogleDB.DUPLICATED_FILE_TOKEN) != -1) {
			String[] parts = path.split(GoogleDB.DUPLICATED_FILE_TOKEN);
			path = parts[0];
			maybeTheId = parts[1];
			logger.info("Deleting real file: '" + maybeTheId + "':'" + path
					+ "'...");
		}

		return jdbcTemplate.update("delete from " + TABLE_FILES
				+ " where path like ?", new Object[] { realPath + "%" });

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
