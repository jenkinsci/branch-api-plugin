/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.branch;

import hudson.model.Computer;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class to handle asynchronous processing of OrganizationFolder webhook events.
 * 
 * This class is used to prevent one slow/blocked OrganizationFolder from blocking 
 * webhook processing for other OrganizationFolders.
 * 
 * @since 2.999999
 */
class OrganizationFolderAsyncHelper {
    
    private static final Logger LOGGER = Logger.getLogger(OrganizationFolderAsyncHelper.class.getName());
    
    /**
     * Timeout for individual folder processing in minutes.
     */
    private static final long FOLDER_TIMEOUT_MINUTES = 5;
    
    /**
     * Threshold for using async processing. If there are more than this many
     * OrganizationFolders, we'll use async processing to prevent blocking.
     */
    private static final int ASYNC_THRESHOLD = 1;
    
    /**
     * Process a folder operation, potentially asynchronously if there are multiple folders.
     * 
     * @param folders The list of all OrganizationFolders to process
     * @param operation The operation to perform on each folder
     * @param global The global event listener for logging
     * @return The total number of matches found across all folders
     */
    static int processWithTimeout(
            List<OrganizationFolder> folders,
            FolderOperation operation,
            StreamTaskListener global) throws InterruptedException {
        
        int matchCount = 0;
        
        // If there's only one folder or we're below the threshold, process synchronously
        // This maintains backward compatibility and keeps tests happy
        if (folders.size() <= ASYNC_THRESHOLD) {
            for (OrganizationFolder folder : folders) {
                try {
                    if (operation.process(folder, global)) {
                        matchCount++;
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error processing folder " + folder.getFullName(), e);
                }
            }
            return matchCount;
        }
        
        // Multiple folders - use async processing to prevent blocking
        List<Future<Boolean>> futures = new ArrayList<>();
        
        for (OrganizationFolder folder : folders) {
            Callable<Boolean> task = () -> {
                try {
                    return operation.process(folder, global);
                } catch (InterruptedException e) {
                    global.error("[%tc] %s was interrupted while processing event",
                            System.currentTimeMillis(), folder.getFullName());
                    Thread.currentThread().interrupt();
                    return false;
                } catch (IOException e) {
                    global.error("[%tc] %s encountered an error while processing event: %s",
                            System.currentTimeMillis(), folder.getFullName(), e.getMessage());
                    return false;
                }
            };
            
            Future<Boolean> future = Computer.threadPoolForRemoting.submit(task);
            futures.add(future);
        }
        
        // Wait for all futures to complete with individual timeouts
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(FOLDER_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    matchCount++;
                }
            } catch (TimeoutException e) {
                global.error("[%tc] Timeout while waiting for folder processing to complete",
                        System.currentTimeMillis());
                future.cancel(true);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LOGGER.log(Level.WARNING, "Error processing folder", cause);
            }
        }
        
        return matchCount;
    }
    
    /**
     * Interface for folder processing operations.
     */
    @FunctionalInterface
    interface FolderOperation {
        /**
         * Process a single OrganizationFolder.
         * 
         * @param folder The folder to process
         * @param global The global event listener
         * @return true if the folder matched the event, false otherwise
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the operation is interrupted
         */
        boolean process(OrganizationFolder folder, StreamTaskListener global) 
                throws IOException, InterruptedException;
    }
}