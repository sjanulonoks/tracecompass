/*******************************************************************************
 * Copyright (c) 2015, 2017 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.statistics;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.text.Format;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.statistics.AbstractSegmentStatisticsAnalysis;
import org.eclipse.tracecompass.analysis.timing.core.statistics.IStatistics;
import org.eclipse.tracecompass.analysis.timing.core.statistics.Statistics;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.SubSecondTimeWithUnitFormat;
import org.eclipse.tracecompass.internal.analysis.timing.ui.Activator;
import org.eclipse.tracecompass.internal.analysis.timing.ui.views.segmentstore.statistics.Messages;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSelectionRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfWindowRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractTmfTreeViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;

import com.google.common.collect.ImmutableList;

/**
 * An abstract tree viewer implementation for displaying segment store
 * statistics
 *
 * @author Bernd Hufmann
 * @author Geneviève Bastien
 * @since 1.3
 */
public abstract class AbstractSegmentsStatisticsViewer extends AbstractTmfTreeViewer {

    private static final Format FORMATTER = new SubSecondTimeWithUnitFormat();

    private @Nullable TmfAbstractAnalysisModule fModule;
    private MenuManager fTablePopupMenuManager;

    private static final String[] COLUMN_NAMES = new String[] {
            checkNotNull(Messages.SegmentStoreStatistics_LevelLabel),
            checkNotNull(Messages.SegmentStoreStatistics_Statistics_MinLabel),
            checkNotNull(Messages.SegmentStoreStatistics_MaxLabel),
            checkNotNull(Messages.SegmentStoreStatistics_AverageLabel),
            checkNotNull(Messages.SegmentStoreStatisticsViewer_StandardDeviation),
            checkNotNull(Messages.SegmentStoreStatisticsViewer_Count),
            checkNotNull(Messages.SegmentStoreStatisticsViewer_Total)
    };

    /**
     * Constructor
     *
     * @param parent
     *            the parent composite
     */
    public AbstractSegmentsStatisticsViewer(Composite parent) {
        super(parent, false);
        setLabelProvider(new SegmentStoreStatisticsLabelProvider());
        fTablePopupMenuManager = new MenuManager();
        fTablePopupMenuManager.setRemoveAllWhenShown(true);
        fTablePopupMenuManager.addMenuListener(manager -> {
            TreeViewer viewer = getTreeViewer();
            ISelection selection = viewer.getSelection();
            if (selection instanceof IStructuredSelection) {
                IStructuredSelection sel = (IStructuredSelection) selection;
                if (manager != null) {
                    appendToTablePopupMenu(manager, sel);
                }
            }
        });
        Menu tablePopup = fTablePopupMenuManager.createContextMenu(getTreeViewer().getTree());
        Tree tree = getTreeViewer().getTree();
        tree.setMenu(tablePopup);
        tree.addDisposeListener(e -> {
            if (fModule != null) {
                fModule.dispose();
            }
        });
    }

    /** Provides label for the Segment Store tree viewer cells */
    protected static class SegmentStoreStatisticsLabelProvider extends TreeLabelProvider {

        @Override
        public String getColumnText(@Nullable Object element, int columnIndex) {
            String value = ""; //$NON-NLS-1$
            if (element instanceof HiddenTreeViewerEntry) {
                if (columnIndex == 0) {
                    value = ((HiddenTreeViewerEntry) element).getName();
                }
            } else if (element instanceof SegmentStoreStatisticsEntry) {
                SegmentStoreStatisticsEntry entry = (SegmentStoreStatisticsEntry) element;
                if (columnIndex == 0) {
                    return String.valueOf(entry.getName());
                }
                if (entry.getEntry().getNbElements() > 0) {
                    if (columnIndex == 1) {
                        value = toFormattedString(entry.getEntry().getMin());
                    } else if (columnIndex == 2) {
                        value = String.valueOf(toFormattedString(entry.getEntry().getMax()));
                    } else if (columnIndex == 3) {
                        value = String.valueOf(toFormattedString(entry.getEntry().getMean()));
                    } else if (columnIndex == 4) {
                        value = String.valueOf(toFormattedString(entry.getEntry().getStdDev()));
                    } else if (columnIndex == 5) {
                        value = String.valueOf(entry.getEntry().getNbElements());
                    } else if (columnIndex == 6) {
                        value = String.valueOf(toFormattedString(entry.getEntry().getTotal()));
                    }
                }
            }
            return checkNotNull(value);
        }
    }

