package org.andresoviedo.apps.gdrive_ftp_adapter.cache;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.FtpGDriveFile;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

/**
 * TODO:
 * 
 * 1- Reparar caché 2- Sincronización
 * 
 * @author Andres Oviedo
 * 
 */
public final class SQLiteCache implements Cache {

	private static Log logger = LogFactory.getLog(SQLiteCache.class);

	private static final String TABLE_FILES = "files";

	private static final String TABLE_CHILDS = "childs";

	private static SQLiteCache instance;

	private RowMapper<FtpGDriveFile> rowMapper;

	private JdbcTemplate jdbcTemplate;

	private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	private final Lock r = rwl.readLock();
	private final Lock w = rwl.writeLock();

	private BasicDataSource dataSource;

	private SQLiteCache() {
		instance = this;
		init();
	}

	public static SQLiteCache getInstance() {
		if (instance == null) {
			instance = new SQLiteCache();
		}
		return instance;
	}

	private void init() {
		try {
			initDAO();

			initDatabase();

			// jdbcTemplate.execute(".timeout 10000");

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void initDAO() {

		// initialize the data store factory
		File dataDir = new File("data/cache");
		if (!dataDir.exists()) {
			if (!dataDir.mkdirs()) {
				throw new RuntimeException("Could not create database folder "
						+ dataDir.getAbsolutePath());
			}
		}

		dataSource = new BasicDataSource();
		dataSource.setDriverClassName("org.sqlite.JDBC");
		dataSource.setUrl("jdbc:sqlite:file:data/cache/gdrive.db");
		// dataSource.setMaxActive(1);
		dataSource.setMaxWait(60000);

		jdbcTemplate = new JdbcTemplate(dataSource);

		rowMapper = new RowMapper<FtpGDriveFile>() {
			@Override
			public FtpGDriveFile mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				FtpGDriveFile ret = new FtpGDriveFile();
				ret.setId(rs.getString("id"));
				// TODO: hace falta informar los parents aqui?
				// ret.setParentId(rs.getString("parentId"));
				ret.setName(rs.getString("filename"));
				ret.setRevision(rs.getLong("revision"));
				ret.setDirectory(rs.getBoolean("isDirectory"));
				ret.setLength(rs.getLong("length"));
				ret.setLastModifiedImpl(rs.getLong("lastModified"));
				ret.setMd5Checksum(rs.getString("md5Checksum"));
				ret.setExists(true);
				return ret;
			}
		};
	}

	private void initDatabase() throws SQLException {

		Integer ret = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM sqlite_master WHERE type='table' AND name='"
						+ TABLE_FILES + "';", Integer.class);
		if (ret == 0) {
			List<String> queries = new ArrayList<String>();
			queries.add("create table "
					+ TABLE_FILES
					+ " (id text, revision integer, "
					+ "filename text not null, isDirectory boolean, length integer, lastModified integer, "
					+ "md5Checksum text, primary key (id))");
			queries.add("create table " + TABLE_CHILDS
					+ " (id integer primary key, childId text references "
					+ TABLE_FILES + "(id), parentId text references "
					+ TABLE_FILES + "(id), unique (childId, parentId))");
			queries.add("create index idx_filename on " + TABLE_FILES
					+ " (filename)");
			jdbcTemplate
					.batchUpdate(queries.toArray(new String[queries.size()]));

			logger.info("Database created");
		} else {
			logger.info("Database found");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache#getFile(java.lang
	 * .String)
	 */
	@Override
	public FtpGDriveFile getFile(String id) {
		r.lock();
		try {
			logger.trace("getFile(" + id + ")");
			FtpGDriveFile file = (FtpGDriveFile) jdbcTemplate.queryForObject(
					"select * from " + TABLE_FILES + " where id=?",
					new Object[] { id }, rowMapper);
			return file;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		} finally {
			r.unlock();
		}
	}

	public void addOrUpdateFile(FtpGDriveFile file) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		// queries.add("insert into "
		// + TABLE_FILES
		// +
		// " (id,revision,filename,isDirectory,length,lastModified,md5checksum)"
		// + " values(?,?,?,?,?,?,?)");
		queries.add("insert or replace into "
				+ TABLE_FILES
				+ " (id, revision,filename,isDirectory,length,lastModified,md5checksum)"
				+ " values(?,?,?,?,?,?,?)");
		args.add(new Object[] { file.getId(), file.getRevision(),
				file.getName(), file.isDirectory(), file.getLength(),
				file.getLastModified(), file.getMd5Checksum() });

		updateParents(file, queries, args);

		executeInTransaction(queries, args);
	}

