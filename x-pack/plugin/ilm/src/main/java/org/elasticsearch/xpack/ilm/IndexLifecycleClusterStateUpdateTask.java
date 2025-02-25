/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ilm;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateTaskListener;
import org.elasticsearch.common.util.concurrent.ListenableFuture;
import org.elasticsearch.index.Index;
import org.elasticsearch.xpack.core.ilm.Step;

/**
 * Base class for index lifecycle cluster state update tasks that requires implementing {@code equals} and {@code hashCode} to allow
 * for these tasks to be deduplicated by {@link IndexLifecycleRunner}.
 */
public abstract class IndexLifecycleClusterStateUpdateTask implements ClusterStateTaskListener {

    private final ListenableFuture<Void> listener = new ListenableFuture<>();

    protected final Index index;

    protected final Step.StepKey currentStepKey;

    protected IndexLifecycleClusterStateUpdateTask(Index index, Step.StepKey currentStepKey) {
        this.index = index;
        this.currentStepKey = currentStepKey;
    }

    final Index getIndex() {
        return index;
    }

    final Step.StepKey getCurrentStepKey() {
        return currentStepKey;
    }

    private boolean executed;

    public final ClusterState execute(ClusterState currentState) throws Exception {
        assert executed == false;
        final ClusterState updatedState = doExecute(currentState);
        if (currentState != updatedState) {
            executed = true;
        }
        return updatedState;
    }

    protected abstract ClusterState doExecute(ClusterState currentState) throws Exception;

    @Override
    public final void clusterStateProcessed(String source, ClusterState oldState, ClusterState newState) {
        listener.onResponse(null);
        if (executed) {
            onClusterStateProcessed(source, oldState, newState);
        }
    }

    @Override
    public final void onFailure(String source, Exception e) {
        listener.onFailure(e);
        handleFailure(source, e);
    }

    /**
     * Add a listener that is resolved once this update has been processed or failed and before either the
     * {@link #onClusterStateProcessed(String, ClusterState, ClusterState)} or the {@link #handleFailure(String, Exception)} hooks are
     * executed.
     */
    public final void addListener(ActionListener<Void> listener) {
        this.listener.addListener(listener);
    }

    /**
     * This method is functionally the same as {@link ClusterStateTaskListener#clusterStateProcessed(String, ClusterState, ClusterState)}
     * and implementations can override it as they would override {@code ClusterStateUpdateTask#clusterStateProcessed}.
     * The only difference to  {@code ClusterStateUpdateTask#clusterStateProcessed} is that if the {@link #execute(ClusterState)}
     * implementation was a noop and returned the input cluster state, then this method will not be invoked. It is therefore guaranteed
     * that {@code oldState} is always different from {@code newState}.
     */
    protected void onClusterStateProcessed(String source, ClusterState oldState, ClusterState newState) {}

    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();

    /**
     * This method is functionally the same as {@link ClusterStateTaskListener#onFailure(String, Exception)} and implementations can
     * override it as they would override {@code ClusterStateUpdateTask#onFailure}.
     */
    protected abstract void handleFailure(String source, Exception e);
}
