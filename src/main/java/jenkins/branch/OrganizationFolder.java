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

import antlr.ANTLRException;
import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.util.DescribableList;
import hudson.util.PersistedList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** {@inheritDoc} */
    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        for (MultiBranchProjectFactoryDescriptor d : ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class)) {
            MultiBranchProjectFactory f = d.newInstance();
            if (f != null) {
                projectFactories.add(f);
            }
        }
        try {
            addTrigger(new PeriodicFolderTrigger("1d"));
        } catch (ANTLRException x) {
            throw new IllegalStateException(x);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
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

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        super.submit(req, rsp);
        navigators.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(SCMNavigatorDescriptor.class), "navigators");
        projectFactories.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), "projectFactories");
    }

    @Override
    protected void computeChildren(final ChildObserver<MultiBranchProject<?,?>> observer, final TaskListener listener) throws IOException, InterruptedException {
        for (SCMNavigator navigator : navigators) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            listener.getLogger().format("Consulting %s%n", navigator.getDescriptor().getDisplayName());
            navigator.visitSources(new SCMSourceObserver() {
                @Override
                public SCMSourceOwner getContext() {
                    return OrganizationFolder.this;
                }
                @Override
                public TaskListener getListener() {
                    return listener;
                }
                @Override
                public SCMSourceObserver.ProjectObserver observe(final String projectName) {
                    return new ProjectObserver() {
                        List<SCMSource> sources = new ArrayList<SCMSource>();
                        @Override
                        public void addSource(SCMSource source) {
                            sources.add(source);
                            source.setOwner(OrganizationFolder.this);
                        }
                        private List<BranchSource> createBranchSources() {
                            if (sources == null) {
                                throw new IllegalStateException();
                            }
                            List<BranchSource> branchSources = new ArrayList<BranchSource>();
                            for (SCMSource source : sources) {
                                // TODO do we want/need a more general BranchPropertyStrategyFactory?
                                branchSources.add(new BranchSource(source));
                            }
                            sources = null; // make sure complete gets called just once
                            return branchSources;
                        }
                        @Override
                        public void addAttribute(String key, Object value) throws IllegalArgumentException, ClassCastException {
                            throw new IllegalArgumentException();
                        }
                        @Override
                        public void complete() throws IllegalStateException, InterruptedException {
                            try {
                                MultiBranchProjectFactory factory = null;
                                Map<String, Object> attributes = Collections.<String,Object>emptyMap();
                                for (MultiBranchProjectFactory candidateFactory : projectFactories) {
                                    if (candidateFactory.recognizes(OrganizationFolder.this, projectName, sources, attributes, listener)) {
                                        factory = candidateFactory;
                                        break;
                                    }
                                }
                                if (factory == null) {
                                    return;
                                }
                                MultiBranchProject<?,?> existing = observer.shouldUpdate(projectName);
                                if (existing != null) {
                                    PersistedList<BranchSource> sourcesList = existing.getSourcesList();
                                    sourcesList.clear();
                                    sourcesList.addAll(createBranchSources());
                                    existing.setOrphanedItemStrategy(getOrphanedItemStrategy());
                                    factory.updateExistingProject(existing, attributes, listener);
                                    existing.scheduleBuild();
                                    return;
                                }
                                if (!observer.mayCreate(projectName)) {
                                    listener.getLogger().println("Ignoring duplicate child " + projectName);
                                    return;
                                }
                                MultiBranchProject<?, ?> project = factory.createNewProject(OrganizationFolder.this, projectName, sources, attributes, listener);
                                project.setOrphanedItemStrategy(getOrphanedItemStrategy());
                                project.getSourcesList().addAll(createBranchSources());
                                try {
                                    project.addTrigger(new PeriodicFolderTrigger("1d"));
                                } catch (ANTLRException x) {
                                    throw new IllegalStateException(x);
                                }
                                observer.created(project);
                                project.scheduleBuild();
                            } catch (InterruptedException x) {
                                throw x;
                            } catch (Exception x) {
                                x.printStackTrace(listener.error("Failed to create or update a subproject " + projectName));
                            }
                        }
                    };
                }
                @Override
                public void addAttribute(String key, Object value) throws IllegalArgumentException, ClassCastException {
                    throw new IllegalArgumentException();
                }
            });
        }
    }

    @Override
    public List<SCMSource> getSCMSources() {
        // Probably unused unless onSCMSourceUpdated implemented, but just in case:
        Set<SCMSource> result = new HashSet<SCMSource>();
        for (MultiBranchProject<?,?> child : getItems()) {
            result.addAll(child.getSCMSources());
        }
        return new ArrayList<SCMSource>(result);
    }

    @Override
    public SCMSource getSCMSource(String sourceId) {
        return null;
    }

    @Override
    public void onSCMSourceUpdated(SCMSource source) {
        // TODO possibly we should recheck whether this project remains valid
    }

    private transient Map<SCMSource,ThreadLocal<SCMSourceCriteria>> criteriaBySource = new WeakHashMap<SCMSource,ThreadLocal<SCMSourceCriteria>>();

    @Override
    public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        ThreadLocal<SCMSourceCriteria> criteriaTL = criteriaBySource.get(source);
        return criteriaTL != null ? criteriaTL.get() : null;
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    @Override
    public <T> T withSCMSourceCriteria(SCMSource source, SCMSourceCriteria criteria, Callable<T> body) throws Exception {
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

    /**
     * Will create an specialized view when there are no repositories or branches found, which contain a Jenkinsfile
     * or other MARKER file.
     */
    @Override
    public View getPrimaryView() {
        if (getItems().isEmpty()) {
            return getWelcomeView();
        }
        return super.getPrimaryView();
    }

    /**
     * Creates a place holder view when there's no active branch indexed.
     */
    protected View getWelcomeView() {
        return new OrganizationFolderEmptyView(this);
    }

    @Override
    public View getView(String name) {
        if (name.equals("Welcome")) {
            return getWelcomeView();
        } else {
            return super.getView(name);
        }
    }

    @Extension
    public static class DescriptorImpl extends AbstractFolderDescriptor {

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new OrganizationFolder(parent, name);
        }

    }

}