	void addFile(FtpGDriveFile file, List<String> queries, List<Object[]> args) {
		queries.add("insert into "
				+ TABLE_FILES
				+ " (id, revision,filename,isDirectory,length,lastModified,md5checksum)"
				+ " values(?,?,?,?,?,?,?)");
		args.add(new Object[] { file.getId(), file.getRevision(),
				file.getName(), file.isDirectory(), file.getLength(),
				file.getLastModified(), file.getMd5Checksum() });
	}

	// TODO: merge de este con el addFile
	public void updateChilds(FtpGDriveFile file, List<FtpGDriveFile> childs) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		queries.add("delete from " + TABLE_CHILDS + " where parentId=?");
		args.add(new Object[] { file.getId() });

		queries.add("update "
				+ TABLE_FILES
				+ " set revision=?,filename=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=?");
		args.add(new Object[] { file.getRevision(), file.getName(),
				file.isDirectory(), file.getLength(), file.getLastModified(),
				file.getMd5Checksum(), file.getId() });

		for (FtpGDriveFile child : childs) {
			queries.add("insert or replace into "
					+ TABLE_FILES
					+ " (id,revision,filename,isDirectory,length,lastModified,md5checksum)"
					+ " values(?,?,?,?,?,?,?)");
			args.add(new Object[] { child.getId(), child.getRevision(),
					child.getName(), child.isDirectory(), child.getLength(),
					child.getLastModified(), child.getMd5Checksum() });

			for (String parent : child.getParents()) {
				queries.add("insert into " + TABLE_CHILDS
						+ " (childId,parentId) values(?,?)");
				args.add(new Object[] { child.getId(), parent });
			}
		}

