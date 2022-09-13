/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphO;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.EntranceNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.StringResourceWrapper;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;

/**
 * Utility class to get or create {@link Context} in current thread.
 *
 * <p>
 * Each {@link SphU}#entry() or {@link SphO}#entry() should be in a {@link Context}.
 * If we don't invoke {@link ContextUtil}#enter() explicitly, DEFAULT context will be used.
 * </p>
 *
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 */
public class ContextUtil {

    /**
     * Store the context in ThreadLocal for easy access.
     * 将上下文存储在 ThreadLocal 中以便于访问。
     */
    private static ThreadLocal<Context> contextHolder = new ThreadLocal<>();

    /**
     * Holds all {@link EntranceNode}. Each {@link EntranceNode} is associated with a distinct context name.
     * 持有所有 {@link EntranceNode}。每个 {@link EntranceNode} 都与一个不同的上下文名称相关联。
     */
    private static volatile Map<String, DefaultNode> contextNameNodeMap = new HashMap<>();

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Context NULL_CONTEXT = new NullContext();

    static {
        // Cache the entrance node for default context.
        // 缓存默认上下文的入口节点。
        initDefaultContext();
    }

    private static void initDefaultContext() {
        String defaultContextName = Constants.CONTEXT_DEFAULT_NAME;
        EntranceNode node = new EntranceNode(new StringResourceWrapper(defaultContextName, EntryType.IN), null);
        Constants.ROOT.addChild(node);
        contextNameNodeMap.put(defaultContextName, node);
    }

    /**
     * Not thread-safe, only for test.
     * 不是线程安全的，仅用于测试。
     */
    static void resetContextMap() {
        if (contextNameNodeMap != null) {
            RecordLog.warn("Context map cleared and reset to initial state");
            contextNameNodeMap.clear();
            initDefaultContext();
        }
    }

    /**
     * <p>
     * Enter the invocation context, which marks as the entrance of an invocation chain.
     * The context is wrapped with {@code ThreadLocal}, meaning that each thread has it's own {@link Context}.
     * New context will be created if current thread doesn't have one.
     * </p>
     * <p>
     * A context will be bound with an {@link EntranceNode}, which represents the entrance statistic node
     * of the invocation chain. New {@link EntranceNode} will be created if
     * current context does't have one. Note that same context name will share
     * same {@link EntranceNode} globally.
     * </p>
     * <p>
     * The origin node will be created in {@link com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot}.
     * Note that each distinct {@code origin} of different resources will lead to creating different new
     * {@link Node}, meaning that total amount of created origin statistic nodes will be:<br/>
     * {@code distinct resource name amount * distinct origin count}.<br/>
     * So when there are too many origins, memory footprint should be carefully considered.
     * </p>
     * <p>
     * Same resource in different context will count separately, see {@link NodeSelectorSlot}.
     * </p>
     *
     * @param name   the context name
     * @param origin the origin of this invocation, usually the origin could be the Service
     *               Consumer's app name. The origin is useful when we want to control different
     *               invoker/consumer separately.
     * @return The invocation context of the current thread
     */
    public static Context enter(String name, String origin) {
        if (Constants.CONTEXT_DEFAULT_NAME.equals(name)) {
            throw new ContextNameDefineException(
                    "The " + Constants.CONTEXT_DEFAULT_NAME + " can't be permit to defined!");
        }
        return trueEnter(name, origin);
    }

    /**
     * @param name   context名称
     * @param origin 来源
     * @return
     */
    protected static Context trueEnter(String name, String origin) {
        // 从Threadlocal中尝试获取context
        Context context = contextHolder.get();
        // 若Threadlocal中没有找到，则尝试从缓存map中获取
        if (context == null) {
            // 缓存map中的key 是context名称，value为EntranceNode
            Map<String, DefaultNode> localCacheNameMap = contextNameNodeMap;
            // 获取EntranceNode
            DefaultNode node = localCacheNameMap.get(name);
            if (node == null) {
                // 检查数量是否超过域值，若缓存map的size大于了context数量的最大域值，则返回NULL_CONTEXT
                if (localCacheNameMap.size() > Constants.MAX_CONTEXT_NAME_SIZE) {
                    setNullContext();
                    return NULL_CONTEXT;
                } else {
                    LOCK.lock();
                    try {
                        // 在获取一次   双重检测锁DCL 为了防止并发创建
                        node = contextNameNodeMap.get(name);
                        if (node == null) {
                            if (contextNameNodeMap.size() > Constants.MAX_CONTEXT_NAME_SIZE) {
                                setNullContext();
                                return NULL_CONTEXT;
                            } else {
                                // 创建EntranceNode
                                node = new EntranceNode(new StringResourceWrapper(name, EntryType.IN), null);
                                // Add entrance node.
                                // 将新建的node添加到root树中
                                Constants.ROOT.addChild(node);

                                // 新建map，将新创建的node放入新的map，再让缓存map指向新map
                                // 为什么不直接put 为了防止"迭代稳定性问题" —— iterate stable
                                // 对于共享集合的写操作，我们一般都采用这种框架，因为对于共享集合的读操作有可能会读到脏数据
                                Map<String, DefaultNode> newMap = new HashMap<>(contextNameNodeMap.size() + 1);
                                newMap.putAll(contextNameNodeMap);
                                newMap.put(name, node);
                                contextNameNodeMap = newMap;
                            }
                        }
                    } finally {
                        LOCK.unlock();
                    }
                }
            }
            // 创建context，将context放入ThreadLocal
            context = new Context(node, name);
            context.setOrigin(origin);
            contextHolder.set(context);
        }

        return context;
    }

