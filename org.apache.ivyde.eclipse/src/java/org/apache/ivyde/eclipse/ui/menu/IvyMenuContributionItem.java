/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivyde.eclipse.ui.menu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivyde.eclipse.IvyNature;
import org.apache.ivyde.eclipse.IvyPlugin;
import org.apache.ivyde.eclipse.cpcontainer.IvyClasspathContainer;
import org.apache.ivyde.eclipse.cpcontainer.IvyClasspathUtil;
import org.apache.ivyde.eclipse.handlers.OpenIvyFileHandler;
import org.apache.ivyde.eclipse.handlers.RefreshHandler;
import org.apache.ivyde.eclipse.handlers.ReloadSettingsHandler;
import org.apache.ivyde.eclipse.handlers.RemoveIvyNatureHandler;
import org.apache.ivyde.eclipse.handlers.ResolveHandler;
import org.apache.ivyde.eclipse.handlers.ViewReverseDependenciesHandler;
import org.apache.ivyde.eclipse.retrieve.RetrieveSetupManager;
import org.apache.ivyde.eclipse.retrieve.StandaloneRetrieveSetup;
import org.apache.ivyde.eclipse.ui.menu.CleanCacheAction.Cleanable;
import org.apache.ivyde.eclipse.ui.menu.CleanCacheAction.RepositoryCacheCleanable;
import org.apache.ivyde.eclipse.ui.menu.CleanCacheAction.ResolutionCacheCleanable;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.services.IServiceLocator;

