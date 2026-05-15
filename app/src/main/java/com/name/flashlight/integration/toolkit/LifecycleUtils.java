package com.name.flashlight.integration.toolkit;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

public final class LifecycleUtils {

    private LifecycleUtils() {
    }

    /**
     * 检查 LifecycleOwner 是否存活且可以安全执行操作
     *
     * @param lifecycleOwner 生命周期拥有者
     * @return true 表示安全
     */
    public static boolean isAlive(@NonNull LifecycleOwner lifecycleOwner) {
        final Lifecycle.State state = lifecycleOwner.getLifecycle().getCurrentState();

        // 已销毁，不安全
        if (state == Lifecycle.State.DESTROYED) {
            return false;
        }

        if (!state.isAtLeast(Lifecycle.State.INITIALIZED)) {
            return false;
        }

        // 检查 Fragment 特有状态
        if (lifecycleOwner instanceof Fragment fragment) {
            if (!isFragmentAndHostAlive(fragment)) {
                return false;
            }
        }

        // 检查 FragmentActivity
        if (lifecycleOwner instanceof FragmentActivity activity) {
            return !isActivityUnavailable(activity);
        }

        return true;
    }

    /**
     * 在 UI 线程执行任务；仅当 {@link #isAlive(LifecycleOwner)}（经 {@link #getSafeLifecycleOwner}
     * 解析后）通过时执行，否则静默丢弃。
     */
    public static void launch(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull Runnable runnable
    ) {
        ThreadUtils.runOnUiThread(() -> {
            final LifecycleOwner target = getSafeLifecycleOwner(lifecycleOwner, true);
            if (isAliveForLaunch(lifecycleOwner, target)) {
                runnable.run();
            }
        });
    }

