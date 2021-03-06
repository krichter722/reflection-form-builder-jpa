/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.richtercloud.reflection.form.builder.jpa.storage;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import de.richtercloud.message.handler.IssueHandler;
import de.richtercloud.reflection.form.builder.jpa.sequence.MySQLSequenceManager;
import de.richtercloud.reflection.form.builder.jpa.sequence.SequenceManager;
import de.richtercloud.reflection.form.builder.storage.StorageConfValidationException;
import de.richtercloud.reflection.form.builder.storage.StorageCreationException;
import de.richtercloud.validation.tools.FieldRetriever;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Using {@link MySQLAutoPersistenceStorageConf#getPassword() } for MySQL
 * {@code root} password since {@code document-scanner} is the only user using
 * the database.
 *
 * Since the Java Process API is quite painful, doesn't implement a mechanism to
 * kill an eventually remaining {@code mysqld} process from previous run (using
 * a PID file (Java seriously doesn't tell you the PID without a hack)), improve
 * shutdown mechanism and leave shutting down the server to the user.
 *
 * @author richter
 */
public class MySQLAutoPersistenceStorage extends AbstractProcessPersistenceStorage<MySQLAutoPersistenceStorageConf> {
    private final static Logger LOGGER = LoggerFactory.getLogger(MySQLAutoPersistenceStorage.class);
    /**
     * Unclear why {@code --socket} has to be specified for connections to
     * localhost when using {@code mysqladmin} with {@code --host} option and
     * specifying {@code bind-address} in {@code my.cnf}.
     */
    private final static String SOCKET = "/tmp/mysql.document-scanner.socket";
    private final static String RUNNING_COMMAND_TEMPLATE = "running command '%s'";
    private final static String COMMAND_FAILED_TEMPLATE = "command '%s' failed with returncode %d";
    private final static String SOCKET_TEMPLATE = "--socket=%s";
    private final static String USER_TEMPLATE = "--user=root";

    public MySQLAutoPersistenceStorage(MySQLAutoPersistenceStorageConf storageConf,
            String persistenceUnitName,
            int parallelQueryCount,
            IssueHandler issueHandler,
            FieldRetriever fieldRetriever) throws StorageConfValidationException, StorageCreationException {
        super(storageConf,
                persistenceUnitName,
                parallelQueryCount,
                fieldRetriever,
                issueHandler,
                String.format("MySQL server at %s:%d",
                        storageConf.getHostname(),
                        storageConf.getPort()));
    }

    @Override
    protected SequenceManager<Long> createSequenceManager() {
        return new MySQLSequenceManager(this);
    }

    @Override
    protected void preCreation() throws IOException {
        assert getStorageConf() != null: "storage configuration mustn't be null";
        assert new File(getStorageConf().getMysqld()).exists(): String.format("mysqld '%s' specified in storage configuration doesn't exist",
                getStorageConf().getMysqld());
        assert new File(getStorageConf().getMysqladmin()).exists(): String.format("mysqladmin '%s' specified in storage configuration doesn't exist",
                getStorageConf().getMysqladmin());
        assert new File(getStorageConf().getMysql()).exists(): String.format("mysql '%s' specified in storage configuration doesn't exist",
                getStorageConf().getMysql());
        File myCnfFile = new File(getStorageConf().getMyCnfFilePath());
        if(!myCnfFile.exists()) {
            LOGGER.debug(String.format("creating inexisting configuration file '%s'", myCnfFile.getAbsolutePath()));
            Files.write(Paths.get(myCnfFile.getAbsolutePath()),
                    String.format("[server]\n"
                            + "user=%s\n"
                            + "basedir=%s\n"
                            + "datadir=%s\n"
                            + "socket=%s\n"
                            + "bind-address=%s\n"
                            + "port=%d\n"
                            + "max_allowed_packet=1073741824\n",
                                //allows upload of binary image data
                                //up to ca. 1 GB (default of 4 MB
                                //is too small) since a 10 page scan can
                                //easily have 200 MB of image data
                                //avoid `Caused by: com.mysql.cj.jdbc.exceptions.PacketTooBigException: Packet for query is too large (24.088.697 > 4.194.304). You can change this value on the server by setting the 'max_allowed_packet' variable.`
                            getStorageConf().getUsername(),
                            getStorageConf().getBaseDir(),
                            getStorageConf().getDatabaseDir(),
                            SOCKET,
                            getStorageConf().getHostname(),
                            getStorageConf().getPort()).getBytes(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE
            );
        }
    }

    @Override
    protected boolean needToCreate() {
        File databaseDirFile = new File(getStorageConf().getDatabaseDir());
        return !databaseDirFile.exists();
    }

    @Override
    protected void createDatabase() throws IOException, StorageCreationException, InterruptedException {
        File databaseDirFile = new File(getStorageConf().getDatabaseDir());
        LOGGER.debug(String.format("creating inexisting database directory '%s'", getStorageConf().getDatabaseDir()));
        File myCnfFile = new File(getStorageConf().getMyCnfFilePath());
        FileUtils.forceMkdir(databaseDirFile);
        ProcessBuilder mysqldInitProcessBuilder = new ProcessBuilder(getStorageConf().getMysqld(),
                String.format("--defaults-file=%s", myCnfFile.getAbsolutePath()),
                "--initialize-insecure" //use --initialize-insecure
                    //instead of --initialize because it's hard to
                    //retrieve the random password for root
                    //from the logs
        );
        LOGGER.debug(String.format(RUNNING_COMMAND_TEMPLATE,
                mysqldInitProcessBuilder.command().toString()));
        Process mysqldInitProcess = mysqldInitProcessBuilder.start();
        mysqldInitProcess.waitFor();
        IOUtils.copy(mysqldInitProcess.getInputStream(), System.out);
        IOUtils.copy(mysqldInitProcess.getErrorStream(), System.err);
        if(mysqldInitProcess.exitValue() != 0) {
            throw new StorageCreationException(String.format(COMMAND_FAILED_TEMPLATE,
                    mysqldInitProcessBuilder.command(), mysqldInitProcess.exitValue()));
        }
    }

    @Override
    protected Process createProcess() throws IOException {
        //if mysqld is already running (because shutdown in the last run of
        //document-scanner failed/couldn't be performed) or a system process
        //is using the same host and port combination, mysqld will simply
        //fail in mysqldThread without the failure being noticed -> @TODO
        File myCnfFile = new File(getStorageConf().getMyCnfFilePath());
        ProcessBuilder mysqldProcessBuilder = new ProcessBuilder(getStorageConf().getMysqld(),
                String.format("--defaults-file=%s", myCnfFile.getAbsolutePath()));
        LOGGER.debug(String.format(RUNNING_COMMAND_TEMPLATE,
                mysqldProcessBuilder.command().toString()));
        return mysqldProcessBuilder.start();
    }

    @Override
    protected void setupDatabase() throws IOException, InterruptedException, StorageCreationException {
        //set password for root user
        ProcessBuilder mysqladminProcessBuilder = new ProcessBuilder(getStorageConf().getMysqladmin(),
                String.format(SOCKET_TEMPLATE, SOCKET),
                USER_TEMPLATE,
                "password", getStorageConf().getPassword());
            //don't need to specify password here because it
            //isn't set yet
            //specification of --host causes failure here (but
            //succeeds after the password has been set)
        Process mysqladminProcess = null;
        int mysqladminTries = 0;
        int mysqladminTriesMax = 5;
        while(mysqladminTries < mysqladminTriesMax) {
            LOGGER.debug(String.format(RUNNING_COMMAND_TEMPLATE,
                    mysqladminProcessBuilder.command().toString()));
            mysqladminProcess = mysqladminProcessBuilder.start();
            mysqladminProcess.waitFor();
            IOUtils.copy(mysqladminProcess.getInputStream(), System.out);
            IOUtils.copy(mysqladminProcess.getErrorStream(), System.err);
            if(mysqladminProcess.exitValue() == 0) {
                break;
            }
            mysqladminTries += 1;
            LOGGER.debug("sleeping 0.5 s to wait for mysqld to be available");
            Thread.sleep(500);
        }
        if(mysqladminTries == mysqladminTriesMax) {
            throw new StorageCreationException(String.format("command '%s' failed 5 times (last time with returncode %d)", mysqladminProcessBuilder.command(), mysqladminProcess.exitValue()));
        }
        //create database
        mysqladminProcessBuilder = new ProcessBuilder(getStorageConf().getMysqladmin(),
                String.format(SOCKET_TEMPLATE, SOCKET),
                String.format("--host=%s", getStorageConf().getHostname()),
                USER_TEMPLATE,
                String.format("--password=%s", getStorageConf().getPassword()),
                "create", getStorageConf().getDatabaseName());
        LOGGER.debug(String.format(RUNNING_COMMAND_TEMPLATE,
                mysqladminProcessBuilder.command().toString()));
        mysqladminProcess = mysqladminProcessBuilder.start();
        mysqladminProcess.waitFor();
        IOUtils.copy(mysqladminProcess.getInputStream(), System.out);
        IOUtils.copy(mysqladminProcess.getErrorStream(), System.err);
        if(mysqladminProcess.exitValue() != 0) {
            throw new StorageCreationException(String.format(COMMAND_FAILED_TEMPLATE,
                    mysqladminProcessBuilder.command(), mysqladminProcess.exitValue()));
        }
        //create user document-scanner
        ProcessBuilder mysqlProcessBuilder = new ProcessBuilder(getStorageConf().getMysql(),
                String.format(SOCKET_TEMPLATE, SOCKET),
                String.format("--host=%s", getStorageConf().getHostname()),
                USER_TEMPLATE,
                String.format("--password=%s", getStorageConf().getPassword()),
                getStorageConf().getDatabaseName());
        LOGGER.debug(String.format(RUNNING_COMMAND_TEMPLATE,
                mysqlProcessBuilder.command().toString()));
        Process mysqlProcess = mysqlProcessBuilder.start();
        String mysqlCommand = String.format("create user '%s'@'%s' identified by '%s';\n"
                + "flush privileges;\n",
                getStorageConf().getUsername(),
                getStorageConf().getHostname(),
                getStorageConf().getPassword());
        mysqlProcess.getOutputStream().write(mysqlCommand.getBytes());
        LOGGER.debug(String.format("sending MySQL command '%s'", mysqlCommand));
        mysqlCommand = String.format("grant all on `%s`.* to '%s'@'%s';\n"
                + "exit\n",
                getStorageConf().getDatabaseName(),
                getStorageConf().getUsername(),
                getStorageConf().getHostname());
        mysqlProcess.getOutputStream().write(mysqlCommand.getBytes());
            //note the quite strange escaping for database names in
            //MySQL (from http://stackoverflow.com/questions/925696/mysql-create-database-with-special-characters-in-the-name)
        LOGGER.debug(String.format("sending MySQL command '%s'", mysqlCommand));
        mysqlProcess.getOutputStream().flush();
            //necessary in order to avoid waiting for ever
        mysqlProcess.waitFor();
        IOUtils.copy(mysqlProcess.getInputStream(), System.out);
        IOUtils.copy(mysqlProcess.getErrorStream(), System.err);
        if(mysqlProcess.exitValue() != 0) {
            throw new StorageCreationException(String.format(COMMAND_FAILED_TEMPLATE,
                    mysqlProcessBuilder.command(), mysqlProcess.exitValue()));
        }
    }

    /**
     * The shutdown routine implementation which is wrapped inside acquisition
     * and release of {@code shutdownLock}. This must not be called without
     * acquiring the shutdown lock on EDT because it joins {@code processThread}
     * which might want to handle issues with a GUI-based IssueHandler
     * implementation.
     */
    @Override
    protected void shutdown0() {
        if(getProcess() != null) {
            //Don't kill the mysqld_safe process because that might
            //corrupt the database -> use `mysqladmin shutdown`
            ProcessBuilder mysqladminProcessBuilder = new ProcessBuilder(getStorageConf().getMysqladmin(),
                    String.format(SOCKET_TEMPLATE, SOCKET),
                    String.format("--host=%s", getStorageConf().getHostname()),
                    USER_TEMPLATE,
                    String.format("--password=%s", getStorageConf().getPassword()),
                    "shutdown");
                //don't need to specify password here because it
                //isn't set yet
            LOGGER.debug(String.format(RUNNING_COMMAND_TEMPLATE,
                    mysqladminProcessBuilder.command().toString()));
            Process mysqladminProcess;
            try {
                mysqladminProcess = mysqladminProcessBuilder.start();
                try {
                    mysqladminProcess.waitFor();
                } catch (InterruptedException ex) {
                    LOGGER.error("waiting for mysqladmin shutdown process failed, see nested exception for details", ex);
                }
                try {
                    IOUtils.copy(mysqladminProcess.getInputStream(), System.out);
                    IOUtils.copy(mysqladminProcess.getErrorStream(), System.err);
                }catch(IOException ex) {
                    LOGGER.error("writing output of mysqladmin shutdown process to stdout and stderr failed, see nested exception for details", ex);
                }
                if(mysqladminProcess.exitValue() != 0) {
                    LOGGER.error(String.format(COMMAND_FAILED_TEMPLATE,
                            mysqladminProcessBuilder.command(), mysqladminProcess.exitValue()));
                }
            } catch (IOException ex) {
                LOGGER.error("starting mysqladmin shutdown process failed, see nested exception for details", ex);
            }
            try {
                LOGGER.info("waiting for mysqld process to terminate");
                getProcess().waitFor();
                    //never waited forever in integration tests; if that ever
                    //happens, call Process.exitValue and catch
                    //IllegalThreadStateException in a loop method in
                    //PostgresqlAutoPersistenceStorage.shutdown0
            } catch (InterruptedException ex) {
                LOGGER.error("waiting for termination of mysqld process failed, see nested exception for details", ex);
            }
            assert getProcessThread() != null;
            try {
                LOGGER.info("waiting for mysqld process watch thread to terminate");
                getProcessThread().join();
                //should handle writing to stdout and stderr
            } catch (InterruptedException ex) {
                LOGGER.error("unexpected exception, see nested exception for details", ex);
            }
            try {
                AbandonedConnectionCleanupThread.shutdown();
            } catch (InterruptedException ex) {
                LOGGER.error("unexpected exception during shutdown of MySQL abandoned connection clean thread, see nested exception for details", ex);
            }
        }
        LOGGER.info(String.format("shutdown hooks in %s finished", MySQLAutoPersistenceStorage.class));
        setServerRunning(false);
    }

    @Override
    protected Map<String, String> getEntityManagerProperties() {
        Map<String, String> properties = super.getEntityManagerProperties();
        properties.put("useSSL", "false");
        return properties;
    }
}
