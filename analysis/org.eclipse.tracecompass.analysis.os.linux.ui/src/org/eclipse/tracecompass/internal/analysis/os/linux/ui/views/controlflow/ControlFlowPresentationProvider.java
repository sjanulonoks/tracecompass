/*******************************************************************************
 * Copyright (c) 2012, 2015 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Patrick Tasse - Initial API and implementation
 *   Geneviève Bastien - Move code to provide base classes for time graph view
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.model.ProcessStatus;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.Attributes;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Activator;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Messages;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.registry.LinuxStyle;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue.Type;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Presentation provider for the control flow view
 */
public class ControlFlowPresentationProvider extends TimeGraphPresentationProvider {

    private static final Map<Integer, StateItem> STATE_MAP;
    private static final List<StateItem> STATE_LIST;
    private static final StateItem[] STATE_TABLE;

    private static StateItem createState(LinuxStyle style) {
        return new StateItem(style.toMap());
    }

    static {
        ImmutableMap.Builder<Integer, StateItem> builder = new ImmutableMap.Builder<>();
        /*
         * ADD STATE MAPPING HERE
         */
        builder.put(ProcessStatus.UNKNOWN.getStateValue().unboxInt(), createState(LinuxStyle.UNKNOWN));
        builder.put(ProcessStatus.RUN.getStateValue().unboxInt(), createState(LinuxStyle.USERMODE));
        builder.put(ProcessStatus.RUN_SYTEMCALL.getStateValue().unboxInt(), createState(LinuxStyle.SYSCALL));
        builder.put(ProcessStatus.INTERRUPTED.getStateValue().unboxInt(), createState(LinuxStyle.INTERRUPTED));
        builder.put(ProcessStatus.WAIT_BLOCKED.getStateValue().unboxInt(), createState(LinuxStyle.WAIT_BLOCKED));
        builder.put(ProcessStatus.WAIT_CPU.getStateValue().unboxInt(), createState(LinuxStyle.WAIT_FOR_CPU));
        builder.put(ProcessStatus.WAIT_UNKNOWN.getStateValue().unboxInt(), createState(LinuxStyle.WAIT_UNKNOWN));
        /*
         * DO NOT MODIFY AFTER
         */
        STATE_MAP = builder.build();
        STATE_LIST = ImmutableList.copyOf(STATE_MAP.values());
        STATE_TABLE = STATE_LIST.toArray(new StateItem[STATE_LIST.size()]);
    }

    /**
     * Average width of the characters used for state labels. Is computed in the
     * first call to postDrawEvent(). Is null before that.
     */
    private Integer fAverageCharacterWidth = null;

    /**
     * Default constructor
     */
    public ControlFlowPresentationProvider() {
        super(Messages.ControlFlowView_stateTypeName);
    }

    @Override
    public StateItem[] getStateTable() {
        return STATE_TABLE;
    }

    @Override
    public int getStateTableIndex(ITimeEvent event) {
        if (event instanceof TimeEvent && ((TimeEvent) event).hasValue()) {
            int status = ((TimeEvent) event).getValue();
            return STATE_LIST.indexOf(getMatchingState(status));
        }
        if (event instanceof NullTimeEvent) {
            return INVISIBLE;
        }
        return TRANSPARENT;
    }

    @Override
    public String getEventName(ITimeEvent event) {
        if (event instanceof TimeEvent) {
            TimeEvent ev = (TimeEvent) event;
            if (ev.hasValue()) {
                return getMatchingState(ev.getValue()).getStateString();
            }
        }
        return Messages.ControlFlowView_multipleStates;
    }