    /**
     * Creates the statistics analysis module
     *
     * @return the statistics analysis module
     */
    @Nullable
    protected abstract TmfAbstractAnalysisModule createStatisticsAnalysiModule();

    /**
     * Gets the statistics analysis module
     *
     * @return the statistics analysis module
     */
    @Nullable
    public TmfAbstractAnalysisModule getStatisticsAnalysisModule() {
        return fModule;
    }

    private static TmfTreeColumnData createTmfTreeColumnData(@Nullable String name, Comparator<SegmentStoreStatisticsEntry> comparator){
        TmfTreeColumnData column = new TmfTreeColumnData(name);
        column.setAlignment(SWT.RIGHT);
        column.setComparator(new ViewerComparator() {
            @Override
            public int compare(@Nullable Viewer viewer, @Nullable Object e1, @Nullable Object e2) {
                if ((e1 == null) || (e2 == null)) {
                    return 0;
                }

                SegmentStoreStatisticsEntry n1 = (SegmentStoreStatisticsEntry) e1;
                SegmentStoreStatisticsEntry n2 = (SegmentStoreStatisticsEntry) e2;

                return comparator.compare(n1, n2);

            }
        });
        return column;
    }

    @Override
    protected ITmfTreeColumnDataProvider getColumnDataProvider() {
        return () -> ImmutableList.of(
                createTmfTreeColumnData(COLUMN_NAMES[0], Comparator.comparing(SegmentStoreStatisticsEntry::getName)),
                createTmfTreeColumnData(COLUMN_NAMES[1], Comparator.comparingLong(s -> s.getEntry().getMin())),
                createTmfTreeColumnData(COLUMN_NAMES[2], Comparator.comparingLong(s -> s.getEntry().getMax())),
                createTmfTreeColumnData(COLUMN_NAMES[3], Comparator.comparingDouble(s -> s.getEntry().getMean())),
                createTmfTreeColumnData(COLUMN_NAMES[4], Comparator.comparingDouble(s -> s.getEntry().getStdDev())),
                createTmfTreeColumnData(COLUMN_NAMES[5], Comparator.comparingLong(s -> s.getEntry().getNbElements())),
                createTmfTreeColumnData(COLUMN_NAMES[6], Comparator.comparingDouble(s -> s.getEntry().getTotal())),
                new TmfTreeColumnData("")); //$NON-NLS-1$
    }

    @Override
    public void initializeDataSource(ITmfTrace trace) {
        TmfAbstractAnalysisModule module = createStatisticsAnalysiModule();
        if (module == null) {
            return;
        }
        try {
            module.setTrace(trace);
            module.schedule();
            if (fModule != null) {
                fModule.dispose();
            }
            fModule = module;
        } catch (TmfAnalysisException e) {
            Activator.getDefault().logError("Error initializing statistics analysis module", e); //$NON-NLS-1$
        }
    }

    /**
     * Method to add commands to the context sensitive menu.
     *
     * @param manager
     *            the menu manager
     * @param sel
     *            the current selection
     */
    protected void appendToTablePopupMenu(IMenuManager manager, IStructuredSelection sel) {
        Object element = sel.getFirstElement();
        if ((element instanceof SegmentStoreStatisticsEntry) && !(element instanceof HiddenTreeViewerEntry)) {
            final SegmentStoreStatisticsEntry segment = (SegmentStoreStatisticsEntry) element;
            IAction gotoStartTime = new Action(Messages.SegmentStoreStatisticsViewer_GotoMinAction) {
                @Override
                public void run() {
                    ISegment minObject = segment.getEntry().getMinObject();
                    long start = minObject == null ? 0 : minObject.getStart();
                    long end = minObject == null ? 0 : minObject.getEnd();
                    broadcast(new TmfSelectionRangeUpdatedSignal(AbstractSegmentsStatisticsViewer.this, TmfTimestamp.fromNanos(start), TmfTimestamp.fromNanos(end), getTrace()));
                    updateContent(start, end, true);
                }
            };

            IAction gotoEndTime = new Action(Messages.SegmentStoreStatisticsViewer_GotoMaxAction) {
                @Override
                public void run() {
                    ISegment maxObject = segment.getEntry().getMaxObject();
                    long start = maxObject == null ? 0 : maxObject.getStart();
                    long end = maxObject == null ? 0 : maxObject.getEnd();
                    broadcast(new TmfSelectionRangeUpdatedSignal(AbstractSegmentsStatisticsViewer.this, TmfTimestamp.fromNanos(start), TmfTimestamp.fromNanos(end), getTrace()));
                    updateContent(start, end, true);
                }
            };

            manager.add(gotoStartTime);
            manager.add(gotoEndTime);
        }
    }

