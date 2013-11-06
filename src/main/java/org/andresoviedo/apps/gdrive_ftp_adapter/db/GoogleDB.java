package org.andresoviedo.apps.gdrive_ftp_adapter.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.andresoviedo.apps.gdrive_ftp_adapter.GDriveFile;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.FtpFile;
import org.springframework.dao.EmptyResultDataAccessException;
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

	private static final String TABLE_FILES = "files";

	private static final String TABLE_CHILDS = "childs";

	public static final String FILE_SEPARATOR = "/";

	private static GoogleDB instance;

	private RowMapper<FtpFile> rowMapper;

	private JdbcTemplate jdbcTemplate;

	private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	private final Lock r = rwl.readLock();
	private final Lock w = rwl.writeLock();

	private GoogleDB() {
		instance = this;
		init();
	}

	public static GoogleDB getInstance() {
		if (instance == null) {
			instance = new GoogleDB();
		}
		return instance;
	}

	private void init() {
		try {
			BasicDataSource dataSource = new BasicDataSource();
			dataSource.setDriverClassName("org.sqlite.JDBC");
			dataSource.setUrl("jdbc:sqlite:gdrive.db");
			// dataSource.setMaxActive(1);
			dataSource.setMaxWait(60000);

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
						.execute("create table "
								+ TABLE_FILES
								+ " (id text, largestChangeId integer, "
								+ "path text not null, isDirectory boolean, length integer, lastModified integer, "
								+ "md5Checksum text, primary key (id))");
				jdbcTemplate.execute("create index idx_path on gdrive (path)");

				logger.info("Database " + TABLE_FILES + " initialized");
			}

			ret = jdbcTemplate.queryForObject(
					"SELECT count(*) FROM sqlite_master WHERE type='table' AND name='"
							+ TABLE_CHILDS + "';", Integer.class);
			if (ret == 0) {
				jdbcTemplate
						.execute("create table "
								+ TABLE_CHILDS
								+ " (id integer autoincrement, childId text references "
								+ TABLE_FILES
								+ "(id), parentId text references "
								+ TABLE_FILES + "(id), primary key (id))");

				logger.info("Database " + TABLE_CHILDS + " initialized");
			}

			// jdbcTemplate.execute(".timeout 10000");

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public GDriveFile getFile(String id) {
		r.lock();
		try {
			// logger.info("queryFile(" + id + ")");
			final GDriveFile file = (GDriveFile) jdbcTemplate.queryForObject(
					"select * from " + TABLE_FILES + " where id=?",
					new Object[] { id }, rowMapper);
			file.setExists(true);
			return file;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		} finally {
			r.unlock();
		}
	}

	public void addFile(GDriveFile file) {
		w.lock();
		try {
			jdbcTemplate
					.update("insert into "
							+ TABLE_FILES
							+ " (id,largestChangeId,path,isDirectory,length,lastModified,md5checksum)"
							+ " values(?,?,?,?,?,?,?)",
							new Object[] { file.getId(),
									file.getLargestChangeId(), file.getPath(),
									file.isDirectory(), file.getLength(),
									file.getLastModified(),
									file.getMd5Checksum() });
		} finally {
			w.unlock();
		}
	}

	public void updateFile(GDriveFile file) {
		w.lock();
		try {
			jdbcTemplate
					.update("update "
							+ TABLE_FILES
							+ " set parentId=?,largestChangeId=?,path=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=?",
							new Object[] { file.getParentId(),
									file.getLargestChangeId(), file.getPath(),
									file.isDirectory(), file.getLength(),
									file.getLastModified(),
									file.getMd5Checksum(), file.getId() });
			jdbcTemplate
					.update("insert or replace "
							+ TABLE_CHILDS
							+ " set id=?,largestChangeId=?,path=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=?",
							new Object[] { file.getParentId(),
									file.getLargestChangeId(), file.getPath(),
									file.isDirectory(), file.getLength(),
									file.getLastModified(),
									file.getMd5Checksum(), file.getId() });
		} finally {
			w.unlock();
		}
	}

	void addFiles(List<GDriveFile> files) {
		w.lock();
		try {
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
		} finally {
			w.unlock();
		}
	}

	// TODO:
	// No se esta poniendo el parentId, ni el path, ni el lastModified....

	public List<FtpFile> getFiles(String parentId) {
		r.lock();
		try {
			final List<FtpFile> query = jdbcTemplate.query("select * from "
					+ TABLE_FILES + " where parentId=?",
					new Object[] { parentId }, rowMapper);

			// interceptar para "codificar" los ficheros duplicados
			Map<String, GDriveFile> nonDuplicatedNames = new HashMap<String, GDriveFile>(
					query.size());
			for (FtpFile file : query) {
				final GDriveFile file2 = (GDriveFile) file;
				if (nonDuplicatedNames.containsKey(file2.getPath())) {
					GDriveFile file3 = nonDuplicatedNames.get(file2.getPath());
					file3.setRelativePath(file3.getPath()
							+ DUPLICATED_FILE_TOKEN + file3.getId());
					final String encodedName = file2.getPath()
							+ DUPLICATED_FILE_TOKEN + file2.getId();
					logger.debug("Detected duplicated file '" + encodedName
							+ "'");
					// assert nonDuplicatedNames.contains(encodedName);
					file2.setRelativePath(encodedName);
					nonDuplicatedNames.put(encodedName, file2);
				} else {
					nonDuplicatedNames.put(file2.getPath(), file2);
				}
			}
			return query;
		} finally {
			r.unlock();
		}
	}

	public List<String> getAllFolderIdsByChangeId(long largestChangedId) {
		r.lock();
		try {
			if (largestChangedId != -1) {
				return jdbcTemplate.queryForList("select id from "
						+ TABLE_FILES
						+ " where isDirectory=1 and largestChangeId = ?",
						new Object[] { largestChangedId }, String.class);
			} else {
				return jdbcTemplate.queryForList("select id from "
						+ TABLE_FILES + " where isDirectory=1", String.class);
			}
		} finally {
			r.unlock();
		}
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
				r.lock();
				try {
					return jdbcTemplate.queryForObject("select * from "
							+ TABLE_FILES + " where path=?",
							new Object[] { normalizedPath }, rowMapper);
				} finally {
					r.unlock();
				}

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
				r.lock();
				try {
					lastKnownFile = (GDriveFile) jdbcTemplate.queryForObject(
							"select * from " + TABLE_FILES
									+ " where path=? and id=?", new Object[] {
									currentPathToResolve, idToken }, rowMapper);
				} finally {
					r.unlock();
				}
				pendingPathToResolve = pendingPathToResolve.substring(idIdx);
				if (pendingPathToResolve.startsWith("/")) {
					pendingPathToResolve = pendingPathToResolve.substring(1);
				}
			}
			if (pendingPathToResolve.length() > 0) {
				String[] pathsToResolve = pendingPathToResolve.split("/");
				for (String pathToResolve : pathsToResolve) {
					lastKnownFile = getChild(lastKnownFile, pathToResolve);
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

	private GDriveFile getChild(GDriveFile parent, String path) {
		r.lock();
		try {
			GDriveFile ret = (GDriveFile) jdbcTemplate.queryForObject(
					"select * from " + TABLE_FILES
							+ " where path=? and parentId=?", new Object[] {
							parent.getPath() + GoogleDB.FILE_SEPARATOR + path,
							parent.getId() }, rowMapper);
			return ret;
		} finally {
			r.unlock();
		}
	}

	public void updateFileLargestChangeId(GDriveFile folderFile) {
		w.lock();
		try {
			jdbcTemplate.update("update " + TABLE_FILES
					+ " set largestChangeId=? where id=?", new Object[] {
					folderFile.getLargestChangeId(), folderFile.getId() });
		} finally {
			w.unlock();
		}
	}

	public int deleteFileByPath(String path) {
		w.lock();
		try {
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
		} finally {
			w.unlock();
		}

	}

	public long getLargestChangeId() {
		r.lock();
		try {
			return jdbcTemplate.queryForObject(
					"select max(largestChangeId) from " + TABLE_FILES,
					Long.class);
		} finally {
			r.unlock();
		}
	}

	public long getLowerChangedId() {
		r.lock();
		try {

			return jdbcTemplate.queryForObject(
					"select min(largestChangeId) from " + TABLE_FILES
							+ " where largestChangeId > 0", Long.class);
		} finally {
			r.unlock();
		}
	}
}
