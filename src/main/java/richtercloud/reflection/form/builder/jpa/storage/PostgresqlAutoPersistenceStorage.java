/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package richtercloud.reflection.form.builder.jpa.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages start of a PostgreSQL instance with system processes.
 *
 * @author richter
 */
public class PostgresqlAutoPersistenceStorage extends AbstractPersistenceStorage<PostgresqlAutoPersistenceStorageConf> {
    private static final long serialVersionUID = 1L;
    private final static Logger LOGGER = LoggerFactory.getLogger(PostgresqlAutoPersistenceStorage.class);
    private boolean serverRunning = false;

    public PostgresqlAutoPersistenceStorage(PostgresqlAutoPersistenceStorageConf storageConf, String persistenceUnitName) throws IOException, InterruptedException {
        super(storageConf, persistenceUnitName);
    }

    @Override
    public void recreateEntityManager() {
        if(!serverRunning) {
            try {
                String initdb = "/usr/lib/postgresql/9.5/bin/initdb";
                String postgres = "/usr/lib/postgresql/9.5/bin/postgres";
                String createdb = "createdb";
                boolean needToCreate = !new File(getStorageConf().getDatabaseDir()).exists();
                if(needToCreate) {
                    File passwordFile = File.createTempFile("image-storage-it-postgres", "suffix");
                    Files.write(Paths.get(passwordFile.getAbsolutePath()),
                            getStorageConf().getPassword().getBytes(),
                            StandardOpenOption.WRITE);
                    ProcessBuilder initdbProcessBuilder = new ProcessBuilder(initdb,
                            String.format("--username=%s", getStorageConf().getUsername()),
                            String.format("--pwfile=%s", passwordFile.getAbsolutePath()),
                            getStorageConf().getDatabaseDir());
                    Process initdbProcess = initdbProcessBuilder.start();
                    initdbProcess.waitFor();
                    passwordFile.delete(); //@TODO: insecure handling of password
                    //fix `FATAL:  could not create lock file "/var/run/postgresql/.s.PGSQL.5432.lock": Keine Berechtigung`
                    File postgresqlConfFile = new File(getStorageConf().getDatabaseDir(), "postgresql.conf");
                    try {
                        Files.write(Paths.get(postgresqlConfFile.getAbsolutePath()), "\nunix_socket_directories = '/tmp'\n".getBytes(), StandardOpenOption.APPEND);
                    }catch (IOException ex) {
                        LOGGER.error(String.format("unexpected exception during writing to PostgreSQL configuration file '%s', see nested exception for details", postgresqlConfFile.getAbsolutePath()), ex);
                    }
                    IOUtils.copy(initdbProcess.getInputStream(), System.out);
                    IOUtils.copy(initdbProcess.getErrorStream(), System.err);
                }else {
                    LOGGER.info(String.format("database directory '%s' exists, expecting valid PostgreSQL data directory which is used", getStorageConf().getDatabaseDir()));
                }
                ProcessBuilder postgresProcessBuilder = new ProcessBuilder(postgres,
                        "-D", getStorageConf().getDatabaseDir(),
                        "-h", getStorageConf().getHostname(),
                        "-p", String.valueOf(getStorageConf().getPort()));
                LOGGER.debug(String.format("running command '%s'", postgresProcessBuilder.command().toString()));
                Process postgresProcess = postgresProcessBuilder.start();
                Thread postgresThread = new Thread(() -> {
                    try {
                        postgresProcess.waitFor();
                        LOGGER.warn("Process 'postgres' returned. This shouldn't happen unless the JVM is shutting down.");
                        IOUtils.copy(postgresProcess.getInputStream(), System.out);
                        IOUtils.copy(postgresProcess.getErrorStream(), System.err);
                    } catch (InterruptedException | IOException ex) {
                        LOGGER.error("unexpected exception, see nested exception for details", ex);
                    }
                });
                postgresThread.start();
                if(needToCreate) {
                    //createdb @TODO: check whether database exists rather than
                    //just assume that it needs to be created if the directory
                    //doesn't exist
                    boolean success = false;
                    while(!success) {
                        ProcessBuilder createdbProcessBuilder = new ProcessBuilder(createdb,
                                String.format("--host=%s", getStorageConf().getHostname()),
                                String.format("--username=%s", getStorageConf().getUsername()),
                                getStorageConf().getDatabaseName());
                        LOGGER.debug(String.format("running command '%s'", createdbProcessBuilder.command().toString()));
                        Process createdbProcess = createdbProcessBuilder.start();
                        createdbProcess.waitFor();
                        IOUtils.copy(createdbProcess.getInputStream(), System.out);
                        IOUtils.copy(createdbProcess.getErrorStream(), System.err);
                        if(createdbProcess.exitValue() == 0) {
                            LOGGER.debug("createdb succeeded");
                            success = true;
                        }else {
                            LOGGER.debug("createdb failed (server might not be up yet, trying again in 1 s");
                            Thread.sleep(1000);
                        }
                    }
                }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    postgresProcess.destroy();
                    try {
                        LOGGER.info("waiting for postgres process to terminate");
                        postgresProcess.waitFor();
                    } catch (InterruptedException ex) {
                        LOGGER.error("waiting for termination of postgres process failed, see nested exception for details", ex);
                    }
                    try {
                        LOGGER.info("waiting for postgres process watch thread to terminate");
                        postgresThread.join();
                        //should handle writing to stdout and stderr
                    } catch (InterruptedException ex) {
                        LOGGER.error("unexpected exception, see nested exception for details", ex);
                    }
                }));
                serverRunning = true;
            }catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
        super.recreateEntityManager();
    }
}