		executeInTransaction(queries, args);
	}

	private void updateParents(FtpGDriveFile file, List<String> queries,
			List<Object[]> args) {
		queries.add("delete from " + TABLE_CHILDS + " where childId=?");
		args.add(new Object[] { file.getId() });
		for (String parent : file.getParents()) {
			queries.add("insert into " + TABLE_CHILDS
					+ " (childId,parentId) values(?,?)");
			args.add(new Object[] { file.getId(), parent });
		}
	}

	// private void executeInTransaction(List<String> queries, List<Object[]>
	// args) {
	// w.lock();
	// try {
	// dataSource.getConnection().setAutoCommit(false);
	// for (int i = 0; i < queries.size(); i++) {
	// jdbcTemplate.update(queries.get(i), args.get(i));
	// }
	// dataSource.getConnection().commit();
	// dataSource.getConnection().setAutoCommit(true);
	// } catch (SQLException e) {
	// try {
	// dataSource.getConnection().rollback();
	// } catch (SQLException e1) {
	// throw new RuntimeException(e1.getMessage(), e1);
	// }
	// throw new RuntimeException(e);
	// } finally {
	// w.unlock();
	// }
	// }

	private int executeInTransaction(final List<String> queries,
			final List<Object[]> args) {
		return jdbcTemplate.execute(new ConnectionCallback<Integer>() {
			@Override
			public Integer doInConnection(Connection connection)
					throws SQLException, DataAccessException {
				w.lock();
				try {
					connection.setAutoCommit(false);
					int ret = 0;
					// connection.createStatement().execute("begin transaction");
					for (int i = 0; i < queries.size(); i++) {
						if (args.get(i) == null) {
							ret += connection.createStatement().executeUpdate(
									queries.get(i));
						} else {
							PreparedStatement ps = connection
									.prepareStatement(queries.get(i));
							PreparedStatementSetter pssetter = new ArgumentPreparedStatementSetter(
									args.get(i));
							pssetter.setValues(ps);
							ret += ps.executeUpdate();
						}
					}
					connection.commit();
					// connection.createStatement().execute("commit transaction");
					return ret;
				} finally {
					try {
						connection.setAutoCommit(true);
					} catch (Throwable e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					w.unlock();
				}
			}

		});
	}

	@Override
	public boolean updateFile(FtpGDriveFile file) {
		return jdbcTemplate
				.update("update "
						+ TABLE_FILES
						+ " set revision=?,filename=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=? and revision < ?",
						new Object[] { file.getRevision(), file.getName(),
								file.isDirectory(), file.getLength(),
								file.getLastModified(), file.getMd5Checksum(),
								file.getId(), file.getRevision() }) == 1;
	}

	// public void updateFileAndParents(FtpGDriveFile patch) {
	//
	// List<String> queries = new ArrayList<String>();
	// List<Object[]> args = new ArrayList<Object[]>();
	//
	// queries.add("update "
	// + TABLE_FILES
	// +
	// " set revision=?,filename=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=?");
	// args.add(new Object[] { patch.getRevision(), patch.getName(),
	// patch.isDirectory(), patch.getLength(),
	// patch.getLastModified(), patch.getMd5Checksum(), patch.getId() });
	//
	// updateParents(patch, queries, args);
	//
	// executeInTransaction(queries, args);
	// }

	void addFiles(List<FtpGDriveFile> files) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		for (FtpGDriveFile file : files) {
			addFile(file, queries, args);
			updateParents(file, queries, args);
		}

		executeInTransaction(queries, args);
	}

	// TODO:
	// No se esta poniendo el parentId, ni el path, ni el lastModified....

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache#getFiles(java.lang
	 * .String)
	 */
	@Override
	public List<FtpGDriveFile> getFiles(String parentId) {
		r.lock();
		try {
			final List<FtpGDriveFile> query = jdbcTemplate.query("select "
					+ TABLE_FILES + ".* from " + TABLE_FILES + ","
					+ TABLE_CHILDS + " where " + TABLE_CHILDS + ".childId="
					+ TABLE_FILES + ".id and " + TABLE_CHILDS + ".parentId=?",
					new Object[] { parentId }, rowMapper);
			return query;
		} finally {
			r.unlock();
		}
	}

	public List<String> getAllFolderByRevision(long revision) {
		r.lock();
		try {
			if (revision != -1) {
				return jdbcTemplate.queryForList(
						"select id from " + TABLE_FILES
								+ " where isDirectory=1 and revision = ?",
						new Object[] { revision }, String.class);
			} else {
				return jdbcTemplate.queryForList("select id from "
						+ TABLE_FILES + " where isDirectory=1", String.class);
			}
		} finally {
			r.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache#getFileByName(java
	 * .lang.String, java.lang.String)
	 */
	@Override
	public FtpGDriveFile getFileByName(String parentId, String filename) {
		r.lock();
		try {
			final FtpGDriveFile query = (FtpGDriveFile) jdbcTemplate
					.queryForObject("select " + TABLE_FILES + ".* from "
							+ TABLE_CHILDS + "," + TABLE_FILES + " where "
							+ TABLE_CHILDS + ".childId=" + TABLE_FILES
							+ ".id and " + TABLE_CHILDS + ".parentId=? and "
							+ TABLE_FILES + ".filename=?", new Object[] {
							parentId, filename }, rowMapper);
			return query;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		} finally {
			r.unlock();
		}
	}

	// void updateFileRevision(FtpGDriveFile fileId) {
	// w.lock();
	// try {
	// jdbcTemplate.update("update " + TABLE_FILES
	// + " set revision=? where id=?",
	// new Object[] { fileId.getRevision(), fileId.getId() });
	// } finally {
	// w.unlock();
	// }
	// }

	// public int deleteChilds(String folderId) {
	// w.lock();
	// try {
	// // Intercept here to process duplicated file names
	// return jdbcTemplate.update("delete from " + TABLE_CHILDS
	// + " where parentId=?", new Object[] { folderId });
	// } finally {
	// w.unlock();
	// }
	//
	// }

	public int deleteFile(String id) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		queries.add("delete from " + TABLE_FILES + " where id=?");
		queries.add("delete from " + TABLE_CHILDS + " where parentId=?");
		args.add(new Object[] { id });
		args.add(new Object[] { id });
		return executeInTransaction(queries, args);
	}

	public long getRevision() {
		r.lock();
		try {
			return jdbcTemplate.queryForObject("select max(revision) from "
					+ TABLE_FILES, Long.class);
		} finally {
			r.unlock();
		}
	}

	long getLowerChangedId() {
		r.lock();
		try {

			return jdbcTemplate.queryForObject("select min(revision) from "
					+ TABLE_FILES + " where revision > 0", Long.class);
		} finally {
			r.unlock();
		}
	}

}
