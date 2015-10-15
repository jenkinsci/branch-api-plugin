/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.util.DescribableList;
import hudson.util.PersistedList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import javax.servlet.ServletException;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * A folder-like collection of {@link MultiBranchProject}s, one per repository.
 */
@Restricted(NoExternalUse.class) // not currently intended as an API
@SuppressWarnings({"unchecked", "rawtypes"}) // mistakes in various places
public final class OrganizationFolder extends ComputedFolder<MultiBranchProject<?,?>> implements SCMSourceOwner, MultiBranchProjectFactory.SCMSourceOwnerHack {

    private final DescribableList<SCMNavigator,SCMNavigatorDescriptor> navigators = new DescribableList<SCMNavigator, SCMNavigatorDescriptor>(this);
    private final DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor> projectFactories = new DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor>(this);

    public OrganizationFolder(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        for (MultiBranchProjectFactoryDescriptor d : ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class)) {
            MultiBranchProjectFactory f = d.newInstance();
            if (f != null) {
                projectFactories.add(f);
            }
        }
    }

    @Override public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        navigators.setOwner(this);
        projectFactories.setOwner(this);
        criteriaBySource = new WeakHashMap<SCMSource,ThreadLocal<SCMSourceCriteria>>();
    }

    public DescribableList<SCMNavigator,SCMNavigatorDescriptor> getNavigators() {
        return navigators;
    }

    public DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor> getProjectFactories() {
        return projectFactories;
    }

    @Override protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        super.submit(req, rsp);
        navigators.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(SCMNavigatorDescriptor.class), "navigators");
        projectFactories.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), "projectFactories");
    }

    @Override protected void computeChildren(final ChildObserver<MultiBranchProject<?,?>> observer, final TaskListener listener) throws IOException, InterruptedException {
        for (SCMNavigator navigator : navigators) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            listener.getLogger().format("Consulting %s%n", navigator.getDescriptor().getDisplayName());
            navigator.visitSources(new SCMSourceObserver() {
                @Override public SCMSourceOwner getContext() {
                    return OrganizationFolder.this;
                }
                @Override public TaskListener getListener() {
                    return listener;
                }
                @Override public SCMSourceObserver.ProjectObserver observe(final String projectName) {
                    return new ProjectObserver() {
                        List<SCMSource> sources = new ArrayList<SCMSource>();
                        @Override public void addSource(SCMSource source) {
                            sources.add(source);
                            source.setOwner(OrganizationFolder.this);
                        }
                        private List<BranchSource> createBranchSources() {
                            if (sources == null) {
                                throw new IllegalStateException();
                            }
                            List<BranchSource> branchSources = new ArrayList<BranchSource>();
                            for (SCMSource source : sources) {
                                // TODO we probably need to define a BranchPropertyStrategyFactory (cf. JENKINS-22242)
                                branchSources.add(new BranchSource(source, new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                            }
                            sources = null; // make sure complete gets called just once
                            return branchSources;
                        }
                        @Override public void addAttribute(String key, Object value) throws IllegalArgumentException, ClassCastException {
                            throw new IllegalArgumentException();
                        }
                        @Override public void complete() throws IllegalStateException, InterruptedException {
                            MultiBranchProject<?,?> existing = observer.shouldUpdate(projectName);
                            if (existing != null) {
                                PersistedList<BranchSource> sourcesList = existing.getSourcesList();
                                sourcesList.clear();
                                sourcesList.addAll(createBranchSources());
                                return;
                            }
                            if (!observer.mayCreate(projectName)) {
                                listener.getLogger().println("Ignoring duplicate child " + projectName);
                                return;
                            }
                            for (MultiBranchProjectFactory factory : projectFactories) {
                                MultiBranchProject<?, ?> project;
                                try {
                                    project = factory.createProject(OrganizationFolder.this, projectName, sources, Collections.<String,Object>emptyMap(), listener);
                                } catch (InterruptedException x) {
                                    throw x;
                                } catch (Exception x) {
                                    x.printStackTrace(listener.error("Failed to create a subproject " + projectName));
                                    continue;
                                }
                                if (project != null) {
                                    project.getSourcesList().addAll(createBranchSources());
                                    observer.created(project);
                                    project.scheduleBuild();
                                    break;
                                }
                            }
                        }
                    };
                }
                @Override public void addAttribute(String key, Object value) throws IllegalArgumentException, ClassCastException {
                    throw new IllegalArgumentException();
                }
            });
        }
    }

    // TODO override orphanedItems if creating OrphanedItemStrategy
    
    @Override public List<SCMSource> getSCMSources() {
        return Collections.emptyList(); // irrelevant unless onSCMSourceUpdated implemented
    }

    @Override public SCMSource getSCMSource(String sourceId) {
        return null;
    }

    @Override public void onSCMSourceUpdated(SCMSource source) {
        // possibly we should recheck whether this project remains valid
    }

    private transient Map<SCMSource,ThreadLocal<SCMSourceCriteria>> criteriaBySource = new WeakHashMap<SCMSource,ThreadLocal<SCMSourceCriteria>>();

    @Override public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        ThreadLocal<SCMSourceCriteria> criteriaTL = criteriaBySource.get(source);
        return criteriaTL != null ? criteriaTL.get() : null;
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    @Override public <T> T withSCMSourceCriteria(SCMSource source, SCMSourceCriteria criteria, Callable<T> body) throws Exception {
        ThreadLocal<SCMSourceCriteria> criteriaTL;
        synchronized (criteriaBySource) {
            criteriaTL = criteriaBySource.get(source);
            if (criteriaTL == null) {
                criteriaTL = new ThreadLocal<SCMSourceCriteria>();
                criteriaBySource.put(source, criteriaTL);
            }
        }
        SCMSourceCriteria old = criteriaTL.get();
        try {
            criteriaTL.set(criteria);
            return body.call();
        } finally {
            criteriaTL.set(old);
        }
    }
    
    @Extension public static class DescriptorImpl extends AbstractFolderDescriptor {

        @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new OrganizationFolder(parent, name);
        }

    }

}
