package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.*;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * TODO:
 * 
 * 1- Reparar caché 2- Sincronización
 * 
 * @author Andres Oviedo
 * 
 */
public final class SQLiteCache implements Cache {

	private static final Log LOG = LogFactory.getLog(SQLiteCache.class);

	private static final String TABLE_FILES = "files";

	private static final String TABLE_CHILDS = "childs";

	@SuppressWarnings("unused")
	private final Properties configuration;

	private final RowMapper<GFile> rowMapper;

	private final JdbcTemplate jdbcTemplate;

	private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	private final Lock r = rwl.readLock();
	private final Lock w = rwl.writeLock();

	private BasicDataSource dataSource;

	public SQLiteCache(Properties configuration) {
		this.configuration = configuration;

		// initialize the data store factory
		String account = configuration.getProperty("account", "default");
		File dataDir = new File("data" + File.separator + account);
		if (!dataDir.exists()) {
			LOG.info("Creating cache '" + dataDir + "'...");
			if (!dataDir.mkdirs()) {
				throw new RuntimeException("Could not create database folder " + dataDir.getAbsolutePath());
			}
		}

		String dataFile = "data/" + account + "/gdrive.db";
		LOG.info("Loading database '" + dataFile + "'...");

		dataSource = new BasicDataSource();
		dataSource.setDriverClassName("org.sqlite.JDBC");
		dataSource.setUrl("jdbc:sqlite:file:" + dataFile);
		// dataSource.setMaxActive(1);
		dataSource.setMaxWait(60000);

		jdbcTemplate = new JdbcTemplate(dataSource);

		rowMapper = new RowMapper<GFile>() {
			@Override
			public GFile mapRow(ResultSet rs, int rowNum) throws SQLException {
				GFile ret = new GFile();
				ret.setId(rs.getString("id"));
				// TODO: hace falta informar los parents aqui?
				// ret.setParentId(rs.getString("parentId"));
				ret.setName(rs.getString("filename"));
				ret.setRevision(rs.getLong("revision"));
				ret.setDirectory(rs.getBoolean("isDirectory"));
				ret.setLength(rs.getLong("length"));
				ret.setLastModified(rs.getLong("lastModified"));
				ret.setMd5Checksum(rs.getString("md5Checksum"));
				ret.setExists(true);
				return ret;
			}
		};

		Integer ret = jdbcTemplate.queryForObject("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='" + TABLE_FILES + "';",
				Integer.class);
		if (ret == 0) {
			List<String> queries = new ArrayList<String>();
			queries.add("create table " + TABLE_FILES + " (id text, revision integer, "
					+ "filename text not null, isDirectory boolean, length integer, lastModified integer, "
					+ "md5Checksum text, primary key (id))");
			queries.add("create table " + TABLE_CHILDS + " (id integer primary key, childId text references " + TABLE_FILES
					+ "(id), parentId text references " + TABLE_FILES + "(id), unique (childId, parentId))");
			queries.add("create index idx_filename on " + TABLE_FILES + " (filename)");
			jdbcTemplate.batchUpdate(queries.toArray(new String[queries.size()]));

			LOG.info("Database created");
		} else {
			LOG.info("Database found");
		}

		// jdbcTemplate.execute(".timeout 10000");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.andresoviedo.apps.gdrive_ftp_adapter.service.Cache#getFile(java.lang .String)
	 */
	@Override
	public GFile getFile(String id) {
		r.lock();
		try {
			LOG.trace("getFile(" + id + ")");
			GFile file = (GFile) jdbcTemplate.queryForObject("select * from " + TABLE_FILES + " where id=?", new Object[] { id },
					rowMapper);
			return file;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		} finally {
			r.unlock();
		}
	}

	public void addOrUpdateFile(GFile file) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		// queries.add("insert into "
		// + TABLE_FILES
		// +
		// " (id,revision,filename,isDirectory,length,lastModified,md5checksum)"
		// + " values(?,?,?,?,?,?,?)");
		queries.add("insert or replace into " + TABLE_FILES + " (id, revision,filename,isDirectory,length,lastModified,md5checksum)"
				+ " values(?,?,?,?,?,?,?)");
		args.add(new Object[] { file.getId(), file.getRevision(), file.getName(), file.isDirectory(), file.getLength(),
				file.getLastModified(), file.getMd5Checksum() });

		updateParents(file, queries, args);

		executeInTransaction(queries, args);
	}

	void addFile(GFile file, List<String> queries, List<Object[]> args) {
		queries.add("insert into " + TABLE_FILES + " (id, revision,filename,isDirectory,length,lastModified,md5checksum)"
				+ " values(?,?,?,?,?,?,?)");
		args.add(new Object[] { file.getId(), file.getRevision(), file.getName(), file.isDirectory(), file.getLength(),
				file.getLastModified(), file.getMd5Checksum() });
	}

	// TODO: merge de este con el addFile
	public void updateChilds(GFile file, List<GFile> childs) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		queries.add("delete from " + TABLE_CHILDS + " where parentId=?");
		args.add(new Object[] { file.getId() });