    private static StateItem getMatchingState(int status) {
        return STATE_MAP.getOrDefault(status, STATE_MAP.get(ProcessStatus.WAIT_UNKNOWN.getStateValue().unboxInt()));
    }

    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event) {
        Map<String, String> retMap = new LinkedHashMap<>();
        if (!(event instanceof TimeEvent) || !((TimeEvent) event).hasValue() ||
                !(event.getEntry() instanceof ControlFlowEntry)) {
            return retMap;
        }
        ControlFlowEntry entry = (ControlFlowEntry) event.getEntry();
        ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(entry.getTrace(), KernelAnalysisModule.ID);
        if (ssq == null) {
            return retMap;
        }

        int status = ((TimeEvent) event).getValue();
        if (status == ProcessStatus.RUN_SYTEMCALL.getStateValue().unboxInt()) {
            int syscallQuark = ssq.optQuarkRelative(entry.getThreadQuark(), Attributes.SYSTEM_CALL);
            if (syscallQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
                return retMap;
            }
            try {
                ITmfStateInterval value = ssq.querySingleState(event.getTime(), syscallQuark);
                if (!value.getStateValue().isNull()) {
                    ITmfStateValue state = value.getStateValue();
                    retMap.put(Messages.ControlFlowView_attributeSyscallName, state.toString());
                }

            } catch (TimeRangeException e) {
                Activator.getDefault().logError("Error in ControlFlowPresentationProvider", e); //$NON-NLS-1$
            } catch (StateSystemDisposedException e) {
                /* Ignored */
            }
        }

        return retMap;
    }

    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event, long hoverTime) {
        Map<String, String> retMap = super.getEventHoverToolTipInfo(event, hoverTime);
        if (retMap == null) {
            retMap = new LinkedHashMap<>();
        }

        if (!(event instanceof TimeEvent) || !((TimeEvent) event).hasValue() ||
                !(event.getEntry() instanceof ControlFlowEntry)) {
            return retMap;
        }

        ControlFlowEntry entry = (ControlFlowEntry) event.getEntry();
        ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(entry.getTrace(), KernelAnalysisModule.ID);
        if (ssq == null) {
            return retMap;
        }

        try {
            int currentCpuRqQuark = ssq.optQuarkRelative(entry.getThreadQuark(), Attributes.CURRENT_CPU_RQ);
            if (currentCpuRqQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                ITmfStateInterval interval = ssq.querySingleState(hoverTime, currentCpuRqQuark);
                ITmfStateValue value = interval.getStateValue();
                if (value.getType() == Type.INTEGER) {
                    retMap.put(Messages.ControlFlowView_attributeCpuName, String.valueOf(value.unboxInt()));
                }
            }
        } catch (TimeRangeException | StateValueTypeException e) {
            Activator.getDefault().logError("Error in ControlFlowPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }

        return retMap;
    }


    @Override
    public void postDrawEvent(ITimeEvent event, Rectangle bounds, GC gc) {
        if (fAverageCharacterWidth == null) {
            fAverageCharacterWidth = gc.getFontMetrics().getAverageCharWidth();
        }
        if (bounds.width <= fAverageCharacterWidth) {
            return;
        }
        if (!(event instanceof TimeEvent)) {
            return;
        }
        ControlFlowEntry entry = (ControlFlowEntry) event.getEntry();
        ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(entry.getTrace(), KernelAnalysisModule.ID);
        if (ss == null) {
            return;
        }
        int status = ((TimeEvent) event).getValue();

        if (status != ProcessStatus.RUN_SYTEMCALL.getStateValue().unboxInt()) {
            return;
        }
        int syscallQuark = ss.optQuarkRelative(entry.getThreadQuark(), Attributes.SYSTEM_CALL);
        if (syscallQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return;
        }
        try {
            ITmfStateInterval value = ss.querySingleState(event.getTime(), syscallQuark);
            if (!value.getStateValue().isNull()) {
                ITmfStateValue state = value.getStateValue();
                gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));

                /*
                 * Remove the "sys_" or "syscall_entry_" or similar from what we
                 * draw in the rectangle. This depends on the trace's event
                 * layout.
                 */
                int beginIndex = 0;
                ITmfTrace trace = entry.getTrace();
                if (trace instanceof IKernelTrace) {
                    IKernelAnalysisEventLayout layout = ((IKernelTrace) trace).getKernelEventLayout();
                    beginIndex = layout.eventSyscallEntryPrefix().length();
                }

                Utils.drawText(gc, state.toString().substring(beginIndex), bounds.x, bounds.y, bounds.width, bounds.height, true, true);
            }
        } catch (TimeRangeException e) {
            Activator.getDefault().logError("Error in ControlFlowPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
    }
}
