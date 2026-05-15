package com.name.flashlight.integration.ads;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PlacementSharedLoadCoordinator<O extends SharedLoadOwner, A> {
    interface LoadEvents {
        void onLoadSuccess();

        void onLoadFailure(@Nullable String error);
    }

    interface SdkAdapter<A> {
        @Nullable
        A createAd(
                @NonNull Context appContext,
                @NonNull String placementId,
                @NonNull LoadEvents events
        );

        boolean isReady(@NonNull A ad);

        boolean isLoading(@NonNull A ad);

        void requestLoad(@NonNull A ad) throws Throwable;
    }

    private enum LoadingSignal {
        LOADING,
        NOT_LOADING,
        BAD
    }

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static final long SUCCESS_DISPATCH_DELAY_MS = 80L;
    private static final long PROBE_INTERVAL_MS = 100L;
    private static final int BAD_SIGNAL_THRESHOLD = 5;
    private static final long FAILURE_COOLDOWN_MS = 5_000L;
    private static final long BYPASS_COOLDOWN_MS = 5_000L;
    private static final long BYPASS_INFLIGHT_TIMEOUT_MS = 15_000L;
    private static final long NO_EVENT_STALE_MS = 10_000L;

    private final Object lock = new Object();
    @NonNull
    private final SdkAdapter<A> sdkAdapter;
    @NonNull
    private final Map<String, PlacementState<O, A>> states = new HashMap<>();
    @Nullable
    private Context globalAppContext;

    PlacementSharedLoadCoordinator(@NonNull SdkAdapter<A> sdkAdapter) {
        this.sdkAdapter = sdkAdapter;
    }

    private static final class PlacementState<O extends SharedLoadOwner, A> {
        @Nullable
        Context appContext;
        @Nullable
        A ad;
        @Nullable
        O inFlightOwner;
        boolean sdkLoading = false;

        int badSignalStreak = 0;
        long lastEventAtMs = 0L;
        long lastFailureAtMs = 0L;
        boolean bypassInFlight = false;
        long lastBypassAtMs = 0L;
        boolean successDispatchInProgress = false;
        boolean loadingProbeScheduled = false;
        long loadingProbeGeneration = 0L;

        @NonNull
        final Set<O> attachedOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        @NonNull
        final Set<O> loadWaitSet = Collections.newSetFromMap(new IdentityHashMap<>());
        @NonNull
        final ArrayDeque<O> loadWaitQueue = new ArrayDeque<>();
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class NextLoadTask<O extends SharedLoadOwner, A> {
        @NonNull
        final O owner;
        @NonNull
        final A ad;

        private NextLoadTask(@NonNull O owner, @NonNull A ad) {
            this.owner = owner;
            this.ad = ad;
        }
    }

    void setGlobalAppContext(@Nullable Context context) {
        if (context == null) return;
        synchronized (lock) {
            globalAppContext = context.getApplicationContext();
        }
    }

    void attachOwner(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull O owner
    ) {
        synchronized (lock) {
            final PlacementState<O, A> state = obtainStateLocked(placementId);
            state.attachedOwners.add(owner);
            final Context ownerContext = AdvertLifeUtils.getContext(lifecycleOwner);
            if (ownerContext != null) {
                state.appContext = ownerContext.getApplicationContext();
                globalAppContext = state.appContext;
            }
            ensureAdInitializedLocked(lifecycleOwner, placementId, state);
        }
    }

    void detachOwner(@NonNull String placementId, @NonNull O owner) {
        NextLoadTask<O, A> nextLoadTask = null;
        synchronized (lock) {
            final PlacementState<O, A> state = states.get(placementId);
            if (state == null) return;

            state.attachedOwners.remove(owner);
            state.loadWaitSet.remove(owner);
            removeFromWaitQueue(state.loadWaitQueue, owner);

            if (state.inFlightOwner == owner) {
                state.inFlightOwner = null;
            }

            if (state.loadWaitQueue.isEmpty()) {
                state.sdkLoading = false;
                state.successDispatchInProgress = false;
                invalidateProbeLocked(state);
            } else {
                nextLoadTask = promoteNextOwnerToLoadLocked(placementId, state);
            }

            if (state.attachedOwners.isEmpty()) {
                states.remove(placementId);
            }
        }

        if (nextLoadTask != null) {
            dispatchLoadAttempt(placementId, nextLoadTask, 0L);
        }
    }

    @Nullable
    A getAd(@NonNull LifecycleOwner lifecycleOwner, @NonNull String placementId) {
        synchronized (lock) {
            final PlacementState<O, A> state = obtainStateLocked(placementId);
            return ensureAdInitializedLocked(lifecycleOwner, placementId, state);
        }
    }

    boolean isReady(@NonNull LifecycleOwner lifecycleOwner, @NonNull String placementId) {
        final A ad;
        synchronized (lock) {
            final PlacementState<O, A> state = obtainStateLocked(placementId);
            ad = ensureAdInitializedLocked(lifecycleOwner, placementId, state);
        }
        if (ad == null) return false;
        try {
            return sdkAdapter.isReady(ad);
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean isLoading(@NonNull LifecycleOwner lifecycleOwner, @NonNull String placementId) {
        synchronized (lock) {
            final PlacementState<O, A> state = obtainStateLocked(placementId);
            if (state.ad == null && ensureAdInitializedLocked(lifecycleOwner, placementId, state) == null) {
                return false;
            }

            // Keep external loading state aligned with internal recovery progress.
            updateBypassTimeoutLocked(state);
            if (!state.loadWaitQueue.isEmpty() && state.inFlightOwner == null && state.sdkLoading) {
                scheduleLoadingProbeIfNeededLocked(placementId, state);
            }

            return state.inFlightOwner != null
                    || state.sdkLoading
                    || state.successDispatchInProgress
                    || state.loadingProbeScheduled
                    || state.bypassInFlight;
        }
    }

    void requestLoad(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull O owner,
            boolean forceReload
    ) {
        NextLoadTask<O, A> nextLoadTask = null;
        boolean notifyCache = false;
        synchronized (lock) {
            final PlacementState<O, A> state = states.get(placementId);
            if (state == null) return;

            if (state.ad == null && ensureAdInitializedLocked(lifecycleOwner, placementId, state) == null) {
                notifyOwnerFailure(owner);
                return;
            }

            if (!forceReload && !isInternalLoadingStateLocked(state)) {
                boolean ready = false;
                try {
                    ready = sdkAdapter.isReady(state.ad);
                } catch (Throwable ignored) {
                }
                if (ready) {
                    notifyCache = true;
                }
            }

            if (notifyCache) {
                state.loadWaitSet.remove(owner);
                removeFromWaitQueue(state.loadWaitQueue, owner);
            } else {
                if (isFailureCooldownActiveLocked(state)) {
                    notifyOwnerFailure(owner);
                    return;
                }
                if (state.loadWaitSet.add(owner)) {
                    state.loadWaitQueue.addLast(owner);
                    if (state.lastEventAtMs == 0L) {
                        state.lastEventAtMs = now();
                    }
                }
                nextLoadTask = promoteNextOwnerToLoadLocked(placementId, state);
            }
        }

        if (notifyCache) {
            sMainHandler.post(() -> {
                try {
                    owner.onSharedAdLoaded(false);
                } catch (Throwable ignored) {
                }
            });
            return;
        }

        if (nextLoadTask != null) {
            dispatchLoadAttempt(placementId, nextLoadTask, 0L);
        }
    }

    @NonNull
    private PlacementState<O, A> obtainStateLocked(@NonNull String placementId) {
        PlacementState<O, A> state = states.get(placementId);
        if (state == null) {
            state = new PlacementState<>();
            states.put(placementId, state);
        }
        return state;
    }

    private void handleImmediateFailure(
            @NonNull String placementId,
            @NonNull O failureOwner,
            @Nullable String error
    ) {
        List<O> failureWaiters;
        synchronized (lock) {
            final PlacementState<O, A> state = states.get(placementId);
            if (state == null || state.ad == null) return;

            markLoadEventSettledLocked(state);
            state.lastFailureAtMs = now();
            state.sdkLoading = false;
            state.inFlightOwner = null;
            state.successDispatchInProgress = false;
            invalidateProbeLocked(state);

            failureWaiters = new ArrayList<>(state.loadWaitQueue);
            state.loadWaitQueue.clear();
            state.loadWaitSet.clear();
        }

        if (failureWaiters.isEmpty()) {
            failureWaiters.add(failureOwner);
        }
        notifyFailureWaiters(failureWaiters, error);
    }

    private void handleSuccess(@NonNull String placementId) {
        final O loadedOwner;
        synchronized (lock) {
            final PlacementState<O, A> state = states.get(placementId);
            if (state == null) return;

            markLoadEventSettledLocked(state);

            if (state.loadWaitQueue.isEmpty()) {
                state.sdkLoading = false;
                state.inFlightOwner = null;
                state.successDispatchInProgress = false;
                invalidateProbeLocked(state);
                return;
            }

            loadedOwner = state.inFlightOwner;
            state.inFlightOwner = null;
            state.sdkLoading = true;
            state.successDispatchInProgress = true;
        }

        sMainHandler.postDelayed(
                () -> dispatchSuccessWaiters(placementId, loadedOwner),
                SUCCESS_DISPATCH_DELAY_MS
        );
    }

    private void dispatchSuccessWaiters(@NonNull String placementId, @Nullable O loadedOwner) {
        O waiter;
        final boolean adLoadedReal;
        final boolean scheduleImmediate;
        final boolean scheduleDelayed;
        final boolean shouldNotify;
        synchronized (lock) {
            final PlacementState<O, A> state = states.get(placementId);
            if (state == null) return;
            if (!state.successDispatchInProgress) return;

            waiter = state.loadWaitQueue.peekFirst();
            if (waiter == null) {
                state.sdkLoading = false;
                state.inFlightOwner = null;
                state.successDispatchInProgress = false;
                return;
            }

            if (!state.loadWaitSet.contains(waiter)) {
                removeFromWaitQueue(state.loadWaitQueue, waiter);
                if (state.loadWaitQueue.isEmpty()) {
                    state.sdkLoading = false;
                    state.inFlightOwner = null;
                    state.successDispatchInProgress = false;
                    scheduleImmediate = false;
                } else {
                    scheduleImmediate = true;
                }
                scheduleDelayed = false;
                waiter = null;
                adLoadedReal = false;
                shouldNotify = false;
            } else {
                state.loadWaitSet.remove(waiter);
                removeFromWaitQueue(state.loadWaitQueue, waiter);
                adLoadedReal = loadedOwner != null && waiter == loadedOwner;
                if (state.loadWaitQueue.isEmpty()) {
                    state.successDispatchInProgress = false;
                    state.inFlightOwner = null;
                    state.sdkLoading = false;
                    scheduleImmediate = false;
                    scheduleDelayed = false;
                } else {
                    scheduleImmediate = false;
                    scheduleDelayed = true;
                }
                shouldNotify = true;
            }
        }

        if (shouldNotify) {
            try {
                waiter.onSharedAdLoaded(adLoadedReal);
            } catch (Throwable ignored) {
            }
        }

        if (scheduleImmediate) {
            sMainHandler.post(() -> dispatchSuccessWaiters(placementId, loadedOwner));
        } else if (scheduleDelayed) {
            sMainHandler.postDelayed(
                    () -> dispatchSuccessWaiters(placementId, loadedOwner),
                    SUCCESS_DISPATCH_DELAY_MS
            );
        }
    }

    private void handleFailure(@NonNull String placementId, @Nullable String error) {
        O failureOwner;
        List<O> failureWaiters;
        synchronized (lock) {
            final PlacementState<O, A> state = states.get(placementId);
            if (state == null) return;

            markLoadEventSettledLocked(state);
            failureOwner = state.inFlightOwner;
            state.lastFailureAtMs = now();
            state.sdkLoading = false;
            state.inFlightOwner = null;
            state.successDispatchInProgress = false;
            invalidateProbeLocked(state);

            failureWaiters = new ArrayList<>(state.loadWaitQueue);
            state.loadWaitQueue.clear();
            state.loadWaitSet.clear();
        }

        if (failureWaiters.isEmpty() && failureOwner != null) {
            failureWaiters.add(failureOwner);
        }
        notifyFailureWaiters(failureWaiters, error);
    }

    private void notifyFailureWaiters(
            @NonNull List<O> failureWaiters,
            @Nullable String error
    ) {
        final ArrayList<O> queue = new ArrayList<>(failureWaiters);
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (queue.isEmpty()) return;
                final O waiter = queue.remove(0);
                try {
                    waiter.onSharedAdLoadFail(error);
                } catch (Throwable ignored) {
                }
                if (!queue.isEmpty()) {
                    sMainHandler.post(this);
                }
            }
        });
    }

    private void notifyOwnerFailure(@NonNull O owner) {
        sMainHandler.post(() -> {
            try {
                owner.onSharedAdLoadFail(null);
            } catch (Throwable ignored) {
            }
        });
    }

    @Nullable
    private NextLoadTask<O, A> promoteNextOwnerToLoadLocked(
            @NonNull String placementId,
            @NonNull PlacementState<O, A> state
    ) {
        updateBypassTimeoutLocked(state);
        if (state.ad == null) return null;

        final O nextOwner = state.loadWaitQueue.peekFirst();
        if (nextOwner == null) return null;
        if (state.inFlightOwner != null) {
            state.sdkLoading = true;
            return null;
        }

        final LoadingSignal signal = readLoadingSignalLocked(state, true);
        final boolean blockedByLoading = state.sdkLoading
                || signal == LoadingSignal.LOADING
                || signal == LoadingSignal.BAD;
        if (blockedByLoading) {
            state.sdkLoading = true;
            if (canBypassBlockedLoadingLocked(state)) {
                state.inFlightOwner = nextOwner;
                state.bypassInFlight = true;
                state.lastBypassAtMs = now();
                return new NextLoadTask<>(nextOwner, state.ad);
            }
            scheduleLoadingProbeIfNeededLocked(placementId, state);
            return null;
        }

        state.badSignalStreak = 0;
        state.inFlightOwner = nextOwner;
        state.sdkLoading = true;
        return new NextLoadTask<>(nextOwner, state.ad);
    }

    private void scheduleLoadingProbeIfNeededLocked(
            @NonNull String placementId,
            @NonNull PlacementState<O, A> state
    ) {
        if (state.loadWaitQueue.isEmpty()) return;
        if (state.inFlightOwner != null) return;
        if (!state.sdkLoading) return;
        if (state.loadingProbeScheduled) return;
        if (state.successDispatchInProgress) return;
        if (state.bypassInFlight) return;

        state.loadingProbeScheduled = true;
        final long expectedGeneration = ++state.loadingProbeGeneration;
        final PlacementState<O, A> expectedStateRef = state;
        sMainHandler.postDelayed(
                () -> runLoadingProbe(placementId, expectedStateRef, expectedGeneration),
                PROBE_INTERVAL_MS
        );
    }

    private void runLoadingProbe(
            @NonNull String placementId,
            @NonNull PlacementState<O, A> expectedStateRef,
            long expectedGeneration
    ) {
        NextLoadTask<O, A> nextLoadTask = null;
        boolean reschedule = false;
        synchronized (lock) {
            final PlacementState<O, A> state = states.get(placementId);
            if (state == null) return;
            if (state != expectedStateRef) return;
            if (state.loadingProbeGeneration != expectedGeneration) return;
            if (!state.loadingProbeScheduled) return;

            updateBypassTimeoutLocked(state);

            if (state.successDispatchInProgress || state.bypassInFlight) {
                reschedule = true;
            } else if (state.inFlightOwner != null) {
                state.loadingProbeScheduled = false;
            } else if (state.loadWaitQueue.isEmpty()) {
                state.loadingProbeScheduled = false;
            } else {
                final LoadingSignal signal = readLoadingSignalLocked(state, true);
                if (signal == LoadingSignal.NOT_LOADING) {
                    state.sdkLoading = false;
                    nextLoadTask = promoteNextOwnerToLoadLocked(placementId, state);
                    if (nextLoadTask == null) {
                        if (state.loadWaitQueue.isEmpty()) {
                            state.loadingProbeScheduled = false;
                        } else {
                            reschedule = state.sdkLoading
                                    && !state.successDispatchInProgress
                                    && !state.bypassInFlight
                                    && state.inFlightOwner == null;
                        }
                    } else {
                        state.loadingProbeScheduled = false;
                    }
                } else if (signal == LoadingSignal.BAD && canBypassBlockedLoadingLocked(state)) {
                    final O nextOwner = state.loadWaitQueue.peekFirst();
                    if (nextOwner == null || state.ad == null) {
                        state.loadingProbeScheduled = false;
                    } else {
                        state.inFlightOwner = nextOwner;
                        state.bypassInFlight = true;
                        state.lastBypassAtMs = now();
                        state.loadingProbeScheduled = false;
                        nextLoadTask = new NextLoadTask<>(nextOwner, state.ad);
                    }
                } else {
                    state.sdkLoading = true;
                    reschedule = true;
                }
            }
        }

        if (nextLoadTask != null) {
            dispatchLoadAttempt(placementId, nextLoadTask, 0L);
            return;
        }

        if (reschedule) {
            sMainHandler.postDelayed(
                    () -> runLoadingProbe(placementId, expectedStateRef, expectedGeneration),
                    PROBE_INTERVAL_MS
            );
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void dispatchLoadAttempt(
            @NonNull String placementId,
            @NonNull NextLoadTask<O, A> task,
            long delayMillis
    ) {
        final Runnable runnable = () -> {
            synchronized (lock) {
                final PlacementState<O, A> state = states.get(placementId);
                if (state == null || !state.sdkLoading || state.inFlightOwner != task.owner || state.ad != task.ad) {
                    return;
                }
                if (state.lastEventAtMs == 0L) {
                    state.lastEventAtMs = now();
                }
            }

            try {
                task.owner.onSharedRequestPre();
                sdkAdapter.requestLoad(task.ad);
            } catch (Throwable throwable) {
                handleImmediateFailure(placementId, task.owner, throwable.getMessage());
            }
        };

        if (delayMillis <= 0L) {
            sMainHandler.post(runnable);
        } else {
            sMainHandler.postDelayed(runnable, delayMillis);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private LoadingSignal readLoadingSignalLocked(
            @NonNull PlacementState<O, A> state,
            boolean updateStreak
    ) {
        final LoadingSignal signal;
        if (state.ad == null) {
            signal = state.sdkLoading ? LoadingSignal.LOADING : LoadingSignal.NOT_LOADING;
        } else {
            final boolean sdkLoading;
            try {
                sdkLoading = sdkAdapter.isLoading(state.ad);
            } catch (Throwable ignored) {
                if (updateStreak) state.badSignalStreak++;
                return LoadingSignal.BAD;
            }

            if (sdkLoading) {
                final boolean hasExpectation = state.inFlightOwner != null
                        || !state.loadWaitQueue.isEmpty()
                        || state.successDispatchInProgress
                        || state.bypassInFlight;
                if (hasExpectation
                        && state.lastEventAtMs > 0L
                        && now() - state.lastEventAtMs > NO_EVENT_STALE_MS) {
                    if (updateStreak) state.badSignalStreak++;
                    return LoadingSignal.BAD;
                }
                signal = LoadingSignal.LOADING;
            } else {
                signal = LoadingSignal.NOT_LOADING;
            }
        }

        if (updateStreak) {
            state.badSignalStreak = 0;
        }
        return signal;
    }

    private boolean canBypassBlockedLoadingLocked(@NonNull PlacementState<O, A> state) {
        if (state.badSignalStreak < BAD_SIGNAL_THRESHOLD) return false;
        if (state.inFlightOwner != null) return false;
        if (state.loadWaitQueue.isEmpty()) return false;
        if (state.bypassInFlight) return false;
        if (state.successDispatchInProgress) return false;
        if (state.lastBypassAtMs <= 0L) return true;
        return now() - state.lastBypassAtMs >= BYPASS_COOLDOWN_MS;
    }

    private void updateBypassTimeoutLocked(@NonNull PlacementState<O, A> state) {
        if (!state.bypassInFlight) return;
        if (now() - state.lastBypassAtMs <= BYPASS_INFLIGHT_TIMEOUT_MS) return;

        // Release the bypass slot if no terminal callback arrives in time.
        state.bypassInFlight = false;
        if (state.inFlightOwner != null) {
            state.inFlightOwner = null;
            state.sdkLoading = true;
        }
    }

    private void markLoadEventSettledLocked(@NonNull PlacementState<O, A> state) {
        state.lastEventAtMs = now();
        state.badSignalStreak = 0;
        state.bypassInFlight = false;
    }

    private boolean isFailureCooldownActiveLocked(@NonNull PlacementState<O, A> state) {
        return state.lastFailureAtMs > 0L
                && now() - state.lastFailureAtMs < FAILURE_COOLDOWN_MS;
    }

    private void invalidateProbeLocked(@NonNull PlacementState<O, A> state) {
        state.loadingProbeScheduled = false;
        state.loadingProbeGeneration++;
    }

    private boolean isInternalLoadingStateLocked(@NonNull PlacementState<O, A> state) {
        return state.inFlightOwner != null
                || state.sdkLoading
                || state.successDispatchInProgress
                || state.loadingProbeScheduled
                || state.bypassInFlight;
    }

    private void removeFromWaitQueue(@NonNull ArrayDeque<O> queue, @Nullable O owner) {
        if (owner == null || queue.isEmpty()) return;
        final Iterator<O> it = queue.iterator();
        while (it.hasNext()) {
            if (it.next() == owner) {
                it.remove();
                return;
            }
        }
    }

    private long now() {
        return SystemClock.elapsedRealtime();
    }

    @Nullable
    private A ensureAdInitializedLocked(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull PlacementState<O, A> state
    ) {
        if (state.ad != null) return state.ad;

        Context context = state.appContext;
        if (context == null) {
            context = globalAppContext;
        }
        if (context == null) {
            final Context ownerContext = AdvertLifeUtils.getContext(lifecycleOwner);
            if (ownerContext != null) {
                context = ownerContext.getApplicationContext();
            }
        }
        if (context == null) return null;

        state.appContext = context.getApplicationContext();
        globalAppContext = state.appContext;
        final String finalPlacementId = placementId;
        try {
            state.ad = sdkAdapter.createAd(
                    state.appContext,
                    placementId,
                    new LoadEvents() {
                        @Override
                        public void onLoadSuccess() {
                            handleSuccess(finalPlacementId);
                        }

                        @Override
                        public void onLoadFailure(@Nullable String error) {
                            handleFailure(finalPlacementId, error);
                        }
                    }
            );
        } catch (Throwable throwable) {
            state.ad = null;
        }
        return state.ad;
    }
}