public class IvyMenuContributionItem extends CompoundContributionItem implements
        IWorkbenchContribution {

    private IServiceLocator serviceLocator;

    public void initialize(IServiceLocator locator) {
        this.serviceLocator = locator;
    }

    protected IContributionItem[] getContributionItems() {
        ISelectionService selectionService = (ISelectionService) serviceLocator
                .getService(ISelectionService.class);
        if (selectionService == null) {
            return new IContributionItem[0];
        }
        ISelection selection = selectionService.getSelection();
        if (selection == null || !(selection instanceof IStructuredSelection)) {
            return new IContributionItem[0];
        }

        Map/* <IProject, Set<IvyClasspathContainer>> */containers = new HashMap();

        Map/* <IProject, Set<StandaloneRetrieveSetup>> */retrieveSetups = new HashMap();

        // this give info about if the selection is only based of classpath containers
        boolean onlyContainers = true;

        int totalSelected = 0;

        Iterator it = ((IStructuredSelection) selection).iterator();
        while (it.hasNext()) {
            totalSelected++;
            Object element = it.next();
            boolean projectCollected = collectProject(containers, retrieveSetups, element);
            if (projectCollected) {
                onlyContainers = false;
            } else {
                IWorkingSet workingSet = (IWorkingSet) IvyPlugin.adapt(element, IWorkingSet.class);
                if (workingSet != null) {
                    onlyContainers = false;
                    IAdaptable[] elements = workingSet.getElements();
                    for (int i = 0; i < elements.length; i++) {
                        collectProject(containers, retrieveSetups, elements[i]);
                    }
                } else if (element instanceof ClassPathContainer) {
                    collectContainer(containers, (ClassPathContainer) element);
                }
            }
        }

        List/* <IContributionItem> */items;
        MenuManager menuManager;
        if (onlyContainers) {
            // we we have only containers, no need to have a root menu entry
            menuManager = null;
            items = new ArrayList();
        } else {
            menuManager = new MenuManager("Ivy", IvyPlugin
                    .getImageDescriptor("icons/logo16x16.gif"), "org.apache.ivyde.eclipse.menu");
            items = Collections.singletonList(menuManager);
        }

        // add resolve, refresh, reload settings
        if (!containers.isEmpty()) {
            addCommand(menuManager, items, ResolveHandler.COMMAND_ID);
            addCommand(menuManager, items, RefreshHandler.COMMAND_ID);
            addCommand(menuManager, items, ReloadSettingsHandler.COMMAND_ID);
            fillMenu(menuManager, items, new IvyMenuSeparator());
        }

        // add retrieve
        if (!retrieveSetups.isEmpty()) {
            boolean oneProject = retrieveSetups.size() == 1 && totalSelected == 1;
            Iterator itProject = retrieveSetups.entrySet().iterator();
            while (itProject.hasNext()) {
                Entry entry = (Entry) itProject.next();
                IProject project = (IProject) entry.getKey();
                Iterator itSetup = ((Set) entry.getValue()).iterator();
                while (itSetup.hasNext()) {
                    StandaloneRetrieveSetup retrieveSetup = (StandaloneRetrieveSetup) itSetup
                            .next();
                    RetrieveAction action = new RetrieveAction(retrieveSetup);
                    action.setText("Retrieve '" + retrieveSetup.getName()
                            + (oneProject ? "'" : "' of " + project.getName()));
                    fillMenu(menuManager, items, new ActionContributionItem(action));
                }
            }
            fillMenu(menuManager, items, new IvyMenuSeparator());
        }

        // add open file
        if (!containers.isEmpty()) {
            addCommand(menuManager, items, OpenIvyFileHandler.COMMAND_ID);
            fillMenu(menuManager, items, new IvyMenuSeparator());
        }

        // add clean cache
        if (!containers.isEmpty()) {
            if (totalSelected == 1 && containers.size() == 1
                    && ((Set) containers.values().iterator().next()).size() == 1) {
                // only one container
                IvyClasspathContainer ivycp = (IvyClasspathContainer) ((Set) containers.values()
                        .iterator().next()).iterator().next();
                Ivy ivy = ivycp.getState().getCachedIvy();
                if (ivy != null) {
                    addCleanableForSingleContainer(menuManager, items, ivy);
                }
            } else {
                addCleanableForManyContainers(menuManager, items, containers.values());
            }
            fillMenu(menuManager, items, new IvyMenuSeparator());
        }

        // add reverse dependency explorer
        if (!containers.isEmpty()) {
            addCommand(menuManager, items, ViewReverseDependenciesHandler.COMMAND_ID);
            fillMenu(menuManager, items, new IvyMenuSeparator());
        }

        // add remove ivy nature
        addCommand(menuManager, items, RemoveIvyNatureHandler.COMMAND_ID);

        return (IContributionItem[]) items.toArray(new IContributionItem[items.size()]);
    }

    private void addCommand(MenuManager menuManager, List/* <IContributionItem> */items,
            String commandId) {
        CommandContributionItemParameter parm = new CommandContributionItemParameter(
                serviceLocator, null, commandId, CommandContributionItem.STYLE_PUSH);
        fillMenu(menuManager, items, new CommandContributionItem(parm));
    }

    private void fillMenu(MenuManager menuManager, List/* <IContributionItem> */items,
            IContributionItem item) {
        if (menuManager != null) {
            menuManager.add(item);
        } else {
            items.add(item);
        }
    }

    private boolean collectProject(Map/* <IProject, Set<IvyClasspathContainer>> */containers,
            Map/* <IProject, Set<StandaloneRetrieveSetup>> */retrieveSetups, Object element) {
        IProject project = (IProject) IvyPlugin.adapt(element, IProject.class);
        if (project != null && project.isOpen() && IvyNature.hasNature(project)) {
            doCollectProject(containers, retrieveSetups, project);
            return true;
        }
        return false;
    }

    private void doCollectProject(Map/* <IProject, Set<IvyClasspathContainer>> */containers,
            Map/* <IProject, Set<StandaloneRetrieveSetup>> */retrieveSetups, IProject project) {
        List ivycps = IvyClasspathUtil.getIvyClasspathContainers(project);
        if (!ivycps.isEmpty()) {
            containers.put(project, new HashSet(ivycps));
        }
        RetrieveSetupManager manager = IvyPlugin.getDefault().getRetrieveSetupManager();
        List setupList;
        try {
            setupList = manager.getSetup(project);
        } catch (IOException e) {
            IvyPlugin.logWarn("Unable to get the retrieve setup for project " + project.getName(),
                e);
            return;
        }
        if (!setupList.isEmpty()) {
            retrieveSetups.put(project, new HashSet(setupList));
        }
    }

    private boolean collectContainer(Map/* <IProject, Set<IvyClasspathContainer>> */containers,
            ClassPathContainer element) {
        IvyClasspathContainer ivycp = IvyClasspathUtil.jdt2IvyCPC(element);
        if (ivycp == null) {
            return false;
        }
        doCollectContainer(containers, ivycp);
        return true;
    }

    private void doCollectContainer(Map/* <IProject, Set<IvyClasspathContainer>> */containers,
            IvyClasspathContainer ivycp) {
        IJavaProject javaProject = ivycp.getConf().getJavaProject();
        if (javaProject == null) {
            return;
        }
        Set/* <IvyClasspathContainer> */cplist = (Set) containers.get(javaProject.getProject());
        if (cplist == null) {
            cplist = new HashSet();
            containers.put(javaProject.getProject(), cplist);
        }
        cplist.add(ivycp);
    }

    private void addCleanableForSingleContainer(MenuManager menuManager,
            List/* <IContributionItem> */items, Ivy ivy) {
        List/* <Cleanable> */allCleanables = new ArrayList();
        List/* <Cleanable> */repositoryCleanables = new ArrayList();
        List/* <Cleanable> */resolutionCleanables = new ArrayList();

        addResolutionCleanable(allCleanables, ivy);
        addResolutionCleanable(resolutionCleanables, ivy);

        addRepositoryCleanable(allCleanables, ivy);
        addRepositoryCleanable(repositoryCleanables, ivy);

        addCleanable(menuManager, items, "Clean all caches", allCleanables);
        addCleanable(menuManager, items, "Clean the resolution cache", resolutionCleanables);
        addCleanable(menuManager, items, "Clean every repository cache", repositoryCleanables);
        Iterator itCleanble = resolutionCleanables.iterator();
        while (itCleanble.hasNext()) {
            Cleanable cleanable = (Cleanable) itCleanble.next();
            addCleanable(menuManager, items, "Clean the cache '" + cleanable.getName() + "'",
                Collections.singletonList(cleanable));
        }
    }

    private void addCleanableForManyContainers(MenuManager menuManager,
            List/* <IContributionItem> */items, Collection/*
                                                           * <Set<IvyClasspathContainer >>
                                                           */containerSets) {
        List/* <Cleanable> */allCleanables = new ArrayList();
        List/* <Cleanable> */repositoryCleanables = new ArrayList();
        List/* <Cleanable> */resolutionCleanables = new ArrayList();

        Iterator itSet = containerSets.iterator();
        while (itSet.hasNext()) {
            Set set = (Set) itSet.next();
            Iterator itContainer = set.iterator();
            while (itContainer.hasNext()) {
                IvyClasspathContainer ivycp = (IvyClasspathContainer) itContainer.next();
                Ivy ivy = ivycp.getState().getCachedIvy();
                if (ivy != null) {
                    addResolutionCleanable(allCleanables, ivy);
                    addResolutionCleanable(resolutionCleanables, ivy);

                    addRepositoryCleanable(allCleanables, ivy);
                    addRepositoryCleanable(repositoryCleanables, ivy);
                }
            }
        }
        addCleanable(menuManager, items, "Clean all caches", allCleanables);
        addCleanable(menuManager, items, "Clean every resolution cache", resolutionCleanables);
        addCleanable(menuManager, items, "Clean every repository cache", repositoryCleanables);
    }

    private void addResolutionCleanable(List/* <Cleanable> */cleanables, Ivy ivy) {
        ResolutionCacheManager manager = ivy.getSettings().getResolutionCacheManager();
        cleanables.add(new ResolutionCacheCleanable(manager));
    }

    private void addRepositoryCleanable(List/* <Cleanable> */cleanables, Ivy ivy) {
        RepositoryCacheManager[] managers = ivy.getSettings().getRepositoryCacheManagers();
        for (int i = 0; i < managers.length; i++) {
            cleanables.add(new RepositoryCacheCleanable(managers[i]));
        }
    }

    public void addCleanable(MenuManager menuManager, List/* <IContributionItem> */items,
            String name, List/* <Cleanable> */cleanables) {
        CleanCacheAction action = new CleanCacheAction(name, cleanables);
        action.setText(name);
        fillMenu(menuManager, items, new ActionContributionItem(action));
    }

}