    /**
     * 等待 onResume 后执行（用于 UI 操作）
     * 如果已经处于 RESUMED 状态，会在安全校验通过后立即执行
     *
     * @param lifecycleOwner 生命周期拥有者
     * @param runnable       要执行的任务
     */
    public static void launchWhenResume(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull Runnable runnable
    ) {
        ThreadUtils.runOnUiThread(() -> {
            final LifecycleOwner originalOwner = lifecycleOwner;
            final LifecycleOwner target = getSafeLifecycleOwner(originalOwner, true);
            if (!canObserveLifecycle(target)) {
                return;
            }

            final Lifecycle.State state = target.getLifecycle().getCurrentState();

            // 已经处于 RESUMED，立即执行
            if (state == Lifecycle.State.RESUMED) {
                if (isAliveForLaunch(originalOwner, target)) {
                    runnable.run();
                }
                return;
            }

            // 已销毁，不再等待
            if (state == Lifecycle.State.DESTROYED) {
                return;
            }

            // 添加 Observer 等待 onResume
            final Lifecycle lifecycle = target.getLifecycle();
            final LifecycleObserver observer = new DefaultLifecycleObserver() {
                @Override
                public void onResume(@NonNull LifecycleOwner owner) {
                    lifecycle.removeObserver(this);
                    if (isAliveForLaunch(originalOwner, owner)) {
                        runnable.run();
                    }
                }

                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    lifecycle.removeObserver(this);
                }
            };
            lifecycle.addObserver(observer);
        });
    }

    /**
     * 等待 onPause 时执行（用于暂停时的轻量操作，如保存状态）
     * 注意：onPause 时 Activity/Fragment 的状态会变为 STARTED
     * 注意：如果已销毁或无法继续观察生命周期，本次任务会被直接丢弃
     *
     * @param lifecycleOwner 生命周期拥有者
     * @param runnable       要执行的任务
     */
    public static void launchWhenPause(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull Runnable runnable
    ) {
        ThreadUtils.runOnUiThread(() -> {
            final LifecycleOwner target = getSafeLifecycleOwner(lifecycleOwner, true);
            final Lifecycle.State state = target.getLifecycle().getCurrentState();

            // 已销毁则无法再等到 onPause，直接放弃本次调度
            if (state == Lifecycle.State.DESTROYED) {
                return;
            }

            // 其余状态需要仍可观察
            if (!canObserveLifecycle(target)) {
                return;
            }

            // 等待 ON_PAUSE；若未收到 pause 直接进入销毁，则仅移除 observer，不执行任务
            final Lifecycle lifecycle = target.getLifecycle();
            final LifecycleObserver observer = new DefaultLifecycleObserver() {
                @Override
                public void onPause(@NonNull LifecycleOwner owner) {
                    lifecycle.removeObserver(this);
                    runnable.run();
                }

                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    lifecycle.removeObserver(this);
                }
            };
            lifecycle.addObserver(observer);
        });
    }

    /**
     * 等待 onDestroy 时执行（仅用于清理资源）
     * 注意：此时 Activity/Fragment 即将销毁，不能执行 UI 操作
     *
     * @param lifecycleOwner 生命周期拥有者
     * @param runnable       清理任务
     */
    public static void launchWhenDestroy(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull Runnable runnable
    ) {
        ThreadUtils.runOnUiThread(() -> {
            // 清理任务更符合 Fragment/Activity 自身生命周期（不要提前到 Fragment 的 onDestroyView）
            final LifecycleOwner target = getSafeLifecycleOwner(lifecycleOwner, false);
            final Lifecycle.State state = target.getLifecycle().getCurrentState();

            // 已销毁，立即执行清理
            if (state == Lifecycle.State.DESTROYED) {
                runnable.run();
                return;
            }

            // 添加 Observer 等待 onDestroy
            final Lifecycle lifecycle = target.getLifecycle();
            final LifecycleObserver observer = new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    lifecycle.removeObserver(this);
                    runnable.run();
                }
            };
            lifecycle.addObserver(observer);
        });
    }

    /**
     * 获取安全的 LifecycleOwner
     * 优先使用 Fragment 的 ViewLifecycleOwner（如果 View 已创建且未销毁）
     *
     * @param lifecycleOwner 原始 LifecycleOwner
     * @return 安全的 LifecycleOwner
     */
    @NonNull
    private static LifecycleOwner getSafeLifecycleOwner(
            @NonNull LifecycleOwner lifecycleOwner,
            boolean preferViewLifecycleOwner
    ) {
        if (!preferViewLifecycleOwner) {
            return lifecycleOwner;
        }
        if (lifecycleOwner instanceof Fragment fragment) {
            // Fragment 已处于不安全状态时，不要切换到 ViewLifecycleOwner（保持由 isAlive(Fragment) 的额外判断拦截）
            if (isFragmentInvalid(fragment)) {
                return lifecycleOwner;
            }

            LifecycleOwner viewOwner;
            try {
                viewOwner = fragment.getViewLifecycleOwner();
            } catch (IllegalStateException ignored) {
                // View 未创建或已销毁，使用 Fragment 自身
                return lifecycleOwner;
            }

            final Lifecycle.State viewState = viewOwner.getLifecycle().getCurrentState();
            // ViewLifecycleOwner 已创建且未销毁，使用它
            if (viewState != Lifecycle.State.DESTROYED) {
                return viewOwner;
            }
        }
        return lifecycleOwner;
    }

    private static boolean isAliveForLaunch(
            @NonNull LifecycleOwner originalOwner,
            @NonNull LifecycleOwner safeOwner
    ) {
        if (!isAlive(safeOwner)) {
            return false;
        }

        // 如果把 Fragment 切换到了 ViewLifecycleOwner，Fragment/Activity 的额外安全约束仍然需要生效
        if (originalOwner instanceof Fragment fragment && safeOwner != originalOwner) {
            return isFragmentAndHostAlive(fragment);
        }

        return true;
    }

    /**
     * 判断是否还值得注册生命周期监听（用于 wait 类接口）
     * 允许 CREATED/INITIALIZED，以便后续事件到来时触发。
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean canObserveLifecycle(@NonNull LifecycleOwner lifecycleOwner) {
        final Lifecycle.State state = lifecycleOwner.getLifecycle().getCurrentState();
        if (state == Lifecycle.State.DESTROYED) {
            return false;
        }

        if (lifecycleOwner instanceof Fragment fragment) {
            return isFragmentAndHostAlive(fragment);
        } else if (lifecycleOwner instanceof FragmentActivity activity) {
            return !isActivityUnavailable(activity);
        }
        return true;
    }

    private static boolean isFragmentInvalid(@NonNull Fragment fragment) {
        return fragment.isDetached() || !fragment.isAdded() || fragment.isRemoving();
    }

    private static boolean isFragmentAndHostAlive(@NonNull Fragment fragment) {
        if (isFragmentInvalid(fragment)) {
            return false;
        }
        return !isActivityUnavailable(fragment.getActivity());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isActivityUnavailable(@Nullable Activity activity) {
        // activity 为空表示宿主不可用；finishing/destroyed 也视为不可用
        return activity == null || activity.isFinishing() || activity.isDestroyed();
    }
}