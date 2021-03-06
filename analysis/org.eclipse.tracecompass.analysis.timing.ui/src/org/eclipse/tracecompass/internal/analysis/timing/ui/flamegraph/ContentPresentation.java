/*******************************************************************************
 * Copyright (c) 2017 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.internal.analysis.timing.ui.flamegraph;

/**
 * The content presentation option enum
 *
 * @author Bernd Hufmann
 */
enum ContentPresentation {
    /** Show by thread */
    BY_THREAD,
    /** Aggregate threads */
    AGGREGATE_THREADS;

    public static ContentPresentation fromName(String name) {
        if (name.equals(ContentPresentation.BY_THREAD.name())) {
            return ContentPresentation.BY_THREAD;
        } else if (name.equals(ContentPresentation.AGGREGATE_THREADS.name())) {
            return ContentPresentation.AGGREGATE_THREADS;
        }
        return ContentPresentation.AGGREGATE_THREADS;
    }
}