		queries.add("update " + TABLE_FILES + " set revision=?,filename=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=?");
		args.add(new Object[] { file.getRevision(), file.getName(), file.isDirectory(), file.getLength(), file.getLastModified(),
				file.getMd5Checksum(), file.getId() });

		for (GFile child : childs) {
			queries.add("insert or replace into " + TABLE_FILES + " (id,revision,filename,isDirectory,length,lastModified,md5checksum)"
					+ " values(?,?,?,?,?,?,?)");
			args.add(new Object[] { child.getId(), child.getRevision(), child.getName(), child.isDirectory(), child.getLength(),
					child.getLastModified(), child.getMd5Checksum() });

			for (String parent : child.getParents()) {
				queries.add("insert into " + TABLE_CHILDS + " (childId,parentId) values(?,?)");
				args.add(new Object[] { child.getId(), parent });
			}
		}

		executeInTransaction(queries, args);
	}

	private void updateParents(GFile file, List<String> queries, List<Object[]> args) {
		queries.add("delete from " + TABLE_CHILDS + " where childId=?");
		args.add(new Object[] { file.getId() });
		for (String parent : file.getParents()) {
			queries.add("insert into " + TABLE_CHILDS + " (childId,parentId) values(?,?)");
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

	private int executeInTransaction(final List<String> queries, final List<Object[]> args) {
		return jdbcTemplate.execute(new ConnectionCallback<Integer>() {
			@Override
			public Integer doInConnection(Connection connection) throws SQLException, DataAccessException {
				w.lock();
				try {
					connection.setAutoCommit(false);
					int ret = 0;
					// connection.createStatement().execute("begin transaction");
					for (int i = 0; i < queries.size(); i++) {
						if (args.get(i) == null) {
							ret += connection.createStatement().executeUpdate(queries.get(i));
						} else {
							PreparedStatement ps = connection.prepareStatement(queries.get(i));
							PreparedStatementSetter pssetter = new ArgumentPreparedStatementSetter(args.get(i));
							pssetter.setValues(ps);
							ret += ps.executeUpdate();
						}
					}
					connection.commit();
					// connection.createStatement().execute("commit transaction");
					return ret;
				} catch (Exception ex) {
					LOG.error("Error executing transaction. Details:");
					for (int i = 0; i < queries.size(); i++) {
						LOG.error("Query '" + queries.get(i) + "' args '" + Arrays.toString(args.get(i)) + "' ");
					}
					throw new RuntimeException(ex);
				} finally {
					try {
						connection.setAutoCommit(true);
					} catch (Throwable e) {
						throw new RuntimeException(e);
					}
					w.unlock();
				}
			}

		});
	}

	@Override
	public boolean updateFile(GFile file) {
		return jdbcTemplate.update(
				"update " + TABLE_FILES
						+ " set revision=?,filename=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=? and revision < ?",
				new Object[] { file.getRevision(), file.getName(), file.isDirectory(), file.getLength(), file.getLastModified(),
						file.getMd5Checksum(), file.getId(), file.getRevision() }) == 1;
	}

	// public void updateFileAndParents(FTPGFile patch) {
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

	void addFiles(List<GFile> files) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		for (GFile file : files) {
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
	 * @see org.andresoviedo.apps.gdrive_ftp_adapter.service.Cache#getFiles(java.lang .String)
	 */
	@Override
	public List<GFile> getFiles(String parentId) {
		System.out.println();
		r.lock();
		try {
			final List<GFile> query = jdbcTemplate.query("select " + TABLE_FILES + ".* from " + TABLE_FILES + "," + TABLE_CHILDS
					+ " where " + TABLE_CHILDS + ".childId=" + TABLE_FILES + ".id and " + TABLE_CHILDS + ".parentId=?",
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
				return jdbcTemplate.queryForList("select id from " + TABLE_FILES + " where isDirectory=1 and revision = ?",
						new Object[] { revision }, String.class);
			} else {
				return jdbcTemplate.queryForList("select id from " + TABLE_FILES + " where isDirectory=1", String.class);
			}
		} finally {
			r.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.andresoviedo.apps.gdrive_ftp_adapter.service.Cache#getFileByName(java .lang.String, java.lang.String)
	 */
	@Override
	public GFile getFileByName(String parentId, String filename) throws IncorrectResultSizeDataAccessException {
		r.lock();
		try {
			final GFile query = (GFile) jdbcTemplate.queryForObject("select " + TABLE_FILES + ".* from " + TABLE_CHILDS + ","
					+ TABLE_FILES + " where " + TABLE_CHILDS + ".childId=" + TABLE_FILES + ".id and " + TABLE_CHILDS + ".parentId=? and "
					+ TABLE_FILES + ".filename=?", new Object[] { parentId, filename }, rowMapper);
			return query;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		} finally {
			r.unlock();
		}
	}

	// void updateFileRevision(FTPGFile fileId) {
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
			return jdbcTemplate.queryForObject("select max(revision) from " + TABLE_FILES, Long.class);
		} finally {
			r.unlock();
		}
	}

	long getLowerChangedId() {
		r.lock();
		try {

			return jdbcTemplate.queryForObject("select min(revision) from " + TABLE_FILES + " where revision > 0", Long.class);
		} finally {
			r.unlock();
		}
	}

}
