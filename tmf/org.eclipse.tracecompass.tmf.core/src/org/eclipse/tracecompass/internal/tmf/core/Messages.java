/*******************************************************************************
 * Copyright (c) 2013, 2014 Ericsson
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/

package org.eclipse.tracecompass.internal.tmf.core;

import org.eclipse.osgi.util.NLS;

/**
 * TMF Core message bundle
 */
@SuppressWarnings("javadoc")
public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.tracecompass.internal.tmf.core.messages"; //$NON-NLS-1$
    public static String CounterAspect_HelpPrefix;
    public static String TmfCheckpointIndexer_EventsPerSecond;
    public static String TmfCheckpointIndexer_Indexing;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
