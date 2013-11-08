package org.andresoviedo.apps.gdrive_ftp_adapter.cache;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.GDriveFile;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
public final class SQLiteCache implements Cache {

	private static Log logger = LogFactory.getLog(SQLiteCache.class);

	private static final String TABLE_FILES = "files";

	private static final String TABLE_CHILDS = "childs";

	private static SQLiteCache instance;

	private RowMapper<GDriveFile> rowMapper;

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
		dataSource = new BasicDataSource();
		dataSource.setDriverClassName("org.sqlite.JDBC");
		dataSource.setUrl("jdbc:sqlite:gdrive.db");
		// dataSource.setMaxActive(1);
		dataSource.setMaxWait(60000);

		jdbcTemplate = new JdbcTemplate(dataSource);

		rowMapper = new RowMapper<GDriveFile>() {
			@Override
			public GDriveFile mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				GDriveFile ret = new GDriveFile();
				ret.setId(rs.getString("id"));
				// TODO: hace falta informar los parents aqui?
				// ret.setParentId(rs.getString("parentId"));
				ret.setName(rs.getString("filename"));
				ret.setRevision(rs.getLong("revision"));
				ret.setDirectory(rs.getBoolean("isDirectory"));
				ret.setLength(rs.getLong("length"));
				ret.setLastModified2(rs.getLong("lastModified"));
				ret.setMd5Checksum(rs.getString("md5Checksum"));
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
	public GDriveFile getFile(String id) {
		r.lock();
		try {
			logger.trace("getFile(" + id + ")");
			GDriveFile file = (GDriveFile) jdbcTemplate.queryForObject(
					"select * from " + TABLE_FILES + " where id=?",
					new Object[] { id }, rowMapper);
			return file;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		} finally {
			r.unlock();
		}
	}

	public void addFile(GDriveFile file) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		queries.add("insert into "
				+ TABLE_FILES
				+ " (id,revision,filename,isDirectory,length,lastModified,md5checksum)"
				+ " values(?,?,?,?,?,?,?)");
		args.add(new Object[] { file.getId(), file.getRevision(),
				file.getName(), file.isDirectory(), file.getLength(),
				file.getLastModified(), file.getMd5Checksum() });

		updateChilds(file, queries, args);

		executeInTransaction(queries, args);
	}

	void addFile(GDriveFile file, List<String> queries, List<Object[]> args) {
		queries.add("insert into "
				+ TABLE_FILES
				+ " (id, revision,filename,isDirectory,length,lastModified,md5checksum)"
				+ " values(?,?,?,?,?,?,?)");
		args.add(new Object[] { file.getId(), file.getRevision(),
				file.getName(), file.isDirectory(), file.getLength(),
				file.getLastModified(), file.getMd5Checksum() });
	}

	public void updateChilds(GDriveFile file, List<GDriveFile> childs) {
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

		for (GDriveFile child : childs) {
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

	private void updateChilds(GDriveFile file, List<String> queries,
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

	private void executeInTransaction(List<String> queries, List<Object[]> args) {
		w.lock();
		try {
			dataSource.getConnection().setAutoCommit(false);
			jdbcTemplate.execute("begin transaction");
			for (int i = 0; i < queries.size(); i++) {
				logger.warn("Executing " + queries.get(i) + " ==> "
						+ Arrays.toString(args.get(i)));
				if (args.get(i) == null) {
					jdbcTemplate.update(queries.get(i));
				} else {
					jdbcTemplate.update(queries.get(i), args.get(i));
				}
			}
			jdbcTemplate.execute("commit transaction");
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
			try {
				dataSource.getConnection().rollback();

			} catch (SQLException e1) {
				logger.error(e.getMessage(), e1);
			}
		} finally {
			try {
				dataSource.getConnection().setAutoCommit(true);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			w.unlock();
		}
	}

	public void updateFile(GDriveFile patch) {

		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();

		queries.add("update "
				+ TABLE_FILES
				+ " set revision=?,filename=?,isDirectory=?,length=?,lastModified=?,md5checksum=? where id=?");
		args.add(new Object[] { patch.getRevision(), patch.getName(),
				patch.isDirectory(), patch.getLength(),
				patch.getLastModified(), patch.getMd5Checksum(), patch.getId() });

		updateChilds(patch, queries, args);

		executeInTransaction(queries, args);
	}

	void addFiles(List<GDriveFile> files) {
		List<String> queries = new ArrayList<String>();
		List<Object[]> args = new ArrayList<Object[]>();
		for (GDriveFile file : files) {
			addFile(file, queries, args);
			updateChilds(file, queries, args);
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
	public List<GDriveFile> getFiles(String parentId) {
		r.lock();
		try {
			final List<GDriveFile> query = jdbcTemplate.query("select "
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
	public GDriveFile getFileByName(String parentId, String filename) {
		r.lock();
		try {
			final GDriveFile query = (GDriveFile) jdbcTemplate.queryForObject(
					"select " + TABLE_FILES + ".* from " + TABLE_CHILDS + ","
							+ TABLE_FILES + " where " + TABLE_CHILDS
							+ ".childId=" + TABLE_FILES + ".id and "
							+ TABLE_CHILDS + ".parentId=? and " + TABLE_FILES
							+ ".filename=?",
					new Object[] { parentId, filename }, rowMapper);
			return query;
		} catch (EmptyResultDataAccessException ex) {
			return null;
		} finally {
			r.unlock();
		}
	}

	void updateFilerevision(GDriveFile folderFile) {
		w.lock();
		try {
			jdbcTemplate
					.update("update " + TABLE_FILES
							+ " set revision=? where id=?", new Object[] {
							folderFile.getRevision(), folderFile.getId() });
		} finally {
			w.unlock();
		}
	}

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
		w.lock();
		try {
			return jdbcTemplate.update("delete from " + TABLE_FILES
					+ " where id=?", new Object[] { id });
		} finally {
			w.unlock();
		}

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
