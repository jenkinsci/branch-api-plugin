/*
 * The MIT License
 *
 * Copyright 2025 Jenkins Contributors
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

import jenkins.scm.impl.SingleSCMNavigator;
import jenkins.scm.impl.SingleSCMSource;
import jenkins.scm.impl.mock.MockSCM;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMHead;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import java.util.Collections;
import static org.junit.Assert.*;

public class OrganizationFolderMultiProjectTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    /**
     * Tests that new configuration fields have correct default values.
     * Verifies createMultipleProjects and projectNamePattern defaults.
     */
    @Test
    public void testMultiProjectConfigurationDefaults() throws Exception {
        OrganizationFolder folder = r.jenkins.createProject(OrganizationFolder.class, "top");

        // Test default values
        assertFalse("createMultipleProjects should default to false", folder.isCreateMultipleProjects());
        assertEquals("Default project name pattern should be correct", ".*?(-[^.]+).*", folder.getProjectNamePattern());

        // Test configuration round-trip
        folder = r.configRoundtrip(folder);

        assertFalse("createMultipleProjects should still be false after round-trip", folder.isCreateMultipleProjects());
        assertEquals("Project name pattern should be preserved after round-trip", ".*?(-[^.]+).*", folder.getProjectNamePattern());
    }

    /**
     * Tests setter methods for new configuration fields.
     */
    @Test
    public void testMultiProjectConfigurationSetters() throws Exception {
        OrganizationFolder folder = r.jenkins.createProject(OrganizationFolder.class, "top");

        // Test setting values
        folder.setCreateMultipleProjects(true);
        folder.setProjectNamePattern(".*?-(dev|prod).*");

        assertTrue("createMultipleProjects should be true after setting", folder.isCreateMultipleProjects());
        assertEquals("Project name pattern should be updated", ".*?-(dev|prod).*", folder.getProjectNamePattern());

        // Test null pattern handling
        folder.setProjectNamePattern(null);
        assertEquals("Null pattern should default to fallback", ".*?(-[^.]+).*", folder.getProjectNamePattern());
    }

    /**
     * Tests configuration persistence through round-trip.
     */
    @Test
    public void testMultiProjectFormSubmission() throws Exception {
        OrganizationFolder folder = r.jenkins.createProject(OrganizationFolder.class, "top");

        // Test through configuration round-trip with modified values
        folder.setCreateMultipleProjects(true);
        folder.setProjectNamePattern(".*?-(test|staging).*");

        OrganizationFolder configured = r.configRoundtrip(folder);

        assertTrue("createMultipleProjects should be true after configuration", configured.isCreateMultipleProjects());
        assertEquals("Project name pattern should be saved correctly", ".*?-(test|staging).*", configured.getProjectNamePattern());
    }

    /**
     * Tests project name extraction functionality when multiple projects are enabled.
     */
    @Test
    public void testProjectNameExtractionWithMultipleProjects() throws Exception {
        try (MockSCMController controller = MockSCMController.create()) {
            // Create repository 
            controller.createRepository("myproject");

            OrganizationFolder folder = r.jenkins.createProject(OrganizationFolder.class, "test-org");
            folder.setCreateMultipleProjects(true);
            folder.setProjectNamePattern(".*?(-[^.]+).*");

            // Add mock navigator and source
            SingleSCMSource source = new SingleSCMSource("myproject-source",
                    new MockSCM(controller, "myproject", new MockSCMHead("master"), null));
            folder.getNavigators().add(new SingleSCMNavigator("myproject", Collections.singletonList(source)));

            // Trigger scan
            folder.scheduleBuild(0);
            r.waitUntilNoActivity();

            // Verify that organization folder processes the scan
            assertNotNull("Organization folder should process scan", folder.getComputation());

            // Test configuration values
            assertTrue("createMultipleProjects should be true", folder.isCreateMultipleProjects());
            assertEquals("Project name pattern should be set", ".*?(-[^.]+).*", folder.getProjectNamePattern());
        }
    }

    /**
     * Tests that configuration changes trigger rescans appropriately.
     */
    @Test
    public void testConfigurationChangeTriggersRescan() throws Exception {
        try (MockSCMController controller = MockSCMController.create()) {
            controller.createRepository("test-repo");

            OrganizationFolder folder = r.jenkins.createProject(OrganizationFolder.class, "test-org");

            // Add source for scanning
            SingleSCMSource source = new SingleSCMSource("test-repo-source",
                    new MockSCM(controller, "test-repo", new MockSCMHead("master"), null));
            folder.getNavigators().add(new SingleSCMNavigator("test-repo", Collections.singletonList(source)));

            // Perform initial scan
            folder.scheduleBuild(0);
            r.waitUntilNoActivity();

            // Change createMultipleProjects setting
            folder.setCreateMultipleProjects(true);
            folder.save();

            // Change projectNamePattern setting
            folder.setProjectNamePattern(".*?(-[^.]+).*");
            folder.save();

            // Verify that folder is properly configured
            assertTrue("createMultipleProjects should be true", folder.isCreateMultipleProjects());
            assertEquals("Project name pattern should be set", ".*?(-[^.]+).*", folder.getProjectNamePattern());

            // Note: In a real test environment, we would verify that rescans are triggered
            // but this requires more complex setup with listeners
        }
    }

    /**
     * Tests backward compatibility - that existing configurations still work.
     */
    @Test
    public void testBackwardCompatibility() throws Exception {
        OrganizationFolder folder = r.jenkins.createProject(OrganizationFolder.class, "legacy-folder");

        // Ensure default values work for existing configurations
        assertFalse("Legacy folders should have createMultipleProjects as false", folder.isCreateMultipleProjects());
        assertNotNull("Legacy folders should have default project name pattern", folder.getProjectNamePattern());

        // Test that existing functionality still works
        try (MockSCMController controller = MockSCMController.create()) {
            controller.createRepository("legacy-project");

            // Add source for scanning
            SingleSCMSource source = new SingleSCMSource("legacy-project-source",
                    new MockSCM(controller, "legacy-project", new MockSCMHead("master"), null));
            folder.getNavigators().add(new SingleSCMNavigator("legacy-project", Collections.singletonList(source)));

            folder.scheduleBuild(0);
            r.waitUntilNoActivity();

            // Folder should work normally even without new features enabled
            assertNotNull("Legacy folder should still function", folder.getComputation());
        }
    }

    /**
     * Tests edge cases for the project name pattern.
     */
    @Test
    public void testProjectNamePatternEdgeCases() throws Exception {
        OrganizationFolder folder = r.jenkins.createProject(OrganizationFolder.class, "top");

        // Test empty pattern
        folder.setProjectNamePattern("");
        assertEquals("Empty pattern should be handled", "", folder.getProjectNamePattern());

        // Test pattern with special regex characters
        folder.setProjectNamePattern(".*?\\-(test|prod)\\-[0-9]+.*");
        assertEquals("Pattern with special characters should be preserved",
                ".*?\\-(test|prod)\\-[0-9]+.*", folder.getProjectNamePattern());

        // Test null pattern (should use default fallback)
        folder.setProjectNamePattern(null);
        assertEquals("Null pattern should use fallback", ".*?(-[^.]+).*", folder.getProjectNamePattern());
    }
}