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
package org.apache.ivyde.eclipse.ui.views;

import org.apache.ivyde.eclipse.IvyPlugin;
import org.apache.ivyde.eclipse.cpcontainer.IvyClasspathContainer;
import org.apache.ivyde.eclipse.revdepexplorer.IvyUtil;
import org.apache.ivyde.eclipse.revdepexplorer.MultiRevisionDependencyDescriptor;
import org.apache.ivyde.eclipse.revdepexplorer.SyncIvyFilesJob;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

/**
 * This is a view to manage synchronizing ivy files in a workspace
 */
public class ReverseDependencyExplorerView extends ViewPart {
    private static TreeViewer viewer;

    private static MultiRevisionDependencyDescriptor[] dependencies;

    private static Display display;

    private static IProject[] selectedProjects;

    private static final String NEW_REVISION = "New Revision";

    private static final String[] PROPS = new String[] {"Organization", "Module", "Revision",
            NEW_REVISION};

    /**
     * This is a callback that will allow us to create the viewer and initialize it.
     */
    public void createPartControl(Composite parent) {
        display = parent.getDisplay();
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();

        Action syncAction = new Action() {
            public void run() {
                if (MessageDialog.openConfirm(Display.getCurrent().getActiveShell(),
                    "Fix dependencies",
                    "Alter dependencies?\n\nAnything marked in green will be synchronized.")) {
                    Job job = new SyncIvyFilesJob(dependencies);
                    job.addJobChangeListener(new JobChangeAdapter() {
                        public void done(IJobChangeEvent arg0) {
                            refresh(true);
                        }
                    });
                    job.schedule();
                }
            }
        };
        syncAction.setToolTipText("Synchronize ivy dependencies");
        syncAction.setImageDescriptor(IvyPlugin.getImageDescriptor("icons/synced.gif"));

        Action refreshAction = new Action() {
            public void run() {
                refresh(true);
            }
        };
        refreshAction.setToolTipText("Refresh");
        refreshAction.setImageDescriptor(IvyPlugin.getImageDescriptor("icons/refresh.gif"));

        Action refreshAllAction = new Action() {
            public void run() {
                ReverseDependencyExplorerView.setSelectedProjects(null);
                refresh(true);
            }
        };
        refreshAllAction.setToolTipText("Show all projects in workspace");
        refreshAllAction.setImageDescriptor(sharedImages
                .getImageDescriptor(ISharedImages.IMG_TOOL_UP));

        IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();
        toolbar.add(syncAction);
        toolbar.add(refreshAction);
        toolbar.add(refreshAllAction);

        newTreeViewer(composite);
        refresh(true);
    }

    private void newTreeViewer(Composite composite) {
        viewer = new TreeViewer(composite, SWT.FULL_SELECTION);
        IvyRevisionProvider ivyRevisionProvider = new IvyRevisionProvider();

        viewer.setContentProvider(ivyRevisionProvider);
        viewer.setLabelProvider(ivyRevisionProvider);
        viewer.setColumnProperties(PROPS);

        Tree tree = viewer.getTree();
        tree.setLayoutData(new GridData(GridData.FILL_BOTH));

        TableLayout layout = new TableLayout();
        layout.addColumnData(new ColumnWeightData(50, 75, true));
        layout.addColumnData(new ColumnWeightData(50, 75, true));
        layout.addColumnData(new ColumnWeightData(25, 75, true));
        layout.addColumnData(new ColumnWeightData(50, 75, true));
        tree.setLayout(layout);

        new TreeColumn(tree, SWT.LEFT).setText("Organization");
        new TreeColumn(tree, SWT.LEFT).setText("Module");
        new TreeColumn(tree, SWT.LEFT).setText("Revision");
        new TreeColumn(tree, SWT.LEFT).setText("New Revision");

        for (int i = 0, n = tree.getColumnCount(); i < n; i++) {
            tree.getColumn(i).pack();
        }

        tree.setHeaderVisible(true);
        tree.setLinesVisible(false);

        CellEditor[] editors = new CellEditor[PROPS.length];
        editors[0] = new TextCellEditor(tree);
        editors[1] = new TextCellEditor(tree);
        editors[2] = new TextCellEditor(tree);
        editors[3] = new TextCellEditor(tree);

        viewer.setCellModifier(new CellModifier());
        viewer.setCellEditors(editors);
    }

