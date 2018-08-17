package org.andresoviedo.google_drive_ftp_adapter.model;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Andres Oviedo
 */
public final class SQLCache implements Cache {

    private static final Log LOG = LogFactory.getLog(SQLCache.class);

    private static final String TABLE_FILES = "files";

    private static final String TABLE_CHILDS = "childs";

    private static final String TABLE_PARAMETERS = "parameters";

    private final RowMapper<GFile> rowMapper;

    private final RowMapper<String> parentIdMapper = (rs, rowNum) -> rs.getString("parentId");

    private final JdbcTemplate jdbcTemplate;

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();

    private final AtomicLong childId = new AtomicLong(System.currentTimeMillis());

    public SQLCache(String driverClassName, String jdbcUrl) {

        LOG.info("JDBC Driver: '" + driverClassName + "'...");
        // LOG.info("JDBC URL: '" + jdbcUrl + "'...");

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(jdbcUrl);
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

        Integer ret = null;
        try {
            ret = jdbcTemplate.queryForObject("SELECT value FROM "+TABLE_PARAMETERS+" where id='revision'", Integer.class);
            LOG.info("Database found");
        } catch (DataAccessException e) {
            List<String> queries = new ArrayList<>();
            queries.add("create table " + TABLE_FILES + " (id character varying(100), revision character varying(100), "
                    + "filename character varying(100) not null, isDirectory boolean, size bigint, lastModified bigint, mimeType character varying(100), "
                    + "md5Checksum character varying(100), primary key (id))");
            queries.add("create table " + TABLE_CHILDS + " (id bigint primary key, childId character varying(100) references " + TABLE_FILES
                    + "(id), parentId character varying(100) references " + TABLE_FILES + "(id), unique (childId, parentId))");
            queries.add("create table " + TABLE_PARAMETERS + " (id character varying(100), value character varying(100), primary key (id))");
            queries.add("create index idx_filename on " + TABLE_FILES + " (filename)");
            queries.add("insert into " + TABLE_PARAMETERS + " (id,value) values('revision',null)");

            jdbcTemplate.batchUpdate(queries.toArray(new String[0]));

            LOG.info("Database created");
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

        GFile cachedFile = getFile(file.getId());
        if (cachedFile == null){
            queries.add("insert into " + TABLE_FILES + " (id,revision,filename,isDirectory,size,lastModified,mimeType,md5checksum)"
                    + " values(?,?,?,?,?,?,?,?)");
            args.add(new Object[]{file.getId(), file.getRevision(), file.getName(), file.isDirectory(), file.getSize(),
                    file.getLastModified(), file.getMimeType(), file.getMd5Checksum()});
        }
        else {
            queries.add("update " + TABLE_FILES + " set revision=?,filename=?,isDirectory=?,size=?,lastModified=?,mimeType=?,md5checksum=?"
                    + " where id=?");
            args.add(new Object[]{file.getRevision(), file.getName(), file.isDirectory(), file.getSize(),
                    file.getLastModified(), file.getMimeType(), file.getMd5Checksum(), file.getId()});
        }

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
            GFile cachedChild = getFile(child.getId());
            if (cachedChild == null) {
                queries.add("insert into " + TABLE_FILES + " (id,revision,filename,isDirectory,size,lastModified,mimeType,md5checksum)"
                        + " values(?,?,?,?,?,?,?,?)");
                args.add(new Object[]{child.getId(), child.getRevision(), child.getName(), child.isDirectory(), child.getSize(),
                        child.getLastModified(), child.getMimeType(), child.getMd5Checksum()});
            } else{
                queries.add("update " + TABLE_FILES + " set revision=?,filename=?,isDirectory=?,size=?,lastModified=?,mimeType=?,md5checksum=?"
                        + " where id=?");
                args.add(new Object[]{child.getRevision(), child.getName(), child.isDirectory(), child.getSize(),
                        child.getLastModified(), child.getMimeType(), child.getMd5Checksum(), child.getId()});
            }
            queries.add("insert into " + TABLE_CHILDS + " (id,childId,parentId) values(?,?,?)");
            args.add(new Object[]{childId.getAndIncrement(), child.getId(), file.getId()});
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
            queries.add("insert into " + TABLE_CHILDS + " (id,childId,parentId) values(?,?,?)");
            args.add(new Object[]{childId.getAndIncrement(), file.getId(), parent});
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
        queries.add("delete from " + TABLE_CHILDS + " where parentId=?");
        queries.add("delete from " + TABLE_CHILDS + " where childId=?");
        queries.add("delete from " + TABLE_FILES + " where id=?");
        args.add(new Object[]{id});
        args.add(new Object[]{id});
        args.add(new Object[]{id});
        return executeInTransaction(queries, args);
    }

    public String getRevision() {
        r.lock();
        try {
            return jdbcTemplate.queryForObject("select value from " + TABLE_PARAMETERS + " where id='revision'", String.class);
        } finally {
            r.unlock();
        }
    }

    public void updateRevision(String revision) {
        jdbcTemplate.update("update " + TABLE_PARAMETERS + " set value=? where id='revision'",revision);
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
