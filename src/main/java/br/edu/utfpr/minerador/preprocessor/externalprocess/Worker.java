package br.edu.utfpr.minerador.preprocessor.externalprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Rodrigo T. Kuroda
 */
public class Worker extends Thread {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final Process process;
    private Integer exitCode;

    private Worker(Process process) {
        this.process = process;
    }

    @Override
    public void run() {
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            log.error("Error to wait for thread." + e);
        }
    }

    public Integer getExitCode() {
        return exitCode;
    }
}