    /**
     * Formats a double value string
     *
     * @param value
     *            a value to format
     * @return formatted value
     */
    protected static String toFormattedString(double value) {
        // The cast to long is needed because the formatter cannot truncate the
        // number.
        String percentageString = String.format("%s", FORMATTER.format(value)); //$NON-NLS-1$
        return percentageString;
    }

    /**
     * Class for defining an entry in the statistics tree.
     */
    protected class SegmentStoreStatisticsEntry extends TmfTreeViewerEntry {

        private final IStatistics<ISegment> fEntry;

        /**
         * Constructor
         *
         * @param name
         *            name of entry
         *
         * @param entry
         *            segment store statistics object
         */
        public SegmentStoreStatisticsEntry(String name, IStatistics<ISegment> entry) {
            super(name);
            fEntry = entry;
        }

        /**
         * Gets the statistics object
         *
         * @return statistics object
         */
        public IStatistics<ISegment> getEntry() {
            return fEntry;
        }

    }

    @Override
    protected @Nullable ITmfTreeViewerEntry updateElements(ITmfTrace trace, long start, long end, boolean isSelection) {

        TmfAbstractAnalysisModule analysisModule = getStatisticsAnalysisModule();

        if (!(analysisModule instanceof AbstractSegmentStatisticsAnalysis) || !trace.equals(analysisModule.getTrace())) {
            return null;
        }

        AbstractSegmentStatisticsAnalysis module = (AbstractSegmentStatisticsAnalysis) analysisModule;

        module.waitForCompletion();

        TmfTreeViewerEntry root = new TmfTreeViewerEntry(""); //$NON-NLS-1$
        List<ITmfTreeViewerEntry> entryList = root.getChildren();

        if (isSelection) {
            setStats(start, end, entryList, module, true, new NullProgressMonitor());
        }
        setStats(start, end, entryList, module, false, new NullProgressMonitor());
        return root;
    }

    private void setStats(long start, long end, List<ITmfTreeViewerEntry> entryList, AbstractSegmentStatisticsAnalysis module, boolean isSelection, IProgressMonitor monitor) {
        String label = isSelection ? getSelectionLabel() : getTotalLabel();
        final IStatistics<ISegment> entry = isSelection ? module.getStatsForRange(start, end, monitor) : module.getStatsTotal();
        if (entry != null) {

            if (entry.getNbElements() == 0) {
                return;
            }
            TmfTreeViewerEntry child = new SegmentStoreStatisticsEntry(checkNotNull(label), entry);
            entryList.add(child);

            final Map<@NonNull String, IStatistics<ISegment>> perTypeStats = isSelection ? module.getStatsPerTypeForRange(start, end, monitor) : module.getStatsPerType();
            for (Entry<@NonNull String, IStatistics<ISegment>> statsEntry : perTypeStats.entrySet()) {
                child.addChild(new SegmentStoreStatisticsEntry(statsEntry.getKey(), statsEntry.getValue()));
            }
        }
    }

    @Override
    @TmfSignalHandler
    public void windowRangeUpdated(@Nullable TmfWindowRangeUpdatedSignal signal) {
        // Do nothing. We do not want to update the view and lose the selection
        // if the window range is updated with current selection outside of this
        // new range.
    }

    /**
     * Get the type label
     *
     * @return the label
     * @since 1.2
     */
    protected String getTypeLabel() {
        return checkNotNull(Messages.AbstractSegmentStoreStatisticsViewer_types);
    }

    /**
     * Get the total column label
     *
     * @return the totals column label
     * @since 1.2
     */
    protected String getTotalLabel() {
        return checkNotNull(Messages.AbstractSegmentStoreStatisticsViewer_total);
    }

    /**
     * Get the selection column label
     *
     * @return The selection column label
     * @since 1.2
     */
    protected String getSelectionLabel() {
        return checkNotNull(Messages.AbstractSegmentStoreStatisticsViewer_selection);
    }

    /**
     * Class to define a level in the tree that doesn't have any values.
     */
    protected class HiddenTreeViewerEntry extends SegmentStoreStatisticsEntry {
        /**
         * Constructor
         *
         * @param name
         *            the name of the level
         */
        public HiddenTreeViewerEntry(String name) {
            super(name, new Statistics<>(s -> s.getLength()));
        }
    }

}
