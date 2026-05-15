package com.name.flashlight.integration.vpages;

import android.annotation.SuppressLint;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class GenericFragmentAdapter<T> extends FragmentStateAdapter {
    private final List<T> items = new ArrayList<>();
    private final MultiFragmentCreator<T> fragmentCreator;
    private final StableIdProvider<T> idProvider;

    @Nullable
    private final DiffCallbackProvider<T> diffCallback;
    private final SparseArray<Fragment> fragmentCache = new SparseArray<>();
    /**
     * 新构造函数：引入稳定 ID 提供器，建议业务侧传入稳定的 long 型 ID。
     */
    public GenericFragmentAdapter(
            @NonNull FragmentActivity activity,
            @NonNull List<T> initialItems,
            @NonNull MultiFragmentCreator<T> fragmentCreator,
            @NonNull StableIdProvider<T> idProvider,
            @Nullable DiffCallbackProvider<T> diffCallback
    ) {
        super(activity);
        this.items.addAll(new ArrayList<>(initialItems));
        this.fragmentCreator = fragmentCreator;
        this.diffCallback = diffCallback;
        this.idProvider = idProvider;
    }

    /**
     * 新构造函数：用于宿主为 Fragment 的场景。
     */
    public GenericFragmentAdapter(
            @NonNull Fragment hostFragment,
            @NonNull List<T> initialItems,
            @NonNull MultiFragmentCreator<T> fragmentCreator,
            @NonNull StableIdProvider<T> idProvider,
            @Nullable DiffCallbackProvider<T> diffCallback
    ) {
        super(hostFragment);
        this.items.addAll(new ArrayList<>(initialItems));
        this.fragmentCreator = fragmentCreator;
        this.diffCallback = diffCallback;
        this.idProvider = idProvider;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment = fragmentCreator.createFragment(items.get(position), position);
        fragmentCache.put(position, fragment);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return idProvider.getItemId(items.get(position)); // 使用稳定 ID 提供器
    }

    @Nullable
    public Fragment getFragmentAt(int position) {
        return fragmentCache.get(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        for (T item : items) {
            if (idProvider.getItemId(item) == itemId) return true;
        }
        return false;
    }

    /**
     * 提交新数据：如果提供 diffCallback 则使用差分，否则直接刷新
     */
    @SuppressLint("NotifyDataSetChanged")
    public void submitList(@NonNull List<T> newList) {
        if (diffCallback == null) {
            items.clear();
            items.addAll(new ArrayList<>(newList));
            notifyDataSetChanged(); // fallback，刷新整个列表
            return;
        }

        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPos, int newItemPos) {
                return diffCallback.areItemsTheSame(items.get(oldItemPos), newList.get(newItemPos));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPos, int newItemPos) {
                return diffCallback.areContentsTheSame(items.get(oldItemPos), newList.get(newItemPos));
            }
        });

        items.clear();
        items.addAll(new ArrayList<>(newList));
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * 便捷方法：在末尾添加一项并通知插入
     */
    public void addItem(@NonNull T item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    /**
     * 便捷方法：在指定位置添加一项并通知插入
     */
    public void addItem(int position, @NonNull T item) {
        if (position < 0 || position > items.size()) {
            return;
        }
        items.add(position, item);
        notifyItemInserted(position);
    }

    /**
     * 便捷方法：移除指定位置的一项并通知移除
     *
     * @return 被移除的元素
     */
    @Nullable
    public T removeAt(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        final T removed = items.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    /**
     * 便捷方法：按值移除并通知移除
     *
     * @return 是否成功移除
     */
    public boolean removeItem(@NonNull T item) {
        final int index = items.indexOf(item);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
            return true;
        }
        return false;
    }

    /**
     * 便捷方法：移动项并通知移动
     */
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) return;
        if (fromPosition < 0 || fromPosition >= items.size()) {
            return;
        }
        final int originalSize = items.size();
        int target = toPosition;
        if (target < 0) target = 0;
        if (target >= originalSize) target = originalSize - 1; // 在原列表中进行边界修正

        // 先移除，再计算插入位置。若从前往后移动，移除后目标索引需要 -1 才能落在期望位置
        final T item = items.remove(fromPosition);
        final int sizeAfterRemove = items.size();
        if (fromPosition < toPosition) {
            target = Math.max(0, Math.min(target - 1, sizeAfterRemove));
        } else {
            target = Math.max(0, Math.min(target, sizeAfterRemove));
        }

        items.add(target, item);
        notifyItemMoved(fromPosition, target);
    }

    /**
     * 便捷方法：替换指定位置的元素并通知变更
     */
    public void setItem(int position, @NonNull T newItem) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        items.set(position, newItem);
        notifyItemChanged(position);
    }

    /**
     * 便捷方法：整体替换（不使用 DiffUtil）并整表刷新
     */
    @SuppressLint("NotifyDataSetChanged")
    public void replaceAll(@NonNull List<T> newList) {
        items.clear();
        items.addAll(new ArrayList<>(newList));
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void removeAll(){
        items.clear();
        notifyDataSetChanged();
    }
}