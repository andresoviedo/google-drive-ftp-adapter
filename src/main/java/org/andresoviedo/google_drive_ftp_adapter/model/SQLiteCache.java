package org.andresoviedo.google_drive_ftp_adapter.model;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.*;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Andres Oviedo
 */
public final class SQLiteCache implements Cache {

    private static final Log LOG = LogFactory.getLog(SQLiteCache.class);

    private static final String TABLE_FILES = "files";

    private static final String TABLE_CHILDS = "childs";

    private static final String TABLE_PARAMETERS = "parameters";

    private final RowMapper<GFile> rowMapper;

    private final RowMapper<String> parentIdMapper = (rs, rowNum) -> rs.getString("parentId");

    private final JdbcTemplate jdbcTemplate;

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();

    public SQLiteCache(Properties configuration) {

        // initialize the data store factory
        String account = configuration.getProperty("account", "default");
        final String pathname = "data/cache";
        File dataDir = new File(pathname);
        if (!dataDir.exists()) {
            LOG.info("Creating cache '" + dataDir + "'...");
            if (!dataDir.mkdirs()) {
                throw new RuntimeException("Could not create database folder " + dataDir.getAbsolutePath());
            }
        }

        String dataFile = pathname + "/" + account + ".db";
        LOG.info("Loading database '" + dataFile + "'...");

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:file:" + dataFile);
        // dataSource.setMaxActive(1);
        dataSource.setMaxWait(60000);

        jdbcTemplate = new JdbcTemplate(dataSource);

        rowMapper = (rs, rowNum) -> {
            GFile ret = new GFile(null);
            ret.setId(rs.getString("id"));
            ret.setName(rs.getString("filename"));
            ret.setRevision(rs.getString("revision"));
            ret.setDirectory(rs.getBoolean("isDirectory"));
            ret.setSize(rs.getLong("size"));
            ret.setLastModified(rs.getLong("lastModified"));
            ret.setMimeType(rs.getString("mimeType"));
            ret.setMd5Checksum(rs.getString("md5Checksum"));
            ret.setExists(true);
            return ret;
        };

        Integer ret = jdbcTemplate.queryForObject("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='" + TABLE_FILES + "';",
                Integer.class);
        if (ret == 0) {
            List<String> queries = new ArrayList<>();
            queries.add("create table " + TABLE_FILES + " (id text, revision text, "
                    + "filename text not null, isDirectory boolean, size integer, lastModified integer, mimeType text, "
                    + "md5Checksum text, primary key (id))");
            queries.add("create table " + TABLE_CHILDS + " (id integer primary key, childId text references " + TABLE_FILES
                    + "(id), parentId text references " + TABLE_FILES + "(id), unique (childId, parentId))");
            queries.add("create table " + TABLE_PARAMETERS + " (id text, value text, primary key (id))");
            queries.add("create index idx_filename on " + TABLE_FILES + " (filename)");
            jdbcTemplate.batchUpdate(queries.toArray(new String[0]));

            LOG.info("Database created");
        } else {
            LOG.info("Database found");
        }
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
            return jdbcTemplate.queryForObject("select * from " + TABLE_FILES + " where id=?", new Object[]{id},
                    rowMapper);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } finally {
            r.unlock();
        }
    }

    public int addOrUpdateFile(GFile file) {
        List<String> queries = new ArrayList<>();
        List<Object[]> args = new ArrayList<>();

        queries.add("insert or replace into " + TABLE_FILES + " (id,revision,filename,isDirectory,size,lastModified,mimeType,md5checksum)"
                + " values(?,?,?,?,?,?,?,?)");
        args.add(new Object[]{file.getId(), file.getRevision(), file.getName(), file.isDirectory(), file.getSize(),
                file.getLastModified(), file.getMimeType(), file.getMd5Checksum()});

        updateParents(file, queries, args);

        return executeInTransaction(queries, args);
    }

    /**
     * Add file and childs
     *
     * @param file   file to add
     * @param childs childs to add
     */
    // TODO: merge de este con el addFile
    public void updateChilds(GFile file, List<GFile> childs) {
        List<String> queries = new ArrayList<>();
        List<Object[]> args = new ArrayList<>();
        queries.add("delete from " + TABLE_CHILDS + " where parentId=?");
        args.add(new Object[]{file.getId()});

        queries.add("update " + TABLE_FILES + " set revision=?,filename=?,isDirectory=?,size=?,lastModified=?,mimeType=?,md5checksum=? where id=?");
        args.add(new Object[]{file.getRevision(), file.getName(), file.isDirectory(), file.getSize(), file.getLastModified(),file.getMimeType(),
                file.getMd5Checksum(), file.getId()});

        for (GFile child : childs) {
            queries.add("insert or replace into " + TABLE_FILES + " (id,revision,filename,isDirectory,size,lastModified,mimeType,md5checksum)"
                    + " values(?,?,?,?,?,?,?,?)");
            args.add(new Object[]{child.getId(), child.getRevision(), child.getName(), child.isDirectory(), child.getSize(),
                    child.getLastModified(), child.getMimeType(), child.getMd5Checksum()});

            for (String parent : child.getParents()) {
                queries.add("insert into " + TABLE_CHILDS + " (childId,parentId) values(?,?)");
                args.add(new Object[]{child.getId(), parent});
            }
        }

        executeInTransaction(queries, args);
    }

    /**
     * Create queries to update the parents of the specified file
     *
     * @param file    file with new parents
     * @param queries output parameter. queries to execute
     * @param args    output parameter. arguments to add to query
     */
    private void updateParents(GFile file, List<String> queries, List<Object[]> args) {
        queries.add("delete from " + TABLE_CHILDS + " where childId=?");
        args.add(new Object[]{file.getId()});
        for (String parent : file.getParents()) {
            queries.add("insert into " + TABLE_CHILDS + " (childId,parentId) values(?,?)");
            args.add(new Object[]{file.getId(), parent});
        }
    }

    private int executeInTransaction(final List<String> queries, final List<Object[]> args) {
        return jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            w.lock();
            try {
                connection.setAutoCommit(false);
                int ret = 0;
                for (int i = 0; i < queries.size(); i++) {
                    if (args == null || args.get(i) == null) {
                        ret += connection.createStatement().executeUpdate(queries.get(i));
                    } else {
                        PreparedStatement ps = connection.prepareStatement(queries.get(i));
                        PreparedStatementSetter psSetter = new ArgumentPreparedStatementSetter(args.get(i));
                        psSetter.setValues(ps);
                        ret += ps.executeUpdate();
                    }
                }
                connection.commit();
                return ret;
            } catch (Exception ex) {
                LOG.error("Error executing transaction. " + ex.getMessage());
                /*for (int i = 0; i < queries.size(); i++) {
                    LOG.error("Query '" + queries.get(i) + "' " + (args != null && i < args.size() ? " args:" + Arrays.toString(args.get(i)) : "") + "' ");
                }*/
                throw new RuntimeException(ex);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                w.unlock();
            }
        });
    }

    @Override
    public List<GFile> getFiles(String parentId) {
        System.out.println();
        r.lock();
        try {
            return jdbcTemplate.query("select " + TABLE_FILES + ".* from " + TABLE_FILES + "," + TABLE_CHILDS
                            + " where " + TABLE_CHILDS + ".childId=" + TABLE_FILES + ".id and " + TABLE_CHILDS + ".parentId=?",
                    new Object[]{parentId}, rowMapper);
        } finally {
            r.unlock();
        }
    }

    public List<String> getAllFoldersWithoutRevision() {
        r.lock();
        try {
            return jdbcTemplate.queryForList("select id from " + TABLE_FILES + " where isDirectory=1 and revision is null", String.class);
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
            return jdbcTemplate.queryForObject("select " + TABLE_FILES + ".* from " + TABLE_CHILDS + ","
                    + TABLE_FILES + " where " + TABLE_CHILDS + ".childId=" + TABLE_FILES + ".id and " + TABLE_CHILDS + ".parentId=? and "
                    + TABLE_FILES + ".filename=?", new Object[]{parentId, filename}, rowMapper);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } finally {
            r.unlock();
        }
    }

    /**
     * Deletes file and it's children
     *
     * @param id the file id to delete
     * @return number of affected records
     */
    public int deleteFile(String id) {
        List<String> queries = new ArrayList<>();
        List<Object[]> args = new ArrayList<>();
        queries.add("delete from " + TABLE_FILES + " where id=?");
        queries.add("delete from " + TABLE_CHILDS + " where parentId=?");
        args.add(new Object[]{id});
        args.add(new Object[]{id});
        return executeInTransaction(queries, args);
    }

    public String getRevision() {
        r.lock();
        try {
            return jdbcTemplate.queryForObject("select value from " + TABLE_PARAMETERS + " where id='revision'", String.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } finally {
            r.unlock();
        }
    }

    public void updateRevision(String revision) {
        if (revision == null) {
            throw new IllegalArgumentException("revision can't be null");
        }
        executeInTransaction(Collections.singletonList("insert or replace into " + TABLE_PARAMETERS + " (id,value) values('revision','" + revision + "')"),
                null);
    }

    public Set<String> getParents(String fileId) {
        r.lock();
        try {
            return new HashSet<>(jdbcTemplate.query("select parentId from " + TABLE_CHILDS
                    + " where childId=?", new Object[]{fileId}, parentIdMapper));
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } finally {
            r.unlock();
        }
    }
}