    private static boolean shouldWarn = true;

    private static void setNullContext() {
        contextHolder.set(NULL_CONTEXT);
        // Don't need to be thread-safe.
        if (shouldWarn) {
            RecordLog.warn("[SentinelStatusChecker] WARN: Amount of context exceeds the threshold "
                    + Constants.MAX_CONTEXT_NAME_SIZE + ". Entries in new contexts will NOT take effect!");
            shouldWarn = false;
        }
    }

    /**
     * <p>
     * Enter the invocation context, which marks as the entrance of an invocation chain.
     * The context is wrapped with {@code ThreadLocal}, meaning that each thread has it's own {@link Context}.
     * New context will be created if current thread doesn't have one.
     * </p>
     * <p>
     * A context will be bound with an {@link EntranceNode}, which represents the entrance statistic node
     * of the invocation chain. New {@link EntranceNode} will be created if
     * current context does't have one. Note that same context name will share
     * same {@link EntranceNode} globally.
     * </p>
     * <p>
     * Same resource in different context will count separately, see {@link NodeSelectorSlot}.
     * </p>
     *
     * @param name the context name
     * @return The invocation context of the current thread
     */
    public static Context enter(String name) {
        return enter(name, "");
    }

    /**
     * Exit context of current thread, that is removing {@link Context} in the
     * ThreadLocal.
     * 当前线程的退出上下文，即删除 ThreadLocal 中的 {@link Context}。
     */
    public static void exit() {
        Context context = contextHolder.get();
        if (context != null && context.getCurEntry() == null) {
            // 将当前线程对应对上下文置空
            contextHolder.set(null);
        }
    }

    /**
     * Get current size of context entrance node map.
     *
     * @return current size of context entrance node map
     * @since 0.2.0
     */
    public static int contextSize() {
        return contextNameNodeMap.size();
    }

    /**
     * Check if provided context is a default auto-created context.
     *
     * @param context context to check
     * @return true if it is a default context, otherwise false
     * @since 0.2.0
     */
    public static boolean isDefaultContext(Context context) {
        if (context == null) {
            return false;
        }
        return Constants.CONTEXT_DEFAULT_NAME.equals(context.getName());
    }

    /**
     * Get {@link Context} of current thread.
     *
     * @return context of current thread. Null value will be return if current
     * thread does't have context.
     */
    public static Context getContext() {
        return contextHolder.get();
    }

    /**
     * <p>
     * Replace current context with the provided context.
     * This is mainly designed for context switching (e.g. in asynchronous invocation).
     * </p>
     * <p>
     * Note: When switching context manually, remember to restore the original context.
     * For common scenarios, you can use {@link #runOnContext(Context, Runnable)}.
     * </p>
     *
     * @param newContext new context to set
     * @return old context
     * @since 0.2.0
     */
    static Context replaceContext(Context newContext) {
        Context backupContext = contextHolder.get();
        if (newContext == null) {
            contextHolder.remove();
        } else {
            contextHolder.set(newContext);
        }
        return backupContext;
    }

    /**
     * Execute the code within provided context.
     * This is mainly designed for context switching (e.g. in asynchronous invocation).
     *
     * @param context the context
     * @param f       lambda to run within the context
     * @since 0.2.0
     */
    public static void runOnContext(Context context, Runnable f) {
        Context curContext = replaceContext(context);
        try {
            f.run();
        } finally {
            replaceContext(curContext);
        }
    }
}