    public static void refresh(final boolean reloadData) {
        display.syncExec(new Runnable() {
            public void run() {
                if (reloadData) {
                    if (selectedProjects == null) {
                        dependencies = IvyUtil.getAllDependencyDescriptorsInWorkspace();
                    } else {
                        dependencies = IvyUtil.getDependencyDescriptorsByProjects(selectedProjects);
                    }
                    viewer.setInput(dependencies);
                }

                viewer.refresh();

                TreeItem[] items = viewer.getTree().getItems();

                for (int i = 0; i < items.length; i++) {
                    TreeItem item = items[i];
                    MultiRevisionDependencyDescriptor multiRevisionDescriptor = (MultiRevisionDependencyDescriptor) item
                            .getData();

                    if (multiRevisionDescriptor.hasMultipleRevisons()
                            && !multiRevisionDescriptor.hasNewRevision()) {
                        item.setForeground(display.getSystemColor(SWT.COLOR_RED));
                    } else if (multiRevisionDescriptor.hasNewRevision()) {
                        item.setForeground(new Color(Display.getDefault(), new RGB(50, 150, 50)));
                    } else {
                        item.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
                    }
                }
            }
        });
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    class IvyRevisionProvider extends LabelProvider implements ITableLabelProvider,
            ITreeContentProvider {

        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            // nothing to do
        }

        public Object[] getElements(Object parent) {
            return dependencies;
        }

        public String getColumnText(Object obj, int index) {
            if (obj instanceof MultiRevisionDependencyDescriptor) {
                MultiRevisionDependencyDescriptor dependencyDescriptor = (MultiRevisionDependencyDescriptor) obj;

                switch (index) {
                    case 0:
                        return dependencyDescriptor.getOrganization();
                    case 1:
                        return dependencyDescriptor.getModule();
                    case 2:
                        return toRevisionList(dependencyDescriptor.getRevisions());
                    case 3:
                        return dependencyDescriptor.getNewRevision();
                    default:
                        break;
                }
            } else if (obj instanceof ClasspathContainerDependencyDescriptorComposite) {
                ClasspathContainerDependencyDescriptorComposite containerDescriptorComposite = (ClasspathContainerDependencyDescriptorComposite) obj;
                switch (index) {
                    case 0:
                        return containerDescriptorComposite.getIvyClasspathContainer()
                                .getDescription()
                                + " in \""
                                + containerDescriptorComposite.getIvyClasspathContainer().getConf()
                                        .getJavaProject().getProject().getName() + "\"";
                    case 2:
                        return toRevisionList(containerDescriptorComposite.getRevisions());
                    default:
                        break;
                }

                return null;
            }

            return null;
        }

        private String toRevisionList(String[] revisions) {
            StringBuffer buffer = new StringBuffer();

            for (int i = 0; i < revisions.length; i++) {
                buffer.append(revisions[i]);

                if (i + 1 < revisions.length) {
                    buffer.append(", ");
                }
            }

            return buffer.toString();
        }

        public Image getColumnImage(Object obj, int index) {
            if (index == 0) {
                return getImage(obj);
            }

            return null;
        }

        public Image getImage(Object obj) {
            ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            if (obj instanceof MultiRevisionDependencyDescriptor) {
                MultiRevisionDependencyDescriptor multiRevisionDescriptor = (MultiRevisionDependencyDescriptor) obj;

                if (multiRevisionDescriptor.hasMultipleRevisons()
                        && !multiRevisionDescriptor.hasNewRevision()) {
                    return sharedImages.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
                } else {
                    return IvyPlugin.getImageDescriptor("icons/synced.gif").createImage();
                }
            } else if (obj instanceof ClasspathContainerDependencyDescriptorComposite) {
                return JavaUI.getSharedImages().getImage(
                    org.eclipse.jdt.ui.ISharedImages.IMG_OBJS_LIBRARY);
            }

            return null;
        }

        public Object[] getChildren(Object parent) {
            if (parent instanceof MultiRevisionDependencyDescriptor) {
                MultiRevisionDependencyDescriptor multiRevisionDescriptor = (MultiRevisionDependencyDescriptor) parent;
                IvyClasspathContainer[] containers = multiRevisionDescriptor
                        .getIvyClasspathContainers();

                Object[] wrappedProjects = new Object[containers.length];
                for (int i = 0; i < containers.length; i++) {
                    wrappedProjects[i] = new ClasspathContainerDependencyDescriptorComposite(
                            containers[i], multiRevisionDescriptor);
                }

                return wrappedProjects;
            }

            return new Object[0];
        }

        public Object getParent(Object parent) {
            return null;
        }

        public boolean hasChildren(Object parent) {
            if (parent instanceof MultiRevisionDependencyDescriptor) {
                MultiRevisionDependencyDescriptor multiRevisionDescriptor = (MultiRevisionDependencyDescriptor) parent;

                if (multiRevisionDescriptor.getIvyClasspathContainers().length > 0) {
                    return true;
                }
            }

            return false;
        }
    }

    class ClasspathContainerDependencyDescriptorComposite {
        private IvyClasspathContainer container;

        private MultiRevisionDependencyDescriptor multiRevisionDescriptor;

        public ClasspathContainerDependencyDescriptorComposite(IvyClasspathContainer container,
                MultiRevisionDependencyDescriptor multiRevisionDescriptor) {
            this.container = container;
            this.multiRevisionDescriptor = multiRevisionDescriptor;
        }

        /**
         * @return revisions for a container
         */
        public String[] getRevisions() {
            return multiRevisionDescriptor.getRevisions(container);
        }

        public IvyClasspathContainer getIvyClasspathContainer() {
            return container;
        }

        public MultiRevisionDependencyDescriptor getMultiRevisionDescriptor() {
            return multiRevisionDescriptor;
        }
    }

    class CellModifier implements ICellModifier {

        public boolean canModify(Object element, String property) {
            if (property.equals(NEW_REVISION)) {
                return true;
            }
            return false;
        }

        public Object getValue(Object element, String property) {
            if (property.equals(NEW_REVISION)) {
                if (element instanceof MultiRevisionDependencyDescriptor) {
                    MultiRevisionDependencyDescriptor dependencyDescriptor = (MultiRevisionDependencyDescriptor) element;
                    String revision = dependencyDescriptor.getNewRevision();

                    if (revision == null) {
                        return "";
                    } else {
                        return revision;
                    }
                }
            }

            return null;
        }

        public void modify(Object element, String property, Object value) {
            if (element instanceof Item) {
                element = ((Item) element).getData();
            }

            if (element instanceof MultiRevisionDependencyDescriptor
                    && property.equals(NEW_REVISION)) {
                MultiRevisionDependencyDescriptor multiRevisionDescriptor = (MultiRevisionDependencyDescriptor) element;
                multiRevisionDescriptor.setNewRevision((String) value);

                refresh(false);
            }
        }
    }

    public static void setSelectedProjects(IProject[] projects) {
        selectedProjects = projects;
    }
}